# Firma de Oxxi

La firma privada identifica la instalación Android. Para que Android actualice Oxxi sin desinstalarla ni borrar sus datos, cada APK de actualización debe estar firmado con **el mismo keystore** que se usó en el teléfono.

El archivo privado no debe subirse al repositorio público ni enviarse por chat.

## Firma local

Guarda tu keystore en una ubicación privada y añade a `local.properties` (archivo ignorado por Git):

```properties
oxxi.signing.storeFile=keystore/oxxi.keystore
oxxi.signing.storePassword=…
oxxi.signing.keyAlias=…
oxxi.signing.keyPassword=…
```

Por compatibilidad, Gradle también sigue leyendo las antiguas propiedades `friday.signing.*`. No cambies el identificador de paquete `com.friday.ai`: es la identidad técnica de las instalaciones actuales.

## Firma en GitHub Actions

El flujo **Build Oxxi APK** admite estos cuatro secretos del repositorio:

- `OXXI_SIGNING_KEYSTORE_BASE64`: contenido del keystore codificado en Base64, en una sola línea.
- `OXXI_SIGNING_STORE_PASSWORD`: contraseña del almacén.
- `OXXI_SIGNING_KEY_ALIAS`: alias de la clave.
- `OXXI_SIGNING_KEY_PASSWORD`: contraseña de la clave.

En GitHub, abre **Settings → Secrets and variables → Actions** y créalos como Repository secrets. Para generar el valor Base64 desde un ordenador con la herramienta `base64`, usa `base64 -w 0 oxxi.keystore` (en sistemas que no admiten `-w`, ejecuta `base64 oxxi.keystore` y elimina los saltos de línea). Nunca publiques el resultado ni el archivo original.

Si se configuran los cuatro secretos, el APK de CI se firma con la clave del propietario. Si no se configura ninguno, CI usa una clave de depuración almacenada en la caché de GitHub Actions para mantener la misma firma entre compilaciones mientras esa caché exista. Esa clave de respaldo sirve para pruebas; no sustituye a una clave privada de propietario para una distribución permanente.

## Tipos de compilación

| Tarea | Con la clave del propietario | Sin ella |
|---|---|---|
| `assembleDebug` | APK de depuración firmado con la clave existente | APK de depuración firmado con la clave de respaldo |
| `assembleRelease` | APK de versión firmado | APK sin firma; solo permite verificar la compilación |
| `ownerRelease` | Genera la versión firmada y comprueba R8 | Falla intencionadamente |

La salida de `ownerRelease` es `app/build/outputs/apk/release/app-release.apk`.

## Importante antes de actualizar

- No desinstales una instalación existente para resolver un error de firma sin exportar antes tus datos.
- Una instalación antigua creada con una clave temporal de CI puede no aceptar el APK de la nueva clave de respaldo. Android no permite recuperar la clave privada a partir del APK.
- La caché de la clave de respaldo de GitHub puede caducar o eliminarse. Para actualizaciones fiables a largo plazo, configura los cuatro secretos con el mismo keystore privado que se usó para firmar la aplicación instalada.
- Los proveedores de OAuth pueden vincular la autorización a la huella SHA-1 del certificado. Al cambiar de certificado puede ser necesario actualizar la configuración OAuth.

No guardes contraseñas, keystores ni contenido Base64 en archivos versionados.
