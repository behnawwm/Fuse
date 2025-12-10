import com.google.devtools.ksp.symbol.KSFile
import com.squareup.kotlinpoet.TypeName

data class FeatureField(
    val name: String,
    val typeName: TypeName
)

data class FeatureMeta(
    val packageName: String,
    val featureName: String,      // e.g. "PureCompose", "SafetyChat"
    val key: String,              // e.g. "pureCompose", "safetyChat" (JSON key)
    val title: String,            // human readable title
    val defaultEnabled: Boolean,
    val payloadFields: List<FeatureField>, // extra fields beyond "enabled"
    val containingFile: KSFile,
    val dtoName:String,
    val domainName:String,
    val enumName:String,
)
