@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
annotation class TapsiFeatureToggle(
    val title: String,
    val dtoName: String = "",
    val domainName: String = "",
    val enumName: String = ""
)



