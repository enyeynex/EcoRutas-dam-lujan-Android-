package pe.upeu.ecorecicla.apoyo

import pe.upeu.ecorecicla.data.local.AlmacenSeguro
import pe.upeu.ecorecicla.domain.plataforma.Reloj
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

class FakeAlmacenSeguro : AlmacenSeguro {
    val valores = mutableMapOf<String, String>()
    override fun guardar(clave: String, valor: String) { valores[clave] = valor }
    override fun leer(clave: String): String? = valores[clave]
    override fun borrar(clave: String) { valores.remove(clave) }
}

class RelojFijo(var ms: Long = 1_000_000L) : Reloj {
    override fun milisegundos(): Long = ms
    override fun fechaHoraLocal(): String = "2026-10-28T10:00:00"
}

/** JWT con la carga indicada (la firma no se verifica en la app). */
@OptIn(ExperimentalEncodingApi::class)
fun jwtDePrueba(usuario: String, expSegundos: Long): String {
    val b64 = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT)
    val cabecera = b64.encode("""{"alg":"HS256"}""".encodeToByteArray())
    val carga = b64.encode("""{"sub":"$usuario","exp":$expSegundos}""".encodeToByteArray())
    return "$cabecera.$carga.firma"
}
