#!/usr/bin/env bash
# Alternativa sin NDK (la que se usó en el entorno de Claude): compila llama-server para Android ARM64
# con zig/musl. En GitHub Actions se usa native/build-android.sh con el NDK oficial.
#
# en tres variantes según las instrucciones de la CPU:
#   armv8   -> cualquier ARM64 (NEON)
#   dotprod -> ARMv8.2 + dotprod + fp16 (Snapdragon 845+, Tensor, Exynos 9820+, Dimensity...)
#   i8mm    -> ARMv8.6 + i8mm (Snapdragon 8 Gen 1+, Tensor G2+, Dimensity 9000+...)
# La app elige en tiempo de ejecución la mejor que admite el móvil (ver chat/Hardware.java).
#
# Se enlaza estáticamente con musl (vía zig) porque glibc moderno usa la llamada clone3,
# que el filtro seccomp de las apps Android bloquea.
#
# Requisitos: cmake, ninja, zig (pip install ziglang) y el código de llama.cpp.
#   LLAMA_SRC=/ruta/a/llama.cpp ZIG=/ruta/a/zig ./native/build-llama.sh
set -euo pipefail
cd "$(dirname "$0")"
LLAMA_SRC="${LLAMA_SRC:?indica LLAMA_SRC con la ruta del código de llama.cpp}"
ZIG="${ZIG:-zig}"
WORK="${WORK:-$PWD/work}"
mkdir -p "$WORK/bin" libs/arm64-v8a

printf '#!/bin/sh\nexec %s ar "$@"\n' "$ZIG" > "$WORK/bin/zar"
printf '#!/bin/sh\nexec %s ranlib "$@"\n' "$ZIG" > "$WORK/bin/zranlib"
chmod +x "$WORK/bin/zar" "$WORK/bin/zranlib"

build() {
  local name=$1 cpu=$2
  printf '#!/bin/sh\nexec %s cc -target aarch64-linux-musl -mcpu=%s "$@"\n' "$ZIG" "$cpu" > "$WORK/bin/$name-cc"
  printf '#!/bin/sh\nexec %s c++ -target aarch64-linux-musl -mcpu=%s "$@"\n' "$ZIG" "$cpu" > "$WORK/bin/$name-c++"
  chmod +x "$WORK/bin/$name-cc" "$WORK/bin/$name-c++"
  cat > "$WORK/tc-$name.cmake" <<TC
set(CMAKE_SYSTEM_NAME Linux)
set(CMAKE_SYSTEM_PROCESSOR aarch64)
set(CMAKE_C_COMPILER $WORK/bin/$name-cc)
set(CMAKE_CXX_COMPILER $WORK/bin/$name-c++)
set(CMAKE_AR $WORK/bin/zar)
set(CMAKE_RANLIB $WORK/bin/zranlib)
set(CMAKE_EXE_LINKER_FLAGS_INIT "-static")
TC
  cmake -S "$LLAMA_SRC" -B "$WORK/$name" -G Ninja -DCMAKE_TOOLCHAIN_FILE="$WORK/tc-$name.cmake" \
    -DCMAKE_BUILD_TYPE=Release -DBUILD_SHARED_LIBS=OFF -DGGML_NATIVE=OFF -DGGML_OPENMP=OFF \
    -DGGML_CCACHE=OFF -DLLAMA_OPENSSL=OFF -DLLAMA_BUILD_TESTS=OFF -DLLAMA_BUILD_EXAMPLES=OFF \
    -DLLAMA_BUILD_APP=OFF -DLLAMA_USE_PREBUILT_UI=OFF -DLLAMA_BUILD_UI=OFF \
    -DLLAMA_BUILD_SERVER=ON -DLLAMA_BUILD_TOOLS=ON
  ninja -C "$WORK/$name" llama-server
  "$ZIG" objcopy --strip-all "$WORK/$name/bin/llama-server" "libs/arm64-v8a/libllama_cpu_$name.so" 2>/dev/null \
    || cp "$WORK/$name/bin/llama-server" "libs/arm64-v8a/libllama_cpu_$name.so"
}

build armv8   generic
build dotprod generic+v8_2a+dotprod+fullfp16
build i8mm    generic+v8_6a+dotprod+fullfp16+i8mm
ls -la libs/arm64-v8a
