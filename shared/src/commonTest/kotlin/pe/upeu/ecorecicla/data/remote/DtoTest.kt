package pe.upeu.ecorecicla.data.remote

import pe.upeu.ecorecicla.data.remote.dto.PaginaDto
import pe.upeu.ecorecicla.data.remote.dto.PesajeDto
import pe.upeu.ecorecicla.data.remote.dto.RutaDto
import pe.upeu.ecorecicla.data.remote.dto.aDominio
import pe.upeu.ecorecicla.domain.model.EstadoPesaje
import pe.upeu.ecorecicla.domain.model.EstadoRuta
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/** Conversion de DTO: el listado llega en el envoltorio de paginacion, no como lista. */
class DtoTest {

    @Test
    fun unPesajeAnuladoSeConvierteEnEstadoAnuladoYNoPendiente() {
        val json = """{"contenido":[{"id":7,"rutaId":1,"puntoId":10,"fechaHora":"2026-10-28T09:00:00",
            "material":"Carton","pesoBruto":50.5,"tara":2.5,"pesoNeto":48.0,"observacion":"humedo",
            "operario":"operario1","anulado":true,"motivoAnulacion":"Pesaje duplicado","campoNuevo":123}],
            "pagina":0,"tamanio":20,"totalElementos":1,"totalPaginas":1,"ultima":true}"""

        val pagina = JsonEco.decodeFromString(PaginaDto.serializer(PesajeDto.serializer()), json)
        val pesaje = pagina.contenido.single().aDominio()

        assertEquals(EstadoPesaje.Anulado("Pesaje duplicado"), pesaje.estado)
        assertEquals(48.0, pesaje.pesoNeto)
        assertEquals("operario1", pesaje.operario)
        assertFalse(pesaje.pendiente)
        assertEquals(1L, pagina.totalElementos)
    }

    @Test
    fun laRutaConvierteSuEstadoYSusPuntos() {
        val json = """{"id":1,"fecha":"2026-10-28","estado":"CERRADA","puntos":[{"id":10,"razonSocial":"A",
            "direccion":"B","zona":"Norte","materialHabitual":"Vidrio","secuencia":3,"atendido":true}]}"""

        val dto = JsonEco.decodeFromString(RutaDto.serializer(), json)

        assertEquals(EstadoRuta.CERRADA, dto.aDominio().estado)
        assertEquals(3, dto.puntos.single().aDominio().secuencia)
    }
}
