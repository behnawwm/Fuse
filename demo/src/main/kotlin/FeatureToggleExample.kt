// Complex feature with nested configuration and multiple data types
@TapsiFeatureToggle(
    title = "Advanced Camera Settings",
    defaultEnabled = false
)
data class AdvancedCameraFeatureToggle(
    val enabled: Boolean = true,
    val timeout: Long = 5000L,
    val filterSettings: FilterSettings = FilterSettings()
) {
    data class FilterSettings(
        val minZoom: Int = 1,
        val maxZoom: Int = 12
    )
}

// Simple feature with basic configuration
//@TapsiFeatureToggle(
//    title = "Push Notifications",
//    defaultEnabled = true,
//    dtoName = "NotificationConfigDto",
//    domainName = "NotificationConfig",
//    enumName = "PushNotifications"
//)
//data class PushNotificationFeatureToggle(
//    val enabled: Boolean = true,
//    val soundEnabled: Boolean = false,
//    val vibrationEnabled: Boolean = true,
//    val maxNotifications: Int = 10,
//    val quietHours: QuietHours = QuietHours()
//) {
//    data class QuietHours(
//        val enabled: Boolean = false,
//        val startHour: Int = 22,
//        val endHour: Int = 8,
//        val weekendsOnly: Boolean = false
//    )
//}
