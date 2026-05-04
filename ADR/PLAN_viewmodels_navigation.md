# Plan: Refactoring de MyLauncher con ViewModels y Jetpack Navigation

Refactorizar la app para separar la lógica de negocio de los composables mediante ViewModels, introducir Jetpack Compose Navigation para gestionar el flujo Tutorial → Launcher, mover `AppInfo` al dominio, y limpiar el acceso al repositorio. Se mantiene el `HorizontalPager` como UX intencionada para el swipe Home ↔ AppList dentro de una única pantalla de navegación.

---

## Paso 1 — Añadir dependencias de ViewModel y Navigation

**Archivos a modificar:**
- `gradle/libs.versions.toml`
- `app/build.gradle.kts`

**Cambios:**
1. En `libs.versions.toml`, añadir las versiones `navigationCompose = "2.9.0"` y `lifecycleViewmodelCompose = "2.10.0"`.
2. Declarar las librerías:
   - `androidx-navigation-compose = { group = "androidx.navigation", name = "navigation-compose", version.ref = "navigationCompose" }`
   - `androidx-lifecycle-viewmodel-compose = { group = "androidx.lifecycle", name = "lifecycle-viewmodel-compose", version.ref = "lifecycleViewmodelCompose" }`
3. En `app/build.gradle.kts`, añadir ambas como `implementation`.
4. Sincronizar Gradle.

---

## Paso 2 — Mover `AppInfo` y `getInstalledApps()` a la capa de dominio

**Archivos a crear:**
- `app/src/main/java/com/braiso22/mylauncher/domain/AppInfo.kt`
- `app/src/main/java/com/braiso22/mylauncher/domain/InstalledAppsProvider.kt` (interfaz)
- `app/src/main/java/com/braiso22/mylauncher/data/AndroidInstalledAppsProvider.kt` (implementación)

**Archivos a modificar:**
- `app/src/main/java/com/braiso22/mylauncher/AppListScreen.kt` — eliminar `AppInfo` y `getInstalledApps()`
- `app/src/main/java/com/braiso22/mylauncher/GreetingScreen.kt` — actualizar imports

**Cambios:**
1. Crear `domain/AppInfo.kt` con la data class `AppInfo(label, packageName, activityName)`.
2. Crear `domain/InstalledAppsProvider.kt` como **interfaz**:
   ```kotlin
   interface InstalledAppsProvider {
       fun getInstalledApps(): ImmutableList<AppInfo>
   }
   ```
3. Crear `data/AndroidInstalledAppsProvider.kt` como **implementación** que recibe `Context` por constructor:
   ```kotlin
   class AndroidInstalledAppsProvider(private val context: Context) : InstalledAppsProvider {
       override fun getInstalledApps(): ImmutableList<AppInfo> { ... }
   }
   ```
4. Eliminar `AppInfo` y `getInstalledApps()` de `AppListScreen.kt`.
5. Actualizar todos los imports en los archivos afectados.

---

## Paso 3 — Crear ViewModels separados por pantalla

Se crean **tres ViewModels** con responsabilidades bien definidas, en lugar de uno compartido monolítico.

### 3.1 — `MainViewModel` (estado global mínimo)

**Archivo a crear:**
- `app/src/main/java/com/braiso22/mylauncher/viewmodel/MainViewModel.kt`

**Responsabilidades:**
- Instancia `AppRepository.getInstance(application)` y `AndroidInstalledAppsProvider(application)`.
- Expone `tutorialCompleted: StateFlow<Boolean>`.
- Expone `setTutorialCompleted(completed: Boolean)`.
- Carga `allApps: StateFlow<ImmutableList<AppInfo>>` en `init` vía `viewModelScope`.
- Se usa a nivel del `NavHost` para determinar `startDestination` y compartir la lista de apps.

### 3.2 — `HomeViewModel` (pantalla Greeting/Home)

**Archivo a crear:**
- `app/src/main/java/com/braiso22/mylauncher/viewmodel/HomeViewModel.kt`

**Responsabilidades:**
- Recibe `AppRepository` (o se obtiene internamente vía `AndroidViewModel`).
- Expone `favoriteApps: StateFlow<ImmutableList<AppInfo>>` derivado de `allApps` + `favorites`.
- Expone `blocked: StateFlow<Set<String>>` y `blockTimes: StateFlow<Map<String, Int>>`.
- Métodos:
  - `launchApp(context, AppInfo)` — lógica de lanzamiento.
  - `handleAppClick(context, AppInfo)` — gestiona la lógica de bloqueo/unlock antes de lanzar.
  - `markAppUnlocked(packageName, minutes)`
  - `isAppUnlocked(packageName): Boolean`
- No gestiona reloj (se queda en UI como helper composable).

### 3.3 — `AppListViewModel` (pantalla AppList)

**Archivo a crear:**
- `app/src/main/java/com/braiso22/mylauncher/viewmodel/AppListViewModel.kt`

**Responsabilidades:**
- Recibe `AppRepository` (o se obtiene internamente vía `AndroidViewModel`).
- Gestiona estado UI propio:
  - `searchQuery: MutableStateFlow<String>`
  - `showingBlocked: MutableStateFlow<Boolean>`
  - `filteredApps: StateFlow<ImmutableList<AppInfo>>` (derivado de `allApps` + `searchQuery` + `blocked` + `showingBlocked`, con debounce interno)
- Expone `favorites`, `blocked`, `blockTimes`, `lastOpenedPackage`, `lastOpenedApp`.
- Métodos:
  - `updateSearchQuery(query: String)`
  - `toggleShowBlocked()`
  - `toggleFavorite(packageName)`
  - `blockApp(packageName, minutes)`
  - `launchApp(context, AppInfo)`
  - `markAppUnlocked(packageName, minutes)`
  - `isAppUnlocked(packageName): Boolean`
  - `setLastOpened(packageName)`

> **Nota sobre compartir `allApps`:** Ambos ViewModels (`HomeViewModel` y `AppListViewModel`) necesitan la lista de apps instaladas. Se puede resolver de dos formas:
> - **(A) Opción simple:** Cada ViewModel carga `allApps` independientemente (la carga es rápida y se cachea en memoria). 
> - **(B) Opción limpia:** `MainViewModel` expone `allApps` y se pasa como parámetro a las pantallas que lo necesiten vía state hoisting.
> 
> Se recomienda la **opción A** por simplicidad y desacoplamiento. Cada ViewModel es autónomo.

---

## Paso 4 — Crear el grafo de navegación en `NavGraph`

**Archivos a crear:**
- `app/src/main/java/com/braiso22/mylauncher/navigation/NavGraph.kt`

**Contenido:**
- Composable `LauncherNavGraph()` con un `NavHost` y dos destinos:
  - `"tutorial"` → renderiza `TutorialScreen(onFinishTutorial = { mainViewModel.setTutorialCompleted(true); navController.navigate("launcher") { popUpTo("tutorial") { inclusive = true } } })`
  - `"launcher"` → renderiza `LauncherPager()` (el `HorizontalPager` refactorizado, cada página crea su propio ViewModel)
- El `startDestination` se determina leyendo `tutorialCompleted` del `MainViewModel`.
- El `MainViewModel` se obtiene con `viewModel()` a nivel del `NavHost`.

---

## Paso 5 — Refactorizar `LauncherPager`, `GreetingScreen` y `AppListScreen`

### 5.1 — `LauncherPager.kt`

**Cambios:**
- Eliminar la lógica de `if (!tutorialCompleted)` (ahora la gestiona Navigation).
- Eliminar `remember { AppRepository.getInstance(context) }`.
- Recibir `onShowTutorial: () -> Unit` como callback (para navegar al tutorial desde settings).
- Mantener el `HorizontalPager` con 2 páginas:
  - Página 0: `Greeting(homeViewModel, onShowTutorial)`
  - Página 1: `AppListScreen(appListViewModel, isActive)`
- Cada ViewModel se obtiene con `viewModel()` dentro de `LauncherPager`.

### 5.2 — `GreetingScreen.kt`

**Cambios:**
- `Greeting` recibe `HomeViewModel` en vez de `AppRepository`.
- Eliminar la carga de `allApps` y `favorites` locales; leer `homeViewModel.favoriteApps`.
- Mantener el reloj como helper composable (es UI pura, no lógica de negocio).
- `GreetingContent` se mantiene sin cambios (composable puro con lambdas).

### 5.3 — `AppListScreen.kt`

**Cambios:**
- `AppListScreen` recibe `AppListViewModel` en vez de `AppRepository` y `Context`.
- Leer todos los estados del ViewModel (`filteredApps`, `favorites`, `blocked`, etc.).
- Usar `LocalContext.current` solo cuando necesite lanzar una app.
- Eliminar `rememberDebouncedValue`; el debounce se gestiona en el ViewModel.
- `AppListScreenContent` se mantiene como composable puro sin cambios.

---

## Paso 6 — Actualizar `MainActivity` y `OverlayActivity`

### 6.1 — `MainActivity.kt`

**Cambios:**
- En `setContent`, reemplazar `LauncherPager()` por `LauncherNavGraph()` (el composable del Paso 4), envuelto en `MyLauncherTheme` y `Scaffold`.
- La lógica de permisos de `onResume` se mantiene en la Activity (es lógica de ciclo de vida Android).

### 6.2 — `OverlayActivity.kt`

**Cambios:**
- Mantener `AppRepository.getInstance()` directamente (es aceptable al ser una Activity aislada y efímera).
- Añadir `// TODO: migrar a OverlayViewModel si la Activity crece en complejidad`.

---

## Consideraciones adicionales

1. **El reloj (`currentTime`/`currentDate`)** se mantiene como composable helper `rememberCurrentTime()`, ya que es puramente presentacional y no lógica de negocio.
2. **`OverlayActivity`** se deja sin ViewModel por ser pragmáticamente correcto para su caso de uso simple y efímero.
3. **Dialogs (`BlockedAppDialog`, `BlockTimePickerDialog`)** se mantienen sin cambios — ya son composables puros que reciben lambdas.
4. **`domain/App.kt`** (con value classes `Name`, `PackageName`) queda sin cambios. No se usa actualmente en la UI y puede integrarse en el futuro si se desea enriquecer el modelo de dominio.

---

## Resumen de archivos

| Acción      | Archivo                                          |
|-------------|--------------------------------------------------|
| Modificar   | `gradle/libs.versions.toml`                      |
| Modificar   | `app/build.gradle.kts`                           |
| Crear       | `domain/AppInfo.kt`                              |
| Crear       | `domain/InstalledAppsProvider.kt` (interfaz)     |
| Crear       | `data/AndroidInstalledAppsProvider.kt`           |
| Crear       | `viewmodel/MainViewModel.kt`                     |
| Crear       | `viewmodel/HomeViewModel.kt`                     |
| Crear       | `viewmodel/AppListViewModel.kt`                  |
| Crear       | `navigation/NavGraph.kt`                         |
| Modificar   | `LauncherPager.kt`                               |
| Modificar   | `GreetingScreen.kt`                              |
| Modificar   | `AppListScreen.kt`                               |
| Modificar   | `MainActivity.kt`                                |
| Modificar   | `OverlayActivity.kt` (mínimo)                    |
| Sin cambios | `TutorialScreen.kt`                              |
| Sin cambios | `BlockedAppDialog.kt`                            |
| Sin cambios | `BlockTimePickerDialog.kt`                       |
| Sin cambios | `domain/AppRepository.kt`                        |
| Sin cambios | `domain/App.kt`                                  |
