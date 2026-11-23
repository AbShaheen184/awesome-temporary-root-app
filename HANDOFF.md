# AwesomRootApp — build state and handoff

> **Read this first.** It is the single source of truth for whoever (human or agent)
> continues this project. Last updated 2026-11-23.
> Everything here is verified against the machine unless it is marked **UNVERIFIED**.

---

## 1. What was asked

1. Clone every GitHub repo referenced by `fynks/awesome-temporary-root` into `G:\Dev\AwesomRoot`. **DONE.**
2. Build one Android app that detects the device, kernel and required parameters, then runs the
   right exploit — a single app combining all of the cloned solutions.

## 2. Deliverables so far

| Path | What it is | State |
|---|---|---|
| `G:\Dev\AwesomRoot\` | 122 cloned upstream repos, `owner/repo` layout | **DONE** (122/122, 2.1 GB, 26,851 files) |
| `G:\Dev\AwesomRoot\repos.json` | the slug list that was cloned | DONE |
| `G:\Dev\AwesomRoot\clone_all.sh` | re-runnable cloner/updater | DONE |
| `G:\Dev\AwesomRoot\CATALOG.md` | repo → HEAD table | DONE |
| `G:\Dev\AwesomRootApp\tools\build_index.py` | generates the canonical loader index from the cloned repos | DONE, runs clean |
| `G:\Dev\AwesomRootApp\app\src\main\assets\loaders.json` | **the index the app reads** (109 loaders, 394 targets) | DONE |
| `G:\Dev\AwesomRootApp\tools\loaders.json` | identical copy for tooling | DONE |
| `G:\Dev\AwesomRootApp\HANDOFF.md` | this file | DONE |

## 3. Environment (verified)

```
Windows 11, git-bash (MSYS). Host is NOT the username; user home is C:\Users\abuza.
python3 = 3.15.0 (has pip 26.2.1).  python3.14 = Hermes' own (no pip).
java on PATH = 27.0.1 (Oracle).  Android Studio JBR = 25.0.4 at
    C:\Program Files\Android\Android Studio\jbr
SDK = C:\Users\abuza\AppData\Local\Android\Sdk
    build-tools: 35.0.0, 36.0.0
    platforms:   android-34, 35, 36, 36.1, 37.0
    cmdline-tools/latest  (installed by hand, see §6)
NDK / CMake: installing (see §6) — NOT present at the start of the task.
Rust: NOT installed.  gradle CLI: NOT installed.  cmake: NOT installed.
Disk: G: 26 GB free, C: 21 GB free.
JAVA_HOME in the login shell points at a non-existent C:/Users/abuza/temurin21/... —
sdkmanager refuses to run with it. Always export JAVA_HOME to the Studio JBR first.
```

### Toolchain gotchas that cost real time

- **Native paths are not translated.** `git -C /c/Users/x` fails; use `C:/Users/x`.
- **`strings` is not installed** and `rg` is (ripgrep binary at `%LOCALAPPDATA%\hermes\tools\ripgrep-15.2.0-win32-x64`).
  To inspect ELF/arm64 binaries in Python use a byte scan:
  `re.findall(rb'[\x20-\x7e]{6,}', open(p,'rb').read())`.
- `du -sh` on the 2 GB clone tree exceeds the 180 s foreground timeout — walk in Python instead.
- `yes | sdkmanager --licenses` then `--install` needs `JAVA_HOME` set correctly, and
  `--sdk_root` must be passed because the bat file otherwise guesses.

## 4. What the cloned repos actually are (the important part)

Two families, and they are **not** interchangeable:

**A. On-device loader apps (Kotlin/Java, ship prebuilt arm64 `.so` runners).**
These are what a unified app can actually drive. Verified examples:

| Repo | Runner mechanism | Detection key |
|---|---|---|
| `CYB3R0ID694/Shark-Moto` (fork of `kpgc10kai/Shark`) | exact `uname -r` → `libk<MODEL>.so` in `jniLibs`, env `PYB110_*`, runner does the whole chain | exact kernel release string |
| `MVN123123123123123/TimerRoot` | downloaded `timer` binary + `conf/<release>[-16k].conf` HOCON profiles, selected by `uname -r` + page size | kernel release + `getconf PAGESIZE` |
| `alex193a/Root-My-Pixel` | `assets/exploits/<codename>-<build>.so` chosen from `assets/profiles.json` | codename + build display + kernel release |
| `YuKongA/ghostlock-app` | `libghostlock.so` + `assets/kernel_profiles/<release>.conf` (HOCON index, 83 profiles) | exact `uname -r` |
| `mitschud/DirtyFrag` | `libdfroot.so` + `jni/ko/dfroot-<kmi>.ko` (8 KMIs) | KMI |
| `BuSung-dev/Root-My-Galaxy-Payloads` | `support/targets-v3.json`: `models[]` + `kernelVersions[]` → `.so` URL | model + kernel |
| `zenyxx-xd/RootMyVivo-Payloads` | `catalog/devices.json` builds → match + `.so` | kernel release |
| `WitAqua-tools/Root-My-Device-Payloads` | `src/targets.json`: full `/proc/version` + fingerprint | device + region + kernel |
| `qq2070006042-create/yuehong-privilege-assistant` | ~120 bundled `libksu_*.so`, vendor/device in the filename | vendor + device |

**B. PC-side / ADB / shell projects (C, Python, Rust source, no Android app).**
`JoinChang/ghostlock-oneplus` (C + `tools/extract_*.py`), `NebuSec/CyberMeowfia`,
`ihamn/matisse-public`, `bkerler/mtkclient` (Python), `R0rt1z2/kaeru`,
`SuperTurtleDev/gbl_root_canoe` (C/UEFI), etc. These **cannot** be run from an Android app;
they need a host. They are in the index as `class: research|boot-chain|tool` so the app can
show them but must not offer to "run" them.

**Also present:** root managers (KernelSU, KernelSU-Next, KowSU, SukiSU Nova, BakaSU,
diabl0w KernelSU, Samsung Root Managers) and transports (Shizuku, Stellar, Obtainium,
Termux, Bifrost). A loader needs a manager that ships `libksud.so`; several loaders
(`Shark`, `Root My Galaxy Next`) stage `ksud` from the installed manager's
`nativeLibraryDir/libksud.so` and refuse to run without it.

### Verified runner CLI contracts (do not guess these)

- **Shark** — `libk<model>.so run` executed through the privileged transport with env:
  `PYB110_KMI`, `PYB110_MANAGER`, `PYB110_MANAGER_PKG`, `PYB110_DIR`, `PYB110_LATE_LOAD=1`,
  `PYB110_UNIVERSAL_KSUD`, `PYB110_SUSFS`, `PYB110_VFS`, `PYB110_CHASE`,
  `PATH=/system/bin:/system/xbin:/data/adb/ksu/bin`, `HOME=/`.
  Runner prints `LATE_LOAD_RC=`, `MODULE_STATE=`, `PLANT_CRED_OK`.
- **TimerRoot** — `/data/local/tmp/timer <conf> [fake]`, conf from `conf/`; Samsung chain
  appends `fake` and needs the bundled diabl0w `libksud.so`.
- **GhostLock App** — `libghostlock.so --ghostlock-app-call` (profile on stdin) or
  `--load-prebuilt-profile <bin>`; workdir `/data/local/tmp/ghostlock-app`, env `GHOSTLOCK_HOME`.
- **Root My Pixel** — extracts `.so` from assets to `/data/local/tmp`, then
  `ksud late-load --kmi <kmi>`; success is KernelSU UAPI check.

### ABI finding (verified by ELF header read — corrected)

`e_machine` is at **offset 18** in both ELF32 and ELF64 (`e_ident` 16 bytes + `e_type` 2).
Read it with `struct.unpack_from('<' + 'H', d, 18)`.

Result across all 593 `.so`/`.ko` files in the clone:

| count | machine | examples |
|---|---|---|
| **590** | **ELF64 aarch64** | every loader payload — `libk61821.so`, `libksud_universal.so`, `lynx-CP2A.260705.006.so`, `dfroot-android17-6.18.ko`, `p7a/timer_p7a` |
| 2 | ELF32 arm | `make-it-root/dist/libglroot.so`, one yuehong payload |
| 1 | ELF64 x86-64 | `starseed12345/QuestStack/native/linux-x64/libusb-1.0.so` (a host-side USB lib, correct) |
| 1 | not ELF | `kuuky29/UniRoot/.../s26u-zzhk/kernelsu.ko` (stub/text) |

**Conclusion: the payloads are genuine arm64.** The app can bundle and exec them.
(An earlier version of this doc reported i386/x86 because of a wrong header offset — that was
the bug, not the data. Fixed here.)

## 5. Index format (canonical)

`loaders.json`, `schema_version: 1`:

```jsonc
{
  "schema_version": 1,
  "loader_count": 109,
  "target_count": 394,
  "loaders": [{ "id","name","cve","klass","repo","upstream","transport","runner",
                "kmi_from","status" }],
  "targets": [{ "loader","match_kind","match_key","kernel","kmi","devices",
                "page","runner","note","source","extra" }]
}
```

`klass` ∈ `kernel-lpe | limited-* | boot-chain | manager | transport | tool | research | multi-method`.
**Only `klass: kernel-lpe` (and `multi-method`) entries are runnable from the app.**
`match_kind` ∈ `kernel-release | kernel-release+page | kmi | model+kernel | codename+build+kernel |
device+region+release | bundled-payload-name | branch | spl | n/a`.

Regenerate with:

```bash
cd "G:/Dev/AwesomRootApp/tools" && python build_index.py
```

It reads the repos' own tables (payload feeds, `conf/` files, README tables) — nothing is
hand-typed that a repo can supply. `source` on every target names the file it came from.

## 6. Toolchain install status

`install_toolchain.sh` in `_toolchain/` is a first attempt that had two bugs:
1. it `mv`'d `cmdline-tools` into `.../latest` without removing the existing dir, leaving a
   nested `latest/cmdline-tools`;
2. it assumed a `JAVA_HOME` that does not exist.

**Fixed path that works** (run in bash):

```bash
SDK="$LOCALAPPDATA/Android/Sdk"
export JAVA_HOME="C:/Program Files/Android/Android Studio/jbr"
rm -rf "$SDK/cmdline-tools/latest" "$SDK/.cx"
mkdir -p "$SDK/.cx" && curl -sL -o "$SDK/.cx/c.zip" \
  "https://dl.google.com/android/repository/commandlinetools-win-11076708_latest.zip"
unzip -q -o "$SDK/.cx/c.zip" -d "$SDK/.cx/x"
mkdir -p "$SDK/cmdline-tools" && mv "$SDK/.cx/x/cmdline-tools" "$SDK/cmdline-tools/latest"
rm -rf "$SDK/.cx"
yes | "$SDK/cmdline-tools/latest/bin/sdkmanager.bat" --sdk_root="$SDK" --licenses
"$SDK/cmdline-tools/latest/bin/sdkmanager.bat" --sdk_root="$SDK" \
  --install "ndk;28.0.13004108" "cmake;3.31.6"
```

Available (from `--list`): `ndk;28.0.13004108` is the newest 28.x; `cmake;3.31.6` newest 3.x
(`3.42.0` does **not** exist; 4.x is 4.0.2+). A background install was started (session
`proc_f56384ca49cb`); verify with `ls "$SDK/ndk" "$SDK/cmake"`.

## 6b. Gradle build — exact working invocation (verified)

```bash
cd "G:/Dev/AwesomRootApp"
export JAVA_HOME="G:/Dev/Tools/jdk-21.0.13.1+2"
export ANDROID_HOME="C:/Users/abuza/AppData/Local/Android/Sdk"
export ANDROID_NDK_HOME="G:/Dev/Tools/android-ndk-r28"
./gradlew --no-daemon :app:assembleDebug
```

**JDK matters.** The Kotlin 2.0.21 Gradle plugin carries an old embedded
`JavaVersion.parse` that throws `IllegalArgumentException` on the version strings of
JDK 25 and 27 (`25.0.4`, `27.0.1`). Neither the Studio JBR (25) nor
`C:\Program Files\Java\jdk-27.0.1` works. Use **JDK 21**.

**All downloaded tools were moved to `G:\Dev\Tools`** (which already held
`jdk-17.0.11+9`, `android-ndk-r30`, `apktool.jar`, `jadx.jar`, `cloudflared`,
`elf_analyzer.py`, `jni_focus.py`, `rea_venv`). Now there:

| Path | Note |
|---|---|
| `G:\Dev\Tools\jdk-21.0.13.1+2` | Temurin 21 — **use this to build** |
| `G:\Dev\Tools\jdk-17.0.11+9` | was already there |
| `G:\Dev\Tools\android-ndk-r28` | NDK 28.0.13004108 (2.2 GB), moved out of the SDK |
| `G:\Dev\Tools\android-ndk-r30` | was already there, unextracted (zip alongside) |
| `G:\Dev\Tools\cmake-3.31.6` | CMake 3.31.6, moved out of the SDK |

`gradle.properties` pins `ndk.dir`/`cmake.dir` to those paths. The `sdk.dir` stays at
`%LOCALAPPDATA%\Android\Sdk` for `platforms`/`build-tools` (35.0.0, 36.0.0, android-34/35/36/36.1/37.0).

Gradle 8.9 (wrapper), AGP 8.7.3, Kotlin 2.0.21, `compileSdk = 36`, `minSdk = 31`.
No Compose plugin — the UI is plain Views on purpose, so one fewer plugin version to fight.

**Shizuku API shape (verified by `javap` on the cached AARs):**
`rikka.shizuku.Shizuku.getBinder()` / `pingBinder()` / `getUid()` are in `dev.rikka.shizuku:api`.
The process API is `moe.shizuku.server.IShizukuService.Stub.asInterface(binder).newProcess(String[], String[], String)`
returning `moe.shizuku.server.IRemoteProcess` (`getInputStream`/`getErrorStream`/`getOutputStream` → `ParcelFileDescriptor`,
`waitFor()` → int, `destroy()`). There is **no** `rikka.shizuku.ShizukuProcess` or `ShizukuServiceInterface`.

## 6c. GUI + verified on-device behaviour (2026-11-23)

The main screen is `ui/MainScreen.kt` (plain Views, dark card theme, whole page in a
ScrollView). `MainActivity` hosts it and owns detection + run + grant flows.

**Verified live on a vivo V2408A (iQOO 13), Android 17, SPL 2026-10-01:**

```
kernel  6.6.127-android15-8-gd489d4ad2a70-abogki567717946-4k
kmi     android15-6.6          page 4096      selinux Enforcing
build   PD2408C_A_17.2.11.8.W10   fingerprint vivo/PD2408/PD2408:17/CP2A.260605.016_V0101L22/...
candidates → exactly one:  Shark  (kernel-release, CVE-2026-98260)  [best match]
transport  Shizuku (uid 2000)   manager  me.weishu.kernelsu
```

**Shizuku grant — the gotcha that cost time.** `Shizuku.checkSelfPermission()` returns
`PERMISSION_DENIED (0)` even when the service fully accepts this client. The reliable
test is the binder uid and a real `newProcess` probe:

```
pingBinder=true  getUid=2000  checkSelfPermission=0(DENIED)  newProcess=ok
```

So the app probes `newProcess` rather than trusting the flag, and the button reads
`SHIZUKU OK` when the binder is live. `GRANT SHIZUKU` / the proactive `ensureShizukuGrant()`
call `Shizuku.requestPermission(1001)` when the probe fails.

**Other fixes this round:**

- `resources.getIdentifier(...)` silently returns 0 for these resources → every view was
  transparent. All colours/drawables now go through `R.color.*` / `R.drawable.*`.
- `DeviceProbe` fell back to a local `uname -r` exec when the transport read came back
  empty (that was why the kernel line was blank at first).
- `kmiFor()` produced `android15-6.6.127`; upstream profiles use `android15-6.6`
  (`ksu_kmi = "android15-6.6"` for `6.6.118-android15-8-g…`). Truncates to major.minor.
- `candidates()` (loose, page-size-only hits scored 25) → `exactCandidates()`: exact
  kernel-release or exact model only. Non-matching loaders are no longer offered.

**Repository:** `https://github.com/AbShaheen184/awesome-temporary-root-app` (remote
`origin`, branch `master`, HEAD `e75eaa9`). Auth is the Windows git credential store —
no token in env, `git push` works directly.

## 7. Next steps (pick up here)

1. **Finish wiring the runner contracts.** `Runner.run()` currently has real
   implementations for `shark`, `timerroot`, `root-my-pixel`, `ghostlock-app`,
   `ghostkey`, `dirtyfrag`/`dirtyfalg-vr`/`dfroot`/`galaxycrash`; everything else
   falls through to `runGeneric()` and returns a clear "not wired yet" failure.
   Add one branch per loader, using the verified contracts in §4 and the
   `Target.runner` / `Target.extra` fields from the index.
2. **Bundle the payloads.** Copy the arm64 payloads the loaders need from
   `G:\Dev\AwesomRoot\<repo>` into `app/src/main/assets/` (or `jniLibs/` for
   `.so`) — see §4's table for which repo ships what. Keep the app under a sane
   size; the full set is ~2 GB, so prefer the per-loader subset or an on-demand
   download. `Runner.stageAsset()` already pipes an asset through the transport
   as base64.
3. **Add the run button + manager picker to the UI.** `MainActivity` currently
   detects and ranks; the tap-to-run path is a stub by design.
4. **Build the APK** with the invocation in §6b, then test on a real device.
5. Keep `HANDOFF.md` updated; it is the continuation point.

### Open issues (fix later, as agreed)

- Runner branches are stubs for ~95 of 109 loaders.
- `TransportSelector` has no Stellar implementation yet (Shizuku, su, local-ADB only).
- `Runner` still imports `java.io.OutputStream` unused after the `stageAsset` rewrite.
- UI has no run/manager controls and no Compose.
- No tests.
- `gradle.properties` points `sdk.dir` at an absolute Windows path — fine here,
  wrong anywhere else.

## 8. Commits

| hash | what |
|---|---|
| `bd8bd35` | initial model: index generator, app skeleton, detector UI |
| `f183d9e` | compile fixes + toolchain moved to `G:\Dev\Tools`; **BUILD SUCCESSFUL**, APK produced |

**APK**: `G:\Dev\AwesomRootApp\app\build\outputs\apk\debug\app-debug.apk`
5.8 MB, `app.awesom.root` v0.1, `minSdk 31 / targetSdk 36`, `assets/loaders.json` bundled.

## 9. Hard rules (from upstream, do not soften)

- Match the **exact** `uname -r` and page size. Never fall back to a near match.
- A loader needs its named manager installed; on Samsung, `diabl0w/KernelSU`.
- Never invoke a manager's install / flash / patch-boot-image action on a locked bootloader.
- Boot-chain / AVB / BROM tools are **out of scope** for the app UI and are flagged `high-risk`.
- Root is per-boot; a full reboot clears it. Do not promise persistence.
