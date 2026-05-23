# QA single-container deployment

One Docker image runs everything: the Spring Boot API, MariaDB, Redis,
and the Angular bundle (served from the jar's `static/` path). Wrong
shape for production (coupled lifecycle, no scaling) — right shape for
QA: one image, one `docker run`, one volume.

Target box: **16.170.11.41** (`ubuntu@`, see `deploy.env`).

## Deploy on demand (automated)

After the one-time bootstrap below, ship the latest commit to QA with a
single command from your machine — it SSHes in, pulls the branch,
rebuilds the image, and restarts the container:

```powershell
# Windows / PowerShell
$env:ORBIX_SSH_KEY = "C:\path\to\orbix-qa.pem"
orbix-engine-infra\qa\deploy.ps1                 # deploys the branch in deploy.env
orbix-engine-infra\qa\deploy.ps1 -Branch main    # or another branch
```

```bash
# macOS / Linux / git-bash
export ORBIX_SSH_KEY=~/keys/orbix-qa.pem
orbix-engine-infra/qa/deploy.sh
```

Target host/user/branch live in `deploy.env` (committed, non-secret).
Your `.pem` path and any PAT stay out of git — pass them via env or a
gitignored `deploy.env.local`.

## First-time bootstrap (once per instance)

The deploy script assumes Docker is installed and the repo is cloned on
the box. Do this once:

```bash
ssh -i orbix-qa.pem ubuntu@16.170.11.41

# install Docker, then re-login so the docker group applies
curl -fsSL https://get.docker.com | sudo sh
sudo usermod -aG docker ubuntu
exit
ssh -i orbix-qa.pem ubuntu@16.170.11.41

# clone with a fine-grained PAT (repo: ERPCLEAN, Contents: Read-only)
git clone https://oauth2:<PAT>@github.com/godfreydesidery/ERPCLEAN.git orbix
exit
```

The PAT stays in the box's git remote so the automated `deploy.*`
`git pull` works hands-off. That's acceptable here — the instance is
private and only you have SSH. If you'd rather not store a token on the
box, add a read-only **deploy key** (SSH) to the repo and instead
`git clone git@github.com:godfreydesidery/ERPCLEAN.git orbix`.

Now run `deploy.ps1` / `deploy.sh` from your machine for every release.
Security group must allow inbound `22` and `80` from your IP.

---

The sections below are the **manual equivalents** of what `deploy.*` does,
plus reset/wipe operations.

## Build on the server

Clone the repo on the instance with a fine-grained GitHub PAT (repo:
ERPCLEAN, Contents: Read-only), then build. The `.dockerignore` at the
repo root keeps `node_modules` / `target` / `.git` / `infra/local-data`
out of the build context.

```bash
# on the Ubuntu instance (Docker already installed)
git clone https://oauth2:<PAT>@github.com/godfreydesidery/ERPCLEAN.git orbix
cd orbix
docker build -f orbix-engine-infra/qa/Dockerfile -t orbix:qa .
```

> Don't bake the PAT into anything — it lives only in the clone URL. To
> avoid it lingering in the git remote, run
> `git remote set-url origin https://github.com/godfreydesidery/ERPCLEAN.git`
> after the first clone (you'll re-auth on the next pull).

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
cd ~/orbix
git pull
docker build -f orbix-engine-infra/qa/Dockerfile -t orbix:qa .
docker stop orbix && docker rm orbix
docker run -d --name orbix -p 80:8081 -v orbix-data:/var/lib/mysql \
  --restart unless-stopped orbix:qa
```

The `orbix-data` volume survives — Flyway migrates the existing schema
forward, no data loss. **Wipe the QA data when you want** a clean slate:

```bash
docker stop orbix && docker rm orbix
docker volume rm orbix-data        # destroys all QA data
# then re-run the Run step — fresh DB, re-run /setup
```

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
