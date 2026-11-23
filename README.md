# awesome-temporary-root-app

One Android app that detects the device and kernel, then runs the temporary-root
loader that actually matches. Built on the compatibility index from
[fynks/awesome-temporary-root](https://github.com/fynks/awesome-temporary-root)
and the 122 upstream projects it indexes.

## What it does

- Reads the device facts: model, codename, fingerprint, build, Android/SPL, ABI,
  `uname -r`, page size, SELinux state, KernelSU state.
- Matches them against `assets/loaders.json` (109 loaders, 394 targets) — **exact
  kernel release or exact model only**, never a near match, which is the rule every
  upstream loader enforces.
- Shows the ranked candidates, the derived KMI (`android15-6.6`), and a streamed run log.
- Runs through Shizuku (shell uid) when it is available, and says so plainly when it
  is not; the direct path is reported rather than failing silently.

## Build

```bash
cd app  # or the project root
export JAVA_HOME="<jdk-21>"
export ANDROID_HOME="<sdk>"
./gradlew --no-daemon :app:assembleDebug
```

JDK 21 specifically: the Kotlin 2.0 Gradle plugin rejects the JDK 25/27 version
strings. NDK/CMake paths are pinned in `gradle.properties`.

APK output: `app/build/outputs/apk/debug/app-debug.apk`.

## Layout

| path | what |
|---|---|
| `tools/build_index.py` | regenerates `loaders.json` from the cloned upstream repos |
| `app/src/main/assets/loaders.json` | the index the app reads |
| `app/.../MainActivity.kt` | screen wiring, detection, run + grant flows |
| `app/.../ui/MainScreen.kt` | the UI (plain Views, no Compose) |
| `app/.../DeviceProbe.kt`, `DeviceProfile.kt` | device facts |
| `app/.../IndexStore.kt` | index parsing, exact matching, KMI derivation |
| `app/.../Transport.kt` | Shizuku / su / local-ADB execution channels |
| `app/.../Runner.kt` | per-loader exec contracts |
| `HANDOFF.md` | full state, verified runner contracts, next steps |

## Status

Detection, matching and the run path work and were verified on a vivo V2408A
(iQOO 13, kernel `6.6.127-android15-8-gd489d4ad2a70-abogki567717946-4k`, Android 17,
SPL 2026-10-01): it resolves to exactly one loader, **Shark** (`CVE-2026-98260`).

Runner branches are wired for the `shark`, `timerroot`, `root-my-pixel`,
`ghostlock-app`/`ghostkey` and DirtyFrag families; the rest return a clear
"not wired yet" result until their contract is ported. See `HANDOFF.md` §7.

## Warning

Temporary root is full root for the session. A wrong profile or a lost race can
panic the device; an incompatible module can bootloop it. Only run loaders that
match the exact kernel and build, on hardware you own. A full reboot clears root.
