# FamilyGuard — Система родительского контроля

Дипломная работа: «Разработка системы родительского контроля и мониторинга сетевой активности»

## Архитектура

```
FamilyGuard/
├── backend/          # Django + DRF + Channels + Celery
├── frontend/         # React + Leaflet.js (дашборд родителя)
├── android/          # Kotlin + Jetpack Compose (приложение ребёнка)
└── docker-compose.yml
```

## Быстрый старт (Backend + Frontend)

### Требования
- Docker 24+
- Docker Compose 2.x
- Node.js 20+ (для frontend)

### 1. Backend

```bash
# Клонировать и перейти в директорию
cd "Family Guard"

# Запустить все сервисы
docker compose up -d

# Создать суперпользователя (первый запуск)
docker compose exec web python manage.py createsuperuser
```

**Сервисы будут доступны:**
| Сервис | URL |
|--------|-----|
| API | http://localhost:8000/api/v1/ |
| Swagger UI | http://localhost:8000/api/docs/ |
| ReDoc | http://localhost:8000/api/redoc/ |
| Admin | http://localhost:8000/admin/ |

**Тестовые данные (созданы при запуске тестов):**
- Родитель: `parent@familyguard.uz` / `TestPass123!`
- Ребёнок: `child@familyguard.uz` / `TestPass123!`

### 2. Frontend

```bash
cd frontend
npm install
npm run dev
# → http://localhost:3000
```

### 3. Android

Открыть `android/` в Android Studio, указать в `app/build.gradle.kts`:
```kotlin
buildConfigField("String", "BASE_URL", "\"http://YOUR_IP:8000/api/v1/\"")
```

## API эндпоинты

### Аутентификация
| Метод | URL | Описание |
|-------|-----|----------|
| POST | `/api/v1/auth/register/` | Регистрация |
| POST | `/api/v1/auth/login/` | Вход (JWT) |
| POST | `/api/v1/auth/logout/` | Выход (blacklist токена) |
| POST | `/api/v1/auth/refresh/` | Обновить access token |
| GET/PATCH | `/api/v1/auth/profile/` | Профиль |

### Семья
| Метод | URL | Описание |
|-------|-----|----------|
| POST | `/api/v1/family/create/` | Создать семью |
| POST | `/api/v1/family/invite/generate/` | Получить 6-значный инвайт-код |
| POST | `/api/v1/family/invite/join/` | Вступить по коду |
| GET | `/api/v1/family/members/` | Список членов |

### GPS / Карта
| Метод | URL | Описание |
|-------|-----|----------|
| POST | `/api/v1/location/` | Отправить координаты (от ребёнка) |
| GET | `/api/v1/location/current/` | Текущие позиции всех членов |
| GET | `/api/v1/location/history/{id}/` | История перемещений |
| WS | `ws://host/ws/family/{id}/map/` | Real-time карта |

### SOS
| Метод | URL | Описание |
|-------|-----|----------|
| POST | `/api/v1/sos/trigger/` | Нажатие SOS-кнопки |
| GET | `/api/v1/sos/history/` | История сигналов |
| POST | `/api/v1/sos/{id}/resolve/` | Отметить как решённый |

### DNS-фильтрация
| Метод | URL | Описание |
|-------|-----|----------|
| GET | `/api/v1/dns/blocklist/` | Получить blocklist (для Android VPN) |
| POST | `/api/v1/dns/blocked/` | Заблокировать домен |
| DELETE | `/api/v1/dns/blocked/{id}/` | Разблокировать |
| POST | `/api/v1/dns/queries/batch/` | Загрузить логи DNS |
| GET | `/api/v1/dns/queries/` | Журнал запросов |
| GET | `/api/v1/dns/categories/` | Категории блокировки |
| PATCH | `/api/v1/dns/categories/{id}/toggle/` | Вкл/выкл категорию |

### Приложения
| Метод | URL | Описание |
|-------|-----|----------|
| POST | `/api/v1/apps/usage/batch/` | Загрузить статистику |
| GET | `/api/v1/apps/usage/{child_id}/` | Просмотр статистики |
| GET/POST | `/api/v1/apps/rules/` | Правила блокировки/лимита |
| GET/POST | `/api/v1/apps/schedule/` | Расписание экрана |
| GET | `/api/v1/apps/permission-alerts/` | Изменения разрешений |

## Тестирование безопасности

### OWASP ZAP
```bash
docker run -t owasp/zap2docker-stable zap-baseline.py \
  -t http://localhost:8000/api/v1/ \
  -r zap_report.html
```

### JWT тестирование
```bash
pip install jwt_tool
python3 jwt_tool.py <TOKEN> -t http://localhost:8000/api/v1/auth/profile/ \
  -rh "Authorization: Bearer TOKEN" -M at
```

### Rate limiting
```bash
# Должен получить 429 после 5 попыток за минуту
for i in {1..10}; do
  curl -s -o /dev/null -w "%{http_code}\n" \
    -X POST http://localhost:8000/api/v1/auth/login/ \
    -d '{"email":"x","password":"x"}' -H "Content-Type: application/json"
done
```

### SQLMap (проверка отсутствия SQL-инъекций)
```bash
sqlmap -u "http://localhost:8000/api/v1/dns/queries/?search=test" \
  -H "Authorization: Bearer TOKEN" --level=3 --risk=2
```

## Технологический стек

| Компонент | Технология |
|-----------|-----------|
| Backend | Django 4.2, DRF 3.15, Django Channels 4.1 |
| Database | PostgreSQL 15 |
| Cache/Broker | Redis 7 |
| Task Queue | Celery 5.4 + django-celery-beat |
| WebSocket | Daphne 4.1 (ASGI) |
| Auth | JWT (djangorestframework-simplejwt) |
| Push | Firebase Admin SDK |
| SMS | Twilio |
| Docs | drf-spectacular (Swagger/OpenAPI 3.0) |
| Android | Kotlin, Jetpack Compose, MVVM, Hilt |
| Frontend | React 18, TypeScript, Tailwind CSS, Leaflet.js |
| Deploy | Docker Compose |
