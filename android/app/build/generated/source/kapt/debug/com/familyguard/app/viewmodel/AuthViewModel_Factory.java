package com.familyguard.app.viewmodel;

import com.familyguard.app.data.api.ApiService;
import com.familyguard.app.data.local.TokenStorage;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

@ScopeMetadata
@QualifierMetadata
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
public final class AuthViewModel_Factory implements Factory<AuthViewModel> {
  private final Provider<ApiService> apiServiceProvider;

  private final Provider<TokenStorage> tokenStorageProvider;

  public AuthViewModel_Factory(Provider<ApiService> apiServiceProvider,
      Provider<TokenStorage> tokenStorageProvider) {
    this.apiServiceProvider = apiServiceProvider;
    this.tokenStorageProvider = tokenStorageProvider;
  }

  @Override
  public AuthViewModel get() {
    return newInstance(apiServiceProvider.get(), tokenStorageProvider.get());
  }

  public static AuthViewModel_Factory create(Provider<ApiService> apiServiceProvider,
      Provider<TokenStorage> tokenStorageProvider) {
    return new AuthViewModel_Factory(apiServiceProvider, tokenStorageProvider);
  }

  public static AuthViewModel newInstance(ApiService apiService, TokenStorage tokenStorage) {
    return new AuthViewModel(apiService, tokenStorage);
  }
}
