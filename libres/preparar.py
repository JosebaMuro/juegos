#!/usr/bin/env python3
"""Descarga y empaqueta los juegos libres de libres/catalogo.json en los assets de la app.

Para cada juego:
  * clona su repositorio en el commit fijado (git, sin historial);
  * DOS: comprime la carpeta del juego en assets/dos/juegos/<id>.zip (añade CWSDPMI si lo necesita);
  * web: copia los archivos del juego en assets/web/<id>/ y quita anuncios, analítica y recursos externos;
  * reúne sus licencias en assets/libres/licencias/<id>.txt.

Uso:  python3 libres/preparar.py           (deja la caché de clones en build/libres-src)
"""
import io
import json
import os
import re
import shutil
import subprocess
import sys
import zipfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, "app", "src", "main", "assets")
CACHE = os.path.join(ROOT, "build", "libres-src")
CATALOGO = os.path.join(ROOT, "libres", "catalogo.json")

# CWSDPMI (servidor DPMI de Charles W. Sandmann) para los juegos DJGPP que no lo traen.
# Se redistribuye sin modificar junto con su documentación; fuente: http://sandmann.dotster.com/cwsdpmi/
CWSDPMI_DE = ("ivan", "GAMES/IVAN")

LICENCIA_RE = re.compile(r"^(licen[cs]|copying|gplv)", re.I)


def run(*cmd, cwd=None):
    subprocess.check_call(cmd, cwd=cwd)


def clonar(j):
    dest = os.path.join(CACHE, j["id"])
    if os.path.isdir(os.path.join(dest, ".git")):
        head = subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=dest).decode().strip()
        if head == j["commit"]:
            return dest
        shutil.rmtree(dest)
    os.makedirs(dest, exist_ok=True)
    run("git", "init", "-q", dest)
    run("git", "-C", dest, "fetch", "-q", "--depth", "1", j["repo"] + (".git" if "gitlab.com" in j["repo"] else ""), j["commit"])
    run("git", "-C", dest, "checkout", "-q", "FETCH_HEAD")
    return dest


def archivos_licencia(src):
    out = []
    for base, dirs, files in os.walk(src):
        dirs[:] = sorted(d for d in dirs if d not in (".git", "node_modules"))
        for f in sorted(files):
            if LICENCIA_RE.match(f) and not f.lower().endswith((".c", ".h", ".cpp", ".pas", ".xml")):
                out.append(os.path.join(base, f))
    return out


def leer_texto(p):
    with open(p, "rb") as fh:
        return fh.read().decode("utf-8", "replace").replace("\r\n", "\n")


def escribir_licencias(j, src):
    textos = []
    cab = "%s\nAutor: %s\nLicencia: %s\nWeb: %s\nCódigo fuente: %s (commit %s)\n" % (
        j["titulo"], j["autor"], j["licencia"], j["web"], j["repo"], j["commit"])
    if j["tipo"] == "dos":
        cab += "Paquete de FreeDOS: el código fuente completo está en la carpeta SOURCE de ese repositorio.\n"
    textos.append(cab)
    vistos = set()
    for p in archivos_licencia(src):
        t = leer_texto(p)
        if t in vistos:
            continue
        vistos.add(t)
        textos.append("=" * 72 + "\n" + os.path.relpath(p, src) + "\n" + "=" * 72 + "\n" + t)
    # Paquetes sin archivo de licencia: la ficha LSM declara la licencia.
    appinfo = os.path.join(src, "APPINFO")
    if os.path.isdir(appinfo):
        for f in sorted(os.listdir(appinfo)):
            if f.upper().endswith(".LSM"):
                textos.append("=" * 72 + "\nAPPINFO/" + f + "\n" + "=" * 72 + "\n" + leer_texto(os.path.join(appinfo, f)))
    if len(textos) == 1 and j["tipo"] == "dos":
        print("  AVISO: %s no trae archivo de licencia; solo la ficha LSM" % j["id"])
    if j.get("dpmi"):
        cws = os.path.join(CACHE, CWSDPMI_DE[0], CWSDPMI_DE[1], "CWSDPMI.DOC")
        textos.append("=" * 72 + "\nCWSDPMI.EXE (incluido para ejecutar el juego)\n" + "=" * 72 + "\n" + leer_texto(cws))
    d = os.path.join(ASSETS, "libres", "licencias")
    os.makedirs(d, exist_ok=True)
    with open(os.path.join(d, j["id"] + ".txt"), "w", encoding="utf-8") as fh:
        fh.write("\n\n".join(textos))


def empaquetar_dos(j, src):
    carpeta = os.path.join(src, j["carpeta"])
    quitar = set(q.upper() for q in j.get("quitar", []))
    out = os.path.join(ASSETS, "dos", "juegos", j["id"] + ".zip")
    os.makedirs(os.path.dirname(out), exist_ok=True)
    nombres = set()
    with zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED, compresslevel=9) as z:
        for base, dirs, files in os.walk(carpeta):
            rel = os.path.relpath(base, carpeta)
            dirs[:] = sorted(d for d in dirs if os.path.normpath(os.path.join(rel, d)).upper() not in quitar)
            if rel != ".":
                # js-dos solo crea las carpetas que vienen como entrada propia en el ZIP.
                z.writestr(zipfile.ZipInfo(rel.replace(os.sep, "/") + "/", (2000, 1, 1, 0, 0, 0)), b"")
            for f in sorted(files):
                r = os.path.normpath(os.path.join(rel, f))
                if r.upper() in quitar or r in j.get("archivos", {}):
                    continue
                zi = zipfile.ZipInfo(r.replace(os.sep, "/"), (2000, 1, 1, 0, 0, 0))
                zi.compress_type = zipfile.ZIP_DEFLATED
                with open(os.path.join(base, f), "rb") as fh:
                    z.writestr(zi, fh.read())
                nombres.add(r.upper())
        for nombre, contenido in j.get("archivos", {}).items():
            z.writestr(zipfile.ZipInfo(nombre, (2000, 1, 1, 0, 0, 0)), contenido)
        if j.get("dpmi") and "CWSDPMI.EXE" not in nombres:
            cws = os.path.join(CACHE, CWSDPMI_DE[0], CWSDPMI_DE[1])
            for f in ("CWSDPMI.EXE", "CWSDPMI.DOC"):
                with open(os.path.join(cws, f), "rb") as fh:
                    z.writestr(zipfile.ZipInfo(f, (2000, 1, 1, 0, 0, 0)), fh.read())


# Arreglos para que los juegos web funcionen sin conexión y sin anuncios ni analítica.
def parchear_web(j, dest):
    idx = os.path.join(dest, "index.html")
    html = leer_texto(idx)
    if j["id"] == "hextris":
        html = re.sub(r"<script[^>]*adsbygoogle[^<]*</script>", "", html)
        html = re.sub(r"<script>\s*\(function\(i,s,o,g,r,a,m\).*?</script>", "", html, flags=re.S)
        html = re.sub(r"<link[^>]*fonts\.googleapis[^>]*>", "", html)
        html = re.sub(r"<script[^>]*rrssb[^>]*></script>", "", html)
    if j["id"] == "adarkroom":
        html = re.sub(r'<script src="https://ajax\.googleapis\.com[^"]*"></script>', "", html)
    if j["id"] == "torre":
        html = re.sub(r"@font-face\{font-family:wenxue;[^}]*\}", "", html)
        html = html.replace("alert('Network error... Please try again.')", "console.log('recurso no disponible')")
    # Google Analytics / Tag Manager (todas las páginas)
    html = re.sub(r"<!-- Google tag \(gtag\.js\) -->", "", html)
    html = re.sub(r"<script async src=\"https://www\.googletagmanager\.com/[^\"]*\"></script>", "", html)
    html = re.sub(r"<script>\s*window\.dataLayer.*?</script>", "", html, flags=re.S)
    html = re.sub(r"<script>function gtag\(\).*?</script>", "", html, flags=re.S)
    with open(idx, "w", encoding="utf-8") as fh:
        fh.write(html)
    if j["id"] == "hextris":
        reemplazar(os.path.join(dest, "js", "initialization.js"),
                   r"\(function\(i, s, o, g, r, a, m\).*?ga\('send', 'pageview'\);", "", re.S)
        reemplazar(os.path.join(dest, "js", "main.js"),
                   r"\(function\(\)\{\s*var script = document\.createElement\('script'\);\s*script\.src = 'http://hextris\.io/a\.js';.*?\}\)\(\)", "", re.S)


def reemplazar(p, patron, por, flags=0):
    t = leer_texto(p)
    n = re.sub(patron, por, t, flags=flags)
    if n == t:
        raise SystemExit("No se pudo parchear " + p)
    with open(p, "w", encoding="utf-8") as fh:
        fh.write(n)


def copiar_web(j, src):
    dest = os.path.join(ASSETS, "web", j["id"])
    if os.path.isdir(dest):
        shutil.rmtree(dest)
    os.makedirs(dest)
    for item in j["incluir"]:
        s = os.path.join(src, item)
        if os.path.isdir(s):
            shutil.copytree(s, os.path.join(dest, item))
        else:
            os.makedirs(os.path.dirname(os.path.join(dest, item)), exist_ok=True)
            shutil.copy2(s, os.path.join(dest, item))
    for q in j.get("quitar", []):
        p = os.path.join(dest, q)
        if os.path.isdir(p):
            shutil.rmtree(p)
        elif os.path.exists(p):
            os.remove(p)
    parchear_web(j, dest)


def main():
    with open(CATALOGO, encoding="utf-8") as fh:
        cat = json.load(fh)
    juegos = cat["juegos"]
    os.makedirs(CACHE, exist_ok=True)
    # Primero el paquete del que sale CWSDPMI.
    cws = {"id": CWSDPMI_DE[0], "repo": "https://gitlab.com/FreeDOS/games/ivan",
           "commit": next(j["commit"] for j in juegos if j["id"] == "ivan")}
    clonar(cws)
    for d in ("dos/juegos", "web"):
        p = os.path.join(ASSETS, d)
        if os.path.isdir(p):
            shutil.rmtree(p)
    for j in juegos:
        print("·", j["id"])
        src = clonar(j)
        if j["tipo"] == "dos":
            empaquetar_dos(j, src)
        else:
            copiar_web(j, src)
        escribir_licencias(j, src)
    shutil.copy2(CATALOGO, os.path.join(ASSETS, "libres", "catalogo.json"))
    total = 0
    for d in ("dos/juegos", "web"):
        for base, _, files in os.walk(os.path.join(ASSETS, d)):
            total += sum(os.path.getsize(os.path.join(base, f)) for f in files)
    print("Listo: %d juegos, %.1f MB en assets" % (len(juegos), total / 1e6))


if __name__ == "__main__":
    main()
