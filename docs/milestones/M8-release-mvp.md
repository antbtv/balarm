# M8: Качество и релиз (MVP-срез)

| | |
|---|---|
| Статус | In progress |
| Утверждён | 2026-10-11 |
| Завершён | — |
| Требования | NFR-2, NFR-3, NFR-4, NFR-5, §9.2 (R1–R20) |
| Definition of Done | Release candidate: подписанный release-APK с R8, на котором пройдена матрица надёжности на эмуляторе API 37 |

## Цель
Получить устанавливаемый подписанный release-APK (R8 включён) и подтвердить на нём, что будильник срабатывает во всех сценариях надёжности, включая перенесённые из M5 и M7. Распространение — для личного использования (sideload); Google Play — вне MVP.

## Вне скоупа MVP (бэклог)
Play-декларации (battery optimizations, SYSTEM_ALERT_WINDOW), реальные OEM-устройства (R18, NFR-8), API 34 (нет образа), живой TalkBack, общий Kover, `VolumeGuard`/Recents-возврат (R17 Recents), маркер R20 (`ringing_since`), бэкап (ADR-005 — остаётся выключенным), светлая тема, смена иконки (текущая векторная иконка M0 принимается).

## Архитектура этапа
Изменений архитектуры нет. Затрагиваются `build-logic` (release-конфигурация), `app` (proguard-правила, signing), скрипты и документация.

## Решения на утверждение
- [x] Распространение: **личное использование (подпись своим ключом, sideload)** — рекомендую для MVP; Google Play откладываем (открытый вопрос PRD §11.2).
- [x] Ключ подписи создаёт пользователь сам (`keytool`), храним вне репозитория: `keystore.properties` + `*.jks` в `.gitignore`. Я не создаю и не коммичу ключ.

## Задачи
| ✓ | ID | Задача | Оценка | Зависит от | Commit |
|---|---|---|---|---|---|
| [x] | M8-T01 | R8: minify + shrink resources, правила, release-сборка 🔔 | M | — | |
| [x] | M8-T02 | Подпись release из `keystore.properties` | S | T01 | |
| [~] | M8-T03 | Release-проверки: размер, `check-permissions.sh`, холодный старт | S | T02 | |
| [ ] | M8-T-test | Матрица R1–R20 на release-APK, эмулятор API 37 (tester) 🔔 | L | T03 | |
| [ ] | M8-T-review | Ревью reviewer (build-конфигурация, правила R8) | S | T-test | |
| [ ] | M8-T-docs | PRD §8 (M8 ✅), CLAUDE.md, инструкция релиза | S | T-review | |

### M8-T01 — R8 🔔
**Описание:** включить `isMinifyEnabled`/`isShrinkResources` для release в `AndroidApplicationConventionPlugin`; правила для Hilt/Dagger, Room, kotlinx.serialization (миссии, `MathConfig`), `CrashGuard`, ресиверов/сервисов из манифеста, Compose. Debug-код (`DebugAlarmReceiver`) в release отсутствует.
**Приёмка:**
- [ ] `./gradlew :app:assembleRelease` зелёный без warnings «missing class», которые влияют на рантайм;
- [ ] release-APK запускается, список/редактор/звонок/миссия Math работают (smoke на эмуляторе);
- [ ] сериализованная конфигурация миссии читается после R8 (создать будильник с Math → сохранить → перезапуск приложения);
- [ ] mapping-файл сохраняется в `app/build/outputs/mapping` (не коммитится).
**Тесты:** `./gradlew :app:assembleRelease lint detekt ktlintCheck`; smoke на эмуляторе.

### M8-T02 — Подпись
**Приёмка:**
- [ ] `signingConfig` release читает `keystore.properties` (в `.gitignore`); без файла сборка release даёт неподписанный APK, а не падает;
- [ ] `apksigner verify` проходит; в репозитории нет ключей и паролей;
- [ ] шаги создания ключа и сборки — в `docs/RELEASE.md` (коротко).
**Зависит от пользователя:** создание ключа (`keytool`, команду дам).

### M8-T03 — Release-проверки
**Приёмка:**
- [ ] APK ≤ 15 МБ (NFR-3);
- [ ] `./scripts/check-permissions.sh` на release-манифесте: нет `INTERNET`, нет debug-компонентов (NFR-4);
- [ ] холодный старт на эмуляторе release ≤ ~800 мс (3–5 замеров `am start -W`; фиксируем число, вердикт NFR-2 на устройстве — бэклог).

### M8-T-test — Матрица надёжности 🔔
Один прогон на release-APK, эмулятор API 37 (`-memory 2048 -no-window -no-snapshot -no-audio`, перед запуском `./gradlew --stop`, **закрыть Firefox/лишнее**, после — `adb emu kill`). Релевантные для debug-команд сценарии выполнять на debug-сборке, тот же код.
Сценарии: R1–R6, R7 (DST), R8–R17, R19, R20 (один раз; crash-loop — известный риск), плюс:
- M7: пропущенный > 10 мин (смена времени вперёд) → уведомление «Пропущен будильник»; автостоп → уведомление;
- M5: запуск приложения при звонке ведёт на `RingingActivity`;
- release-специфика: перезагрузка до разблокировки (R3) с кастомной мелодией и Math на R8-сборке.
Отчёт — в плане; critical/major блокируют закрытие этапа.

### M8-T-review / M8-T-docs
Один проход ревью; в docs — PRD, CLAUDE.md, `docs/RELEASE.md`, раздел «Перенесено» (остальное из «Вне скоупа»).

## Риски
| Риск | Митигация |
|---|---|
| R8 вырезает нужное (Hilt, serialization, ресиверы) и ломает звонок только в release | smoke + R3/R1 именно на release-APK; правила keep точечно |
| Эмулятор не грузится (M7: 15 мин без `boot_completed`) | закрыть тяжёлые приложения, один процесс Gradle/эмулятор, холодный старт ждать до ~5 мин; при провале — остановить и сообщить, не жечь время |
| Ключ подписи потерян/утёк | вне репозитория, `.gitignore`, бэкап ключа — на пользователе |
| Память машины (15 ГБ) | одна Gradle-сборка, лимиты не повышать |

## Добавлено по ходу
* T01: R8 включён (`proguard-android-optimize` + `app/proguard-rules.pro`, только sourcefile/line numbers; consumer-rules библиотек хватило), `missing_rules.txt` нет. Smoke на эмуляторе и чтение конфига миссии после R8 — в T-test (release-APK подписывается ключом T02).
* T02: подпись из `keystore.properties` (в `.gitignore`), без файла — `app-release-unsigned.apk`; проверено одноразовым ключом (`apksigner verify` ок, ключ не в репозитории). Инструкция — `docs/RELEASE.md`. Постоянный ключ создаёт пользователь.
* T03: APK 3,4 МБ (≤ 15), `check-permissions.sh` ок (11 разрешений, без INTERNET). Холодный старт на эмуляторе — в T-test.

## Уроки

## Перенесено в следующий этап
