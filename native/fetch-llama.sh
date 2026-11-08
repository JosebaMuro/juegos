#!/usr/bin/env bash
# Descarga el código de llama.cpp usado por la app (el mismo que va dentro de
# llama-cpp-python 0.3.36 en PyPI, llama.cpp commit 0c1e570) en native/llama.cpp.
set -euo pipefail
cd "$(dirname "$0")"
VER=0.3.36
if [ ! -d llama.cpp ]; then
  python3 - "$VER" <<'PY'
import io, json, sys, tarfile, urllib.request
ver = sys.argv[1]
d = json.load(urllib.request.urlopen(f"https://pypi.org/pypi/llama-cpp-python/{ver}/json"))
url = [u["url"] for u in d["urls"] if u["packagetype"] == "sdist"][0]
data = urllib.request.urlopen(url).read()
tarfile.open(fileobj=io.BytesIO(data), mode="r:gz").extractall(".")
PY
  mv "llama_cpp_python-$VER/vendor/llama.cpp" llama.cpp
  rm -rf "llama_cpp_python-$VER"
fi
echo "llama.cpp en $(pwd)/llama.cpp"
