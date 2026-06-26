package com.familyguard.app.services;

import androidx.hilt.work.WorkerAssistedFactory;
import androidx.work.ListenableWorker;
import dagger.Binds;
import dagger.Module;
import dagger.hilt.InstallIn;
import dagger.hilt.codegen.OriginatingElement;
import dagger.hilt.components.SingletonComponent;
import dagger.multibindings.IntoMap;
import dagger.multibindings.StringKey;
import javax.annotation.processing.Generated;

@Generated("androidx.hilt.AndroidXHiltProcessor")
@Module
@InstallIn(SingletonComponent.class)
@OriginatingElement(
    topLevelClass = UsageStatsSyncWorker.class
)
public interface UsageStatsSyncWorker_HiltModule {
  @Binds
  @IntoMap
  @StringKey("com.familyguard.app.services.UsageStatsSyncWorker")
  WorkerAssistedFactory<? extends ListenableWorker> bind(
      UsageStatsSyncWorker_AssistedFactory factory);
}
