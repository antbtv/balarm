# ADR-017: Звук звонка в M4: громкость STREAM_ALARM с восстановлением, нарастание, цепочка резервов, превью, телефонный звонок

## Status
Proposed (2026-10-09, план M4). Расширяет ADR-008 (плеер — по-прежнему платформенный `MediaPlayer`) и ADR-007 (сессия звонка). **Изменяет ADR-012** в части пункта `ALARM_VOLUME` (п. 7 ниже); остальное в ADR-012 действует.

## Context
* FR-SND-5: не открылась/не заиграла за 1 с → встроенная по умолчанию → `ToneGenerator` + вибрация. FR-SND-6: нарастание от 10 % до целевой за 15/30/60 с. FR-SND-7: громкость будильника выставляется на `STREAM_ALARM` на время звонка и восстанавливается после. FR-EDIT-5: превью звука при перетаскивании слайдера громкости. FR-RING-8 (перенесён в M4 ADR-008 §4): во время телефонного звонка будильник тихо/вибрацией, после — полноценно.
* **Android 17 background audio hardening** (developer.android.com/about/versions/17/changes/bg-audio): `setStreamVolume`/`adjustStreamVolume`/`setStreamMute` из фона без видимой Activity или FGS (а для target 37 — FGS с while-in-use) **молча игнорируются**; исключение — приложение с exact-alarm-разрешением меняет потоки с `USAGE_ALARM`. Режим `adb shell cmd audio set-enable-hardening throw` бросает `IllegalStateException`. Следствие: громкость можно менять только внутри `RingingService` (FGS) или из видимой Activity; из `RescheduleReceiver`/`LOCKED_BOOT` — нельзя.
* У `STREAM_ALARM` дискретные индексы (на Pixel ≈ 7 шагов), минимальный индекс может быть > 0 (`getStreamMinVolume`), на части устройств громкость фиксирована (`isVolumeFixed`).
* Процесс может умереть посреди звонка (ADR-007 §7, crash re-arm → `RESUME`); громкость, выставленная перед падением, должна вернуться пользователю.
* В M1 зафиксирован риск «громкость будильника = 0 → тихий звонок» и пункт здоровья `ALARM_VOLUME` (INFO, ADR-012). С FR-SND-7 громкость звонка задаёт будильник.

## Decision
1. **Контракт плеера** (`:core:alarm`, вызывается с главного потока):
   ```kotlin
   interface AlarmSoundPlayer {
       /** [fadeIn] false — без нарастания (RESUME, CATCH_UP, ранний звук сторожа). */
       fun start(settings: SoundSettings, fadeIn: Boolean = true, onFallback: () -> Unit = {})
       /** FR-RING-8: true — заглушить мелодию (вибрацию включает сервис), false — продолжить с нарастания. */
       fun setMuted(muted: Boolean)
       fun stop()                                   // идемпотентно; возвращает громкость (п. 2)
   }
   ```
   Обобщение `setMuted` до уровня приглушения — в M5 (миссии, FR-MIS-2), когда появится второй потребитель.
2. **Громкость FR-SND-7 — `AlarmVolumeController`** (`:core:alarm`, `@Singleton`):
   ```kotlin
   enum class VolumeOwner { RINGING, PREVIEW }
   internal class AlarmVolumeController {
       fun acquire(owner: VolumeOwner, percent: Int)   // синхронно ставит индекс; запись в стор — асинхронно
       fun release(owner: VolumeOwner)                 // последний владелец → восстановление
       fun restorePendingIfIdle()                      // APP_LAUNCH (видимая Activity), если RingingState.Idle
   }
   // :core:domain/sound — хранение снимка, impl в :core:data (DataStore app_prefs, DE)
   data class SavedVolume(val original: Int, val applied: Int)
   interface RingVolumeStore { suspend fun get(): SavedVolume?; suspend fun put(value: SavedVolume); suspend fun clear() }
   ```
   * Индекс: `max(minIndex, ceil(percent × maxIndex / 100))`; `isVolumeFixed` → не трогаем, событие `VOLUME_FIXED`. Шкала слайдера — **10–100 % с шагом 10** (`SoundSettings.MIN_VOLUME`): 0 % при выключенной вибрации даёт беззвучный будильник — противоречит главному инварианту (правка PRD FR-EDIT-5).
   * Первый `acquire` при пустом сторе запоминает `SavedVolume(original = текущий, applied = выставленный)`; если снимок уже есть (возобновление после падения, второй будильник очереди, превью → звонок) — `original` не перезаписывается, обновляется только `applied`. Запись — в `@ApplicationScope` под `Mutex`; ошибка стора **не мешает** выставить громкость (звонок важнее восстановления).
   * После `setStreamVolume` — контрольное чтение `getStreamVolume`; не совпало (Android 17 молча проигнорировал) → событие `VOLUME_NOT_APPLIED(requested, actual)`, звонок продолжается на текущей громкости. `IllegalStateException`/`SecurityException` ловятся.
   * Восстановление: в `stop()` плеера, пока сервис ещё в foreground (ADR-007: `silence()` до `stopIfIdle`). Возвращаем `original` **только если** текущий индекс == `applied` (пользователь не менял громкость сам), затем `clear()`. Снимок, оставшийся после падения процесса, восстанавливается при следующем звонке (по его окончании) или при запуске UI (`restorePendingIfIdle` рядом с `rescheduleAll(APP_LAUNCH)`); из ресиверов перезапуска — нет (hardening).
   * Резервный `ToneGenerator(STREAM_ALARM, MAX)` звучит на уже выставленной громкости потока.
3. **Нарастание FR-SND-6** — громкость плеера `MediaPlayer.setVolume(g, g)` поверх выставленного индекса потока: `g(t) = 10^((-20 + 20·t/T)/20)`, т. е. от −20 дБ (10 % амплитуды) до 0 дБ, шаг 200 мс на `Handler` главного потока (CPU держит сессионный WakeLock). Нарастание только у основной мелодии; резервные ступени (встроенная по умолчанию, тон) — сразу полная громкость. `RESUME` (после падения), `CATCH_UP` и ранний звук сторожа (`FIRST_SOUND_DEADLINE`, ADR-007 уточнения §2) — без нарастания.
4. **Цепочка резервов FR-SND-5** — три ступени, у каждой свой таймаут подготовки 1 с:
   1. `settings.sound`: `Builtin` → `android.resource://<pkg>/raw/<key>`; `Custom` → `SoundFileStore.fileOf(id)` → `FileInputStream(...).fd` (не путь и не `Uri.fromFile`: `mediaserver` не читает приватный каталог). Нет файла / ошибка открытия → сразу ступень 2, без ожидания таймаута.
   2. `BuiltinSound.DEFAULT` (если ступень 1 не была им же).
   3. `ToneGenerator` + `onFallback()` (сервис включает вибрацию, даже если она выключена в будильнике).
   Ошибка `onError` **после** старта воспроизведения — тоже переход на следующую ступень. События: `SoundStarted(source = builtin|custom|default|tone, volume)`, `SoundFallback(stage, reason)`. Худший случай до первого звука при битой своей мелодии — сторож 2 с + 1 с + 1 с; AC FR-RING-1 (≤ 2 с) относится к штатному пути, R15 — «звук ≤ 4 с».
5. **Интеграция в `RingingService`.** `ring(decision)` → `sound.start(decision.alarm.sound, fadeIn = kind !in {RESUME, CATCH_UP})`. Если звук уже идёт от сторожа (`earlySound`), а решение принесло другую мелодию/громкость, — `sound.start(...)` заново без нарастания (короткая пауза приемлема, путь деградации). Второй будильник очереди со своей громкостью — `acquire` с новым процентом, исходный снимок сохраняется. Источник (`kind`) передаётся из `ScheduleRequest`, `FireDecision` не меняется.
6. **Превью** (FR-EDIT-5, пикер, библиотека): интерфейс в `:core:domain/sound`, реализация в `:core:alarm` — отдельный `MediaPlayer` с теми же `USAGE_ALARM`-атрибутами и `AlarmVolumeController` (`VolumeOwner.PREVIEW`):
   ```kotlin
   interface SoundPreview {
       val playing: StateFlow<SoundRef?>
       fun play(settings: SoundSettings)       // без нарастания и резервов; автостоп через 10 с; повтор — перезапуск
       fun stop()
   }
   ```
   Вызывается только из видимого экрана (hardening разрешает); ViewModel останавливает его в `onCleared`, экран — на `ON_STOP`. Начался звонок (`RingingState.Ringing`) → превью останавливается само и не мешает (`RINGING` становится владельцем громкости, исходный снимок общий). Ошибка превью — сообщение «Не удалось воспроизвести», без резервов.
7. **Пункт здоровья `ALARM_VOLUME` удаляется** (изменение ADR-012 §1): громкость звонка теперь задаёт будильник; оставшийся случай (FGS не стартовал → звонит канал `alarm_fallback` на текущей громкости потока) — редкая деградация, ради неё пункт не держим. Удаляются `HealthItem.ALARM_VOLUME`, `PermissionSnapshot.alarmVolumeMuted`, интент «Исправить» и строки. Порядок остальных пунктов и баннер не меняются.
8. **Телефонный звонок FR-RING-8** — без `READ_PHONE_STATE` (PRD §7): `AudioManager.addOnModeChangedListener` (API 31+) на время сессии. Режимы `MODE_RINGTONE`, `MODE_IN_CALL`, `MODE_IN_COMMUNICATION`, `MODE_CALL_SCREENING`, `MODE_CALL_REDIRECT`, `MODE_COMMUNICATION_REDIRECT` → `setMuted(true)` + вибрация; `MODE_NORMAL` → `setMuted(false)`, мелодия заново с нарастания 15 с. Автостоп продолжает отсчёт. Отказ/потеря audio focus звонок по-прежнему не глушит (ADR-008 §4).
9. **Вибрация** — `AlarmVibrator` без изменений (паттерн M1, `USAGE_ALARM`); тумблер появляется в редакторе (FR-EDIT-5). Выбора паттерна нет.

## Alternatives considered
* **Не трогать поток, громкость только через `MediaPlayer.setVolume`** — нет восстановления и проблем с hardening, но при системной громкости будильника 0–1 звонок тихий: FR-SND-7 не выполняется. Отклонено.
* **`setVolumeIndexForAttributes(USAGE_ALARM)`** — системный API (`@SystemApi`) для обычных приложений недоступен. Отклонено.
* **Нарастание шагами индекса потока** — 7 ступеней на Pixel, громкие скачки; плюс каждое изменение — вызов, подпадающий под hardening. Отклонено.
* **Восстанавливать громкость всегда, даже если пользователь её менял во время звонка** — затирает осознанное действие пользователя. Отклонено (сравнение с `applied`).
* **Хранить снимок громкости в `SharedPreferences` (синхронно)** — проще для пути падения, но второе хранилище настроек рядом с `app_prefs` (ADR-001 п. 1–2); асинхронная запись DataStore успевает задолго до типичного падения. Отклонено.
* **Детект звонка через `TelephonyCallback`** — требует `READ_PHONE_STATE`. Отклонено (PRD §7).
* **Media3 ExoPlayer ради цепочки/нарастания** — ADR-008 уже отклонил; задача решается платформой. Отклонено.

## Consequences
* (+) Звонок слышен при любой системной громкости будильника (кроме фиксированной и DND «Полная тишина»); громкость пользователя возвращается, в т.ч. после падения процесса.
* (+) Битая/удалённая своя мелодия → встроенная, затем тон: звонок не молчит (R15).
* (−) На Android 17 с жёстким hardening (`enable`/`throw`) смена громкости из фонового FGS может быть запрещена — звонок остаётся на текущей громкости (событие `VOLUME_NOT_APPLIED`); это тот же открытый риск R19, что и для самого воспроизведения (ADR-008 уточнения M1).
* (−) Снимок после падения восстанавливается с задержкой (до запуска UI или следующего звонка).
* (−) Пункт `ALARM_VOLUME` исчезает с экрана здоровья — правка ADR-012 и тестов M3.
* Проверка: Robolectric — `ShadowAudioManager` (индекс, min/max, фиксированная громкость, контрольное чтение), ступени `ShadowMediaPlayer` (ошибка/таймаут/отсутствие файла → default → tone), кривая нарастания на виртуальном времени (`ShadowLooper`), владельцы громкости (ring поверх превью, очередь, повторный `acquire` после «падения»); эмулятор — R1, R2, R3, R11 (`media volume --stream 4 --set 1` → звонок на 80 %, после «Отключить» снова 1), R13, R14 (`adb emu gsm call/accept/cancel`), R15, R19 (`dumpsys audio | grep AudioHardening`; при наличии — `set-enable-hardening throw`), R20 (debug `CRASH` → громкость восстановлена после отключения возобновлённого звонка).

## Related
ADR-002, ADR-007, ADR-008, ADR-012 (изменён п. 7), ADR-016; FR-SND-5…7, FR-EDIT-5, FR-RING-1, FR-RING-8, FR-REL-8, FR-REL-9, NFR-5; R1–R3, R11, R13–R15, R19, R20.
