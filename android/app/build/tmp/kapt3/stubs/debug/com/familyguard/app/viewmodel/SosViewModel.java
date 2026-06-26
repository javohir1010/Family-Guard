package com.familyguard.app.viewmodel;

@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000L\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\b\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0006\b\u0007\u0018\u00002\u00020\u0001B!\b\u0007\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0005\u0012\b\b\u0001\u0010\u0006\u001a\u00020\u0007\u00a2\u0006\u0002\u0010\bJ\u000e\u0010\u0014\u001a\u00020\u0015H\u0082@\u00a2\u0006\u0002\u0010\u0016J\n\u0010\u0017\u001a\u0004\u0018\u00010\u0018H\u0002J\b\u0010\u0019\u001a\u00020\u0015H\u0014J\u0006\u0010\u001a\u001a\u00020\u0015J\u0016\u0010\u001b\u001a\u00020\u00152\u0006\u0010\u001c\u001a\u00020\rH\u0082@\u00a2\u0006\u0002\u0010\u001dR\u0014\u0010\t\u001a\b\u0012\u0004\u0012\u00020\u000b0\nX\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0002\u001a\u00020\u0003X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0006\u001a\u00020\u0007X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\f\u001a\u00020\rX\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u0010\u0010\u000e\u001a\u0004\u0018\u00010\u000fX\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0004\u001a\u00020\u0005X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u0017\u0010\u0010\u001a\b\u0012\u0004\u0012\u00020\u000b0\u0011\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0012\u0010\u0013\u00a8\u0006\u001e"}, d2 = {"Lcom/familyguard/app/viewmodel/SosViewModel;", "Landroidx/lifecycle/ViewModel;", "apiService", "Lcom/familyguard/app/data/api/ApiService;", "tokenStorage", "Lcom/familyguard/app/data/local/TokenStorage;", "context", "Landroid/content/Context;", "(Lcom/familyguard/app/data/api/ApiService;Lcom/familyguard/app/data/local/TokenStorage;Landroid/content/Context;)V", "_uiState", "Lkotlinx/coroutines/flow/MutableStateFlow;", "Lcom/familyguard/app/viewmodel/SosUiState;", "pendingPressCount", "", "pressResetJob", "Lkotlinx/coroutines/Job;", "uiState", "Lkotlinx/coroutines/flow/StateFlow;", "getUiState", "()Lkotlinx/coroutines/flow/StateFlow;", "checkConnection", "", "(Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "getCurrentLocation", "Landroid/location/Location;", "onCleared", "onSosPressed", "triggerSos", "pressCount", "(ILkotlin/coroutines/Continuation;)Ljava/lang/Object;", "app_debug"})
@dagger.hilt.android.lifecycle.HiltViewModel()
public final class SosViewModel extends androidx.lifecycle.ViewModel {
    @org.jetbrains.annotations.NotNull()
    private final com.familyguard.app.data.api.ApiService apiService = null;
    @org.jetbrains.annotations.NotNull()
    private final com.familyguard.app.data.local.TokenStorage tokenStorage = null;
    @org.jetbrains.annotations.NotNull()
    private final android.content.Context context = null;
    @org.jetbrains.annotations.NotNull()
    private final kotlinx.coroutines.flow.MutableStateFlow<com.familyguard.app.viewmodel.SosUiState> _uiState = null;
    @org.jetbrains.annotations.NotNull()
    private final kotlinx.coroutines.flow.StateFlow<com.familyguard.app.viewmodel.SosUiState> uiState = null;
    @org.jetbrains.annotations.Nullable()
    private kotlinx.coroutines.Job pressResetJob;
    private int pendingPressCount = 0;
    
    @javax.inject.Inject()
    public SosViewModel(@org.jetbrains.annotations.NotNull()
    com.familyguard.app.data.api.ApiService apiService, @org.jetbrains.annotations.NotNull()
    com.familyguard.app.data.local.TokenStorage tokenStorage, @dagger.hilt.android.qualifiers.ApplicationContext()
    @org.jetbrains.annotations.NotNull()
    android.content.Context context) {
        super();
    }
    
    @org.jetbrains.annotations.NotNull()
    public final kotlinx.coroutines.flow.StateFlow<com.familyguard.app.viewmodel.SosUiState> getUiState() {
        return null;
    }
    
    public final void onSosPressed() {
    }
    
    private final java.lang.Object triggerSos(int pressCount, kotlin.coroutines.Continuation<? super kotlin.Unit> $completion) {
        return null;
    }
    
    private final android.location.Location getCurrentLocation() {
        return null;
    }
    
    private final java.lang.Object checkConnection(kotlin.coroutines.Continuation<? super kotlin.Unit> $completion) {
        return null;
    }
    
    @java.lang.Override()
    protected void onCleared() {
    }
}