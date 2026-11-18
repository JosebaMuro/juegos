"""Lógica del juego de bloques (la misma que la app de Android: BloquesEngine.java)."""
import random

COLS, ROWS = 10, 20
# Siete piezas de cuatro casillas; cada una como lista de (x, y) en una caja de 4x4.
SHAPES = [
    [(0, 1), (1, 1), (2, 1), (3, 1)],  # barra
    [(0, 0), (0, 1), (1, 1), (2, 1)],  # J
    [(2, 0), (0, 1), (1, 1), (2, 1)],  # L
    [(1, 0), (2, 0), (1, 1), (2, 1)],  # cuadrado
    [(1, 0), (2, 0), (0, 1), (1, 1)],  # S
    [(1, 0), (0, 1), (1, 1), (2, 1)],  # T
    [(0, 0), (1, 0), (1, 1), (2, 1)],  # Z
]
BOX = [4, 3, 3, 4, 3, 3, 3]
# Colores RGB de cada pieza (1..7) y el índice de color de 8 de la terminal más cercano.
COLORES = [(0, 188, 212), (63, 81, 181), (255, 152, 0), (255, 235, 59), (76, 175, 80), (156, 39, 176), (244, 67, 54)]
COLORES_TERM = [6, 4, 3, 3, 2, 5, 1]


def cells(t, r):
    """Casillas de la pieza (tipo t, giro r) relativas a su caja."""
    n = BOX[t]
    out = []
    for x, y in SHAPES[t]:
        for _ in range(0 if t == 3 else r):
            x, y = n - 1 - y, x
        out.append((x, y))
    return out


class Bloques:
    def __init__(self, semilla=None):
        self.rnd = random.Random(semilla)
        self.reset()

    def reset(self):
        self.board = [0] * (COLS * ROWS)  # 0 vacío, 1..7 tipo de pieza
        self.score = self.lines = 0
        self.level = 1
        self.over = False
        self._bag = []
        self.next = self._draw()
        self._spawn()

    def _draw(self):
        if not self._bag:
            self._bag = list(range(7))
            self.rnd.shuffle(self._bag)
        return self._bag.pop()

    def _spawn(self):
        self.type = self.next
        self.next = self._draw()
        self.rot = 0
        self.px = (COLS - BOX[self.type]) // 2
        self.py = -1 if self.type == 0 else 0
        if self._collides(self.px, self.py, self.rot):
            self.over = True

    def _collides(self, x, y, r):
        for cx, cy in cells(self.type, r):
            cx += x
            cy += y
            if cx < 0 or cx >= COLS or cy >= ROWS:
                return True
            if cy >= 0 and self.board[cy * COLS + cx]:
                return True
        return False

    def move(self, dx):
        if self.over or self._collides(self.px + dx, self.py, self.rot):
            return False
        self.px += dx
        return True

    def rotate(self):
        if self.over:
            return False
        nr = (self.rot + 1) % 4
        for k in (0, -1, 1, -2, 2):
            if not self._collides(self.px + k, self.py, nr):
                self.px += k
                self.rot = nr
                return True
        if not self._collides(self.px, self.py - 1, nr):
            self.py -= 1
            self.rot = nr
            return True
        return False

    def step(self):
        """Baja una fila. Devuelve las líneas hechas si la pieza se fija, o -1 si sigue cayendo."""
        if self.over:
            return -1
        if not self._collides(self.px, self.py + 1, self.rot):
            self.py += 1
            return -1
        return self._lock()

    def soft_drop(self):
        r = self.step()
        if r < 0:
            self.score += 1
        return r

    def hard_drop(self):
        if self.over:
            return 0
        d = 0
        while not self._collides(self.px, self.py + 1, self.rot):
            self.py += 1
            d += 1
        self.score += 2 * d
        return self._lock()

    def ghost_y(self):
        y = self.py
        while not self._collides(self.px, y + 1, self.rot):
            y += 1
        return y

    def _lock(self):
        for cx, cy in cells(self.type, self.rot):
            cx += self.px
            cy += self.py
            if cy < 0:
                self.over = True
                continue
            self.board[cy * COLS + cx] = self.type + 1
        cleared = 0
        y = ROWS - 1
        while y >= 0:
            if all(self.board[y * COLS + x] for x in range(COLS)):
                del self.board[y * COLS:(y + 1) * COLS]
                self.board[0:0] = [0] * COLS
                cleared += 1
            else:
                y -= 1
        if cleared:
            self.score += (0, 100, 300, 500, 800)[cleared] * self.level
            self.lines += cleared
            self.level = 1 + self.lines // 10
        if not self.over:
            self._spawn()
        return cleared

    def drop_interval(self):
        """Segundos entre caídas según el nivel."""
        return max(0.07, 0.8 * 0.85 ** (self.level - 1))

    def visible(self):
        """Tablero con la pieza actual dibujada: lista de filas con 0..7 y -1 para la sombra."""
        b = list(self.board)
        if not self.over:
            gy = self.ghost_y()
            for cx, cy in cells(self.type, self.rot):
                x, y = self.px + cx, gy + cy
                if 0 <= y < ROWS and not b[y * COLS + x]:
                    b[y * COLS + x] = -1
            for cx, cy in cells(self.type, self.rot):
                x, y = self.px + cx, self.py + cy
                if 0 <= y < ROWS:
                    b[y * COLS + x] = self.type + 1
        return [b[r * COLS:(r + 1) * COLS] for r in range(ROWS)]
