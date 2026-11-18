"""Chat con IA local y visión: los mismos modelos GGUF que la app de Android, servidos por
llama.cpp (llama-server) en 127.0.0.1. En Linux usa la GPU con Vulkan (NVIDIA, AMD,
Intel) o CUDA si el llama-server lo trae, y si no la CPU."""
import base64
import json
import os
import shutil
import socket
import subprocess
import time
import urllib.request
from dataclasses import dataclass
from pathlib import Path

from . import rutas


@dataclass
class Modelo:
    id: str
    nombre: str
    repo: str
    quant: str
    min_ram_gb: float
    aprox_gb: float


MODELOS = [
    Modelo("smolvlm2-500m", "SmolVLM2 500M (muy ligero)", "ggml-org/SmolVLM2-500M-Video-Instruct-GGUF", "Q8_0", 0, 0.6),
    Modelo("smolvlm2-2.2b", "SmolVLM2 2.2B (equilibrado)", "ggml-org/SmolVLM2-2.2B-Instruct-GGUF", "Q4_K_M", 4.5, 1.9),
    Modelo("gemma3-4b", "Gemma 3 4B (el mejor en español)", "ggml-org/gemma-3-4b-it-GGUF", "Q4_K_M", 7.0, 3.3),
]


def ram_gb():
    try:
        for l in open("/proc/meminfo"):
            if l.startswith("MemTotal"):
                return int(l.split()[1]) / 1024 / 1024
    except OSError:
        pass
    return 0.0


def recomendado():
    best = MODELOS[0]
    for m in MODELOS:
        if ram_gb() >= m.min_ram_gb:
            best = m
    return best


def carpeta(m: Modelo) -> Path:
    return rutas.sub("modelos") / m.id


def instalado(m: Modelo) -> bool:
    d = carpeta(m)
    return (d / "modelo.gguf").is_file() and (d / "vision.gguf").is_file()


def _get(url):
    req = urllib.request.Request(url, headers={"User-Agent": "juegos-linux"})
    with urllib.request.urlopen(req, timeout=60) as r:
        return r.read()


def descargar(m: Modelo, progreso=lambda hecho, total, que: None):
    """Busca en Hugging Face el GGUF con la cuantización elegida y su proyector de visión."""
    info = json.loads(_get(f"https://huggingface.co/api/models/{m.repo}"))
    nombres = [s["rfilename"] for s in info.get("siblings", [])]
    ggufs = [n for n in nombres if n.lower().endswith(".gguf")]
    mmproj = [n for n in ggufs if "mmproj" in n.lower()]
    vis = next((n for n in mmproj if "q8_0" in n.lower()), mmproj[0] if mmproj else None)
    mods = [n for n in ggufs if "mmproj" not in n.lower()]
    mod = next((n for n in mods if m.quant.lower() in n.lower()), None) or \
        next((n for n in mods if "q4_0" in n.lower()), None)
    if not mod or not vis:
        raise RuntimeError(f"No encuentro los archivos del modelo en {m.repo}")
    d = carpeta(m)
    for remoto, local, que in ((vis, "vision.gguf", "Visión"), (mod, "modelo.gguf", "Modelo")):
        dest = d / local
        if dest.is_file():
            continue
        part = d / (local + ".part")
        url = f"https://huggingface.co/{m.repo}/resolve/main/{remoto}"
        have = part.stat().st_size if part.exists() else 0
        req = urllib.request.Request(url, headers={"User-Agent": "juegos-linux", **({"Range": f"bytes={have}-"} if have else {})})
        with urllib.request.urlopen(req, timeout=60) as r:
            total = have + int(r.headers.get("Content-Length") or 0)
            if r.status == 200:
                have = 0
            with open(part, "ab" if have else "wb") as fh:
                while True:
                    b = r.read(1 << 20)
                    if not b:
                        break
                    fh.write(b)
                    have += len(b)
                    progreso(have, total, que)
        part.rename(dest)


# ---------------------------------------------------------------- llama-server

def servidores():
    """llama-server disponibles, el más rápido primero (GPU antes que CPU)."""
    out = []
    env = os.environ.get("JUEGOS_LLAMA_SERVER")
    if env:
        out.append(env)
    for base in (Path("/opt/suite/llama.cpp"), rutas.PAQUETE / "llama.cpp"):
        for v in ("cuda", "vulkan", "cpu"):
            p = base / v / "llama-server"
            if p.is_file():
                out.append(str(p))
    p = shutil.which("llama-server")
    if p:
        out.append(p)
    return list(dict.fromkeys(out))


def _puerto_libre():
    with socket.socket() as s:
        s.bind(("127.0.0.1", 0))
        return s.getsockname()[1]


class Motor:
    """Un llama-server con el modelo cargado. Prueba cada binario hasta que uno arranca."""

    def __init__(self, m: Modelo, hilos=None):
        self.m = m
        self.hilos = hilos or max(1, (os.cpu_count() or 4) - 1)
        self.proc = None
        self.port = 0
        self.binario = ""
        self.log = ""

    def arrancar(self, espera=180):
        if not instalado(self.m):
            raise RuntimeError("El modelo no está descargado")
        bins = servidores()
        if not bins:
            raise RuntimeError("No hay llama-server (lo instala el instalador de la suite o: sudo snap install llama-cpp)")
        d = carpeta(self.m)
        errores = []
        for b in bins:
            self.port = _puerto_libre()
            logf = rutas.sub("modelos") / "llama-server.log"
            cmd = [b, "-m", str(d / "modelo.gguf"), "--mmproj", str(d / "vision.gguf"), "--host", "127.0.0.1",
                   "--port", str(self.port), "-c", "4096", "-t", str(self.hilos), "-ngl", "99", "--jinja"]
            with open(logf, "wb") as lf:
                self.proc = subprocess.Popen(cmd, stdout=lf, stderr=subprocess.STDOUT,
                                             env={**os.environ, "LD_LIBRARY_PATH": str(Path(b).parent)})
            t0 = time.time()
            while time.time() - t0 < espera and self.proc.poll() is None:
                try:
                    if json.loads(_get(f"http://127.0.0.1:{self.port}/health")).get("status") == "ok":
                        self.binario = b
                        return b
                except Exception:
                    pass
                time.sleep(0.5)
            self.parar()
            self.log = logf.read_text(errors="replace")[-600:] if logf.exists() else ""
            errores.append(f"{b}: {self.log.strip().splitlines()[-1] if self.log.strip() else 'no arrancó'}")
        raise RuntimeError("No arranca ningún llama-server:\n" + "\n".join(errores))

    @property
    def variante(self):
        p = Path(self.binario)
        return p.parent.name if p.parent.name in ("cuda", "vulkan", "cpu") else "sistema"

    def parar(self):
        if self.proc and self.proc.poll() is None:
            self.proc.terminate()
            try:
                self.proc.wait(5)
            except subprocess.TimeoutExpired:
                self.proc.kill()
        self.proc = None

    def chat(self, mensajes, imagen: str = None, al_escribir=lambda t: None):
        """Envía la conversación (lista de {role, content}) y devuelve la respuesta, que
        va llegando a [al_escribir]. [imagen]: ruta de una imagen para el último mensaje."""
        msgs = [dict(x) for x in mensajes]
        if imagen:
            data = base64.b64encode(Path(imagen).read_bytes()).decode()
            ext = Path(imagen).suffix.lower().lstrip(".") or "png"
            msgs[-1]["content"] = [{"type": "text", "text": msgs[-1]["content"]},
                                   {"type": "image_url", "image_url": {"url": f"data:image/{ext};base64,{data}"}}]
        body = json.dumps({"messages": msgs, "stream": True, "temperature": 0.7}).encode()
        req = urllib.request.Request(f"http://127.0.0.1:{self.port}/v1/chat/completions", body,
                                     {"Content-Type": "application/json"})
        out = ""
        with urllib.request.urlopen(req, timeout=600) as r:
            for line in r:
                line = line.decode("utf-8", "replace").strip()
                if not line.startswith("data:"):
                    continue
                data = line[5:].strip()
                if data == "[DONE]":
                    break
                try:
                    delta = json.loads(data)["choices"][0]["delta"].get("content") or ""
                except (KeyError, IndexError, ValueError):
                    continue
                out += delta
                al_escribir(out)
        return out
