package pe.upeu.ecorecicla.data.local

/**
 * Almacen cifrado del dispositivo. En Android lo implementa
 * EncryptedSharedPreferences con la llave en el Android Keystore.
 */
interface AlmacenSeguro {
    fun guardar(clave: String, valor: String)
    fun leer(clave: String): String?
    fun borrar(clave: String)

    companion object {
        const val TOKEN = "jwt"
        const val PERFIL = "perfil"
        const val ULTIMO_USUARIO = "ultimo_usuario"
    }
}
