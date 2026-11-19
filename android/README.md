# App Android (entrega 1 de la etapa 2)

Kotlin + Jetpack Compose. `minSdk 26`, `compileSdk`/`targetSdk 36` (Android 16).

## Módulos

- `:app` — actividad, pantalla principal y estado de la conversación.
- `:core:ui` — tema JARVIS (negro, azul eléctrico, cian), círculo central animado y onda de voz.

## Cómo abrirlo

1. Android Studio **Meerkat (2024.3.1) o más reciente** (el proyecto usa AGP 8.9.1 y Kotlin 2.1.20).
2. File → Open → selecciona esta carpeta `android`. Deja que sincronice Gradle (usa JDK 17).
3. Conecta el móvil con depuración USB y pulsa Run.
4. Tests unitarios: `./gradlew test`.

## Qué hace esta entrega

Solo la base visual: el círculo central cambia de estado al tocarlo
(reposo → escuchando → pensando → reposo; al hablar, un toque interrumpe y vuelve a escuchar).
**Todavía no hay voz ni conexión con Claude**: el estado «escuchando» no activa el micrófono.
Eso llega en la entrega 2 (voz) y la 3 (backend). No hay botones de calendario, tareas ni ajustes
porque esas pantallas aún no existen.

Este código no se ha compilado ni ejecutado: el entorno donde se escribió no puede descargar
dependencias de Android. Si Gradle o el compilador dan errores, pásamelos tal cual.
