plugins {
    alias(libs.plugins.android.application) apply false
    // Not applied: pins the Kotlin compiler used by AGP's built-in Kotlin support.
    alias(libs.plugins.kotlin.android) apply false
}
