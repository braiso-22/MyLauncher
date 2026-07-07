# ⏳ Feature: Banco de tiempo por abstinencia

## 📋 Resumen

Sustituir el sistema actual de desbloqueos con **tiempo fijo** (1/5/10 min elegidos por el usuario)
por un sistema donde el tiempo de uso de apps bloqueadas **se gana** en función del tiempo que
llevas **sin usar ninguna app bloqueada**. El tiempo ganado se acumula en un **banco con saldo**
(tope: 1 hora). El captcha se mantiene como fricción al desbloquear.

---

## 🎯 Problema a resolver

1. El tiempo fijo no incentiva nada: da igual llevar 5 minutos o 5 horas sin usar Instagram, el
   desbloqueo siempre da lo mismo.
2. El monitor actual hace polling cada 3 segundos todo el día, aunque el 95 % del tiempo no hay
   ninguna app bloqueada en uso.

---

## 🧩 Conceptos clave

### Abstinencia

- Tiempo transcurrido desde el **final de la última sesión de una app bloqueada**.
- Usar apps NO bloqueadas (teléfono, Maps, banco…) **no** resetea el contador.
- No se cuenta en vivo: se calcula **retroactivamente** con `UsageStatsManager.queryEvents()`
  cuando hace falta (al abrir el launcher o al pulsar sobre una app bloqueada).

### Curva de conversión (por tramos)

| Abstinencia continua | Tiempo ganado |
|----------------------|---------------|
| < 30 min             | 0             |
| ≥ 30 min, < 1 h      | 1 min         |
| ≥ 1 h                | 5 min         |
| ≥ 2 h                | 7 min         |
| ≥ 4 h                | 15 min        |
| ≥ 6 h                | 25 min        |
| ≥ 8 h                | 40 min        |
| ≥ 10 h               | 50 min        |
| ≥ 12 h               | 60 min        |

> El umbral mínimo de 30 min elimina el micro-farming: por debajo no se gana nada. Los tramos viven
> como dato en la `EarningPolicy`, así que ajustarlos no toca lógica.

### Banco de tiempo

- El tiempo ganado se **deposita** en un saldo persistente.
- **Tope del saldo: 60 min.** Dormir 8 h no puede generar más de 40 min, y el saldo nunca supera 1 h.
- El **depósito ocurre al iniciar una sesión** de app bloqueada (es el momento en que la abstinencia
  termina): `saldo = min(60 min, saldo + tramo(abstinencia))`. Así abrir el launcher solo para mirar
  el saldo no "cosecha" ni duplica nada.
- El uso de apps bloqueadas **descuenta** del saldo minuto a minuto. Si sales antes de agotarlo, el
  resto se conserva (reconciliación retroactiva, ver abajo).

---

## 🏗️ Diseño (MVVM + DDD)

### Dominio puro — el corazón del sistema

Todo el estado se **deriva** del stream de eventos de UsageStats + un ancla persistida. Sin
contadores en vivo:

```kotlin
// domain/timebank/ — sin dependencias de Android, testeable con JUnit
data class TimeBankAnchor(val at: Instant, val balance: Duration)

data class EarningPolicy(
    val tiers: List<Tier>,       // (minAbstinence, earned) ordenados
    val maxBalance: Duration,    // 60 min
)

data class BlockedSession(val start: Instant, val end: Instant?) // null = sigue abierta

/**
 * Función pura: dado el ancla, las sesiones de apps bloqueadas desde el ancla
 * (derivadas de UsageStats) y el ahora, devuelve el saldo actual.
 * Deposita tramo(gap) al inicio de cada sesión, resta la duración de cada sesión.
 */
fun reconcile(
    anchor: TimeBankAnchor,
    sessions: List<BlockedSession>,
    now: Instant,
    policy: EarningPolicy,
): TimeBankAnchor

/** Cuánto ganarías si desbloquearas ahora mismo (para mostrar en UI). */
fun accruedIfUnlockedNow(lastSessionEnd: Instant, now: Instant, policy: EarningPolicy): Duration
```

Claves del diseño:

- **El tiempo se inyecta** (`now: Instant`), nunca `System.currentTimeMillis()` en dominio.
- La reconciliación es **idempotente**: ejecutarla dos veces con los mismos eventos da lo mismo.
- El "salir antes de tiempo devuelve lo no gastado" sale gratis: la siguiente reconciliación ve la
  duración real de la sesión en UsageStats y descuenta solo lo usado.

### Capa data

```kotlin
// data/UsageSessionsDataSource.kt
// Traduce UsageEvents (ACTIVITY_RESUMED/PAUSED de apps bloqueadas) a List<BlockedSession>
// data/TimeBankRepository.kt — persiste el ancla en DataStore
```

| Key DataStore          | Tipo                   | Descripción                              |
|------------------------|------------------------|------------------------------------------|
| `KEY_TIMEBANK_ANCHOR`  | `stringPreferencesKey` | `"timestampMs:balanceMs"` — ancla + saldo |

- Se **eliminan** con el tiempo: `KEY_BLOCK_TIMES` (los 1/5/10 min fijos) y su UI
  (`BlockTimePickerDialog`).
- ⚠️ UsageStats solo retiene eventos unos días → **cada apertura del launcher reconcilia y avanza
  el ancla**, así la ventana a consultar siempre es corta.

### Capa UI (con ViewModel, según CLAUDE.md)

- `TimeBankViewModel` expone `StateFlow<TimeBankUiState>`:
  `data class TimeBankUiState(val balance: Duration, val accruingNow: Duration, val abstinenceSince: Instant?)`
- **GreetingScreen**: mostrar saldo y lo que está "madurando" — p. ej.
  _"Saldo: 12 min · +7 min si desbloqueas ahora (2 h sin usar apps bloqueadas)"_. Esto convierte la
  abstinencia en algo visible y motivador.
- **BlockedAppDialog**: se elimina el selector de tiempo; muestra saldo disponible + captcha
  (fricción se mantiene). Si `saldo + acumulado < 1 min` → no se puede desbloquear, mostrar cuánto
  falta para el siguiente tramo.

### Capa service — de polling permanente a vigilancia bajo demanda

1. **Al desbloquear**: se conoce el instante exacto de expiración (`now + saldo`). El monitor
   arranca **solo entonces** y se para cuando el usuario sale de la app o expira. Opcionalmente,
   `AlarmManager.setExactAndAllowWhileIdle` como respaldo para lanzar el overlay al expirar.
2. **Caso `NOT_FROM_LAUNCHER`** (abrir app bloqueada por fuera): único caso que necesita vigilancia
   continua. Al no pasar por el launcher no hay depósito ni desbloqueo → overlay. Se puede relajar
   el intervalo (10–15 s) porque ya no hay que cronometrar nada con precisión, solo detectar la
   infracción.
3. El servicio **delega toda decisión al dominio** (`reconcile` + estado) — él solo observa y lanza
   el overlay.

---

## ⚠️ Casos borde

| Caso                                   | Comportamiento                                                                 |
|----------------------------------------|--------------------------------------------------------------------------------|
| Dormir / noche                         | El tope de tramo (60 min a las 12 h) y el tope de saldo (1 h) lo acotan solos  |
| Reinicio del móvil                     | El ancla está en DataStore; UsageStats sobrevive al reboot → se reconcilia igual |
| Cambio de hora del sistema             | Aceptado como exploit menor (UsageStats usa wall clock); no defenderse de ello  |
| Permiso UsageStats revocado            | Sin datos → no se deposita nada; pedir permiso de nuevo (flujo ya existente)    |
| App bloqueada nueva                    | Sus sesiones cuentan desde que se marca como bloqueada (sesiones previas al ancla no existen) |
| Saldo agotado a mitad de sesión        | Overlay `TIME_UP` (flujo actual)                                               |

---

## 🔀 Migración

1. Introducir dominio puro + tests (sin tocar el flujo actual).
2. Añadir reconciliación + saldo en paralelo al sistema actual (solo visible en GreetingScreen).
3. Cambiar `BlockedAppDialog` para consumir del banco; retirar `BlockTimePickerDialog` y
   `KEY_BLOCK_TIMES`.
4. Aligerar `BlockedAppMonitorService` (arranque bajo demanda + intervalo relajado para
   `NOT_FROM_LAUNCHER`).

## ❓ Preguntas abiertas

- Interacción con `FEATURE_SCHEDULE_BLOCK`: durante una franja de bloqueo duro, ¿la abstinencia
  sigue acumulando saldo? (propuesta: sí — el bloqueo duro impide gastar, no ganar).
