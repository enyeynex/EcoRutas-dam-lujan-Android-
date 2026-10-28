package pe.upeu.ecorecicla.domain.plataforma

import kotlinx.coroutines.flow.Flow

/** Estado de la conexion del dispositivo; lo implementa cada plataforma. */
interface MonitorDeRed {
    val conectado: Flow<Boolean>
}
