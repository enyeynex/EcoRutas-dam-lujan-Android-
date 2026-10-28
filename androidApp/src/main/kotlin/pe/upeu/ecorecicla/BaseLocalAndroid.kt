package pe.upeu.ecorecicla

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import pe.upeu.ecorecicla.data.local.BaseLocal
import pe.upeu.ecorecicla.db.EcoDb

/** Driver de SQLDelight para Android: /data/data/pe.upeu.ecorecicla/databases/ecorutas.db */
class BaseLocalAndroid(private val contexto: Context) : BaseLocal {
    override fun crearDriver(): SqlDriver = AndroidSqliteDriver(
        schema = EcoDb.Schema,
        context = contexto,
        name = "ecorutas.db"
    )
}
