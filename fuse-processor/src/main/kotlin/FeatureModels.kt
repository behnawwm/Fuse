import com.google.devtools.ksp.symbol.KSFile
import com.squareup.kotlinpoet.TypeName

data class FeatureField(
    val name: String,
    val typeName: TypeName,
    val defaultValue: String
)

data class NestedConfig(
    val className: String,
    val fields: List<FeatureField>
)

data class FeatureMeta(
    val packageName: String,
    val className: String,           // Original class name (e.g. "SmoothZoomFeatureToggle")
    val featureName: String,         // Derived name (e.g. "SmoothZoom")
    val title: String,               // Human readable title
    val enabledDefault: Boolean,     // Default value for enabled field
    val fields: List<FeatureField>,  // All fields including enabled
    val nestedConfigs: List<NestedConfig>, // Inner data classes
    val containingFile: KSFile,
    val dtoName: String,
    val domainName: String,
    val enumName: String
)

data class NamingConfig(
    val dtoName: String,
    val domainName: String,
    val enumName: String
) {
    companion object {
        fun fromFeatureName(featureName: String): NamingConfig {
            return NamingConfig(
                dtoName = "${featureName}ConfigDto",
                domainName = "${featureName}Config", 
                enumName = featureName
            )
        }
    }
}
