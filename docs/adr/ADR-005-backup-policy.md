# ADR-005: Резервное копирование и перенос данных выключены до M8

## Status
Accepted (2026-10-03, реализовано и проверено в M1). Предложено 2026-09-29. Уточняет ADR-001 §6 (не заменяет).

## Context
* В M0 выставлено `android:allowBackup="false"` до решения. Заметка M0 «DE-данные авто-бэкап не захватывает» неточна: по документации Auto Backup включает домены device-protected storage (`device_root`, `device_file`, `device_database`, `device_sharedpref`), если они не исключены правилами.
* Для target 31+ у части производителей `allowBackup="false"` отключает облачный бэкап, но **не** перенос device-to-device; управлять им можно только через `android:dataExtractionRules` (секция `<device-transfer>`).
* Восстановление происходит при установке, до первого запуска: в БД появляются `enabled = true` будильники, но в `AlarmManager` их нет, и никакой broadcast (boot/replaced) не придёт. Инвариант «enabled ⇒ запланирован» нарушается до первого запуска UI или перезагрузки.
* С M4 в БД будут пути к файлам мелодий в `files/sounds/`, с M6 — пользовательские цитаты; частичное восстановление (БД без файлов) даёт битые ссылки (есть резервный звук FR-SND-5, но это деградация).
* (C) бэкап/восстановление JSON (PRD §3.9) — осознанная пользовательская функция, в v1.x.

## Decision
1. До M8 бэкап и D2D-перенос **выключены полностью**: `allowBackup="false"` **плюс** `dataExtractionRules` (API 31+), где в `<cloud-backup>` и `<device-transfer>` исключены все домены, включая `device_*`. `fullBackupContent` не нужен (minSdk 34). Реализовано: `app/src/main/res/xml/data_extraction_rules.xml`, тест `AppWiringTest` проверяет, что исключены все 9 доменов и нет `<include>`.
2. Независимо от бэкапа в M1 вводится страховка «перепланировать при запуске UI»: `MainActivity` при старте вызывает `AlarmEngine.rescheduleAll(APP_LAUNCH)` (идемпотентно). Это же чинит последствия `force-stop` (он легально снимает все alarms — R8).
3. В M8 (решение о Play, PRD §11.2) — пересмотреть: либо включить облако/D2D для `device_database` + `BackupAgent.onRestoreFinished` → перепланирование, либо оставить выключенным и полагаться на (C) экспорт.

## Alternatives considered
* **Включить Auto Backup сейчас с `device_*`-доменами** — удобно пользователю, но восстановленные будильники молча не запланированы, ссылки на файлы могут быть битыми, нужен BackupAgent и тесты. Отложено до M8.
* **Только `allowBackup="false"`** (как в M0) — D2D на части устройств всё равно переносит данные. Отклонено.

## Consequences
* (+) Никаких «призрачных» будильников после восстановления/переноса.
* (−) При смене телефона будильники не переносятся (в v1 — приемлемо; настраивается за минуту).
* Проверка: `apkanalyzer manifest print` содержит `dataExtractionRules`; `adb shell bmgr backupnow com.antbtv.balarm` → «Backup is not allowed»/пусто.

## Related
ADR-001 §6, ADR-004; PRD §3.9 (бэкап — C), §11.2; R8, R9.
