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

## 7. Next steps (pick up here)

1. **Resolve §4 ABI question.** Compare a real upstream release APK's `lib/arm64-v8a/*.so`
   against the cloned file. This gates everything else.
2. **Scaffold the Gradle project** in `G:\Dev\AwesomRootApp\app` (AGP 9.x + Kotlin 2.x,
   `compileSdk 37`, `minSdk 33`, JDK 17+; the reference apps use `namespace`/`applicationId`
   of their own, use e.g. `app.awesom.root`). `gradlew` from any cloned repo
   (`CYB3R0ID694/Shark-Moto/app/gradlew.bat`) can be reused.
3. **Ports to write** (Kotlin, in `app/src/main/java/app/awesom/root/`):
   - `DeviceProbe` — `ro.product.*`, `ro.build.*`, `ro.build.fingerprint`, `uname -r`,
     `getconf PAGESIZE`, `getenforce`, ABI. Privileged reads via the transport.
   - `IndexStore` — loads `assets/loaders.json`, matches on the keys above, returns
     ranked candidates (exact `kernel-release` > `model+kernel` > `kmi`).
   - `Transport` — Shizuku first, then Stellar, then local-ADB, then plain `su`.
     Reference impls: `kpgc10kai/Shark/.../Privileged.kt`,
     `YuKongA/ghostlock-app/.../ShizukuExploitRunner.kt`,
     `MVN123123123123123/TimerRoot/.../UserService.java`.
   - `Runner` — per-loader exec wrapper: stage binaries from assets to `/data/local/tmp`,
     set the loader's env, stream stdout, map exit code + success markers
     (`LATE_LOAD_RC=0`, `/proc/modules` has `kernelsu ... Live`, `su -c id` → `u:r:ksu:s0`).
   - UI: device card, candidate list, run log, manager picker.
4. **Build**: `JAVA_HOME=<studio jbr> ./gradlew :app:assembleDebug` with
   `sdk.dir` in `local.properties` pointing at the SDK. Then install and test on a device.
5. Keep `HANDOFF.md` updated; it is the continuation point.

## 8. Hard rules (from upstream, do not soften)

- Match the **exact** `uname -r` and page size. Never fall back to a near match.
- A loader needs its named manager installed; on Samsung, `diabl0w/KernelSU`.
- Never invoke a manager's install / flash / patch-boot-image action on a locked bootloader.
- Boot-chain / AVB / BROM tools are **out of scope** for the app UI and are flagged `high-risk`.
- Root is per-boot; a full reboot clears it. Do not promise persistence.
