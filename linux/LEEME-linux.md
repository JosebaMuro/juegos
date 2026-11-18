# Juegos Reunidos para Linux

La misma colección que la app de Android, para el escritorio (X11 o Wayland) y para la
terminal (TUI), también en la consola de texto sin escritorio:

| | Escritorio (`juegos-x11`) | Terminal (`juegos-tui`) |
|---|---|---|
| Bloques | ventana, teclado | en la terminal, con colores |
| Punto de cruz | clic y arrastre para colorear, rueda para acercar | cursor, Espacio para colorear |
| Juegos libres web (2048, Hextris…) | en el navegador, servidos desde 127.0.0.1 | solo en el escritorio |
| Juegos libres de DOS (NetHack, Crawl, IVAN…) | DOSBox en una ventana | DOSBox a pantalla completa sin X11 (SDL sobre KMS) |
| DOS con unidad C: propia | DOSBox | DOSBox a pantalla completa |
| Terminal | abre la terminal del escritorio | la shell, y `exit` para volver |
| Chat con IA y visión | modelos GGUF de la app con llama.cpp (GPU con Vulkan o CUDA, si no CPU) | igual, en texto (`/imagen RUTA pregunta`) |

`juegos` elige solo: con escritorio abre la ventana y sin él la TUI.

## Instalar

Lo hace el instalador de la suite (`instalar-ubuntu.sh`, en el repositorio Herrero), que comparte
el venv de Python con los demás proyectos. A mano:

```bash
tar xzf juegos-linux.tar.gz && cd juegos-linux
python3 -m venv ~/.venvs/suite && ~/.venvs/suite/bin/pip install .
sudo apt install dosbox zenity      # DOS y diálogo de archivos (opcionales)
JUEGOS_DATOS=$PWD/datos ~/.venvs/suite/bin/juegos
```

Los datos del usuario (patrones, partidas de DOS, modelos de IA) van en `~/.local/share/juegos`.
