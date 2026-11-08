# Juegos Reunidos

Colección de juegos y herramientas para Android:

- **Punto de cruz**: convierte una foto en un patrón y lo coloreas punto a punto.
- **Bloques**: piezas que caen y líneas que se completan (deslizar mueve, tocar gira, deslizar abajo deja caer; también hay botones).
- **DOS**: DOSBox (js-dos 6.22 compilado a WebAssembly) dentro de un WebView, sin Internet. C: se guarda entre sesiones. Menú → *Importar* copia archivos o el contenido de un ZIP a C:. Teclado en pantalla completo y botón *Abc* para usar el teclado del móvil.
- **Terminal Linux**: la shell real del sistema (`/system/bin/sh`, con las órdenes de toybox). No es una emulación: ejecuta procesos de verdad, sin permisos de root. Funciona con órdenes de línea (ls, cat, grep, ps, ping…); los programas de pantalla completa (vi, top) no, porque no hay pseudoterminal.
- **Chat con IA local**: IA con visión que funciona en el propio móvil, sin Internet (solo se conecta una vez para descargar el modelo). Lleva llama.cpp compilado para cada tipo de procesador:
  - **Snapdragon**: NPU Hexagon y GPU Adreno (OpenCL), además de la CPU.
  - **Tensor, Exynos, MediaTek…**: GPU por Vulkan (Mali, Xclipse…) y CPU.
  - **CPU ARM64** en tres niveles: i8mm (Snapdragon 8 Gen 1+, Tensor G2+…), dotprod y ARMv8 básica.

  La primera vez que se usa un modelo, la app **mide la velocidad real de cada motor disponible** en ese móvil y se queda con el más rápido (se puede repetir desde el menú). Usa solo los núcleos rápidos de la CPU, elige el modelo según la RAM (SmolVLM2 500M, SmolVLM2 2.2B o Gemma 3 4B) y en Snapdragon descarga la cuantización Q4_0, la optimizada para Adreno y Hexagon.

## Punto de cruz
1. Toca **Elegir imagen** y escoge una foto o dibujo.
2. Ajusta el **ancho** (20–150 puntadas) y el **número de colores** (2–49). La vista previa se actualiza al soltar el control. "Girar 90°" corrige fotos de lado.
3. Toca **Empezar a colorear**.
4. Elige un color en la paleta inferior (cada color tiene su símbolo y los puntos que faltan). Sus casillas se resaltan en azul.
5. **Un dedo** colorea (toque o arrastre; solo se rellenan las casillas de ese color). **Dos dedos** mueven y hacen zoom. "Buscar punto" lleva a la casilla pendiente más cercana.
6. El progreso se guarda solo. En el menú: ver entero, ver resultado final y reiniciar.

Cómo convierte la imagen (`PatternGenerator`): promedia bloques de píxeles para obtener la cuadrícula, agrupa colores con k-means en espacio CIELab, quita puntadas sueltas y ordena la paleta por uso. Las zonas transparentes quedan como tela sin bordar.

## Motor de IA (native/)
- `native/fetch-llama.sh` descarga llama.cpp (commit 0c1e570, el de llama-cpp-python 0.3.36).
- `native/build-android.sh <variante>` lo compila con el NDK oficial: `cpu-armv8`, `cpu-dotprod`, `cpu-i8mm`, `vulkan` y `snapdragon` (este último dentro del contenedor `ghcr.io/snapdragon-toolchain/arm64-android`, que trae el SDK de Hexagon y OpenCL).
- `.github/workflows/android.yml` compila todos los motores y el APK en GitHub Actions; el APK queda como artefacto de cada ejecución.
- `native/build-llama.sh` es la alternativa sin NDK (zig + musl, solo CPU).

Los binarios van en `lib/arm64-v8a/libllama_*.so` dentro del APK (`extractNativeLibs=true`) y la app los ejecuta como procesos `llama-server` en 127.0.0.1.

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
