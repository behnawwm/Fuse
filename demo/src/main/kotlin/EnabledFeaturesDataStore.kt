interface EnabledFeaturesDataStore {
    val latestFetchedEnabledFeatures: AppConfig?
}

fun isFeatureEnabled(vararg featureToggles: FeatureToggles): Boolean {
    // This is a placeholder implementation
    // In real usage, you would check against your configuration
    return true
}
