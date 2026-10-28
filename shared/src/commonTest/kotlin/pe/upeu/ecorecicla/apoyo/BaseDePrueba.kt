package pe.upeu.ecorecicla.apoyo

import app.cash.sqldelight.db.SqlDriver
import pe.upeu.ecorecicla.db.EcoDb

/** Driver de pruebas: SQLite en memoria, nuevo en cada llamada (una base por prueba). */
expect fun crearDriverDePrueba(): SqlDriver

fun baseEnMemoria(): EcoDb = EcoDb(crearDriverDePrueba())
