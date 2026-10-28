package pe.upeu.ecorecicla.domain.model

/** Nombres de los privilegios del caso (seccion 3.3). La app solo los consulta. */
object Privilegios {
    const val PUNTO_LEER = "PUNTO_LEER"
    const val PESAJE_LEER = "PESAJE_LEER"
    const val PESAJE_CREAR = "PESAJE_CREAR"
    const val PESAJE_EDITAR = "PESAJE_EDITAR"
    /** Corregir pesajes de cualquier operario (la celda «Todos» de la matriz). */
    const val PESAJE_EDITAR_TODOS = "PESAJE_EDITAR_TODOS"
    const val PESAJE_ANULAR = "PESAJE_ANULAR"
    const val RUTA_CERRAR = "RUTA_CERRAR"
    const val PUNTO_GESTIONAR = "PUNTO_GESTIONAR"
}
