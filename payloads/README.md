# Payloads

Executable payloads used by the loaders, kept out of the APK so the download is ~1 MB
instead of ~24 MB. The app fetches a file from this folder the first time a loader needs
it and caches it under `filesDir/payloads/`.

**44 files, 22.82 MiB.**

Raw base URL: `https://raw.githubusercontent.com/AbShaheen184/awesome-temporary-root-app/master/payloads`

`manifest.json` lists every file with its size and SHA-256.

## Shark runner (libk<model>.so)

12 files, 5.32 MiB

| file | KiB | sha256 (first 16) |
|---|---|---|
| `libk61223.so` | 439.9 | `da103ca84ae7a794` |
| `libk61230.so` | 446.0 | `4a626316fb676692` |
| `libk61238.so` | 446.0 | `4f6257516a842d1a` |
| `libk61258.so` | 487.1 | `2be48fc4988deb0d` |
| `libk61258x.so` | 448.3 | `a2073b4acaa23fc4` |
| `libk61269.so` | 446.9 | `6ce22d37264fd676` |
| `libk61821.so` | 435.3 | `4f11a985f44f7d61` |
| `libk61821p.so` | 484.4 | `5292811b7e257940` |
| `libk61821x.so` | 448.0 | `c15f63400dbe03d0` |
| `libk66118.so` | 446.9 | `36c94c7e7b338a89` |
| `libk66127.so` | 477.1 | `efa9a6cd1975e3e3` |
| `libk6689.so` | 446.1 | `59f2d0c2a4cb9686` |

## Shark universal ksud payload

1 files, 12.07 MiB

| file | KiB | sha256 (first 16) |
|---|---|---|
| `libksud_universal.so` | 12356.0 | `d14f50dfb36fd27c` |

## Root My Pixel helper

1 files, 0.02 MiB

| file | KiB | sha256 (first 16) |
|---|---|---|
| `libcve43499root.so` | 23.6 | `55347eaf529228e1` |

## Root My Pixel per-build exploit

29 files, 3.37 MiB

| file | KiB | sha256 (first 16) |
|---|---|---|
| `akita-CP2A.260805.005.so` | 90.8 | `7ede4853ee73f2f7` |
| `blazer-CP2A.260705.006.so` | 81.0 | `c659561ad500bbd7` |
| `bluejay-CP1A.260405.005.so` | 125.6 | `878bb27d5c6bff46` |
| `bluejay-CP2A.260705.006.so` | 125.6 | `81ff9e8c915044f0` |
| `caiman-CP2A.260705.006.so` | 125.8 | `5032b91dd1336ff0` |
| `cheetah-CP2A.260705.006.so` | 125.6 | `0ad04b8fdd88a451` |
| `comet-CP2A.260705.006.so` | 125.6 | `d42f00aff0d8f60f` |
| `cubs-CD1A.260618.001.C2.so` | 138.3 | `1ed0b0909c52bc45` |
| `felix-CP2A.260605.012.so` | 125.8 | `1540bda01249bd4f` |
| `frankel-CP2A.260705.006.so` | 115.8 | `9052e47b5b3bda2d` |
| `grizzly-CD1A.260618.001.C2.so` | 138.3 | `a8eb8ea380d1748f` |
| `husky-CP2A.260705.006.so` | 125.6 | `8ec2aa03ee1c10e4` |
| `kodiak-CD1A.260618.001.C2.so` | 138.3 | `8cf274f90f5275f9` |
| `komodo-CP2A.260705.006.so` | 125.8 | `87e958f435f9ed99` |
| `lynx-CP2A.260705.006.so` | 125.6 | `6d76d20c0235178d` |
| `mustang-CP2A.260705.006.so` | 81.6 | `fbdbed0a3e93f3e3` |
| `oriole-CP2A.260705.006.so` | 90.3 | `46ed1e49ccbbae25` |
| `panther-BP2A.250705.008.so` | 125.8 | `e3df38d2a9c84364` |
| `panther-CP2A.260705.006.so` | 125.6 | `b0d47e35e25846e6` |
| `rango-CP2A.260705.006.so` | 81.0 | `09c340d163afe1c7` |
| `raven-CP2A.260705.006.so` | 125.8 | `ab56cbb831cb2dc8` |
| `shiba-CP2A.260705.006.so` | 125.6 | `f7f19cad7ed301c2` |
| `stallion-CP2A.260805.005.so` | 125.8 | `85fe9739aaafbe3f` |
| `tangorpro-BP1A.250405.007.so` | 125.8 | `a9e974bb6b80ef47` |
| `tangorpro-CP2A.260705.006.so` | 125.8 | `49fedabf57a63d11` |
| `tegu-CP2A.260705.006.so` | 90.6 | `1bc5098965480254` |
| `tokay-AD1A.240905.004.so` | 125.8 | `c5272c5b5794781d` |
| `tokay-CP2A.260705.006.so` | 125.8 | `fccc392264439314` |
| `yogi-CD1A.260618.001.C3.so` | 138.3 | `bbe74a56989f4f30` |

## TimerRoot exploit binary

1 files, 2.04 MiB

| file | KiB | sha256 (first 16) |
|---|---|---|
| `timer` | 2089.5 | `0131fbd2e0c3d84f` |

## Notes

- The repo must be **public** for `raw.githubusercontent.com` downloads to work without a token.
- The small HOCON profiles (`timerroot/conf/*.conf`) and `loaders.json` stay in APK assets;
  they are a few hundred KiB in total and the app reads them directly.
- Nothing here is executed from the phone's app-private storage: apps targeting API 29+
  cannot `execve()` files in their home directory, so the app streams the bytes through
  Shizuku/su into `/data/local/tmp/awesomroot/` and runs them from there.
- The file names are the names the loaders expect (`libk<digits>.so`, `<codename>-<build>.so`, `libcve43499root.so`, `timer`). Do not rename them.
