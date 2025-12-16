// Complex feature with nested configuration and multiple data types
@TapsiFeatureToggle(
    title = "Advanced Camera Settings"
)
data class AdvancedCameraFeatureToggle(
    val enabled: Boolean = false,
    val maxPhotos: Int = 50,
    val quality: String = "high",
    val timeout: Long = 5000L,
    val zoomSettings: ZoomSettings = ZoomSettings(),
    val filterSettings: FilterSettings = FilterSettings()
) {
    data class ZoomSettings(
        val minZoom: Double = 1.0,
        val maxZoom: Double = 10.0,
        val smoothZoom: Boolean = true,
        val zoomSpeed: Float = 0.5f
    )
    
    data class FilterSettings(
        val enableFilters: Boolean = false,
        val defaultFilter: String = "none",
        val filterIntensity: Double = 0.8,
        val availableFilters: Int = 12
    )
}

// Simple feature with basic configuration
@TapsiFeatureToggle(
    title = "Push Notifications",
    dtoName = "NotificationConfigDto",
    domainName = "NotificationConfig",
    enumName = "PushNotifications"
)
data class PushNotificationFeatureToggle(
    val enabled: Boolean = true,
    val soundEnabled: Boolean = false,
    val vibrationEnabled: Boolean = true,
    val maxNotifications: Int = 10,
    val quietHours: QuietHours = QuietHours()
) {
    data class QuietHours(
        val enabled: Boolean = false,
        val startHour: Int = 22,
        val endHour: Int = 8,
        val weekendsOnly: Boolean = false
    )
}
