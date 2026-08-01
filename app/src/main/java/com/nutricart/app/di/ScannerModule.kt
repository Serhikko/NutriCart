package com.nutricart.app.di

import com.nutricart.app.scanner.BarcodeScanner
import com.nutricart.app.scanner.MlKitBarcodeScanner
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Binds the BarcodeScanner interface to its ML Kit implementation.
 * The rest of the app only ever sees the interface (spec feature 3).
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class ScannerModule {

    @Binds
    abstract fun bindBarcodeScanner(impl: MlKitBarcodeScanner): BarcodeScanner
}
