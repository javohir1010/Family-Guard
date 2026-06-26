package com.familyguard.app.services;

import com.familyguard.app.data.api.ApiService;
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
public final class AppMonitorAccessibilityService_MembersInjector implements MembersInjector<AppMonitorAccessibilityService> {
  private final Provider<ApiService> apiServiceProvider;

  public AppMonitorAccessibilityService_MembersInjector(Provider<ApiService> apiServiceProvider) {
    this.apiServiceProvider = apiServiceProvider;
  }

  public static MembersInjector<AppMonitorAccessibilityService> create(
      Provider<ApiService> apiServiceProvider) {
    return new AppMonitorAccessibilityService_MembersInjector(apiServiceProvider);
  }

  @Override
  public void injectMembers(AppMonitorAccessibilityService instance) {
    injectApiService(instance, apiServiceProvider.get());
  }

  @InjectedFieldSignature("com.familyguard.app.services.AppMonitorAccessibilityService.apiService")
  public static void injectApiService(AppMonitorAccessibilityService instance,
      ApiService apiService) {
    instance.apiService = apiService;
  }
}
