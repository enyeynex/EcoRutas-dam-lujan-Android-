# EcoRutas · App Android (KMP) de pesajes en ruta

Aplicación del caso **EcoRecicla** (Examen Parcial U2, versión B). El operario
recorre la ruta del día y registra pesajes **con o sin conexión**; el supervisor
corrige, anula y cierra la ruta. Consume el servicio **EcoAPI**.

## Cómo ejecutar

1. Levantar primero el backend EcoAPI (ver su README); queda en `http://localhost:8080/api/v1`.
2. Abrir este proyecto en Android Studio y ejecutar `androidApp` en el emulador.
   El emulador llega al PC por `http://10.0.2.2:8080/api/v1/` (`data/remote/ApiConfig.kt`).
3. Usuarios de prueba: `operario1`, `supervisor1`, `admin` (contraseña `Eco2026*`).

Pruebas de `commonTest` (JVM, sin emulador ni red):

```powershell
.\gradlew :shared:testAndroidHostTest
```

## Estructura

```
shared/src/commonMain/kotlin/pe/upeu/ecorecicla/
├── domain/
│   ├── model/        PuntoAcopio, EstadoPesaje, Pesaje, Sesion (enunciado 4.5), ErrorApi, Ruta, Privilegios
│   ├── repository/   AuthRepository, RutaRepository, PesajeRepository (solo contratos)
│   ├── usecase/      SincronizarUseCase, RegistrarPesajeUseCase (validación), Corregir, Anular, CerrarRuta, sesión
│   ├── sesion/       AvisosDeSesion (canal del 401)
│   └── plataforma/   MonitorDeRed, Reloj (interfaces que implementa Android)
├── data/
│   ├── remote/       ClienteHttp.kt, ApiConfig.kt, MapeoErrores.kt, EcoApi.kt, LectorJwt.kt
│   │   └── dto/      DTO del contrato + funciones aDominio()
│   ├── local/        AlmacenSeguro.kt (interfaz), BaseLocal.kt, LocalEco.kt (consultas SQLDelight)
│   └── repository/   AuthRepositorioRest, EcoRepositorioOffline (offline-first)
├── presentation/     sesion/, listado/, detalle/, perfil/, navigation/, theme/, components/
└── di/               redModule, dataModule, domainModule, presentationModule (+ platformModule)
shared/src/commonMain/sqldelight/pe/upeu/ecorecicla/db/Eco.sq   esquema y consultas locales
androidApp/src/main/kotlin/pe/upeu/ecorecicla/
├── AlmacenSeguroAndroid.kt   EncryptedSharedPreferences + Android Keystore
├── BaseLocalAndroid.kt       AndroidSqliteDriver (ecorutas.db)
├── MonitorDeRedAndroid.kt    ConnectivityManager: avisa cuando vuelve la red
├── RelojAndroid.kt
└── PlatformModule.android.kt motor OkHttp y dependencias de Android
```

## Decisiones de arquitectura

- **Capas**: `domain` no conoce Ktor, SQLDelight ni anotaciones de serialización.
  Los ViewModel (StateFlow + UiState) solo llaman casos de uso; los casos de uso
  dependen de interfaces de repositorio; Koin ensambla todo.
- **Cliente Ktor único** (`redModule`, `single`): negociación de contenido con
  `ignoreUnknownKeys`, dirección base centralizada, tiempos de espera
  (conexión 10 s, petición 15 s), registro solo de cabeceras en depuración y
  `Authorization` saneada. La cabecera la agrega **un solo lugar**: el plugin
  `Auth` bearer (`loadTokens` lee el almacén seguro; `clearToken()` al iniciar y
  cerrar sesión).
- **Errores tipados** (`ErrorApi`): `MapeoErrores.kt` traduce en un solo punto
  400 → `Validacion` (campo → mensaje), 401 → `NoAutorizado` (+ aviso de sesión),
  403 → `SinPrivilegio`, 404 → `NoEncontrado`, 409 → `Conflicto` (motivo del
  servidor), 5xx → `Servidor`, `IOException` → `SinConexion`, timeouts →
  `TiempoAgotado`. Un 204 (anulación) no se intenta convertir.

| Respuesta | Conducta en la app |
| --- | --- |
| 400 | El mensaje aparece bajo el campo del formulario (mismas claves `material`, `pesoBruto`, `tara`). |
| 401 | Se borra el token y se vuelve al login con aviso. |
| 403 | «Operación no autorizada»; la sesión se mantiene. |
| 404 | Se informa y la lista se refresca (sin ruta asignada → estado vacío). |
| 409 | Se muestra el motivo del servicio (ruta cerrada, faltan puntos para cerrar). |
| 500 | Mensaje breve y botón Reintentar. |
| Sin red | El cambio queda pendiente y se ve igual en la lista. |

## Estrategia offline-first

- La interfaz **solo lee la base local** (`Eco.sq`) mediante `Flow`
  (`asFlow().mapToList`). La red únicamente actualiza esa base.
- Un pesaje creado sin red se guarda con **id negativo** (`siguienteIdLocal`
  usa `COALESCE(MIN(id), 0)` y `minOf(x, 0) - 1`: funciona con la tabla vacía y el
  servicio nunca emite negativos), `pendiente = 1` y `operacion = CREAR`.
  Correcciones y anulaciones sin red se marcan `EDITAR` / `ANULAR`.
- `sincronizar()` (con `Mutex`): **primero sube** lo pendiente (POST, PUT,
  DELETE) y **después baja** la ruta y los pesajes de cada punto. La fila
  provisional se sustituye por la definitiva en la misma transacción. La bajada
  borra solo lo sincronizado y usa `INSERT OR IGNORE`: nunca pisa un pendiente.
- Un rechazo de negocio (400/403/404/409) se guarda en la columna `rechazo` y
  **no bloquea** a los demás; el usuario lo descarta. Sin red, timeout o 401
  cortan la sincronización y todo queda pendiente.
- Sincronización manual (botón con progreso y contador de pendientes) y
  **automática al recuperar la conexión** (`MonitorDeRed`), con resumen
  «Enviados N · Recibidos M».
- La pantalla muestra «Actualizado hace X» o «Nunca sincronizado».

## Sesión y privilegios

- Token y perfil (usuario, rol, privilegios) en `AlmacenSeguroAndroid`
  (EncryptedSharedPreferences, llave en el Keystore).
- Al reabrir la app se lee el claim `exp` del JWT: vigente → entra directo;
  vencido → se borra y vuelve al login.
- Cerrar sesión borra el token **y la base local** (teléfono compartido entre
  turnos). Si hay pesajes sin enviar, el perfil pide una segunda confirmación.
- La interfaz pregunta por **privilegio**, nunca por rol: «Registrar pesaje»
  (`PESAJE_CREAR`), «Corregir» (`PESAJE_EDITAR`; solo los propios salvo
  `PESAJE_EDITAR_TODOS`), «Anular» (`PESAJE_ANULAR`), «Cerrar ruta»
  (`RUTA_CERRAR`). Ocultar el botón es experiencia de usuario: **el servicio
  verifica el privilegio igual** y responde 403.

## Pruebas (commonTest)

Base SQLite en memoria (`JdbcSqliteDriver(IN_MEMORY)`, una por prueba) y
`MockEngine` de Ktor con un EcoAPI simulado:

- `DtoTest`: conversión de DTO (envoltorio paginado, anulado → `EstadoPesaje.Anulado`).
- `MapeoErroresTest`: 400 por campo, 401 con aviso, 403 sin cerrar sesión, 404, 409, 500, sin red.
- `RepositorioOfflineTest`: registro sin conexión (id negativo, pendiente, Flow),
  orden de sincronización (POST antes de GET rutas/hoy, sin duplicados),
  rechazo que no bloquea, la bajada no pisa una corrección pendiente, 403 de
  RN-02, anulación con 204, sin ruta asignada, cierre de sesión limpio.
- `AuthRepositorioTest`: login, credenciales incorrectas, token vencido.
- `ValidacionYAntiguedadTest`.
