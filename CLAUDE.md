# MyLauncher — Normas del proyecto

Launcher minimalista de Android enfocado en reducir el uso de apps que distraen
(bloqueo de apps, desbloqueos temporales, monitorización en segundo plano).

## Arquitectura

Objetivo: **MVVM + DDD ligero**. El código actual es "MVVM-lite" (repositorio como
state holder, sin ViewModels); todo código nuevo debe acercarse a esta estructura
y las modificaciones grandes deben migrar hacia ella:

- **`domain/`** — Núcleo del dominio (DDD). Modelos con value classes (`App`, `Name`,
  `PackageName`), reglas de negocio puras y contratos (interfaces de repositorio).
  Sin dependencias de Android framework en la medida de lo posible.
- **`data/`** — Implementaciones de repositorios (DataStore, UsageStatsManager).
  La lógica de serialización/persistencia vive aquí, no en el dominio.
- **`ui/`** — Pantallas Compose + ViewModels. Cada pantalla con estado propio debe
  tener su ViewModel que exponga un único `StateFlow<UiState>` y reciba eventos
  (patrón unidireccional: UI → eventos → ViewModel → estado → UI).
- **`service/`** — Servicios Android (monitor de apps bloqueadas, boot receiver).
  Deben delegar las decisiones de negocio al dominio, no contenerlas.

Reglas DDD concretas:
- Las decisiones de negocio (¿está bloqueada? ¿ha expirado el desbloqueo? ¿cuánto
  tiempo ganado queda?) se modelan como funciones puras del dominio, testeables
  sin Android.
- Usa value classes / tipos del dominio en vez de `String`/`Long` sueltos
  (p. ej. `PackageName`, no `String`).
- El tiempo se inyecta (pasar `now: Long` o un `Clock` como parámetro), nunca
  `System.currentTimeMillis()` directo dentro de lógica de dominio.

## Stack técnico

- Kotlin 2.3.x, Java 17, AGP 9.x — versiones en `gradle/libs.versions.toml`
- Jetpack Compose + Material3, single-activity, edge-to-edge
- Persistencia: **DataStore Preferences** (no hay Room). Estado reactivo con `StateFlow`
- Sin framework de DI (no Hilt/Koin) — inyección manual por constructor
- Min SDK 31, target SDK 36
- `kotlinx.collections.immutable` para colecciones en estado de UI

## Convenciones

- UI siempre en Compose; nada de XML layouts nuevos
- Strings de usuario en `res/values/strings.xml` **y** `res/values-es/` (la app es bilingüe EN/ES)
- Documentar decisiones de diseño de features nuevas en `ADR/` como markdown
  (ver `ADR/FEATURE_SCHEDULE_BLOCK.md` como ejemplo de formato)
- Claves nuevas de DataStore: definirlas centralizadas en el repositorio, con
  serialización explícita y tolerante a datos corruptos
- Commits pequeños con mensaje en inglés, en minúscula, estilo de los existentes

## Ficheros clave

- `app/src/main/java/com/braiso22/mylauncher/domain/AppRepository.kt` — estado y persistencia
- `app/src/main/java/com/braiso22/mylauncher/service/BlockedAppMonitorService.kt` — monitor (loop cada 3 s)
- `app/src/main/java/com/braiso22/mylauncher/OverlayActivity.kt` — pantalla de bloqueo
- `app/src/main/java/com/braiso22/mylauncher/service/ForegroundAppDetector.kt` — detección de app en primer plano vía UsageStats

## Verificación

- Compilar: `./gradlew assembleDebug`
- No hay suite de tests todavía; la lógica de dominio nueva debe llegar con tests
  unitarios (JUnit) al ser funciones puras
