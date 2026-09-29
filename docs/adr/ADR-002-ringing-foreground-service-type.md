# ADR-002: Тип foreground service для звонка — `systemExempted`

## Status
Accepted (2026-09-28, утверждено вместе с планом M0). Предложено 2026-09-28.

## Context
* FR-REL-5 / NFR-5: звук будильника живёт в `RingingService` (foreground service), а не в Activity. Сервис стартует из фона — из ресивера, вызванного `setAlarmClock()`.
* Android 12+ (target 31+): FGS из фона запрещён, кроме исключений; одно из них — «приложение обрабатывает exact alarm, чтобы выполнить действие пользователя». Подходит нам для любого типа.
* Android 14+ (target 34+): у каждого FGS обязателен `foregroundServiceType` + соответствующее `FOREGROUND_SERVICE_<TYPE>`-разрешение; для Google Play — декларация типов в Play Console.
* Кандидаты:
  * `mediaPlayback` — «продолжить воспроизведение аудио в фоне». Android 15+ (target 35+): **запрещён старт из `BOOT_COMPLETED`**. Требует декларации в Play Console с обоснованием и видео.
  * `systemExempted` — допустим только для узкого круга; в списке явно есть **«приложения с `SCHEDULE_EXACT_ALARM` или `USE_EXACT_ALARM`»**. Не входит в список типов, запрещённых из `BOOT_COMPLETED`; по справке Play Console отдельной декларации не требует. Иначе — `ForegroundServiceTypeNotAllowedException`.
* Android 17 (API 37), «background audio hardening»: воспроизведение, audio focus и изменение громкости из фона без запущенного FGS глушатся **для всех приложений**; для target 37 FGS дополнительно должен иметь while-in-use (WIU) capability, **кроме случая, когда у приложения есть exact-alarm-разрешение и оно работает с потоками `USAGE_ALARM`**. FGS, стартованный из exact-alarm-ресивера, WIU обычно не получает, поэтому нас спасает именно это исключение — независимо от типа FGS.
* Манифест (PRD §7): `USE_EXACT_ALARM` (API 33+, выдаётся при установке, пользователь не отзывает) + `SCHEDULE_EXACT_ALARM` `maxSdkVersion=32`.

## Decision
1. `RingingService` объявляется с `android:foregroundServiceType="systemExempted"` и разрешениями `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_SYSTEM_EXEMPTED`. `mediaPlayback` **не объявляется**.
2. Вызов `startForeground`:
   * API 34+: `ServiceCompat.startForeground(..., FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED)`; право на тип гарантировано `USE_EXACT_ALARM`.
   * API 26–33: без явного типа (у платформы нет `systemExempted`; типы не проверяются).
3. Звук — только `AudioAttributes.USAGE_ALARM` (FR-REL-8): это условие исключения Android 17 для воспроизведения, `requestAudioFocus` и `setStreamVolume(STREAM_ALARM)` (FR-SND-7).
4. Никакого аудио/громкости вне запущенного `RingingService` (кроме превью в видимой Activity редактора — это foreground, ограничения не действуют).
5. Ресиверы boot/time/tz **никогда не стартуют `RingingService` напрямую** — только перепланируют через `setAlarmClock()`; «вот-вот наступивший» будильник планируется на `now + несколько секунд`. Звонок всегда идёт через путь exact alarm.
6. Страховка (детали в M1): если `startForeground` бросил исключение (`ForegroundServiceStartNotAllowedException` / `ForegroundServiceTypeNotAllowedException`), ресивер публикует high-priority уведомление в канале будильника со звуком `USAGE_ALARM` и full-screen intent — третья линия из PRD §10.

## Alternatives considered
* **`mediaPlayback`** — семантически «плеер медиа»; Android 15+ блокирует старт из `BOOT_COMPLETED` (ломает любой будущий путь «звонить сразу после загрузки»); обязательная декларация в Play Console с видео и риск отказа ревью. На Android 17 никаких преимуществ не даёт: WIU всё равно не будет, спасает то же исключение exact alarm + `USAGE_ALARM`. Отклонено.
* **Оба типа (`systemExempted|mediaPlayback`) с выбором в рантайме** — фолбэк бессмыслен: без exact-alarm-разрешения `setAlarmClock` не работает, будильник и так не прозвенит. Лишняя Play-декларация. Отклонено.
* **`specialUse`** — требует обоснования в Play Console и ручного ревью; для будильника есть прямой легальный тип. Отклонено.
* **`shortService`** — лимит ~3 мин, будильник звонит до 30 мин (FR-RING-6). Отклонено.

## Consequences
* (+) Легальный, документированный путь для приложений-будильников; работает и из `LOCKED_BOOT_COMPLETED`-сценариев; без Play-декларации типа FGS.
* (+) Соответствует Android 17 background audio hardening при targetSdk 37.
* (−) Зависимость от `USE_EXACT_ALARM`. Если для Google Play (открытый вопрос PRD §11.2) придётся перейти на отзываемое `SCHEDULE_EXACT_ALARM`, отзыв одновременно отменяет все будильники (обрабатывается ресивером смены разрешения, FR-REL-3) и лишает права на `systemExempted` — новый режим отказа не появляется, но экран здоровья (FR-REL-7) обязан это показывать.
* (−) На API 29–33 в манифесте флаг `systemExempted` неизвестен платформе — **проверить в M1** на эмуляторах API 29/31/33 (сценарий R1), что двухаргументный `startForeground` не падает.
* Проверка (M1): R1, R2, R3, R8, R10, R11 на API 26/29/31/33/34/36/37; на API 37 — `adb shell cmd audio set-enable-hardening throw` + `adb shell dumpsys audio | grep AudioHardening` (не должно быть записей для пакета); `adb shell dumpsys activity services <pkg>` показывает `types=0x00000400`.
* PRD §7 и FR-REL-5 обновить: `FOREGROUND_SERVICE_SYSTEM_EXEMPTED`, без `FOREGROUND_SERVICE_MEDIA_PLAYBACK`.

## Related
FR-REL-1, FR-REL-5, FR-REL-8, FR-SND-7, FR-RING-6, NFR-5, PRD §6.5, §7, §10; ADR-001; сценарии R1–R3, R8, R10, R11.
Источники: developer.android.com — «Foreground service types» (systemExempted, mediaPlayback), «Restrictions on starting FGS from the background», Android 15 «BOOT_COMPLETED FGS restrictions», Android 17 «Background audio hardening».
