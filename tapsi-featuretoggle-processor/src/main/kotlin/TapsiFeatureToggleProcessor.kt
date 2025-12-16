import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.*
import com.google.devtools.ksp.validate
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ksp.writeTo
import kotlinx.serialization.Serializable

class TapsiFeatureToggleProcessor(
    private val environment: SymbolProcessorEnvironment
) : SymbolProcessor {

    private val codeGenerator: CodeGenerator = environment.codeGenerator
    private val logger: KSPLogger = environment.logger
    private var processed = false

    override fun process(resolver: Resolver): List<KSAnnotated> {
        if (processed) return emptyList()

        val symbols = resolver.getSymbolsWithAnnotation(TapsiFeatureToggle::class.qualifiedName!!)
        val invalidSymbols = symbols.filterNot { it.validate() }.toList()
        val validSymbols = symbols.filter { it.validate() }.toList()

        val features = validSymbols
            .filterIsInstance<KSClassDeclaration>()
            .mapNotNull { processFeatureClass(it) }

        if (features.isEmpty()) {
            processed = true
            return invalidSymbols
        }

        val mainPackage = features.first().packageName

        generateAllFiles(mainPackage, features)

        processed = true
        return invalidSymbols
    }

    private fun processFeatureClass(declaration: KSClassDeclaration): FeatureMeta? {
        if (!KspUtils.validateDataClass(declaration, logger)) {
            return null
        }

        val annotation = declaration.annotations.firstOrNull {
            it.annotationType.resolve().declaration.qualifiedName?.asString() == 
                TapsiFeatureToggle::class.qualifiedName
        } ?: return null

        val className = declaration.simpleName.asString()
        val featureName = KspUtils.extractFeatureName(className)
        val packageName = declaration.packageName.asString()
        
        val title = annotation.arguments
            .firstOrNull { it.name?.asString() == "title" }
            ?.value as? String ?: run {
                logger.error("@TapsiFeatureToggle: 'title' is required", declaration)
                return null
            }

        val naming = extractNaming(annotation, featureName)
        
        // First collect nested configs, then fields (so we can resolve nested types properly)
        val nestedConfigs = KspUtils.collectNestedConfigs(declaration, logger)
        val fields = KspUtils.collectFields(declaration, nestedConfigs, logger)
        
        val enabledField = fields.firstOrNull { it.name == "enabled" }
        val enabledDefault = enabledField?.defaultValue?.toBoolean() ?: false

        return FeatureMeta(
            packageName = packageName,
            className = className,
            featureName = featureName,
            title = title,
            enabledDefault = enabledDefault,
            fields = fields,
            nestedConfigs = nestedConfigs,
            containingFile = declaration.containingFile!!,
            dtoName = naming.dtoName,
            domainName = naming.domainName,
            enumName = naming.enumName
        )
    }

    private fun extractNaming(annotation: KSAnnotation, featureName: String): NamingConfig {
        val dtoName = annotation.arguments
            .firstOrNull { it.name?.asString() == "dtoName" }
            ?.value as? String

        val domainName = annotation.arguments
            .firstOrNull { it.name?.asString() == "domainName" }
            ?.value as? String

        val enumName = annotation.arguments
            .firstOrNull { it.name?.asString() == "enumName" }
            ?.value as? String

        return NamingConfig(
            dtoName = dtoName?.takeIf { it.isNotEmpty() } ?: "${featureName}ConfigDto",
            domainName = domainName?.takeIf { it.isNotEmpty() } ?: "${featureName}Config",
            enumName = enumName?.takeIf { it.isNotEmpty() } ?: featureName
        )
    }

    private fun generateAllFiles(packageName: String, features: List<FeatureMeta>) {
        // Generate individual feature files
        features.forEach { feature ->
            generateFeatureDto(packageName, feature)
            generateFeatureDomain(packageName, feature)
            generateNestedDomains(packageName, feature)
        }
        
        // Generate aggregate files
        generateFeatureEnum(packageName, features)
        generateAppConfig(packageName, features)
        generateMappers(packageName, features)
    }

    private fun generateFeatureDto(packageName: String, feature: FeatureMeta) {
        val dtoType = CodeGenerators.generateNullableDto(feature)
        
        val file = FileSpec.builder(packageName, feature.dtoName)
            .addType(dtoType)
            .build()

        file.writeTo(codeGenerator, Dependencies(false, feature.containingFile))
    }

    private fun generateFeatureDomain(packageName: String, feature: FeatureMeta) {
        val domainType = CodeGenerators.generateDomainModel(feature)
        
        val file = FileSpec.builder(packageName, feature.domainName)
            .addType(domainType)
            .build()

        file.writeTo(codeGenerator, Dependencies(false, feature.containingFile))
    }

    private fun generateNestedDomains(packageName: String, feature: FeatureMeta) {
        feature.nestedConfigs.forEach { nested ->
            val domainType = CodeGenerators.generateNestedDomainModel(nested)
            val fileName = "${nested.className}Config"
            
            val file = FileSpec.builder(packageName, fileName)
                .addType(domainType)
                .build()

            file.writeTo(codeGenerator, Dependencies(false, feature.containingFile))
        }
    }

    private fun generateFeatureEnum(packageName: String, features: List<FeatureMeta>) {
        val enumBuilder = TypeSpec.enumBuilder("FeatureToggles")
            .primaryConstructor(
                FunSpec.constructorBuilder()
                    .addParameter("title", String::class)
                    .build()
            )
            .addProperty(
                PropertySpec.builder("title", String::class)
                    .initializer("title")
                    .build()
            )

        features.forEach { feature ->
            enumBuilder.addEnumConstant(
                feature.enumName,
                TypeSpec.anonymousClassBuilder()
                    .addSuperclassConstructorParameter("%S", feature.title)
                    .build()
            )
        }

        val file = FileSpec.builder(packageName, "FeatureToggles")
            .addType(enumBuilder.build())
            .build()

        val deps = Dependencies(true, *features.map { it.containingFile }.toTypedArray())
        file.writeTo(codeGenerator, deps)
    }

    private fun generateAppConfig(packageName: String, features: List<FeatureMeta>) {
        // AppConfigDto (nullable)
        val appConfigDtoBuilder = TypeSpec.classBuilder("AppConfigDto")
            .addAnnotation(Serializable::class)
            .addModifiers(KModifier.DATA)

        val dtoCtorBuilder = FunSpec.constructorBuilder()
        val dtoProperties = mutableListOf<PropertySpec>()

        features.forEach { feature ->
            val dtoType = ClassName(packageName, feature.dtoName).copy(nullable = true)
            val fieldName = feature.featureName.replaceFirstChar { it.lowercase() }
            
            dtoCtorBuilder.addParameter(fieldName, dtoType)
            dtoProperties.add(
                PropertySpec.builder(fieldName, dtoType)
                    .initializer(fieldName)
                    .build()
            )
        }

        appConfigDtoBuilder.primaryConstructor(dtoCtorBuilder.build())
        dtoProperties.forEach { appConfigDtoBuilder.addProperty(it) }

        // AppConfig (non-null)
        val appConfigBuilder = TypeSpec.classBuilder("AppConfig")
            .addModifiers(KModifier.DATA)

        val configCtorBuilder = FunSpec.constructorBuilder()
        val configProperties = mutableListOf<PropertySpec>()

        features.forEach { feature ->
            val domainType = ClassName(packageName, feature.domainName)
            val fieldName = feature.featureName.replaceFirstChar { it.lowercase() }
            
            configCtorBuilder.addParameter(fieldName, domainType)
            configProperties.add(
                PropertySpec.builder(fieldName, domainType)
                    .initializer(fieldName)
                    .build()
            )
        }

        appConfigBuilder.primaryConstructor(configCtorBuilder.build())
        configProperties.forEach { appConfigBuilder.addProperty(it) }

        // Write files
        val dtoFile = FileSpec.builder(packageName, "AppConfigDto")
            .addType(appConfigDtoBuilder.build())
            .build()

        val configFile = FileSpec.builder(packageName, "AppConfig")
            .addType(appConfigBuilder.build())
            .build()

        val deps = Dependencies(true, *features.map { it.containingFile }.toTypedArray())
        dtoFile.writeTo(codeGenerator, deps)
        configFile.writeTo(codeGenerator, deps)
    }

    private fun generateMappers(packageName: String, features: List<FeatureMeta>) {
        val fileBuilder = FileSpec.builder(packageName, "FeatureToggleMappers")

        // Individual feature mappers
        features.forEach { feature ->
            val mapper = CodeGenerators.generateMapper(feature, packageName)
            fileBuilder.addFunction(mapper)
            
            // Nested mappers
            feature.nestedConfigs.forEach { nested ->
                val nestedMapper = CodeGenerators.generateNestedMapper(nested, packageName, feature.dtoName)
                fileBuilder.addFunction(nestedMapper)
            }
        }

        // AppConfigDto -> AppConfig mapper
        val appConfigMapper = generateAppConfigMapper(packageName, features)
        fileBuilder.addFunction(appConfigMapper)

        val deps = Dependencies(true, *features.map { it.containingFile }.toTypedArray())
        fileBuilder.build().writeTo(codeGenerator, deps)
    }

    private fun generateAppConfigMapper(packageName: String, features: List<FeatureMeta>): FunSpec {
        val appConfigDto = ClassName(packageName, "AppConfigDto")
        val appConfig = ClassName(packageName, "AppConfig")

        val mappingArgs = features.joinToString(",\n        ") { feature ->
            val fieldName = feature.featureName.replaceFirstChar { it.lowercase() }
            "$fieldName = $fieldName.to${feature.domainName}()"
        }

        return FunSpec.builder("toDomain")
            .receiver(appConfigDto)
            .returns(appConfig)
            .addStatement(
                """
                return AppConfig(
                    $mappingArgs
                )
                """.trimIndent()
            )
            .build()
    }
}
