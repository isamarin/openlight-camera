#!/usr/bin/env bash
set -euo pipefail

IMAGE="openlight-decomp-tools:latest"
DOCKERFILE="${DOCKERFILE:-docker/Dockerfile}"
ENTRYPOINT="${ENTRYPOINT:-docker/entrypoint.sh}"
STAMP_FILE=".docker-build-stamp"

die() { echo "Error: $*" >&2; exit 1; }

if ! command -v docker >/dev/null 2>&1; then
  die "Docker is not installed or not on PATH."
fi

need_build=0

if ! docker image inspect "$IMAGE" >/dev/null 2>&1; then
  echo "Docker image '$IMAGE' not found. Building..."
  need_build=1
elif [[ ! -f "$STAMP_FILE" ]] || [[ "$DOCKERFILE" -nt "$STAMP_FILE" ]] || [[ "$ENTRYPOINT" -nt "$STAMP_FILE" ]]; then
  echo "Dockerfile/Entrypoint is newer than last build. Rebuilding..."
  need_build=1
fi

if (( need_build == 1 )); then
  tmp_log="$(mktemp)"
  if ! docker build -f "$DOCKERFILE" -t "$IMAGE" .; then
    die "Docker build failed"
  fi
  touch "$STAMP_FILE"
  #echo "Docker image built."
fi

INTERACTIVE=0

# The container is entered through bash itself (see DOCKER_FLAGS below), so CMD
# holds bash's own arguments — a leading "bash" would make it try to run the
# bash binary as a script and die with "cannot execute binary file".
if [[ $# -eq 0 ]]; then
  CMD=()
  INTERACTIVE=1
else
  case "$1" in
    build)
      shift
      CMD=(-c "bash .github/scripts/calver.sh && make apk $*")
      set --
      ;;
    clean)
      shift
      CMD=(-c "make clean $*")
      set --
      ;;
    rebuild)
      shift
      CMD=(-c "make clean apk $*")
      set --
      ;;
    bash|shell)
      shift
      CMD=()
      INTERACTIVE=1
      set --
      ;;
    *)
      CMD=(-c "$*")
      set --
      ;;
  esac
fi

DOCKER_FLAGS=(--rm --entrypoint bash -v "$PWD":/workspace -w /workspace)
[[ $INTERACTIVE -eq 1 ]] && DOCKER_FLAGS+=(--privileged -it -e UID="$(id -u)" -e GID="$(id -g)" -v "$PWD":/repo)

docker run "${DOCKER_FLAGS[@]}" "$IMAGE" "${CMD[@]}"
