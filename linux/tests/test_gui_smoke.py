"""La versión de escritorio dibuja sin errores (SDL sin pantalla) recorriendo pantallas."""
import os

os.environ["SDL_VIDEODRIVER"] = "dummy"
os.environ["SDL_AUDIODRIVER"] = "dummy"

import pygame  # noqa: E402

from juegos_linux.core import puntocruz as PC  # noqa: E402
from juegos_linux.gui import app as G  # noqa: E402


def key(k, text=None):
    return [pygame.event.Event(pygame.KEYDOWN, key=k, mod=0, unicode=text or "")]


class Guion(G.App):
    def __init__(self, pasos):
        super().__init__(900, 640)
        self.pasos = list(pasos)
        self.frames = 0

    def eventos(self):
        self.frames += 1
        if not self.pasos:
            self.vivo = False
            return []
        return self.pasos.pop(0)


def test_recorrido(tmp_path, monkeypatch):
    monkeypatch.setenv("XDG_DATA_HOME", str(tmp_path))
    from PIL import Image
    img = tmp_path / "foto.png"
    Image.new("RGB", (60, 40), (10, 200, 30)).save(img)
    monkeypatch.setattr(G, "elegir_archivo", lambda *a: str(img))
    pasos = [
        key(pygame.K_RETURN),                      # Bloques
        key(pygame.K_LEFT), key(pygame.K_UP), key(pygame.K_SPACE), [], [],
        key(pygame.K_ESCAPE),
        key(pygame.K_DOWN), key(pygame.K_RETURN),  # Punto de cruz
        key(pygame.K_n),                           # nueva imagen -> colorear
        [pygame.event.Event(pygame.MOUSEBUTTONDOWN, button=1, pos=(30, 70))],
        [pygame.event.Event(pygame.MOUSEBUTTONUP, button=1, pos=(30, 70))],
        [pygame.event.Event(pygame.MOUSEWHEEL, x=0, y=1, flipped=False)],
        key(pygame.K_ESCAPE), [], key(pygame.K_ESCAPE),
        key(pygame.K_DOWN), key(pygame.K_RETURN),  # juegos libres: sin catálogo vuelve con un aviso
        key(pygame.K_ESCAPE),                      # salir
    ]
    a = Guion(pasos)
    a.run()
    assert not a.pasos  # el guion se ha recorrido entero
    guardados = list((tmp_path / "juegos" / "puntocruz").glob("*.json"))
    assert len(guardados) == 1
    p = PC.Patron.cargar(guardados[0])
    assert p.progress()[0] >= 1
