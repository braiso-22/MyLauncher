# 🕐 Feature: Bloqueo por Horario + Apps Grises

## 📋 Resumen

Añadir un sistema de **franjas horarias** para bloqueo duro de apps (sin bypass posible) y una
categoría de **"apps grises"** que permiten acceso temporal con justificación escrita, incluso
durante el horario bloqueado.

---

## 🎯 Problema a resolver

1. **Habituación**: El sistema actual (captcha 6 chars + timer 10s) ya no genera fricción real — el
   usuario se acostumbró.
2. **Molestia innecesaria**: Cuando realmente se necesita el móvil, el bloqueo estorba.
3. **Apps ambiguas**: Algunas apps como Chrome son necesarias a veces (buscar algo rápido) pero
   también pueden ser una fuente de distracción.

---

## 🧩 Conceptos clave

### Bloqueo por horario (Bloqueo duro)

- El usuario define una **franja horaria** (ej. `09:00 - 17:00`) por cada app.
- Durante esa franja, la app está **completamente bloqueada** — no hay captcha, no hay timer, no hay
  forma de abrirla.
- Fuera de la franja, la app se comporta como ahora (bloqueo normal con captcha + timer si está
  marcada como bloqueada).

### Apps grises

- Subcategoría de apps con bloqueo por horario.
- Durante la franja bloqueada, **se permite acceso temporal** escribiendo una **justificación
  obligatoria** (mínimo 20 caracteres).
- El acceso es muy limitado: **2 minutos** fijos, no configurables (para evitar auto-engaño).
- Tras los 2 minutos, se muestra el overlay y se cierra el acceso.

---

## 🏗️ Plan de implementación

### 1. Nuevas keys en DataStore (`AppRepository.kt`)

| Key                        | Tipo                      | Descripción                                                                         |
|----------------------------|---------------------------|-------------------------------------------------------------------------------------|
| `KEY_SCHEDULE_RANGES`      | `stringPreferencesKey`    | Mapa serializado `packageName -> "HH:mm-HH:mm"` (una franja por app)                |
| `KEY_GREY_APPS`            | `stringSetPreferencesKey` | Set de packageNames marcados como "grises"                                          |
| `KEY_GREY_UNLOCK_EXPIRIES` | `stringPreferencesKey`    | Mapa serializado `packageName -> timestamp` (expiry de acceso gris, 2 min)          |
| `KEY_GREY_JUSTIFICATIONS`  | `stringPreferencesKey`    | Mapa serializado `packageName -> última justificación` (para auto-reflexión futura) |

**Nuevos StateFlows:**

- `scheduleRanges: StateFlow<Map<String, Pair<String, String>>>` — mapa de packageName a par (
  horaInicio, horaFin)
- `greyApps: StateFlow<Set<String>>` — set de apps grises
- `greyUnlockExpiries: StateFlow<Map<String, Long>>` — expiries de desbloqueos grises

**Nuevos métodos:**

- `setSchedule(packageName: String, startTime: String, endTime: String)` — guardar franja horaria
- `removeSchedule(packageName: String)` — eliminar franja horaria
- `markAsGreyApp(packageName: String)` — marcar app como gris
- `unmarkGreyApp(packageName: String)` — desmarcar app como gris
- `isInScheduledBlock(packageName: String): Boolean` — compara hora actual con franja configurada
- `justifyAndUnlockGrey(packageName: String, justification: String)` — guarda justificación + setea
  expiry de 2 min
- `isGreyAppUnlocked(packageName: String): Boolean` — comprueba si tiene un desbloqueo gris no
  expirado
- `clearGreyUnlock(packageName: String)` — limpia unlock gris
- `clearExpiredGreyUnlocks()` — limpia expirados

**Constante:**

```kotlin
private const val GREY_ACCESS_MINUTES = 2
```

---

### 2. Nuevos diálogos de UI

#### `SchedulePickerDialog.kt` (nuevo archivo)

- **Propósito**: Configurar la franja horaria de bloqueo de una app.
- **Contenido**:
    - `TimePicker` (Material3) para hora de inicio.
    - `TimePicker` (Material3) para hora de fin.
    - Toggle/Checkbox para marcar la app como "gris" (acceso con justificación).
    - Texto informativo explicando el comportamiento.
- **Callbacks**: `onConfirm(startTime: String, endTime: String, isGrey: Boolean)`, `onDismiss()`.
- **Se abre desde**: Nueva opción en `AppContextMenu`.

#### `GreyAppJustificationDialog.kt` (nuevo archivo)

- **Propósito**: Solicitar justificación escrita al usuario para acceder a una app gris en horario
  bloqueado.
- **Contenido**:
    - Título: "¿Por qué necesitas esta app ahora?"
    - `OutlinedTextField` multilínea para la justificación.
    - Contador de caracteres con indicador visual (mínimo 20 chars).
    - Texto informativo: "Tendrás 2 minutos de acceso".
    - Botón confirmar habilitado solo cuando `text.length >= 20`.
- **Callbacks**: `onConfirm(justification: String)`, `onDismiss()`.
- **Se abre desde**: Click en app gris durante horario bloqueado (en `AppListScreen` y
  `GreetingScreen`).

---

### 3. Cambios en `BlockedAppMonitorService.kt`

Modificar el método `check()` para evaluar en capas **antes** de la lógica actual:

```
1. ¿La app tiene horario configurado Y estamos dentro de la franja?
   ├── SÍ → ¿Es app gris?
   │   ├── SÍ → ¿Tiene desbloqueo gris válido (no expirado)?
   │   │   ├── SÍ → Permitir uso (no hacer nada)
   │   │   └── NO → Lanzar OverlayActivity con Reason.GREY_EXPIRED
   │   └── NO → Lanzar OverlayActivity con Reason.HARD_SCHEDULE (bloqueo total)
   └── NO → Flujo actual sin cambios (captcha + timer normal)
```

También añadir limpieza periódica de grey unlocks expirados (`clearExpiredGreyUnlocks()`).

---

### 4. Cambios en `OverlayActivity.kt`

Añadir 2 nuevos valores al enum `Reason`:

| Reason          | Icono                   | Título                      | Mensaje                                                           | Botones                 |
|-----------------|-------------------------|-----------------------------|-------------------------------------------------------------------|-------------------------|
| `HARD_SCHEDULE` | `Icons.Default.Block`   | "App bloqueada por horario" | "Esta app está bloqueada hasta las HH:mm. No es posible acceder." | Solo "Volver al inicio" |
| `GREY_EXPIRED`  | `Icons.Default.Warning` | "Tiempo de acceso agotado"  | "Tu acceso justificado de 2 minutos ha terminado."                | Solo "Volver al inicio" |

Para `HARD_SCHEDULE`: **no hay absolutamente ningún bypass**. Solo el botón de volver a home.

---

### 5. Cambios en `AppListScreen.kt`

#### En `AppContextMenu`:

- Añadir tercera opción: **"Horario de bloqueo"** con icono `Icons.Default.Schedule`.
- Al pulsar, abre `SchedulePickerDialog`.

#### En la lógica de click de apps (tanto en vista bloqueada como normal):

```
Al hacer click en una app:
1. ¿Tiene horario Y estamos en franja bloqueada?
   ├── SÍ → ¿Es gris?
   │   ├── SÍ → ¿Ya tiene unlock gris válido?
   │   │   ├── SÍ → Lanzar app directamente
   │   │   └── NO → Mostrar GreyAppJustificationDialog
   │   └── NO → Mostrar Snackbar "Bloqueada hasta HH:mm"
   └── NO → Flujo actual (si está bloqueada → captcha, si no → lanzar)
```

#### Nuevos parámetros a pasar al content:

- `scheduleRanges`, `greyApps`
- `onSetSchedule`, `onRemoveSchedule`, `onMarkGrey`
- `isInScheduledBlock`, `isGreyAppUnlocked`
- `onGreyAppJustified` (callback para cuando se confirma justificación)

---

### 6. Cambios en `GreetingScreen.kt`

Misma lógica de click que `AppListScreen` para los favoritos:

- Antes de comprobar si está bloqueada normalmente, comprobar si está en bloqueo por horario.
- Si es gris → mostrar `GreyAppJustificationDialog`.
- Si es bloqueo duro → mostrar Snackbar.

Necesita recibir los nuevos StateFlows del repository (`scheduleRanges`, `greyApps`).

---

### 7. Nuevos strings

#### `res/values/strings.xml` (inglés)

```xml

<string name="schedule_block_option">Schedule block</string><string name="schedule_block_title">
Schedule block
</string><string name="schedule_start_time">Start time</string><string name="schedule_end_time">End
time
</string><string name="mark_as_grey_app">Allow access with justification</string><string
name="grey_app_explanation">Grey apps can be opened during blocked hours by writing a justification.
Access is limited to 2 minutes.
</string><string name="schedule_confirm">Save schedule</string><string name="remove_schedule">Remove
schedule
</string><string name="grey_justification_title">Why do you need this app now?</string><string
name="grey_justification_hint">Write your reason here (min. 20 characters)
</string><string name="grey_time_info">You will have 2 minutes of access</string><string
name="grey_min_chars">%1$d / 20 characters minimum
</string><string name="justify_and_open">Justify and open</string><string
name="hard_schedule_blocked_title">Blocked by schedule
</string><string name="hard_schedule_blocked_message">This app is blocked until %1$s. There is no
way to access it.
</string><string name="grey_expired_title">Access time expired</string><string
name="grey_expired_message">Your justified 2-minute access has ended.
</string><string name="blocked_until_snackbar">Blocked until %1$s</string>
```

#### `res/values-es/strings.xml` (español)

```xml

<string name="schedule_block_option">Bloqueo por horario</string>
<string name="schedule_block_title">Bloqueo por horario</string>
<string name="schedule_start_time">Hora de inicio</string>
<string name="schedule_end_time">Hora de fin</string>
<string name="mark_as_grey_app">Permitir acceso con justificación</string>
<string name="grey_app_explanation">Las apps grises pueden abrirse en horario bloqueado escribiendo una
justificación. El acceso se limita a 2 minutos.</string>
<string name="schedule_confirm">Guardar horario</string>
<string name="remove_schedule">Eliminar horario</string>
<string name="grey_justification_title">¿Por qué necesitas esta app ahora?</string>
<string name="grey_justification_hint">Escribe tu razón aquí (mín. 20 caracteres)</string>
<string name="grey_time_info">Tendrás 2 minutos de acceso</string>
<string name="grey_min_chars">%1$d / 20 caracteres mínimo</string>
<string name="justify_and_open">Justificar y abrir</string>
<string name="hard_schedule_blocked_title">Bloqueada por horario</string>
<string name="hard_schedule_blocked_message">Esta app está bloqueada hasta las %1$s. No es posible acceder.</string>
<string name="grey_expired_title">Tiempo de acceso agotado</string>
<string name="grey_expired_message">Tu acceso justificado de 2 minutos ha terminado.</string>
<string name="blocked_until_snackbar">Bloqueada hasta las %1$s</string>
```

---

## 📁 Archivos afectados

| Archivo                               | Acción                                                                 |
|---------------------------------------|------------------------------------------------------------------------|
| `domain/AppRepository.kt`             | Modificar — nuevas keys, StateFlows y métodos                          |
| `domain/App.kt`                       | Sin cambios (la categoría se infiere del DataStore, no del modelo)     |
| `SchedulePickerDialog.kt`             | **Crear** — diálogo de configuración de horario                        |
| `GreyAppJustificationDialog.kt`       | **Crear** — diálogo de justificación                                   |
| `service/BlockedAppMonitorService.kt` | Modificar — nueva lógica de capas en `check()`                         |
| `OverlayActivity.kt`                  | Modificar — nuevos `Reason` + UI correspondiente                       |
| `AppListScreen.kt`                    | Modificar — nueva opción en menú + lógica de click + nuevos parámetros |
| `GreetingScreen.kt`                   | Modificar — lógica de click para favoritos con horario                 |
| `res/values/strings.xml`              | Modificar — nuevos strings EN                                          |
| `res/values-es/strings.xml`           | Modificar — nuevos strings ES                                          |

---

## 🔄 Compatibilidad con el sistema actual

- **No se rompe nada**: Las keys existentes de DataStore (`blocked`, `block_times`,
  `unlock_expiries`, etc.) se mantienen intactas.
- **Capas de evaluación**: El bloqueo por horario se evalúa **primero**. Si no aplica, se cae al
  flujo actual (captcha + timer).
- **Una app puede tener ambos**: bloqueo normal (captcha fuera de horario) + bloqueo por horario (
  duro o gris dentro de franja). Son complementarios.
- **Sin permisos nuevos**: Todo funciona con los permisos que ya tiene la app.

---

## 🧪 Flujos de usuario

### Configurar bloqueo por horario

1. Long press en una app → Menú contextual.
2. Pulsar "Horario de bloqueo".
3. Seleccionar hora de inicio y fin con TimePicker.
4. (Opcional) Activar toggle "Permitir acceso con justificación" → la marca como gris.
5. Pulsar "Guardar horario".

### Intentar abrir app con bloqueo duro (dentro de horario)

1. Pulsar app en la lista o en favoritos.
2. Aparece Snackbar: "Bloqueada hasta las 17:00".
3. No se puede hacer nada más.
4. Si se intenta abrir desde fuera del launcher → OverlayActivity con pantalla de bloqueo total.

### Intentar abrir app gris (dentro de horario)

1. Pulsar app gris en la lista o en favoritos.
2. Aparece `GreyAppJustificationDialog`.
3. Escribir justificación de ≥20 caracteres.
4. Pulsar "Justificar y abrir".
5. La app se abre con un timer de 2 minutos.
6. A los 2 minutos → OverlayActivity "Tiempo de acceso agotado" → Volver a home.

### App fuera de horario bloqueado

1. Se comporta exactamente como ahora (si está bloqueada → captcha + timer normal).

---

## 💡 Decisiones de diseño

| Decisión                                                         | Razón                                                                            |
|------------------------------------------------------------------|----------------------------------------------------------------------------------|
| **Una sola franja por app**                                      | Simplicidad. Se puede iterar a múltiples franjas en el futuro.                   |
| **2 minutos fijos para apps grises**                             | Evitar que el usuario se auto-engañe configurando tiempos largos.                |
| **Justificación mínima 20 chars**                                | Fuerza reflexión real, no un "ok" o "lo necesito".                               |
| **Sin persistencia de historial de justificaciones (por ahora)** | Solo se guarda la última. Un historial completo requeriría Room — mejora futura. |
| **Sin permisos nuevos**                                          | Máxima simplicidad, usar solo lo que ya existe.                                  |

