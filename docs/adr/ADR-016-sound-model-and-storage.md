# ADR-016: Мелодии: модель, встроенный каталог, импорт своих в device-protected storage, схема Room v3

## Status
Proposed (2026-10-09, план M4). Дополняет ADR-001 (DE-хранилище), ADR-004 (эволюция схемы), ADR-008 (воспроизведение); их решения не меняются. Связанное решение о громкости и цепочке звука — ADR-017.

## Context
* FR-SND-1 (8–12 встроенных, CC0/собственные, `res/raw`), FR-SND-2 (SAF `ACTION_OPEN_DOCUMENT`, копия в DE, лимит 20 МБ; аудио-«цитаты» — те же мелодии), FR-SND-3 (прослушать, переименовать, удалить; используемая → будильники на встроенную по умолчанию с предупреждением), FR-SND-4 — бэклог (системных рингтонов нет), FR-EDIT-5 (мелодия, громкость, нарастание, вибрация в редакторе). DoD M4: **своя мелодия играет после перезагрузки до разблокировки** (R3).
* Звонок не должен ждать I/O дольше 2 с (FR-RING-1 AC, ADR-007 §2); после `LOCKED_BOOT` доступны только DE-БД и DE-файлы (ADR-001). Решение движка (`FireDecision.Ring`) уже несёт `Alarm` целиком — настройки звука приходят «бесплатно», если лежат в строке `alarm`.
* `mediaserver` не имеет доступа к приватному каталогу приложения: `setDataSource(path)`/`Uri.fromFile` для `files/…` не работает — нужен `FileDescriptor`.
* M1 положил `res/raw/alarm_default.wav`; его URI **по имени** хранится в канале `alarm_fallback` (ADR-008, уточнения M1) — переименовывать/удалять ресурс нельзя.
* Машина сборки без `ffmpeg/sox/oggenc`, но с `python3`, `numpy` и системными `libsndfile.so.1` (1.0.37), `libvorbisenc`, `libopus`; сеть есть, но лицензии сторонних звуков — риск PRD §10. NFR-3: APK ≤ 15 МБ, мелодии — OGG.
* Feature flags (FR-FLAG): `feature.customSounds` уже в реестре; выбор встроенной мелодии/громкости/нарастания в редакторе — новая пользовательская фича без флага. Звук звонка и хранение — неотключаемое ядро (FR-FLAG-4).
* Навигация: feature-модули не знают друг друга; результат другого экрана — Result API Nav3 в `:app` (ADR-009 §3).

## Decision
1. **Модель (`:core:model`, чистый Kotlin).**
   ```kotlin
   enum class BuiltinSound(val key: String) {          // key = имя файла в res/raw = значение в БД; не меняется никогда
       DEFAULT("alarm_default"),                       // M1, им же звонит канал alarm_fallback
       SUNRISE("snd_sunrise"), MARIMBA("snd_marimba"), BELLS("snd_bells"), PIANO("snd_piano"),
       CHIPTUNE("snd_chiptune"), DIGITAL("snd_digital"), ASCEND("snd_ascend"), CHIMES("snd_chimes"), SIREN("snd_siren");
       companion object { fun fromKey(key: String): BuiltinSound? }
   }

   @JvmInline value class CustomSoundId(val value: Long) { init { require(value > 0) } }

   sealed interface SoundRef {
       data class Builtin(val sound: BuiltinSound) : SoundRef
       data class Custom(val id: CustomSoundId) : SoundRef
       fun encode(): String                              // "builtin:<key>" | "custom:<id>"
       companion object {
           val DEFAULT: SoundRef = Builtin(BuiltinSound.DEFAULT)
           fun decode(raw: String): SoundRef?            // неизвестное/битое → null, вызывающий берёт DEFAULT и логирует
       }
   }

   data class SoundSettings(
       val sound: SoundRef = SoundRef.DEFAULT,
       val volumePercent: Int = DEFAULT_VOLUME,          // MIN_VOLUME..100, шаг 10 (ADR-017 §2)
       val fadeIn: Duration = Duration.ZERO,             // одно из FADE_IN_OPTIONS
   ) { companion object { const val MIN_VOLUME = 10; const val DEFAULT_VOLUME = 80
       val FADE_IN_OPTIONS = listOf(0L, 15, 30, 60).map(Duration::ofSeconds); val DEFAULT = SoundSettings() } }

   data class CustomSound(val id: CustomSoundId, val title: String, val duration: Duration,
                          val sizeBytes: Long, val addedAt: Instant)   // title: 1..40 code points
   ```
   `Alarm` получает `val sound: SoundSettings = SoundSettings.DEFAULT` (аддитивно; `vibrate` остаётся полем `Alarm`). Тип `SYSTEM` из черновика PRD §6.4 не вводится (FR-SND-4 — бэклог).
2. **Встроенные мелодии** — 10 файлов (`DEFAULT` + 9 новых) в `:core:alarm/src/main/res/raw` (там же, где плеер; APK доступен до разблокировки). `alarm_default.wav` не трогаем. Новые — **OGG Vorbis**, моно, 22,05/32 кГц, бесшовный цикл 15–40 с, суммарно ≤ 2 МБ. Источник — **собственный синтез** (CC0, без лицензионного риска): `tools/sound/generate_builtin_sounds.py` (stdlib + `numpy`; аддитивный/FM-синтез: маримба, колокольчики, «пианино», 8-бит, нарастающее арпеджио, сирена) → кодирование через `ctypes` в системный `libsndfile` (`SF_FORMAT_OGG | SF_FORMAT_VORBIS`), без установки пакетов. Нет `libsndfile` с Vorbis → скрипт пишет WAV 16 кГц моно (≈ 32 КБ/с; бюджет ≤ 3 МБ на все) и печатает предупреждение. Скрипт детерминирован (фиксированный seed), результат коммитится; каждая мелодия — строка в `docs/LICENSES.md`. Отображаемые названия (RU/EN) — строковые ресурсы `:core:format` (`soundTitle(...)`), т. к. их показывают редактор, пикер и библиотека, которые не зависят друг от друга. Тест (Robolectric): для каждого `BuiltinSound` существует `raw/<key>` и строка названия.
3. **Свои мелодии — файлы `DE files/sounds/<id>`** (без расширения, имя = `CustomSoundId`). Путь **вычисляется из id**, в БД не хранится: на звонке нужен только `SoundRef` из строки `alarm`, ни одного дополнительного запроса. Абстракция пути — в `:core:domain`:
   ```kotlin
   interface SoundFileStore { fun fileOf(id: CustomSoundId): java.io.File }  // impl в :core:data от @DeviceProtected Context
   ```
   Плеер открывает файл как `FileInputStream(file).fd` → `MediaPlayer.setDataSource(FileDescriptor)` (ADR-017 §4).
4. **Импорт (`:core:data`, только из UI, устройство разблокировано).**
   ```kotlin
   @JvmInline value class SoundSource(val uri: String)            // content:// из SAF, домен не знает Uri
   sealed interface ImportResult {
       data class Imported(val sound: CustomSound) : ImportResult
       data class TooLarge(val limitBytes: Long) : ImportResult
       data object Unsupported : ImportResult                      // нет аудиодорожки/декодера
       data object NoSpace : ImportResult
       data class Failed(val reason: String) : ImportResult
   }
   interface SoundRepository {                                     // :core:domain/sound
       fun observeCustomSounds(): Flow<List<CustomSound>>          // по addedAt desc
       suspend fun getCustom(id: CustomSoundId): CustomSound?
       suspend fun import(source: SoundSource): ImportResult
       suspend fun rename(id: CustomSoundId, title: String): Boolean
       suspend fun usageCount(id: CustomSoundId): Int              // для предупреждения FR-SND-3
       suspend fun delete(id: CustomSoundId): Int                  // число будильников, переключённых на DEFAULT
       suspend fun cleanUp()                                       // сироты: tmp-файлы, файлы без строк, строки без файлов
       companion object { const val IMPORT_LIMIT_BYTES = 20L * 1024 * 1024 }
   }
   ```
   Алгоритм `import`: `OpenableColumns` (имя, размер; размер > лимита → `TooLarge` без копирования) → поток в `sounds/.tmp-<uuid>` с подсчётом байт (обрыв на лимите → `TooLarge`; `IOException` с `ENOSPC` → `NoSpace`) → проба `AudioProbe` (`MediaExtractor` по FD: есть трек `audio/*`, `MediaCodecList(REGULAR_CODECS).findDecoderForFormat` ≠ null, длительность > 0; иначе `Unsupported`) → транзакция: вставка строки `custom_sound` → `rename(tmp, sounds/<id>)`; неудача переименования → откат строки и удаление tmp. Отмена корутины → tmp удаляется. Название по умолчанию — `DISPLAY_NAME` без расширения, обрезанное до 40 code points (пусто → «Мелодия N»). SAF-пикер: `ActivityResultContracts.OpenDocument` с типами `audio/*`, `application/ogg`; persistable-разрешение не берём (копируем сразу). `cleanUp()` вызывается при `APP_LAUNCH` (не на пути звонка).
5. **Удаление (FR-SND-3)** — одна транзакция Room: `UPDATE alarm SET sound = 'builtin:alarm_default' WHERE sound = 'custom:<id>'` → `DELETE custom_sound` → после коммита удалить файл. Расписание не меняется, поэтому это допустимое исключение из «только `AlarmEngine` пишет `alarm`» (ADR-006 §8): движок не трогает колонки звука. Будильник, звонящий этой мелодией, доигрывает (открытый FD переживает unlink). Устаревший черновик редактора со ссылкой на удалённую мелодию сохраняется как есть — на звонке сработает резерв FR-SND-5; при открытии редактора отсутствующая `Custom` показывается как «Мелодия удалена» и предлагает выбор.
6. **Room схема v3** (AutoMigration 2→3, тест `MigrationTestHelper`, ADR-004 §5):
   ```
   alarm        + sound          TEXT    NOT NULL DEFAULT 'builtin:alarm_default'
                + volume_percent INTEGER NOT NULL DEFAULT 80
                + fade_in_sec    INTEGER NOT NULL DEFAULT 0
   custom_sound ( id INTEGER PK AUTOINCREMENT, title TEXT NOT NULL, duration_ms INTEGER NOT NULL,
                  size_bytes INTEGER NOT NULL, added_at INTEGER NOT NULL )
   ```
   Внешнего ключа `alarm.sound → custom_sound` нет (строка — объединение builtin/custom); целостность держит п. 5, а при рассинхроне — резерв FR-SND-5. Маппер: неизвестный `sound`, `volume_percent` вне диапазона, `fade_in_sec` не из списка → значения по умолчанию + событие лога (будильник не должен падать при чтении). Флаг «звонит» для R20 (план M5) переезжает в схему **v4**.
7. **Feature flags.** Новый `feature.alarmSound` (false до T-docs M4): секция «Звук» редактора (мелодия, громкость, нарастание, вибрация) и пикер. Выключен → секция скрыта, сохранённые значения звука используются как есть. `feature.customSounds` (есть): «Добавить мелодию», библиотека «Мои мелодии» (строка в «Настройках»), свои мелодии в пикере. Выключен → точки входа скрыты; будильник, уже ссылающийся на свою мелодию, **играет её** (воспроизведение — ядро, FR-FLAG-4), файлы и строки не удаляются.
8. **UI-модули.** Новый `:feature:sounds` (PRD §6.2): `SoundPickerRoute(selected: SoundRef, onPicked: (SoundRef) -> Unit, onClose: () -> Unit)` и `SoundLibraryRoute(onClose: () -> Unit)`. Пикер возвращает выбор в редактор через Nav3 Result API в `:app` (ADR-009 §3); `:feature:alarmedit` получает выбор параметром Route и превращает в событие `SoundSelected(ref)`. Ключи `SoundPickerKey(selected: String /* SoundRef.encode() */)`, `SoundLibraryKey` — в `:app`.

## Alternatives considered
* **Хранить путь/URI файла в БД** — лишняя колонка и риск рассинхрона пути с DE-каталогом; путь однозначно выводится из id. Отклонено.
* **Таблица `sound` со строками и для встроенных** (черновик PRD §6.4) — сидинг, миграции при добавлении мелодии в обновлении, лишний запрос на звонке. Отклонено: встроенные — enum + ресурсы.
* **Две колонки `builtin_key` + `custom_sound_id` с FK `ON DELETE SET NULL`** — FK даёт целостность, но `NULL` = «по умолчанию» размазывает правило по мапперу и запросам, а переключение «с предупреждением» всё равно делается явно. Отклонено ради одной строки `SoundRef.encode()`.
* **Хранить ссылку на исходный `content://` (persistable permission)** — не работает до разблокировки и после удаления исходника (FR-SND-2). Отклонено.
* **Сторонние CC0 (freesound.org с фильтром CC0, OpenGameArt CC0)** — качественнее, но проверка лицензии каждого файла, риск ошибочной маркировки; Pixabay/«royalty free» — не CC0. Допустимо как дополнение позже, с записью источника в `LICENSES.md`. Для v0.1 отклонено.
* **MIDI в `res/raw`** (килобайты, синтезатор Sonivox) — звучание «телефона 2005 года», зависимость от OEM-синтезатора. Отклонено.
* **WAV для всех новых мелодий** — 10 × 30 с × 32 КБ/с ≈ 10 МБ, ломает NFR-3. Только как запасной режим скрипта.
* **Пикер внутри `:feature:alarmedit`** (без результата между экранами) — проще навигация, но пикер и библиотека делят список, превью и импорт; модуль `:feature:sounds` уже заложен в PRD §6.2. Отклонено.

## Consequences
* (+) На звонке — ноль дополнительных обращений к хранилищу: `SoundRef` в строке `alarm`, путь своей мелодии вычисляется, файл в DE → DoD M4 (R3) выполняется по построению.
* (+) Лицензии встроенных мелодий закрыты собственным синтезом; повторяемо офлайн.
* (+) Удаление и битые файлы не ломают звонок: явное переключение + резерв FR-SND-5 (R15).
* (−) Синтезированные мелодии проще «студийных»; при желании позже заменяются CC0-файлами с тем же `key`.
* (−) Свои мелодии в DE не защищены экраном блокировки (принято в ADR-001); суммарный объём не ограничен — только 20 МБ на файл (открытый вопрос).
* (−) Ссылочная целостность `alarm.sound` держится кодом, а не FK.
* Проверка: unit — `SoundRef.encode/decode`, валидация `SoundSettings`/`CustomSound`, маппер с мусорными значениями; Robolectric — миграция 2→3, импорт (лимит, обрыв, отмена, сироты), удаление с переключением, наличие `raw/<key>` для каждого `BuiltinSound`, файл в `/data/user_de/...`; эмулятор — R3 со своей мелодией, R15 (удалить файл через `adb shell run-as`), R9 (мелодии на месте после `install -r`).

## Related
ADR-001, ADR-004, ADR-005, ADR-006 §8, ADR-008, ADR-009, ADR-017; FR-SND-1…5, FR-EDIT-5, FR-FLAG-2…4, NFR-3, NFR-7; PRD §6.2–6.4, §10; R3, R9, R15.
