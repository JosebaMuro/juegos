#!/usr/bin/env bash
# Empaqueta Juegos Reunidos para Linux: dist/juegos-linux.tar.gz
# (paquete Python + juegos libres preparados con libres/preparar.py, sin internet al usarlo).
set -euo pipefail
cd "$(dirname "$0")/.."
OUT=dist/juegos-linux
mkdir -p dist
if [[ ! -f linux/datos/libres/catalogo.json ]]; then
  echo "Juegos libres (libres/preparar.py)…"
  JUEGOS_ASSETS="$PWD/linux/datos" python3 libres/preparar.py
fi
rm -rf "$OUT"
mkdir -p "$OUT"
cp -r linux/juegos_linux linux/pyproject.toml linux/LEEME-linux.md "$OUT/"
cp -r linux/datos "$OUT/datos"
find "$OUT" -name __pycache__ -type d -prune -exec rm -rf {} +
tar -C dist -czf dist/juegos-linux.tar.gz juegos-linux
ls -l dist/juegos-linux.tar.gz
