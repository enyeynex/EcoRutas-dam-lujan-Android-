package pe.upeu.ecorecicla

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import pe.upeu.ecorecicla.data.local.AlmacenSeguro

/**
 * Preferencias cifradas: clave y valor se cifran con una llave que custodia
 * el Android Keystore. El token nunca queda en texto plano en el telefono.
 */
@Suppress("DEPRECATION") // security-crypto 1.1.0 marca la API como deprecada, pero sigue funcionando.
class AlmacenSeguroAndroid(contexto: Context) : AlmacenSeguro {

    private val llave = MasterKey.Builder(contexto)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        contexto, "sesion_segura", llave,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    override fun guardar(clave: String, valor: String) = prefs.edit().putString(clave, valor).apply()
    override fun leer(clave: String): String? = prefs.getString(clave, null)
    override fun borrar(clave: String) = prefs.edit().remove(clave).apply()
}
