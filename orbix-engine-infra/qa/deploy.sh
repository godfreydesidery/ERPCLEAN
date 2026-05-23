#!/usr/bin/env bash
#
# On-demand QA deploy (bash equivalent of deploy.ps1, for macOS/Linux/git-bash).
# SSHes to the EC2 box, pulls the branch, rebuilds the single-container image,
# and restarts the container.
#
# Prereq (one-time): the box has Docker + the repo cloned at ~/$REMOTE_DIR
# (see README "First-time bootstrap").
#
# Usage:
#   export ORBIX_SSH_KEY=~/keys/orbix-qa.pem
#   ./deploy.sh                 # deploys BRANCH from deploy.env
#   BRANCH=main ./deploy.sh     # override the branch
set -euo pipefail

here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck disable=SC1091
[ -f "$here/deploy.env" ] && source "$here/deploy.env"
# optional local, gitignored overrides (e.g. SSH_KEY=...)
# shellcheck disable=SC1091
[ -f "$here/deploy.env.local" ] && source "$here/deploy.env.local"

: "${EC2_HOST:?set EC2_HOST in deploy.env}"
: "${EC2_USER:=ubuntu}"
: "${BRANCH:=feature}"
: "${REMOTE_DIR:=orbix}"
: "${CONTAINER:=orbix}"
: "${IMAGE:=orbix:qa}"
: "${DB_VOLUME:=orbix-data}"
KEY="${SSH_KEY:-${ORBIX_SSH_KEY:?set SSH_KEY or ORBIX_SSH_KEY to your .pem path}}"

echo "Deploying $BRANCH to $EC2_USER@$EC2_HOST ..."
# Unquoted heredoc: local vars expand here; \$HOME is escaped for the remote shell.
ssh -i "$KEY" -o StrictHostKeyChecking=accept-new "$EC2_USER@$EC2_HOST" bash -s <<REMOTE
set -euo pipefail
cd "\$HOME/${REMOTE_DIR}"
echo '==> git pull (${BRANCH})'
git fetch --all --quiet
git checkout "${BRANCH}" --quiet
git pull --ff-only
echo '==> docker build'
docker build -f orbix-engine-infra/qa/Dockerfile -t "${IMAGE}" .
echo '==> restart container'
docker stop "${CONTAINER}" 2>/dev/null || true
docker rm "${CONTAINER}" 2>/dev/null || true
docker volume create "${DB_VOLUME}" >/dev/null
ENV_ARG=""
if [ -f orbix-engine-infra/qa/orbix.env ]; then
  ENV_ARG="--env-file orbix-engine-infra/qa/orbix.env"
  echo '==> using orbix.env (env-driven bootstrap)'
else
  echo 'WARNING: orbix-engine-infra/qa/orbix.env missing — app will start NOT bootstrapped'
fi
docker run -d --name "${CONTAINER}" -p 80:8081 -v "${DB_VOLUME}":/var/lib/mysql \$ENV_ARG --restart unless-stopped "${IMAGE}"
docker image prune -f >/dev/null || true
echo '==> deployed'
REMOTE
echo "Done -> http://$EC2_HOST/"
