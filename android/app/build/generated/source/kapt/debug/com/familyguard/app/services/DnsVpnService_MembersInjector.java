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
public final class DnsVpnService_MembersInjector implements MembersInjector<DnsVpnService> {
  private final Provider<ApiService> apiServiceProvider;

  public DnsVpnService_MembersInjector(Provider<ApiService> apiServiceProvider) {
    this.apiServiceProvider = apiServiceProvider;
  }

  public static MembersInjector<DnsVpnService> create(Provider<ApiService> apiServiceProvider) {
    return new DnsVpnService_MembersInjector(apiServiceProvider);
  }

  @Override
  public void injectMembers(DnsVpnService instance) {
    injectApiService(instance, apiServiceProvider.get());
  }

  @InjectedFieldSignature("com.familyguard.app.services.DnsVpnService.apiService")
  public static void injectApiService(DnsVpnService instance, ApiService apiService) {
    instance.apiService = apiService;
  }
}
