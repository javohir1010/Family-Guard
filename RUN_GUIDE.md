# FamilyGuard — Запуск проекта

## Быстрый старт без Android

Для запуска backend + frontend (дашборд родителя) Android SDK не требуется.

### 1. Запустить backend (Docker Compose)

```bash
docker compose up -d
```

Сервисы:
| Сервис | URL |
|--------|-----|
| API | http://localhost:8000/api/v1/ |
| Swagger UI | http://localhost:8000/api/docs/ |
| Admin | http://localhost:8000/admin/ |

Проверить backend автотестами:
```bash
docker compose exec web python manage.py test apps
```

### 2. Запустить frontend (дашборд)

```bash
cd frontend
npm install
npm run dev
# → http://localhost:3000
```

## Компиляция Android-приложения

### Вариант A: Android Studio (рекомендуется)

1. Убедиться, что установлен **Android Studio Giraffe+** и Android SDK API 35
2. Открыть папку `android/` как проект
3. Дождаться синхронизации Gradle
4. Собрать APK: **Build → Build Bundle(s)/APK(s) → Build APK(s)**

### Вариант B: Командная строка

```bash
cd android
./gradlew assembleDebug
# APK: android/app/build/outputs/apk/debug/app-debug.apk
./gradlew installDebug   # на подключённое устройство
```

### Настройки сети

Адрес backend для debug-сборки — свойство `familyguard.devServer` в `android/gradle.properties`
(можно переопределить в `android/local.properties`, он не коммитится):

- **Для эмулятора**: `familyguard.devServer=10.0.2.2:8000`
- **Для физического устройства**: IP вашего компьютера в той же сети, например `familyguard.devServer=192.168.1.100:8000`

Debug-сборка разрешает HTTP на любой адрес; release — только HTTPS.

### Первый запуск на телефоне ребёнка

1. Войти или зарегистрироваться, ввести инвайт-код из дашборда («Семья» → «Пригласить ребёнка»).
2. Разрешить доступ к геолокации и уведомлениям.
3. Нажать «⚙️ Настройте защиту» и включить все пункты: статистика приложений,
   спец. возможности (блокировка приложений), DNS-фильтр (подтвердить VPN).

## Требования

- Docker 24+ (backend)
- Node.js 20+ (frontend)
- JDK 21+ (Android)
- Android Studio (сборка APK)

## Пользователи для теста

Создайте суперпользователя:
```bash
docker compose exec web python manage.py createsuperuser
```
