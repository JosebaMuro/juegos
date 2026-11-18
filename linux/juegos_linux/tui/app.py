"""Juegos Reunidos en la terminal (curses): funciona en la consola de texto de Linux,
sin escritorio, y en cualquier terminal (xterm, GNOME Terminal, SSH…)."""
import curses
import locale
import os
import subprocess
import textwrap
import threading
import time
from pathlib import Path

from ..core import bloques as B
from ..core import catalogo, chat, rutas
from ..core import puntocruz as PC

locale.setlocale(locale.LC_ALL, "")
ASCII = os.environ.get("TERM") == "linux" or os.environ.get("JUEGOS_ASCII") == "1"

MENU = [
    ("Bloques", "Piezas que caen: completa líneas."),
    ("Punto de cruz", "Convierte una foto en patrón y colorea punto a punto."),
    ("Juegos libres", "Juegos de DOS (NetHack, Crawl, IVAN…) a pantalla completa."),
    ("DOS", "MS-DOS con tu propia unidad C: que se conserva."),
    ("Terminal", "La shell del sistema (exit para volver)."),
    ("Chat con IA", "IA local con visión, sin internet (llama.cpp)."),
    ("Salir", ""),
]


class App:
    def __init__(self, scr):
        self.scr = scr
        curses.curs_set(0)
        scr.keypad(True)
        curses.start_color()
        try:
            curses.use_default_colors()
            self.bg = -1
        except curses.error:
            self.bg = curses.COLOR_BLACK
        self.ncolors = 256 if curses.COLORS >= 256 and not ASCII else 8
        self._pairs = {}
        self.sel = 0

    # ------------------------------------------------------------ utilidades

    def pair(self, fg, bg=None):
        bg = self.bg if bg is None else bg
        key = (fg, bg)
        if key not in self._pairs:
            n = len(self._pairs) + 1
            if n >= curses.COLOR_PAIRS:
                return 0
            curses.init_pair(n, fg, bg)
            self._pairs[key] = n
        return curses.color_pair(self._pairs[key])

    def put(self, y, x, s, attr=0):
        h, w = self.scr.getmaxyx()
        if 0 <= y < h and x < w:
            try:
                self.scr.addstr(y, max(0, x), s[: max(0, w - x - 1)], attr)
            except curses.error:
                pass

    def header(self, titulo, ayuda):
        h, w = self.scr.getmaxyx()
        self.put(0, 0, f" JUEGOS REUNIDOS · {titulo} ".ljust(w), curses.A_BOLD | self.pair(curses.COLOR_WHITE, curses.COLOR_BLUE))
        self.put(h - 1, 0, f" {ayuda}".ljust(w), self.pair(curses.COLOR_BLACK, curses.COLOR_WHITE))

    def pedir(self, y, x, pregunta, defecto=""):
        """Línea de texto con eco (rutas, números…)."""
        curses.echo()
        curses.curs_set(1)
        self.put(y, x, pregunta, curses.A_BOLD)
        self.scr.clrtoeol()
        if defecto:
            self.put(y + 1, x, f"(Enter = {defecto})", curses.A_DIM)
        self.scr.move(y, x + len(pregunta))
        try:
            s = self.scr.getstr(y, x + len(pregunta), 300).decode("utf-8", "replace").strip()
        except Exception:
            s = ""
        curses.noecho()
        curses.curs_set(0)
        return s or defecto

    def mensaje(self, texto, titulo="Aviso"):
        self.scr.erase()
        self.header(titulo, "Una tecla para volver")
        _, w = self.scr.getmaxyx()
        for i, l in enumerate(textwrap.wrap(texto, max(20, w - 4)) or [""]):
            self.put(2 + i, 2, l)
        self.scr.refresh()
        self.scr.getch()

    def fuera(self, cmd_o_func):
        """Sale de curses para ejecutar algo a pantalla completa (DOSBox, shell) y vuelve."""
        curses.def_prog_mode()
        curses.endwin()
        try:
            r = cmd_o_func() if callable(cmd_o_func) else subprocess.call(cmd_o_func)
        finally:
            curses.reset_prog_mode()
            self.scr.clear()
            self.scr.refresh()
        return r

    def lista(self, titulo, items, ayuda, sel=0, extra=None):
        """Lista con ↑↓ y Enter. Devuelve (índice, tecla) o (None, tecla) al salir."""
        while True:
            self.scr.erase()
            self.header(titulo, ayuda)
            h, w = self.scr.getmaxyx()
            top = max(0, sel - (h - 6))
            for i, (t, d) in enumerate(items[top: top + h - 4]):
                k = top + i
                attr = curses.A_REVERSE | curses.A_BOLD if k == sel else 0
                self.put(2 + i, 2, f" {t} ".ljust(28), attr)
                self.put(2 + i, 32, d, curses.A_DIM)
            if extra:
                extra(sel)
            self.scr.refresh()
            c = self.scr.getch()
            if c in (curses.KEY_UP, ord("k")):
                sel = (sel - 1) % len(items)
            elif c in (curses.KEY_DOWN, ord("j")):
                sel = (sel + 1) % len(items)
            elif c in (curses.KEY_ENTER, 10, 13):
                return sel, c
            elif c in (27, ord("q")):
                return None, c
            elif ord("1") <= c <= ord("9") and c - ord("1") < len(items):
                return c - ord("1"), c
            else:
                return sel, c

    # ------------------------------------------------------------ menú

    def run(self):
        enter = (10, 13, curses.KEY_ENTER)
        acciones = [self.bloques, self.puntocruz, self.libres, self.dos, self.terminal, self.chat]
        while True:
            ayuda = "Flechas · Enter abrir · q salir" if ASCII else "↑↓ elegir · Enter abrir · 1-7 · q salir"
            i, c = self.lista("Menú", MENU, ayuda, self.sel)
            if i is None:
                return
            self.sel = i
            if c not in enter and not ord("1") <= c <= ord("9"):
                continue
            if i == len(MENU) - 1:
                return
            acciones[i]()

    # ------------------------------------------------------------ Bloques

    def bloques(self):
        g = B.Bloques()
        pausa = False
        ultimo = time.time()
        self.scr.timeout(30)
        lleno = "[]" if ASCII else "██"
        sombra = "::" if ASCII else "░░"
        try:
            while True:
                if not pausa and not g.over and time.time() - ultimo >= g.drop_interval():
                    g.step()
                    ultimo = time.time()
                self.scr.erase()
                self.header("Bloques", "←→ mover · ↑ girar · ↓ bajar · Espacio soltar · p pausa · r reiniciar · q salir")
                h, w = self.scr.getmaxyx()
                ox = max(2, (w - B.COLS * 2 - 24) // 2)
                oy = 2
                for y, fila in enumerate(g.visible()):
                    self.put(oy + y, ox - 1, "|" if ASCII else "│")
                    for x, v in enumerate(fila):
                        if v > 0:
                            self.put(oy + y, ox + 2 * x, lleno, self.pair(B.COLORES_TERM[v - 1]) | curses.A_BOLD)
                        elif v < 0:
                            self.put(oy + y, ox + 2 * x, sombra, curses.A_DIM)
                        else:
                            self.put(oy + y, ox + 2 * x, " ." if ASCII else " ·", curses.A_DIM)
                    self.put(oy + y, ox + B.COLS * 2, "|" if ASCII else "│")
                self.put(oy + B.ROWS, ox - 1, ("+" + "-" * (B.COLS * 2) + "+") if ASCII else ("└" + "─" * (B.COLS * 2) + "┘"))
                px = ox + B.COLS * 2 + 4
                self.put(oy, px, "Siguiente", curses.A_BOLD)
                for cx, cy in B.cells(g.next, 0):
                    self.put(oy + 2 + cy, px + 2 * cx, lleno, self.pair(B.COLORES_TERM[g.next]) | curses.A_BOLD)
                self.put(oy + 6, px, f"Puntos  {g.score}", curses.A_BOLD)
                self.put(oy + 7, px, f"Líneas  {g.lines}")
                self.put(oy + 8, px, f"Nivel   {g.level}")
                if g.over:
                    self.put(oy + 10, px, "FIN DE LA PARTIDA", curses.A_BOLD | self.pair(curses.COLOR_RED))
                    self.put(oy + 11, px, "r para jugar otra")
                elif pausa:
                    self.put(oy + 10, px, "PAUSA", curses.A_BOLD | self.pair(curses.COLOR_YELLOW))
                self.scr.refresh()
                c = self.scr.getch()
                if c == -1:
                    continue
                if c in (ord("q"), 27):
                    return
                if c == ord("r"):
                    g.reset()
                    pausa = False
                elif c == ord("p"):
                    pausa = not pausa
                elif pausa or g.over:
                    continue
                elif c == curses.KEY_LEFT:
                    g.move(-1)
                elif c == curses.KEY_RIGHT:
                    g.move(1)
                elif c in (curses.KEY_UP, ord("x")):
                    g.rotate()
                elif c == curses.KEY_DOWN:
                    g.soft_drop()
                    ultimo = time.time()
                elif c == ord(" "):
                    g.hard_drop()
                    ultimo = time.time()
        finally:
            self.scr.timeout(-1)

    # ------------------------------------------------------------ Punto de cruz

    def puntocruz(self):
        carpeta = rutas.sub("puntocruz")
        while True:
            guardados = sorted(carpeta.glob("*.json"), key=lambda p: -p.stat().st_mtime)
            items = [("Nuevo desde una imagen", "JPG, PNG…")]
            for p in guardados:
                try:
                    pat = PC.Patron.cargar(p)
                    hecho, tot = pat.progress()
                    items.append((pat.nombre or p.stem, f"{pat.width}×{pat.height} · {len(pat.palette)} colores · "
                                  f"{100 * hecho // max(1, tot)} % hecho"))
                except Exception:
                    items.append((p.stem, "(dañado)"))
            i, c = self.lista("Punto de cruz", items, "Enter abrir · d borrar · q volver")
            if i is None:
                return
            if c == ord("d") and i > 0:
                guardados[i - 1].unlink()
                continue
            if c not in (10, 13, curses.KEY_ENTER):
                continue
            if i == 0:
                self.scr.erase()
                self.header("Punto de cruz · nuevo", "Escribe y pulsa Enter")
                ruta = self.pedir(3, 2, "Imagen: ")
                ruta = os.path.expanduser(ruta)
                if not ruta or not Path(ruta).is_file():
                    self.mensaje(f"No encuentro la imagen «{ruta}».")
                    continue
                try:
                    ancho = int(self.pedir(5, 2, "Ancho en puntadas (20-150): ", "60"))
                    colores = int(self.pedir(7, 2, "Colores (2-49): ", "12"))
                except ValueError:
                    ancho, colores = 60, 12
                self.put(9, 2, "Calculando el patrón…", curses.A_BOLD)
                self.scr.refresh()
                try:
                    pat = PC.generar(ruta, max(20, min(150, ancho)), max(2, min(49, colores)), nombre=Path(ruta).stem)
                except Exception as e:
                    self.mensaje(f"No se pudo abrir la imagen: {e}")
                    continue
                path = carpeta / f"{Path(ruta).stem}-{int(time.time())}.json"
                pat.guardar(path)
            else:
                path = guardados[i - 1]
                pat = PC.Patron.cargar(path)
            self._colorear(pat, path)

    def _colorear(self, pat, path):
        sym = PC.SYMBOLS_ASCII if ASCII else PC.SYMBOLS
        term = [PC.color_terminal(c, self.ncolors) for c in pat.palette]
        cx = cy = 0
        color = 0
        vx = vy = 0
        while True:
            self.scr.erase()
            hecho, tot = pat.progress()
            self.header(f"{pat.nombre} · {100 * hecho // max(1, tot)} %",
                        "flechas mover · Espacio colorear · [ ] color · f rellenar color · q guardar y salir")
            h, w = self.scr.getmaxyx()
            vw, vh = max(4, (w - 2) // 2), max(4, h - 5)
            vx = min(max(vx, cx - vw + 1), cx)
            vy = min(max(vy, cy - vh + 1), cy)
            for y in range(vy, min(pat.height, vy + vh)):
                for x in range(vx, min(pat.width, vx + vw)):
                    i = y * pat.width + x
                    c = pat.cells[i]
                    sx, sy = 1 + 2 * (x - vx), 2 + (y - vy)
                    if c < 0:
                        s, a = "  ", 0
                    elif pat.done[i]:
                        s, a = "██" if not ASCII else "##", self.pair(term[c])
                    else:
                        s = sym[c % len(sym)] + " "
                        a = curses.A_BOLD | (curses.A_REVERSE if c == color else curses.A_DIM)
                    if x == cx and y == cy:
                        a |= curses.A_STANDOUT
                        s = "<>" if c < 0 or pat.done[i] else s
                    self.put(sy, sx, s, a)
            # paleta
            line = ""
            self.put(h - 2, 1, "Color: ", curses.A_BOLD)
            px = 8
            for k in range(len(pat.palette)):
                rest = pat.remaining(k)
                lab = f"{sym[k % len(sym)]}{rest}" if rest else ("✓" if not ASCII else "ok")
                attr = self.pair(term[k]) | (curses.A_REVERSE | curses.A_BOLD if k == color else 0)
                self.put(h - 2, px, f" {lab} ", attr)
                px += len(lab) + 3
                if px > w - 6:
                    break
            del line
            self.scr.refresh()
            c = self.scr.getch()
            if c in (ord("q"), 27):
                pat.guardar(path)
                return
            if c == curses.KEY_LEFT:
                cx = max(0, cx - 1)
            elif c == curses.KEY_RIGHT:
                cx = min(pat.width - 1, cx + 1)
            elif c == curses.KEY_UP:
                cy = max(0, cy - 1)
            elif c == curses.KEY_DOWN:
                cy = min(pat.height - 1, cy + 1)
            elif c == ord("]"):
                color = (color + 1) % max(1, len(pat.palette))
            elif c == ord("["):
                color = (color - 1) % max(1, len(pat.palette))
            elif c in (ord(" "), 10, 13):
                i = cy * pat.width + cx
                if pat.cells[i] >= 0 and pat.cells[i] != color and not pat.done[i]:
                    color = pat.cells[i]  # toca otro símbolo: cambia al color de esa casilla
                pat.fill(cx, cy, color)
            elif c == ord("f"):
                for y in range(pat.height):
                    for x in range(pat.width):
                        pat.fill(x, y, color)
            if c in (ord(" "), ord("f")) and pat.progress()[0] % 25 == 0:
                pat.guardar(path)

    # ------------------------------------------------------------ juegos libres y DOS

    def libres(self):
        juegos = catalogo.cargar()
        if not juegos:
            self.mensaje("No están los juegos libres. Se preparan con libres/preparar.py (lo hace el instalador).")
            return
        sel = 0
        while True:
            items = []
            for j in juegos:
                tipo = "DOS" if j.tipo == "dos" else "web (solo escritorio)"
                ok = catalogo.disponible(j)
                items.append((j.titulo, f"{tipo}{'' if ok else ' · no incluido'} · {j.licencia}"))

            def detalle(k):
                h, w = self.scr.getmaxyx()
                j = juegos[k]
                for n, l in enumerate(textwrap.wrap(f"{j.descripcion} Controles: {j.controles} Autor: {j.autor}.",
                                                    max(20, w - 4))[:3]):
                    self.put(h - 5 + n, 2, l, curses.A_DIM)

            i, c = self.lista("Juegos libres", items, "Enter jugar · l licencia · q volver", sel, detalle)
            if i is None:
                return
            sel = i
            j = juegos[i]
            if c == ord("l"):
                self.texto(f"Licencia · {j.titulo}", catalogo.licencia(j))
                continue
            if c not in (10, 13, curses.KEY_ENTER):
                continue
            if j.tipo == "web":
                if catalogo.hay_escritorio():
                    catalogo.abrir_web(j)
                else:
                    self.mensaje("Los juegos web necesitan un navegador: ábrelos desde el escritorio (X11).")
                continue
            self._dos(lambda: catalogo.jugar_dos(j))

    def dos(self):
        self._dos(catalogo.dos_libre)

    def _dos(self, func):
        if not catalogo.dosbox():
            self.mensaje("No está DOSBox. Instálalo con: sudo apt install dosbox")
            return
        try:
            self.fuera(func)
        except Exception as e:
            self.mensaje(f"DOSBox no pudo arrancar: {e}\n\nSin escritorio usa la pantalla directamente (KMS): "
                         "tu usuario debe estar en el grupo video.")

    def terminal(self):
        sh = os.environ.get("SHELL", "/bin/bash")
        self.fuera(lambda: subprocess.call([sh], env={**os.environ, "PS1": "(juegos) \\w $ "}))

    def texto(self, titulo, texto):
        lineas = []
        _, w = self.scr.getmaxyx()
        for p in texto.splitlines():
            lineas += textwrap.wrap(p, max(20, w - 4)) or [""]
        top = 0
        while True:
            self.scr.erase()
            self.header(titulo, "↑↓ RePág AvPág · q volver")
            h, _ = self.scr.getmaxyx()
            for n, l in enumerate(lineas[top: top + h - 3]):
                self.put(2 + n, 2, l)
            self.scr.refresh()
            c = self.scr.getch()
            if c in (ord("q"), 27):
                return
            top = {curses.KEY_UP: top - 1, curses.KEY_DOWN: top + 1, curses.KEY_PPAGE: top - (h - 4),
                   curses.KEY_NPAGE: top + (h - 4)}.get(c, top)
            top = max(0, min(top, max(0, len(lineas) - (h - 3))))

    # ------------------------------------------------------------ chat

    def chat(self):
        listos = [m for m in chat.MODELOS if chat.instalado(m)]
        rec = chat.recomendado()
        if not listos:
            i, c = self.lista("Chat con IA · descargar modelo",
                              [(m.nombre, f"{m.aprox_gb} GB{' · recomendado' if m is rec else ''}") for m in chat.MODELOS],
                              "Enter descargar (una vez, de Hugging Face) · q volver", chat.MODELOS.index(rec))
            if i is None or c not in (10, 13, curses.KEY_ENTER):
                return
            m = chat.MODELOS[i]
            estado = {"t": "Conectando…"}

            def prog(hecho, total, que):
                estado["t"] = f"{que}: {hecho / 2**20:.0f} de {total / 2**20:.0f} MB"

            err = []
            th = threading.Thread(target=lambda: self._try(lambda: chat.descargar(m, prog), err), daemon=True)
            th.start()
            while th.is_alive():
                self.scr.erase()
                self.header("Chat con IA · descargando", "Espera…")
                self.put(3, 2, m.nombre, curses.A_BOLD)
                self.put(5, 2, estado["t"])
                self.scr.refresh()
                time.sleep(0.3)
            if err:
                self.mensaje(f"No se pudo descargar: {err[0]}")
                return
            listos = [m]
        m = rec if rec in listos else listos[-1]
        motor = chat.Motor(m)
        err = []
        th = threading.Thread(target=lambda: self._try(motor.arrancar, err), daemon=True)
        th.start()
        n = 0
        while th.is_alive():
            self.scr.erase()
            self.header("Chat con IA", "Cargando el modelo…")
            giro = "|/-\\"[n % 4]
            self.put(3, 2, f"Cargando {m.nombre} {giro}")
            self.scr.refresh()
            n += 1
            time.sleep(0.2)
        if err:
            self.mensaje(str(err[0]))
            return
        try:
            self._conversacion(motor)
        finally:
            motor.parar()

    @staticmethod
    def _try(f, err):
        try:
            f()
        except Exception as e:
            err.append(e)

    def _conversacion(self, motor):
        msgs = [{"role": "system", "content": "Eres un asistente amable. Responde en español, de forma clara y breve."}]
        vista = []
        while True:
            self.scr.erase()
            self.header(f"Chat con IA · {motor.m.nombre} · {motor.variante.upper()}",
                        "Escribe y Enter · /imagen RUTA pregunta · /nuevo · /salir")
            h, w = self.scr.getmaxyx()
            lines = []
            for rol, t in vista:
                pref = "Tú: " if rol == "user" else "IA: "
                for k, l in enumerate(textwrap.wrap(t, max(20, w - 8)) or [""]):
                    lines.append(((pref if k == 0 else "    ") + l, rol))
            for n, (l, rol) in enumerate(lines[-(h - 5):]):
                self.put(2 + n, 2, l, curses.A_BOLD if rol == "user" else 0)
            self.scr.refresh()
            s = self.pedir(h - 2, 2, "> ")
            if not s:
                continue
            if s in ("/salir", "/q"):
                return
            if s == "/nuevo":
                msgs, vista = msgs[:1], []
                continue
            imagen = None
            if s.startswith("/imagen "):
                partes = s.split(maxsplit=2)
                imagen = os.path.expanduser(partes[1]) if len(partes) > 1 else None
                s = partes[2] if len(partes) > 2 else "Describe la imagen."
                if not imagen or not Path(imagen).is_file():
                    vista.append(("assistant", f"(no encuentro la imagen {imagen})"))
                    continue
            msgs.append({"role": "user", "content": s})
            vista.append(("user", s + (f" [imagen {Path(imagen).name}]" if imagen else "")))
            vista.append(("assistant", "…"))
            res = {"t": ""}

            def escribir(t):
                res["t"] = t
                vista[-1] = ("assistant", t)

            err = []
            th = threading.Thread(target=lambda: self._try(lambda: motor.chat(msgs, imagen, escribir), err), daemon=True)
            th.start()
            while th.is_alive():
                self.scr.erase()
                self.header(f"Chat con IA · {motor.m.nombre} · {motor.variante.upper()}", "Respondiendo…")
                lines = []
                for rol, t in vista:
                    pref = "Tú: " if rol == "user" else "IA: "
                    for k, l in enumerate(textwrap.wrap(t, max(20, w - 8)) or [""]):
                        lines.append(((pref if k == 0 else "    ") + l, rol))
                for n, (l, rol) in enumerate(lines[-(h - 5):]):
                    self.put(2 + n, 2, l, curses.A_BOLD if rol == "user" else 0)
                self.scr.refresh()
                time.sleep(0.1)
            if err:
                vista[-1] = ("assistant", f"(error: {err[0]})")
                msgs.pop()
            else:
                msgs.append({"role": "assistant", "content": res["t"]})


def main():
    curses.wrapper(lambda scr: App(scr).run())
