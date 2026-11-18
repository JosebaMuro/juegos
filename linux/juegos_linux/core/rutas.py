"""Carpetas de datos: los del paquete (juegos libres) y los del usuario (partidas, modelos)."""
import os
from pathlib import Path

PAQUETE = Path(__file__).resolve().parents[2]  # .../linux (o /opt/suite/juegos)


def datos_juegos() -> Path:
    """Juegos libres preparados por libres/preparar.py (web/, dos/juegos/, libres/)."""
    env = os.environ.get("JUEGOS_DATOS")
    if env:
        return Path(env)
    for c in (PAQUETE / "datos", Path("/opt/suite/juegos/datos"), Path("/usr/share/juegos")):
        if (c / "libres" / "catalogo.json").is_file():
            return c
    return PAQUETE / "datos"


def usuario() -> Path:
    base = os.environ.get("XDG_DATA_HOME") or os.path.join(os.path.expanduser("~"), ".local", "share")
    p = Path(base) / "juegos"
    p.mkdir(parents=True, exist_ok=True)
    return p


def sub(nombre: str) -> Path:
    p = usuario() / nombre
    p.mkdir(parents=True, exist_ok=True)
    return p
