package com.familyguard.app.services;

import android.content.Context;
import androidx.work.WorkerParameters;
import dagger.internal.DaggerGenerated;
import dagger.internal.InstanceFactory;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

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
public final class UsageStatsSyncWorker_AssistedFactory_Impl implements UsageStatsSyncWorker_AssistedFactory {
  private final UsageStatsSyncWorker_Factory delegateFactory;

  UsageStatsSyncWorker_AssistedFactory_Impl(UsageStatsSyncWorker_Factory delegateFactory) {
    this.delegateFactory = delegateFactory;
  }

  @Override
  public UsageStatsSyncWorker create(Context arg0, WorkerParameters arg1) {
    return delegateFactory.get(arg0, arg1);
  }

  public static Provider<UsageStatsSyncWorker_AssistedFactory> create(
      UsageStatsSyncWorker_Factory delegateFactory) {
    return InstanceFactory.create(new UsageStatsSyncWorker_AssistedFactory_Impl(delegateFactory));
  }

  public static dagger.internal.Provider<UsageStatsSyncWorker_AssistedFactory> createFactoryProvider(
      UsageStatsSyncWorker_Factory delegateFactory) {
    return InstanceFactory.create(new UsageStatsSyncWorker_AssistedFactory_Impl(delegateFactory));
  }
}
