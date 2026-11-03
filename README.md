# Oxxi para Android

**Oxxi** es una asistente personal de voz para Android. La interfaz holográfica y el chat actuales se mantienen; la conversación, las indicaciones habladas y los mensajes de la aplicación están configurados en español de España.

> Proyecto personal en desarrollo. Algunas acciones dependen de la versión de Android, los permisos concedidos, las aplicaciones instaladas y los servicios externos configurados. Que una orden sea reconocida no garantiza que Android o una aplicación externa permita completarla.

## Funciones del proyecto

- Activación por voz «Oxxi» con reconocimiento local Vosk y verificación de voz del propietario mediante ONNX Runtime.
- Conversación continua, interrupción por la palabra de activación, respuestas habladas y alternativa de síntesis de voz de Android.
- Chat con historial local, memoria y agente de IA con herramientas. Las acciones solo se confirman cuando existe un resultado real.
- Acciones del teléfono: apertura de aplicaciones y ajustes, linterna, volumen, conectividad compatible, cámara, grabadora, temporizadores, alarmas y controles multimedia.
- Contactos, llamadas y flujos de SMS/mensajería. WhatsApp, Telegram y otras aplicaciones dependen de sus propias capacidades y de los permisos disponibles.
- Lectura y respuesta a notificaciones compatibles, correo Gmail y resúmenes, si el usuario concede acceso y configura las cuentas necesarias.
- Calendario, notas, tareas y recordatorios por ubicación.
- Consultas meteorológicas, búsqueda web y cálculos.
- Modos personalizados, horarios y activadores compatibles como NFC, Bluetooth, cargador y red Wi-Fi.
- Accesos rápidos, mosaico de Ajustes rápidos, panel de voz/pantalla bloqueada y cuaderno de escritura.
- Análisis de texto, imágenes y capturas de pantalla compatibles. Los formatos no admitidos se indican en la aplicación.

## Requisitos y configuración

- Android 8.0 (API 26) o posterior; la compilación CI se orienta a dispositivos ARM64.
- Una clave de Groq introducida desde los ajustes de Oxxi para transcripción y funciones de IA en línea.
- Permisos de micrófono, notificaciones, contactos, teléfono, ubicación, superposición u otros, según la función que se use.
- Conexión a internet para Groq, consultas web, meteorología, Gmail y voz neuronal remota. La detección local de la palabra de activación usa el modelo Vosk descargado en el dispositivo.

Android puede mostrar el indicador del micrófono mientras está activo el servicio de escucha. Los fabricantes también pueden limitar los servicios en segundo plano; revisa los ajustes de batería del dispositivo cuando la escucha persistente no se mantenga.

## Privacidad y datos

La detección de la palabra de activación y la verificación de voz se realizan en el dispositivo. Después de activarse, el audio de la orden se envía a Groq para su transcripción; las solicitudes de conversación y las funciones en línea también pueden enviar texto a los servicios correspondientes. La voz neuronal usa un servicio remoto cuando está disponible y puede recurrir al motor de voz de Android.

El historial y los datos de la aplicación se guardan localmente. El acceso a notificaciones puede exponer a Oxxi el contenido que muestran esas notificaciones; concede ese permiso solo si deseas utilizar esas funciones. La clave de Groq se introduce en la aplicación y no se incluye como valor dentro del código fuente del APK.

## Compilar y probar

El repositorio incluye flujos de GitHub Actions para ejecutar pruebas, análisis estático y generar un APK de depuración.

Requisitos para compilar localmente: JDK 17 o 21 y Android SDK 35.

```bash
./gradlew :app:testDebugUnitTest
./gradlew detekt
./gradlew :app:assembleDebug
./gradlew :app:assembleRelease verifyReleaseKeepRules
```

El APK de depuración se genera en `app/build/outputs/apk/debug/`. El flujo **Build Oxxi APK** publica un artefacto `Oxxi-Android-APK` cuando las pruebas terminan correctamente. Para instalar una actualización sobre una instalación existente, el APK debe estar firmado con la misma clave que esa instalación; consulta [la guía de firma](keystore/README.md) antes de cambiar de clave.

## Identidad técnica

El identificador de paquete y el namespace Android se conservan por compatibilidad con las instalaciones existentes. El nombre visible del producto es Oxxi. No cambies `applicationId` ni la clave de firma al preparar una actualización.

## Repositorio

[ivanduharte23-svg/Oxxi-mobile](https://github.com/ivanduharte23-svg/Oxxi-mobile)

## Licencia

Consulta el archivo [LICENSE](LICENSE).
