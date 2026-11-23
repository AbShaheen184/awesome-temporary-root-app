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
2000, manager `me.weishu.kernelsu`, and **the exploit itself ran and rooted the boot** —
`/proc/modules` showed `kernelsu … Live` and `/data/adb` appeared root-owned. See
`HANDOFF.md` §6e for the evidence and the eight bugs fixed to get there.

## The one thing to do next

**One cold-boot run of this exact build.** Root is per-boot, and the successful run
predates the final `kmi=null` fix. So: reboot the device (which also clears the parked
fake-cred child), install the APK, tap RUN BEST MATCH, and read the result from logcat
now that the UI log is mirrored there:

```bash
adb logcat -d | grep -E "LATE_LOAD_RC=|MODULE_STATE=|PLANT_CRED_OK|KSUD_MISSING|LATE_LOAD_SKIP"
```

A run that prints `LATE_LOAD_SKIP=env-missing` or exits 0 without `LATE_LOAD_RC=0` is a
**false** success — the app now reports those as failures, do not trust the exit code.

Then finish the manager's own first-run setup so `su` lands in PATH; until then root is
only visible as a live module, not as a shell (`su: inaccessible or not found`).

## Do not

- Do not trust `Shizuku.checkSelfPermission()` — it reports DENIED until the manager UI
  has shown the client once. Probe binder uid / `newProcess`.
- Do not offer a candidate whose kernel release is not an exact match.
- Do not edit `loaders.json` by hand.
- Do not run boot-chain/BROM tools; they are reference-only in the index.

## When you finish

Commit, push to `origin master`, and update `HANDOFF.md` with what you verified —
marking unverified things explicitly. Keep the *verified vs attempted* split.
