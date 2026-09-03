package com.michaldrabik.data_remote.di.module

import com.michaldrabik.data_remote.floppy.DefaultFloppyRemoteDataSource
import com.michaldrabik.data_remote.floppy.FloppyRemoteDataSource
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class FloppyModule {

  @Binds
  @Singleton
  internal abstract fun bindsFloppyRemoteDataSource(source: DefaultFloppyRemoteDataSource): FloppyRemoteDataSource
}
