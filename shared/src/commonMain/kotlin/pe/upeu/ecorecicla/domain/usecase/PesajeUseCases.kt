package pe.upeu.ecorecicla.domain.usecase

import pe.upeu.ecorecicla.domain.model.DatosPesaje
import pe.upeu.ecorecicla.domain.model.ErrorApi
import pe.upeu.ecorecicla.domain.model.Pesaje
import pe.upeu.ecorecicla.domain.repository.AuthRepository
import pe.upeu.ecorecicla.domain.repository.PesajeRepository

/**
 * Validacion local del formulario (la misma que hace el servicio). Usa las
 * mismas claves de campo que validationErrors para que los mensajes del
 * servidor y los locales caigan bajo el mismo campo.
 */
fun validarPesaje(
    material: String, pesoBruto: String, tara: String, observacion: String,
    humedad: String = ""
): Result<DatosPesaje> {
    val bruto = pesoBruto.replace(',', '.').toDoubleOrNull()
    val taraValor = tara.replace(',', '.').toDoubleOrNull()
    // SC-C: la humedad es opcional; si se escribe, debe estar entre 0 y 100.
    val humedadValor = humedad.trim().replace(',', '.').ifBlank { null }?.toDoubleOrNull()
    val campos = buildMap {
        if (material.isBlank()) put("material", "El material es obligatorio")
        when {
            bruto == null || !bruto.isFinite() -> put("pesoBruto", "Ingresa el peso bruto en kg")
            bruto <= 0 -> put("pesoBruto", "El peso bruto debe ser mayor que cero")
        }
        when {
            taraValor == null || !taraValor.isFinite() -> put("tara", "Ingresa la tara en kg")
            taraValor < 0 -> put("tara", "La tara no puede ser negativa")
            bruto != null && bruto > 0 && bruto - taraValor <= 0 ->
                put("tara", "El peso neto (bruto - tara) debe ser mayor que cero")
        }
        if (humedad.isNotBlank() && (humedadValor == null || humedadValor < 0 || humedadValor > 100)) {
            put("humedadEstimada", MENSAJE_HUMEDAD)
        }
    }
    if (campos.isNotEmpty()) return Result.failure(ErrorApi.Validacion(campos))
    return Result.success(
        DatosPesaje(material.trim(), bruto!!, taraValor!!, observacion.trim().ifBlank { null }, humedadValor)
    )
}

class RegistrarPesajeUseCase(
    private val pesajes: PesajeRepository,
    private val auth: AuthRepository
) {
    suspend operator fun invoke(
        rutaId: Long, puntoId: Long,
        material: String, pesoBruto: String, tara: String, observacion: String,
        humedad: String = ""
    ): Result<Pesaje> {
        val datos = validarPesaje(material, pesoBruto, tara, observacion, humedad).getOrElse { return Result.failure(it) }
        val operario = auth.sesionGuardada()?.usuario ?: auth.ultimoUsuario().orEmpty()
        return resultadoDe { pesajes.registrar(rutaId, puntoId, datos, operario) }
    }
}

class CorregirPesajeUseCase(private val pesajes: PesajeRepository) {
    suspend operator fun invoke(
        id: Long, material: String, pesoBruto: String, tara: String, observacion: String,
        humedad: String = ""
    ): Result<Pesaje> {
        val datos = validarPesaje(material, pesoBruto, tara, observacion, humedad).getOrElse { return Result.failure(it) }
        return resultadoDe { pesajes.corregir(id, datos) }
    }
}

class AnularPesajeUseCase(private val pesajes: PesajeRepository) {
    suspend operator fun invoke(id: Long, motivo: String): Result<Unit> {
        if (motivo.isBlank()) {
            return Result.failure(ErrorApi.Validacion(mapOf("motivo" to "Indica el motivo de la anulación")))
        }
        return resultadoDe { pesajes.anular(id, motivo.trim()) }
    }
}

class DescartarPesajeUseCase(private val pesajes: PesajeRepository) {
    suspend operator fun invoke(id: Long): Result<Unit> = resultadoDe { pesajes.descartar(id) }
}

const val MENSAJE_HUMEDAD = "La humedad debe estar entre 0 y 100"
