# Osh Market — Система управления Ошским рынком

Backend-система (REST API) для управления арендой торговых мест на Ошском рынке:
**личный кабинет арендатора** и **панель администратора**.

---

## Содержание
- [Технологии](#технологии)
- [Архитектура](#архитектура)
- [Быстрый старт](#быстрый-старт)
  - [Запуск через Docker](#запуск-через-docker-рекомендуется)
  - [Локальный запуск](#локальный-запуск)
- [Конфигурация (переменные окружения)](#конфигурация-переменные-окружения)
- [Аутентификация и роли](#аутентификация-и-роли)
- [Поток онлайн-оплаты (модерация)](#поток-онлайн-оплаты-модерация)
- [Объектное хранилище (MinIO)](#объектное-хранилище-minio)
- [Swagger / OpenAPI](#swagger--openapi)
- [Справочник API](#справочник-api)
- [Миграции БД](#миграции-бд)
- [Структура проекта](#структура-проекта)

---

## Технологии
- **Java 17**, **Spring Boot 3.3.5**
- **Spring Security** + **JWT** (jjwt 0.12.3)
- **Spring Data JPA** / Hibernate 6.5
- **PostgreSQL** (нативные enum-типы `user_role`, `notification_type`)
- **Flyway** — миграции схемы
- **MinIO** (S3-совместимое хранилище) — QR-коды способов оплаты и чеки об оплате
- **Apache POI** (Excel), **OpenPDF** (PDF) — экспорт отчётов
- **springdoc-openapi** — Swagger UI
- **Lombok**

---

## Архитектура

```
controller → service → repository → PostgreSQL
                 │
                 └── StorageService → MinIO (QR, чеки)
```

**Ключевые сущности:** `User`, `Tenant`, `Place`, `RentContract`, `Payment`,
`PaymentMethod`, `Notification`, `BankLink`, `PasswordResetToken`.

- Долг хранится в `rent_contracts.debt` (может быть отрицательным — аванс).
- **Soft delete** арендаторов: ИНН удалённого не блокирует регистрацию нового.
- Долг начисляется планировщиком (`@Scheduled`, `DebtCalculationService`).

---

## Быстрый старт

### Запуск через Docker (рекомендуется)
Поднимает PostgreSQL, MinIO и приложение одной командой:

```bash
docker compose up --build
```

После старта:
- API / Swagger — http://localhost:8080/swagger-ui.html
- MinIO API — http://localhost:9000
- MinIO веб-консоль — http://localhost:9001

### Локальный запуск
Требуется запущенный PostgreSQL с созданной БД (имя по умолчанию `osh_market`).
MinIO при старте не обязателен — без него приложение поднимется, но загрузка/выдача
QR и чеков работать не будет (в логах будет предупреждение).

```bash
# Windows PowerShell — задать креды БД и запустить
$env:DB_USERNAME="postgres"; $env:DB_PASSWORD="<ваш_пароль>"
mvn spring-boot:run
```

Сборка исполняемого jar:
```bash
mvn clean package
java -jar target/osh-market-0.0.1-SNAPSHOT.jar
```

---

## Конфигурация (переменные окружения)

Все значения имеют дефолты в `src/main/resources/application.yml`.
Для Docker используются переменные из `.env`.

| Переменная | Назначение | Дефолт |
|---|---|---|
| `SPRING_DATASOURCE_URL` | JDBC URL PostgreSQL | `jdbc:postgresql://localhost:5432/osh_market` |
| `DB_USERNAME` / `DB_PASSWORD` | Креды БД | `postgres` / `postgres` |
| `JWT_SECRET` | Base64-ключ JWT (≥ 32 байт) | встроенный (заменить в проде) |
| `MINIO_ENDPOINT` | Адрес MinIO | `http://localhost:9000` |
| `MINIO_ACCESS_KEY` / `MINIO_SECRET_KEY` | Креды MinIO | `minioadmin` / `minioadmin` |
| `MINIO_BUCKET` | Имя бакета | `osh-market` |
| `MAIL_HOST` / `MAIL_PORT` / `MAIL_USERNAME` / `MAIL_PASSWORD` / `MAIL_FROM` | SMTP | Gmail SMTP, креды пустые |
| `SMS_ENABLED` | Включение SMS (заглушка) | `false` |

Прочие настройки (`application.yml`):
- JWT TTL — 8 часов (`jwt.expiration-ms`)
- Rate-limit входа — 5 попыток / 15 минут блокировки
- Сброс пароля — код/токен живут 15 минут
- Лимит загружаемого файла — 10 MB (`spring.servlet.multipart`)
- `ddl-auto: validate` — схема управляется только Flyway

---

## Аутентификация и роли

Вход по **ИНН + пароль**, в ответ — **JWT** (срок 8 часов). Токен передаётся в заголовке:
```
Authorization: Bearer <token>
```

**Роли:**
- `ADMIN` — доступ к `/api/admin/**`
- `TENANT` — доступ к `/api/tenant/**`
- `/api/auth/**` и Swagger — без авторизации

**Дефолтный администратор** (создаётся при первом старте):
- ИНН: `0000000000`
- Пароль: `Admin@123456` — **смените после первого входа**

Безопасность: bcrypt (saltRounds=12), токены сброса пароля хранятся как SHA-256 хэш,
rate-limit на вход (in-memory).

---

## Поток онлайн-оплаты (модерация)

Официальной интеграции с банками нет — реализована модерируемая «заглушка»:

```
Арендатор                                  Администратор
─────────                                  ─────────────
1. GET  /api/tenant/payment-methods
2. GET  /api/tenant/payment-methods/{id}/qr   ← QR из MinIO
3. (платит сам по QR во внешнем банке)
4. POST /api/tenant/payments
   (methodId + amount + чек)
   → платёж создаётся со статусом PENDING
     долг НЕ списывается
                                           5. GET  /api/admin/payments/pending
                                           6. GET  /api/admin/payments/{id}/receipt  ← чек из MinIO
                                           7a. POST /api/admin/payments/{id}/approve
                                               → APPROVED, долг списывается, уведомление
                                           7b. POST /api/admin/payments/{id}/reject
                                               → REJECTED + причина, уведомление
```

**Статусы платежа (`PaymentStatus`):** `PENDING` → `APPROVED` / `REJECTED`.

- Долг (`rent_contracts.debt`) уменьшается **только при подтверждении** платежа.
- **Ручной ввод платежа админом** (`POST /api/admin/tenants/{id}/payment`) — это
  «наличный» платёж: сразу `APPROVED` и сразу списывает долг.
- Отчёты (Excel/PDF/TXT) и «дата последней оплаты» учитывают **только** `APPROVED`.

---

## Объектное хранилище (MinIO)

QR-коды способов оплаты и чеки об оплате хранятся в MinIO; в БД лежат только ключи объектов.

- Бакет: `osh-market` (создаётся автоматически при старте, если доступен).
- Префиксы ключей: `qr/` — QR-коды, `receipts/` — чеки.
- QR загружает администратор: `POST /api/admin/payment-methods/{id}/qr` (multipart).

При недоступности MinIO приложение стартует (ошибка логируется как WARN),
но эндпоинты загрузки/выдачи файлов вернут ошибку.

---

## Swagger / OpenAPI

- Swagger UI: **http://localhost:8080/swagger-ui.html**
- OpenAPI JSON: **http://localhost:8080/v3/api-docs**

Как авторизоваться в UI:
1. Вызвать `POST /api/auth/login` → скопировать `token`.
2. Нажать **Authorize**, вставить токен (схема `bearerAuth`, формат JWT).
3. Вызывать защищённые эндпоинты.

---

## Справочник API

### `/api/auth/**` — публичные
| Метод | Путь | Описание |
|---|---|---|
| POST | `/api/auth/login` | Вход по ИНН + пароль, выдаёт JWT |
| POST | `/api/auth/forgot-password` | Инициировать сброс пароля (вернёт маскированные контакты) |
| POST | `/api/auth/forgot-password/send-code` | Отправить код выбранным способом (email/SMS) |
| POST | `/api/auth/forgot-password/verify-code` | Проверить код |
| POST | `/api/auth/reset-password` | Установить новый пароль |

### `/api/tenant/**` — роль `TENANT`
| Метод | Путь | Описание |
|---|---|---|
| GET | `/api/tenant/profile` | Профиль арендатора |
| GET | `/api/tenant/payments/next` | Следующий платёж (аренда + долг) |
| GET | `/api/tenant/payments/history` | История платежей (со статусами) |
| GET | `/api/tenant/notifications` | Уведомления + счётчик непрочитанных |
| PATCH | `/api/tenant/notifications/read-all` | Отметить все прочитанными |
| GET | `/api/tenant/bank-links` | Ссылки на банки |
| GET | `/api/tenant/payment-methods` | Способы оплаты (с флагом наличия QR) |
| GET | `/api/tenant/payment-methods/{id}/qr` | Изображение QR-кода |
| POST | `/api/tenant/payments` | Отправить платёж на проверку (multipart: `methodId`, `amount`, `receipt`) |

### `/api/admin/**` — роль `ADMIN`
| Метод | Путь | Описание |
|---|---|---|
| GET | `/api/admin/dashboard` | Сводка (арендаторы, места, долги) |
| GET | `/api/admin/analytics` | Аналитика, топ должников, выручка |
| GET | `/api/admin/tenants` | Список арендаторов |
| POST | `/api/admin/tenants` | Создать арендатора + место |
| GET | `/api/admin/tenants/{id}` | Карточка арендатора |
| PUT | `/api/admin/tenants/{id}` | Обновить арендатора |
| DELETE | `/api/admin/tenants/{id}` | Soft delete арендатора |
| POST | `/api/admin/tenants/{id}/payment` | Ручной (наличный) платёж → сразу `APPROVED` |
| GET | `/api/admin/payments/pending` | Платежи на модерации |
| GET | `/api/admin/payments/{id}/receipt` | Просмотр чека |
| POST | `/api/admin/payments/{id}/approve` | Подтвердить платёж |
| POST | `/api/admin/payments/{id}/reject` | Отклонить платёж (body: `reason`) |
| GET | `/api/admin/payment-methods` | Список способов оплаты |
| POST | `/api/admin/payment-methods` | Создать способ оплаты |
| PUT | `/api/admin/payment-methods/{id}` | Обновить способ оплаты |
| POST | `/api/admin/payment-methods/{id}/qr` | Загрузить QR (multipart: `qr`) |
| GET | `/api/admin/debtors` | Список должников |
| GET | `/api/admin/places/free` | Свободные места |
| GET | `/api/admin/places/occupied` | Занятые места |
| POST | `/api/admin/places/{id}/book` | Забронировать место (новый арендатор) |
| POST | `/api/admin/places/{id}/release` | Освободить место |
| GET | `/api/admin/reports/export?type=excel\|pdf\|txt` | Экспорт отчёта |

---

## Миграции БД

Flyway, каталог `src/main/resources/db/migration`:
- `V1__init_schema.sql` — базовая схема, enum-типы, индексы
- `V2__seed_data.sql` — ссылки на банки, дефолтный администратор
- `V3__payment_moderation.sql` — таблица `payment_methods`, поля модерации в `payments`,
  новые типы уведомлений `PAYMENT_PENDING` / `PAYMENT_REJECTED`

> **Примечание.** Нативные PostgreSQL enum (`user_role`, `notification_type`) маппятся
> в Hibernate через `@JdbcType(PostgreSQLEnumJdbcType.class)` — без этого `INSERT`/`UPDATE`
> падают с ошибкой приведения типа. Статус платежа (`payments.status`) хранится как `VARCHAR`.

---

## Структура проекта

```
src/main/java/com/oshmarket/
├── config/        # SecurityConfig, MinioConfig, OpenApiConfig, ApplicationConfig
├── controller/    # Auth, Tenant, Admin
├── dto/           # auth / tenant / admin DTO + FileContent
├── entity/        # JPA-сущности и enum (UserRole, PaymentStatus, NotificationType)
├── exception/     # ApiException, GlobalExceptionHandler
├── repository/    # Spring Data JPA репозитории
├── security/      # JWT-фильтр, UserDetails
└── service/       # бизнес-логика (Auth, TenantCabinet, Admin, Storage, Notification, ...)

src/main/resources/
├── application.yml
└── db/migration/  # Flyway-миграции

Dockerfile, docker-compose.yml, .env
```
