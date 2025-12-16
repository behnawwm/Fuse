import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.*
import com.squareup.kotlinpoet.ksp.toTypeName

object KspUtils {
    
    fun extractFeatureName(className: String): String {
        return when {
            className.endsWith("FeatureToggle") -> className.removeSuffix("FeatureToggle")
            className.endsWith("Feature") -> className.removeSuffix("Feature")
            className.endsWith("Toggle") -> className.removeSuffix("Toggle")
            else -> className
        }
    }
    
    fun extractDefaultValue(parameter: KSValueParameter, nestedConfigs: List<NestedConfig>, logger: KSPLogger): String? {
        if (!parameter.hasDefault) return null
        
        val typeName = parameter.type.resolve().declaration.qualifiedName?.asString()
        val simpleTypeName = parameter.type.resolve().declaration.simpleName.asString()
        
        // Check if this is a nested config type
        val isNestedType = nestedConfigs.any { it.className == simpleTypeName }
        
        return when {
            isNestedType -> "${simpleTypeName}Config.default"
            typeName == "kotlin.Boolean" -> "false"
            typeName == "kotlin.String" -> "\"\""
            typeName == "kotlin.Int" -> "0"
            typeName == "kotlin.Long" -> "0L"
            typeName == "kotlin.Double" -> "0.0"
            typeName == "kotlin.Float" -> "0.0f"
            else -> {
                // Try to extract from source or use constructor
                "$simpleTypeName()"
            }
        }
    }
    
    fun validateDataClass(declaration: KSClassDeclaration, logger: KSPLogger): Boolean {
        if (declaration.classKind != ClassKind.CLASS) {
            logger.error("@TapsiFeatureToggle can only be applied to data classes", declaration)
            return false
        }
        
        if (!declaration.modifiers.contains(Modifier.DATA)) {
            logger.error("@TapsiFeatureToggle requires a data class", declaration)
            return false
        }
        
        val primaryCtor = declaration.primaryConstructor
        if (primaryCtor == null) {
            logger.error("@TapsiFeatureToggle requires a primary constructor", declaration)
            return false
        }
        
        // Check for enabled field
        val hasEnabledField = primaryCtor.parameters.any { 
            it.name?.asString() == "enabled" && 
            it.type.resolve().declaration.qualifiedName?.asString() == "kotlin.Boolean"
        }
        
        if (!hasEnabledField) {
            logger.error("@TapsiFeatureToggle requires an 'enabled: Boolean' field", declaration)
            return false
        }
        
        // Check all parameters have defaults
        val missingDefaults = primaryCtor.parameters.filter { !it.hasDefault }
        if (missingDefaults.isNotEmpty()) {
            logger.error("All fields must have default values: ${missingDefaults.map { it.name?.asString() }}", declaration)
            return false
        }
        
        return true
    }
    
    fun collectFields(declaration: KSClassDeclaration, nestedConfigs: List<NestedConfig>, logger: KSPLogger): List<FeatureField> {
        val primaryCtor = declaration.primaryConstructor ?: return emptyList()
        
        return primaryCtor.parameters.mapNotNull { param ->
            val name = param.name?.asString() ?: return@mapNotNull null
            val typeName = param.type.toTypeName()
            val defaultValue = extractDefaultValue(param, nestedConfigs, logger) ?: return@mapNotNull null
            
            FeatureField(name, typeName, defaultValue)
        }
    }
    
    fun collectNestedConfigs(declaration: KSClassDeclaration, logger: KSPLogger): List<NestedConfig> {
        return declaration.declarations
            .filterIsInstance<KSClassDeclaration>()
            .filter { it.modifiers.contains(Modifier.DATA) }
            .map { nestedClass ->
                val className = nestedClass.simpleName.asString()
                // For nested configs, we need to collect their fields without nested configs (to avoid recursion)
                val fields = collectFields(nestedClass, emptyList(), logger)
                NestedConfig(className, fields)
            }
            .toList()
    }
    
    fun getSimpleTypeName(typeName: String): String {
        return typeName.substringAfterLast(".")
    }
}
