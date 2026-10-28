package pe.upeu.ecorecicla.apoyo

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import pe.upeu.ecorecicla.db.EcoDb

actual fun crearDriverDePrueba(): SqlDriver {
    val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
    EcoDb.Schema.create(driver)
    return driver
}
