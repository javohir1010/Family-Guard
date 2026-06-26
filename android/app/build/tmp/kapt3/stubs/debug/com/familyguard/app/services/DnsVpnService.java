package com.familyguard.app.services;

/**
 * DNS VPN Service — перехватывает DNS-запросы (UDP порт 53),
 * логирует запрошенные домены и блокирует запросы из blocklist
 * отвечая NXDOMAIN (нулевым ответом).
 *
 * Остальной трафик форвардится на реальный DNS (8.8.8.8).
 */
@dagger.hilt.android.AndroidEntryPoint()
@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000j\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0005\n\u0002\u0010#\n\u0002\u0010\u000e\n\u0000\n\u0002\u0010!\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000b\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u0012\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u0002\n\u0002\b\u0007\n\u0002\u0018\u0002\n\u0002\b\u0004\n\u0002\u0010\b\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0007\b\u0007\u0018\u0000 12\u00020\u0001:\u00011B\u0005\u00a2\u0006\u0002\u0010\u0002J\b\u0010\u0015\u001a\u00020\u0016H\u0002J\u0010\u0010\u0017\u001a\u00020\u00182\u0006\u0010\u0019\u001a\u00020\u001aH\u0002J\b\u0010\u001b\u001a\u00020\u001cH\u0002J\u0012\u0010\u001d\u001a\u0004\u0018\u00010\u000b2\u0006\u0010\u001e\u001a\u00020\u001aH\u0002J\u000e\u0010\u001f\u001a\u00020\u001cH\u0082@\u00a2\u0006\u0002\u0010 J\u0018\u0010!\u001a\u00020\u001c2\u0006\u0010\"\u001a\u00020\u001a2\u0006\u0010#\u001a\u00020$H\u0002J\u0010\u0010%\u001a\u00020\u00102\u0006\u0010\u001e\u001a\u00020\u001aH\u0002J\u000e\u0010&\u001a\u00020\u001cH\u0082@\u00a2\u0006\u0002\u0010 J\b\u0010\'\u001a\u00020\u001cH\u0016J\"\u0010(\u001a\u00020)2\b\u0010*\u001a\u0004\u0018\u00010+2\u0006\u0010,\u001a\u00020)2\u0006\u0010-\u001a\u00020)H\u0016J\u000e\u0010.\u001a\u00020\u001cH\u0082@\u00a2\u0006\u0002\u0010 J\b\u0010/\u001a\u00020\u001cH\u0002J\b\u00100\u001a\u00020\u001cH\u0002R\u001e\u0010\u0003\u001a\u00020\u00048\u0006@\u0006X\u0087.\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\u0005\u0010\u0006\"\u0004\b\u0007\u0010\bR\u0014\u0010\t\u001a\b\u0012\u0004\u0012\u00020\u000b0\nX\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u0014\u0010\f\u001a\b\u0012\u0004\u0012\u00020\u000e0\rX\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u000f\u001a\u00020\u0010X\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0011\u001a\u00020\u0012X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u0010\u0010\u0013\u001a\u0004\u0018\u00010\u0014X\u0082\u000e\u00a2\u0006\u0002\n\u0000\u00a8\u00062"}, d2 = {"Lcom/familyguard/app/services/DnsVpnService;", "Landroid/net/VpnService;", "()V", "apiService", "Lcom/familyguard/app/data/api/ApiService;", "getApiService", "()Lcom/familyguard/app/data/api/ApiService;", "setApiService", "(Lcom/familyguard/app/data/api/ApiService;)V", "blocklist", "", "", "pendingQueries", "", "Lcom/familyguard/app/data/model/DnsQueryBatch$Query;", "running", "", "serviceScope", "Lkotlinx/coroutines/CoroutineScope;", "vpnInterface", "Landroid/os/ParcelFileDescriptor;", "buildNotification", "Landroid/app/Notification;", "buildNxDomainResponse", "", "queryPacket", "Ljava/nio/ByteBuffer;", "createNotificationChannel", "", "extractDomainFromDns", "buffer", "flushQueryBatch", "(Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "forwardDnsQuery", "packet", "output", "Ljava/io/FileOutputStream;", "isUdpDnsPacket", "loadBlocklist", "onDestroy", "onStartCommand", "", "intent", "Landroid/content/Intent;", "flags", "startId", "runPacketLoop", "startVpnTunnel", "stopVpn", "Companion", "app_debug"})
public final class DnsVpnService extends android.net.VpnService {
    @javax.inject.Inject()
    public com.familyguard.app.data.api.ApiService apiService;
    @org.jetbrains.annotations.NotNull()
    public static final java.lang.String CHANNEL_ID = "fg_vpn_channel";
    public static final int NOTIFICATION_ID = 1002;
    @org.jetbrains.annotations.NotNull()
    public static final java.lang.String UPSTREAM_DNS = "8.8.8.8";
    public static final int DNS_PORT = 53;
    @org.jetbrains.annotations.NotNull()
    private static final java.lang.String ACTION_START = "com.familyguard.VPN_START";
    @org.jetbrains.annotations.NotNull()
    private static final java.lang.String ACTION_STOP = "com.familyguard.VPN_STOP";
    @org.jetbrains.annotations.Nullable()
    private android.os.ParcelFileDescriptor vpnInterface;
    @org.jetbrains.annotations.NotNull()
    private final kotlinx.coroutines.CoroutineScope serviceScope = null;
    @org.jetbrains.annotations.NotNull()
    private final java.util.Set<java.lang.String> blocklist = null;
    @org.jetbrains.annotations.NotNull()
    private final java.util.List<com.familyguard.app.data.model.DnsQueryBatch.Query> pendingQueries = null;
    private boolean running = false;
    @org.jetbrains.annotations.NotNull()
    public static final com.familyguard.app.services.DnsVpnService.Companion Companion = null;
    
    public DnsVpnService() {
        super();
    }
    
    @org.jetbrains.annotations.NotNull()
    public final com.familyguard.app.data.api.ApiService getApiService() {
        return null;
    }
    
    public final void setApiService(@org.jetbrains.annotations.NotNull()
    com.familyguard.app.data.api.ApiService p0) {
    }
    
    @java.lang.Override()
    public int onStartCommand(@org.jetbrains.annotations.Nullable()
    android.content.Intent intent, int flags, int startId) {
        return 0;
    }
    
    private final void startVpnTunnel() {
    }
    
    /**
     * Main packet processing loop.
     * Reads IP packets from TUN, extracts DNS queries, checks blocklist,
     * either responds locally (NXDOMAIN) or forwards to real DNS server.
     */
    private final java.lang.Object runPacketLoop(kotlin.coroutines.Continuation<? super kotlin.Unit> $completion) {
        return null;
    }
    
    private final boolean isUdpDnsPacket(java.nio.ByteBuffer buffer) {
        return false;
    }
    
    /**
     * Extract domain name from DNS query packet.
     * DNS payload starts after IP header (ihl) + UDP header (8 bytes) + 12 bytes DNS header.
     */
    private final java.lang.String extractDomainFromDns(java.nio.ByteBuffer buffer) {
        return null;
    }
    
    private final byte[] buildNxDomainResponse(java.nio.ByteBuffer queryPacket) {
        return null;
    }
    
    private final void forwardDnsQuery(java.nio.ByteBuffer packet, java.io.FileOutputStream output) {
    }
    
    private final java.lang.Object loadBlocklist(kotlin.coroutines.Continuation<? super kotlin.Unit> $completion) {
        return null;
    }
    
    private final java.lang.Object flushQueryBatch(kotlin.coroutines.Continuation<? super kotlin.Unit> $completion) {
        return null;
    }
    
    private final void stopVpn() {
    }
    
    private final android.app.Notification buildNotification() {
        return null;
    }
    
    private final void createNotificationChannel() {
    }
    
    @java.lang.Override()
    public void onDestroy() {
    }
    
    @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000*\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0010\u000e\n\u0002\b\u0003\n\u0002\u0010\b\n\u0002\b\u0003\n\u0002\u0010\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\b\u0086\u0003\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J\u000e\u0010\u000b\u001a\u00020\f2\u0006\u0010\r\u001a\u00020\u000eJ\u000e\u0010\u000f\u001a\u00020\f2\u0006\u0010\r\u001a\u00020\u000eR\u000e\u0010\u0003\u001a\u00020\u0004X\u0082D\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0005\u001a\u00020\u0004X\u0082D\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0006\u001a\u00020\u0004X\u0086T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0007\u001a\u00020\bX\u0086T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\t\u001a\u00020\bX\u0086T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\n\u001a\u00020\u0004X\u0086T\u00a2\u0006\u0002\n\u0000\u00a8\u0006\u0010"}, d2 = {"Lcom/familyguard/app/services/DnsVpnService$Companion;", "", "()V", "ACTION_START", "", "ACTION_STOP", "CHANNEL_ID", "DNS_PORT", "", "NOTIFICATION_ID", "UPSTREAM_DNS", "startVpn", "", "context", "Landroid/content/Context;", "stopVpn", "app_debug"})
    public static final class Companion {
        
        private Companion() {
            super();
        }
        
        public final void startVpn(@org.jetbrains.annotations.NotNull()
        android.content.Context context) {
        }
        
        public final void stopVpn(@org.jetbrains.annotations.NotNull()
        android.content.Context context) {
        }
    }
}