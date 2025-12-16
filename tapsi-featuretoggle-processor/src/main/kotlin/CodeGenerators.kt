import com.squareup.kotlinpoet.*
import kotlinx.serialization.Serializable

object CodeGenerators {
    
    fun generateNullableDto(feature: FeatureMeta): TypeSpec {
        val ctorBuilder = FunSpec.constructorBuilder()
        val properties = mutableListOf<PropertySpec>()
        
        feature.fields.forEach { field ->
            val fieldType = if (isNestedType(field, feature)) {
                // For nested types, use the nested DTO type as inner class
                ClassName("", "${getSimpleTypeName(field.typeName)}ConfigDto").copy(nullable = true)
            } else {
                field.typeName.copy(nullable = true)
            }
            
            ctorBuilder.addParameter(field.name, fieldType)
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
            ctorBuilder.addParameter(field.name, nullableType)
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
                // For nested types, use the domain config type
                ClassName("", "${getSimpleTypeName(field.typeName)}Config")
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
            "${field.name} = ${field.defaultValue}"
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
    
    fun generateMapper(feature: FeatureMeta, packageName: String): FunSpec {
        val dtoType = ClassName(packageName, feature.dtoName)
        val domainType = ClassName(packageName, feature.domainName)
        
        val mappingArgs = feature.fields.joinToString(",\n            ") { field ->
            if (isNestedType(field, feature)) {
                "${field.name} = ${field.name}.to${getSimpleTypeName(field.typeName)}Config()"
            } else {
                "${field.name} = it.${field.name} ?: ${feature.domainName}.default.${field.name}"
            }
        }
        
        return FunSpec.builder("to${feature.domainName}")
            .receiver(dtoType.copy(nullable = true))
            .returns(domainType)
            .addStatement(
                """
                return this?.let {
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
    
    fun generateNestedMapper(nested: NestedConfig, packageName: String, parentDtoName: String): FunSpec {
        val dtoType = ClassName(packageName, "$parentDtoName.${nested.className}ConfigDto")
        val domainType = ClassName(packageName, "${nested.className}Config")
        
        val mappingArgs = nested.fields.joinToString(",\n            ") { field ->
            "${field.name} = it.${field.name} ?: ${nested.className}Config.default.${field.name}"
        }
        
        return FunSpec.builder("to${nested.className}Config")
            .receiver(dtoType.copy(nullable = true))
            .returns(domainType)
            .addStatement(
                """
                return this?.let {
                    ${nested.className}Config(
                        $mappingArgs
                    )
                } ?: ${nested.className}Config.default
                """.trimIndent()
            )
            .build()
    }
}
