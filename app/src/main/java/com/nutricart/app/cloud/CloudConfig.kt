package com.nutricart.app.cloud

import com.nutricart.app.BuildConfig

/**
 * Where the cloud lives, from BuildConfig (local.properties or CI secrets).
 * A build without keys is a valid build: [isConfigured] is false, Settings
 * hides the sync section, and nothing in the app tries to reach the network.
 */
object CloudConfig {
    val url: String = BuildConfig.SUPABASE_URL.trim().trimEnd('/')
    val anonKey: String = BuildConfig.SUPABASE_ANON_KEY.trim()

    val isConfigured: Boolean
        get() = url.startsWith("https://") && anonKey.isNotEmpty()

    /** Retrofit needs a base URL ending in "/", even when nothing will be called. */
    val baseUrl: String
        get() = if (isConfigured) "$url/" else "https://unconfigured.invalid/"
}
