# Sheqeri Im — Android Native

Native Kotlin / Jetpack Compose Android application, developed independently from FitPlan.

## Current status
- Native Android project and dark UI: implemented
- Albanian / German / English UI selection: initial implementation
- Bluetooth LE device discovery: initial implementation (permissions + scanning)
- Accu-Chek Guide authenticated connection and automatic glucose sync: **NOT implemented / NOT tested**
- Registration/login, per-user private profiles, encrypted storage, medication log, AI, charts and German PDF report: **planned, not implemented**

**Do not use this prototype for insulin dosing or clinical decisions.**

## Build
GitHub Actions workflow builds a debug APK artifact after commits. Local build: `gradle :app:assembleDebug` (Android SDK/JDK 17 required).

## Security
Never commit glucose readings, patient identifiers, passwords, API keys, or medical records. Repository may be public. No backend is configured.
