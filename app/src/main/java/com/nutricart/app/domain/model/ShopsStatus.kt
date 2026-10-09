package com.nutricart.app.domain.model

/**
 * What the Ukrainian shops' catalogue said about a scan that ended "not
 * found". Only a source that answered may be named in a "this product isn't
 * in ..." message.
 */
enum class ShopsStatus {
    /** Not asked: a Belarusian or UK code (the shops list practically none). */
    NOT_ASKED,

    /** At least one shop answered, and none had anything usable. */
    MISS,

    /** No shop answered at all (offline, timeouts, server errors). */
    UNAVAILABLE,
}
