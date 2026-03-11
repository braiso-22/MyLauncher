# 📱 MyLauncher

Launcher personalizado para Android desarrollado íntegramente en **Kotlin** con **Jetpack Compose**. Reemplaza el launcher nativo del dispositivo y ofrece una experiencia minimalista basada en texto, sin iconos de apps, centrada en productividad y control de uso.

---

## 🏗️ Arquitectura y Estructura

El proyecto sigue una estructura de módulo único (`app`) con una separación básica en capas:

| Capa | Contenido |
|------|-----------|
| **`domain/`** | Modelo de dominio (`App`) con value classes validadas (`Name`, `PackageName`) y repositorio de datos (`AppRepository`) |
| **Pantallas (root)** | Composables de pantalla: `GreetingScreen`, `AppListScreen`, `LauncherPager`, `BlockedAppDialog` |
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
Permite bloquear apps individualmente. Al intentar abrir una app bloqueada, aparece un **diálogo con temporizador de 10 segundos** (barra de progreso animada). Solo se puede acceder a la app una vez completada la espera, lo que desincentiva el uso impulsivo.

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
| Build system | Gradle (Kotlin DSL) con Version Catalog |

---

## 🎨 Características de Diseño

- **Interfaz minimalista** — solo texto, sin iconos de apps, estilo limpio.
- **Soporte de temas** — Light y Dark mode.
- **Edge-to-edge** — aprovecha toda la pantalla del dispositivo.
- **Single Activity** — toda la app funciona dentro de una única `MainActivity`.

---

## 💡 Puntos Destacados

1. **Anti-distracción** — el bloqueo con temporizador obliga a una pausa reflexiva antes de abrir apps potencialmente distractoras.
2. **Sin iconos** — al no mostrar iconos de apps, reduce el estímulo visual y fomenta un uso más consciente del dispositivo.
3. **Acceso rápido inteligente** — combina favoritos en la pantalla de inicio con la última app abierta en la lista.
4. **Launcher nativo** — se registra mediante intent-filters `HOME` + `DEFAULT` como reemplazo del launcher del sistema.

