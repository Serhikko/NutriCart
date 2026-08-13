package com.nutricart.app.domain.model

/**
 * Where a workout entry came from. HEALTH_CONNECT sessions are display-only:
 * their calories are already inside the day's activeKcal, so only MANUAL
 * entries are added on top of the kcal target (and only MANUAL can be deleted).
 */
enum class WorkoutSource { MANUAL, HEALTH_CONNECT }
