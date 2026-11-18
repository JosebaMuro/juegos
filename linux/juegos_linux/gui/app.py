"""Juegos Reunidos para el escritorio de Linux (X11 o Wayland) con pygame.

Ratón y teclado: menú, Bloques, Punto de cruz (clic y arrastre para colorear, rueda para
acercar), juegos libres (web en el navegador, DOS en DOSBox), DOS, terminal y chat con IA.
"""
import os
import shutil
import subprocess
import textwrap
import threading
import time
from pathlib import Path

os.environ.setdefault("PYGAME_HIDE_SUPPORT_PROMPT", "1")
import pygame  # noqa: E402

from ..core import bloques as B  # noqa: E402
from ..core import catalogo, chat, rutas  # noqa: E402
from ..core import puntocruz as PC  # noqa: E402

FONDO = (24, 26, 33)
PANEL = (38, 41, 52)
TEXTO = (235, 235, 240)
TENUE = (150, 155, 170)
ACENTO = (255, 152, 0)

MENU = [
    ("Bloques", "Piezas que caen: completa líneas"),
    ("Punto de cruz", "Convierte una foto en patrón y colorea"),
    ("Juegos libres", "2048, Hextris, NetHack, Crawl, IVAN…"),
    ("DOS", "MS-DOS con tu unidad C: propia"),
    ("Terminal", "La shell del sistema"),
    ("Chat con IA", "IA local con visión, sin internet"),
]


def elegir_archivo(titulo="Elige una imagen"):
    """Diálogo del escritorio (zenity o kdialog); devuelve la ruta o ''."""
    for cmd in (["zenity", "--file-selection", f"--title={titulo}",
                 "--file-filter=Imágenes | *.png *.jpg *.jpeg *.webp *.bmp *.gif"],
                ["kdialog", "--getopenfilename", str(Path.home()), "image/png image/jpeg image/webp"]):
        if shutil.which(cmd[0]):
            r = subprocess.run(cmd, capture_output=True, text=True)
            return r.stdout.strip() if r.returncode == 0 else ""
    return ""


class App:
    def __init__(self, ancho=1100, alto=760):
        pygame.init()
        pygame.display.set_caption("Juegos Reunidos")
        self.win = pygame.display.set_mode((ancho, alto), pygame.RESIZABLE)
        self.clock = pygame.time.Clock()
        self.f = pygame.font.SysFont("dejavusans,notosans,freesans,sans", 20)
        self.fb = pygame.font.SysFont("dejavusans,notosans,freesans,sans", 26, bold=True)
        self.fs = pygame.font.SysFont("dejavusans,notosans,freesans,sans", 15)
        self.fsym = pygame.font.SysFont("dejavusans,notosanssymbols2,notosans,sans", 14)
        self.pantalla = self.menu
        self.sel = 0
        self.aviso = ""
        self.aviso_t = 0
        self.vivo = True

    # ------------------------------------------------------------ utilidades

    def txt(self, s, pos, font=None, color=TEXTO, centro=False):
        img = (font or self.f).render(s, True, color)
        r = img.get_rect(center=pos) if centro else img.get_rect(topleft=pos)
        self.win.blit(img, r)
        return r

    def avisar(self, s):
        self.aviso, self.aviso_t = s, time.time()

    def pie(self, ayuda):
        w, h = self.win.get_size()
        pygame.draw.rect(self.win, PANEL, (0, h - 30, w, 30))
        if self.aviso and time.time() - self.aviso_t < 6:
            self.txt(self.aviso, (10, h - 25), self.fs, ACENTO)
        else:
            self.txt(ayuda, (10, h - 25), self.fs, TENUE)

    def cabecera(self, titulo):
        w, _ = self.win.get_size()
        pygame.draw.rect(self.win, (21, 101, 192), (0, 0, w, 44))
        self.txt(f"Juegos Reunidos · {titulo}", (14, 9), self.fb)

    def run(self):
        while self.vivo:
            self.pantalla()
        pygame.quit()

    def eventos(self):
        evs = pygame.event.get()
        for e in evs:
            if e.type == pygame.QUIT:
                self.vivo = False
        return evs

    def fuera(self, func):
        """Ejecuta algo que abre su propia ventana (DOSBox) y espera sin bloquear la nuestra."""
        res = {}

        def run():
            try:
                res["r"] = func()
            except Exception as e:
                res["e"] = e

        th = threading.Thread(target=run, daemon=True)
        th.start()
        while th.is_alive() and self.vivo:
            self.eventos()
            self.win.fill(FONDO)
            self.txt("Jugando en otra ventana… ciérrala para volver.", (self.win.get_width() // 2, 200), centro=True)
            pygame.display.flip()
            self.clock.tick(15)
        if "e" in res:
            self.avisar(str(res["e"]))

    # ------------------------------------------------------------ menú

    def menu(self):
        acciones = [self.bloques, self.puntocruz, self.libres, self.dos, self.terminal, self.chat]
        while self.vivo and self.pantalla == self.menu:
            w, h = self.win.get_size()
            botones = []
            for e in self.eventos():
                if e.type == pygame.KEYDOWN:
                    if e.key in (pygame.K_DOWN, pygame.K_s):
                        self.sel = (self.sel + 1) % len(MENU)
                    elif e.key in (pygame.K_UP, pygame.K_w):
                        self.sel = (self.sel - 1) % len(MENU)
                    elif e.key in (pygame.K_RETURN, pygame.K_KP_ENTER, pygame.K_SPACE):
                        acciones[self.sel]()
                    elif e.key in (pygame.K_ESCAPE, pygame.K_q):
                        self.vivo = False
                elif e.type == pygame.MOUSEBUTTONUP and e.button == 1:
                    for i in range(len(MENU)):
                        if self._caja(i, w).collidepoint(e.pos):
                            self.sel = i
                            acciones[i]()
            self.win.fill(FONDO)
            self.cabecera("Menú")
            for i, (t, d) in enumerate(MENU):
                r = self._caja(i, w)
                botones.append(r)
                hover = r.collidepoint(pygame.mouse.get_pos())
                pygame.draw.rect(self.win, ACENTO if i == self.sel else (PANEL if not hover else (55, 60, 75)), r,
                                 border_radius=12)
                col = (20, 20, 20) if i == self.sel else TEXTO
                self.txt(t, (r.x + 20, r.y + 14), self.fb, col)
                self.txt(d, (r.x + 20, r.y + 50), self.fs, (40, 40, 40) if i == self.sel else TENUE)
            self.pie("Ratón o ↑↓ y Enter · Esc salir")
            pygame.display.flip()
            self.clock.tick(30)

    def _caja(self, i, w):
        col, fila = i % 2, i // 2
        bw = min(420, (w - 90) // 2)
        x0 = (w - 2 * bw - 30) // 2
        return pygame.Rect(x0 + col * (bw + 30), 80 + fila * 110, bw, 90)

    # ------------------------------------------------------------ Bloques

    def bloques(self):
        g = B.Bloques()
        pausa = False
        ultimo = time.time()
        while self.vivo:
            for e in self.eventos():
                if e.type != pygame.KEYDOWN:
                    continue
                if e.key in (pygame.K_ESCAPE, pygame.K_q):
                    return
                if e.key == pygame.K_r:
                    g.reset()
                    pausa = False
                elif e.key == pygame.K_p:
                    pausa = not pausa
                elif pausa or g.over:
                    continue
                elif e.key == pygame.K_LEFT:
                    g.move(-1)
                elif e.key == pygame.K_RIGHT:
                    g.move(1)
                elif e.key in (pygame.K_UP, pygame.K_x):
                    g.rotate()
                elif e.key == pygame.K_DOWN:
                    g.soft_drop()
                    ultimo = time.time()
                elif e.key == pygame.K_SPACE:
                    g.hard_drop()
                    ultimo = time.time()
            if not pausa and not g.over and time.time() - ultimo >= g.drop_interval():
                g.step()
                ultimo = time.time()
            w, h = self.win.get_size()
            self.win.fill(FONDO)
            self.cabecera("Bloques")
            cs = max(12, min((h - 120) // B.ROWS, 34))
            ox, oy = (w - B.COLS * cs) // 2 - 100, 60
            pygame.draw.rect(self.win, PANEL, (ox - 4, oy - 4, B.COLS * cs + 8, B.ROWS * cs + 8), border_radius=6)
            for y, fila in enumerate(g.visible()):
                for x, v in enumerate(fila):
                    r = (ox + x * cs, oy + y * cs, cs - 1, cs - 1)
                    if v > 0:
                        pygame.draw.rect(self.win, B.COLORES[v - 1], r, border_radius=3)
                    elif v < 0:
                        pygame.draw.rect(self.win, (90, 95, 110), r, 1, border_radius=3)
            px = ox + B.COLS * cs + 40
            self.txt("Siguiente", (px, oy), self.fb)
            for cx, cy in B.cells(g.next, 0):
                pygame.draw.rect(self.win, B.COLORES[g.next], (px + cx * cs, oy + 50 + cy * cs, cs - 1, cs - 1),
                                 border_radius=3)
            for k, (et, v) in enumerate((("Puntos", g.score), ("Líneas", g.lines), ("Nivel", g.level))):
                self.txt(f"{et}: {v}", (px, oy + 170 + 34 * k))
            if g.over:
                self.txt("Fin de la partida · R para otra", (px, oy + 290), self.fb, (244, 67, 54))
            elif pausa:
                self.txt("Pausa", (px, oy + 290), self.fb, ACENTO)
            self.pie("←→ mover · ↑ girar · ↓ bajar · Espacio soltar · P pausa · R reiniciar · Esc volver")
            pygame.display.flip()
            self.clock.tick(60)

    # ------------------------------------------------------------ Punto de cruz

    def puntocruz(self):
        carpeta = rutas.sub("puntocruz")
        ancho, colores = 60, 12
        while self.vivo:
            guardados = sorted(carpeta.glob("*.json"), key=lambda p: -p.stat().st_mtime)
            w, h = self.win.get_size()
            filas = []
            for e in self.eventos():
                if e.type == pygame.KEYDOWN:
                    if e.key in (pygame.K_ESCAPE, pygame.K_q):
                        return
                    if e.key == pygame.K_n:
                        self._nuevo_patron(carpeta, ancho, colores)
                    elif e.key in (pygame.K_LEFT, pygame.K_RIGHT):
                        ancho = max(20, min(150, ancho + (10 if e.key == pygame.K_RIGHT else -10)))
                    elif e.key in (pygame.K_UP, pygame.K_DOWN):
                        colores = max(2, min(49, colores + (1 if e.key == pygame.K_UP else -1)))
                elif e.type == pygame.MOUSEBUTTONUP and e.button == 1:
                    if pygame.Rect(30, 60, 260, 50).collidepoint(e.pos):
                        self._nuevo_patron(carpeta, ancho, colores)
                    for k, p in enumerate(guardados[:12]):
                        r = pygame.Rect(30, 190 + k * 44, w - 60, 38)
                        if r.collidepoint(e.pos):
                            self._colorear(PC.Patron.cargar(p), p)
            self.win.fill(FONDO)
            self.cabecera("Punto de cruz")
            pygame.draw.rect(self.win, ACENTO, (30, 60, 260, 50), border_radius=10)
            self.txt("Nuevo desde imagen (N)", (45, 73), self.f, (20, 20, 20))
            self.txt(f"Ancho: {ancho} puntadas (←→)   Colores: {colores} (↑↓)", (320, 75), self.f, TENUE)
            self.txt("Patrones guardados", (30, 150), self.fb)
            for k, p in enumerate(guardados[:12]):
                try:
                    pat = PC.Patron.cargar(p)
                    hecho, tot = pat.progress()
                    s = f"{pat.nombre or p.stem} · {pat.width}×{pat.height} · {len(pat.palette)} colores · " \
                        f"{100 * hecho // max(1, tot)} %"
                except Exception:
                    s = f"{p.stem} (dañado)"
                r = pygame.Rect(30, 190 + k * 44, w - 60, 38)
                filas.append(r)
                pygame.draw.rect(self.win, PANEL, r, border_radius=8)
                self.txt(s, (r.x + 14, r.y + 8))
            self.pie("Clic en un patrón para colorearlo · Esc volver")
            pygame.display.flip()
            self.clock.tick(30)

    def _nuevo_patron(self, carpeta, ancho, colores):
        ruta = elegir_archivo()
        if not ruta:
            self.avisar("No se eligió imagen (instala zenity para el diálogo de archivos).")
            return
        try:
            pat = PC.generar(ruta, ancho, colores, nombre=Path(ruta).stem)
        except Exception as e:
            self.avisar(f"No se pudo abrir la imagen: {e}")
            return
        path = carpeta / f"{Path(ruta).stem}-{int(time.time())}.json"
        pat.guardar(path)
        self._colorear(pat, path)

    def _colorear(self, pat, path):
        color = 0
        cs = 18
        ox, oy = 20, 60
        arrastre = None
        pintando = False
        while self.vivo:
            w, h = self.win.get_size()
            for e in self.eventos():
                if e.type == pygame.KEYDOWN:
                    if e.key in (pygame.K_ESCAPE, pygame.K_q):
                        pat.guardar(path)
                        return
                    if e.key == pygame.K_RIGHTBRACKET or e.key == pygame.K_TAB:
                        color = (color + 1) % max(1, len(pat.palette))
                    elif e.key == pygame.K_LEFTBRACKET:
                        color = (color - 1) % max(1, len(pat.palette))
                elif e.type == pygame.MOUSEWHEEL:
                    mx, my = pygame.mouse.get_pos()
                    nc = max(6, min(48, cs + e.y * 2))
                    ox = mx - (mx - ox) * nc // cs
                    oy = my - (my - oy) * nc // cs
                    cs = nc
                elif e.type == pygame.MOUSEBUTTONDOWN:
                    if e.button == 1:
                        if e.pos[1] > h - 90:
                            k = self._paleta_hit(pat, e.pos, w, h)
                            if k is not None:
                                color = k
                            continue
                        pintando = True
                        x, y = (e.pos[0] - ox) // cs, (e.pos[1] - oy) // cs
                        if 0 <= x < pat.width and 0 <= y < pat.height:
                            c = pat.cells[y * pat.width + x]
                            if c >= 0 and c != color and not pat.done[y * pat.width + x]:
                                color = c
                            pat.fill(x, y, color)
                    elif e.button in (2, 3):
                        arrastre = (e.pos, ox, oy)
                elif e.type == pygame.MOUSEBUTTONUP:
                    pintando = False
                    arrastre = None
                elif e.type == pygame.MOUSEMOTION:
                    if arrastre:
                        (sx, sy), o0x, o0y = arrastre
                        ox, oy = o0x + e.pos[0] - sx, o0y + e.pos[1] - sy
                    elif pintando:
                        pat.fill((e.pos[0] - ox) // cs, (e.pos[1] - oy) // cs, color)
            self.win.fill((250, 248, 240))
            for y in range(pat.height):
                sy = oy + y * cs
                if sy < 44 - cs or sy > h - 90:
                    continue
                for x in range(pat.width):
                    sx = ox + x * cs
                    if sx < -cs or sx > w:
                        continue
                    i = y * pat.width + x
                    c = pat.cells[i]
                    if c < 0:
                        continue
                    r = (sx, sy, cs - 1, cs - 1)
                    if pat.done[i]:
                        pygame.draw.rect(self.win, pat.palette[c], r)
                    else:
                        fondo = (190, 215, 255) if c == color else (235, 233, 225)
                        pygame.draw.rect(self.win, fondo, r)
                        if cs >= 12:
                            img = self.fsym.render(PC.SYMBOLS[c], True, (60, 60, 60))
                            self.win.blit(img, img.get_rect(center=(sx + cs // 2, sy + cs // 2)))
            hecho, tot = pat.progress()
            self.cabecera(f"{pat.nombre} · {100 * hecho // max(1, tot)} %")
            pygame.draw.rect(self.win, PANEL, (0, h - 90, w, 60))
            for k, rect in self._paleta(pat, w, h):
                pygame.draw.rect(self.win, pat.palette[k], rect, border_radius=6)
                if k == color:
                    pygame.draw.rect(self.win, ACENTO, rect.inflate(6, 6), 3, border_radius=8)
                rest = pat.remaining(k)
                lum = sum(pat.palette[k]) / 3
                self.txt(f"{PC.SYMBOLS[k]} {rest}" if rest else "✓", (rect.x + 6, rect.y + 10), self.fs,
                         (0, 0, 0) if lum > 128 else (255, 255, 255))
            self.pie("Clic/arrastrar colorear · rueda zoom · botón derecho mover · Tab/[ ] color · Esc guardar y salir")
            pygame.display.flip()
            self.clock.tick(60)

    def _paleta(self, pat, w, h):
        out = []
        x = 10
        for k in range(len(pat.palette)):
            r = pygame.Rect(x, h - 82, 64, 44)
            out.append((k, r))
            x += 70
            if x > w - 70:
                break
        return out

    def _paleta_hit(self, pat, pos, w, h):
        for k, r in self._paleta(pat, w, h):
            if r.collidepoint(pos):
                return k
        return None

    # ------------------------------------------------------------ juegos libres, DOS, terminal

    def libres(self):
        juegos = catalogo.cargar()
        if not juegos:
            self.avisar("No están los juegos libres (los prepara el instalador con libres/preparar.py).")
            return
        sel = 0
        top = 0
        while self.vivo:
            w, h = self.win.get_size()
            visibles = max(1, (h - 200) // 40)
            for e in self.eventos():
                if e.type == pygame.KEYDOWN:
                    if e.key in (pygame.K_ESCAPE, pygame.K_q):
                        return
                    if e.key == pygame.K_DOWN:
                        sel = min(len(juegos) - 1, sel + 1)
                    elif e.key == pygame.K_UP:
                        sel = max(0, sel - 1)
                    elif e.key in (pygame.K_RETURN, pygame.K_KP_ENTER):
                        self._jugar(juegos[sel])
                elif e.type == pygame.MOUSEWHEEL:
                    top = max(0, min(len(juegos) - visibles, top - e.y))
                elif e.type == pygame.MOUSEBUTTONUP and e.button == 1:
                    for k in range(visibles):
                        if pygame.Rect(30, 60 + k * 40, w - 60, 36).collidepoint(e.pos) and top + k < len(juegos):
                            if sel == top + k:
                                self._jugar(juegos[sel])
                            sel = top + k
            top = min(max(top, sel - visibles + 1), sel)
            self.win.fill(FONDO)
            self.cabecera("Juegos libres")
            for k, j in enumerate(juegos[top: top + visibles]):
                i = top + k
                r = pygame.Rect(30, 60 + k * 40, w - 60, 36)
                pygame.draw.rect(self.win, ACENTO if i == sel else PANEL, r, border_radius=8)
                col = (20, 20, 20) if i == sel else TEXTO
                ok = catalogo.disponible(j)
                self.txt(f"{j.titulo}  ·  {'DOS' if j.tipo == 'dos' else 'web'}{'' if ok else ' (no incluido)'}",
                         (r.x + 12, r.y + 7), self.f, col)
            j = juegos[sel]
            for n, l in enumerate(textwrap.wrap(f"{j.descripcion}  Controles: {j.controles}  Autor: {j.autor} · "
                                                f"{j.licencia}", max(40, (w - 60) // 9))[:4]):
                self.txt(l, (30, h - 130 + n * 22), self.fs, TENUE)
            self.pie("Doble clic o Enter para jugar · Esc volver")
            pygame.display.flip()
            self.clock.tick(30)

    def _jugar(self, j):
        if not catalogo.disponible(j):
            self.avisar("Este juego no está en el paquete.")
        elif j.tipo == "web":
            u = catalogo.abrir_web(j)
            self.avisar(f"Abierto en el navegador: {u}")
        elif not catalogo.dosbox():
            self.avisar("No está DOSBox: sudo apt install dosbox")
        else:
            self.fuera(lambda: catalogo.jugar_dos(j))

    def dos(self):
        if not catalogo.dosbox():
            self.avisar("No está DOSBox: sudo apt install dosbox")
            return
        self.fuera(catalogo.dos_libre)

    def terminal(self):
        try:
            catalogo.terminal_grafica()
        except Exception as e:
            self.avisar(str(e))

    # ------------------------------------------------------------ chat

    def chat(self):
        listos = [m for m in chat.MODELOS if chat.instalado(m)]
        m = chat.recomendado()
        estado = {"t": "", "motor": None, "err": None, "msgs": [], "vista": [], "busy": False}
        if m not in listos and listos:
            m = listos[-1]

        def preparar():
            try:
                if not chat.instalado(m):
                    chat.descargar(m, lambda a, b, q: estado.__setitem__("t", f"Descargando {q}: {a >> 20} de {b >> 20} MB"))
                estado["t"] = f"Cargando {m.nombre}…"
                mo = chat.Motor(m)
                mo.arrancar()
                estado["motor"] = mo
                estado["t"] = ""
            except Exception as e:
                estado["err"] = str(e)

        threading.Thread(target=preparar, daemon=True).start()
        estado["msgs"] = [{"role": "system", "content": "Eres un asistente amable. Responde en español, claro y breve."}]
        entrada = ""
        imagen = None
        try:
            while self.vivo:
                w, h = self.win.get_size()
                for e in self.eventos():
                    if e.type == pygame.KEYDOWN:
                        if e.key == pygame.K_ESCAPE:
                            return
                        if e.key in (pygame.K_RETURN, pygame.K_KP_ENTER) and entrada.strip() and estado["motor"] \
                                and not estado["busy"]:
                            self._enviar(estado, entrada.strip(), imagen)
                            entrada, imagen = "", None
                        elif e.key == pygame.K_BACKSPACE:
                            entrada = entrada[:-1]
                        elif e.key == pygame.K_i and e.mod & pygame.KMOD_CTRL:
                            imagen = elegir_archivo() or None
                    elif e.type == pygame.TEXTINPUT:
                        entrada += e.text
                self.win.fill(FONDO)
                mo = estado["motor"]
                self.cabecera(f"Chat con IA · {m.nombre}" + (f" · {mo.variante.upper()}" if mo else ""))
                y = h - 110
                lineas = []
                for rol, t in estado["vista"]:
                    for k, l in enumerate(textwrap.wrap(t, max(30, (w - 80) // 10)) or [""]):
                        lineas.append(((("Tú: " if rol == "user" else "IA: ") if k == 0 else "      ") + l, rol))
                for l, rol in reversed(lineas):
                    if y < 60:
                        break
                    self.txt(l, (30, y), self.f, ACENTO if rol == "user" else TEXTO)
                    y -= 26
                if estado["err"]:
                    for n, l in enumerate(textwrap.wrap(estado["err"], 90)[:6]):
                        self.txt(l, (30, 70 + n * 24), self.fs, (244, 67, 54))
                elif estado["t"]:
                    self.txt(estado["t"], (30, 70), self.f, TENUE)
                pygame.draw.rect(self.win, PANEL, (20, h - 76, w - 40, 40), border_radius=8)
                cur = "|" if int(time.time() * 2) % 2 else " "
                self.txt((f"[{Path(imagen).name}] " if imagen else "") + entrada + cur, (32, h - 68))
                self.pie("Enter enviar · Ctrl+I adjuntar imagen · Esc salir")
                pygame.display.flip()
                self.clock.tick(30)
        finally:
            if estado["motor"]:
                estado["motor"].parar()

    def _enviar(self, estado, texto, imagen):
        estado["msgs"].append({"role": "user", "content": texto})
        estado["vista"].append(("user", texto + (f" [imagen {Path(imagen).name}]" if imagen else "")))
        estado["vista"].append(("assistant", "…"))
        estado["busy"] = True

        def run():
            try:
                r = estado["motor"].chat(estado["msgs"], imagen,
                                         lambda t: estado["vista"].__setitem__(-1, ("assistant", t)))
                estado["msgs"].append({"role": "assistant", "content": r})
            except Exception as e:
                estado["vista"][-1] = ("assistant", f"(error: {e})")
                estado["msgs"].pop()
            estado["busy"] = False

        threading.Thread(target=run, daemon=True).start()


def main():
    App().run()
