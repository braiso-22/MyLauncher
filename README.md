# 📱 MyLauncher

Launcher personalizado para Android desarrollado íntegramente en **Kotlin** con **Jetpack Compose**. Reemplaza el launcher nativo del dispositivo y ofrece una experiencia minimalista basada en texto, sin iconos de apps, centrada en productividad y control de uso.

---

## 🏗️ Arquitectura y Estructura

El proyecto sigue una estructura de módulo único (`app`) con una separación básica en capas:

| Capa | Contenido |
|------|-----------|
| **`domain/`** | Modelo de dominio (`App`) con value classes validadas (`Name`, `PackageName`) y repositorio de datos (`AppRepository`) |
| **`service/`** | Servicio en segundo plano (`BlockedAppMonitorService`) y detector de app en primer plano (`ForegroundAppDetector`) |
| **Pantallas (root)** | Composables de pantalla: `GreetingScreen`, `AppListScreen`, `LauncherPager`, `BlockedAppDialog`, `BlockTimePickerDialog`, `OverlayActivity` |
| **`ui/theme/`** | Tema Material 3 (colores, tipografía, tema) |

La app no usa ViewModels ni inyección de dependencias. El estado se gestiona directamente en los composables con `remember` y `StateFlow` expuestos desde el repositorio.

---

## 📲 Pantallas Principales

### Pantalla de Inicio (Greeting)
- Muestra un **reloj en tiempo real** (hora y fecha) con actualización cada segundo.
- Lista de **apps favoritas** con acceso directo rápido.
- Las apps bloqueadas también aparecen, pero requieren confirmación antes de abrirse.

### Pantalla de Lista de Apps
- Muestra **todas las apps instaladas** ordenadas alfabéticamente.
- **Buscador con debounce** para filtrar apps en tiempo real.
- Muestra la **última app abierta** como acceso rápido en la parte superior.
- **Menú contextual** (long press) para cada app con opciones de favorito y bloqueo.
- Indicadores visuales: estrella para favoritas, opacidad reducida para bloqueadas.
- **Filtro de apps bloqueadas** — chip interactivo que permite alternar entre ver todas las apps o solo las bloqueadas.

### Navegación
- Navegación entre pantallas mediante **HorizontalPager** (swipe lateral):
  - **Página 0**: Pantalla de inicio con reloj y favoritos.
  - **Página 1**: Lista completa de apps.
- La búsqueda se limpia automáticamente al salir de la lista de apps.

---

## ✨ Funcionalidades Clave

### ⭐ Sistema de Favoritos
Cualquier app puede marcarse/desmarcarse como favorita. Las favoritas aparecen como accesos directos en la pantalla de inicio. El estado es persistente entre sesiones.

### 🔒 Sistema de Bloqueo de Apps
Permite bloquear apps individualmente con **tiempo de uso personalizable**. Al bloquear una app se muestra un **selector de tiempo** (`BlockTimePickerDialog`) donde el usuario elige cuántos minutos puede usar la app antes de recibir un aviso (1, 5 o 10 minutos).

Al intentar abrir una app bloqueada, se presenta un flujo de desbloqueo en dos fases:

1. **Temporizador de espera** — diálogo con una **barra de progreso animada de 10 segundos** que obliga a una pausa reflexiva.
2. **Verificación captcha** — al completarse la espera, se muestra un **código alfanumérico aleatorio de 6 caracteres** (fuente monoespaciada) que el usuario debe escribir exactamente igual en un campo de texto. El botón "Entrar" solo se habilita cuando el código introducido coincide.

Este doble mecanismo (espera + captcha) desincentiva el uso impulsivo de apps bloqueadas.

### ⏱️ Monitorización de Tiempo de Uso
Un **servicio en segundo plano** (`BlockedAppMonitorService`) se ejecuta como Foreground Service y monitorea continuamente si la app bloqueada en uso ha excedido el tiempo permitido:

- Cada 5 segundos comprueba si el tiempo transcurrido supera el límite configurado.
- Usa `ForegroundAppDetector` (basado en `UsageStatsManager`) para determinar qué app está en primer plano.
- Si se excede el tiempo y la app bloqueada sigue en primer plano, lanza la **pantalla de bloqueo overlay**.

### 🚫 Overlay de Bloqueo (`OverlayActivity`)
Cuando se agota el tiempo permitido de una app bloqueada, se muestra una **Activity transparente a pantalla completa** sobre la app:

- Fondo oscuro semitransparente con una tarjeta central que indica "App bloqueada — Se acabó el tiempo".
- Botón "Cerrar app" que redirige al launcher (pantalla de inicio).
- **No se puede cerrar** con el botón atrás ni tocando fuera del diálogo.

### 🕐 Última App Abierta
Se registra la última app lanzada y se muestra como acceso rápido en la lista de apps.

### 🔍 Búsqueda con Debounce
Campo de búsqueda con debounce de 300ms para evitar filtrado excesivo mientras el usuario escribe. El foco del teclado se gestiona automáticamente según la página activa.

---

## 💾 Persistencia de Datos

Se usa **DataStore Preferences** para almacenar:
- Conjunto de apps favoritas.
- Conjunto de apps bloqueadas.
- Última app abierta.
- **Tiempos de bloqueo por app** (minutos permitidos antes del overlay).
- **App bloqueada en uso** (package name y timestamp de apertura) para que el servicio de monitorización controle el tiempo.

El repositorio expone los datos como `StateFlow` reactivos, consumidos directamente por los composables.

---

## 🛠️ Stack Tecnológico

| Aspecto | Tecnología |
|---------|-----------|
| Lenguaje | Kotlin |
| UI | Jetpack Compose + Material 3 |
| Persistencia | DataStore Preferences |
| Colecciones | kotlinx-collections-immutable |
| Concurrencia | Coroutines + StateFlow |
| Servicios | Foreground Service (monitorización de apps bloqueadas) |
| Detección de uso | UsageStatsManager (UsageEvents + queryUsageStats) |
| Build system | Gradle (Kotlin DSL) con Version Catalog |

---

## 🔐 Permisos

| Permiso | Uso |
|---------|-----|
| `QUERY_ALL_PACKAGES` | Listar todas las apps instaladas en el dispositivo |
| `FOREGROUND_SERVICE` | Ejecutar el servicio de monitorización en segundo plano |
| `FOREGROUND_SERVICE_SPECIAL_USE` | Tipo especial de Foreground Service para monitorización |
| `PACKAGE_USAGE_STATS` | Detectar qué app está en primer plano (requiere concesión manual en Ajustes) |

---

## 🎨 Características de Diseño

- **Interfaz minimalista** — solo texto, sin iconos de apps, estilo limpio.
- **Soporte de temas** — Light y Dark mode.
- **Edge-to-edge** — aprovecha toda la pantalla del dispositivo.
- **Single Activity** — toda la app funciona dentro de `MainActivity` + `OverlayActivity` (transparente, solo para bloqueo).

---

## 💡 Puntos Destacados

1. **Anti-distracción reforzada** — triple barrera: temporizador de espera + captcha alfanumérico + límite de tiempo de uso con overlay de bloqueo forzoso.
2. **Sin iconos** — al no mostrar iconos de apps, reduce el estímulo visual y fomenta un uso más consciente del dispositivo.
3. **Acceso rápido inteligente** — combina favoritos en la pantalla de inicio con la última app abierta en la lista.
4. **Launcher nativo** — se registra mediante intent-filters `HOME` + `DEFAULT` como reemplazo del launcher del sistema.
5. **Monitorización activa** — servicio en segundo plano que vigila el uso de apps bloqueadas y actúa automáticamente al exceder el tiempo.
6. **Tiempos configurables** — cada app bloqueada puede tener un tiempo de uso diferente (1, 5 o 10 minutos).

