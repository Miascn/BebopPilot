# BebopPilot

Estación de control FPV, vídeo H.264 en vivo y telemetría por **USB Accessory (MUX)** para **Parrot Bebop 2** mediante **Parrot Skycontroller 2 / 2P / NG** en Android.

**Creada por [MiasCN](https://github.com/Miascn).**

---

## Descarga rápida (APK)

- **Dentro del repositorio:** [`releases/BebopPilot_0.1.0_debug.apk`](releases/BebopPilot_0.1.0_debug.apk)
- **GitHub Releases (`v0.1.0`):** disponible en la pestaña *Releases* del repositorio.
- **Versión:** `0.1.0` (`versionCode = 1`)
- **Requisitos:** Android 8.0+ (`minSdk 26`, `targetSdk 35`, ABI `arm64-v8a`), cable USB OTG conectado al **Parrot Skycontroller 2** y **Parrot Bebop 2** encendido.

---

## Características principales

- **Enlace directo USB/MUX con recuperación automática:** conexión USB directa al Skycontroller 2 (`UsbAccessoryMux` + `ARDiscoveryService`) sin depender de Wi-Fi del teléfono ni servicios en la nube, con verificación y reconexión automática al regresar desde segundo plano.
- **Vídeo H.264 por hardware (`MediaCodec`):** decodificación en vivo de baja latencia sobre `SurfaceView`, telemetría RX/DEC FPS, bitrate y recuperación automática ante pausas.
- **Grabación dual y fotografía:**
  - Grabación en la memoria interna del Bebop 2 (con temporizador en HUD y avisos de memoria/batería).
  - Transcodificación MP4 local en el teléfono (`HardwareMp4Transcoder`) con keyframes periódicos.
  - Captura de fotos con descarga e importación al almacenamiento de Android (`Movies/BebopPilot` / `Pictures/BebopPilot`).
- **Configuración translúcida en tiempo real sobre cámara (`⚙`):**
  - **Vuelo:** presets (`Lento`, `Normal`, `Rápido`, `Personalizado`), límites de ángulo/velocidad vertical/yaw, curvas Expo del Skycontroller 2, RTH, FlatTrim y calibración magnética asistida de 3 ejes.
  - **Cámara:** perfil de resolución (`720p HD` / `1080p`), FPS, modo de grabación/stream, estabilización (`Roll/Pitch`), anti-autorecord, exposición EV manual/auto, saturación y balance de blancos.
  - **Estado:** horizonte artificial, sensores, GPS/Wi-Fi, métricas de vídeo reales y bitácora automática de vuelo en CSV exportable a `Downloads`.
  - **Galería:** vista dual (**En este celular** + **Memoria del dron**) con visor integrado de fotos/vídeos, descarga por FTP MUX y borrado remoto con confirmación.
  - **Acerca de:** logotipo, versión, capacidades, diagnóstico de enlace y créditos de **MiasCN**.
- **Seguridad y alertas:** icono adaptativo de batería siempre visible y alerta visual + sonora al `<= 15%`.

---

## Compilación desde el código fuente

```bash
cd BebopPilot
./gradlew :app:assembleDebug
```

La APK generada se encuentra en:

```text
app/build/outputs/apk/debug/app-debug.apk
```

---

## Arquitectura del proyecto

- `app/` — Aplicación Android (Kotlin + Jetpack Compose + Material 3, gestión de sesión `SkyControllerConnectionManager`, grabador MP4, galería FTP MUX y HUD FPV).
- `arsdk/` — Módulo contenedor de **Parrot ARSDK3 3.13.1** (`libmux`, `ARDeviceController`, `ARDiscovery`, `ARDataTransfer`, bibliotecas nativas `arm64-v8a` y capa `UsbAccessoryMux`).
- `releases/` — APK compilada lista para instalar (`BebopPilot_0.1.0_debug.apk`).

---

## Autoría

- **Autor:** MiasCN (`josuejeremiascastillonieves@gmail.com`)
- **Repositorio:** https://github.com/Miascn/BebopPilot
