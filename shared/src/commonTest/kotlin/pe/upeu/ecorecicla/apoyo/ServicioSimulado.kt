package pe.upeu.ecorecicla.apoyo

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.io.IOException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.double
import kotlinx.serialization.json.long

/**
 * EcoAPI simulado para MockEngine: una ruta abierta con dos puntos, los
 * pesajes del servidor y un interruptor de red. [eventos] guarda el orden
 * de las peticiones para comprobar que se sube antes de bajar.
 */
class ServicioSimulado {

    var conRed = true
    var rutaEstado = "ABIERTA"
    var sinRuta = false
    /** Materiales que el servicio rechaza con 409 (por ejemplo, ruta cerrada). */
    val materialesRechazados = mutableSetOf<String>()
    val eventos = mutableListOf<String>()
    val pesajes = mutableListOf<P>()
    private var siguienteId = 100L

    data class P(val id: Long, val puntoId: Long, val material: String, val bruto: Double, val tara: Double,
                 val operario: String, var anulado: Boolean = false, var motivo: String? = null)

    private val json = headersOf(HttpHeaders.ContentType, "application/json")

    val motor = MockEngine { peticion -> responder(this, peticion) }

    fun responder(scope: MockRequestHandleScope, p: HttpRequestData): HttpResponseData {
        if (!conRed) throw IOException("Modo avion")
        val ruta = p.url.encodedPath.removePrefix("/api/v1/")
        eventos += "${p.method.value} $ruta"
        return when {
            ruta == "rutas/hoy" && sinRuta -> error(scope, 404, "No tiene una ruta asignada hoy")
            ruta == "rutas/hoy" -> scope.respond(
                """{"id":1,"fecha":"2026-10-28","estado":"$rutaEstado","puntos":[
                  {"id":10,"razonSocial":"Plaza Norte SAC","direccion":"Av. Tupac 123","zona":"Norte","materialHabitual":"Carton","secuencia":1,"atendido":false},
                  {"id":11,"razonSocial":"Mega Centro SA","direccion":"Jr. Lima 456","zona":"Centro","materialHabitual":"Plastico","secuencia":2,"atendido":false}
                ]}""", HttpStatusCode.OK, json
            )
            ruta.startsWith("puntos/") -> {
                val puntoId = ruta.split("/")[1].toLong()
                val lista = pesajes.filter { it.puntoId == puntoId }.joinToString(",") { aJson(it) }
                scope.respond("""{"contenido":[$lista],"pagina":0,"tamanio":50,"totalElementos":0,"totalPaginas":1,"ultima":true}""",
                    HttpStatusCode.OK, json)
            }
            ruta == "pesajes" && p.method == HttpMethod.Post -> {
                val cuerpo = Json.parseToJsonElement((p.body as TextContent).text).jsonObject
                val material = cuerpo.getValue("material").jsonPrimitive.content
                val bruto = cuerpo.getValue("pesoBruto").jsonPrimitive.double
                val tara = cuerpo.getValue("tara").jsonPrimitive.double
                when {
                    material in materialesRechazados -> error(scope, 409, "La ruta está cerrada: no se pueden registrar pesajes")
                    bruto - tara <= 0 -> error(scope, 400, "Datos invalidos", """{"tara":"El peso neto debe ser mayor que cero"}""")
                    else -> {
                        val nuevo = P(siguienteId++, cuerpo.getValue("puntoId").jsonPrimitive.long, material, bruto, tara, "operario1")
                        pesajes += nuevo
                        scope.respond(aJson(nuevo), HttpStatusCode.Created, json)
                    }
                }
            }
            ruta.startsWith("pesajes/") && p.method == HttpMethod.Put -> {
                val id = ruta.removePrefix("pesajes/").toLong()
                val actual = pesajes.firstOrNull { it.id == id } ?: return error(scope, 404, "Pesaje no encontrado")
                if (actual.operario != "operario1") return error(scope, 403, "Solo puede corregir sus propios pesajes del día")
                val cuerpo = Json.parseToJsonElement((p.body as TextContent).text).jsonObject
                val cambiado = actual.copy(bruto = cuerpo.getValue("pesoBruto").jsonPrimitive.double,
                    tara = cuerpo.getValue("tara").jsonPrimitive.double)
                pesajes[pesajes.indexOf(actual)] = cambiado
                scope.respond(aJson(cambiado), HttpStatusCode.OK, json)
            }
            ruta.startsWith("pesajes/") && p.method == HttpMethod.Delete -> {
                val id = ruta.removePrefix("pesajes/").toLong()
                pesajes.first { it.id == id }.apply { anulado = true; motivo = p.url.parameters["motivo"] }
                scope.respond(ByteReadChannel.Empty, HttpStatusCode.NoContent)
            }
            else -> error(scope, 404, "Ruta desconocida $ruta")
        }
    }

    fun error(scope: MockRequestHandleScope, status: Int, mensaje: String, campos: String = "null"): HttpResponseData =
        scope.respond(
            """{"timestamp":"2026-10-28T10:00:00","status":$status,"error":"x","message":"$mensaje","path":"/api/v1","validationErrors":$campos}""",
            HttpStatusCode.fromValue(status), json
        )

    private fun aJson(p: P) =
        """{"id":${p.id},"rutaId":1,"puntoId":${p.puntoId},"fechaHora":"2026-10-28T09:00:00","material":"${p.material}",""" +
            """"pesoBruto":${p.bruto},"tara":${p.tara},"pesoNeto":${p.bruto - p.tara},"observacion":null,""" +
            """"operario":"${p.operario}","anulado":${p.anulado},"motivoAnulacion":${p.motivo?.let { "\"$it\"" } ?: "null"}}"""
}
