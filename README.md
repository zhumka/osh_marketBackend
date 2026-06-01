# Osh Market Backend

Backend для системы управления арендой торговых мест на Ошском рынке.

Проект закрывает два основных сценария:

- панель администратора: арендаторы, места, платежи, должники, отчеты, способы оплаты;
- кабинет арендатора: профиль, следующий платеж, история платежей, уведомления, оплата с чеком.

## Содержание

- [Стек](#стек)
- [Что нужно установить](#что-нужно-установить)
- [Быстрый запуск через Docker](#быстрый-запуск-через-docker)
- [Как работать с Docker](#как-работать-с-docker)
- [Локальный запуск без контейнера app](#локальный-запуск-без-контейнера-app)
- [Настройка .env](#настройка-env)
- [Доступы после старта](#доступы-после-старта)
- [Ключевая бизнес-логика](#ключевая-бизнес-логика)
- [API для фронта](#api-для-фронта)
- [Миграции БД](#миграции-бд)
- [Структура проекта](#структура-проекта)
- [Частые проблемы](#частые-проблемы)

## Стек

- Java 17
- Spring Boot 3.3.5
- Spring Security + JWT
- Spring Data JPA / Hibernate
- PostgreSQL 16
- Flyway
- MinIO для QR-кодов и чеков
- Resend для email-уведомлений
- Apache POI / OpenPDF для отчетов
- Swagger UI через springdoc-openapi
- Docker / Docker Compose

## Что нужно установить

Для обычного запуска достаточно:

- Docker Desktop;
- Git;
- свободные порты `8081`, `15432`, `19000`, `19001`.

Для запуска backend без контейнера `app` дополнительно нужны:

- Java 17;
- Maven 3.9+.

В репозитории нет `mvnw`, поэтому без установленного Maven команды `mvn ...` работать не будут.

## Быстрый запуск через Docker

Docker Compose поднимает сразу три сервиса:

- `db` - PostgreSQL;
- `minio` - объектное хранилище для QR и чеков;
- `app` - Spring Boot backend.

1. Скопировать пример env-файла:

```powershell
Copy-Item .env.example .env
```

На Linux/macOS:

```bash
cp .env.example .env
```

2. Открыть `.env` и заменить минимум эти значения:

```env
DB_PASSWORD=change_me
JWT_SECRET=replace_with_base64_secret_min_32_bytes
MINIO_ROOT_PASSWORD=change_me
RESEND_API_KEY=re_xxxxxxxxxxxxxxxxxxxxxxxx
```

`RESEND_API_KEY` можно временно оставить пустым, если email-отправка не нужна при локальном тесте.

3. Запустить проект:

```bash
docker compose up --build -d
```

4. Проверить, что контейнеры поднялись:

```bash
docker compose ps
```

5. Смотреть логи backend:

```bash
docker compose logs -f app
```

После успешного старта:

- API: `http://localhost:8081`
- Swagger UI: `http://localhost:8081/swagger-ui.html`
- OpenAPI JSON: `http://localhost:8081/v3/api-docs`
- PostgreSQL с хоста: `localhost:15432`
- MinIO API с хоста: `http://localhost:19000`
- MinIO Console: `http://localhost:19001`

Важно: внутри Docker-сети backend ходит в PostgreSQL по `db:5432`, а в MinIO по `minio:9000`. С хоста используются порты `15432`, `19000`, `19001`.

## Как работать с Docker

Остановить контейнеры без удаления данных:

```bash
docker compose down
```

Запустить уже собранные контейнеры:

```bash
docker compose up -d
```

Пересобрать backend после изменений в коде:

```bash
docker compose up --build -d app
```

Посмотреть логи всех сервисов:

```bash
docker compose logs -f
```

Посмотреть логи только backend:

```bash
docker compose logs -f app
```

Полностью удалить контейнеры и данные PostgreSQL/MinIO:

```bash
docker compose down -v
```

Команда `down -v` удаляет volumes `postgres_data` и `minio_data`. После нее база стартует с нуля и Flyway заново применит миграции.

## Локальный запуск без контейнера app

Этот вариант удобен для разработки в IDE: PostgreSQL и MinIO можно оставить в Docker, а Spring Boot запускать локально.

1. Поднять только PostgreSQL и MinIO:

```bash
docker compose up -d db minio
```

2. Задать переменные окружения для локального backend.

Windows PowerShell:

```powershell
$env:SPRING_DATASOURCE_URL="jdbc:postgresql://localhost:15432/osh_market"
$env:DB_USERNAME="postgres"
$env:DB_PASSWORD="change_me"
$env:JWT_SECRET="replace_with_base64_secret_min_32_bytes"
$env:MINIO_ENDPOINT="http://localhost:19000"
$env:MINIO_ACCESS_KEY="minioadmin"
$env:MINIO_SECRET_KEY="change_me"
$env:MINIO_BUCKET="osh-market"
$env:APP_SEED_ENABLED="true"
mvn spring-boot:run
```

Linux/macOS:

```bash
export SPRING_DATASOURCE_URL="jdbc:postgresql://localhost:15432/osh_market"
export DB_USERNAME="postgres"
export DB_PASSWORD="change_me"
export JWT_SECRET="replace_with_base64_secret_min_32_bytes"
export MINIO_ENDPOINT="http://localhost:19000"
export MINIO_ACCESS_KEY="minioadmin"
export MINIO_SECRET_KEY="change_me"
export MINIO_BUCKET="osh-market"
export APP_SEED_ENABLED="true"
mvn spring-boot:run
```

При локальном запуске без Docker backend слушает порт `8080`, если не переопределить `server.port`.

Собрать jar:

```bash
mvn clean package
```

Запустить jar:

```bash
java -jar target/osh-market-0.0.1-SNAPSHOT.jar
```

## Настройка .env

`.env` используется Docker Compose. Сам файл `.env` не должен попадать в git.

| Переменная | Для чего нужна | Пример |
|---|---|---|
| `DB_NAME` | имя базы PostgreSQL | `osh_market` |
| `DB_USERNAME` | пользователь PostgreSQL | `postgres` |
| `DB_PASSWORD` | пароль PostgreSQL | `change_me` |
| `JWT_SECRET` | Base64 JWT secret, минимум 32 байта | `openssl rand -base64 32` |
| `RESEND_API_KEY` | ключ Resend для email | `re_...` |
| `RESEND_FROM` | отправитель email | `noreply@domain.com` |
| `MINIO_ROOT_USER` | root user MinIO | `minioadmin` |
| `MINIO_ROOT_PASSWORD` | root password MinIO | `change_me` |
| `MINIO_BUCKET` | bucket для QR и чеков | `osh-market` |
| `APP_SEED_ENABLED` | включить тестовые данные при старте | `true` локально, `false` на проде |

Сгенерировать JWT secret:

```bash
openssl rand -base64 32
```

Если OpenSSL нет, можно использовать любой надежный генератор Base64-строки длиной не меньше 32 байт.

## Доступы после старта

Flyway создает дефолтного администратора, а `ApplicationConfig` выставляет ему пароль при первом старте.

Админ:

- ИНН: `00000000000000`
- пароль: `Admin@123456`

После первого входа пароль нужно заменить.

Если `APP_SEED_ENABLED=true`, дополнительно создаются тестовые арендаторы:

- `11111111111111`
- `22222222222222`
- `33333333333333`

Пароль тестовых арендаторов:

```text
Tenant@123456
```

Вход выполняется через:

```http
POST /api/auth/login
```

В защищенные запросы нужно передавать JWT:

```http
Authorization: Bearer <token>
```

## Ключевая бизнес-логика

### Аренда и места

- У арендатора может быть только одна активная аренда.
- После освобождения места активный договор закрывается, место становится свободным.
- Сам арендатор не удаляется, если используется освобождение места. Его можно позже назначить на другое свободное место через `POST /api/admin/tenants/{id}/place`.
- `plannedEndDate` хранит плановую дату окончания аренды. Это отдельное поле, оно не закрывает договор автоматически.
- `endDate` заполняется при фактическом закрытии/освобождении аренды.

### Задолженность, штрафы и оплата

- `debt` - обычная задолженность по аренде.
- `penaltyDebt` - отдельная задолженность по штрафам.
- `totalDebt` - сумма задолженности и штрафов.
- Статус `Оплачено` ставится только если есть подтвержденный платеж и нет долга/штрафов.
- При бронировании новый арендатор получает статус `Не оплачено`, пока платеж не подтвержден.
- При бронировании создается уведомление, что нужно оплатить аренду.
- Ежемесячное начисление аренды выполняется планировщиком `DebtCalculationService`.
- После даты оплаты дается 3 рабочих дня льготного периода.
- Если после льготного периода оплаты нет, один раз за расчетный месяц начисляется штраф 5% от месячной аренды.
- Штраф начисляется в `penaltyDebt`, а не смешивается с обычной задолженностью.
- При подтверждении платежа сумма сначала закрывает `debt`, потом `penaltyDebt`, остаток уходит в аванс.

### Договор

В backend включен дефолтный PDF-шаблон договора:

```http
GET /api/admin/contracts/default
```

Ответ:

- `Content-Type: application/pdf`
- файл: `dogovor_arenda_osh_bazar.pdf`

Фронт может открывать его как Blob в новой вкладке или скачивать.

## API для фронта

### Auth

| Метод | Путь | Описание |
|---|---|---|
| POST | `/api/auth/login` | вход по ИНН и паролю |
| POST | `/api/auth/forgot-password` | начать сброс пароля |
| POST | `/api/auth/forgot-password/send-code` | отправить код |
| POST | `/api/auth/forgot-password/verify-code` | проверить код |
| POST | `/api/auth/reset-password` | установить новый пароль |

### Tenant

| Метод | Путь | Описание |
|---|---|---|
| GET | `/api/tenant/profile` | профиль арендатора, включая `debt`, `penaltyDebt`, `totalDebt`, `plannedEndDate` |
| GET | `/api/tenant/payments/next` | следующий платеж: `monthlyRent`, `debt`, `penaltyDebt`, `totalDue` |
| GET | `/api/tenant/payments/history` | история платежей |
| POST | `/api/tenant/payments` | отправить платеж на проверку, multipart: `methodId`, `amount`, `receipt` |
| GET | `/api/tenant/notifications` | уведомления и счетчик непрочитанных |
| PATCH | `/api/tenant/notifications/read-all` | отметить уведомления прочитанными |
| GET | `/api/tenant/bank-links` | ссылки на банки |
| GET | `/api/tenant/payment-methods` | способы оплаты |
| GET | `/api/tenant/payment-methods/{id}/qr` | QR-код способа оплаты |

### Admin

| Метод | Путь | Описание |
|---|---|---|
| GET | `/api/admin/dashboard` | сводка: арендаторы, места, должники, `totalDebt`, `totalPenaltyDebt` |
| GET | `/api/admin/analytics` | аналитика и топ должников |
| GET | `/api/admin/tenants` | список арендаторов, включая `debt`, `penaltyDebt`, `totalDebt`, `status` |
| POST | `/api/admin/tenants` | создать арендатора и назначить место |
| POST | `/api/admin/tenants/{id}/place` | назначить свободное место существующему арендатору без активной аренды |
| GET | `/api/admin/tenants/{id}` | карточка арендатора |
| PUT | `/api/admin/tenants/{id}` | обновить арендатора, ИНН, цену аренды, `plannedEndDate` |
| DELETE | `/api/admin/tenants/{id}` | soft delete арендатора и закрытие активной аренды |
| POST | `/api/admin/tenants/{id}/payment` | ручной подтвержденный платеж |
| GET | `/api/admin/payments/pending` | платежи на модерации |
| GET | `/api/admin/payments/{id}/receipt` | открыть чек |
| POST | `/api/admin/payments/{id}/approve` | подтвердить платеж |
| POST | `/api/admin/payments/{id}/reject` | отклонить платеж |
| GET | `/api/admin/contracts/default` | скачать дефолтный PDF договора |
| GET | `/api/admin/payment-methods` | список способов оплаты |
| POST | `/api/admin/payment-methods` | создать способ оплаты |
| PUT | `/api/admin/payment-methods/{id}` | обновить способ оплаты |
| POST | `/api/admin/payment-methods/{id}/qr` | загрузить QR |
| GET | `/api/admin/debtors` | список должников с `debt`, `penaltyDebt`, `totalDebt` |
| GET | `/api/admin/places` | все места |
| GET | `/api/admin/places/free` | свободные места |
| GET | `/api/admin/places/occupied` | занятые места |
| POST | `/api/admin/places` | создать место |
| PUT | `/api/admin/places/{id}` | обновить место |
| DELETE | `/api/admin/places/{id}` | удалить свободное место |
| POST | `/api/admin/places/{id}/release` | освободить место |
| GET | `/api/admin/reports/export?type=excel\|pdf\|txt` | экспорт отчета |

## Миграции БД

Миграции лежат в:

```text
src/main/resources/db/migration
```

Flyway применяет их автоматически при старте приложения.

Текущие важные миграции:

- `V1__init_schema.sql` - базовая схема;
- `V2__seed_data.sql` - дефолтные данные;
- `V3__payment_moderation.sql` - модерация платежей;
- `V4__inn_14digits.sql` - переход ИНН на 14 цифр;
- `V5__places_seed.sql` - seed мест;
- `V6__lease_end_and_penalties.sql` - плановая дата конца аренды и тип уведомления штрафов;
- `V7__separate_penalty_debt.sql` - отдельное поле `penalty_debt`.

Важно: `spring.jpa.hibernate.ddl-auto=validate`, поэтому схему меняем только через Flyway.

## Структура проекта

```text
src/main/java/com/oshmarket/
  config/        Security, MinIO, OpenAPI, init config
  controller/    Auth, Tenant, Admin controllers
  dto/           request/response DTO
  entity/        JPA entities and enums
  exception/     ApiException, GlobalExceptionHandler
  repository/    Spring Data repositories
  security/      JWT filter and UserDetails
  service/       business logic

src/main/resources/
  application.yml
  contracts/dogovor_arenda_osh_bazar.pdf
  db/migration/

Dockerfile
docker-compose.yml
.env.example
```

## Частые проблемы

### Порт уже занят

Если занят `8081`, поменяйте левую часть порта в `docker-compose.yml`:

```yaml
ports:
  - "8082:8080"
```

После этого backend будет доступен на `http://localhost:8082`.

То же правило для PostgreSQL и MinIO:

- `"15432:5432"` - порт PostgreSQL на хосте;
- `"19000:9000"` - MinIO API на хосте;
- `"19001:9001"` - MinIO Console на хосте.

### Backend не стартует из-за БД

Проверить контейнеры:

```bash
docker compose ps
```

Проверить логи PostgreSQL:

```bash
docker compose logs -f db
```

Полностью пересоздать локальную БД:

```bash
docker compose down -v
docker compose up --build -d
```

### Не загружаются QR или чеки

Проверить MinIO:

```bash
docker compose logs -f minio
```

Открыть консоль:

```text
http://localhost:19001
```

Логин и пароль берутся из `.env`: `MINIO_ROOT_USER`, `MINIO_ROOT_PASSWORD`.

### Не отправляются email

Проверить:

- `RESEND_API_KEY`;
- `RESEND_FROM`;
- подтвержден ли домен/адрес отправителя в Resend.

Для локальной разработки email можно не настраивать, но реальные письма тогда уходить не будут.
