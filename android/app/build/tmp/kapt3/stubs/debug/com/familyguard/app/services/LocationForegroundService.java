package com.familyguard.app.services;

@dagger.hilt.android.AndroidEntryPoint()
@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000V\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0005\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0005\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u0002\n\u0000\n\u0002\u0010\b\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0007\n\u0002\u0018\u0002\n\u0002\b\u0004\b\u0007\u0018\u0000 )2\u00020\u0001:\u0001)B\u0005\u00a2\u0006\u0002\u0010\u0002J\b\u0010\u0015\u001a\u00020\u0016H\u0002J\b\u0010\u0017\u001a\u00020\u0018H\u0002J\b\u0010\u0019\u001a\u00020\u001aH\u0002J\u0014\u0010\u001b\u001a\u0004\u0018\u00010\u001c2\b\u0010\u001d\u001a\u0004\u0018\u00010\u001eH\u0016J\b\u0010\u001f\u001a\u00020\u0018H\u0016J\b\u0010 \u001a\u00020\u0018H\u0016J\"\u0010!\u001a\u00020\u001a2\b\u0010\u001d\u001a\u0004\u0018\u00010\u001e2\u0006\u0010\"\u001a\u00020\u001a2\u0006\u0010#\u001a\u00020\u001aH\u0016J\u0016\u0010$\u001a\u00020\u00182\u0006\u0010%\u001a\u00020&H\u0082@\u00a2\u0006\u0002\u0010\'J\b\u0010(\u001a\u00020\u0018H\u0002R\u001e\u0010\u0003\u001a\u00020\u00048\u0006@\u0006X\u0087.\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\u0005\u0010\u0006\"\u0004\b\u0007\u0010\bR\u000e\u0010\t\u001a\u00020\nX\u0082.\u00a2\u0006\u0002\n\u0000R\u0010\u0010\u000b\u001a\u0004\u0018\u00010\fX\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u000e\u0010\r\u001a\u00020\u000eX\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u001e\u0010\u000f\u001a\u00020\u00108\u0006@\u0006X\u0087.\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\u0011\u0010\u0012\"\u0004\b\u0013\u0010\u0014\u00a8\u0006*"}, d2 = {"Lcom/familyguard/app/services/LocationForegroundService;", "Landroid/app/Service;", "()V", "apiService", "Lcom/familyguard/app/data/api/ApiService;", "getApiService", "()Lcom/familyguard/app/data/api/ApiService;", "setApiService", "(Lcom/familyguard/app/data/api/ApiService;)V", "fusedLocationClient", "Lcom/google/android/gms/location/FusedLocationProviderClient;", "locationCallback", "Lcom/google/android/gms/location/LocationCallback;", "serviceScope", "Lkotlinx/coroutines/CoroutineScope;", "tokenStorage", "Lcom/familyguard/app/data/local/TokenStorage;", "getTokenStorage", "()Lcom/familyguard/app/data/local/TokenStorage;", "setTokenStorage", "(Lcom/familyguard/app/data/local/TokenStorage;)V", "buildNotification", "Landroid/app/Notification;", "createNotificationChannel", "", "getBatteryLevel", "", "onBind", "Landroid/os/IBinder;", "intent", "Landroid/content/Intent;", "onCreate", "onDestroy", "onStartCommand", "flags", "startId", "sendLocationToServer", "location", "Landroid/location/Location;", "(Landroid/location/Location;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "startLocationUpdates", "Companion", "app_debug"})
public final class LocationForegroundService extends android.app.Service {
    @javax.inject.Inject()
    public com.familyguard.app.data.api.ApiService apiService;
    @javax.inject.Inject()
    public com.familyguard.app.data.local.TokenStorage tokenStorage;
    private com.google.android.gms.location.FusedLocationProviderClient fusedLocationClient;
    @org.jetbrains.annotations.NotNull()
    private final kotlinx.coroutines.CoroutineScope serviceScope = null;
    @org.jetbrains.annotations.Nullable()
    private com.google.android.gms.location.LocationCallback locationCallback;
    @org.jetbrains.annotations.NotNull()
    public static final java.lang.String CHANNEL_ID = "fg_location_channel";
    public static final int NOTIFICATION_ID = 1001;
    public static final long LOCATION_INTERVAL_MS = 600000L;
    public static final long LOCATION_FASTEST_INTERVAL_MS = 300000L;
    @org.jetbrains.annotations.NotNull()
    public static final com.familyguard.app.services.LocationForegroundService.Companion Companion = null;
    
    public LocationForegroundService() {
        super();
    }
    
    @org.jetbrains.annotations.NotNull()
    public final com.familyguard.app.data.api.ApiService getApiService() {
        return null;
    }
    
    public final void setApiService(@org.jetbrains.annotations.NotNull()
    com.familyguard.app.data.api.ApiService p0) {
    }
    
    @org.jetbrains.annotations.NotNull()
    public final com.familyguard.app.data.local.TokenStorage getTokenStorage() {
        return null;
    }
    
    public final void setTokenStorage(@org.jetbrains.annotations.NotNull()
    com.familyguard.app.data.local.TokenStorage p0) {
    }
    
    @java.lang.Override()
    public void onCreate() {
    }
    
    @java.lang.Override()
    public int onStartCommand(@org.jetbrains.annotations.Nullable()
    android.content.Intent intent, int flags, int startId) {
        return 0;
    }
    
    private final void startLocationUpdates() {
    }
    
    private final java.lang.Object sendLocationToServer(android.location.Location location, kotlin.coroutines.Continuation<? super kotlin.Unit> $completion) {
        return null;
    }
    
    private final int getBatteryLevel() {
        return 0;
    }
    
    private final android.app.Notification buildNotification() {
        return null;
    }
    
    private final void createNotificationChannel() {
    }
    
    @java.lang.Override()
    public void onDestroy() {
    }
    
    @java.lang.Override()
    @org.jetbrains.annotations.Nullable()
    public android.os.IBinder onBind(@org.jetbrains.annotations.Nullable()
    android.content.Intent intent) {
        return null;
    }
    
    @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000.\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0010\u000e\n\u0000\n\u0002\u0010\t\n\u0002\b\u0002\n\u0002\u0010\b\n\u0000\n\u0002\u0010\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\b\u0086\u0003\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J\u000e\u0010\n\u001a\u00020\u000b2\u0006\u0010\f\u001a\u00020\rJ\u000e\u0010\u000e\u001a\u00020\u000b2\u0006\u0010\f\u001a\u00020\rR\u000e\u0010\u0003\u001a\u00020\u0004X\u0086T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0005\u001a\u00020\u0006X\u0086T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0007\u001a\u00020\u0006X\u0086T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\b\u001a\u00020\tX\u0086T\u00a2\u0006\u0002\n\u0000\u00a8\u0006\u000f"}, d2 = {"Lcom/familyguard/app/services/LocationForegroundService$Companion;", "", "()V", "CHANNEL_ID", "", "LOCATION_FASTEST_INTERVAL_MS", "", "LOCATION_INTERVAL_MS", "NOTIFICATION_ID", "", "startService", "", "context", "Landroid/content/Context;", "stopService", "app_debug"})
    public static final class Companion {
        
        private Companion() {
            super();
        }
        
        public final void startService(@org.jetbrains.annotations.NotNull()
        android.content.Context context) {
        }
        
        public final void stopService(@org.jetbrains.annotations.NotNull()
        android.content.Context context) {
        }
    }
}