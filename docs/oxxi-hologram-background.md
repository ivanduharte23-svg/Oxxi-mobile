# Holograma de Oxxi en segundo plano

## Decisión técnica

La referencia muestra el holograma sobre el fondo, los widgets y los iconos que ya tiene el teléfono. Por eso Oxxi utiliza una ventana transparente de Android `TYPE_APPLICATION_OVERLAY` en lugar de instalar un fondo animado que sustituya el fondo actual. La ventana ocupa la pantalla, dibuja el holograma y usa `FLAG_NOT_TOUCHABLE` para que los toques lleguen a los iconos y controles que hay debajo.

En Android 12 y posteriores, el sistema bloquea ciertos toques que atraviesan ventanas no confiables si la opacidad combinada supera el límite de seguridad. Oxxi fija la opacidad de esa capa en 0,78 (el límite predeterminado documentado es 0,8). Si el fabricante cambia ese comportamiento, el sistema puede impedir que se atraviesen los toques; nunca se intenta desactivar la protección del teléfono.

## Movimiento y sincronización

El holograma se dibuja con Compose Canvas: malla esférica proyectada, rotación, respiración, partículas orbitales y tres ondas que se expanden. Se utiliza la misma composición en la pantalla de Oxxi, en el panel de voz y en la capa transparente de segundo plano. El nivel de audio modifica el radio, la deformación de la malla y la intensidad de las ondas.

El nivel puede proceder de dos fuentes:
- El micrófono de la conversación de Oxxi, mientras está escuchando o transcribiendo.
- El visualizador musical opcional, que calcula localmente el nivel RMS del audio reproducido por otras aplicaciones.

## Captura de música

El visualizador utiliza `AudioPlaybackCapture` (Android 10 o posterior) junto con `MediaProjection`. Android muestra su propio cuadro de autorización y el usuario debe aceptarlo. La autorización es de un solo uso; Oxxi no almacena el audio ni lo envía a Groq, solo calcula el nivel para animar el holograma.

Solo se capturan usos multimedia/juegos (`USAGE_MEDIA` y `USAGE_GAME`) que la aplicación reproductora permita compartir. Algunas aplicaciones protegidas no permiten la captura: en esos casos el holograma seguirá moviéndose con sus animaciones propias, pero no podrá seguir el ritmo de esa aplicación. El visualizador se puede detener desde Ajustes o desde la notificación persistente.

## Escucha en segundo plano

La escucha de la palabra «Oxxi» utiliza un servicio de primer plano de tipo micrófono, una notificación persistente y el modelo Vosk en español. Debe activarse desde una pantalla visible de Oxxi y necesita los permisos de micrófono y «Mostrar sobre otras aplicaciones». Android 12+ y Android 14+ restringen iniciar servicios de micrófono desde segundo plano; por eso Oxxi no intenta arrancar el micrófono a escondidas. La exención de optimización de batería de Samsung/Android reduce el riesgo de que el sistema suspenda el servicio, pero no garantiza que el fabricante nunca lo cierre.

## Activación en el teléfono

1. Abrir Oxxi y entrar en Ajustes → Voz.
2. Descargar el modelo de voz en español y activar «Palabra de activación Oxxi».
3. Conceder micrófono, notificaciones, «Mostrar sobre otras aplicaciones» y permitir funcionamiento sin restricciones de batería cuando Android/Samsung lo solicite.
4. Volver a Inicio. Al salir de Oxxi, el holograma se muestra por encima del fondo y los iconos mientras el servicio de escucha esté activo.
5. Para que siga la música de otras aplicaciones, abrir Ajustes → Visualizador musical → Activar visualizador musical y aceptar el cuadro del sistema.

La capa del holograma no se muestra sobre la pantalla de bloqueo. Los controles y la conversación de voz siguen utilizando la interfaz de voz normal.

## Documentación oficial

- [WallpaperService de Android](https://developer.android.com/reference/android/service/wallpaper/WallpaperService) — alternativa de fondo animado; sustituye el fondo actual y por eso no reproduce la composición solicitada con el fondo/los widgets existentes.
- [Ventanas de superposición y restricciones de toques en Android 12](https://developer.android.com/about/versions/12/behavior-changes-all#untrusted-touch-events) — explica el límite de opacidad para permitir toques pasantes.
- [Restricciones para iniciar servicios en primer plano desde segundo plano](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start) — explica las restricciones para servicios que usan micrófono.
- [Captura de audio de otras aplicaciones](https://developer.android.com/media/platform/av-capture) — requisitos de MediaProjection y políticas de captura de los reproductores.
- [Modelos Vosk](https://alphacephei.com/vosk/models) — catálogo oficial; el modelo español pequeño está indicado para dispositivos móviles.

## Límites conocidos

La esfera es una proyección 2D animada que simula volumen, no un modelo 3D de Filament. La capa superpuesta está por encima de la interfaz del launcher, no detrás de sus iconos a nivel de composición del sistema; al ser semitransparente, la imagen conserva la lectura de widgets e iconos. La disponibilidad final depende de los permisos y de las restricciones que aplique el fabricante del teléfono.
