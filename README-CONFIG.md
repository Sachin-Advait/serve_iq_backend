# Configuration & Deployment — ServeIQ (queue-backend)

Everything secret or environment-specific is read from the environment. There
are no working defaults in `application.properties` any more, so a missing
required variable makes the app **fail at startup** rather than come up pointing
at the wrong database.

That is deliberate. Previously the Postgres password, the live Twilio SID and
auth token, and the Cloudinary API secret (hardcoded in `CloudinaryConfig.java`)
were all in tracked files — so anyone with repo access, or a copy of the built
jar, had them.

---

## 1. Variables

### Required — the app will not start without these

| Variable | What it is | Example |
|---|---|---|
| `DB_URL` | JDBC URL | `jdbc:postgresql://db.internal:5432/serveiq` |
| `DB_USERNAME` | Database user | `serveiq` |
| `DB_PASSWORD` | Database password | |
| `JWT_SECRET` | HS256 signing key, **min 32 chars** | output of `openssl rand -base64 48` |

### Optional — defaults shown, override per environment

| Variable | Default | Notes |
|---|---|---|
| `PORT` | `8085` | |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:3000` | Comma-separated **exact** origins. Wildcards rejected. |
| `JWT_EXPIRATION_MINUTES` | `720` | 12 hours |
| `JPA_DDL_AUTO` | `validate` | See §2 — do not set `update` in a deployed environment |
| `DB_POOL_MAX` / `DB_POOL_MIN_IDLE` | `15` / `5` | Hikari sizing |
| `TWILIO_SID` / `TWILIO_TOKEN` / `TWILIO_FROM` | *(blank)* | Blank disables WhatsApp; the app logs and carries on |
| `CLOUDINARY_CLOUD_NAME` / `_API_KEY` / `_API_SECRET` | *(blank)* | Blank disables media sync |
| `FIREBASE_CREDENTIALS_PATH` | *(blank)* | Absolute path to the service-account JSON. Blank disables push. |
| `LOG_LEVEL_APP` | `INFO` | `DEBUG` only while debugging — it is noisy |

Optional integrations degrade rather than crash: if Twilio/Cloudinary/Firebase
credentials are absent the feature is skipped with a log line. That keeps local
development workable without handing every developer production API keys.

---

## 2. Schema management — important change

`spring.jpa.hibernate.ddl-auto` was `update`, meaning Hibernate altered the live
schema at every boot. That silently adds columns without backfilling defaults
(which is how `is_transfer` ended up NULL on existing rows and crashed the
transferred-tokens report), and it never creates the indexes or constraints the
domain needs.

Schema is now owned by **Flyway**, and Hibernate is set to `validate` — it checks
the mapping matches and refuses to start if it does not.

- Migrations live in `src/main/resources/db/migration`, named `V<n>__<desc>.sql`.
- They run automatically at startup, in order, once each.
- `baseline-on-migrate=true` is set, so pointing this at the **existing**
  database is safe: Flyway marks the current state as the baseline and only
  applies migrations numbered above it.

Adding a schema change:

```
src/main/resources/db/migration/V3__add_something.sql
```

Never edit a migration that has already been applied anywhere — add a new one.

For a genuinely throwaway local database you can still set `JPA_DDL_AUTO=update`,
but do not do that anywhere shared.

---

## 3. Local development

```bash
cp .env.example .env      # then fill in the blanks
set -a; source .env; set +a
./mvnw spring-boot:run
```

`.env` is gitignored. `set -a` exports everything sourced after it so Spring
sees it; `set +a` turns that off again.

Postgres via Docker:

```bash
docker run -d --name serveiq-db \
  -e POSTGRES_DB=serveiq -e POSTGRES_USER=serveiq -e POSTGRES_PASSWORD=localdev \
  -p 5432:5432 postgres:16
```

Then `DB_URL=jdbc:postgresql://127.0.0.1:5432/serveiq`, `DB_USERNAME=serveiq`,
`DB_PASSWORD=localdev`. Flyway builds the schema on first start.

Generate a local JWT secret once:

```bash
echo "JWT_SECRET=$(openssl rand -base64 48)" >> .env
```

Leave `TWILIO_*`, `CLOUDINARY_*` and `FIREBASE_CREDENTIALS_PATH` blank locally
unless you are specifically testing those paths.

---

## 4. Deployed environments

Order of preference for where secrets live:

1. **A real secret manager** — AWS Secrets Manager, Azure Key Vault, GCP Secret
   Manager, Vault, injected as env vars at runtime.
2. **Orchestrator secrets** — Kubernetes `Secret`, ECS task-definition secrets.
3. **CI/CD masked variables** — Bitbucket Pipelines *secured* repository
   variables.

Never: `.env` committed, values in the Dockerfile, or secrets as `--build-arg`
(they stay in the image layers and show up in `docker history`).

### Docker

```bash
docker build -t serveiq:local .
docker run --rm -p 8085:8085 --env-file .env serveiq:local
```

`--env-file` keeps the values out of shell history and `ps`.

### docker-compose

```yaml
services:
  serveiq:
    image: serveiq:local
    ports: ["8085:8085"]
    env_file: [.env]          # not committed
    environment:
      DB_URL: jdbc:postgresql://db:5432/serveiq
    depends_on: [db]

  db:
    image: postgres:16
    environment:
      POSTGRES_DB: serveiq
      POSTGRES_USER: serveiq
      POSTGRES_PASSWORD: ${DB_PASSWORD}
    volumes: ["pg-data:/var/lib/postgresql/data"]

volumes:
  pg-data:
```

### Kubernetes

```bash
kubectl create secret generic serveiq-secrets \
  --from-literal=DB_PASSWORD='...' \
  --from-literal=JWT_SECRET="$(openssl rand -base64 48)" \
  --from-literal=TWILIO_TOKEN='...' \
  --from-literal=CLOUDINARY_API_SECRET='...'
```

```yaml
spec:
  containers:
    - name: serveiq
      image: your-registry/serveiq:1.4.0
      envFrom:
        - secretRef:    { name: serveiq-secrets }
        - configMapRef: { name: serveiq-config }    # non-secret values
      ports: [{ containerPort: 8085 }]
      readinessProbe:
        httpGet: { path: /actuator/health/readiness, port: 8085 }
        initialDelaySeconds: 20
      livenessProbe:
        httpGet: { path: /actuator/health/liveness, port: 8085 }
        initialDelaySeconds: 40
```

The Firebase service-account JSON is a *file*, not a string — mount it from a
secret and point `FIREBASE_CREDENTIALS_PATH` at the mount path:

```yaml
      volumeMounts:
        - name: firebase, mountPath: /etc/firebase, readOnly: true
  volumes:
    - name: firebase
      secret: { secretName: serveiq-firebase }
```
…with `FIREBASE_CREDENTIALS_PATH=/etc/firebase/service-account.json`.

Split secret from non-secret: `CORS_ALLOWED_ORIGINS`, `LOG_LEVEL_APP`,
`DB_POOL_MAX` belong in a ConfigMap; anything with a credential belongs in the
Secret.

### Running more than one instance

Flyway takes a lock, so concurrent startups are safe — one migrates, the others
wait. Token issuance is protected by a unique constraint plus a retry, so it is
safe across instances.

---

## 5. Changing a credential

When a credential changes — a scheduled key roll, a password policy, someone
leaving — the sequence is:

1. Change it at the source: the Postgres user, the Twilio auth token (Twilio
   console), the Cloudinary API secret (Cloudinary console), or the Firebase
   service account key.
2. Update the value in the secret store.
3. Roll the deployment so the new value is picked up. Nothing is cached beyond
   process lifetime, so a normal rolling restart is enough.
4. Confirm `GET /actuator/health` reports `db` as `UP`.

Because every value is read from the environment, none of this needs a code
change or a new build — the same artifact picks up the new value on restart.

---

## 6. Startup checklist

- [ ] `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` set and reachable
- [ ] `JWT_SECRET` at least 32 chars and **different per environment**
- [ ] `JPA_DDL_AUTO=validate` (not `update`)
- [ ] `CORS_ALLOWED_ORIGINS` lists the real frontend origins, no `*`
- [ ] Flyway migrations applied — check the `flyway_schema_history` table
- [ ] `/actuator/health/readiness` returns 200
