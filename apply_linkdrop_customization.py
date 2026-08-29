#!/usr/bin/env python3
from pathlib import Path
import argparse
import re
import shutil
import subprocess
import sys

SCRIPT_DIR = Path(__file__).resolve().parent

TARGETS = {
    "ShareActivity.kt": Path("src/main/java/org/kde/kdeconnect/plugins/share/ShareActivity.kt"),
    "ShareScreen.kt": Path("src/main/java/org/kde/kdeconnect/ui/compose/screen/share/ShareScreen.kt"),
    "PluginFactory.kt": Path("src/main/java/org/kde/kdeconnect/plugins/PluginFactory.kt"),
}

def die(msg):
    print(f"ERROR: {msg}", file=sys.stderr)
    raise SystemExit(1)

def backup(repo, rel):
    src = repo / rel
    if not src.exists():
        die(f"Missing expected file: {rel}")
    dst = repo / ".linkdrop_backup" / rel
    dst.parent.mkdir(parents=True, exist_ok=True)
    if not dst.exists():
        shutil.copy2(src, dst)

def set_app_name(path, name):
    text = path.read_text(encoding="utf-8")
    pattern = re.compile(r'(<string\s+name="kde_connect"[^>]*>)(.*?)(</string>)')
    if pattern.search(text):
        text = pattern.sub(lambda m: m.group(1) + name + m.group(3), text, count=1)
        path.write_text(text, encoding="utf-8")

def add_default_strings(path):
    text = path.read_text(encoding="utf-8")
    additions = []
    if 'name="send_to_all_devices"' not in text:
        additions.append('    <string name="send_to_all_devices">Send to all devices</string>')
    if 'name="sent_to_all_devices"' not in text:
        additions.append('    <string name="sent_to_all_devices">Sent to %1$d device(s)</string>')
    if additions:
        if "</resources>" not in text:
            die("Default strings.xml has no closing resources tag")
        text = text.replace("</resources>", "\n" + "\n".join(additions) + "\n</resources>", 1)
        path.write_text(text, encoding="utf-8")

def add_arabic_strings(repo):
    ar_dir = repo / "src/main/res/values-ar"
    ar_dir.mkdir(parents=True, exist_ok=True)
    ar_file = ar_dir / "linkdrop_strings.xml"
    ar_file.write_text(
        '''<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="send_to_all_devices">إرسال إلى جميع الأجهزة</string>
    <string name="sent_to_all_devices">تم الإرسال إلى %1$d جهاز</string>
</resources>
''',
        encoding="utf-8"
    )

def main():
    p = argparse.ArgumentParser()
    p.add_argument("repo")
    p.add_argument("--name", default="LinkDrop")
    p.add_argument("--application-id", default="com.linkdrop.connect")
    p.add_argument("--full-features", action="store_true")
    p.add_argument("--build", action="store_true")
    args = p.parse_args()

    repo = Path(args.repo).resolve()
    if not (repo / "build.gradle.kts").exists():
        die("This does not look like kdeconnect-android")

    direct_files = [
        Path("build.gradle.kts"),
        Path("src/main/AndroidManifest.xml"),
        Path("src/main/res/values/strings.xml"),
        TARGETS["ShareActivity.kt"],
        TARGETS["ShareScreen.kt"],
    ]
    if not args.full_features:
        direct_files.append(TARGETS["PluginFactory.kt"])

    for rel in direct_files:
        backup(repo, rel)

    gradle = repo / "build.gradle.kts"
    text = gradle.read_text(encoding="utf-8")
    old = 'applicationId = "org.kde.kdeconnect_tp"'
    if old not in text:
        die("Could not find upstream applicationId")
    text = text.replace(old, f'applicationId = "{args.application_id}"', 1)
    gradle.write_text(text, encoding="utf-8")

    manifest = repo / "src/main/AndroidManifest.xml"
    mtext = manifest.read_text(encoding="utf-8")
    mtext = mtext.replace('android:label="KDE Connect"', 'android:label="@string/kde_connect"')
    manifest.write_text(mtext, encoding="utf-8")

    for strings in (repo / "src/main/res").glob("values*/strings.xml"):
        set_app_name(strings, args.name)

    add_default_strings(repo / "src/main/res/values/strings.xml")
    add_arabic_strings(repo)

    for filename in ("ShareActivity.kt", "ShareScreen.kt"):
        shutil.copy2(SCRIPT_DIR / "overrides" / filename, repo / TARGETS[filename])

    if not args.full_features:
        shutil.copy2(
            SCRIPT_DIR / "overrides" / "PluginFactory.kt",
            repo / TARGETS["PluginFactory.kt"]
        )

    print(f"Applied LinkDrop V1: {args.name} / {args.application_id}")
    print("Backup: .linkdrop_backup")

    if args.build:
        gradlew = repo / ("gradlew.bat" if sys.platform.startswith("win") else "gradlew")
        if not gradlew.exists():
            die("Gradle wrapper not found")
        subprocess.run([str(gradlew), "assembleDebug"], cwd=repo, check=True)

if __name__ == "__main__":
    main()
