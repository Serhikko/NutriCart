package com.nutricart.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/** App entry point; @HiltAndroidApp turns on dependency injection for the whole app. */
@HiltAndroidApp
class NutriCartApp : Application()
