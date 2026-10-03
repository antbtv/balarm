---
name: verify-alarm-reliability
description: Прогон сценариев надёжности будильника Balarm (R1–R19 из PRD §9.2) на эмуляторе/устройстве через adb и mobile-mcp — Doze, перезагрузка до разблокировки (Direct Boot), смена часового пояса, overlay поверх приложений, бездействие в миссии, падение процесса. Используй после изменений в движке будильника, ресиверах, сервисе звонка, разрешениях, и перед релизом.
argument-hint: "<список сценариев, например R1 R3 R5, или all>"
---

# verify-alarm-reliability

## 0. Подготовка

**Ресурсы машины (CLAUDE.md):** один эмулятор, без окна, с лимитом памяти; перед запуском — `./gradlew --stop`, после — `adb emu kill`. Образ, нужный разово (например API 34), ставится → тест → удаляется.

```bash
# До сборки: иначе Gradle подпишет APK другим debug-ключом (~/.android) и `adb install -r` упадёт
# с INSTALL_FAILED_UPDATE_INCOMPATIBLE — тогда `adb uninstall $PKG` и заново.
source <(grep "^export ANDROID\|^export PATH" ~/.profile)   # ANDROID_HOME, ANDROID_AVD_HOME, adb/emulator в PATH
./gradlew :app:assembleDebug && ./gradlew --stop
emulator -avd balarm_api37 -memory 2048 -no-window -no-snapshot -no-audio &   # фоном
adb wait-for-device; until [ "$(adb shell getprop sys.boot_completed | tr -d '\r')" = 1 ]; do adb shell sleep 2; done

PKG=com.antbtv.balarm
adb install -r -g app/build/outputs/apk/debug/app-debug.apk   # -g: POST_NOTIFICATIONS без диалога
adb shell am start -W -n $PKG/.MainActivity                    # ОБЯЗАТЕЛЬНО: снимает stopped state после установки
adb shell input keyevent KEYCODE_HOME
adb shell getprop ro.build.version.sdk                          # зафиксировать API в отчёте
adb logcat -c
```
Без первого запуска приложение остаётся в stopped state: alarms и broadcast ему не доставляются, любой сценарий «провалится» ложно.

### Debug-команды (только debug-сборка, `DebugAlarmReceiver`)
```bash
dbg() { adb shell am broadcast -n $PKG/.debug.DebugAlarmReceiver -a $PKG.debug.$1 "${@:2}"; }
dbg SCHEDULE_IN --ei minutes 2                 # + --ei seconds 30 --es label Work --es days MON,TUE (label без пробелов)
dbg LIST                                       # DEBUG_ALARM id=… time=… next=… kind=…
dbg DISMISS                                    # команда текущему звонку; без звонка — DEBUG_DISMISS ignored=no_ringing
dbg SNOOZE                                     # то же для «Отложить»
dbg RESCHEDULE_ALL | dbg CLEAR_ALL
dbg CRASH                                      # необработанное исключение на главном потоке
```
`SCHEDULE_IN` округляет момент **вверх до целой минуты** (время будильника — целые минуты): фактическое время — в `DEBUG_SCHEDULED … time=`. Закладывай +1 мин к ожиданию. С `--es days` без сегодняшнего дня будильник сработает в ближайший из указанных дней.
Ресивер закрыт разрешением `DUMP`: команды работают только из `adb shell`.

### Как проверять
```bash
adb shell dumpsys alarm | grep -B2 -A8 "$PKG"   # тип RTC_WAKEUP и alarmClock (только setAlarmClock)
adb logcat -s Balarm:I AndroidRuntime:E          # события, см. ниже
```
При первом показе экрана звонка Android может показать подсказку «Viewing full screen» (скрытые системные панели) — это не ошибка. Экран звонка — скриншот или `mobile_list_elements_on_screen` (mobile-mcp), либо `adb shell dumpsys window | grep mCurrentFocus` → `RingingActivity`.

### События лога (тег `Balarm`, формат `EVENT key=value`)
| Событие | Значит |
|---|---|
| `SCHEDULED id at kind` / `SCHEDULE_FAILED id at` | отдано в `setAlarmClock` / система отказала |
| `CANCELLED id` | будильник снят из `AlarmManager` (удалён) |
| `RESCHEDULE_ERROR id error` | один будильник не перепланировался; остальные — дальше |
| `RESCHEDULE_ALL reason count` / `RESCHEDULE_ALL_FAILED reason error` | перепланирование (LOCKED_BOOT, BOOT, TIME_SET, TIMEZONE_CHANGED, PACKAGE_REPLACED, LOCALE_CHANGED, APP_LAUNCH, DEBUG); `error=BroadcastTimeout` — сторож goAsync |
| `CATCH_UP id missed_at` | пропуск ≤ 10 мин (перезагрузка) — догоняющий звонок через 3 с |
| `ALARM_FIRED id kind late_ms` | движок зафиксировал срабатывание |
| `FIRE_SKIPPED id reason` | STALE / DELETED / DUPLICATE / MISSED |
| `FIRE_DEGRADED id error` | движок недоступен — звонок с настройками по умолчанию |
| `RINGING_STARTED id degraded` | звук пошёл (критерий: ≤ 2 с после `ALARM_FIRED`; `degraded=true` — сработал сторож 2 с) |
| `SOUND_STARTED source volume` / `SOUND_FALLBACK reason` | `raw`/`tone`; `volume=0/…` — будильник беззвучен (риск M1) |
| `RINGING_QUEUED id` | второй будильник ждёт (FR-RING-7) |
| `RINGING_STOPPED id reason` | dismiss / snooze / auto_stop / destroyed |
| `SNOOZED id until count`, `DISMISSED id reason` | USER / AUTO_STOP |
| `CRASH_REARMED id at` | процесс падает посреди звонка → RESUME через 3 с |
| `FGS_START_FAILED id error` | сервис не стартовал → fallback-уведомление |
| `RINGING_COMMAND_FAILED command error`, `VIBRATION_FAILED error` | ошибка команды/вибрации, звонок продолжается |
| `DEBUG_SCHEDULED id time days`, `DEBUG_LIST count`, `DEBUG_ALARM id time days enabled next kind snoozes`, `DEBUG_CLEARED count`, `DEBUG_CRASH`, `DEBUG_DISMISS/SNOOZE ignored=no_ringing` | ответы debug-команд |

## 1. Сценарии
| ID | Команды | Ожидание |
|---|---|---|
| R1 | `dbg SCHEDULE_IN --ei minutes 2`; `adb shell input keyevent KEYCODE_SLEEP` | экран включился, `RingingActivity`, `RINGING_STARTED` |
| R2 | schedule +3; экран off; `adb shell dumpsys battery unplug`; разрешить deep idle при близком alarm clock: API 37 `adb shell settings put global device_idle_constants min_time_to_alarm=1000`, API 34 `adb shell device_config put device_idle min_time_to_alarm 1000`; `adb shell cmd deviceidle force-idle deep`; `adb shell dumpsys deviceidle get deep` = IDLE (без этого застревает на INACTIVE «Unable to go deep idle») | звонок ±5 с; после — `deviceidle unforce`, `settings delete global device_idle_constants` / `device_config delete device_idle min_time_to_alarm`, `dumpsys battery reset` |
| R3 | `adb shell getprop ro.crypto.type` = `file` (иначе Direct Boot не проверяем — ⏭); PIN `adb shell locksettings set-pin 1111`; schedule +4; `adb reboot`; `adb wait-for-device`; **не** разблокировать (до разблокировки `/sdcard` недоступен — `uiautomator dump /data/local/tmp/ui.xml`) | `RESCHEDULE_ALL reason=LOCKED_BOOT`, звонок до разблокировки |
| R4 | как R3 + разблокировка `adb shell input text 1111 && adb shell input keyevent 66` | звонок; `BOOT` не создаёт дубликатов (`dbg LIST`) |
| R5 | schedule +3; `adb shell cmd alarm set-timezone Asia/Tokyo`; затем обратно | `RESCHEDULE_ALL reason=TIMEZONE_CHANGED`, `dumpsys alarm` — пересчитано |
| R6 | `adb shell cmd alarm set-time <ms>` вперёд/назад | `TIME_SET`, корректный пересчёт; назад — без второго звонка |
| R7 | DST — ручной (дай пользователю инструкцию) | |
| R8 | schedule +2; `adb shell am kill $PKG` (НЕ force-stop: он легально снимает alarms) | звонок |
| R9 | schedule +3; `adb install -r app/build/outputs/apk/debug/app-debug.apk` | `PACKAGE_REPLACED`, alarm в dumpsys на месте |
| R10 | schedule +2; `adb shell am start -a android.settings.SETTINGS` | `RingingActivity` поверх (или heads-up при разблокированном экране — overlay с M3) |
| R11 | `adb shell cmd notification set_dnd priority` (будильники разрешены; `on` = Total Silence глушит и будильники — ограничение платформы, предупреждение — M3); беззвучный режим | `SOUND_STARTED source=raw` (громкость потока ALARM ≠ 0) |
| R12 | `adb shell appops set $PKG USE_FULL_SCREEN_INTENT deny` | heads-up вместо экрана, звук есть (баннер — M3) |
| R13 | два `SCHEDULE_IN` на одну минуту | один экран, `RINGING_QUEUED`, второй звонит после «Отключить» |
| R14 | `adb emu gsm call 5551234` во время звонка | M4 (FR-RING-8) |
| R15 | битый файл мелодии | M4 |
| R16–R17 | миссии | M5 |
| R18 | OEM — ручной, реальное устройство | |
| R19 | API 37: `adb shell cmd audio set-enable-hardening throw` (в образе balarm_api37 команды нет — молча rc=0, `mHardeningOverride=0`); звонок с выключенным экраном | звук есть, исключений нет; `adb shell dumpsys audio \| grep -i AudioHardening`: строки `would be muted … usage: USAGE_ALARM … exemption: 4` при `mutedState:none` — известный риск M1 (исключение exact alarm + USAGE_ALARM), не провал; провал — `mutedState` ≠ none или исключение |
| CRASH | schedule +2; во время звонка `dbg CRASH` (один раз: два падения за < 60 с — система останавливает процесс и снимает alarms, ADR-007 §7) | `CRASH_REARMED`, процесс умер (`pidof` сменился), через ~3–5 с снова `RINGING_STARTED` (у RESUME нет `ALARM_FIRED`); после «Отключить» `dbg LIST` — у повторяющегося обычное следующее срабатывание (`kind=REGULAR`), разовый `enabled=false`. Если экран уже был включён и разблокирован — после возврата только heads-up (overlay — M3) |
| AUTO | (долго) звонок без реакции 30 мин | `RINGING_STOPPED reason=auto_stop`, `DISMISSED reason=AUTO_STOP`, уведомление «Пропущенный будильник» |

## 2. После сценариев
Верни всё обратно: DND off, timezone, `appops set … allow`, `locksettings clear --old 1111`, `deviceidle unforce`, `cmd audio set-enable-hardening` — по умолчанию; `dbg CLEAR_ALL`; `adb emu kill`.

## 3. Отчёт
Формат — как у агента tester (таблица ID / результат / доказательство: строки лога, dumpsys, скриншот). Непрогнанные сценарии помечай ⏭ с причиной. Любой провал R1–R6, R8–R11, R13, R16–R17, R19, CRASH = **critical**.
