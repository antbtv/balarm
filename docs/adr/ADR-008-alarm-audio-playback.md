# ADR-008: Воспроизведение звука будильника — платформенный MediaPlayer, резерв — ToneGenerator

## Status
Proposed (2026-09-29, план M1). Действует и для M4 (мелодии, fade-in, громкость).

## Context
* FR-REL-8: `USAGE_ALARM` + `CONTENT_TYPE_SONIFICATION`, audio focus `AUDIOFOCUS_GAIN_TRANSIENT`. FR-SND-5: не открылось за 1 с → встроенная мелодия → `ToneGenerator` + вибрация. FR-SND-6 (fade-in, M4), FR-SND-7 (громкость `STREAM_ALARM`, M4). FR-RING-1 AC: ≤ 2 с от срабатывания до звука.
* Источники: `res/raw` (APK) и файлы в DE `files/sounds/` (M4). Стриминга, плейлистов, медиа-сессий нет.
* NFR-3: APK ≤ 15 МБ. Media3 ExoPlayer 1.11.1 добавляет ~1–2 МБ и собственные потоки/лупер.
* Android 17 background audio hardening: разрешено из FGS при `USAGE_ALARM` и exact-alarm-разрешении (ADR-002) — одинаково для любого плеера.

## Decision
1. **`android.media.MediaPlayer`** за интерфейсом `AlarmSoundPlayer` (`:core:alarm`): `setAudioAttributes(USAGE_ALARM, CONTENT_TYPE_SONIFICATION)`, `isLooping = true`, `setWakeMode(PARTIAL_WAKE_LOCK)`, `prepareAsync` с таймаутом 1 с → фолбэк.
2. **Источник M1** — один встроенный тон `res/raw/alarm_default.ogg` в `:core:alarm` (сгенерирован самостоятельно — без лицензионных вопросов, ≤ 100 КБ). Системный рингтон не используется: `content://` медиапровайдера ненадёжен в Direct Boot.
3. **Резерв** — `ToneGenerator(STREAM_ALARM, 100)` циклически (`TONE_CDMA_ALERT_CALL_GUARD` или аналог) + вибрация; событие `SOUND_FALLBACK`.
4. **Audio focus** — `AudioManagerCompat.requestAudioFocus(GAIN_TRANSIENT)`; отказ фокуса **не** отменяет звонок (будильник важнее). Реакция на потерю фокуса при телефонном звонке (FR-RING-8) — M4.
5. **Вибрация** — `AlarmVibrator`: API 31+ `VibratorManager.defaultVibrator`, API 33+ `vibrate(effect, VibrationAttributes.USAGE_ALARM)`, ниже — `vibrate(effect, AudioAttributes USAGE_ALARM)`; повторяющийся паттерн.
6. Громкость потока в M1 не трогаем (FR-SND-7 — M4); риск «громкость будильника = 0» фиксируется в экране здоровья (M3) и M4.

## Alternatives considered
* **Media3 ExoPlayer** — лучше диагностика ошибок и форматов, gapless-loop; но размер, собственный жизненный цикл/потоки и зависимость ради одного локального файла. Отклонено (принцип «платформа решает < 50 строк»). Пересмотр — если в M4 MediaPlayer не справится с пользовательскими форматами.
* **`Ringtone`/`RingtoneManager`** — проще, но нет контроля фолбэка/таймаута, лупинг только API 28+, системный URI в Direct Boot. Отклонено.
* **`SoundPool`** — только короткие клипы. Отклонено.

## Consequences
* (+) Ноль зависимостей, предсказуемое поведение на API 26–37.
* (−) Callback-API MediaPlayer → аккуратная обёртка с таймаутом и `release()` во всех путях.
* Проверка: Robolectric (`ShadowMediaPlayer`) — атрибуты, фолбэк по таймауту/ошибке; эмулятор — R1, R11 (DND/беззвучный), R19 (`dumpsys audio | grep AudioHardening` пусто); M4 — R15.

## Related
ADR-002, ADR-007; FR-REL-8, FR-REL-9, FR-SND-5…7, FR-RING-1, FR-RING-8, NFR-3; R1, R11, R15, R19.
