package pe.upeu.ecorecicla.presentation.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel
import pe.upeu.ecorecicla.domain.model.Sesion
import pe.upeu.ecorecicla.presentation.detalle.DetallePuntoScreen
import pe.upeu.ecorecicla.presentation.listado.RutaScreen
import pe.upeu.ecorecicla.presentation.perfil.PerfilScreen
import pe.upeu.ecorecicla.presentation.sesion.LoginScreen
import pe.upeu.ecorecicla.presentation.sesion.SesionViewModel
import pe.upeu.ecorecicla.presentation.theme.EcoTheme

/** Pantallas del area protegida. */
sealed interface Pantalla {
    data object Ruta : Pantalla
    data class Detalle(val puntoId: Long) : Pantalla
    data object Perfil : Pantalla
}

@Composable
fun EcoApp() {
    EcoTheme {
        val sesionVm = koinViewModel<SesionViewModel>()
        val estado by sesionVm.uiState.collectAsStateWithLifecycle()
        val sesion = estado.sesion
        when {
            estado.verificando -> Box(Modifier.fillMaxSize()) { CircularProgressIndicator(Modifier.align(Alignment.Center)) }
            sesion == null -> Scaffold { padding ->
                Box(Modifier.padding(padding)) {
                    LoginScreen(estado, sesionVm::onUsuarioChange, sesionVm::onClaveChange, sesionVm::iniciarSesion)
                }
            }
            // key: cada inicio de sesion crea ViewModel nuevos (turno compartido).
            else -> key(estado.idSesion) { AreaProtegida(sesion, estado.idSesion, sesionVm::cerrarSesion) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AreaProtegida(sesion: Sesion, idSesion: Int, onCerrarSesion: () -> Unit) {
    var pantalla by remember { mutableStateOf<Pantalla>(Pantalla.Ruta) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when (pantalla) {
                            Pantalla.Ruta -> "Ruta del día"
                            is Pantalla.Detalle -> "Punto de acopio"
                            Pantalla.Perfil -> "Mi perfil"
                        }
                    )
                },
                navigationIcon = {
                    if (pantalla != Pantalla.Ruta) {
                        IconButton(onClick = { pantalla = Pantalla.Ruta }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                        }
                    }
                },
                actions = {
                    if (pantalla != Pantalla.Perfil) {
                        IconButton(onClick = { pantalla = Pantalla.Perfil }) {
                            Icon(Icons.Default.AccountCircle, contentDescription = "Perfil")
                        }
                    }
                }
            )
        }
    ) { padding ->
        val modifier = Modifier.padding(padding)
        when (val actual = pantalla) {
            Pantalla.Ruta -> RutaScreen(
                viewModel = koinViewModel(key = "ruta-$idSesion"), sesion = sesion,
                onVerPunto = { pantalla = Pantalla.Detalle(it) }, modifier = modifier
            )
            is Pantalla.Detalle -> DetallePuntoScreen(
                puntoId = actual.puntoId, viewModel = koinViewModel(key = "detalle-$idSesion"),
                sesion = sesion, modifier = modifier
            )
            Pantalla.Perfil -> PerfilScreen(
                sesion = sesion, viewModel = koinViewModel(key = "perfil-$idSesion"),
                onCerrarSesion = onCerrarSesion, modifier = modifier
            )
        }
    }
}
