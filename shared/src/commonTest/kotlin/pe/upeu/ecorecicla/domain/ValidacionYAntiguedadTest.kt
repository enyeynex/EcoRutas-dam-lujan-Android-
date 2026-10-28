package pe.upeu.ecorecicla.domain

import pe.upeu.ecorecicla.domain.model.ErrorApi
import pe.upeu.ecorecicla.domain.usecase.validarPesaje
import pe.upeu.ecorecicla.presentation.listado.NUNCA_SINCRONIZADO
import pe.upeu.ecorecicla.presentation.listado.textoAntiguedad
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ValidacionYAntiguedadTest {

    @Test
    fun elPesoNetoDebeSerMayorQueCeroYElErrorCaeBajoLaTara() {
        val fallo = assertIs<ErrorApi.Validacion>(validarPesaje("Carton", "10", "10", "").exceptionOrNull())
        assertEquals("El peso neto (bruto - tara) debe ser mayor que cero", fallo.campos["tara"])
    }

    @Test
    fun cadaCampoInvalidoTieneSuMensaje() {
        val fallo = assertIs<ErrorApi.Validacion>(validarPesaje("", "abc", "-1", "").exceptionOrNull())
        assertEquals(setOf("material", "pesoBruto", "tara"), fallo.campos.keys)
    }

    @Test
    fun aceptaComaDecimal() {
        assertEquals(12.5, validarPesaje("Papel", "12,5", "0", "").getOrThrow().pesoBruto)
    }

    @Test
    fun antiguedadEnLenguajeNatural() {
        val ahora = 10L * 24 * 3_600_000
        assertEquals(NUNCA_SINCRONIZADO, textoAntiguedad(null, ahora))
        assertEquals("Actualizado hace un momento", textoAntiguedad(ahora - 10_000, ahora))
        assertEquals("Actualizado hace 5 minutos", textoAntiguedad(ahora - 5 * 60_000, ahora))
        assertEquals("Actualizado hace 2 horas", textoAntiguedad(ahora - 2 * 3_600_000, ahora))
        assertEquals("Actualizado hace 1 día", textoAntiguedad(ahora - 25 * 3_600_000, ahora))
    }
}
