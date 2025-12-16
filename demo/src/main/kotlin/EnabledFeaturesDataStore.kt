interface EnabledFeaturesDataStore {
    //    fun updateEnableFeatures(enableFeatures: EnabledFeatures)
//    fun enableFeaturesFlow(): Flow<EnabledFeatures>
    val latestFetchedEnabledFeatures: AppConfig?
}

fun isFeatureEnabled(vararg featureToggles: FeatureToggles): Boolean {
    return featureToggles.all { it.enabled() }
}