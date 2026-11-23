#!/usr/bin/env python3
"""
Build the canonical loader index for AwesomRootApp.

Reads the device/kernel tables that each cloned upstream project actually ships
(payload feeds, profile indexes, README tables) and emits one JSON document that
the Android app reads at runtime.  Nothing is hand-typed that a repo can give
us; every entry carries the repo it came from so the app can show provenance.

Output:  G:/Dev/AwesomRootApp/app/src/main/assets/loaders.json
         G:/Dev/AwesomRootApp/tools/loaders.json  (identical copy, for tooling)
"""
import json, os, re, hashlib, sys

ROOT = r"G:\Dev\AwesomRoot"
APP = r"G:\Dev\AwesomRootApp"
OUT_ASSETS = os.path.join(APP, "app", "src", "main", "assets", "loaders.json")
OUT_TOOLS = os.path.join(APP, "tools", "loaders.json")

def read(p, limit=None):
    try:
        t = open(p, encoding="utf-8", errors="replace").read()
        return t[:limit] if limit else t
    except OSError:
        return None

def sha256(p):
    h = hashlib.sha256()
    with open(p, "rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()

# ---------------------------------------------------------------- loaders
# id, cve, class (kernel-lpe / limited), transport (adb/shizuku/on-device),
# source repo, what it needs from the user.
LOADERS = [
    # --- CVE-2026-98260 (POSIX CPU timer UAF) ---
    dict(id="shark", name="Shark", cve="CVE-2026-98260", klass="kernel-lpe",
         repo="CYB3R0ID694/Shark-Moto", upstream="https://github.com/kpgc10kai/Shark",
         transport="shizuku|stellar|wireless-adb", runner="bundled .so per kernel",
         kmi_from="release", status="active"),
    dict(id="shark-upstream", name="Shark (upstream)", cve="CVE-2026-98260", klass="kernel-lpe",
         repo="Yunnijian/Shark-Releases", upstream="https://github.com/Yunnijian/Shark-Releases",
         transport="shizuku", runner="apk-only", kmi_from="release", status="releases-only"),
    dict(id="timerroot", name="TimerRoot", cve="CVE-2026-98260", klass="kernel-lpe",
         repo="MVN123123123123123/TimerRoot", upstream="https://github.com/MVN123123123123123/TimerRoot",
         transport="shizuku", runner="downloaded timer binary + conf/", kmi_from="release",
         status="active"),
    dict(id="samtimer", name="SamTimer", cve="CVE-2026-98260", klass="kernel-lpe",
         repo="Leo141212/SamTimer", upstream="https://github.com/Leo141212/SamTimer",
         transport="shizuku", runner="bundled", kmi_from="release", status="active"),
    dict(id="ghostkey", name="GhostKey", cve="CVE-2026-98260", klass="kernel-lpe",
         repo="Feiman55/CVE-2026-98260-GhostKey", upstream="https://github.com/Feiman55/CVE-2026-98260-GhostKey",
         transport="shizuku", runner="bundled", kmi_from="release", status="active"),
    dict(id="rootmys24", name="RootMyS24", cve="CVE-2026-43499", klass="kernel-lpe",
         repo="NanoTurtle1145/root-my-s24", upstream="https://github.com/NanoTurtle1145/root-my-s24",
         transport="shizuku|adb", runner="bundled", kmi_from="release", status="active"),
    dict(id="cve-2026-98260-asteroids", name="CVE-2026-98260-Asteroids", cve="CVE-2026-98260", klass="kernel-lpe",
         repo="arkylin/CVE-2026-98260-Asteroids", upstream="https://github.com/arkylin/CVE-2026-98260-Asteroids",
         transport="shizuku", runner="bundled", kmi_from="release", status="active"),
    dict(id="cve-2026-64560-rmx6699", name="cve-2026-64560-rmx6699", cve="CVE-2026-64560", klass="kernel-lpe",
         repo="totsiaw/cve-2026-64560-rmx6699", upstream="https://github.com/totsiaw/cve-2026-64560-rmx6699",
         transport="adb", runner="bundled", kmi_from="release", status="active"),
    dict(id="time-my-galaxy", name="Time-My-Galaxy", cve="CVE-2026-98260", klass="kernel-lpe",
         repo="MocLG/Time-My-Galaxy", upstream="https://github.com/MocLG/Time-My-Galaxy",
         transport="shizuku", runner="per-build signed bundle", kmi_from="incremental", status="active"),
    dict(id="ksulate", name="KsuLateLoad", cve="CVE-2026-98260", klass="kernel-lpe",
         repo="NanoTurtle1145/KsuLateLoad", upstream="https://github.com/NanoTurtle1145/KsuLateLoad",
         transport="shizuku", runner="helper", kmi_from="release", status="active"),
    dict(id="infinitylock", name="InfinityLock", cve="CVE-2026-98260", klass="kernel-lpe",
         repo="kailashrs/infinitylock-app", upstream="https://github.com/kailashrs/infinitylock-app",
         transport="shizuku", runner="bundled", kmi_from="firmware+kernel", status="active"),
    dict(id="cve-2026-98260-xiaomi17pm", name="CVE-2026-98260-Xiaomi-17-Pro-Max", cve="CVE-2026-98260", klass="kernel-lpe",
         repo="starriv/CVE-2026-98260-Xiaomi-17-Pro-Max", upstream="https://github.com/starriv/CVE-2026-98260-Xiaomi-17-Pro-Max",
         transport="adb", runner="bundled", kmi_from="release", status="active"),
    dict(id="xring-98260", name="xring-CVE-2026-98260-OS4.0", cve="CVE-2026-98260", klass="kernel-lpe",
         repo="ChaYeWuu/xring-CVE-2026-98260-OS4.0", upstream="https://github.com/ChaYeWuu/xring-CVE-2026-98260-OS4.0",
         transport="shizuku", runner="bundled", kmi_from="release", status="active"),
    dict(id="oneplus16-pyb110", name="OnePlus 16 PYB110 kit", cve="CVE-2026-98260", klass="kernel-lpe",
         repo="AzirX1337/CVE-2026-98260-OnePlus16-PYB110-Jailbreak", upstream="https://github.com/AzirX1337/CVE-2026-98260-OnePlus16-PYB110-Jailbreak",
         transport="adb", runner="bundled", kmi_from="release", status="active"),
    dict(id="honor-win2", name="CVE-2026-98260-BRM-AN00", cve="CVE-2026-98260", klass="kernel-lpe",
         repo="sganggs/CVE-2026-98260-BRM-AN00", upstream="https://github.com/sganggs/CVE-2026-98260-BRM-AN00",
         transport="adb", runner="bundled", kmi_from="release", status="active"),
    dict(id="lenovo-tb710fu", name="cve-2026-98260-tb710fu", cve="CVE-2026-98260", klass="kernel-lpe",
         repo="aoxzh/cve-2026-98260-tb710fu", upstream="https://github.com/aoxzh/cve-2026-98260-tb710fu",
         transport="adb", runner="bundled", kmi_from="release", status="active"),
    dict(id="lenovo-tb320fc", name="tb320fc-temp-root", cve="CVE-2026-98260", klass="kernel-lpe",
         repo="armyja/tb320fc-temp-root", upstream="https://github.com/armyja/tb320fc-temp-root",
         transport="adb", runner="bundled", kmi_from="release", status="active"),
    dict(id="vivo-gki-98260", name="vivo/iQOO 6.6.127 notes", cve="CVE-2026-98260", klass="research",
         repo="janewas/cve-2026-98260-vivo-gki-research", upstream="https://github.com/janewas/cve-2026-98260-vivo-gki-research",
         transport="n/a", runner="notes-only", kmi_from="release", status="notes-only"),
    dict(id="nezha-98260", name="Xiaomi nezha 98260 port", cve="CVE-2026-98260", klass="research",
         repo="quyicheng03-boop/CVE-2026-98260-nezha-android", upstream="https://github.com/quyicheng03-boop/CVE-2026-98260-nezha-android",
         transport="n/a", runner="notes-only", kmi_from="release", status="notes-only"),
    dict(id="v4bel-infinitycore", name="V4bel InfinityCore", cve="CVE-2026-98260", klass="research",
         repo="V4bel/InfinityCore", upstream="https://github.com/V4bel/InfinityCore",
         transport="n/a", runner="disclosure", kmi_from="release", status="notes-only"),

    # --- CVE-2026-43499 (GhostLock futex PI UAF) ---
    dict(id="ghostlock-app", name="GhostLock App", cve="CVE-2026-43499", klass="kernel-lpe",
         repo="YuKongA/ghostlock-app", upstream="https://github.com/YuKongA/ghostlock-app",
         transport="shizuku", runner="bundled libghostlock.so + conf profile", kmi_from="release",
         status="active"),
    dict(id="ghostlock-oneplus", name="ghostlock-oneplus", cve="CVE-2026-43499", klass="kernel-lpe",
         repo="JoinChang/ghostlock-oneplus", upstream="https://github.com/JoinChang/ghostlock-oneplus",
         transport="adb", runner="adb binary", kmi_from="release", status="active"),
    dict(id="ghostlock-for-oneplus", name="GhostLock-for-OnePlus", cve="CVE-2026-43499", klass="kernel-lpe",
         repo="p2p3p/GhostLock-for-OnePlus", upstream="https://github.com/p2p3p/GhostLock-for-OnePlus",
         transport="adb", runner="bundled", kmi_from="release", status="active"),
    dict(id="ghostlock-a17", name="ghostlock-a17", cve="CVE-2026-43499", klass="kernel-lpe",
         repo="mobilehackinglab/ghostlock-a17", upstream="https://github.com/mobilehackinglab/ghostlock-a17",
         transport="adb", runner="bundled", kmi_from="release", status="active"),
    dict(id="ghostlock-galaxy", name="GhostLock-Galaxy", cve="CVE-2026-43499", klass="kernel-lpe",
         repo="wxxsfxyzm/GhostLock-Galaxy", upstream="https://github.com/wxxsfxyzm/GhostLock-Galaxy",
         transport="adb|shizuku", runner="bundled", kmi_from="release", status="active"),
    dict(id="ghostsam", name="GhostSam", cve="CVE-2026-43499", klass="kernel-lpe",
         repo="snothin/GhostSam", upstream="https://github.com/snothin/GhostSam",
         transport="adb", runner="bundled preload.so", kmi_from="release", status="active"),
    dict(id="ghostsam-s26", name="ghostlock-s26", cve="CVE-2026-43499", klass="kernel-lpe",
         repo="snothin/ghostlock-s26", upstream="https://github.com/snothin/ghostlock-s26",
         transport="adb", runner="bundled", kmi_from="release", status="merged-into-ghostsam"),
    dict(id="root-my-galaxy", name="Root My Galaxy", cve="CVE-2026-43499", klass="kernel-lpe",
         repo="BuSung-dev/Root-My-Galaxy", upstream="https://github.com/BuSung-dev/Root-My-Galaxy",
         transport="shizuku|on-device", runner="payload feed", kmi_from="model+kernel", status="active"),
    dict(id="root-my-galaxy-next", name="Root My Galaxy Next", cve="CVE-2026-43499", klass="kernel-lpe",
         repo="rushiranpise/Root-My-Galaxy-Next", upstream="https://github.com/rushiranpise/Root-My-Galaxy-Next",
         transport="shizuku|wireless-adb", runner="payload feed", kmi_from="model+kernel", status="active"),
    dict(id="root-my-galaxy-ultra", name="Root My Galaxy Ultra", cve="CVE-2026-43499", klass="kernel-lpe",
         repo="xorpix/Root-My-Galaxy-Ultra", upstream="https://github.com/xorpix/Root-My-Galaxy-Ultra",
         transport="shizuku", runner="bundled m3q payload", kmi_from="release", status="active"),
    dict(id="root-my-galaxy-extended", name="Root My Galaxy Extended", cve="CVE-2026-43499", klass="kernel-lpe",
         repo="igorcv88/Root-My-Galaxy-Extended", upstream="https://github.com/igorcv88/Root-My-Galaxy-Extended",
         transport="shizuku", runner="payload feed", kmi_from="model+kernel", status="active"),
    dict(id="root-my-pixel", name="Root My Pixel", cve="CVE-2026-43499", klass="kernel-lpe",
         repo="alex193a/Root-My-Pixel", upstream="https://github.com/alex193a/Root-My-Pixel",
         transport="shizuku", runner="bundled per-build .so", kmi_from="release+build", status="active"),
    dict(id="rootmyvivo", name="RootMyVivo", cve="CVE-2026-43499", klass="kernel-lpe",
         repo="zenyxx-xd/RootMyVivo", upstream="https://github.com/zenyxx-xd/RootMyVivo",
         transport="shizuku|adb", runner="payload catalog", kmi_from="release", status="active"),
    dict(id="root-my-device-witaqua", name="Root My Device (Witaqua)", cve="CVE-2026-43499", klass="kernel-lpe",
         repo="WitAqua-tools/Root-My-Device", upstream="https://github.com/WitAqua-tools/Root-My-Device",
         transport="on-device", runner="targets-v2 feed", kmi_from="release+fingerprint", status="active"),
    dict(id="root-my-device-tqmane", name="Root My Device (tqmane)", cve="CVE-2026-43499", klass="kernel-lpe",
         repo="tqmane/Root-My-Device", upstream="https://github.com/tqmane/Root-My-Device",
         transport="shizuku", runner="bundled per-device .so", kmi_from="release", status="active"),
    dict(id="uniiroot", name="UniRoot", cve="CVE-2026-43499", klass="kernel-lpe",
         repo="kuuky29/UniRoot", upstream="https://github.com/kuuky29/UniRoot",
         transport="on-device", runner="bundled per-profile .so", kmi_from="release", status="active"),
    dict(id="r0rt1z2-ghostlock", name="R0rt1z2/GhostLock", cve="CVE-2026-43499", klass="kernel-lpe",
         repo="R0rt1z2/GhostLock", upstream="https://github.com/R0rt1z2/GhostLock",
         transport="adb", runner="multi-branch binary", kmi_from="branch", status="active"),
    dict(id="ghostlock-nvidia", name="GhostLock NVIDIA Shield", cve="CVE-2026-43499", klass="kernel-lpe",
         repo="cyberbalsa/GhostLock-NVIDIA-Shield-9.2.4", upstream="https://github.com/cyberbalsa/GhostLock-NVIDIA-Shield-9.2.4",
         transport="adb", runner="bundled", kmi_from="release", status="active"),
    dict(id="ghostlock-x200", name="GhostLock-X200", cve="CVE-2026-43499", klass="kernel-lpe",
         repo="xiaohj233/ghostlock-x200-root", upstream="https://github.com/xiaohj233/ghostlock-x200-root",
         transport="pc|app", runner="bundled", kmi_from="release", status="active"),
    dict(id="ghostlock-emerald", name="ghostlock-emerald", cve="CVE-2026-43499", klass="kernel-lpe",
         repo="datfooldive/ghostlock-emerald", upstream="https://github.com/datfooldive/ghostlock-emerald",
         transport="adb", runner="bundled", kmi_from="release", status="active"),
    dict(id="ghostlock-xringo1", name="ghostlock-xringo1", cve="CVE-2026-43499", klass="kernel-lpe",
         repo="ayyy7128/ghostlock-xringo1", upstream="https://github.com/ayyy7128/ghostlock-xringo1",
         transport="shizuku", runner="bundled", kmi_from="release", status="active"),
    dict(id="ghostlock-honor90", name="GhostLock-Honor90", cve="CVE-2026-43499", klass="kernel-lpe",
         repo="anVzdGFtb25/GhostLock-Honor90-REA-AN00", upstream="https://github.com/anVzdGFtb25/GhostLock-Honor90-REA-AN00",
         transport="adb", runner="bundled", kmi_from="release", status="active"),
    dict(id="honor90-ghostlock", name="honor90-ghostlock", cve="CVE-2026-43499", klass="kernel-lpe",
         repo="cyrrp/honor90-ghostlock", upstream="https://github.com/cyrrp/honor90-ghostlock",
         transport="adb", runner="bundled", kmi_from="release", status="active"),
    dict(id="ghostlock-honorfne", name="GhostLock-HonorFne", cve="CVE-2026-43499", klass="kernel-lpe",
         repo="MSEDGE-CORE/GhostLock-HonorFne", upstream="https://github.com/MSEDGE-CORE/GhostLock-HonorFne",
         transport="adb", runner="bundled", kmi_from="release", status="active"),
    dict(id="ghostlock-honor-ldy", name="honor-ldy-an00-ghostlock", cve="CVE-2026-43499", klass="kernel-lpe",
         repo="iodn/honor-ldy-an00-ghostlock", upstream="https://github.com/iodn/honor-ldy-an00-ghostlock",
         transport="adb", runner="bundled", kmi_from="release", status="active"),
    dict(id="magic5-oneclick", name="magic5-one-click-root", cve="CVE-2026-43499", klass="kernel-lpe",
         repo="Sakuramoe233/magic5-one-click-root", upstream="https://github.com/Sakuramoe233/magic5-one-click-root",
         transport="adb", runner="bundled", kmi_from="release", status="active"),
    dict(id="honor-pad9", name="honor-pad9-ghostlock", cve="CVE-2026-43499", klass="kernel-lpe",
         repo="vexpaer/honor-pad9-ghostlock", upstream="https://github.com/vexpaer/honor-pad9-ghostlock",
         transport="adb", runner="bundled", kmi_from="release", status="active"),
    dict(id="hclick", name="HOneClick", cve="CVE-2026-43499", klass="kernel-lpe",
         repo="superboyyy/HOneClick", upstream="https://github.com/superboyyy/HOneClick",
         transport="adb", runner="bundled", kmi_from="release", status="active"),
    dict(id="honor-p50pro", name="GhostLock-P50Pro-JAD-AL00", cve="CVE-2026-43499", klass="kernel-lpe",
         repo="anVzdGFtb25/GhostLock-P50Pro-JAD-AL00", upstream="https://github.com/anVzdGFtb25/GhostLock-P50Pro-JAD-AL00",
         transport="adb", runner="bundled", kmi_from="release", status="active"),
    dict(id="ghostlock-matepad11", name="GhostLock-MatePad11", cve="CVE-2026-43499", klass="kernel-lpe",
         repo="natsuu233/GhostLock-MatePad11", upstream="https://github.com/natsuu233/GhostLock-MatePad11",
         transport="adb", runner="bundled", kmi_from="release", status="active"),
    dict(id="zenfone9-ghostlock", name="zenfone9-ghostlock", cve="CVE-2026-43499", klass="kernel-lpe",
         repo="CKwasd/zenfone9-ghostlock", upstream="https://github.com/CKwasd/zenfone9-ghostlock",
         transport="adb", runner="bundled", kmi_from="release", status="active"),
    dict(id="ghostlock-rmx5085", name="ghostlock-rmx5085", cve="CVE-2026-43499", klass="kernel-lpe",
         repo="Nixbones/ghostlock-rmx5085", upstream="https://github.com/Nixbones/ghostlock-rmx5085",
         transport="adb", runner="bundled", kmi_from="release", status="active"),
    dict(id="ghostlock-firetv-tank", name="ghostlock-firetv-tank", cve="CVE-2026-43499", klass="kernel-lpe",
         repo="Anezium/ghostlock-firetv-tank", upstream="https://github.com/Anezium/ghostlock-firetv-tank",
         transport="adb", runner="bundled", kmi_from="release", status="active"),
    dict(id="mt6989-ghostlock", name="MT6989-ghostlock", cve="CVE-2026-43499", klass="kernel-lpe",
         repo="MaheshSharan/MT6989-ghostlock", upstream="https://github.com/MaheshSharan/MT6989-ghostlock",
         transport="adb", runner="bundled", kmi_from="release", status="active"),
    dict(id="iqooz9-t3-port", name="iQOO Z9 / vivo T3 port", cve="CVE-2026-43499", klass="kernel-lpe",
         repo="ankitrawatgit/iQOO-Z9_5G-vivo-T3_5G-Root-GhostLock", upstream="https://github.com/ankitrawatgit/iQOO-Z9_5G-vivo-T3_5G-Root-GhostLock",
         transport="adb", runner="bundled", kmi_from="release", status="active"),
    dict(id="meizu21-ghostlock", name="Meizu 21 GhostLock", cve="CVE-2026-43499", klass="kernel-lpe",
         repo="ymh001/meizu21-ghostlock-root", upstream="https://github.com/ymh001/meizu21-ghostlock-root",
         transport="adb", runner="bundled", kmi_from="release", status="active"),
    dict(id="meizu20pro", name="meizu20pro-temp-root", cve="CVE-2026-43499", klass="kernel-lpe",
         repo="h5hg6vy8nt-hue/meizu20pro-temp-root", upstream="https://github.com/h5hg6vy8nt-hue/meizu20pro-temp-root",
         transport="adb", runner="bundled", kmi_from="release", status="active"),
    dict(id="xperia1vi", name="xperia1vi-root", cve="CVE-2026-43499", klass="kernel-lpe",
         repo="RetributionByRevenue/xperia1vi-root", upstream="https://github.com/RetributionByRevenue/xperia1vi-root",
         transport="adb", runner="bundled", kmi_from="release", status="active"),
    dict(id="humane-aipin", name="humane-aipin-ghostlock", cve="CVE-2026-43499", klass="kernel-lpe",
         repo="TheAndersMadsen/humane-aipin-ghostlock", upstream="https://github.com/TheAndersMadsen/humane-aipin-ghostlock",
         transport="adb", runner="bundled", kmi_from="release", status="active"),
    dict(id="make-it-root", name="make-it-root", cve="CVE-2026-43499", klass="kernel-lpe",
         repo="DarkDevil-937/make-it-root", upstream="https://github.com/DarkDevil-937/make-it-root",
         transport="adb", runner="bundled", kmi_from="release", status="active"),
    dict(id="oppo-jailbreak", name="oppo-jailbreak", cve="CVE-2026-43499", klass="kernel-lpe",
         repo="tw0m8/oppo-jailbreak", upstream="https://github.com/tw0m8/oppo-jailbreak",
         transport="adb", runner="bundled", kmi_from="release", status="active"),
    dict(id="findn2-ksu-chain", name="findn2-ksu-chain", cve="CVE-2026-43499", klass="kernel-lpe",
         repo="pubglite55/findn2-ksu-chain", upstream="https://github.com/pubglite55/findn2-ksu-chain",
         transport="adb", runner="bundled", kmi_from="release", status="active"),
    dict(id="cve-2026-43499-popsicle", name="CVE-2026-43499-popsicle", cve="CVE-2026-43499", klass="kernel-lpe",
         repo="x-spy/CVE-2026-43499-popsicle", upstream="https://github.com/x-spy/CVE-2026-43499-popsicle",
         transport="adb", runner="bundled", kmi_from="release", status="active"),
    dict(id="cve-2025-21479-iqooneo8", name="cve-2025-21479_iqooneo8", cve="CVE-2025-21479", klass="kernel-lpe",
         repo="Qingizi7/cve-2025-21479_iqooneo8", upstream="https://github.com/Qingizi7/cve-2025-21479_iqooneo8",
         transport="adb", runner="bundled", kmi_from="release", status="active"),
    dict(id="ionstackquest3", name="IonStackQuest3", cve="CVE-2026-43499", klass="kernel-lpe",
         repo="F-19-F/IonStackQuest3", upstream="https://github.com/F-19-F/IonStackQuest3",
         transport="adb", runner="bundled", kmi_from="release", status="active"),
    dict(id="redmik70pro", name="Redmi K70 Pro temp root", cve="MIUI-service", klass="kernel-lpe",
         repo="Berge520/redmik70pro", upstream="https://github.com/Berge520/redmik70pro",
         transport="pc+adb+fastboot", runner="bundled", kmi_from="release", status="active"),
    dict(id="root-my-s23", name="root-my-s23", cve="CVE-2026-43499", klass="kernel-lpe",
         repo="zxsos/root-my-s23", upstream="https://github.com/zxsos/root-my-s23",
         transport="adb", runner="bundled", kmi_from="release", status="active"),
    dict(id="sm-a166w", name="SM-A166W port", cve="CVE-2026-43499", klass="kernel-lpe",
         repo="jojobear691/SM-A166W-DZG1-Temporary-Root", upstream="https://github.com/jojobear691/SM-A166W-DZG1-Temporary-Root",
         transport="shizuku", runner="bundled", kmi_from="release", status="active"),
    dict(id="yuehong", name="yuehong-privilege-assistant", cve="multi", klass="multi-method",
         repo="qq2070006042-create/yuehong-privilege-assistant", upstream="https://github.com/qq2070006042-create/yuehong-privilege-assistant",
         transport="stellar|otg|adb", runner="bundled (multi-engine)", kmi_from="release", status="active"),
    dict(id="honor-anb", name="ANB Root", cve="CVE-2026-43499", klass="kernel-lpe",
         repo="Chuniex-0631/honor-anb-an00-root", upstream="https://github.com/Chuniex-0631/honor-anb-an00-root",
         transport="shizuku", runner="bundled", kmi_from="release", status="active"),
    dict(id="galaxyxr", name="galaxyxr-root", cve="CVE-2026-43499", klass="kernel-lpe",
         repo="booinspace/galaxyxr-root", upstream="https://github.com/booinspace/galaxyxr-root",
         transport="on-device", runner="bundled", kmi_from="release", status="active"),
    dict(id="samsung-root-kit", name="Samsung Root Kit", cve="CVE-2026-43499", klass="kernel-lpe",
         repo="shangu2003/SamsungRootKit", upstream="https://github.com/shangu2003/SamsungRootKit",
         transport="on-device", runner="toolbox", kmi_from="model", status="active"),
    dict(id="ghostlock-nvidia-shield", name="GhostLock NVIDIA Shield", cve="CVE-2026-43499", klass="kernel-lpe",
         repo="cyberbalsa/GhostLock-NVIDIA-Shield-9.2.4", upstream="https://github.com/cyberbalsa/GhostLock-NVIDIA-Shield-9.2.4",
         transport="adb", runner="bundled", kmi_from="release", status="active"),

    # --- DirtyFrag (CVE-2026-43284) ---
    dict(id="dirtyfrag", name="DirtyFrag", cve="CVE-2026-43284", klass="kernel-lpe",
         repo="mitschud/DirtyFrag", upstream="https://github.com/mitschud/DirtyFrag",
         transport="on-device", runner="libdfroot.so + per-KMI .ko", kmi_from="kmi", status="active"),
    dict(id="dirtyfalg-vr", name="DirtyFalg-vr", cve="CVE-2026-43284", klass="kernel-lpe",
         repo="DONGDERS/DirtyFalg-vr", upstream="https://github.com/DONGDERS/DirtyFalg-vr",
         transport="on-device", runner="bundled", kmi_from="kmi", status="active"),
    dict(id="dfroot", name="DFRoot", cve="CVE-2026-43284", klass="kernel-lpe",
         repo="diabl0w/DFRoot", upstream="https://github.com/diabl0w/DFRoot",
         transport="on-device", runner="bundled", kmi_from="kmi", status="active"),
    dict(id="dfreroot", name="DFReroot", cve="CVE-2026-43284", klass="kernel-lpe",
         repo="polygraphene/DFReroot", upstream="https://github.com/polygraphene/DFReroot",
         transport="on-device", runner="second-stage", kmi_from="kmi", status="active"),
    dict(id="dirtyinit", name="DirtyInit", cve="CVE-2026-43284", klass="limited-init",
         repo="combeng6th/DirtyInit", upstream="https://github.com/combeng6th/DirtyInit",
         transport="adb", runner="bundled", kmi_from="n/a", status="active"),
    dict(id="galaxycrash", name="GalaxyCrash", cve="CVE-2026-43284", klass="kernel-lpe",
         repo="RemiPelloux/GalaxyCrash", upstream="https://github.com/RemiPelloux/GalaxyCrash",
         transport="on-device", runner="bundled", kmi_from="kmi", status="active"),

    # --- CVE-2025-21479 (Adreno KGSL) ---
    dict(id="cve-2025-21479-fx5p", name="CVE-2025-21479-FX5P", cve="CVE-2025-21479", klass="kernel-lpe",
         repo="diyiqiuye/CVE-2025-21479-FX5P", upstream="https://github.com/diyiqiuye/CVE-2025-21479-FX5P",
         transport="adb", runner="bundled", kmi_from="release", status="active"),
    dict(id="cheese", name="cheese", cve="CVE-2025-21479", klass="kernel-lpe",
         repo="zhuowei/cheese", upstream="https://github.com/zhuowei/cheese",
         transport="adb", runner="bundled", kmi_from="release", status="active"),
    dict(id="eureka-panther", name="eureka_panther-adreno-gpu-exploit-1", cve="CVE-2025-21479", klass="kernel-lpe",
         repo="FreeXR/eureka_panther-adreno-gpu-exploit-1", upstream="https://github.com/FreeXR/eureka_panther-adreno-gpu-exploit-1",
         transport="adb", runner="bundled", kmi_from="release", status="active"),
    dict(id="cheese-cake", name="cheese-cake", cve="CVE-2025-21479", klass="kernel-lpe",
         repo="sarabpal-dev/cheese-cake", upstream="https://github.com/sarabpal-dev/cheese-cake",
         transport="adb", runner="bundled", kmi_from="release", status="active"),

    # --- limited / non-kernel ---
    dict(id="tlpe", name="TLPE", cve="CVE-2026-49881", klass="limited-system_server",
         repo="Supersonic/TLPE", upstream="https://github.com/Supersonic/TLPE",
         transport="adb", runner="bundled", kmi_from="spl", status="active"),
    dict(id="zygote-31317", name="zygote-CVE-2024-31317", cve="CVE-2024-31317", klass="limited-zygote",
         repo="rabits/zygote-CVE-2024-31317", upstream="https://github.com/rabits/zygote-CVE-2024-31317",
         transport="adb", runner="bundled", kmi_from="spl", status="active"),
    dict(id="magica", name="Magica", cve="seccomp-disable", klass="limited-utility",
         repo="vvb2060/Magica", upstream="https://github.com/vvb2060/Magica",
         transport="adb", runner="utility", kmi_from="n/a", status="archived"),
    dict(id="lspromise", name="LSPromise", cve="CVE-2026-49881+CVE-2026-43284", klass="kernel-lpe",
         repo="LSPosed/LSPromise", upstream="https://github.com/LSPosed/LSPromise",
         transport="on-device", runner="bundled", kmi_from="release", status="active"),

    # --- boot-chain / unlock (separate scope, higher risk) ---
    dict(id="queststack", name="QuestStack", cve="CVE-2021-1931+GhostLock", klass="boot-chain",
         repo="starseed12345/QuestStack", upstream="https://github.com/starseed12345/QuestStack",
         transport="adb", runner="bundled", kmi_from="release", status="high-risk"),
    dict(id="gbl-root-canoe", name="gbl_root_canoe", cve="boot-chain", klass="boot-chain",
         repo="SuperTurtleDev/gbl_root_canoe", upstream="https://github.com/SuperTurtleDev/gbl_root_canoe",
         transport="pc", runner="uefi", kmi_from="n/a", status="high-risk"),
    dict(id="mtkclient", name="mtkclient", cve="brom-edl", klass="boot-chain",
         repo="bkerler/mtkclient", upstream="https://github.com/bkerler/mtkclient",
         transport="pc-usb", runner="python", kmi_from="n/a", status="high-risk"),
    dict(id="kaeru", name="kaeru", cve="mtk-lk", klass="boot-chain",
         repo="R0rt1z2/kaeru", upstream="https://github.com/R0rt1z2/kaeru",
         transport="pc-usb", runner="bundled", kmi_from="n/a", status="high-risk"),
    dict(id="oppo-mtk-fastboot", name="oppo-mtk-fastboot-unlock", cve="mtk-fastboot", klass="boot-chain",
         repo="Shocked-Cat/oppo-mtk-fastboot-unlock", upstream="https://github.com/Shocked-Cat/oppo-mtk-fastboot-unlock",
         transport="pc-usb", runner="bundled", kmi_from="n/a", status="high-risk"),
    dict(id="qualcomm-avb-poc", name="qualcomm_avb_exploit_poc", cve="avb", klass="boot-chain",
         repo="atlas4381/qualcomm_avb_exploit_poc", upstream="https://github.com/atlas4381/qualcomm_avb_exploit_poc",
         transport="pc", runner="bundled", kmi_from="n/a", status="high-risk"),

    # --- managers (not root methods) ---
    dict(id="kernelsu", name="KernelSU", cve="manager", klass="manager",
         repo="tiann/KernelSU", upstream="https://github.com/tiann/KernelSU",
         transport="n/a", runner="manager apk", kmi_from="n/a", status="manager"),
    dict(id="kernelsu-next", name="KernelSU-Next", cve="manager", klass="manager",
         repo="KernelSU-Next/KernelSU-Next", upstream="https://github.com/KernelSU-Next/KernelSU-Next",
         transport="n/a", runner="manager apk", kmi_from="n/a", status="manager"),
    dict(id="kowsu", name="KowSU", cve="manager", klass="manager",
         repo="KOWX712/KernelSU", upstream="https://github.com/KOWX712/KernelSU",
         transport="n/a", runner="manager apk", kmi_from="n/a", status="manager-archived"),
    dict(id="sukisu-nova", name="SukiSU Nova", cve="manager", klass="manager",
         repo="SukiSU-Nova/SukiSU-Nova", upstream="https://github.com/SukiSU-Nova/SukiSU-Nova",
         transport="n/a", runner="manager apk", kmi_from="n/a", status="manager"),
    dict(id="bakasu", name="BakaSU", cve="manager", klass="manager",
         repo="Baka-SU/BakaSU", upstream="https://github.com/Baka-SU/BakaSU",
         transport="n/a", runner="manager apk", kmi_from="n/a", status="manager"),
    dict(id="diabl0w-kernelsu", name="diabl0w KernelSU", cve="manager", klass="manager",
         repo="diabl0w/KernelSU", upstream="https://github.com/diabl0w/KernelSU",
         transport="n/a", runner="manager apk (Samsung)", kmi_from="n/a", status="manager"),
    dict(id="samsung-root-managers", name="Samsung Root Managers", cve="manager", klass="manager",
         repo="thecaticorn01/Samsung-Root-Managers", upstream="https://github.com/thecaticorn01/Samsung-Root-Managers",
         transport="n/a", runner="rebuilt managers", kmi_from="n/a", status="manager"),
    dict(id="shizuku", name="Shizuku", cve="transport", klass="transport",
         repo="RikkaApps/Shizuku", upstream="https://github.com/RikkaApps/Shizuku",
         transport="adb", runner="transport apk", kmi_from="n/a", status="transport"),
    dict(id="obtainium", name="Obtainium", cve="tool", klass="tool",
         repo="ImranR98/Obtainium", upstream="https://github.com/ImranR98/Obtainium",
         transport="n/a", runner="apk updater", kmi_from="n/a", status="tool"),
    dict(id="bifrost", name="Bifrost", cve="tool", klass="tool",
         repo="zacharee/Bifrost", upstream="https://github.com/zacharee/Bifrost",
         transport="n/a", runner="firmware downloader", kmi_from="n/a", status="tool"),
    dict(id="termux", name="Termux", cve="tool", klass="tool",
         repo="termux/termux-app", upstream="https://github.com/termux/termux-app",
         transport="n/a", runner="terminal", kmi_from="n/a", status="tool"),
    dict(id="extractkit", name="ExtractKit", cve="calibration", klass="tool",
         repo="qq2770355800/ExtractKit", upstream="https://github.com/qq2770355800/ExtractKit",
         transport="n/a", runner="offset extractor", kmi_from="n/a", status="tool"),
    dict(id="rootwithoutunlock", name="rootWithoutUnlockBootloaderList", cve="taxonomy", klass="reference",
         repo="lokey0905/rootWithoutUnlockBootloaderList", upstream="https://github.com/lokey0905/rootWithoutUnlockBootloaderList",
         transport="n/a", runner="index", kmi_from="n/a", status="reference"),
    dict(id="matisse-public", name="matisse-public", cve="CVE-2026-43499", klass="research",
         repo="ihamn/matisse-public", upstream="https://github.com/ihamn/matisse-public",
         transport="n/a", runner="source+failure log", kmi_from="release", status="research"),
    dict(id="redmi-pad-se-87", name="redmi-pad-se-87-root", cve="CVE-2026-43499", klass="research",
         repo="nestchao/redmi-pad-se-87-root", upstream="https://github.com/nestchao/redmi-pad-se-87-root",
         transport="n/a", runner="ruled-out routes", kmi_from="release", status="research"),
    dict(id="nebusecl-ionstack", name="NebuSec IonStack", cve="CVE-2026-43499", klass="research",
         repo="NebuSec/CyberMeowfia", upstream="https://github.com/NebuSec/CyberMeowfia",
         transport="n/a", runner="PoC source", kmi_from="release", status="research"),
]

# ------------------------------------------------------- per-loader targets
TARGETS = []

def T(loader, match_key, match_kind, kernel=None, kmi=None, devices=None, runner=None,
      note=None, source=None, page=None, extra=None):
    TARGETS.append(dict(
        loader=loader, match_kind=match_kind, match_key=match_key,
        kernel=kernel, kmi=kmi, devices=devices or [], page=page,
        runner=runner, note=note, source=source, extra=extra or {}))

# --- Root My Galaxy payload feed (model + kernelVersion) ---------------
rmg_path = os.path.join(ROOT, "BuSung-dev", "Root-My-Galaxy-Payloads", "support", "targets-v3.json")
rmg = json.load(open(rmg_path, encoding="utf-8"))
for p in rmg["payloads"]:
    kv = p.get("kernelVersions") or []
    T("root-my-galaxy", p["payloadId"], "model+kernel",
      kernel=kv[0] if kv else None, devices=p.get("models") or [],
      runner=p.get("exploit", {}).get("url"), note=p.get("displayName"),
      source="Root-My-Galaxy-Payloads/support/targets-v3.json")

# --- Root My Pixel (codename + build display + kernel release) ---------
rmp = json.load(open(os.path.join(ROOT, "alex193a", "Root-My-Pixel", "app", "src", "main", "assets", "profiles.json"), encoding="utf-8"))
for p in rmp["profiles"]:
    T("root-my-pixel", p["profileId"], "codename+build+kernel",
      kernel=p.get("kernelRelease"), kmi=p.get("kmi"), devices=[p.get("codename")],
      runner=p.get("exploitAsset"), note=p.get("buildDisplay"),
      source="alex193a/Root-My-Pixel assets/profiles.json")

# --- WitAqua Root-My-Device feed (full fingerprint + kernelVersion) ----
wq = json.load(open(os.path.join(ROOT, "WitAqua-tools", "Root-My-Device-Payloads", "src", "targets.json"), encoding="utf-8"))
for t in wq["targets"]:
    T("root-my-device-witaqua", t["profileId"], "device+region+release",
      kernel=t.get("kernelRelease"), kmi=None, devices=[t.get("device")],
      runner=t.get("core"), note=f"{t.get('marketingName')} / {t.get('buildDisplay')}",
      source="WitAqua-tools/Root-My-Device-Payloads src/targets.json",
      extra=dict(fingerprint=t.get("buildFingerprint"), kernelVersion=t.get("kernelVersion"),
                 soc=t.get("soc"), page=t.get("pageSize")))

# --- vivo / iQOO catalog (kernel release -> payload) --------------------
vv = json.load(open(os.path.join(ROOT, "zenyxx-xd", "RootMyVivo-Payloads", "catalog", "devices.json"), encoding="utf-8"))
for bid, b in vv.get("builds", {}).items():
    for rel in b.get("match", []) or []:
        T("rootmyvivo", bid, "kernel-release", kernel=rel,
          runner=(b.get("file") or {}).get("url"),
          note=b.get("matchCondition", "")[:120],
          source="zenyxx-xd/RootMyVivo-Payloads catalog/devices.json")

# --- GhostLock App profile index (release -> conf) ----------------------
gl_dir = os.path.join(ROOT, "YuKongA", "ghostlock-app", "app", "src", "main", "assets", "kernel_profiles")
gl_idx = read(os.path.join(gl_dir, "index.conf"))
for m in re.finditer(r'release\s*=\s*"([^"]+)"\s*,\s*file\s*=\s*"([^"]+)"', gl_idx or ""):
    rel, fname = m.group(1), m.group(2)
    T("ghostlock-app", rel, "kernel-release", kernel=rel, runner=fname,
      source="YuKongA/ghostlock-app assets/kernel_profiles/index.conf")

# --- GhostKey profile index -------------------------------------------
gk_dir = os.path.join(ROOT, "Feiman55", "CVE-2026-98260-GhostKey", "app", "src", "main", "assets", "kernel_profiles")
gk_idx = read(os.path.join(gk_dir, "index.conf"))
for m in re.finditer(r'release\s*=\s*"([^"]+)"\s*,\s*file\s*=\s*"([^"]+)"', gk_idx or ""):
    T("ghostkey", m.group(1), "kernel-release", kernel=m.group(1), runner=m.group(2),
      source="Feiman55/CVE-2026-98260-GhostKey assets/kernel_profiles/index.conf")

# --- TimerRoot conf/ (kernel release + page size) ----------------------
tr_dir = os.path.join(ROOT, "MVN123123123123123", "TimerRoot", "conf")
for dp, dn, fn in os.walk(tr_dir):
    for f in fn:
        if not f.endswith(".conf"):
            continue
        txt = read(os.path.join(dp, f)) or ""
        rel = re.search(r'release\s*=\s*"([^"]+)"', txt)
        kmi = re.search(r'ksu_kmi\s*=\s*"([^"]+)"', txt)
        page = "16384" if "16k" in f.lower() else ("4096" if "4k" in f.lower() else None)
        if rel:
            T("timerroot", rel.group(1), "kernel-release+page", kernel=rel.group(1),
              kmi=kmi.group(1) if kmi else None, page=page, runner=os.path.relpath(os.path.join(dp, f), tr_dir).replace("\\", "/"),
              note="samsung" if "samsung" in dp else ("dirty" if "dirty" in dp else None),
              source="MVN123123123123123/TimerRoot conf/")

# --- Shark exact uname -> runner map (from source of truth) -------------
shark_src = read(os.path.join(ROOT, "CYB3R0ID694", "Shark-Moto", "app", "app", "src", "main", "java", "com", "shark", "kty", "JailbreakCore.kt"))
for m in re.finditer(r'"([0-9][^"]*-\d+k)"\s+to\s+"(\w+)"', shark_src or ""):
    T("shark", m.group(1), "kernel-release", kernel=m.group(1), runner=m.group(2),
      source="CYB3R0ID694/Shark-Moto JailbreakCore.kt")
# Shark dynamic profiles.json (release + offsets)
shark_prof = json.load(open(os.path.join(ROOT, "CYB3R0ID694", "Shark-Moto", "profiles.json"), encoding="utf-8"))
for p in shark_prof.get("profiles", []):
    T("shark", p["release"], "kernel-release", kernel=p["release"], kmi=None,
      runner=p.get("engine"), note=p.get("model"),
      source="CYB3R0ID694/Shark-Moto profiles.json",
      extra={k: v for k, v in p.items() if k not in ("release", "model", "engine")})

# --- yuehong assistant: bundled payloads by vendor/device ---------------
yh_dir = os.path.join(ROOT, "qq2070006042-create", "yuehong-privilege-assistant", "assistant", "src", "main", "jniLibs", "arm64-v8a")
if os.path.isdir(yh_dir):
    for f in sorted(os.listdir(yh_dir)):
        if not f.endswith(".so"):
            continue
        # libksu_<vendor>_<device...>_<hash>.so
        stem = f[4:-3] if f.startswith("lib") else f[:-3]
        T("yuehong", stem, "bundled-payload-name", runner=f,
          note=stem.replace("_", " "),
          source="qq2070006042-create/yuehong-privilege-assistant jniLibs")

# --- DirtyFrag / DirtyFalg-vr per-KMI modules --------------------------
df_dir = os.path.join(ROOT, "mitschud", "DirtyFrag", "app", "src", "main", "jni", "ko")
if os.path.isdir(df_dir):
    for f in sorted(os.listdir(df_dir)):
        m = re.match(r"dfroot-(android\d+-\d+\.\d+)\.ko", f)
        if m:
            T("dirtyfrag", m.group(1), "kmi", kmi=m.group(1), runner=f,
              source="mitschud/DirtyFrag app/src/main/jni/ko")

# --- per-repo device tables from READMEs (brand/model/kernel) -----------
README_TABLES = [
    ("ghostlock-oneplus", "JoinChang/ghostlock-oneplus", "README.md"),
    ("ghostsam", "snothin/GhostSam", "README.md"),
    ("root-my-galaxy-next", "rushiranpise/Root-My-Galaxy-Next", "README.md"),
    ("root-my-galaxy-ultra", "xorpix/Root-My-Galaxy-Ultra", "README.md"),
    ("root-my-galaxy-extended", "igorcv88/Root-My-Galaxy-Extended", "README.md"),
    ("rootmys24", "NanoTurtle1145/root-my-s24", "README.md"),
    ("shark", "kpgc10kai/Shark", "README.md"),
    ("uniiroot", "kuuky29/UniRoot", "README.md"),
    ("timerroot", "MVN123123123123123/TimerRoot", "docs/supported-devices.md"),
]
KERNEL_RE = re.compile(r"\b(\d+\.\d+(?:\.\d+)?-android\d{2}-\d+(?:-g[0-9a-f]+)?(?:-[a-z0-9.]+)?(?:-\d+k)?)\b")
PLAIN_KERNEL_RE = re.compile(r"\b(\d+\.\d+\.\d+(?:-android\d{2}-\d+)?)\b")
for loader, repo, relpath in README_TABLES:
    txt = read(os.path.join(ROOT, *repo.split("/"), relpath))
    if not txt:
        continue
    seen = set()
    for line in txt.splitlines():
        if "|" not in line:
            continue
        kers = KERNEL_RE.findall(line) or PLAIN_KERNEL_RE.findall(line)
        if not kers:
            continue
        cells = [c.strip(" `") for c in line.strip().strip("|").split("|")]
        dev = next((c for c in cells if c and not KERNEL_RE.fullmatch(c.strip(" `")) and "kernel" not in c.lower()), None)
        for k in kers:
            key = (loader, k)
            if key in seen:
                continue
            seen.add(key)
            T(loader, k, "kernel-release", kernel=k, devices=[dev] if dev else [],
              note=line.strip()[:160], source=f"{repo} {relpath}")

# --- dedupe -------------------------------------------------------------
seen = set()
uniq = []
for t in TARGETS:
    key = (t["loader"], t["match_key"], t.get("kernel"), t.get("kmi"), t.get("runner"))
    if key in seen:
        continue
    seen.add(key)
    uniq.append(t)
TARGETS = uniq

doc = {
    "schema_version": 1,
    "generated_from": "https://github.com/fynks/awesome-temporary-root",
    "loader_count": len(LOADERS),
    "target_count": len(TARGETS),
    "loaders": LOADERS,
    "targets": TARGETS,
}

os.makedirs(os.path.dirname(OUT_ASSETS), exist_ok=True)
os.makedirs(os.path.dirname(OUT_TOOLS), exist_ok=True)
for out in (OUT_ASSETS, OUT_TOOLS):
    with open(out, "w", encoding="utf-8") as f:
        json.dump(doc, f, indent=1, ensure_ascii=False)
    print("wrote", out, os.path.getsize(out), "bytes")

print("loaders:", len(LOADERS), "targets:", len(TARGETS))
by = {}
for t in TARGETS:
    by[t["loader"]] = by.get(t["loader"], 0) + 1
for k, v in sorted(by.items(), key=lambda x: -x[1]):
    print(f"  {v:5d}  {k}")
