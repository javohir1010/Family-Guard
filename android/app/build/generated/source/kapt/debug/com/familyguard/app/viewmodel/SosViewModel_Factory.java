package com.familyguard.app.viewmodel;

import android.content.Context;
import com.familyguard.app.data.api.ApiService;
import com.familyguard.app.data.local.TokenStorage;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

@ScopeMetadata
@QualifierMetadata("dagger.hilt.android.qualifiers.ApplicationContext")
@DaggerGenerated
@Generated(
    value = "dagger.internal.codegen.ComponentProcessor",
    comments = "https://dagger.dev"
)
@SuppressWarnings({
    "unchecked",
    "rawtypes",
    "KotlinInternal",
    "KotlinInternalInJava",
    "cast"
})
public final class SosViewModel_Factory implements Factory<SosViewModel> {
  private final Provider<ApiService> apiServiceProvider;

  private final Provider<TokenStorage> tokenStorageProvider;

  private final Provider<Context> contextProvider;

  public SosViewModel_Factory(Provider<ApiService> apiServiceProvider,
      Provider<TokenStorage> tokenStorageProvider, Provider<Context> contextProvider) {
    this.apiServiceProvider = apiServiceProvider;
    this.tokenStorageProvider = tokenStorageProvider;
    this.contextProvider = contextProvider;
  }

  @Override
  public SosViewModel get() {
    return newInstance(apiServiceProvider.get(), tokenStorageProvider.get(), contextProvider.get());
  }

  public static SosViewModel_Factory create(Provider<ApiService> apiServiceProvider,
      Provider<TokenStorage> tokenStorageProvider, Provider<Context> contextProvider) {
    return new SosViewModel_Factory(apiServiceProvider, tokenStorageProvider, contextProvider);
  }

  public static SosViewModel newInstance(ApiService apiService, TokenStorage tokenStorage,
      Context context) {
    return new SosViewModel(apiService, tokenStorage, context);
  }
}
