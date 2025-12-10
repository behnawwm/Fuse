import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.ksp.writeTo
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.validate
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.TypeSpec.Companion.enumBuilder
import com.squareup.kotlinpoet.ksp.toTypeName
import kotlinx.serialization.Serializable

class TapsiFeatureToggleProcessor(
    environment: SymbolProcessorEnvironment
) : SymbolProcessor {

    private val codeGenerator: CodeGenerator = environment.codeGenerator
    private val logger: KSPLogger = environment.logger

    // We aggregate everything in one round
    private var processed = false

    override fun process(resolver: Resolver): List<KSAnnotated> {
        if (processed) return emptyList()

        val symbols = resolver.getSymbolsWithAnnotation(TapsiFeatureToggle::class.qualifiedName!!)
        val invalidSymbols = symbols.filterNot { it.validate() }.toList()
        val validSymbols = symbols.filter { it.validate() }.toList()

        val features = validSymbols
            .filterIsInstance<KSClassDeclaration>()
            .mapNotNull { it.toFeatureMeta() }

        if (features.isEmpty()) {
            processed = true
            return invalidSymbols
        }

        // Decide package for generated files (use package of first feature, or fallback)
        val mainPackage = features.map { it.packageName }.distinct().singleOrNull()
            ?: features.first().packageName

        generateFeatureEnum(mainPackage, features)
        generateFeatureDtos(mainPackage, features)
        generateFeatureDomainModels(mainPackage, features)
        generateAppConfig(mainPackage, features)
        generateMappers(mainPackage, features)

        processed = true
        return invalidSymbols
    }

    private fun KSClassDeclaration.toFeatureMeta(): FeatureMeta? {
        val annotation = annotations.firstOrNull {
            it.annotationType.resolve().declaration.qualifiedName?.asString() ==
                    TapsiFeatureToggle::class.qualifiedName
        } ?: return null

        val featureName = simpleName.asString()

        val key = annotation.arguments.firstOrNull { it.name?.asString() == "key" }?.value as? String
            ?: run {
                logger.error("@TapsiFeatureToggle: 'key' is required", this)
                return null
            }

        val title = annotation.arguments.firstOrNull { it.name?.asString() == "title" }?.value as? String
            ?: run {
                logger.error("@TapsiFeatureToggle: 'title' is required", this)
                return null
            }

        val defaultEnabled = annotation.arguments
            .firstOrNull { it.name?.asString() == "defaultEnabled" }
            ?.value as? Boolean
            ?: false

        val dtoName = annotation.arguments
            .firstOrNull { it.name?.asString() == "dtoName" }
            ?.value as? String
            ?: "${featureName}ConfigDto"

        val domainName = annotation.arguments
            .firstOrNull { it.name?.asString() == "domainName" }
            ?.value as? String
            ?: "${featureName}ConfigDto"

        val enumName = annotation.arguments
            .firstOrNull { it.name?.asString() == "enumName" }
            ?.value as? String
            ?: "${featureName}ConfigDto"

        val payloadFields = collectPayloadFields(this)

        val pkg = packageName.asString()
        val name = simpleName.asString()
        val file = containingFile ?: run {
            logger.error("No containing file for $name", this)
            return null
        }

        return FeatureMeta(
            packageName = pkg,
            featureName = name,
            key = key,
            title = title,
            defaultEnabled = defaultEnabled,
            payloadFields = payloadFields,
            containingFile = file,
            dtoName = dtoName,
            domainName = domainName,
            enumName = enumName,
        )
    }

    /**
     * For a data class with primary constructor params, use them as payload fields.
     * For interfaces or classes without primary constructor → no payload.
     */
    private fun collectPayloadFields(declaration: KSClassDeclaration): List<FeatureField> {
        if (declaration.classKind != ClassKind.CLASS && declaration.classKind != ClassKind.INTERFACE) {
            return emptyList()
        }

        val primaryCtor = declaration.primaryConstructor ?: return emptyList()

        return primaryCtor.parameters
            .filter { it.isVal || it.isVar || true } // accept them even if not val/var to be lenient
            .mapNotNull { param ->
                val name = param.name?.asString()
                val type = param.type
                if (name != null) {
                    FeatureField(
                        name = name,
                        typeName = type.toTypeName()
                    )
                } else null
            }
    }

    // region Enum generation

    private fun generateFeatureEnum(
        pkg: String,
        features: List<FeatureMeta>
    ) {
        val enumBuilder = enumBuilder("FeatureToggles")
            .addKdoc(
                "Generated by TapsiFeatureToggle KSP Processor.\n" +
                        "Do not edit manually.\n"
            )
            .primaryConstructor(
                FunSpec.constructorBuilder()
                    .addParameter("title", String::class)
                    .addParameter("defaultEnabled", Boolean::class)
                    .build()
            )
            .addProperty(
                PropertySpec.builder("title", String::class)
                    .initializer("title")
                    .build()
            )
            .addProperty(
                PropertySpec.builder("defaultEnabled", Boolean::class)
                    .initializer("defaultEnabled")
                    .build()
            )

        features.forEach { feature ->
            enumBuilder.addEnumConstant(
                feature.enumName,
                TypeSpec.anonymousClassBuilder()
                    .addSuperclassConstructorParameter("%S", feature.title)
                    .addSuperclassConstructorParameter("%L", feature.defaultEnabled)
                    .build()
            )
        }

        enumBuilder.addFunction(
            FunSpec.builder("enabled")
                .returns(Boolean::class)
                .addStatement("return defaultEnabled")
                .build()
        )

        val file = FileSpec.builder(pkg, "FeatureToggles")
            .addType(enumBuilder.build())
            .build()

        val deps = Dependencies(
            aggregating = true,
            *features.map { it.containingFile }.toTypedArray()
        )
        file.writeTo(codeGenerator, deps)
    }

    // endregion

    // region Feature DTOs

    private fun generateFeatureDtos(
        pkg: String,
        features: List<FeatureMeta>
    ) {

        val serialNameClass = ClassName("kotlinx.serialization", "SerialName")

        features.forEach { feature ->
            val dtoName = feature.dtoName

            val ctorBuilder = FunSpec.constructorBuilder()
            val props = mutableListOf<PropertySpec>()

            // enabled field
            val enabledParam = ParameterSpec.builder("enabled", Boolean::class)
                .addAnnotation(
                    AnnotationSpec.builder(serialNameClass)
                        .addMember("%S", "enabled")
                        .build()
                )
                .build()
            ctorBuilder.addParameter(enabledParam)
            props.add(
                PropertySpec.builder("enabled", Boolean::class)
                    .initializer("enabled")
                    .build()
            )

            // payload fields
            feature.payloadFields.forEach { field ->
                val param = ParameterSpec.builder(field.name, field.typeName)
                    .addAnnotation(
                        AnnotationSpec.builder(serialNameClass)
                            .addMember("%S", field.name)
                            .build()
                    )
                    .build()
                ctorBuilder.addParameter(param)
                props.add(
                    PropertySpec.builder(field.name, field.typeName)
                        .initializer(field.name)
                        .build()
                )
            }

            val dtoType = TypeSpec.classBuilder(dtoName)
                .addAnnotation(Serializable::class)
                .addModifiers(KModifier.DATA)
                .primaryConstructor(ctorBuilder.build())
                .apply {
                    props.forEach { addProperty(it) }
                }
                .build()

            val file = FileSpec.builder(pkg, dtoName)
                .addType(dtoType)
                .build()

            val deps = Dependencies(aggregating = false, feature.containingFile)
            file.writeTo(codeGenerator, deps)
        }
    }

    // endregion

    // region Domain models

    private fun generateFeatureDomainModels(
        pkg: String,
        features: List<FeatureMeta>
    ) {
        features.forEach { feature ->
            val className = feature.domainName

            val ctorBuilder = FunSpec.constructorBuilder()
            val props = mutableListOf<PropertySpec>()

            // enabled field
            val enabledParam = ParameterSpec.builder("enabled", Boolean::class).build()
            ctorBuilder.addParameter(enabledParam)
            props.add(
                PropertySpec.builder("enabled", Boolean::class)
                    .initializer("enabled")
                    .build()
            )

            // payload fields
            feature.payloadFields.forEach { field ->
                val param = ParameterSpec.builder(field.name, field.typeName).build()
                ctorBuilder.addParameter(param)
                props.add(
                    PropertySpec.builder(field.name, field.typeName)
                        .initializer(field.name)
                        .build()
                )
            }

            val type = TypeSpec.classBuilder(className)
                .addModifiers(KModifier.DATA)
                .primaryConstructor(ctorBuilder.build())
                .apply {
                    props.forEach { addProperty(it) }
                }
                .build()

            val file = FileSpec.builder(pkg, className)
                .addType(type)
                .build()

            val deps = Dependencies(aggregating = false, feature.containingFile)
            file.writeTo(codeGenerator, deps)
        }
    }

    // endregion

    // region AppConfig / AppConfigDto

    private fun generateAppConfig(
        pkg: String,
        features: List<FeatureMeta>
    ) {
        val serialNameClass = ClassName("kotlinx.serialization", "SerialName")

        // AppConfigDto
        run {
            val ctorBuilder = FunSpec.constructorBuilder()
            val props = mutableListOf<PropertySpec>()

            features.forEach { feature ->
                val dtoType = ClassName(pkg, feature.dtoName)
                val propertyName = feature.key // use key as field name: pureCompose, safetyChat

                val param = ParameterSpec.builder(propertyName, dtoType)
                    .addAnnotation(
                        AnnotationSpec.builder(serialNameClass)
                            .addMember("%S", feature.key)
                            .build()
                    )
                    .build()
                ctorBuilder.addParameter(param)
                props.add(
                    PropertySpec.builder(propertyName, dtoType)
                        .initializer(propertyName)
                        .build()
                )
            }

            val type = TypeSpec.classBuilder("AppConfigDto")
                .addAnnotation(Serializable::class)
                .addModifiers(KModifier.DATA)
                .primaryConstructor(ctorBuilder.build())
                .apply {
                    props.forEach { addProperty(it) }
                }
                .build()

            val file = FileSpec.builder(pkg, "AppConfigDto")
                .addType(type)
                .build()

            val deps = Dependencies(
                aggregating = true,
                *features.map { it.containingFile }.toTypedArray()
            )
            file.writeTo(codeGenerator, deps)
        }

        // AppConfig (domain)
        run {
            val ctorBuilder = FunSpec.constructorBuilder()
            val props = mutableListOf<PropertySpec>()

            features.forEach { feature ->
                val domainType = ClassName(pkg, feature.domainName)
                val propertyName = feature.key

                val param = ParameterSpec.builder(propertyName, domainType).build()
                ctorBuilder.addParameter(param)
                props.add(
                    PropertySpec.builder(propertyName, domainType)
                        .initializer(propertyName)
                        .build()
                )
            }

            val type = TypeSpec.classBuilder("AppConfig")
                .addModifiers(KModifier.DATA)
                .primaryConstructor(ctorBuilder.build())
                .apply {
                    props.forEach { addProperty(it) }
                }
                .build()

            val file = FileSpec.builder(pkg, "AppConfig")
                .addType(type)
                .build()

            val deps = Dependencies(
                aggregating = true,
                *features.map { it.containingFile }.toTypedArray()
            )
            file.writeTo(codeGenerator, deps)
        }
    }

    // endregion

    // region Mappers

    private fun generateMappers(
        pkg: String,
        features: List<FeatureMeta>
    ) {
        val appConfigDto = ClassName(pkg, "AppConfigDto")
        val appConfig = ClassName(pkg, "AppConfig")

        val fileBuilder = FileSpec.builder(pkg, "FeatureToggleMappers")
//            .addKdoc(
//                "Generated mappers for feature toggles.\n" +
//                        "Do not edit manually.\n"
//            )

        // Per-feature mappers
        features.forEach { feature ->
            val dtoType = ClassName(pkg, feature.dtoName)
            val domainType = ClassName(pkg, feature.domainName)

            val funSpec = FunSpec.builder("toDomain")
                .receiver(dtoType)
                .returns(domainType)
                .addStatement(
                    "return %T(" +
                            buildString {
                                append("enabled = enabled")
                                feature.payloadFields.forEach { field ->
                                    append(", ${field.name} = ${field.name}")
                                }
                            } +
                            ")",
                    domainType
                )
                .build()

            fileBuilder.addFunction(funSpec)
        }

        // AppConfigDto -> AppConfig
        run {
            val funSpec = FunSpec.builder("toDomain")
                .receiver(appConfigDto)
                .returns(appConfig)

            val argsJoined = features.joinToString(", ") { feature ->
                val propertyName = feature.key
                "$propertyName = $propertyName.toDomain()"
            }

            funSpec.addStatement("return %T($argsJoined)", appConfig)

            fileBuilder.addFunction(funSpec.build())
        }

        val deps = Dependencies(
            aggregating = true,
            *features.map { it.containingFile }.toTypedArray()
        )
        fileBuilder.build().writeTo(codeGenerator, deps)
    }

    // endregion
}



