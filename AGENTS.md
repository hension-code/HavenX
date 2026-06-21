# AGENTS.md

Project context for the Haven (HavenX) Android app — a free SSH, VNC, RDP & SFTP client for Android.
Fork of [GlassOnTin/Haven](https://github.com/GlassOnTin/Haven), with additional modifications by `hension-code`.

## Tech stack

- **Language:** Kotlin 2.3.10, JVM target 17 (`kotlin.code.style=official`)
- **Build:** Gradle 8.9.1 (Kotlin DSL), version catalog at `gradle/libs.versions.toml`
- **Android:** compileSdk 36, minSdk 26, targetSdk 35
- **UI:** Jetpack Compose (BOM 2026.01.01), Material 3. No XML views / fragments.
- **DI:** Hilt 2.58 + KSP. `@HiltAndroidApp` on `HavenApp`; `@AndroidEntryPoint` on activities.
- **Persistence:** Room 2.7.1 (`haven.db`, migrations listed in `core/data/.../DatabaseModule.kt`, currently through v19) + DataStore preferences.
- **Networking/transport:** JSch (mwiede fork), BouncyCastle, pure-Kotlin Mosh/ET transports, IronRDP via UniFFI/JNA, SMBJ, YubiKit FIDO2.
- **Terminal:** `org.connectbot:termlib` (built from local submodule fork).
- **Python:** Chaquopy plugin embeds Python 3.13 with `rns` + a patched local `rnsh` wheel for Reticulum support.

## Module layout

Multi-module project declared in `settings.gradle.kts`. Package roots: `com.hension.havenx` (app), `sh.haven.*` (core/feature).

- `app` — `MainActivity`, `HavenApp`, navigation, biometric lock, reticulum bridge, text editor / image / player activities.
- `core:ui` — `HavenTheme`, `KeyEventInterceptor`.
- `core:ssh` — `SshConnectionService`, `SessionManager`, host key verification, proxy jump.
- `core:security` — `BiometricAuthenticator`.
- `core:data` — Room entities/DAOs/repositories, `UserPreferencesRepository`, backup.
- `core:reticulum` / `core:mosh` / `core:et` / `core:vnc` / `core:rdp` / `core:smb` / `core:fido` — transport/domain modules.
- `feature:connections` / `feature:terminal` / `feature:sftp` / `feature:keys` / `feature:settings` / `feature:vnc` / `feature:rdp` — Compose screens + ViewModels.

Included builds (submodules, see `.gitmodules` + comments in `settings.gradle.kts`):
`termlib`, `et-kotlin`, `mosh-kotlin`, `rdp-kotlin` — each substitutes a published coordinate with the local project. Clone with `--recurse-submodules`.

## Build & test

```bash
# Debug APK (Windows)
.\gradlew.bat assembleDebug
# macOS/Linux
./gradlew assembleDebug

# Unit tests + lint (matches CI)
./gradlew testDebugUnitTest lintDebug
```

Output: `app/build/outputs/apk/<abi>/debug/havenx-*-<abi>-debug.apk`.

- **Product flavors:** `arm64` (arm64-v8a), `x64` (x86_64) on the `abi` dimension.
- **Version codes:** `versionCode * 10 + offset` (arm64=1, x64=2). Bump `versionCode`/`versionName` in `app/build.gradle.kts` for releases.
- **Signing:** `haven-release.jks` in repo root; release builds read `KEYSTORE_PASSWORD` / `KEY_ALIAS` / `KEY_PASSWORD` from env / GitHub secrets.
- **Tests:** JUnit 4, MockK, `kotlinx-coroutines-test`, Turbine, Robolectric. Feature/library modules set `unitTests.isReturnDefaultValues = true`.
- When adding dependencies, prefer the version catalog (`libs.versions.toml`) over inline coordinates — though a few app-level deps (coil, media3) are declared inline.

## Architecture conventions

- **Navigation:** `HavenNavHost` uses a `HorizontalPager` across top-level screens (`Screen` enum), not Jetpack Navigation. Cross-screen handoffs pass state via `rememberSaveable` "pending*" variables consumed by `LaunchedEffect`. Preserve this pattern when wiring new destinations.
- **DI modules:** Hilt `@Module @InstallIn(SingletonComponent::class) object` modules per concern (e.g. `DatabaseModule`, `DataStoreModule`). Add `@Provides` for new DAOs/repositories there.
- **State:** `StateFlow` exposed from repositories/ViewModels; screens `collectAsState`. User prefs live in `UserPreferencesRepository` (DataStore-backed `Flow`s + enum config types like `ThemeMode`, `LockTimeout`, `ToolbarLayout`).
- **Compose:** single-activity, screen composables in `feature:*` modules with paired `*ViewModel`. Match the existing comment density and naming (PascalCase composables, camelCase handlers, `onNavigateTo*` callbacks).
- **Database migrations:** every schema change needs a numbered `MIGRATION_n_(n+1)` in `HavenDatabase` and must be appended to the `addMigrations(...)` chain in `DatabaseModule.kt`.

## Commit & release style

- Commit messages are often in Chinese (`feat:`/`fix:`/`优化`/`新增`). Match the surrounding tone; keep the conventional-commit prefix.
- Release: bump `versionName`/`versionCode`, tag `v<x.y.z>`, push tag — `v*` tag triggers `.github/workflows/release.yml` (signed APK+AAB). See `RELEASE.md` for the full F-Droid/Play procedure.

## Do not

- Don't commit `local.properties`, `*.jks`/`*.keystore`, `build/`, or `*.apk` (see `.gitignore`).
- Don't drop the `includeBuild` substitutions in `settings.gradle.kts` — the published coordinates are intentionally replaced by local submodule builds.
- No telemetry/analytics — keep the app local-only (see `PRIVACY_POLICY.md`).
