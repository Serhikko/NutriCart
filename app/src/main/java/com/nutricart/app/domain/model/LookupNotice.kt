package com.nutricart.app.domain.model

/**
 * The message for a scan that found nothing: which sources were checked, and
 * which could not be reached. Picked by lookupNotice(); each value has its own
 * string, and every one of them keeps the "Add" action.
 */
enum class LookupNotice {
    /** Open Food Facts didn't answer; the shops were not asked or didn't answer either. */
    OFF_DOWN,

    /** Open Food Facts didn't answer; the shops answered and don't list the code. */
    OFF_DOWN_SHOPS_MISS,

    /** Not in Open Food Facts (shops not asked): a Ukrainian barcode. */
    NOT_FOUND_UKRAINE,

    /** Not in Open Food Facts (shops not asked): a Belarusian barcode. */
    NOT_FOUND_BELARUS,

    /** Not in Open Food Facts (shops not asked): any other barcode. */
    NOT_FOUND_OTHER,

    /** Neither Open Food Facts nor the shops know this Ukrainian barcode. */
    NOT_FOUND_UKRAINE_SHOPS,

    /** Neither Open Food Facts nor the shops know this barcode. */
    NOT_FOUND_OTHER_SHOPS,

    /** Not in Open Food Facts, and no shop answered. */
    NOT_FOUND_SHOPS_DOWN,
}
