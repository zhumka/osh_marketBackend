# Деплой и CI/CD — Osh Market Backend

Автодеплой на сервер (DigitalOcean droplet) при мерже в `main`.

```
push/merge в main ──► GitHub Actions ──SSH──► сервер: git pull + docker compose up -d --build
```

- **Код:** GitHub `zhumka/oah_marketBackend` (публичный — сервер тянет по HTTPS без авторизации).
- **Приложение:** работает в Docker Compose на сервере (app + PostgreSQL + MinIO).
- **CI:** `.github/workflows/deploy.yml` — подключается к серверу по SSH и обновляет контейнеры.

---

## Часть 1. Разовая настройка сервера

Зайди на сервер по SSH и выполни (Ubuntu).

### 1.1. Установить Docker + Compose
```bash
curl -fsSL https://get.docker.com | sh
docker compose version   # проверка, что плагин compose есть
```

### 1.2. SSH-ключ для GitHub Actions
Сгенерируй отдельный ключ для деплоя (на своём компьютере или на сервере):
```bash
ssh-keygen -t ed25519 -C "github-actions-deploy" -f deploy_key -N ""
```
- `deploy_key` (приватный) → пойдёт в GitHub Secret `SSH_KEY`.
- `deploy_key.pub` (публичный) → добавь на сервер пользователю, под которым будет деплой:
```bash
# выполнить НА СЕРВЕРЕ под нужным пользователем (напр. root или deploy)
mkdir -p ~/.ssh && chmod 700 ~/.ssh
echo "<содержимое deploy_key.pub>" >> ~/.ssh/authorized_keys
chmod 600 ~/.ssh/authorized_keys
```

### 1.3. Склонировать репозиторий
```bash
sudo mkdir -p /opt/oahmarket && sudo chown $USER:$USER /opt/oahmarket
git clone https://github.com/zhumka/oah_marketBackend.git /opt/oahmarket
cd /opt/oahmarket
```

### 1.4. Создать продакшн `.env`
`.env` НЕ хранится в git (там секреты) — создаём прямо на сервере:
```bash
cp .env.example .env
nano .env
```
Обязательно задай:
```dotenv
DB_NAME=osh_market
DB_USERNAME=osh_user
DB_PASSWORD=<сильный пароль>

JWT_SECRET=<base64, минимум 32 байта>    # openssl rand -base64 32

MAIL_HOST=smtp.gmail.com
MAIL_PORT=587
MAIL_USERNAME=<рабочая почта>
MAIL_PASSWORD=<app-password>
MAIL_FROM=<рабочая почта>

MINIO_ROOT_USER=<логин>
MINIO_ROOT_PASSWORD=<сильный пароль>
MINIO_BUCKET=osh-market

# ВАЖНО для прода — не создавать тестовых арендаторов:
APP_SEED_ENABLED=false
```
> `SPRING_DATASOURCE_URL` и `MINIO_ENDPOINT` для контейнеров уже заданы в `docker-compose.yml`
> (через имена сервисов `db` / `minio`), отдельно прописывать не нужно.

### 1.5. Первый запуск
```bash
docker compose up -d --build
docker compose ps
docker compose logs -f app   # посмотреть, что приложение поднялось
```
Приложение будет на `http://<IP-сервера>:8080` (Swagger: `/swagger-ui.html`).

---

## Часть 2. Секреты в GitHub (для CI)

Репозиторий → **Settings → Secrets and variables → Actions → New repository secret**:

| Secret | Значение |
|---|---|
| `SSH_HOST` | IP сервера (droplet) |
| `SSH_USER` | пользователь SSH (напр. `root` или `deploy`) |
| `SSH_KEY` | содержимое приватного ключа `deploy_key` целиком (вместе со строками BEGIN/END) |
| `DEPLOY_PATH` | путь к проекту на сервере, напр. `/opt/oahmarket` |
| `SSH_PORT` | (опционально) если SSH-порт не 22 — тогда раскомментируй `port:` в workflow |

---

## Часть 3. Как это работает дальше

1. Делаешь merge / push в ветку `main`.
2. GitHub Actions (`Deploy`) запускается автоматически.
3. По SSH на сервере: `git reset --hard origin/main` → `docker compose up -d --build` → очистка старых образов.
4. Контейнер с новым кодом поднят.

Можно запустить вручную: вкладка **Actions → Deploy → Run workflow**.

---

## Часть 4. Безопасность (важно перед публикацией)

1. **Не выставляй наружу порты БД и MinIO.** В `docker-compose.yml` проброшены `5432` (Postgres) и
   `9000/9001` (MinIO). Приложению они нужны только внутри сети Docker. Для прода привяжи их к localhost,
   например `127.0.0.1:5432:5432`, либо убери публикацию портов вовсе (app общается с ними по внутренней сети).
   ⚠️ Учти: `ufw` НЕ блокирует порты, опубликованные Docker, — поэтому надёжнее именно localhost-биндинг.
2. **Открой наружу только нужное** (SSH 22 и приложение 8080).
3. **`APP_SEED_ENABLED=false`** — чтобы в прод-базе не появились тестовые арендаторы.
4. **Сильные секреты** для `DB_PASSWORD`, `JWT_SECRET`, `MINIO_ROOT_PASSWORD`.
5. **HTTPS:** поставь reverse-proxy (nginx / Caddy / Traefik) перед `app:8080` и выпусти TLS-сертификат
   (Let's Encrypt). Тогда фронт сможет ходить на `https://<домен>/api/...`.

---

## Откат (rollback)
На сервере:
```bash
cd /opt/oahmarket
git reset --hard <предыдущий-commit-sha>
docker compose up -d --build
```
