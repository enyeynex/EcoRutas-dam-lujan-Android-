package pe.upeu.ecorecicla

import android.content.pm.ApplicationInfo
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module
import pe.upeu.ecorecicla.data.local.AlmacenSeguro
import pe.upeu.ecorecicla.data.local.BaseLocal
import pe.upeu.ecorecicla.di.ES_DEPURACION
import pe.upeu.ecorecicla.domain.plataforma.MonitorDeRed
import pe.upeu.ecorecicla.domain.plataforma.Reloj

/** Lo que solo Android sabe hacer: motor HTTP, cifrado, SQLite, red y reloj. */
val platformModule: Module = module {
    single<HttpClientEngine> { OkHttp.create() }
    single<AlmacenSeguro> { AlmacenSeguroAndroid(androidContext()) }
    single<BaseLocal> { BaseLocalAndroid(androidContext()) }
    single<MonitorDeRed> { MonitorDeRedAndroid(androidContext()) }
    single<Reloj> { RelojAndroid() }
    single(ES_DEPURACION) {
        (androidContext().applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    }
}
