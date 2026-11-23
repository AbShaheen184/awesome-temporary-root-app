# RESUME HERE — read this first

Continuing agent: everything needed is in this repo. Do **not** rely on the chat
transcript; it contains dead ends that were later superseded.

## Read in this order

1. `HANDOFF.md` — full state, verified runner contracts, toolchain setup, pitfalls.
   Pay attention to **§6c** (what was verified on device) and **§6d** (the later fixes
   and the explicit *"Not yet verified"* subsection).
2. This file (`RESUME.md`) — the single next action and the environment facts.
3. Load the skill `android-temporary-root-loaders` — it encodes the matching rules,
   KMI derivation, Shizuku probe and the build invocation.

## Where things are

| path | what |
|---|---|
| `G:\Dev\AwesomRootApp\` | this Android project (remote `origin` = `AbShaheen184/awesome-temporary-root-app`, branch `master`) |
| `G:\Dev\AwesomRoot\` | the 122 cloned upstream repos; `CATALOG.md` lists them |
| `G:\Dev\AwesomRootApp\app\src\main\assets\loaders.json` | canonical index (109 loaders / 394 targets); regenerate with `tools/build_index.py`, never hand-edit |
| `G:\Dev\AwesomRootApp\HANDOFF.md` | the source of truth |
| `G:\Dev\Tools\` | JDK 21 (`jdk-21.0.13.1+2`), NDK r28, CMake 3.31.6 |

## Build + install (verified)

```bash
cd "G:/Dev/AwesomRootApp"
export JAVA_HOME="G:/Dev/Tools/jdk-21.0.13.1+2"
export ANDROID_HOME="C:/Users/abuza/AppData/Local/Android/Sdk"
export ANDROID_NDK_HOME="G:/Dev/Tools/android-ndk-r28"
./gradlew --no-daemon :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

JDK 21 is required — the Kotlin 2.0 Gradle plugin throws `IllegalArgumentException`
on JDK 25/27 version strings. `gradle.properties` pins `ndk.dir`/`cmake.dir`.

## Current state

Working and verified on a vivo V2408A (iQOO 13): device card, KMI, exact-match
candidate list (one hit: **Shark**, `CVE-2026-98260`), Shizuku transport live as uid
2000, manager `me.weishu.kernelsu`, and enabled RUN BEST MATCH button.

Fixed since the last push: disabled-button bug (`setCandidates` did not call
`renderButtons`), wrong runner filename (`libCVE66127.so` → `libk66127.so`),
Shark runners now bundled in `jniLibs/arm64-v8a/`.

## The one thing to do next

**Confirm or refute the actual exploit run.** It has NOT been verified end-to-end.
Launch fresh, tap RUN BEST MATCH, then read the result by grepping logcat for the
runner's own markers rather than trusting the on-screen log:

```bash
adb logcat -d | grep -E "LATE_LOAD_RC=|MODULE_STATE=|PLANT_CRED_OK|KSUD_MISSING"
```

Check the prerequisite first — the loader stages `ksud` out of the manager's APK:

```bash
adb shell pm path me.weishu.kernelsu
adb shell ls -l <that dir>/lib/arm64/libksud.so
```

If `libksud.so` is absent, root cannot happen on this device and the app should say
so instead of running. Also fix `Runner.runShark()`: it hardcodes
`PYB110_MANAGER=kernelsu` instead of using the selected manager kind.

## Do not

- Do not trust `Shizuku.checkSelfPermission()` — it reports DENIED until the manager UI
  has shown the client once. Probe binder uid / `newProcess`.
- Do not offer a candidate whose kernel release is not an exact match.
- Do not edit `loaders.json` by hand.
- Do not run boot-chain/BROM tools; they are reference-only in the index.

## When you finish

Commit, push to `origin master`, and update `HANDOFF.md` with what you verified —
marking unverified things explicitly. Keep the *verified vs attempted* split.
