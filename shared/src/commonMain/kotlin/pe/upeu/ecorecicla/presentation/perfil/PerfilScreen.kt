package pe.upeu.ecorecicla.presentation.perfil

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pe.upeu.ecorecicla.domain.model.Sesion
import pe.upeu.ecorecicla.presentation.sesion.Mensaje

@Composable
fun PerfilScreen(
    sesion: Sesion,
    viewModel: PerfilViewModel,
    onCerrarSesion: () -> Unit,
    modifier: Modifier = Modifier
) {
    val pendientes by viewModel.pendientes.collectAsStateWithLifecycle()
    var confirmando by remember { mutableStateOf(false) }

    Column(
        modifier = modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(sesion.nombreCompleto, style = MaterialTheme.typography.titleLarge)
                Text("Usuario: ${sesion.usuario}")
                Text("Rol: ${sesion.rol}")
            }
        }
        Text("Privilegios", style = MaterialTheme.typography.titleSmall)
        sesion.privilegios.sorted().forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }

        if (confirmando && pendientes > 0) {
            Mensaje(
                "Tienes $pendientes pesajes sin enviar. Si cierras sesión se borrarán de este equipo. " +
                    "Sincroniza antes o confirma.", error = true
            )
            Button(
                onClick = onCerrarSesion, modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) { Text("Cerrar sesión de todos modos") }
            OutlinedButton(onClick = { confirmando = false }, modifier = Modifier.fillMaxWidth()) { Text("Cancelar") }
        } else {
            Button(
                onClick = { if (pendientes > 0) confirmando = true else onCerrarSesion() },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Cerrar sesión") }
        }
    }
}
