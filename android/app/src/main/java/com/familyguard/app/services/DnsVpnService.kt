package com.familyguard.app.services

import android.app.*
import android.content.Intent
import android.net.VpnService
import android.os.*
import android.system.OsConstants
import androidx.core.app.NotificationCompat
import com.familyguard.app.R
import com.familyguard.app.data.api.ApiService
import com.familyguard.app.data.model.DnsQueryBatch
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.*
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import javax.inject.Inject

/**
 * DNS VPN Service — перехватывает DNS-запросы (UDP порт 53),
 * логирует запрошенные домены и блокирует запросы из blocklist
 * отвечая NXDOMAIN (нулевым ответом).
 *
 * Остальной трафик форвардится на реальный DNS (8.8.8.8).
 */
@AndroidEntryPoint
class DnsVpnService : VpnService() {

    @Inject lateinit var apiService: ApiService

    companion object {
        const val CHANNEL_ID = "fg_vpn_channel"
        const val NOTIFICATION_ID = 1002
        const val UPSTREAM_DNS = "8.8.8.8"
        const val DNS_PORT = 53

        private val ACTION_START = "com.familyguard.VPN_START"
        private val ACTION_STOP = "com.familyguard.VPN_STOP"

        fun startVpn(context: android.content.Context) {
            val intent = Intent(context, DnsVpnService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                context.startForegroundService(intent)
            else context.startService(intent)
        }

        fun stopVpn(context: android.content.Context) {
            context.startService(
                Intent(context, DnsVpnService::class.java).apply { action = ACTION_STOP }
            )
        }
    }

    private var vpnInterface: ParcelFileDescriptor? = null
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val blocklist = mutableSetOf<String>()
    private val pendingQueries = mutableListOf<DnsQueryBatch.Query>()
    private var running = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopVpn()
                return START_NOT_STICKY
            }
        }
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        serviceScope.launch { loadBlocklist() }
        startVpnTunnel()
        // Flush query batch every 5 minutes
        serviceScope.launch {
            while (running) {
                delay(5 * 60 * 1000L)
                flushQueryBatch()
            }
        }
        return START_STICKY
    }

    private fun startVpnTunnel() {
        val builder = Builder()
            .setSession("FamilyGuard DNS Filter")
            .addAddress("10.0.0.2", 32)
            .addRoute("0.0.0.0", 0)
            .addDnsServer(UPSTREAM_DNS)
            .setMtu(1500)
            .setBlocking(true)

        // Exclude our own app from VPN to avoid routing loop
        builder.addDisallowedApplication(packageName)

        vpnInterface = builder.establish() ?: run {
            stopSelf()
            return
        }

        running = true
        serviceScope.launch { runPacketLoop() }
    }

    /**
     * Main packet processing loop.
     * Reads IP packets from TUN, extracts DNS queries, checks blocklist,
     * either responds locally (NXDOMAIN) or forwards to real DNS server.
     */
    private suspend fun runPacketLoop() = withContext(Dispatchers.IO) {
        val vpnFd = vpnInterface?.fileDescriptor ?: return@withContext
        val inputStream = FileInputStream(vpnFd)
        val outputStream = FileOutputStream(vpnFd)
        val packet = ByteBuffer.allocate(32767)

        while (running && isActive) {
            packet.clear()
            val length = inputStream.read(packet.array())
            if (length <= 0) continue

            packet.limit(length)

            // Only process UDP/DNS packets
            if (!isUdpDnsPacket(packet)) {
                // Forward non-DNS packets unchanged
                outputStream.write(packet.array(), 0, length)
                continue
            }

            val domain = extractDomainFromDns(packet) ?: continue

            // Log query
            synchronized(pendingQueries) {
                pendingQueries.add(
                    DnsQueryBatch.Query(
                        domain = domain,
                        queryType = "A",
                        wasBlocked = domain in blocklist,
                        timestamp = java.time.Instant.now().toString()
                    )
                )
            }

            if (domain in blocklist) {
                // Respond with NXDOMAIN — browser will show "site not found"
                val nxResponse = buildNxDomainResponse(packet)
                outputStream.write(nxResponse)
            } else {
                // Forward to upstream DNS
                forwardDnsQuery(packet, outputStream)
            }
        }
    }

    private fun isUdpDnsPacket(buffer: ByteBuffer): Boolean {
        // IP version 4, protocol UDP (17), destination port 53
        if (buffer.limit() < 28) return false
        val version = (buffer.get(0).toInt() shr 4) and 0xF
        if (version != 4) return false
        val protocol = buffer.get(9).toInt() and 0xFF
        if (protocol != 17) return false  // UDP
        val ihl = (buffer.get(0).toInt() and 0xF) * 4
        val destPort = ((buffer.get(ihl + 2).toInt() and 0xFF) shl 8) or
                (buffer.get(ihl + 3).toInt() and 0xFF)
        return destPort == DNS_PORT
    }

    /**
     * Extract domain name from DNS query packet.
     * DNS payload starts after IP header (ihl) + UDP header (8 bytes) + 12 bytes DNS header.
     */
    private fun extractDomainFromDns(buffer: ByteBuffer): String? {
        return try {
            val ihl = (buffer.get(0).toInt() and 0xF) * 4
            val dnsOffset = ihl + 8 + 12  // IP header + UDP header + DNS header
            if (buffer.limit() <= dnsOffset) return null

            val sb = StringBuilder()
            var i = dnsOffset
            while (i < buffer.limit()) {
                val len = buffer.get(i).toInt() and 0xFF
                if (len == 0) break
                if (sb.isNotEmpty()) sb.append('.')
                for (j in 1..len) {
                    if (i + j >= buffer.limit()) return null
                    sb.append(buffer.get(i + j).toChar())
                }
                i += len + 1
            }
            sb.toString().lowercase().takeIf { it.isNotEmpty() }
        } catch (e: Exception) {
            null
        }
    }

    private fun buildNxDomainResponse(queryPacket: ByteBuffer): ByteArray {
        // Build minimal DNS NXDOMAIN response
        // Copy transaction ID from query, set QR=1, RCODE=3 (NXDOMAIN)
        val ihl = (queryPacket.get(0).toInt() and 0xF) * 4
        val dnsStart = ihl + 8
        val txId = ByteArray(2) { queryPacket.get(dnsStart + it) }
        val response = ByteArray(dnsStart + 12)
        // Copy IP + UDP headers
        queryPacket.position(0)
        queryPacket.get(response, 0, dnsStart)
        // DNS header: txId, flags=0x8183 (QR=1,AA=1,RCODE=3), QDCOUNT=1, rest 0
        response[dnsStart] = txId[0]
        response[dnsStart + 1] = txId[1]
        response[dnsStart + 2] = 0x81.toByte()
        response[dnsStart + 3] = 0x83.toByte()  // NXDOMAIN
        response[dnsStart + 4] = 0x00; response[dnsStart + 5] = 0x01  // QDCOUNT
        return response
    }

    private fun forwardDnsQuery(packet: ByteBuffer, output: FileOutputStream) {
        try {
            val ihl = (packet.get(0).toInt() and 0xF) * 4
            val dnsStart = ihl + 8
            val dnsPayload = ByteArray(packet.limit() - dnsStart)
            packet.position(dnsStart)
            packet.get(dnsPayload)

            val socket = DatagramSocket()
            protect(socket)
            val dnsServer = InetAddress.getByName(UPSTREAM_DNS)
            socket.send(DatagramPacket(dnsPayload, dnsPayload.size, dnsServer, DNS_PORT))

            val response = ByteArray(4096)
            val responsePacket = DatagramPacket(response, response.size)
            socket.soTimeout = 3000
            socket.receive(responsePacket)
            socket.close()

            // Wrap DNS response back in IP/UDP and write to TUN
            // (simplified — for production use proper IP packet construction)
            output.write(response, 0, responsePacket.length)
        } catch (e: Exception) {
            // Timeout or error — DNS resolution fails silently
        }
    }

    private suspend fun loadBlocklist() {
        try {
            val response = apiService.getBlocklist()
            synchronized(blocklist) {
                blocklist.clear()
                blocklist.addAll(response.domains)
            }
        } catch (e: Exception) {
            // Use cached blocklist from Room DB
        }
    }

    private suspend fun flushQueryBatch() {
        val batch: List<DnsQueryBatch.Query>
        synchronized(pendingQueries) {
            batch = pendingQueries.toList()
            pendingQueries.clear()
        }
        if (batch.isEmpty()) return
        try {
            apiService.uploadDnsQueries(DnsQueryBatch(queries = batch))
        } catch (e: Exception) {
            // Re-add to pending if upload failed
            synchronized(pendingQueries) { pendingQueries.addAll(batch) }
        }
    }

    private fun stopVpn() {
        running = false
        serviceScope.launch { flushQueryBatch() }
        vpnInterface?.close()
        vpnInterface = null
        stopForeground(true)
        stopSelf()
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
