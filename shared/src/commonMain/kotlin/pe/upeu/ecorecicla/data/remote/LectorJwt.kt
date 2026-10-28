package pe.upeu.ecorecicla.data.remote

import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Lee el claim exp del JWT. La firma garantiza que nadie altero el contenido
 * pero no lo oculta: la app puede leer la carga sin conocer la clave.
 */
@OptIn(ExperimentalEncodingApi::class)
fun expiracionDelToken(token: String): Long? = try {
    val carga = token.split(".")[1]
    val json = Base64.UrlSafe
        .withPadding(Base64.PaddingOption.ABSENT_OPTIONAL)
        .decode(carga)
        .decodeToString()
    JsonEco.parseToJsonElement(json).jsonObject["exp"]?.jsonPrimitive?.longOrNull
} catch (fallo: Exception) {
    null
}
