package pe.upeu.ecorecicla

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import pe.upeu.ecorecicla.domain.plataforma.MonitorDeRed

/** Emite true/false cuando el telefono gana o pierde conexion (modo avion, sotano). */
class MonitorDeRedAndroid(contexto: Context) : MonitorDeRed {

    private val conectividad = contexto.getSystemService(ConnectivityManager::class.java)

    override val conectado: Flow<Boolean> = callbackFlow {
        fun estadoActual(): Boolean = conectividad.activeNetwork
            ?.let { conectividad.getNetworkCapabilities(it) }
            ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true

        trySend(estadoActual())
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { trySend(true) }
            override fun onLost(network: Network) { trySend(estadoActual()) }
        }
        conectividad.registerDefaultNetworkCallback(callback)
        awaitClose { conectividad.unregisterNetworkCallback(callback) }
    }.distinctUntilChanged()
}
