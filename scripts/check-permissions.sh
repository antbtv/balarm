#!/usr/bin/env bash
# Проверки итогового release-манифеста :app (PRD §7, NFR-4, FR-FLAG-5):
#  1) все uses-permission есть в config/permissions-allowlist.txt;
#  2) в release нет debug-компонентов (FeatureFlagsActivity и пакет .debug).
# Запуск после сборки release (./gradlew build или :app:assembleRelease).
set -euo pipefail

root="$(cd "$(dirname "$0")/.." && pwd)"
manifest="$root/app/build/intermediates/merged_manifests/release/processReleaseManifest/AndroidManifest.xml"

if [[ ! -f "$manifest" ]]; then
    echo "release merged manifest not found — build :app release first ($manifest)" >&2
    exit 2
fi

python3 - "$manifest" "$root/config/permissions-allowlist.txt" <<'PY'
import sys
import xml.etree.ElementTree as ET

manifest_path, allowlist_path = sys.argv[1], sys.argv[2]
ns = "{http://schemas.android.com/apk/res/android}"
root = ET.parse(manifest_path).getroot()

declared = {
    el.get(ns + "name")
    for tag in ("uses-permission", "uses-permission-sdk-23")
    for el in root.iter(tag)
}
with open(allowlist_path, encoding="utf-8") as f:
    allowed = {line.strip() for line in f if line.strip() and not line.lstrip().startswith("#")}

errors = []
if not declared:
    # androidx.core всегда добавляет DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION: пусто = сломан разбор.
    errors.append("no uses-permission found — manifest format changed?")
errors += [f"permission not in allowlist: {p}" for p in sorted(declared - allowed)]

components = [
    el.get(ns + "name") or ""
    for tag in ("activity", "activity-alias", "service", "receiver", "provider")
    for el in root.iter(tag)
]
errors += [f"debug component in release: {c}" for c in components if ".debug." in c or "FeatureFlagsActivity" in c]

if errors:
    print("\n".join(errors), file=sys.stderr)
    sys.exit(1)
print(f"Release manifest OK: {len(declared)} permission(s), no debug components")
PY
