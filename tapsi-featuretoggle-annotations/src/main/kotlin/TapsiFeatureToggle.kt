@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
annotation class TapsiFeatureToggle(
    val key: String,
    val title: String,
    val defaultEnabled: Boolean = false,
    val dtoName: String = "",
    val domainName: String = "",
    val enumName: String = ""
)



