package com.nutricart.app.scanner

/**
 * Abstraction over barcode scanning (spec feature 3), so the rest of the app
 * never depends on a concrete library. Implemented by [MlKitBarcodeScanner]
 * (bound in ScannerModule); the food-search screen calls it and looks the
 * barcode up via OpenFoodFactsApi.productByBarcode.
 */
interface BarcodeScanner {
    /** Returns the scanned barcode digits, or null if the user cancelled. */
    suspend fun scan(): String?
}
