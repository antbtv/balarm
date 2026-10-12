# Релиз (личное использование, sideload)

## Один раз: ключ подписи
Ключ создаёт владелец и хранит **вне репозитория** (потеря ключа = нельзя обновить установленное приложение; сделайте бэкап).

```bash
keytool -genkeypair -v -keystore ~/Work/IdeaProjects/balarm-release.jks -alias balarm \
  -keyalg RSA -keysize 4096 -validity 10000
```

В корне проекта создайте `keystore.properties` (в `.gitignore`):

```properties
storeFile=/home/<user>/balarm-release.jks
storePassword=...
keyAlias=balarm
keyPassword=...
```

## Сборка
```bash
./gradlew --stop
./gradlew :app:assembleRelease
$ANDROID_HOME/build-tools/<ver>/apksigner verify --print-certs app/build/outputs/apk/release/app-release.apk
adb install -r app/build/outputs/apk/release/app-release.apk
```
Без `keystore.properties` получится `app-release-unsigned.apk` (устанавливать нельзя).

Mapping для расшифровки стектрейсов: `app/build/outputs/mapping/release/mapping.txt` — сохраняйте вместе с каждой выданной сборкой.

## Проверки перед выдачей
Размер APK ≤ 15 МБ, встроенные мелодии не вырезаны shrinker'ом (`unzip -l app/build/outputs/apk/release/app-release.apk | grep -ciE "ogg|wav|mp3"` = числу `BuiltinSound` (сейчас 10); иначе звонок идёт резервным тоном, `SOUND_FALLBACK`), `./scripts/check-permissions.sh`, матрица надёжности PRD §9.2 на release-APK (скилл `verify-alarm-reliability`).
