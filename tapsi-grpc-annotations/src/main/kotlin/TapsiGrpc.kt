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
