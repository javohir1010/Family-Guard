package com.familyguard.app.di;

import com.familyguard.app.data.local.TokenStorage;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;
import okhttp3.OkHttpClient;

@ScopeMetadata("javax.inject.Singleton")
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
public final class NetworkModule_ProvideOkHttpClientFactory implements Factory<OkHttpClient> {
  private final Provider<TokenStorage> tokenStorageProvider;

  public NetworkModule_ProvideOkHttpClientFactory(Provider<TokenStorage> tokenStorageProvider) {
    this.tokenStorageProvider = tokenStorageProvider;
  }

  @Override
  public OkHttpClient get() {
    return provideOkHttpClient(tokenStorageProvider.get());
  }

  public static NetworkModule_ProvideOkHttpClientFactory create(
      Provider<TokenStorage> tokenStorageProvider) {
    return new NetworkModule_ProvideOkHttpClientFactory(tokenStorageProvider);
  }

  public static OkHttpClient provideOkHttpClient(TokenStorage tokenStorage) {
    return Preconditions.checkNotNullFromProvides(NetworkModule.INSTANCE.provideOkHttpClient(tokenStorage));
  }
}
