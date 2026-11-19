# Antonia

Asistente personal por voz para Android, con Claude como cerebro.

- `android/`: la app (Kotlin + Jetpack Compose).
- `backend/`: servidor que protege la clave de Anthropic y ejecuta herramientas.
- `.github/workflows/`: compilación automática. Cada cambio en `android/` genera un APK
  en la sección **Releases** del repositorio.

Nunca se suben claves ni contraseñas a este repositorio.
