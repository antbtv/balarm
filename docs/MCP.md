# MCP-серверы для разработки Balarm

## Предусловия
* ✅ Android SDK в `~/Android/Sdk` (cmdline-tools, platform-tools, platforms 36/37, emulator). В `.mcp.json` для mobile-mcp прописаны `ANDROID_HOME` и `PATH` к `platform-tools` — без этого сервер не видит `adb`. Плюс `MOBILEMCP_LEGACY_ROBOT=1`: встроенный в 1.0.5 `mobilecli` 1.0.13 не видит локальный эмулятор (возвращает пустой список), legacy-режим работает напрямую через `adb`.
* ✅ AVD `balarm_api37` (API 37, google_apis x86_64). Остальные образы из PRD §9.3 (26, 29, 31, 33, 34, 36) — ставить по мере надобности (M1), каждый ≈ 1.5 ГБ.
* ✅ JDK 21 (sdkman, `.sdkmanrc`).
* Запуск эмулятора — только headless с лимитом памяти (см. CLAUDE.md «Ресурсы машины»).
* Node 22 / npx — есть (нужен для MCP ниже).

## Обязательные (уже прописаны в `.mcp.json`)
| Сервер | Зачем | Команда |
|---|---|---|
| **mobile-mcp** (`@mobilenext/mobile-mcp`) | Управление эмулятором/устройством: скриншоты, дерево UI, тапы/свайпы, запуск приложения. Нужен tester-агенту и скиллу `verify-alarm-reliability` для E2E-проверки экрана звонка и миссий. | `npx -y @mobilenext/mobile-mcp@1.0.5` (версия зафиксирована) |
| **context7** (`@upstash/context7-mcp`) | Актуальная документация библиотек (Compose, Room, Hilt, AlarmManager, Media3) — снижает риск устаревших API. Используют architect и все при реализации. | `npx -y @upstash/context7-mcp` |

## Рекомендуемые (добавить по необходимости)
| Сервер | Зачем | Как |
|---|---|---|
| **adb-mcp** ([iksnerd/adb-mcp](https://github.com/iksnerd/adb-mcp)) или **Android-Studio-MCP** ([MauricePutinas](https://github.com/MauricePutinas/Android-Studio-MCP-Claude-Code)) | Всё из mobile-mcp + logcat, Gradle build/test/lint, AVD, `dumpsys`. Альтернатива или дополнение к mobile-mcp. | см. README репозитория |
| **GitHub MCP** | Issues под задачи milestones, PR, CI-статусы. Можно обойтись `gh` CLI. | `claude mcp add --transport http github https://api.githubcopilot.com/mcp/` |
| **Figma MCP** | Если будешь рисовать макеты в Figma — передача токенов/экранов в Compose. | официальный Dev Mode MCP |

## Не нужны
Базы данных, облака, браузерные MCP — приложение полностью офлайн.

## Без MCP (достаточно Bash)
Gradle (`./gradlew`), `adb shell dumpsys alarm`, `adb reboot`, `dumpsys deviceidle` — tester и скиллы вызывают их напрямую.

## Проверка
```bash
claude mcp list          # серверы из .mcp.json появятся после одобрения при старте сессии
adb devices              # должен видеть эмулятор перед использованием mobile-mcp
```

Источники: [adb-mcp](https://github.com/iksnerd/adb-mcp), [Android-Studio-MCP-Claude-Code](https://github.com/MauricePutinas/Android-Studio-MCP-Claude-Code), [replicant-mcp](https://glama.ai/mcp/servers/@thecombatwombat/replicant-mcp/blob/80c6709ca10c3511eabfdbf4365f276c7b02aa3e/README.md).
