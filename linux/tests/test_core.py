import json
import os
import zipfile
from pathlib import Path

import pytest
from PIL import Image

from juegos_linux.core import bloques as B
from juegos_linux.core import catalogo, chat
from juegos_linux.core import puntocruz as PC


def test_bloques_linea_y_puntos():
    g = B.Bloques(semilla=1)
    # deja una fila casi llena y suelta una barra horizontal en el hueco
    for x in range(B.COLS - 4):
        g.board[(B.ROWS - 1) * B.COLS + x] = 1
    g.type, g.rot, g.px, g.py = 0, 0, B.COLS - 4, 0
    hechas = g.hard_drop()
    assert hechas == 1
    assert g.lines == 1 and g.score >= 100
    assert all(v == 0 for v in g.board[(B.ROWS - 1) * B.COLS:])


def test_bloques_giro_y_fin():
    g = B.Bloques(semilla=2)
    assert len(B.cells(0, 1)) == 4
    for _ in range(300):
        if g.over:
            break
        g.hard_drop()
    assert g.over
    assert len(g.visible()) == B.ROWS


def test_puntocruz_genera_y_guarda(tmp_path):
    im = Image.new("RGB", (80, 40), (255, 0, 0))
    for x in range(40, 80):
        for y in range(40):
            im.putpixel((x, y), (0, 0, 255))
    p = PC.generar(im, ancho=20, colores=4, nombre="prueba")
    assert (p.width, p.height) == (20, 10)
    assert len(p.palette) == 2
    rojo = min(range(2), key=lambda k: abs(p.palette[k][0] - 255) + p.palette[k][2])
    assert p.cells[0] == rojo
    assert p.fill(0, 0, rojo) and not p.fill(0, 0, rojo)
    f = tmp_path / "p.json"
    p.guardar(f)
    q = PC.Patron.cargar(f)
    assert q.done[0] and q.progress() == (1, 200)


def test_color_terminal():
    assert PC.color_terminal((250, 0, 0), 8) == 1
    assert 16 <= PC.color_terminal((10, 200, 30), 256) <= 255


def test_catalogo_y_dos(tmp_path, monkeypatch):
    monkeypatch.setenv("XDG_DATA_HOME", str(tmp_path / "home"))
    datos = tmp_path / "datos"
    (datos / "libres").mkdir(parents=True)
    (datos / "dos" / "juegos").mkdir(parents=True)
    (datos / "web" / "w").mkdir(parents=True)
    (datos / "web" / "w" / "index.html").write_text("<p>hola</p>")
    (datos / "libres" / "catalogo.json").write_text(json.dumps({"juegos": [
        {"id": "w", "tipo": "web", "titulo": "W", "otro": 1},
        {"id": "d", "tipo": "dos", "titulo": "D", "exe": "JUEGO"}]}))
    with zipfile.ZipFile(datos / "dos" / "juegos" / "d.zip", "w") as z:
        z.writestr("JUEGO.EXE", b"MZ")
    js = catalogo.cargar(datos)
    assert [j.id for j in js] == ["w", "d"]
    assert all(catalogo.disponible(j, datos) for j in js)
    c = catalogo.carpeta_dos(js[1], datos)
    assert (c / "JUEGO.EXE").is_file()
    if catalogo.dosbox():
        cmd = catalogo.comando_dos(c, "JUEGO", pantalla_completa=True)
        assert "-fullscreen" in cmd and "JUEGO" in cmd
    import urllib.request
    u = catalogo.url_web(js[0], datos)
    assert urllib.request.urlopen(u).read() == b"<p>hola</p>"


def test_chat_modelos():
    assert chat.recomendado() in chat.MODELOS
    assert all(m.repo.startswith("ggml-org/") for m in chat.MODELOS)
