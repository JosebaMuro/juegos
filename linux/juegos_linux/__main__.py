"""juegos [--tui | --gui]: con escritorio (X11/Wayland) abre la ventana; sin él, la TUI."""
import os
import sys


def main(argv=None):
    argv = sys.argv[1:] if argv is None else argv
    if "--version" in argv:
        from . import __version__
        print(__version__)
        return 0
    if "-h" in argv or "--ayuda" in argv or "--help" in argv:
        print(__doc__)
        return 0
    escritorio = bool(os.environ.get("DISPLAY") or os.environ.get("WAYLAND_DISPLAY"))
    if "--tui" in argv or ("--gui" not in argv and not escritorio):
        return tui()
    return gui()


def tui():
    from .tui.app import main as m
    m()
    return 0


def gui():
    from .gui.app import main as m
    m()
    return 0


if __name__ == "__main__":
    sys.exit(main())
