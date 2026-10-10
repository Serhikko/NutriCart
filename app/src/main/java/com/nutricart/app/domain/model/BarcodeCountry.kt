package com.nutricart.app.domain.model

/**
 * The national GS1 office that issued a barcode, for the two prefixes the app
 * treats specially (see BarcodeOrigin). It is NOT where the food was made.
 */
enum class BarcodeCountry {
    /** GS1 prefix 482. */
    UKRAINE,

    /** GS1 prefix 481. */
    BELARUS,
}
