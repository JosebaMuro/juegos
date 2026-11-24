"""Punto de cruz: convierte una imagen en un patrón y guarda el progreso.

El algoritmo es el de la app de Android (PatternGenerator.java): media por bloques,
k-means++ en CIELab, limpieza de puntadas sueltas y paleta ordenada por uso.
"""
import json
from dataclasses import dataclass, field
from pathlib import Path

import numpy as np
from PIL import Image, ImageOps

SYMBOLS = ["●", "■", "▲", "◆", "★", "○", "□", "△", "◇", "☆", "▼", "+", "×", "=", "#", "%", "@", "&", "?", "!",
           "A", "B", "C", "D", "E", "F", "G", "H", "K", "M", "N", "P", "R", "S", "T", "U",
           "V", "W", "X", "Y", "Z", "2", "3", "4", "5", "6", "7", "8", "9"]
# Símbolos que caben en cualquier consola (la de Linux sin fuente Unicode amplia).
SYMBOLS_ASCII = list("@#%&*+=ox$XO0123456789ABCDEFGHKMNPRSTUVWYZabcdeghkmn")
MAX_COLORS = len(SYMBOLS)
EMPTY = -1


@dataclass
class Patron:
    width: int
    height: int
    cells: list           # índice de paleta por celda, o EMPTY
    palette: list         # [(r, g, b)]
    done: list = field(default_factory=list)
    nombre: str = ""

    def __post_init__(self):
        if not self.done:
            self.done = [False] * len(self.cells)

    def total(self, c):
        return sum(1 for v in self.cells if v == c)

    def remaining(self, c):
        return sum(1 for v, d in zip(self.cells, self.done) if v == c and not d)

    def progress(self):
        tot = sum(1 for v in self.cells if v >= 0)
        hechos = sum(1 for v, d in zip(self.cells, self.done) if v >= 0 and d)
        return hechos, tot

    def fill(self, x, y, color):
        """Colorea la casilla si es de [color]. Devuelve True si cambió."""
        i = y * self.width + x
        if 0 <= x < self.width and 0 <= y < self.height and self.cells[i] == color and not self.done[i]:
            self.done[i] = True
            return True
        return False

    def to_json(self):
        return {"w": self.width, "h": self.height, "cells": self.cells, "palette": self.palette,
                "done": "".join("1" if d else "0" for d in self.done), "nombre": self.nombre}

    @staticmethod
    def from_json(j):
        return Patron(j["w"], j["h"], list(j["cells"]), [tuple(c) for c in j["palette"]],
                      [ch == "1" for ch in j["done"]], j.get("nombre", ""))

    def guardar(self, path: Path):
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(json.dumps(self.to_json()), encoding="utf-8")

    @staticmethod
    def cargar(path: Path):
        return Patron.from_json(json.loads(path.read_text(encoding="utf-8")))


# ---- sRGB <-> CIELab (D65) ----

def _to_lab(rgb):
    c = rgb / 255.0
    lin = np.where(c <= 0.04045, c / 12.92, ((c + 0.055) / 1.055) ** 2.4)
    m = np.array([[0.4124, 0.3576, 0.1805], [0.2126, 0.7152, 0.0722], [0.0193, 0.1192, 0.9505]])
    xyz = lin @ m.T / np.array([0.95047, 1.0, 1.08883])
    f = np.where(xyz > 0.008856, np.cbrt(xyz), 7.787 * xyz + 16 / 116.0)
    return np.stack([116 * f[:, 1] - 16, 500 * (f[:, 0] - f[:, 1]), 200 * (f[:, 1] - f[:, 2])], axis=1)


def _from_lab(lab):
    fy = (lab[0] + 16) / 116.0
    fx = fy + lab[1] / 500.0
    fz = fy - lab[2] / 200.0

    def finv(t):
        return t ** 3 if t > 0.206893 else (t - 16 / 116.0) / 7.787

    x, y, z = finv(fx) * 0.95047, finv(fy), finv(fz) * 1.08883
    rgb = [x * 3.2406 + y * -1.5372 + z * -0.4986, x * -0.9689 + y * 1.8758 + z * 0.0415,
           x * 0.0557 + y * -0.2040 + z * 1.0570]

    def ch(c):
        v = 12.92 * c if c <= 0.0031308 else 1.055 * (max(c, 0) ** (1 / 2.4)) - 0.055
        return int(max(0, min(255, round(v * 255))))

    return tuple(ch(c) for c in rgb)


def _kmeans(pts, k, rnd):
    centers = [pts[rnd.integers(len(pts))]]
    d2 = np.full(len(pts), np.inf)
    for _ in range(1, k):
        d2 = np.minimum(d2, ((pts - centers[-1]) ** 2).sum(1))
        total = d2.sum()
        if total <= 0:
            centers.append(pts[rnd.integers(len(pts))])
            continue
        centers.append(pts[min(len(pts) - 1, int(np.searchsorted(np.cumsum(d2), rnd.random() * total)))])
    centers = np.array(centers, dtype=float)
    assign = np.zeros(len(pts), dtype=int)
    for it in range(20):
        d = ((pts[:, None, :] - centers[None, :, :]) ** 2).sum(2)
        new = d.argmin(1)
        changed = it == 0 or (new != assign).any()
        assign = new
        for c in range(k):
            sel = pts[assign == c]
            if len(sel):
                centers[c] = sel.mean(0)
        if not changed:
            break
    return centers, assign


def _limpiar(cells, w, h):
    """Quita puntadas aisladas: las cambia por el color que domina alrededor."""
    copy = list(cells)
    for y in range(h):
        for x in range(w):
            i = y * w + x
            c = copy[i]
            if c < 0:
                continue
            alone = True
            nb = []
            for dy in (-1, 0, 1):
                for dx in (-1, 0, 1):
                    if dx == 0 and dy == 0:
                        continue
                    nx, ny = x + dx, y + dy
                    if 0 <= nx < w and 0 <= ny < h:
                        v = copy[ny * w + nx]
                        if v == c and (dx == 0 or dy == 0):
                            alone = False
                        if v >= 0:
                            nb.append(v)
            if alone and nb:
                best = max(set(nb), key=nb.count)
                if nb.count(best) >= 3:
                    cells[i] = best


def generar(imagen, ancho=60, colores=12, limpiar=True, nombre="") -> Patron:
    """Patrón de [ancho] puntadas y como mucho [colores] colores a partir de una imagen
    (ruta o PIL.Image)."""
    im = imagen if isinstance(imagen, Image.Image) else Image.open(imagen)
    im = ImageOps.exif_transpose(im).convert("RGBA")
    ancho = max(4, min(ancho, im.width))
    colores = max(2, min(colores, MAX_COLORS))
    alto = max(1, round(im.height * ancho / im.width))
    # 1) media por bloques (BOX = promedio de cada bloque), con la transparencia aparte
    small = np.asarray(im.resize((ancho, alto), Image.BOX), dtype=float)
    n = ancho * alto
    flat = small.reshape(n, 4)
    opaque = flat[:, 3] >= 128
    cells = [EMPTY] * n
    if not opaque.any():
        return Patron(ancho, alto, cells, [], nombre=nombre)
    lab = _to_lab(flat[opaque, :3])
    k = min(colores, int(opaque.sum()))
    centers, assign = _kmeans(lab, k, np.random.default_rng(1234))
    idx = np.flatnonzero(opaque)
    for j, i in enumerate(idx):
        cells[i] = int(assign[j])
    if limpiar:
        _limpiar(cells, ancho, alto)
    # 4) paleta ordenada por número de puntadas
    counts = [cells.count(c) for c in range(k)]
    order = sorted((c for c in range(k) if counts[c] > 0), key=lambda c: -counts[c])
    remap = {c: i for i, c in enumerate(order)}
    palette = [_from_lab(centers[c]) for c in order]
    cells = [remap[c] if c >= 0 else EMPTY for c in cells]
    return Patron(ancho, alto, cells, palette, nombre=nombre)


def color_terminal(rgb, colores=8):
    """Color de terminal más cercano: 8 básicos (consola de Linux) o los 256 de xterm."""
    r, g, b = rgb
    if colores >= 256:
        if abs(r - g) < 10 and abs(g - b) < 10:
            return 232 + min(23, max(0, round((r - 8) / 10)))
        q = [round(v / 255 * 5) for v in (r, g, b)]
        return 16 + 36 * q[0] + 6 * q[1] + q[2]
    base = [(0, 0, 0), (205, 0, 0), (0, 205, 0), (205, 205, 0), (0, 0, 238), (205, 0, 205), (0, 205, 205),
            (229, 229, 229)]
    return min(range(8), key=lambda i: sum((a - c) ** 2 for a, c in zip(rgb, base[i])))
