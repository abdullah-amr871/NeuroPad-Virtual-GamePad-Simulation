"""Builds the files that are attached to a GitHub release.

    ./gradlew :app:assembleRelease
    python scripts/package_release.py

Output (in dist/):
    NeuroPad_<version>.apk
    NeuroPad_<version>_OneClick_Installer.zip   installer scripts + adb + APK + driver
    SHA256SUMS.txt
"""
import hashlib
import re
import shutil
import sys
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
APK = ROOT / "app" / "build" / "outputs" / "apk" / "release" / "app-release.apk"
DAEMON = ROOT / "app" / "src" / "main" / "assets" / "uinput_proto"
INSTALLER = ROOT / "installer"
DIST = ROOT / "dist"
# Unix scripts must stay executable after extraction on macOS / Linux.
EXECUTABLE = {".sh", ""}


def version() -> str:
    text = (ROOT / "app" / "build.gradle.kts").read_text(encoding="utf-8")
    return re.search(r'versionName\s*=\s*"([^"]+)"', text).group(1)


def add(archive: zipfile.ZipFile, source: Path, name: str) -> None:
    info = zipfile.ZipInfo.from_file(source, name)
    info.compress_type = zipfile.ZIP_DEFLATED
    mode = 0o755 if source.suffix in EXECUTABLE else 0o644
    info.external_attr = (0o100000 | mode) << 16
    archive.writestr(info, source.read_bytes())


def main() -> int:
    if not APK.is_file():
        print(f"Missing {APK}. Run ./gradlew :app:assembleRelease first.")
        return 1
    v = version()
    DIST.mkdir(exist_ok=True)
    apk_out = DIST / f"NeuroPad_{v}.apk"
    shutil.copyfile(APK, apk_out)

    zip_out = DIST / f"NeuroPad_{v}_OneClick_Installer.zip"
    folder = f"NeuroPad_{v}_OneClick_Installer"
    with zipfile.ZipFile(zip_out, "w") as archive:
        for path in sorted(INSTALLER.rglob("*")):
            if path.is_file():
                add(archive, path, f"{folder}/{path.relative_to(INSTALLER).as_posix()}")
        add(archive, APK, f"{folder}/NeuroPad.apk")
        add(archive, DAEMON, f"{folder}/uinput_proto")
        add(archive, ROOT / "LICENSE", f"{folder}/LICENSE.txt")

    sums = []
    for path in (apk_out, zip_out):
        digest = hashlib.sha256(path.read_bytes()).hexdigest()
        sums.append(f"{digest}  {path.name}")
        print(f"{path.name}  {path.stat().st_size / 1e6:.1f} MB")
    (DIST / "SHA256SUMS.txt").write_text("\n".join(sums) + "\n", encoding="utf-8")
    return 0


if __name__ == "__main__":
    sys.exit(main())
