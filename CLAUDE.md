# FamilyGuard — контекст для Claude

Дипломная работа: «Разработка системы родительского контроля и мониторинга
сетевой активности». Цель текущей работы — **улучшение и «усиление» проекта**:
исправлять баги, закрывать дыры в безопасности, доводить недоделанные функции
до рабочего состояния. Отвечай пользователю по-русски.

## Архитектура
- `backend/` — Django 4.2 + DRF + Channels (WebSocket карты) + Celery (SOS, push).
  Приложения: `users` (семья, инвайты), `location` (GPS, геозоны), `sos`,
  `dns_filter`, `app_control` (статистика, правила, расписания), `notifications`.
- `frontend/` — React 18 + TS + Vite, дашборд родителя (карта Leaflet, SOS, DNS, приложения).
- `android/` — Kotlin + Compose + Hilt, приложение ребёнка: SOS-кнопка,
  `LocationForegroundService`, `DnsVpnService` (локальный DNS-фильтр через VpnService),
  `AppMonitorAccessibilityService` (блокировка приложений), `UsageStatsSyncWorker`.
- Связь: REST `/api/v1/`, JWT (refresh-токены ротируются и попадают в blacklist),
  WebSocket `/ws/family/<id>/map/?token=…`.

## Как проверять
- Backend: `docker compose up -d`, тесты `docker compose exec web python manage.py test apps`
  (без Docker нужны Postgres и Redis; настройки в `backend/.env`).
- Frontend: `cd frontend && npm install && npx tsc --noEmit && npm run build`.
- Android: собирать в Android Studio (`./gradlew assembleDebug`). Адрес сервера —
  `familyguard.devServer` в `android/gradle.properties` или `local.properties`.

## Правила
- Любой эндпоинт, который фильтрует по `user.family`, должен требовать семью
  (`IsFamilyMember`), иначе `family=None` открывает данные «бессемейных» пользователей.
- Родитель может трогать только детей своей семьи — проверяй `child.family_id`.
- Исправление бага на backend сопровождай тестом в `apps/<app>/tests.py`
  (база — `apps/tests_base.py`, Celery и Redis там замоканы).
- Не коммить `venv/`, `node_modules/`, `build/`, `.gradle/`, `__pycache__/`.
- Экономь лимит пользователя: читай только нужные файлы, проверяй точечно,
  не перечитывай весь репозиторий без необходимости.

## Что ещё не сделано (кандидаты на улучшение)
- UI расписаний экрана в дашборде (API `apps/schedule/` уже есть) и их соблюдение на Android.
- Push через Firebase: на Android нет FCM-клиента, `device_token` не отправляется.
- Буфер координат и DNS-логов без интернета (Room подключён, но не используется).
- Тестов для Android и фронтенда нет.
- `backend/.env` лежит в git — настоящие ключи туда не класть.
- Device Admin запрашивает лишние политики (`wipe-data`, `reset-password`).
