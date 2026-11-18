"""Prueba de humo de la TUI en un terminal de verdad (pty): python3 tests/tui_pty.py [TERM]"""
import os, pty, select, struct, sys, time, fcntl, termios

term = sys.argv[1] if len(sys.argv) > 1 else "linux"
pid, fd = pty.fork()
if pid == 0:
    os.environ["TERM"] = term
    os.execv(sys.executable, [sys.executable, "-m", "juegos_linux", "--tui"])
fcntl.ioctl(fd, termios.TIOCSWINSZ, struct.pack("HHHH", 40, 120, 0, 0))
out = b""


def pump(s):
    global out
    end = time.time() + s
    while time.time() < end:
        r, _, _ = select.select([fd], [], [], 0.1)
        if r:
            try:
                out += os.read(fd, 65536)
            except OSError:
                return


def send(b, wait=0.5):
    os.write(fd, b)
    pump(wait)


# flecha abajo como la manda cada terminal (consola de Linux: ESC [ B; xterm: ESC O B)
ABAJO = b"\x1b[B" if term == "linux" else b"\x1bOB"
IZQ = b"\x1b[D" if term == "linux" else b"\x1bOD"
pump(1.5)
send(b"\r", 1.0)            # Bloques
send(IZQ)
send(b" ", 0.8)
send(b"q", 0.8)
send(ABAJO + b"\r", 0.8)       # Punto de cruz
send(b"q", 0.8)
send(ABAJO + b"\r", 0.8)       # Juegos libres
send(b"q", 0.8)
send(b"7", 1.5)              # Salir
_, st = os.waitpid(pid, 0)
txt = out.decode("utf-8", "replace")
faltan = [s for s in ("JUEGOS REUNIDOS", "Bloques", "Siguiente", "Punto de cruz", "Nuevo desde una imagen") if s not in txt]
code = os.waitstatus_to_exitcode(st)
print("salida", code, "bytes", len(out), "faltan", faltan)
if code != 0 or faltan:
    print(txt[-2500:])
    sys.exit(1)
