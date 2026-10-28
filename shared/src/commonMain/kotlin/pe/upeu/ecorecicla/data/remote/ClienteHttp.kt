package pe.upeu.ecorecicla.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.DefaultRequest
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.bearer
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.logging.SIMPLE
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.encodedPath
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import pe.upeu.ecorecicla.data.local.AlmacenSeguro

/** Lector JSON tolerante: el servicio puede enviar campos que la app no usa. */
internal val JsonEco = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
}

/**
 * Unico HttpClient de la app (Koin lo registra como single). Aqui se
 * configuran una sola vez la direccion base, los tiempos de espera, la
 * negociacion de contenido y el punto unico donde se agrega la cabecera
 * Authorization: ningun repositorio la escribe a mano.
 */
fun crearHttpClient(
    motor: HttpClientEngine,
    almacen: AlmacenSeguro,
    esDepuracion: Boolean,
    registro: Logger = Logger.SIMPLE
): HttpClient = HttpClient(motor) {

    // Un 4xx o 5xx se lanza como ResponseException y lo traduce MapeoErrores.
    expectSuccess = true

    install(ContentNegotiation) { json(JsonEco) }

    install(DefaultRequest) {
        url(ApiConfig.URL_BASE)
        contentType(ContentType.Application.Json)
    }

    install(HttpTimeout) {
        connectTimeoutMillis = ApiConfig.TIEMPO_CONEXION_MS
        requestTimeoutMillis = ApiConfig.TIEMPO_PETICION_MS
    }

    install(Auth) {
        bearer {
            loadTokens {
                almacen.leer(AlmacenSeguro.TOKEN)?.let { BearerTokens(it, "") }
            }
            // El token viaja desde la primera peticion, sin esperar un 401.
            sendWithoutRequest { peticion ->
                !peticion.url.encodedPath.endsWith("/auth/login")
            }
        }
    }

    install(Logging) {
        logger = registro
        // Registro reducido: cabeceras solo en depuracion y nunca el token.
        level = if (esDepuracion) LogLevel.HEADERS else LogLevel.NONE
        sanitizeHeader { nombre -> nombre == HttpHeaders.Authorization }
    }
}
