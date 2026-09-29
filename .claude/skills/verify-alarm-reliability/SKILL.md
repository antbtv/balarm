---
name: verify-alarm-reliability
description: Прогон сценариев надёжности будильника Balarm (R1–R18 из PRD §9.2) на эмуляторе/устройстве через adb и mobile-mcp — Doze, перезагрузка до разблокировки (Direct Boot), смена часового пояса, overlay поверх приложений, бездействие в миссии. Используй после изменений в движке будильника, ресиверах, сервисе звонка, разрешениях, и перед релизом.
argument-hint: "<список сценариев, например R1 R3 R5, или all>"
---

# verify-alarm-reliability

## 0. Подготовка
```bash
adb devices                                   # должно быть устройство; нет → стоп, сообщить
PKG=$(grep applicationId app/build.gradle.kts | sed -E 's/.*"(.*)".*/\1/')
./gradlew :app:installDebug
adb shell getprop ro.build.version.sdk        # зафиксировать API
adb logcat -c
```
Хелпер «будильник через N минут» — debug-only broadcast (реализуется в M1):
```bash
adb shell am broadcast -n $PKG/.debug.DebugAlarmReceiver -a $PKG.debug.SCHEDULE_IN --ei minutes 2
```
Проверка, что alarm запланирован:
```bash
adb shell dumpsys alarm | grep -B2 -A8 "$PKG"   # ищем тип RTC_WAKEUP и alarmClock
```
Проверка, что звонит: `adb logcat -s Balarm:*` (событие `RINGING_STARTED`) + скриншот через mobile-mcp.

## 1. Сценарии
| ID | Команды | Ожидание |
|---|---|---|
| R1 | schedule +2; `adb shell input keyevent KEYCODE_POWER` | экран включён, RingingActivity на скриншоте |
| R2 | schedule +3; экран off; `adb shell dumpsys deviceidle force-idle`; `adb shell dumpsys deviceidle get deep` = IDLE | звонок ±5 с |
| R3 | на эмуляторе должен быть PIN (`adb shell locksettings set-pin 1111`); schedule +4; `adb reboot`; `adb wait-for-device`; **не** разблокировать | звонок; в logcat `LOCKED_BOOT_COMPLETED` → reschedule |
| R4 | как R3 + разблокировка `adb shell input text 1111 && adb shell input keyevent 66` | звонок |
| R5 | schedule +3; `adb shell cmd alarm set-timezone Asia/Tokyo`; затем обратно | dumpsys показывает пересчитанное время |
| R6 | `adb shell cmd alarm set-time <ms>` вперёд/назад (или настройки) | перепланировано корректно |
| R8 | schedule +2; `adb shell am kill $PKG` (НЕ force-stop) | звонок |
| R9 | schedule +3; `./gradlew :app:installDebug` повторно | alarm в dumpsys на месте |
| R10 | schedule +2; открыть другое приложение (`adb shell am start -a android.settings.SETTINGS`) | RingingActivity поверх |
| R11 | `adb shell cmd notification set_dnd on`; `adb shell cmd media_session volume --stream 2 --set 0` | звук есть |
| R12 | `adb shell appops set $PKG SYSTEM_ALERT_WINDOW deny`; `adb shell appops set $PKG USE_FULL_SCREEN_INTENT deny` | баннер в приложении, fallback-уведомление |
| R13 | два будильника на одну минуту | один экран, второй после первого |
| R14 | эмулятор: `adb emu gsm call 5551234` во время звонка | пауза/тихо, потом продолжение |
| R15 | удалить файл кастомной мелодии через `adb shell run-as $PKG rm …` | резервный звук |
| R16 | звонок → «Отключить» → миссия → не трогать 20 с | громкость вернулась, прогресс сброшен |
| R17 | во время миссии `adb shell input keyevent KEYCODE_BACK / KEYCODE_HOME / KEYCODE_APP_SWITCH` | звук не прекратился, экран вернулся |

R7 (DST) и R18 (OEM) — ручные; дай пользователю инструкцию.

## 2. После сценариев
Верни всё обратно: DND off, timezone, `appops set … allow`, `locksettings clear --old 1111`.

## 3. Отчёт
Формат — как у агента tester (таблица ID/результат/доказательство). Непрогнанные сценарии помечай ⏭ с причиной. Любой провал R1–R6, R8–R11, R16–R17 = **critical**.
