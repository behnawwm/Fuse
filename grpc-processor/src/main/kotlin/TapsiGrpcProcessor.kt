import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.squareup.kotlinpoet.ANY
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.writeTo
import kotlin.reflect.KClass

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class TapsiGrpc

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class TapsiGrpcEndpoint(
    val methodName: String,     // maps to Wire client method
    val requestType: KClass<*>, // EmptyRequest::class, etc
    val responseType: KClass<*> // String::class, etc (or wire message)
)

class TapsiGrpcProcessor(
    private val environment: SymbolProcessorEnvironment
) : SymbolProcessor {

    private val codeGenerator = environment.codeGenerator
    private val logger = environment.logger

    override fun process(resolver: Resolver): List<KSAnnotated> {

        // find all interfaces annotated with @TapsiGrpc
        val grpcInterfaces = resolver
            .getSymbolsWithAnnotation(TapsiGrpc::class.qualifiedName!!)
            .filterIsInstance<KSClassDeclaration>()

        grpcInterfaces.forEach { generateImpl(it, resolver) }

        return emptyList()
    }

    private fun generateImpl(
        interfaceDecl: KSClassDeclaration,
        resolver: Resolver
    ) {
        val packageName = interfaceDecl.packageName.asString()
        val interfaceName = interfaceDecl.simpleName.asString()
        val implName = "${interfaceName}Impl"

        val methods = interfaceDecl.getAllFunctions()
            .filter { it.hasAnnotation(TapsiGrpcEndpoint::class.qualifiedName!!) }
            .toList()

        generateImplFile(packageName, interfaceName, implName, methods)
    }

    fun KSAnnotated.findAnnotation(qName: String): KSAnnotation? {
        return annotations.firstOrNull {
            it.annotationType.resolve().declaration.qualifiedName?.asString() == qName
        }
    }

    fun KSAnnotated.hasAnnotation(qName: String): Boolean = findAnnotation(qName) != null


    fun generateImplFile(
        packageName: String,
        interfaceName: String,
        implName: String,
        methods: List<KSFunctionDeclaration>
    ) {
        val grpcClientClass = ClassName("com.squareup.wire.grpc", "GrpcClient")
        val className = ClassName(packageName, implName)
        val interfaceClassName = ClassName(packageName, interfaceName)

        val typeSpec = TypeSpec.classBuilder(className)
            .addSuperinterface(interfaceClassName)
            .primaryConstructor(
                FunSpec.constructorBuilder()
                    .addParameter("grpcClient", grpcClientClass)
                    .build()
            )
            .addProperty(
                PropertySpec.builder(
                    "client",
                    ANY, // replaced by actual service client later
                    KModifier.PRIVATE
                )
                    .initializer("grpcClient.create(%T::class)", guessServiceClient(interfaceName))
                    .build()
            )
            .apply {
                methods.forEach { function ->
                    addFunction(generateEndpointFunction(function))
                }
            }
            .build()

        FileSpec.builder(packageName, implName)
            .addType(typeSpec)
            .build()
            .writeTo(codeGenerator, Dependencies(false))
    }


    private fun generateEndpointFunction(
        function: KSFunctionDeclaration
    ): FunSpec {

        val ann = function.annotations.first {
            it.shortName.asString() == "TapsiGrpcEndpoint"
        }

        val methodName = ann.arguments.first { it.name?.asString() == "methodName" }.value as String
        val requestType = ann.arguments.first { it.name?.asString() == "requestType" }.value as KSType
        val responseType = ann.arguments.first { it.name?.asString() == "responseType" }.value as KSType

        return FunSpec.builder(function.simpleName.asString())
            .addModifiers(KModifier.OVERRIDE)
            .addModifiers(KModifier.SUSPEND)
            .returns(
                ClassName("kotlin", "Result")
                    .parameterizedBy(responseType.toClassName())
            )
            .addStatement("return runCatching {")
            .addStatement("  val call = client.%L()", methodName)
            .addStatement("  call.executeWithDecoder(%T())", requestType.toClassName())
            .addStatement("}")
            .build()
    }

    private fun guessServiceClient(interfaceName: String): ClassName {
        return ClassName("your.grpc.client.pkg", interfaceName.removeSuffix("Grpc") + "ServiceClient")
    }


}



