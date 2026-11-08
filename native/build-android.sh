#!/usr/bin/env bash
# Compila llama-server para Android con el NDK oficial (bionic).
# Uso: native/build-android.sh <variante>
#   cpu-armv8 | cpu-dotprod | cpu-i8mm   -> CPU, binario estático
#   vulkan                               -> GPU por Vulkan (Mali/Tensor, Adreno, Xclipse...)
#   snapdragon                           -> GPU Adreno (OpenCL) + NPU Hexagon; se ejecuta dentro
#                                           del contenedor ghcr.io/snapdragon-toolchain/arm64-android
# Deja los archivos en native/libs/arm64-v8a/.
set -euo pipefail
cd "$(dirname "$0")"
V="$1"
SRC="$PWD/llama.cpp"
OUT="$PWD/libs/arm64-v8a"
NDK="${ANDROID_NDK_ROOT:-${ANDROID_NDK_HOME:-}}"
[ -n "$NDK" ] || { echo "Falta ANDROID_NDK_ROOT"; exit 1; }
mkdir -p "$OUT"
COMMON=(-G Ninja -DCMAKE_BUILD_TYPE=Release
  -DCMAKE_TOOLCHAIN_FILE="$NDK/build/cmake/android.toolchain.cmake"
  -DANDROID_ABI=arm64-v8a -DANDROID_STL=c++_static
  -DBUILD_SHARED_LIBS=OFF -DGGML_NATIVE=OFF -DGGML_OPENMP=OFF -DGGML_CCACHE=OFF
  -DLLAMA_OPENSSL=OFF -DLLAMA_BUILD_TESTS=OFF -DLLAMA_BUILD_EXAMPLES=OFF -DLLAMA_BUILD_APP=OFF
  -DLLAMA_USE_PREBUILT_UI=OFF -DLLAMA_BUILD_UI=OFF -DLLAMA_BUILD_SERVER=ON -DLLAMA_BUILD_TOOLS=ON)
B="$PWD/work/$V"
case "$V" in
  cpu-armv8)   cmake -S "$SRC" -B "$B" "${COMMON[@]}" -DANDROID_PLATFORM=android-24 -DGGML_CPU_ARM_ARCH=armv8-a ;;
  cpu-dotprod) cmake -S "$SRC" -B "$B" "${COMMON[@]}" -DANDROID_PLATFORM=android-24 -DGGML_CPU_ARM_ARCH=armv8.2-a+dotprod+fp16 ;;
  cpu-i8mm)    cmake -S "$SRC" -B "$B" "${COMMON[@]}" -DANDROID_PLATFORM=android-24 -DGGML_CPU_ARM_ARCH=armv8.6-a+dotprod+fp16+i8mm ;;
  vulkan)
    cmake -S "$SRC" -B "$B" "${COMMON[@]}" -DANDROID_PLATFORM=android-28 -DGGML_CPU_ARM_ARCH=armv8.2-a+dotprod+fp16 \
      -DGGML_VULKAN=ON -DVulkan_GLSLC_EXECUTABLE="$NDK/shader-tools/linux-x86_64/glslc" \
      -DVulkan_INCLUDE_DIR="${VULKAN_HEADERS:?}/include" \
      -DSPIRV-Headers_DIR="${SPIRV_HEADERS_DIR:?}" \
      -DCMAKE_CXX_FLAGS="-I${VULKAN_HEADERS}/include -I${SPIRV_HEADERS_DIR}/../../../include" ;;
  snapdragon)
    # Preajuste oficial de llama.cpp para Snapdragon (OpenCL + Hexagon), pero enlazado estático.
    cmake -S "$SRC" -B "$B" "${COMMON[@]}" -DANDROID_PLATFORM=android-31 \
      -DCMAKE_C_FLAGS="-march=armv8.7a+fp16+dotprod+i8mm -fvectorize -ffp-model=fast -fno-finite-math-only -D_GNU_SOURCE" \
      -DCMAKE_CXX_FLAGS="-march=armv8.7a+fp16+dotprod+i8mm -fvectorize -ffp-model=fast -fno-finite-math-only -D_GNU_SOURCE" \
      -DCMAKE_PREFIX_PATH="${OPENCL_SDK_ROOT:-}" -DHEXAGON_SDK_ROOT="${HEXAGON_SDK_ROOT:-}" \
      -DHEXAGON_TOOLS_ROOT="${HEXAGON_TOOLS_ROOT:-}" -DPREBUILT_LIB_DIR=android_aarch64 \
      -DGGML_LLAMAFILE=OFF -DGGML_OPENCL=ON -DGGML_OPENCL_USE_ADRENO_KERNELS=ON -DGGML_OPENCL_EMBED_KERNELS=ON \
      -DGGML_HEXAGON=ON ;;
  *) echo "variante desconocida: $V"; exit 1 ;;
esac
cmake --build "$B" --target llama-server -j"$(nproc)"
STRIP="$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-strip"
NAME="${V//-/_}"
"$STRIP" -o "$OUT/libllama_$NAME.so" "$B/bin/llama-server"
if [ "$V" = snapdragon ]; then
  # Bibliotecas que se cargan en el DSP Hexagon (una por generación de NPU).
  find "$B" -name 'libggml-htp-v*.so' -exec cp {} "$OUT/" \;
fi
ls -la "$OUT"
