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

## 6d. Button/run fixes + current state (2026-11-23, later)

**Why RUN BEST MATCH / TEST ALL were disabled.** `MainScreen.setCandidates()` updated the
list and the cards but never called `renderButtons()`. The button row was therefore built
once, in `init`, when `candidates` was still empty, and stayed `enabled=false` forever even
though a card was drawn. Fixed: `setCandidates()` now calls `renderButtons()`.
(logcat probe that found it: `renderButtons: candidates=0 busy=false canRun=false`.)
TEST ALL remains correctly disabled while only one candidate matches — there is nothing to
"test all"; it enables at ≥2.

**Why the run failed with `missing runner libCVE66127.so`.** Two bugs in `Runner.runShark()`:

1. It built the filename as `"lib" + target.runner + ".so"` → `libCVE66127.so`. The index
   stores the engine id (`CVE66127`); the shipped file is named by the **model digits**:
   `libk66127.so`. Mapping now mirrors `JailbreakCore.runnerLib()` in the Shark fork —
   digits of the CVE id for the plain runners, full name for the 6.18 plant-core variants
   (`libk61821p.so`).
2. The runners were not in the APK at all. They are now bundled:
   `app/src/main/jniLibs/arm64-v8a/libk*.so` + `libksud_universal.so` (18 MB,
   from `CYB3R0ID694/Shark-Moto/.../jniLibs/arm64-v8a/`). Verified present in the installed
   APK (`unzip -l` shows `lib/arm64-v8a/libk66127.so`, 488528 bytes).

The failure message now lists what *is* in the build, so a missing runner reads as an
actionable list instead of a bare name.

**Log box.** Was a hardcoded 220dp and clipped long output. Now wrapped in its own
ScrollView, grows to fit its content, caps at 45% of the viewport and scrolls internally,
auto-scrolls to the newest line, extra bottom padding. Verified on device: 24 lines fully
visible, no clipping, no wasted space.

### Not yet verified — the actual exploit run

After the rename fix the app resolves `libk66127.so` correctly, but **the run has not been
confirmed end-to-end**. The last attempt produced no `running Shark` line in the UI log,
and logcat showed a large GC pause plus repeated `Skipping invisible child` from
`AccessibilityNodeInfoDumper` — i.e. the screen was scrolled such that the log was off-screen
when the dump ran. Do not assume root happened or failed; re-test with a fresh launch and
read the log by scrolling, or grep `logcat` for the runner's own markers
(`LATE_LOAD_RC=`, `MODULE_STATE=`, `PLANT_CRED_OK`).

Known prerequisite before this device can actually root: `me.weishu.kernelsu` is installed
but its `nativeLibraryDir/libksud.so` must exist for the loader to stage `ksud`. Verify with:

```bash
adb shell pm path me.weishu.kernelsu
adb shell ls -l <that dir>/lib/arm64/libksud.so
```

Also note the Shark runner expects `PYB110_MANAGER` / `PYB110_KMI`; `runShark()` sets
`PYB110_MANAGER=kernelsu` unconditionally and should use the selected manager kind.

### Commits / repo

| hash | what |
|---|---|
| `bd8bd35` | initial model: index generator, app skeleton, detector UI |
| `f183d9e` | compile fixes, toolchain → `G:\Dev\Tools`, first working APK |
| `aa2111f` | doc cleanup |
| `4d612c2` | GUI main screen, Shizuku grant flow, exact-match candidates |
| `488ab60` | ignore `.kotlin` logs |
| `e75eaa9` | README, push to GitHub |
| `cf1b80a` | HANDOFF: GUI round + Shizuku finding |
| `8ba1c14` | log box fits its content |
| `53e5c67` | disabled run buttons + (incorrectly) runner filename |
| `373f5b9` | RESUME.md continuation point |
| _this round_ | real runner filename, `PYB110_KSUD`, manager kind, `kmi=null`, env parity, logcat mirror |

Remote `origin` = `https://github.com/AbShaheen184/awesome-temporary-root-app.git`,
branch `master`, HEAD `cf1b80a` (last pushed). **Unpushed local work exists** — the button
fix, the runner rename, the bundled jniLibs and this section are not on GitHub yet.

**APK**: `app/build/outputs/apk/debug/app-debug.apk` (~24 MB with the bundled runners).

## 6e. The exploit run — VERIFIED (2026-11-23, 15:38)

**Root happened on the vivo V2408A.** The Shark runner (CVE-2026-98260, kernel
`6.6.127-android15-8-gd489d4ad2a70-abogki567717946-4k`) completed plant → fake cred →
late-load on this boot. Ground truth, not UI text:

```
$ adb shell cat /proc/modules | grep kernelsu
kernelsu 180224 1 - Live 0x0000000000000000 (O)

$ adb shell ls -la /data/local/tmp/awesomroot/
-rwxr-xr-x 1 root root 330664 ko.bin          # extracted + bound as root
-rw------- 1 root root 330982 ko_bound.bin

$ adb shell ls -ld /data/adb
 drwx------ 3 root root  /data/adb                # created by ksud install
```

`ksud install` then opened the KernelSU manager (`me.weishu.kernelsu/.ui.MainActivity`
was the top resumed activity). The app's own device card re-probes and shows
**Root now: active** (verified against the fresh detection path). Runner log from that
run: `PLANT_CRED_OK child=…`, `[child] woke uid=0 euid=0`, `[auth] hook0/hook1/hook2`,
`[fake] child uid=0 euid=0`, `LOAD_EXTRACT=…`, `PLANT_OK: cred ready`.

**Still open after that run:** `su` is not on PATH yet (`/system/bin/sh: su:
 inaccessible or not found`) and `/data/adb/ksu/bin` is 0700 root — the manager's own
first-run setup (install su / reboot prompt) never completed because the app process was
force-stopped mid-run. Also the parked fake-cred child (`libk66127.so`, reparented to
init, holding the spray sockets) survives until reboot.

### Bugs found and fixed in this round

1. **The runner filename was still wrong.** `Runner` built `lib` + digits → `lib66127.so`.
   The shipped file is `libk66127.so`. On-device log: `FAILED: lib66127.so is not
   bundled in this APK. Available runners in this build: … libk66127.so …`. The §6d note
   claimed this was fixed; it was not. Now `sharkRunnerLib()` mirrors
   `JailbreakCore.runnerLib()`, including `libk61821p`/`libk61821x` for the 6.18.21
   plant-core releases and `libk61258`/`libk61258x`.
2. **`PYB110_KSUD` was never passed.** The runner reads it for the late-load and prints
   `LATE_LOAD_SKIP=env-missing ksud=(null) pkg=…` then **returns 0 without loading
   anything** — a silent false success. It is now the manager's
   `nativeLibraryDir/libksud.so`, and a manager without that file is refused up front
   with an actionable message instead of running.
3. **`PYB110_MANAGER` was hardcoded** to `kernelsu`. Now mapped from the selected
   package (`next` / `next_spoofed` / `bakasu` / `sukisu` / `finasu` / `kernelsu`), per
   `JailbreakCore.managerRuntime()`.
4. **`kmi` arrived as the literal string `null`.** `org.json`'s `optString()` returns
   `"null"` for a JSON `null`, so `ifBlank { null }` never fired. Symptom on device:
   `LOAD_EXTRACT=… debug extract-binary null_kernelsu.ko …` → `Error: asset not found:
   null_kernelsu.ko`. `IndexStore` now parses optional fields through `str()`, which
   treats blank and `"null"` as absent.
5. **The rest of the runner env now mirrors `JailbreakCore.buildEnv()`**: `PYB110_GATE`
   (CVE66127 and the 6.18.21/6.12.58 gate releases), `PYB110_CHASE` (vivo/iQOO),
   `PYB110_UNIVERSAL` + `PYB110_UNIVERSAL_KSUD` (the six universal engines),
   `PYB110_UNLOAD_GUARD`, `PYB110_DISABLE_ANTIROOT`, `PYB110_SAFE_MODE`,
   `PYB110_FILES_DIR`. Divergence: `FILES_DIR` points at `/data/local/tmp/awesomroot`,
   not the app's 0700 `filesDir`, because the runner executes as uid 2000 and cannot
   write there.
6. **Work dir is created through the transport.** The transport uses `dir` as the child's
   cwd; a missing `/data/local/tmp/awesomroot` made `newProcess` fail. It is now
   `mkdir -p`'d once via the transport (the app uid cannot write `/data/local/tmp`).
7. **Stale success markers.** `Runner.captured` was never cleared between runs, so run #2
   could report success from run #1's marker. Cleared per run; a skipped late-load is now
   called out in the failure reason, and `SKIP=`/`KSUD_MISSING`/`LATE_LOAD_RC=<non-zero>`
   are coloured as failures.
8. **The UI log is mirrored to logcat** (`I AwesomRoot:`) so a run can be verified with
   `adb logcat -d | grep -E "LATE_LOAD_RC=|MODULE_STATE=|PLANT_CRED_OK|KSUD_MISSING"`,
   which is what RESUME.md asks for. On-screen text was the only copy before.

### Not yet verified

- **A cold-boot run of this exact build.** The successful run above used the build with
  fixes 1–3 but *before* the `kmi=null` fix (4), which is why its gate worker still shows
  `null_kernelsu.ko` before the load completed. The final build has not been run from a
  clean boot; root is per-boot, so that needs a reboot first.
- **`su` / module manager wiring.** `/data/adb/ksu/bin` exists (0700 root) but no `su` in
  PATH; the manager's first-run setup must be finished before shell root is usable.
- TEST ALL (needs ≥2 candidates), the other ~95 loader branches, Stellar transport.

## 6f. Payload bundling (this round)

Four loaders had real `Runner` branches but no payloads in the APK, so they failed at
the "missing file" check. Investigated all four against the cloned sources; two were
portable, two are not.

### Done — bundled and re-wired

**Root My Pixel (29 target rows).** Its exploits are plain assets in
`alex193a/Root-My-Pixel/app/src/main/assets/exploits/`, so all 29 `.so` files are now
`app/src/main/assets/exploits/*.so` (3.5 MB). The run contract was wrong before: the
`.so` is *not* exec'd directly. Upstream hands its bytes to a loader binary
`libcve43499root.so` (in that app's `jniLibs`, now bundled too, 24 KB):

```
libcve43499root.so --run-payload <exploit.so> <helper> <log path>
  env RMG_CLIENT_UID=2000  RMG_APP_UID=<caller uid>
```

Success is its wire line `RMP_PAYLOAD_RESULT:v1 … success=1 reason=OK`, or the legacy
`RUN_END … done=1 root=1` (`PayloadResultParser` in the clone). `runRootMyPixel()` now
matches that.

**TimerRoot (31 of 58 rows).** The `timer` binary is *not* in the repo — it is a CI
build product published at
`https://github.com/MVN123123123123123/TimerRoot/releases/download/bingchiling/timer`
(2.1 MB, ELF64 aarch64, verified by header). It is now bundled as
`assets/timerroot/timer`, with 37 `conf/*.conf` profiles (all 31 rows that name one,
plus the rest of the repo's profiles). Real CLI from the binary's own usage string:

```
timer --conf <conf> [--protocol N] [--samsung] [ids|leak|shell|fake|permissive] …
  APP_PROTOCOL = 3   (p7a/timer_p7a.c)
```

`runTimerRoot()` stages the binary + profile and runs the generic `shell` stage.
Success is `ROOT_SHELL_OK` or a `uid=0(root) … context=…` line. The process **parks**
after success instead of exiting, so this branch waits for the marker
(`execUntilMarker`) and keeps the process referenced rather than waiting on an exit
code that never comes.

**Staging had to change.** `stageAsset()` used one `echo` of the whole base64 blob.
`MAX_ARG_STRLEN` is 128 KiB per argv entry, so the 2.1 MB `timer` and even a
128 KB exploit `.so` could never stage. It now appends the base64 in 60 KB chunks and
decodes once, and creates the destination's parent directory.

### Not portable from the clones (verified, not guessed)

- **GhostLock App (83 rows).** `libghostlock.so` does not exist in the repo: it is
  compiled from `src/` (8,164 lines of C/C++, static libc++) by the project's own
  Makefile and copied in by the Gradle task `prepareGhostlockJniLibs`. The profile is
  also not the `.conf` file — the app encodes it to a GLK1 blob and pipes it on
  **stdin** (`--ghostlock-app-call`), which needs an `OutputStream` on `PrivProcess`
  and the profile serializer. Bundling `conf/` alone would not work.
- **DirtyFrag (8 rows).** The `.ko` files in `app/src/main/jni/ko/` are prebuilt, but
  `libdfroot.so` / `libbootstrap.so` are CMake products of `app/src/main/jni/*.c`, and
  the run needs a Java IpSec harness (`ExploitRunner` builds an `IpSecTransform`, UDP
  encap socket, SPI and random AES key, and passes `--encap-port/--sender-port/--spi/
  --aes-key`). That is a port of a whole class, not an asset copy.

### Index gaps found while counting

- `shark`: 16 of 38 rows have `runner: null` → refused with "no runner in index".
- `timerroot`: 27 of 58 rows have `runner: null` → same.
- 96 of 109 loaders still have **zero** target rows, so they can never be offered.

### Verified / not verified

- **Verified:** `:app:assembleDebug` BUILD SUCCESSFUL; APK 21,369,263 bytes contains
  `assets/exploits/` 29 files, `assets/timerroot/conf/` 37 files, `assets/timerroot/timer`,
  `lib/arm64-v8a/libcve43499root.so`.
- **Not verified:** neither new branch has been *run* on hardware — the test device
  (vivo PD2408C, 6.6.127) matches neither a Root My Pixel (Pixel) nor a TimerRoot row,
  so it cannot exercise them. Their markers come from the upstream sources, not from a
  device run.
- **Not wired:** Root My Pixel's second phase (using the exploit's own `su` path to
  `ksud late-load`) and TimerRoot's KernelSU endgame. Both branches currently stop at
  "temporary root reached"; KernelSU loading is still only implemented for Shark.

## 6g. Payloads moved out of the APK (2026-11-23, later)

The APK carried 23 MB of exploit payloads that only a handful of devices can use.
All 44 executables now live in `payloads/` at the repo root, and the app downloads
the one it needs the first time a loader runs.

### What moved (44 files, 22.82 MiB)

| was | now | count | size |
|---|---|---|---|
| `app/src/main/jniLibs/arm64-v8a/libk*.so` | `payloads/libk*.so` | 12 | 5.32 MiB |
| `app/src/main/jniLibs/arm64-v8a/libksud_universal.so` | `payloads/libksud_universal.so` | 1 | 12.06 MiB |
| `app/src/main/jniLibs/arm64-v8a/libcve43499root.so` | `payloads/libcve43499root.so` | 1 | 23 KiB |
| `app/src/main/assets/exploits/*.so` | `payloads/<codename>-<build>.so` | 29 | 3.47 MiB |
| `app/src/main/assets/timerroot/timer` | `payloads/timer` | 1 | 2.04 MiB |

`payloads/manifest.json` lists every file with its size and SHA-256 and
`payloads/README.md` is the hosting index. The 37 TimerRoot `.conf` profiles and
`loaders.json` stay in `assets/` — ~380 KiB, and the app reads them directly.
The old `app/src/main/jniLibs/` tree no longer exists.

### How the app gets them (`PayloadStore.kt`)

- Base URL is one constant:
  `https://raw.githubusercontent.com/AbShaheen184/awesome-temporary-root-app/master/payloads`.
  **The repo has to be public** — a private repo answers 404 to the raw host.
- Lookup order: `filesDir/payloads/<name>` cache → bundled asset → HTTP GET → write
  `<name>.part` then rename. The byte count is checked against `Content-Length`; a
  mismatch is reported and nothing is cached.
- Every failure is a log line in the run pane (`HTTP 404`, timeout, truncated).

### Why nothing is exec'd straight from the cache

`targetSdk = 36`, and since API 29 an app cannot `execve()` a file inside its own
home directory (W^X / `execute_no_trans`). So `Runner.stageAsset()` streams the
payload through the transport into `/data/local/tmp/awesomroot/` (0700,
shell-writable) and every loader is executed from there. That is also why the Shark
runner and the Root My Pixel helper are staged now instead of read out of
`nativeLibraryDir`.

`stageAsset()` base64-encodes 45,000-byte slices (a multiple of 3 → exactly 60,000
chars, under `MAX_ARG_STRLEN`), so even the 12 MiB ksud payload never needs its
whole base64 string in memory.

### Verified / not verified

- **Verified:** `:app:assembleDebug` BUILD SUCCESSFUL; the APK is now
  **6,346,476 bytes** (was 21,369,263) and `unzip -l` shows **no `.so` and no
  `lib/` entry** — only `assets/loaders.json` plus the 37 confs.
- **Not verified:** no device run since the split. The download → stage → exec path
  has not run on hardware, and the raw URLs only answer once `payloads/` is pushed
  and the repo is public.

## 7. Next steps (pick up here)

1. **Reboot, then run this exact build once** and confirm `LATE_LOAD_RC=0` via logcat:

   ```bash
   adb logcat -d | grep -E "LATE_LOAD_RC=|MODULE_STATE=|PLANT_CRED_OK|KSUD_MISSING|LATE_LOAD_SKIP"
   ```

   A reboot also clears the parked fake-cred child and the loaded module (root is per-boot).
2. **Finish the manager setup** so `su` works, then have the app verify root by `su -c id`
   rather than only `/proc/modules`.
3. **Finish wiring the runner contracts.** `Runner.run()` currently has real
   implementations for `shark`, `timerroot`, `root-my-pixel`, `ghostlock-app`,
   `ghostkey`, `dirtyfrag`/`dirtyfalg-vr`/`dfroot`/`galaxycrash`; everything else
   falls through to `runGeneric()` and returns a clear "not wired yet" failure.
   Add one branch per loader, using the verified contracts in §4 and the
   `Target.runner` / `Target.extra` fields from the index.
2. **Bundle the remaining payloads.** Done for Root My Pixel (29 `.so`) and
   TimerRoot (`timer` + 37 confs) — see §6f. Still needed: GhostLock (`libghostlock.so`
   must be compiled from `src/`, plus the GLK1 profile serializer) and DirtyFrag
   (`libdfroot.so`/`libbootstrap.so` + the IpSec harness in `ExploitRunner`).
   `Runner.stageAsset()` chunks base64 in 45,000-byte slices and payloads are
   downloaded from `payloads/` on demand instead of shipped in the APK (§6g).
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
