package pe.upeu.ecorecicla.apoyo

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import pe.upeu.ecorecicla.db.EcoDb
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * SC-C: el emulador ya tiene ecorutas.db en la version 1 (sin humedad). La
 * migracion 1.sqm agrega la columna sin perder los pesajes, ni los pendientes.
 */
class MigracionHumedadTest {

    @Test
    fun laVersion1MigraALa2ConservandoLosPesajes() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        driver.execute(
            null,
            """CREATE TABLE pesaje (id INTEGER NOT NULL PRIMARY KEY, rutaId INTEGER NOT NULL, puntoId INTEGER NOT NULL,
               fechaHora TEXT NOT NULL, material TEXT NOT NULL, pesoBruto REAL NOT NULL, tara REAL NOT NULL,
               pesoNeto REAL NOT NULL, observacion TEXT, operario TEXT NOT NULL, anulado INTEGER NOT NULL DEFAULT 0,
               motivoAnulacion TEXT, pendiente INTEGER NOT NULL DEFAULT 0, operacion TEXT, rechazo TEXT)""",
            0
        )
        driver.execute(
            null,
            "INSERT INTO pesaje VALUES (-1, 1, 10, '2026-10-28T10:00:00', 'Carton', 40.0, 4.0, 36.0, NULL, 'operario1', 0, NULL, 1, 'CREAR', NULL)",
            0
        )

        EcoDb.Schema.migrate(driver, oldVersion = 1, newVersion = EcoDb.Schema.version)
        val pesaje = EcoDb(driver).ecoQueries.pesajePorId(-1).executeAsOne()

        assertEquals(2L, EcoDb.Schema.version)
        assertEquals("Carton", pesaje.material)
        assertEquals(1L, pesaje.pendiente, "el pendiente sigue esperando su envio")
        assertNull(pesaje.humedadEstimada)
    }
}
