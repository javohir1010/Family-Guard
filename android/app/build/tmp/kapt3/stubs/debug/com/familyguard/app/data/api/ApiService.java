package com.familyguard.app.data.api;

@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000\u0086\u0001\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010 \n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\bf\u0018\u00002\u00020\u0001J\u0014\u0010\u0002\u001a\b\u0012\u0004\u0012\u00020\u00040\u0003H\u00a7@\u00a2\u0006\u0002\u0010\u0005J\u000e\u0010\u0006\u001a\u00020\u0007H\u00a7@\u00a2\u0006\u0002\u0010\u0005J\u000e\u0010\b\u001a\u00020\tH\u00a7@\u00a2\u0006\u0002\u0010\u0005J\u0014\u0010\n\u001a\b\u0012\u0004\u0012\u00020\u000b0\u0003H\u00a7@\u00a2\u0006\u0002\u0010\u0005J\u0018\u0010\f\u001a\u00020\r2\b\b\u0001\u0010\u000e\u001a\u00020\u000fH\u00a7@\u00a2\u0006\u0002\u0010\u0010J\u0018\u0010\u0011\u001a\u00020\u00122\b\b\u0001\u0010\u000e\u001a\u00020\u0013H\u00a7@\u00a2\u0006\u0002\u0010\u0014J\u0018\u0010\u0015\u001a\u00020\u00162\b\b\u0001\u0010\u000e\u001a\u00020\u0017H\u00a7@\u00a2\u0006\u0002\u0010\u0018J\u0018\u0010\u0019\u001a\u00020\u001a2\b\b\u0001\u0010\u000e\u001a\u00020\u001bH\u00a7@\u00a2\u0006\u0002\u0010\u001cJ\u0018\u0010\u001d\u001a\u00020\u001a2\b\b\u0001\u0010\u000e\u001a\u00020\u001eH\u00a7@\u00a2\u0006\u0002\u0010\u001fJ\u0018\u0010 \u001a\u00020!2\b\b\u0001\u0010\u000e\u001a\u00020\"H\u00a7@\u00a2\u0006\u0002\u0010#J\u0018\u0010$\u001a\u00020\u001a2\b\b\u0001\u0010\u000e\u001a\u00020%H\u00a7@\u00a2\u0006\u0002\u0010&J\u0018\u0010\'\u001a\u00020\u001a2\b\b\u0001\u0010(\u001a\u00020)H\u00a7@\u00a2\u0006\u0002\u0010*J\u0018\u0010+\u001a\u00020\u001a2\b\b\u0001\u0010(\u001a\u00020,H\u00a7@\u00a2\u0006\u0002\u0010-\u00a8\u0006."}, d2 = {"Lcom/familyguard/app/data/api/ApiService;", "", "getActiveRules", "", "Lcom/familyguard/app/data/model/AppRule;", "(Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "getBlocklist", "Lcom/familyguard/app/data/model/BlocklistResponse;", "getProfile", "Lcom/familyguard/app/data/model/UserProfile;", "getScreenSchedule", "Lcom/familyguard/app/data/model/ScreenSchedule;", "joinFamily", "Lcom/familyguard/app/data/model/JoinFamilyResponse;", "request", "Lcom/familyguard/app/data/model/JoinFamilyRequest;", "(Lcom/familyguard/app/data/model/JoinFamilyRequest;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "login", "Lcom/familyguard/app/data/model/LoginResponse;", "Lcom/familyguard/app/data/model/LoginRequest;", "(Lcom/familyguard/app/data/model/LoginRequest;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "register", "Lcom/familyguard/app/data/model/RegisterResponse;", "Lcom/familyguard/app/data/model/RegisterRequest;", "(Lcom/familyguard/app/data/model/RegisterRequest;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "reportPermissionChange", "", "Lcom/familyguard/app/data/model/PermissionAlertRequest;", "(Lcom/familyguard/app/data/model/PermissionAlertRequest;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "sendLocation", "Lcom/familyguard/app/data/model/LocationRequest;", "(Lcom/familyguard/app/data/model/LocationRequest;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "triggerSos", "Lcom/familyguard/app/data/model/SosTriggerResponse;", "Lcom/familyguard/app/data/model/SosTriggerRequest;", "(Lcom/familyguard/app/data/model/SosTriggerRequest;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "updateDeviceToken", "Lcom/familyguard/app/data/model/DeviceTokenRequest;", "(Lcom/familyguard/app/data/model/DeviceTokenRequest;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "uploadAppUsage", "batch", "Lcom/familyguard/app/data/model/AppUsageBatch;", "(Lcom/familyguard/app/data/model/AppUsageBatch;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "uploadDnsQueries", "Lcom/familyguard/app/data/model/DnsQueryBatch;", "(Lcom/familyguard/app/data/model/DnsQueryBatch;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "app_debug"})
public abstract interface ApiService {
    
    @retrofit2.http.POST(value = "auth/register/")
    @org.jetbrains.annotations.Nullable()
    public abstract java.lang.Object register(@retrofit2.http.Body()
    @org.jetbrains.annotations.NotNull()
    com.familyguard.app.data.model.RegisterRequest request, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super com.familyguard.app.data.model.RegisterResponse> $completion);
    
    @retrofit2.http.POST(value = "auth/login/")
    @org.jetbrains.annotations.Nullable()
    public abstract java.lang.Object login(@retrofit2.http.Body()
    @org.jetbrains.annotations.NotNull()
    com.familyguard.app.data.model.LoginRequest request, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super com.familyguard.app.data.model.LoginResponse> $completion);
    
    @retrofit2.http.GET(value = "auth/profile/")
    @org.jetbrains.annotations.Nullable()
    public abstract java.lang.Object getProfile(@org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super com.familyguard.app.data.model.UserProfile> $completion);
    
    @retrofit2.http.POST(value = "auth/device-token/")
    @org.jetbrains.annotations.Nullable()
    public abstract java.lang.Object updateDeviceToken(@retrofit2.http.Body()
    @org.jetbrains.annotations.NotNull()
    com.familyguard.app.data.model.DeviceTokenRequest request, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super kotlin.Unit> $completion);
    
    @retrofit2.http.POST(value = "family/invite/join/")
    @org.jetbrains.annotations.Nullable()
    public abstract java.lang.Object joinFamily(@retrofit2.http.Body()
    @org.jetbrains.annotations.NotNull()
    com.familyguard.app.data.model.JoinFamilyRequest request, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super com.familyguard.app.data.model.JoinFamilyResponse> $completion);
    
    @retrofit2.http.POST(value = "location/")
    @org.jetbrains.annotations.Nullable()
    public abstract java.lang.Object sendLocation(@retrofit2.http.Body()
    @org.jetbrains.annotations.NotNull()
    com.familyguard.app.data.model.LocationRequest request, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super kotlin.Unit> $completion);
    
    @retrofit2.http.POST(value = "sos/trigger/")
    @org.jetbrains.annotations.Nullable()
    public abstract java.lang.Object triggerSos(@retrofit2.http.Body()
    @org.jetbrains.annotations.NotNull()
    com.familyguard.app.data.model.SosTriggerRequest request, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super com.familyguard.app.data.model.SosTriggerResponse> $completion);
    
    @retrofit2.http.GET(value = "dns/blocklist/")
    @org.jetbrains.annotations.Nullable()
    public abstract java.lang.Object getBlocklist(@org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super com.familyguard.app.data.model.BlocklistResponse> $completion);
    
    @retrofit2.http.POST(value = "dns/queries/batch/")
    @org.jetbrains.annotations.Nullable()
    public abstract java.lang.Object uploadDnsQueries(@retrofit2.http.Body()
    @org.jetbrains.annotations.NotNull()
    com.familyguard.app.data.model.DnsQueryBatch batch, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super kotlin.Unit> $completion);
    
    @retrofit2.http.GET(value = "apps/rules/device/")
    @org.jetbrains.annotations.Nullable()
    public abstract java.lang.Object getActiveRules(@org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super java.util.List<com.familyguard.app.data.model.AppRule>> $completion);
    
    @retrofit2.http.POST(value = "apps/usage/batch/")
    @org.jetbrains.annotations.Nullable()
    public abstract java.lang.Object uploadAppUsage(@retrofit2.http.Body()
    @org.jetbrains.annotations.NotNull()
    com.familyguard.app.data.model.AppUsageBatch batch, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super kotlin.Unit> $completion);
    
    @retrofit2.http.POST(value = "apps/permissions/")
    @org.jetbrains.annotations.Nullable()
    public abstract java.lang.Object reportPermissionChange(@retrofit2.http.Body()
    @org.jetbrains.annotations.NotNull()
    com.familyguard.app.data.model.PermissionAlertRequest request, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super kotlin.Unit> $completion);
    
    @retrofit2.http.GET(value = "apps/schedule/device/")
    @org.jetbrains.annotations.Nullable()
    public abstract java.lang.Object getScreenSchedule(@org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super java.util.List<com.familyguard.app.data.model.ScreenSchedule>> $completion);
}