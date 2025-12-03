@TapsiGrpc
interface MyExample {

    @TapsiGrpcEndpoint(
        methodName = "helloWorld",
        requestType = EmptyRequest::class,
        responseType = String::class
    )
    suspend fun helloWorld(): Result<String>
}