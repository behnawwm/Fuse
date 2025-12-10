//@TapsiFeatureToggle(
//    "pureCompose",
//    "Pure Compose",
//    false,
//)
//data object PureComposeFeatureToggle

@TapsiFeatureToggle(
    key = "safetyChat",
    title = "Safety Chat",
    dtoName = "SafetyChatConfigDto",
    domainName = "SafetyChatConfig",
    enumName = "SafetyChat",
    defaultEnabled = true,
)
data class SafetyChatFeatureToggle(
    val time: Int,
)
