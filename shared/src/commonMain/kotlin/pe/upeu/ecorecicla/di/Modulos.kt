package pe.upeu.ecorecicla.di

import org.koin.core.KoinApplication
import org.koin.core.context.startKoin
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.core.qualifier.named
import org.koin.dsl.binds
import org.koin.dsl.module
import pe.upeu.ecorecicla.data.local.BaseLocal
import pe.upeu.ecorecicla.data.local.LocalEco
import pe.upeu.ecorecicla.data.remote.EcoApi
import pe.upeu.ecorecicla.data.remote.crearHttpClient
import pe.upeu.ecorecicla.data.repository.AuthRepositorioRest
import pe.upeu.ecorecicla.data.repository.EcoRepositorioOffline
import pe.upeu.ecorecicla.db.EcoDb
import pe.upeu.ecorecicla.domain.repository.AuthRepository
import pe.upeu.ecorecicla.domain.repository.PesajeRepository
import pe.upeu.ecorecicla.domain.repository.RutaRepository
import pe.upeu.ecorecicla.domain.sesion.AvisosDeSesion
import pe.upeu.ecorecicla.domain.usecase.AnularPesajeUseCase
import pe.upeu.ecorecicla.domain.usecase.CerrarRutaUseCase
import pe.upeu.ecorecicla.domain.usecase.CerrarSesionUseCase
import pe.upeu.ecorecicla.domain.usecase.CorregirPesajeUseCase
import pe.upeu.ecorecicla.domain.usecase.DescartarPesajeUseCase
import pe.upeu.ecorecicla.domain.usecase.IniciarSesionUseCase
import pe.upeu.ecorecicla.domain.usecase.ObservarPesajesUseCase
import pe.upeu.ecorecicla.domain.usecase.ObservarRutaUseCase
import pe.upeu.ecorecicla.domain.usecase.RegistrarPesajeUseCase
import pe.upeu.ecorecicla.domain.usecase.SesionGuardadaUseCase
import pe.upeu.ecorecicla.domain.usecase.SincronizarUseCase
import pe.upeu.ecorecicla.presentation.detalle.DetallePuntoViewModel
import pe.upeu.ecorecicla.presentation.listado.RutaViewModel
import pe.upeu.ecorecicla.presentation.perfil.PerfilViewModel
import pe.upeu.ecorecicla.presentation.sesion.SesionViewModel

/** Calificador del Boolean que dice si es una compilacion de depuracion. */
val ES_DEPURACION = named("esDepuracion")

/** Red: una unica instancia de HttpClient para toda la app. */
val redModule = module {
    single { crearHttpClient(motor = get(), almacen = get(), esDepuracion = get(ES_DEPURACION)) }
    single { EcoApi(get(), get()) }
}

val dataModule = module {
    single { EcoDb(get<BaseLocal>().crearDriver()) }
    single { LocalEco(get()) }
    single { EcoRepositorioOffline(get(), get(), get()) } binds arrayOf(RutaRepository::class, PesajeRepository::class)
    single<AuthRepository> { AuthRepositorioRest(get(), get(), get(), get()) }
}

val domainModule = module {
    single { AvisosDeSesion() }
    factory { IniciarSesionUseCase(get(), get()) }
    factory { SesionGuardadaUseCase(get()) }
    factory { CerrarSesionUseCase(get(), get()) }
    factory { SincronizarUseCase(get()) }
    factory { CerrarRutaUseCase(get()) }
    factory { ObservarRutaUseCase(get()) }
    factory { ObservarPesajesUseCase(get()) }
    factory { RegistrarPesajeUseCase(get(), get()) }
    factory { CorregirPesajeUseCase(get()) }
    factory { AnularPesajeUseCase(get()) }
    factory { DescartarPesajeUseCase(get()) }
}

val presentationModule = module {
    viewModel { SesionViewModel(get(), get(), get(), get()) }
    viewModel { RutaViewModel(get(), get(), get(), get(), get()) }
    viewModel { DetallePuntoViewModel(get(), get(), get(), get(), get(), get()) }
    viewModel { PerfilViewModel(get()) }
}

/** La plataforma aporta motor HTTP, AlmacenSeguro, BaseLocal, MonitorDeRed, Reloj y ES_DEPURACION. */
fun modulosComunes(): List<Module> = listOf(redModule, dataModule, domainModule, presentationModule)

fun initKoin(platformModule: Module, configuracion: KoinApplication.() -> Unit = {}) {
    startKoin {
        configuracion()
        modules(modulosComunes() + platformModule)
    }
}
