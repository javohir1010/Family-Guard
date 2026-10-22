package com.familyguard.app.services

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.*
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.familyguard.app.R
import com.familyguard.app.data.api.ApiService
import com.familyguard.app.data.local.PendingDnsQuery
import com.familyguard.app.data.local.PendingDnsQueryDao
import com.familyguard.app.data.model.DnsQueryBatch
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.ByteBuffer
import javax.inject.Inject

/**
 * DNS VPN Service — перехватывает DNS-запросы, логирует домены и отвечает
 * NXDOMAIN для доменов из blocklist (включая поддомены).
 *
 * В туннель маршрутизируется только адрес виртуального DNS-сервера
 * ([VIRTUAL_DNS]), поэтому остальной трафик идёт мимо VPN как обычно.
 * Разрешённые запросы пересылаются на [UPSTREAM_DNS] через защищённый
 * (protect) сокет, ответ упаковывается обратно в IPv4/UDP-пакет.
 */
@AndroidEntryPoint
class DnsVpnService : VpnService() {

    @Inject lateinit var apiService: ApiService
    @Inject lateinit var queryBuffer: PendingDnsQueryDao

    companion object {
        private const val TAG = "DnsVpnService"
        const val CHANNEL_ID = "fg_vpn_channel"
        const val NOTIFICATION_ID = 1002
        const val UPSTREAM_DNS = "8.8.8.8"
        const val DNS_PORT = 53

        // Private addresses used only inside the tunnel
        private const val VPN_ADDRESS = "10.111.222.1"
        private const val VIRTUAL_DNS = "10.111.222.2"

        private const val FLUSH_INTERVAL_MS = 5 * 60 * 1000L
        // Fallback poll; changes normally arrive at once via a "blocklist_updated" push
        private const val BLOCKLIST_REFRESH_MS = 5 * 60 * 1000L
        private const val MAX_PENDING_QUERIES = 2000   // in memory, between flushes
        private const val MAX_BUFFERED_QUERIES = 20_000 // in Room, while offline

        private const val BLOCKLIST_FILE = "dns_blocklist.txt"

        /**
         * DNS-over-HTTPS endpoints (browser "Secure DNS") and Firefox's canary
         * domain. Their names are resolved through us first, so answering
         * NXDOMAIN stops apps from switching to DoH and skipping the filter.
         */
        private val DOH_BYPASS_DOMAINS = setOf(
            "use-application-dns.net",
            "dns.google", "dns.google.com",
            "cloudflare-dns.com", "one.one.one.one", "1dot1dot1dot1.cloudflare-dns.com",
            "dns.quad9.net", "dns9.quad9.net", "dns10.quad9.net", "dns11.quad9.net",
            "doh.opendns.com", "doh.familyshield.opendns.com",
            "dns.adguard.com", "dns.adguard-dns.com", "family.adguard-dns.com",
            "dns.nextdns.io", "doh.cleanbrowsing.org", "doh.mullvad.net",
            "dns.alidns.com", "doh.pub", "dns.yandex.ru", "common.dot.dns.yandex.net",
        )

        private const val ACTION_START = "com.familyguard.VPN_START"
        private const val ACTION_STOP = "com.familyguard.VPN_STOP"
        private const val ACTION_REFRESH = "com.familyguard.VPN_REFRESH_BLOCKLIST"

        /**
         * Call [VpnService.prepare] from an Activity first and start only after
         * the user has confirmed the VPN dialog (prepare() returned null / RESULT_OK).
         */
        fun startVpn(context: Context) {
            val intent = Intent(context, DnsVpnService::class.java).apply { action = ACTION_START }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    context.startForegroundService(intent)
                else context.startService(intent)
            } catch (e: Exception) {
                Log.w(TAG, "Could not start VPN service", e)
            }
        }

        /** True while the tunnel is up in this process. */
        @Volatile var isRunning = false
            private set

        /** Reload the blocklist now (parent changed it). No-op if the filter is off. */
        fun refreshBlocklist(context: Context) {
            if (!isRunning) return
            try {
                context.startService(
                    Intent(context, DnsVpnService::class.java).apply { action = ACTION_REFRESH }
                )
            } catch (e: Exception) {
                Log.w(TAG, "Could not request blocklist refresh", e)
            }
        }

        fun stopVpn(context: Context) {
            context.startService(
                Intent(context, DnsVpnService::class.java).apply { action = ACTION_STOP }
            )
        }
    }

    private var vpnInterface: ParcelFileDescriptor? = null
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    @Volatile private var blocklist: Set<String> = emptySet()
    private val pendingQueries = mutableListOf<DnsQueryBatch.Query>()
    private val flushMutex = Mutex() // periodic and on-stop flushes must not upload the same rows
    @Volatile private var running = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopVpn()
            return START_NOT_STICKY
        }
        if (intent?.action == ACTION_REFRESH) {
            // Never bring the tunnel up from a refresh request
            if (!running) {
                stopSelf()
                return START_NOT_STICKY
            }
            serviceScope.launch { loadBlocklist() }
            return START_STICKY
        }
        createNotificationChannel()
        try {
            ServiceCompat.startForeground(
                this, NOTIFICATION_ID, buildNotification(),
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                else 0
            )
        } catch (e: Exception) {
            Log.w(TAG, "startForeground failed", e)
            stopSelf()
            return START_NOT_STICKY
        }
        if (!running) startVpnTunnel()
        return START_STICKY
    }

    private fun startVpnTunnel() {
        val builder = Builder()
            .setSession("FamilyGuard DNS Filter")
            .addAddress(VPN_ADDRESS, 32)
            .addDnsServer(VIRTUAL_DNS)
            .addRoute(VIRTUAL_DNS, 32) // only DNS traffic enters the tunnel
            .setMtu(1500)
            .setBlocking(true)

        // Our own API calls must not go through the filter
        try {
            builder.addDisallowedApplication(packageName)
        } catch (e: Exception) {
            Log.w(TAG, "addDisallowedApplication failed", e)
        }

        vpnInterface = try {
            builder.establish()
        } catch (e: Exception) {
            Log.w(TAG, "establish failed", e)
            null
        }
        if (vpnInterface == null) {
            // VPN permission not granted (prepare() was not confirmed)
            stopSelf()
            return
        }

        running = true
        isRunning = true
        // Last known list first: after a reboot without network the filter still works
        loadSavedBlocklist()
        serviceScope.launch { runPacketLoop() }
        serviceScope.launch {
            while (running) {
                loadBlocklist()
                delay(BLOCKLIST_REFRESH_MS)
            }
        }
        serviceScope.launch {
            while (running) {
                delay(FLUSH_INTERVAL_MS)
                flushQueryBatch()
            }
        }
    }

    /** Reads IPv4 packets from the TUN device and answers or forwards DNS queries. */
    private suspend fun runPacketLoop() = withContext(Dispatchers.IO) {
        val fd = vpnInterface?.fileDescriptor ?: return@withContext
        val input = FileInputStream(fd)
        val output = FileOutputStream(fd)
        val buffer = ByteArray(32767)

        while (running && isActive) {
            val length = try {
                input.read(buffer)
            } catch (e: IOException) {
                break // interface closed
            }
            if (length <= 0) continue

            val packet = buffer.copyOf(length)
            val query = parseDnsQuery(packet) ?: continue

            val blocked = isBlocked(query.domain)
            synchronized(pendingQueries) {
                if (pendingQueries.size < MAX_PENDING_QUERIES) {
                    pendingQueries.add(
                        DnsQueryBatch.Query(
                            domain = query.domain,
                            queryType = query.type,
                            wasBlocked = blocked,
                            timestamp = java.time.Instant.now().toString()
                        )
                    )
                }
            }

            if (blocked) {
                writePacket(output, buildIpUdpResponse(packet, query.ihl, buildNxDomain(packet, query)))
            } else {
                // Don't block the read loop while waiting for the upstream answer
                launch { forwardDnsQuery(packet, query, output) }
            }
        }
    }

    private class DnsQuery(val ihl: Int, val dnsStart: Int, val questionEnd: Int,
                           val domain: String, val type: String)

    /** Returns the parsed question if this is an IPv4/UDP packet to port 53. */
    private fun parseDnsQuery(p: ByteArray): DnsQuery? {
        if (p.size < 20 || (p[0].toInt() shr 4) and 0xF != 4) return null
        val ihl = (p[0].toInt() and 0xF) * 4
        if ((p[9].toInt() and 0xFF) != 17 || p.size < ihl + 8 + 12) return null // UDP only
        if (u16(p, ihl + 2) != DNS_PORT) return null

        val dnsStart = ihl + 8
        if (u16(p, dnsStart + 4) < 1) return null // QDCOUNT

        val sb = StringBuilder()
        var i = dnsStart + 12
        while (true) {
            if (i >= p.size) return null
            val len = p[i].toInt() and 0xFF
            if (len == 0) { i++; break }
            if (len > 63 || i + len >= p.size) return null // compression isn't used in queries
            if (sb.isNotEmpty()) sb.append('.')
            for (j in 1..len) sb.append((p[i + j].toInt() and 0xFF).toChar())
            i += len + 1
        }
        if (i + 4 > p.size || sb.isEmpty()) return null
        val qtype = when (val t = u16(p, i)) {
            1 -> "A"; 28 -> "AAAA"; 5 -> "CNAME"; 15 -> "MX"; 16 -> "TXT"; 65 -> "HTTPS"
            else -> t.toString()
        }
        return DnsQuery(ihl, dnsStart, i + 4, sb.toString().lowercase().trimEnd('.'), qtype)
    }

    /** Exact match or any parent domain: blocking tiktok.com also blocks www.tiktok.com. */
    private fun isBlocked(domain: String): Boolean {
        val list = blocklist
        var d = domain
        if (d in DOH_BYPASS_DOMAINS) return true
        while (true) {
            if (d in list) return true
            val dot = d.indexOf('.')
            if (dot < 0) return false
            d = d.substring(dot + 1)
        }
    }

    /** DNS answer with RCODE=3 (NXDOMAIN) echoing the question section. */
    private fun buildNxDomain(packet: ByteArray, q: DnsQuery): ByteArray {
        val dns = packet.copyOfRange(q.dnsStart, q.questionEnd)
        dns[2] = (0x80 or (dns[2].toInt() and 0x79)).toByte() // QR=1, keep opcode + RD
        dns[3] = 0x83.toByte()                                  // RA=1, RCODE=NXDOMAIN
        dns[4] = 0; dns[5] = 1                                  // QDCOUNT=1
        for (k in 6 until 12) dns[k] = 0                        // AN/NS/AR = 0
        return dns
    }

    private fun forwardDnsQuery(packet: ByteArray, q: DnsQuery, output: FileOutputStream) {
        try {
            DatagramSocket().use { socket ->
                protect(socket)
                socket.soTimeout = 5000
                val payload = packet.copyOfRange(q.dnsStart, packet.size)
                socket.send(DatagramPacket(payload, payload.size,
                    InetAddress.getByName(UPSTREAM_DNS), DNS_PORT))
                val response = ByteArray(4096)
                val responsePacket = DatagramPacket(response, response.size)
                socket.receive(responsePacket)
                writePacket(output, buildIpUdpResponse(
                    packet, q.ihl, response.copyOf(responsePacket.length)))
            }
        } catch (e: Exception) {
            // Timeout or network error — the client will retry the query
        }
    }

    /** Wraps a DNS payload into an IPv4/UDP packet addressed back to the querying app. */
    private fun buildIpUdpResponse(query: ByteArray, ihl: Int, dns: ByteArray): ByteArray {
        val total = 20 + 8 + dns.size
        val out = ByteArray(total)
        val b = ByteBuffer.wrap(out)
        b.put(0x45.toByte())                 // IPv4, IHL=5
        b.put(0.toByte())                    // DSCP/ECN
        b.putShort(total.toShort())
        b.putShort(0.toShort())              // identification
        b.putShort(0x4000.toShort())         // flags: don't fragment
        b.put(64.toByte())                   // TTL
        b.put(17.toByte())                   // protocol UDP
        b.putShort(0.toShort())              // header checksum (filled below)
        b.put(query, 16, 4)                  // src = original destination
        b.put(query, 12, 4)                  // dst = original source
        b.put(query, ihl + 2, 2)             // src port = 53
        b.put(query, ihl, 2)                 // dst port = original source port
        b.putShort((8 + dns.size).toShort())
        b.putShort(0.toShort())              // UDP checksum optional for IPv4
        b.put(dns)
        val checksum = ipChecksum(out, 20)
        out[10] = (checksum shr 8).toByte()
        out[11] = checksum.toByte()
        return out
    }

    private fun ipChecksum(data: ByteArray, length: Int): Int {
        var sum = 0L
        var i = 0
        while (i < length) {
            sum += u16(data, i)
            i += 2
        }
        while (sum shr 16 != 0L) sum = (sum and 0xFFFF) + (sum shr 16)
        return (sum.inv() and 0xFFFF).toInt()
    }

    private fun u16(data: ByteArray, offset: Int): Int =
        ((data[offset].toInt() and 0xFF) shl 8) or (data[offset + 1].toInt() and 0xFF)

    private fun writePacket(output: FileOutputStream, packet: ByteArray) {
        try {
            synchronized(output) { output.write(packet) }
        } catch (e: IOException) {
            // Interface closed while answering
        }
    }

    private suspend fun loadBlocklist() {
        val domains = try {
            apiService.getBlocklist().domains.map { it.lowercase() }.toSet()
        } catch (e: Exception) {
            return // Keep the previous list on network errors
        }
        blocklist = domains
        try {
            File(filesDir, BLOCKLIST_FILE).writeText(domains.joinToString("\n"))
        } catch (e: IOException) {
            Log.w(TAG, "Could not save blocklist", e)
        }
    }

    private fun loadSavedBlocklist() {
        try {
            val file = File(filesDir, BLOCKLIST_FILE)
            if (file.exists()) blocklist = file.readLines().filter { it.isNotBlank() }.toSet()
        } catch (e: IOException) {
            Log.w(TAG, "Could not read saved blocklist", e)
        }
    }

    /** Moves the in-memory log into Room, then uploads Room oldest-first. */
    private suspend fun flushQueryBatch() = flushMutex.withLock {
        saveQueriesToBuffer()
        try {
            while (true) {
                // Backend accepts up to 500 queries per batch
                val batch = queryBuffer.oldest(500)
                if (batch.isEmpty()) break
                apiService.uploadDnsQueries(DnsQueryBatch(queries = batch.map { it.toQuery() }))
                queryBuffer.deleteUpTo(batch.last().id)
            }
        } catch (e: Exception) {
            // Offline: the rest stays in Room for the next flush
        }
    }

    private suspend fun saveQueriesToBuffer() {
        val batch: List<DnsQueryBatch.Query>
        synchronized(pendingQueries) {
            batch = pendingQueries.toList()
            pendingQueries.clear()
        }
        if (batch.isEmpty()) return
        try {
            queryBuffer.insertAll(batch.map {
                PendingDnsQuery(domain = it.domain, queryType = it.queryType,
                                wasBlocked = it.wasBlocked, timestamp = it.timestamp)
            })
            queryBuffer.trim(MAX_BUFFERED_QUERIES)
        } catch (e: Exception) {
            Log.w(TAG, "Could not buffer DNS log", e)
        }
    }

    private fun stopVpn() {
        if (running) {
            running = false
            isRunning = false
            // Separate scope: serviceScope is cancelled in onDestroy right after this
            CoroutineScope(Dispatchers.IO).launch { flushQueryBatch() }
        }
        try {
            vpnInterface?.close()
        } catch (e: IOException) {
        }
        vpnInterface = null
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onRevoke() {
        // User disabled the VPN in system settings: let the parent know
        stopVpn()
        ProtectionWatchWorker.checkSoon(applicationContext)
        super.onRevoke()
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("DNS фильтр активен")
            .setContentText("Нежелательные сайты заблокированы")
            .setSmallIcon(R.drawable.ic_shield)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setSilent(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "DNS Фильтр FamilyGuard",
                NotificationManager.IMPORTANCE_LOW
            ).apply { setShowBadge(false) }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        stopVpn()
        serviceScope.cancel()
        super.onDestroy()
    }
}
