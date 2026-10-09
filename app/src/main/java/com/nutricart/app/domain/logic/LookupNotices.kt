package com.nutricart.app.domain.logic

import com.nutricart.app.domain.model.BarcodeCountry
import com.nutricart.app.domain.model.LookupNotice
import com.nutricart.app.domain.model.ShopsStatus

/**
 * The message for a scan that found nothing. The rule that matters: never
 * say "isn't in ..." about a source that did not answer. Open Food Facts
 * being down outranks everything else, since trying again in a minute may
 * well find the product. The website applies the same rule to the same test
 * vectors.
 */
fun lookupNotice(
    country: BarcodeCountry?,
    offUnavailable: Boolean,
    shops: ShopsStatus,
): LookupNotice = when {
    offUnavailable ->
        if (shops == ShopsStatus.MISS) LookupNotice.OFF_DOWN_SHOPS_MISS else LookupNotice.OFF_DOWN
    shops == ShopsStatus.MISS ->
        if (country == BarcodeCountry.UKRAINE) LookupNotice.NOT_FOUND_UKRAINE_SHOPS
        else LookupNotice.NOT_FOUND_OTHER_SHOPS
    shops == ShopsStatus.UNAVAILABLE -> LookupNotice.NOT_FOUND_SHOPS_DOWN
    else -> when (country) {
        BarcodeCountry.UKRAINE -> LookupNotice.NOT_FOUND_UKRAINE
        BarcodeCountry.BELARUS -> LookupNotice.NOT_FOUND_BELARUS
        null -> LookupNotice.NOT_FOUND_OTHER
    }
}
