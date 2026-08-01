package com.nutricart.app.scanner

/**
 * Barcode scanning is a STRETCH goal (spec feature 3): v1 defines only this
 * interface so the rest of the app never depends on a concrete library.
 * A future MlKitBarcodeScanner will implement it; the food-search screen can
 * then call it and look the barcode up via OpenFoodFactsApi.productByBarcode.
 */
interface BarcodeScanner {
    /** Returns the scanned barcode digits, or null if the user cancelled. */
    suspend fun scan(): String?
}
