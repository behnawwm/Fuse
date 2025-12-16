@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
annotation class TapsiFeatureToggle(
    val title: String,
    val defaultEnabled: Boolean,
    val dtoName: String = "",
    val domainName: String = "",
    val enumName: String = ""
)



