import com.squareup.kotlinpoet.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

object CodeGenerators {
    
    fun generateNullableDto(feature: FeatureMeta): TypeSpec {
        val ctorBuilder = FunSpec.constructorBuilder()
        val properties = mutableListOf<PropertySpec>()
        
        feature.fields.forEach { field ->
            val fieldType = if (isNestedType(field, feature)) {
                ClassName("", "${getSimpleTypeName(field.typeName)}ConfigDto").copy(nullable = true)
            } else {
                field.typeName.copy(nullable = true)
            }
            
            val param = ParameterSpec.builder(field.name, fieldType)
                .addAnnotation(
                    AnnotationSpec.builder(SerialName::class)
                        .addMember("%S", field.name)
                        .build()
                )
                .build()
            
            ctorBuilder.addParameter(param)
            properties.add(
                PropertySpec.builder(field.name, fieldType)
                    .initializer(field.name)
                    .build()
            )
        }
        
        val typeBuilder = TypeSpec.classBuilder(feature.dtoName)
            .addAnnotation(Serializable::class)
            .addModifiers(KModifier.DATA)
            .primaryConstructor(ctorBuilder.build())
        
        properties.forEach { typeBuilder.addProperty(it) }
        
        // Add nested DTOs as inner classes
        feature.nestedConfigs.forEach { nested ->
            val nestedDto = generateNestedDto(nested)
            typeBuilder.addType(nestedDto)
        }
        
        return typeBuilder.build()
    }
    
    private fun generateNestedDto(nested: NestedConfig): TypeSpec {
        val ctorBuilder = FunSpec.constructorBuilder()
        val properties = mutableListOf<PropertySpec>()
        
        nested.fields.forEach { field ->
            val nullableType = field.typeName.copy(nullable = true)
            
            val param = ParameterSpec.builder(field.name, nullableType)
                .addAnnotation(
                    AnnotationSpec.builder(SerialName::class)
                        .addMember("%S", field.name)
                        .build()
                )
                .build()
            
            ctorBuilder.addParameter(param)
            properties.add(
                PropertySpec.builder(field.name, nullableType)
                    .initializer(field.name)
                    .build()
            )
        }
        
        val typeBuilder = TypeSpec.classBuilder("${nested.className}ConfigDto")
            .addAnnotation(Serializable::class)
            .addModifiers(KModifier.DATA)
            .primaryConstructor(ctorBuilder.build())
        
        properties.forEach { typeBuilder.addProperty(it) }
        
        return typeBuilder.build()
    }
    
    fun generateDomainModel(feature: FeatureMeta): TypeSpec {
        val ctorBuilder = FunSpec.constructorBuilder()
        val properties = mutableListOf<PropertySpec>()
        
        feature.fields.forEach { field ->
            val fieldType = if (isNestedType(field, feature)) {
                // Use nested class reference within the same class
                val nestedConfigName = "${getSimpleTypeName(field.typeName)}Config"
                ClassName("", nestedConfigName)
            } else {
                field.typeName
            }
            
            ctorBuilder.addParameter(field.name, fieldType)
            properties.add(
                PropertySpec.builder(field.name, fieldType)
                    .initializer(field.name)
                    .build()
            )
        }
        
        // Generate companion object with default
        val defaultArgs = feature.fields.joinToString(", ") { field ->
            val defaultValue = if (field.name == "enabled") {
                feature.enabledDefault.toString()
            } else if (isNestedType(field, feature)) {
                "${getSimpleTypeName(field.typeName)}Config.default"
            } else {
                field.defaultValue
            }
            "${field.name} = $defaultValue"
        }
        
        val companionObject = TypeSpec.companionObjectBuilder()
            .addProperty(
                PropertySpec.builder("default", ClassName("", feature.domainName))
                    .initializer("${feature.domainName}($defaultArgs)")
                    .build()
            )
            .build()
        
        return TypeSpec.classBuilder(feature.domainName)
            .addModifiers(KModifier.DATA)
            .primaryConstructor(ctorBuilder.build())
            .apply { properties.forEach { addProperty(it) } }
            .addType(companionObject)
            .apply { 
                // Add nested domain models as public classes
                feature.nestedConfigs.forEach { nested ->
                    val nestedDomainType = generateNestedDomainModel(nested)
                    addType(nestedDomainType)
                }
            }
            .build()
    }
    
    fun generateNestedDomainModel(nested: NestedConfig): TypeSpec {
        val ctorBuilder = FunSpec.constructorBuilder()
        val properties = mutableListOf<PropertySpec>()
        
        nested.fields.forEach { field ->
            ctorBuilder.addParameter(field.name, field.typeName)
            properties.add(
                PropertySpec.builder(field.name, field.typeName)
                    .initializer(field.name)
                    .build()
            )
        }
        
        val domainName = "${nested.className}Config"
        val defaultArgs = nested.fields.joinToString(", ") { field ->
            "${field.name} = ${field.defaultValue}"
        }
        
        val companionObject = TypeSpec.companionObjectBuilder()
            .addProperty(
                PropertySpec.builder("default", ClassName("", domainName))
                    .initializer("$domainName($defaultArgs)")
                    .build()
            )
            .build()
        
        return TypeSpec.classBuilder(domainName)
            .addModifiers(KModifier.DATA)
            .primaryConstructor(ctorBuilder.build())
            .apply { properties.forEach { addProperty(it) } }
            .addType(companionObject)
            .build()
    }
    
    fun generateUseCase(feature: FeatureMeta, packageName: String): TypeSpec {
        val useCaseName = "Is${feature.domainName}EnabledUseCase"
        
        val executeFunction = FunSpec.builder("execute")
            .returns(Boolean::class)
            .addStatement("val isRemoteFeatureToggleEnabled =")
            .addStatement("    enabledFeaturesDataStore.latestFetchedEnabledFeatures?.${feature.featureName.replaceFirstChar { it.lowercase() }}?.enabled ?: false")
            .addStatement("val isLocalFeatureToggleEnabled = isFeatureEnabled(FeatureToggles.${feature.enumName})")
            .addStatement("")
            .addStatement("return isLocalFeatureToggleEnabled && isRemoteFeatureToggleEnabled")
            .build()
        
        return TypeSpec.classBuilder(useCaseName)
            .primaryConstructor(
                FunSpec.constructorBuilder()
                    .addParameter("enabledFeaturesDataStore", ClassName("", "EnabledFeaturesDataStore"))
                    .build()
            )
            .addProperty(
                PropertySpec.builder("enabledFeaturesDataStore", ClassName("", "EnabledFeaturesDataStore"))
                    .initializer("enabledFeaturesDataStore")
                    .addModifiers(KModifier.PRIVATE)
                    .build()
            )
            .addFunction(executeFunction)
            .build()
    }
    
    fun generateMapper(feature: FeatureMeta, packageName: String): FunSpec {
        val dtoType = ClassName(packageName, feature.dtoName)
        val domainType = ClassName(packageName, feature.domainName)
        
        val mappingArgs = feature.fields.joinToString(",\n        ") { field ->
            if (isNestedType(field, feature)) {
                "${field.name} = to${getSimpleTypeName(field.typeName)}Config(it.${field.name})"
            } else {
                "${field.name} = it.${field.name} ?: ${feature.domainName}.default.${field.name}"
            }
        }
        
        return FunSpec.builder("to${feature.domainName}")
            .addParameter("dto", dtoType.copy(nullable = true))
            .returns(domainType)
            .addCode(
                """
                return dto?.let {
                    ${feature.domainName}(
                        $mappingArgs
                    )
                } ?: ${feature.domainName}.default
                """.trimIndent()
            )
            .build()
    }
    
    private fun isNestedType(field: FeatureField, feature: FeatureMeta): Boolean {
        val fieldTypeName = getSimpleTypeName(field.typeName)
        return feature.nestedConfigs.any { it.className == fieldTypeName }
    }
    
    private fun getSimpleTypeName(typeName: TypeName): String {
        return typeName.toString().substringAfterLast(".")
    }
    
    fun generateNestedMapper(nested: NestedConfig, packageName: String, parentDtoName: String, parentDomainName: String): FunSpec {
        val dtoType = ClassName(packageName, "$parentDtoName.${nested.className}ConfigDto")
        val domainType = ClassName(packageName, "$parentDomainName.${nested.className}Config")
        
        val mappingArgs = nested.fields.joinToString(",\n        ") { field ->
            "${field.name} = it.${field.name} ?: ${nested.className}Config.default.${field.name}"
        }
        
        return FunSpec.builder("to${nested.className}Config")
            .addParameter("dto", dtoType.copy(nullable = true))
            .returns(domainType)
            .addCode(
                """
                return dto?.let {
                    ${nested.className}Config(
                        $mappingArgs
                    )
                } ?: ${nested.className}Config.default
                """.trimIndent()
            )
            .build()
    }
    
    fun generateFixtures(packageName: String, features: List<FeatureMeta>): TypeSpec {
        val objectBuilder = TypeSpec.objectBuilder("Fixtures")
            .addKdoc("Generated fixtures for testing and development")
        
        // Generate nested fixtures first (so they can be referenced)
        features.forEach { feature ->
            val featurePackage = "$packageName.${feature.featureName.replaceFirstChar { it.lowercase() }}"
            feature.nestedConfigs.forEach { nested ->
                val nestedDtoFixture = generateNestedDtoFixture(nested, feature, featurePackage)
                objectBuilder.addProperty(nestedDtoFixture)
                
                val nestedDomainFixture = generateNestedDomainFixture(nested, featurePackage, feature.domainName)
                objectBuilder.addProperty(nestedDomainFixture)
            }
        }
        
        // Generate main feature fixtures
        features.forEach { feature ->
            val featurePackage = "$packageName.${feature.featureName.replaceFirstChar { it.lowercase() }}"
            val dtoFixture = generateDtoFixture(feature, featurePackage)
            objectBuilder.addProperty(dtoFixture)
            
            val domainFixture = generateDomainFixture(feature, featurePackage)
            objectBuilder.addProperty(domainFixture)
        }
        
        // Generate AppConfig fixtures
        val appConfigDtoFixture = generateAppConfigDtoFixture(features, packageName)
        objectBuilder.addProperty(appConfigDtoFixture)
        
        val appConfigFixture = generateAppConfigFixture(features, packageName)
        objectBuilder.addProperty(appConfigFixture)
        
        return objectBuilder.build()
    }
    
    private fun generateDtoFixture(feature: FeatureMeta, packageName: String): PropertySpec {
        val dtoType = ClassName(packageName, feature.dtoName)
        val fixtureName = "${feature.featureName.replaceFirstChar { it.lowercase() }}ConfigDtoFixture"
        
        val args = feature.fields.joinToString(",\n        ") { field ->
            "${field.name} = ${generateFixtureValue(field, feature, true)}"
        }
        
        return PropertySpec.builder(fixtureName, dtoType)
            .initializer(
                """
                ${feature.dtoName}(
                    $args
                )
                """.trimIndent()
            )
            .build()
    }
    
    private fun generateDomainFixture(feature: FeatureMeta, packageName: String): PropertySpec {
        val domainType = ClassName(packageName, feature.domainName)
        val fixtureName = "${feature.featureName.replaceFirstChar { it.lowercase() }}ConfigFixture"
        
        val args = feature.fields.joinToString(",\n        ") { field ->
            "${field.name} = ${generateFixtureValue(field, feature, false)}"
        }
        
        return PropertySpec.builder(fixtureName, domainType)
            .initializer(
                """
                ${feature.domainName}(
                    $args
                )
                """.trimIndent()
            )
            .build()
    }
    
    private fun generateNestedDtoFixture(nested: NestedConfig, feature: FeatureMeta, packageName: String): PropertySpec {
        val dtoType = ClassName(packageName, "${feature.dtoName}.${nested.className}ConfigDto")
        val fixtureName = "${nested.className.replaceFirstChar { it.lowercase() }}ConfigDtoFixture"
        
        val args = nested.fields.joinToString(",\n        ") { field ->
            "${field.name} = ${generateSimpleFixtureValue(field, true)}"
        }
        
        return PropertySpec.builder(fixtureName, dtoType)
            .initializer(
                """
                ${feature.dtoName}.${nested.className}ConfigDto(
                    $args
                )
                """.trimIndent()
            )
            .build()
    }
    
    private fun generateNestedDomainFixture(nested: NestedConfig, packageName: String, parentDomainName: String): PropertySpec {
        val domainType = ClassName(packageName, "$parentDomainName.${nested.className}Config")
        val fixtureName = "${nested.className.replaceFirstChar { it.lowercase() }}ConfigFixture"
        
        val args = nested.fields.joinToString(",\n        ") { field ->
            "${field.name} = ${generateSimpleFixtureValue(field, false)}"
        }
        
        return PropertySpec.builder(fixtureName, domainType)
            .initializer(
                """
                ${nested.className}Config(
                    $args
                )
                """.trimIndent()
            )
            .build()
    }
    
    private fun generateAppConfigDtoFixture(features: List<FeatureMeta>, packageName: String): PropertySpec {
        val appConfigDtoType = ClassName(packageName, "AppConfigDto")
        
        val args = features.joinToString(",\n        ") { feature ->
            val fieldName = feature.featureName.replaceFirstChar { it.lowercase() }
            val fixtureName = "${fieldName}ConfigDtoFixture"
            "$fieldName = $fixtureName"
        }
        
        return PropertySpec.builder("appConfigDtoFixture", appConfigDtoType)
            .initializer(
                """
                AppConfigDto(
                    $args
                )
                """.trimIndent()
            )
            .build()
    }
    
    private fun generateAppConfigFixture(features: List<FeatureMeta>, packageName: String): PropertySpec {
        val appConfigType = ClassName(packageName, "AppConfig")
        
        val args = features.joinToString(",\n        ") { feature ->
            val fieldName = feature.featureName.replaceFirstChar { it.lowercase() }
            val fixtureName = "${fieldName}ConfigFixture"
            "$fieldName = $fixtureName"
        }
        
        return PropertySpec.builder("appConfigFixture", appConfigType)
            .initializer(
                """
                AppConfig(
                    $args
                )
                """.trimIndent()
            )
            .build()
    }
    
    private fun generateFixtureValue(field: FeatureField, feature: FeatureMeta, isDto: Boolean): String {
        return if (isNestedType(field, feature)) {
            val nestedName = getSimpleTypeName(field.typeName)
            "${nestedName.replaceFirstChar { it.lowercase() }}Config${if (isDto) "Dto" else ""}Fixture"
        } else {
            generateSimpleFixtureValue(field, isDto)
        }
    }
    
    private fun generateSimpleFixtureValue(field: FeatureField, isDto: Boolean): String {
        val typeName = field.typeName.toString()
        return when {
            typeName.contains("Boolean") -> "true"
            typeName.contains("String") -> "\"sample_${field.name}\""
            typeName.contains("Int") -> "42"
            typeName.contains("Long") -> "1000L"
            typeName.contains("Double") -> "3.14"
            typeName.contains("Float") -> "2.5f"
            else -> "null"
        }
    }
}
