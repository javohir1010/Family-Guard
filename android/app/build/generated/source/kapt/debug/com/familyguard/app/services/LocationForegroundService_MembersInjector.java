package com.familyguard.app.services;

import com.familyguard.app.data.api.ApiService;
import com.familyguard.app.data.local.TokenStorage;
import dagger.MembersInjector;
import dagger.internal.DaggerGenerated;
import dagger.internal.InjectedFieldSignature;
import dagger.internal.QualifierMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

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
public final class LocationForegroundService_MembersInjector implements MembersInjector<LocationForegroundService> {
  private final Provider<ApiService> apiServiceProvider;

  private final Provider<TokenStorage> tokenStorageProvider;

  public LocationForegroundService_MembersInjector(Provider<ApiService> apiServiceProvider,
      Provider<TokenStorage> tokenStorageProvider) {
    this.apiServiceProvider = apiServiceProvider;
    this.tokenStorageProvider = tokenStorageProvider;
  }

  public static MembersInjector<LocationForegroundService> create(
      Provider<ApiService> apiServiceProvider, Provider<TokenStorage> tokenStorageProvider) {
    return new LocationForegroundService_MembersInjector(apiServiceProvider, tokenStorageProvider);
  }

  @Override
  public void injectMembers(LocationForegroundService instance) {
    injectApiService(instance, apiServiceProvider.get());
    injectTokenStorage(instance, tokenStorageProvider.get());
  }

  @InjectedFieldSignature("com.familyguard.app.services.LocationForegroundService.apiService")
  public static void injectApiService(LocationForegroundService instance, ApiService apiService) {
    instance.apiService = apiService;
  }

  @InjectedFieldSignature("com.familyguard.app.services.LocationForegroundService.tokenStorage")
  public static void injectTokenStorage(LocationForegroundService instance,
      TokenStorage tokenStorage) {
    instance.tokenStorage = tokenStorage;
  }
}
