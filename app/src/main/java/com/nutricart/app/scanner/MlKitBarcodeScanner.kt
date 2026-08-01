package com.nutricart.app.scanner

import android.content.Context
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * [BarcodeScanner] implemented with Google's ready-made code scanner:
 * Play services shows its own full-screen camera UI, so the app needs
 * NO camera permission and no camera code at all.
 */
@Singleton
class MlKitBarcodeScanner @Inject constructor(
    @ApplicationContext context: Context,
) : BarcodeScanner {

    private val scanner = GmsBarcodeScanning.getClient(
        context,
        GmsBarcodeScannerOptions.Builder()
            // Only the formats food packaging actually uses.
            .setBarcodeFormats(
                Barcode.FORMAT_EAN_13,
                Barcode.FORMAT_EAN_8,
                Barcode.FORMAT_UPC_A,
                Barcode.FORMAT_UPC_E,
            )
            .enableAutoZoom()
            .build(),
    )

    /**
     * Bridges the scanner's callback API into a suspend function:
     * success -> the barcode digits, user cancelled -> null,
     * failure (e.g. scanner module still downloading) -> exception for the
     * caller to show as an error.
     */
    override suspend fun scan(): String? = suspendCancellableCoroutine { continuation ->
        scanner.startScan()
            .addOnSuccessListener { barcode -> continuation.resume(barcode.rawValue) }
            .addOnCanceledListener { continuation.resume(null) }
            .addOnFailureListener { error ->
                if (continuation.isActive) continuation.resumeWith(Result.failure(error))
            }
    }
}
