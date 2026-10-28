package pe.upeu.ecorecicla.domain.usecase

import kotlin.coroutines.cancellation.CancellationException

/** runCatching que no se traga la cancelacion de corrutinas. */
internal suspend fun <T> resultadoDe(bloque: suspend () -> T): Result<T> =
    try {
        Result.success(bloque())
    } catch (cancelacion: CancellationException) {
        throw cancelacion
    } catch (fallo: Exception) {
        Result.failure(fallo)
    }
