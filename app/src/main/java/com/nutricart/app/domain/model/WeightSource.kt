package com.nutricart.app.domain.model

/** Where a weight measurement came from. MANUAL entries win over watch data for the same day. */
enum class WeightSource { MANUAL, HEALTH_CONNECT }
