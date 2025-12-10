import com.squareup.wire.GrpcClient
import mamad.v1.EmptyRequest
import mamad.v1.MamadServiceClient
import mamad.v1.StringResponse

@TapsiGrpc
interface MamadGrpc {

    @TapsiGrpcEndpoint(MamadServiceClient)
    suspend fun <T : Any> functionNameMamad(request: T): Result<StringResponse>

}


class MamadGrpcImpl(
    grpcClient: GrpcClient
) : MamadGrpc {
    private val client = grpcClient.create(MamadServiceClient::class)

    override suspend fun functionNameMamad(request: EmptyRequest): Result<StringResponse> {
        return runCatching {
            client.functionName().executeWithDecoder(request)
        }
    }
}