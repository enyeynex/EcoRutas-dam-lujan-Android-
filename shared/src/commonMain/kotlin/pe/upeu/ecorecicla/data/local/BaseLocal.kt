package pe.upeu.ecorecicla.data.local

import app.cash.sqldelight.db.SqlDriver

/**
 * Abre la base de datos local. El esquema y las consultas viven en el codigo
 * comun; solo el driver cambia por plataforma (BaseLocalAndroid en androidApp).
 */
interface BaseLocal {
    fun crearDriver(): SqlDriver
}
