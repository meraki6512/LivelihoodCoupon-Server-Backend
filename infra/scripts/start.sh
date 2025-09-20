#!/usr/bin/env bash
# infra/scripts/start-fast.sh
# - infra/* 경로의 docker-compose.yml을 병렬로 실행
# - docker compose v2 / docker-compose(v1) 자동 감지
# - 네트워크 없으면 생성
set -euo pipefail

# ── 1️⃣ docker compose 명령어 감지 ──
if docker compose version >/dev/null 2>&1; then
  DOCKER_COMPOSE_CMD="docker compose"
elif docker-compose version >/dev/null 2>&1; then
  DOCKER_COMPOSE_CMD="docker-compose"
else
  echo "ERROR: docker compose 또는 docker-compose가 설치되어 있지 않습니다." >&2
  exit 1
fi

# ── 2️⃣ 경로 설정 ──
ROOT_DIR="$(cd "$(dirname "$0")/../.." && pwd)"
INFRA_BASE_DIR="${ROOT_DIR}/infra"
NETWORK_NAME="livelihoodCoupon-net"

cd "$ROOT_DIR" || exit 1

# ── 3️⃣ 네트워크 생성 ──
if ! docker network inspect "${NETWORK_NAME}" >/dev/null 2>&1; then
  echo "Creating docker network '${NETWORK_NAME}'..."
  docker network create "${NETWORK_NAME}"
else
  echo "Docker network '${NETWORK_NAME}' exists."
fi

# ── 4️⃣ compose 디렉토리 목록: 부가 인프라 서비스 병렬 실행 (ELK, Monitoring) ──
COMPOSE_DIRS=(
  "${INFRA_BASE_DIR}/elk"
  "${INFRA_BASE_DIR}/monitoring"
)


# ── 5️⃣ 병렬 실행 ──
PIDS=()
for d in "${COMPOSE_DIRS[@]}"; do
  if [ -f "${d}/docker-compose.yml" ] || [ -f "${d}/docker-compose.yaml" ]; then
    echo "=== Starting services in ${d} ==="
    pushd "${d}" >/dev/null

    if [ -f "${ROOT_DIR}/.env" ]; then
      $DOCKER_COMPOSE_CMD --env-file "${ROOT_DIR}/.env" up -d --quiet-pull &
    else
      $DOCKER_COMPOSE_CMD up -d --quiet-pull &
    fi

    PIDS+=($!)
    popd >/dev/null
  else
    echo "Skip: no docker-compose.yml in ${d}"
  fi
done

# ── 6️⃣ 모든 백그라운드 프로세스 대기 ──
for pid in "${PIDS[@]}"; do
  wait $pid
done

echo "부가 인프라(ELK, Monitoring) compose 스택이 병렬로 시작되었습니다."

# ── 7️⃣ 메인 앱 스택 실행 (DB, Redis, App) ──
echo ""
echo "=== Starting Main Application Stack (DB, Redis, App) ==="
if [ -f "${ROOT_DIR}/.env" ]; then
  ${DOCKER_COMPOSE_CMD} --project-directory "${ROOT_DIR}" --env-file "${ROOT_DIR}/.env" -f "${ROOT_DIR}/infra/database/docker-compose.yml" -f "${ROOT_DIR}/docker-compose.yml" up --build -d
else
  ${DOCKER_COMPOSE_CMD} --project-directory "${ROOT_DIR}" -f "${ROOT_DIR}/infra/database/docker-compose.yml" -f "${ROOT_DIR}/docker-compose.yml" up --build -d
fi

echo ""
echo "모든 서비스가 성공적으로 시작되었습니다!"