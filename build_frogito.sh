#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
echo "==> Root: ${ROOT_DIR}"

mkdir -p "${ROOT_DIR}/frogito/lib"

echo "==> Building Java (Gradle)..."
cd "${ROOT_DIR}"
./gradlew clean jar createNative deployNative

echo "==> Building C++ (CMake)..."
CPP_SRC_DIR="${ROOT_DIR}/frogitovm"
CPP_BUILD_DIR="${CPP_SRC_DIR}/build"
mkdir -p "${CPP_BUILD_DIR}"

CMAKE_GENERATOR_ARGS=()
if command -v ninja >/dev/null 2>&1; then
  CMAKE_GENERATOR_ARGS=(-G Ninja)
fi

IS_WINDOWS=0
case "$(uname -s 2>/dev/null || echo unknown)" in
  MINGW*|MSYS*|CYGWIN*) IS_WINDOWS=1 ;;
esac

cmake -S "${CPP_SRC_DIR}" -B "${CPP_BUILD_DIR}" "${CMAKE_GENERATOR_ARGS[@]}" -DCMAKE_BUILD_TYPE=Release

if [ "${IS_WINDOWS}" -eq 1 ]; then
  cmake --build "${CPP_BUILD_DIR}" --config Release
  cmake --build "${CPP_BUILD_DIR}" --target deploy --config Release
else
  cmake --build "${CPP_BUILD_DIR}"
  cmake --build "${CPP_BUILD_DIR}" --target deploy
fi

echo "==> Build finished successfully"

FROGITO_BIN="${ROOT_DIR}/frogito/bin/frogito"
BASHRC="${HOME}/.bashrc"

if [ -f "${FROGITO_BIN}" ]; then
  mkdir -p "$(dirname "${BASHRC}")"
  touch "${BASHRC}"

  TMP="$(mktemp)"

  awk '
    BEGIN { skip=0; brace=0 }
    {
      if ($0 ~ /^[[:space:]]*alias[[:space:]]+frogito=/) next
      if ($0 ~ /^[[:space:]]*frogito[[:space:]]*\(\)[[:space:]]*\{/) { skip=1; brace=0 }
      if (skip==1) {
        brace += gsub(/\{/, "{")
        brace -= gsub(/\}/, "}")
        if (brace <= 0 && $0 ~ /\}/) { skip=0 }
        next
      }
      print
    }
  ' "${BASHRC}" > "${TMP}"

  mv "${TMP}" "${BASHRC}"

  if ! grep -Fqx "# Frogito CLI" "${BASHRC}" 2>/dev/null; then
    printf "\n# Frogito CLI\n" >> "${BASHRC}"
  fi

  printf 'alias frogito="%s"\n' "${FROGITO_BIN}" >> "${BASHRC}"

  echo "==> Updated ${BASHRC}"
  echo "==> Run: source ~/.bashrc"
else
  echo "==> frogito binary not found, alias not created"
fi
