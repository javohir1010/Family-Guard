package com.familyguard.app.services;

import android.content.Context;
import androidx.work.WorkerParameters;
import com.familyguard.app.data.api.ApiService;
import dagger.internal.DaggerGenerated;
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
public final class UsageStatsSyncWorker_Factory {
  private final Provider<ApiService> apiServiceProvider;

  public UsageStatsSyncWorker_Factory(Provider<ApiService> apiServiceProvider) {
    this.apiServiceProvider = apiServiceProvider;
  }

  public UsageStatsSyncWorker get(Context context, WorkerParameters params) {
    return newInstance(context, params, apiServiceProvider.get());
  }

  public static UsageStatsSyncWorker_Factory create(Provider<ApiService> apiServiceProvider) {
    return new UsageStatsSyncWorker_Factory(apiServiceProvider);
  }

  public static UsageStatsSyncWorker newInstance(Context context, WorkerParameters params,
      ApiService apiService) {
    return new UsageStatsSyncWorker(context, params, apiService);
  }
}
