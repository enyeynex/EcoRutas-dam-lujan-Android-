package pe.upeu.ecorecicla.presentation.sesion

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Recycling
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import pe.upeu.ecorecicla.presentation.components.ValidatedTextField

@Composable
fun LoginScreen(
    estado: SesionUiState,
    onUsuarioChange: (String) -> Unit,
    onClaveChange: (String) -> Unit,
    onIngresar: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Default.Recycling, contentDescription = null,
            tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(64.dp))
        Text("EcoRutas", style = MaterialTheme.typography.headlineMedium)
        Text("Pesajes en ruta · EcoRecicla", style = MaterialTheme.typography.bodyMedium)

        estado.aviso?.let { Mensaje(it, error = true) }

        ValidatedTextField(
            value = estado.usuario, onValueChange = onUsuarioChange, label = "Usuario",
            error = estado.errorUsuario, leadingIcon = Icons.Default.Person
        )
        ValidatedTextField(
            value = estado.clave, onValueChange = onClaveChange, label = "Contraseña",
            error = estado.errorClave, leadingIcon = Icons.Default.Lock,
            keyboardType = KeyboardType.Password, visualTransformation = PasswordVisualTransformation()
        )

        estado.error?.let { Mensaje(it, error = true) }

        Button(onClick = onIngresar, enabled = !estado.cargando, modifier = Modifier.fillMaxWidth()) {
            if (estado.cargando) {
                CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
            } else {
                Text("Ingresar")
            }
        }
        Text("Usuarios de prueba: operario1 · supervisor1 · admin", style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
fun Mensaje(texto: String, error: Boolean) {
    Surface(
        color = if (error) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
        contentColor = if (error) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(texto, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium)
    }
}
