# Juegos Reunidos

Colección de juegos y herramientas para Android:

- **Punto de cruz**: convierte una foto en un patrón y lo coloreas punto a punto.
- **Bloques**: piezas que caen y líneas que se completan (deslizar mueve, tocar gira, deslizar abajo deja caer; también hay botones).
- **DOS**: DOSBox (js-dos 6.22 compilado a WebAssembly) dentro de un WebView, sin Internet. C: se guarda entre sesiones. Menú → *Importar* copia archivos o el contenido de un ZIP a C:. Teclado en pantalla completo y botón *Abc* para usar el teclado del móvil.
- **Terminal Linux**: la shell real del sistema (`/system/bin/sh`, con las órdenes de toybox). No es una emulación: ejecuta procesos de verdad, sin permisos de root. Funciona con órdenes de línea (ls, cat, grep, ps, ping…); los programas de pantalla completa (vi, top) no, porque no hay pseudoterminal.
- **Chat con IA local**: IA con visión que funciona en el propio móvil, sin Internet (solo se conecta una vez para descargar el modelo). Motor llama.cpp incluido en tres variantes de CPU ARM64; al arrancar se elige la más rápida que admite el procesador (i8mm → dotprod → ARMv8), se usan solo los núcleos rápidos y se recomienda el modelo según la RAM:
  - menos de 4,5 GB: SmolVLM2 500M · 4,5–7 GB: SmolVLM2 2.2B · 7 GB o más: Gemma 3 4B.

## Punto de cruz
1. Toca **Elegir imagen** y escoge una foto o dibujo.
2. Ajusta el **ancho** (20–150 puntadas) y el **número de colores** (2–49). La vista previa se actualiza al soltar el control. "Girar 90°" corrige fotos de lado.
3. Toca **Empezar a colorear**.
4. Elige un color en la paleta inferior (cada color tiene su símbolo y los puntos que faltan). Sus casillas se resaltan en azul.
5. **Un dedo** colorea (toque o arrastre; solo se rellenan las casillas de ese color). **Dos dedos** mueven y hacen zoom. "Buscar punto" lleva a la casilla pendiente más cercana.
6. El progreso se guarda solo. En el menú: ver entero, ver resultado final y reiniciar.

Cómo convierte la imagen (`PatternGenerator`): promedia bloques de píxeles para obtener la cuadrícula, agrupa colores con k-means en espacio CIELab, quita puntadas sueltas y ordena la paleta por uso. Las zonas transparentes quedan como tela sin bordar.

## Motor de IA (native/)
`native/build-llama.sh` compila `llama-server` de llama.cpp con zig (musl, estático) para cada nivel de CPU y deja los binarios en `native/libs/arm64-v8a/libllama_*.so`, que `build.sh` mete en el APK. Se usa musl porque glibc moderno llama a `clone3`, que el filtro seccomp de Android prohíbe. Versión compilada: llama.cpp 0c1e570 (de llama-cpp-python 0.3.36).

GPU y NPU (Adreno/Hexagon en Snapdragon, Mali/TPU en Tensor) no están incluidas: necesitan el NDK oficial y SDKs de los fabricantes, que no se pueden descargar en el entorno donde se compiló.

## Licencias
El emulador DOS incluye js-dos/DOSBox, con licencia GPL (ver `app/src/main/assets/dos/LICENSE-js-dos.txt`). Su código fuente está en https://github.com/caiiiycuk/js-dos (versión 6.22.60) y en el paquete npm `js-dos@6.22.60`.

## Estructura
```
app/src/main/java/com/joseba/juegos/
  MainActivity.java      menú con una tarjeta por juego
  Game.java              interfaz que implementa cada juego
  GameRegistry.java      lista de juegos (añade aquí los nuevos)
  Ui.java                utilidades de interfaz compartidas
  ImageUtil.java         carga de imágenes con giro EXIF
  puntocruz/             punto de cruz
  bloques/               juego de bloques (BloquesEngine no depende de Android)
  dos/                   actividad del WebView de DOS (la página está en assets/dos/)
  terminal/              terminal sobre la shell del sistema
  chat/                  chat local: Hardware (detección), ModelCatalog, ModelStore (descarga), LocalEngine (llama-server)
```
Para añadir un juego: crea un paquete nuevo, implementa `Game`, declara su Activity en `AndroidManifest.xml` y regístralo en `GameRegistry`.

## Compilar
**Opción A, sin Gradle (así se ha compilado el APK entregado):**
```
sudo apt install openjdk-17-jdk android-sdk-platform-23 aapt zipalign apksigner dalvik-exchange
./build.sh          # genera build/JuegosReunidos.apk
```
**Opción B, Android Studio:** abre la carpeta del proyecto (`settings.gradle`, `app/build.gradle`, AGP 8.7, compileSdk 34). Esta configuración no se ha probado en el entorno donde se creó el proyecto.

El código usa solo el framework de Android (sin AndroidX ni dependencias), Java 8 sin lambdas, minSdk 21 (Android 5.0) y targetSdk 34.

## Firma
La clave de firma no está en el repositorio (es público). `build.sh` usa `keystore/juegos.jks` si existe o crea una nueva.
En GitHub Actions se toma de los secretos `KEYSTORE_B64` (el .jks en base64) y `KEYSTORE_PASS`.
Usa siempre la misma clave para que las actualizaciones se instalen encima sin desinstalar.
