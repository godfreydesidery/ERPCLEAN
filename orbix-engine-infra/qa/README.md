# QA single-container deployment

One Docker image runs everything: the Spring Boot API, MariaDB, Redis,
and the Angular bundle (served from the jar's `static/` path). Wrong
shape for production (coupled lifecycle, no scaling) — right shape for
QA: one image, one `docker run`, one volume.

## Build on the server

```bash
# rsync the working tree to the EC2 instance, e.g.
rsync -az --delete --exclude target --exclude node_modules \
  ./ ec2-user@<host>:/home/ec2-user/orbix/

# on the instance
cd /home/ec2-user/orbix
docker build -f orbix-engine-infra/qa/Dockerfile -t orbix:qa .
```

Build takes ~5 min on a `t3.medium` the first time (pulls base images,
runs `npm ci`, runs `mvn dependency:go-offline`). Subsequent builds
reuse the cached layers — usually under 90 s if only code changed.

## Run

```bash
docker volume create orbix-data       # persists MariaDB across container restarts
docker run -d --name orbix \
  -p 80:8081 \
  -v orbix-data:/var/lib/mysql \
  --restart unless-stopped \
  orbix:qa
```

First boot takes ~30 s (MariaDB init + Flyway migrations + JVM warm).
Then hit `http://<elastic-ip>/setup` to run the one-time setup wizard.

## Watch it boot

```bash
docker logs -f orbix
```

You should see, roughly in order:
1. `[entrypoint] First run — initialising MariaDB`
2. supervisord starts `mariadb`, `redis`, `api` programs
3. Flyway prints `Successfully applied N migrations`
4. `Tomcat started on port 8081`

## Update

```bash
# Pull latest source, then:
cd /home/ec2-user/orbix
docker build -f orbix-engine-infra/qa/Dockerfile -t orbix:qa .
docker stop orbix && docker rm orbix
docker run -d --name orbix -p 80:8081 -v orbix-data:/var/lib/mysql \
  --restart unless-stopped orbix:qa
```

The `orbix-data` volume survives — Flyway migrates the existing schema
forward, no data loss.

## Credentials

DB user `orbix` / password `orbixlocal`, root `rootlocal`. These never
leave the container (MariaDB binds 127.0.0.1 only). Override via env
on `docker run` if you want different ones at first init:

```bash
-e DB_PASSWORD=<...> -e DB_ROOT_PASSWORD=<...>
```

Override only affects the **first run** — once the data dir is
initialised, MariaDB ignores the env vars.

## Reset everything

```bash
docker stop orbix && docker rm orbix
docker volume rm orbix-data   # destroys all data
```

## Notes / caveats

- **JWT signing mode is `dev-in-memory`.** The RSA key regenerates on
  every container restart, so every restart logs every user out. Fine
  for QA. Production needs a stable RS256 key loaded from a secret.
- **No HTTPS.** Put Caddy or nginx with Let's Encrypt in front of the
  container (host-level, port 443 → container 8081) when QA needs HTTPS.
- **Single point of failure.** Restart of the container = downtime for
  the DB too. Acceptable for QA; the production topology splits them.
- **Logs are intermingled.** All three programs (mariadb, redis, api)
  write to the container's stdout via supervisord. `docker logs orbix`
  shows everything in chronological order, prefixed by program name.
