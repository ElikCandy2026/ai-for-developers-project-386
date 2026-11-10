# Render: деплой и MCP

## Публичный стенд

<https://ai-for-developers-project-386-izjw.onrender.com>

Сервис: `ai-for-developers-project-386` (`srv-dbom4gs9v7es73fn429g`), регион
Frankfurt, free plan, runtime Docker. Render собирает образ по `./Dockerfile` из
ветки `main`; пуш в `main` запускает автодеплой. `PORT` передаёт Render,
приложение читает его из `server.port`, поэтому вручную задавать порт не нужно.

Dashboard: <https://dashboard.render.com/web/srv-dbom4gs9v7es73fn429g>

## Данные

Бесплатный план не даёт постоянного диска. SQLite лежит в
`/app/data/call-booking.db` внутри контейнера, поэтому данные **сбрасываются**
при перезапуске, редеплое и пробуждении после простоя. При старте схема
создаётся заново, а `SEED_DEMO_DATA=true` добавляет три демо-типа встреч.
Постоянное хранение требует Render Disk на платном плане или внешней БД.

## MCP-конфигурация

Проектная конфигурация OpenCode — `opencode.json` в корне. Секрет в неё не
записан: заголовок авторизации подставляется из переменной окружения
`RENDER_API_KEY`.

```bash
# Render Dashboard → Account Settings → API Keys
export RENDER_API_KEY="rnd_..."
opencode
```

Если переменную окружения задать нельзя, используйте OAuth: удалите блок
`headers` у сервера `render` в `opencode.json`, запустите OpenCode и выполните
`/mcps` → `render` → Sign in. OAuth-токены OpenCode хранит вне репозитория.

Ключ нельзя коммитить. `.env` и `.env.*` уже в `.gitignore`; в истории
репозитория секретов нет.

## Проверка после деплоя

```bash
BASE=https://ai-for-developers-project-386-izjw.onrender.com

curl -fsS "$BASE/api/owner"
curl -fsS "$BASE/api/meeting-types"
curl -fsS "$BASE/"
```

Сценарий гостя и владельца:

1. `GET /api/meeting-types` → выбрать тип.
2. `GET /api/meeting-types/{id}/availability` → взять слот на будущий день.
3. `POST /api/bookings` с этим слотом → `201`.
4. Повторить тот же `POST` → `409` с `SLOT_TAKEN`.
5. `GET /api/bookings?includePast=false` → запись видна владельцу.
6. `POST /api/meeting-types` → `201`, новый тип появляется в списке.

Первый запрос к бесплатному инстансу после простоя может занять до минуты.
