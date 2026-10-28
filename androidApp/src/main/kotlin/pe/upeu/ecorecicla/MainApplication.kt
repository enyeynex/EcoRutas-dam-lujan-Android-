package pe.upeu.ecorecicla

import android.app.Application
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import pe.upeu.ecorecicla.di.initKoin

/** Arranca Koin una sola vez con los modulos comunes y el de Android. */
class MainApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        initKoin(platformModule) {
            androidLogger()
            androidContext(this@MainApplication)
        }
    }
}
