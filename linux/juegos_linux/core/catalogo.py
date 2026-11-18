"""Catálogo de juegos libres (libres/catalogo.json) y cómo lanzarlos en Linux:
los web en el navegador, servidos desde 127.0.0.1 (el navegador en snap no lee /opt),
y los de DOS en DOSBox nativo (también sin escritorio, con SDL sobre KMS)."""
import functools
import http.server
import json
import os
import shutil
import subprocess
import threading
import webbrowser
import zipfile
from dataclasses import dataclass
from pathlib import Path

from . import rutas


@dataclass
class Juego:
    id: str
    tipo: str
    titulo: str
    descripcion: str = ""
    controles: str = ""
    autor: str = ""
    licencia: str = ""
    web: str = ""
    repo: str = ""
    exe: str = ""
    inicio: str = "index.html"


def cargar(datos: Path = None):
    datos = datos or rutas.datos_juegos()
    f = datos / "libres" / "catalogo.json"
    if not f.is_file():
        return []
    j = json.loads(f.read_text(encoding="utf-8"))
    campos = Juego.__dataclass_fields__
    return [Juego(**{k: v for k, v in o.items() if k in campos}) for o in j["juegos"]]


def disponible(j: Juego, datos: Path = None) -> bool:
    datos = datos or rutas.datos_juegos()
    if j.tipo == "web":
        return (datos / "web" / j.id / j.inicio).is_file()
    return (datos / "dos" / "juegos" / f"{j.id}.zip").is_file()


def licencia(j: Juego, datos: Path = None) -> str:
    f = (datos or rutas.datos_juegos()) / "libres" / "licencias" / f"{j.id}.txt"
    return f.read_text(encoding="utf-8") if f.is_file() else f"Licencia: {j.licencia}\nCódigo: {j.repo}"


# ---------------------------------------------------------------- juegos web

_servidor = None


def _servir(raiz: Path) -> int:
    global _servidor
    if _servidor is None:
        h = functools.partial(_Silencioso, directory=str(raiz))
        _servidor = http.server.ThreadingHTTPServer(("127.0.0.1", 0), h)
        threading.Thread(target=_servidor.serve_forever, daemon=True).start()
    return _servidor.server_address[1]


class _Silencioso(http.server.SimpleHTTPRequestHandler):
    def log_message(self, *a):
        pass


def url_web(j: Juego, datos: Path = None) -> str:
    datos = datos or rutas.datos_juegos()
    port = _servir(datos / "web")
    return f"http://127.0.0.1:{port}/{j.id}/{j.inicio}"


def abrir_web(j: Juego, datos: Path = None) -> str:
    u = url_web(j, datos)
    webbrowser.open(u)
    return u


# ---------------------------------------------------------------- DOS

def dosbox() -> str:
    for exe in ("dosbox-staging", "dosbox-x", "dosbox"):
        p = shutil.which(exe)
        if p:
            return p
    return ""


def hay_escritorio() -> bool:
    return bool(os.environ.get("DISPLAY") or os.environ.get("WAYLAND_DISPLAY"))


def entorno_dos():
    """Sin escritorio, SDL dibuja directamente en la pantalla (KMS/DRM)."""
    env = dict(os.environ)
    if not hay_escritorio():
        env.setdefault("SDL_VIDEODRIVER", "kmsdrm")
    return env


def carpeta_dos(j: Juego, datos: Path = None) -> Path:
    """Carpeta del juego (su C:), descomprimida una vez: las partidas se guardan ahí."""
    d = rutas.sub("dos") / j.id
    marca = d / ".juegos-listo"
    if not marca.is_file():
        zf = (datos or rutas.datos_juegos()) / "dos" / "juegos" / f"{j.id}.zip"
        d.mkdir(parents=True, exist_ok=True)
        with zipfile.ZipFile(zf) as z:
            z.extractall(d)
        marca.write_text("1")
    return d


def comando_dos(carpeta: Path, exe: str = "", pantalla_completa: bool = None):
    db = dosbox()
    if not db:
        raise FileNotFoundError("No está DOSBox: sudo apt install dosbox")
    fs = (not hay_escritorio()) if pantalla_completa is None else pantalla_completa
    cmd = [db, "-c", f'mount c "{carpeta}"', "-c", "c:"]
    if exe:
        cmd += ["-c", exe, "-c", "exit"]
    if fs:
        cmd.append("-fullscreen")
    return cmd


def jugar_dos(j: Juego, datos: Path = None, esperar=True):
    cmd = comando_dos(carpeta_dos(j, datos), j.exe)
    p = subprocess.Popen(cmd, env=entorno_dos(), stdout=subprocess.DEVNULL, stderr=subprocess.PIPE)
    if esperar:
        _, err = p.communicate()
        if p.returncode not in (0, None):
            raise RuntimeError((err or b"").decode(errors="replace")[-400:] or f"DOSBox terminó con {p.returncode}")
    return p


def dos_libre(esperar=True):
    """El DOS de la app: una unidad C: propia del usuario, que se conserva."""
    c = rutas.sub("dos") / "C"
    c.mkdir(parents=True, exist_ok=True)
    p = subprocess.Popen(comando_dos(c), env=entorno_dos(), stdout=subprocess.DEVNULL, stderr=subprocess.PIPE)
    if esperar:
        p.wait()
    return p


# ---------------------------------------------------------------- terminal

def terminal_grafica():
    for t in ("x-terminal-emulator", "gnome-terminal", "konsole", "xfce4-terminal", "xterm"):
        p = shutil.which(t)
        if p:
            return subprocess.Popen([p])
    raise FileNotFoundError("No hay ninguna terminal gráfica instalada")
