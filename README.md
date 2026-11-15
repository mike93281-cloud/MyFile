# MyFile

Gestor de archivos para Android, sencillo y privado. Funciona desde Android 7.0 (API 24) hasta Android 15.

## Qué hace

- **Inicio** con el espacio usado del almacenamiento interno, categorías (Imágenes, Vídeos, Música, Documentos, APK, Comprimidos) y tus carpetas.
- **Menú lateral** con accesos rápidos.
- **Explorador** en lista o cuadrícula, con miniaturas de fotos y vídeos.
- Copiar, mover, renombrar, eliminar, compartir, crear carpetas, ver detalles.
- Búsqueda por nombre dentro de una carpeta o en todas tus carpetas.
- Ordenar por nombre, fecha o tamaño.
- **Papelera**: lo que borras de tus carpetas se puede restaurar (las fotos, vídeos y música usan la papelera del sistema en Android 11+).
- **Favoritos** y **Recientes**.
- **Editor de texto**, **visor de fotos** (zoom y deslizar) y **reproductor de vídeo y música** integrados.
- **Comprimir y descomprimir ZIP** (con protección contra rutas maliciosas tipo `../`).
- **Analizador de almacenamiento**: qué carpetas ocupan más.
- Mostrar u ocultar archivos ocultos; tamaño total de carpetas en Detalles.
- Tema claro y oscuro automático. Español e inglés.

## Privacidad y permisos

MyFile **no tiene permiso de internet**, así que no puede mandar nada fuera del teléfono. No lleva anuncios, analíticas ni ninguna librería de terceros (el proyecto no tiene dependencias).

| Permiso | Para qué | Cuándo se pide |
|---|---|---|
| `READ_MEDIA_IMAGES` / `READ_MEDIA_VIDEO` / `READ_MEDIA_AUDIO` (Android 13+) | Ver fotos, vídeos y música en sus categorías | Solo al abrir esa categoría; se puede negar |
| `READ_EXTERNAL_STORAGE` (solo Android 12 o anterior) | Lo mismo en versiones antiguas | Solo al abrir una categoría |

Las carpetas se abren con el **selector de Android** (Storage Access Framework): no hace falta permiso y la app solo ve las carpetas que tú eliges. Puedes quitar el acceso desde el inicio con la ✕.

Además: `allowBackup="false"` y reglas que excluyen todo de la copia en la nube.

### Permisos que pedía la app de referencia y MyFile no pide

`INTERNET`, `ACCESS_NETWORK_STATE`, `CHANGE_NETWORK_STATE`, `ACCESS_WIFI_STATE`, `CHANGE_WIFI_STATE`, `CHANGE_WIFI_MULTICAST_STATE`, `READ_PHONE_STATE`, `BLUETOOTH`, `BLUETOOTH_ADMIN`, `ACCESS_SUPERUSER`, `SYSTEM_ALERT_WINDOW`, `GET_TASKS`, `KILL_BACKGROUND_PROCESSES`, `WRITE_SETTINGS`, `SET_WALLPAPER`, `WAKE_LOCK`, `VIBRATE`, `WRITE_EXTERNAL_STORAGE`, `WRITE_MEDIA_STORAGE`, `INSTALL_SHORTCUT`, `UNINSTALL_SHORTCUT`, `CHECK_LICENSE`.

## Limitaciones (por diseño)

- Desde Android 11, el selector del sistema no deja elegir la raíz del almacenamiento ni la carpeta *Download* entera; elige subcarpetas (por ejemplo `DCIM`, `Documents`, o una carpeta dentro de `Download`).
- MyFile no instala APK (no tiene `REQUEST_INSTALL_PACKAGES`).
- Las categorías Documentos, APK y Comprimidos buscan dentro de las carpetas que hayas añadido.
- No incluye funciones de red, nube, gestor de apps ni root: requerirían permisos que van contra la privacidad.

## Cómo compilar

**Opción A: Android Studio.** Abre esta carpeta con *File › Open*, espera a que sincronice y pulsa *Run*, o usa *Build › Build APK(s)*.

**Opción B: línea de comandos** (con el Android SDK instalado):

```bash
./gradlew assembleDebug
# APK en app/build/outputs/apk/debug/app-debug.apk
```

**Opción C: GitHub.** Sube el proyecto a un repositorio de GitHub; el flujo `.github/workflows/build.yml` compila el APK solo y lo deja en la pestaña *Actions › MyFile-apk*.

Para publicar en Google Play hay que generar un APK/AAB de *release* firmado con tu propia clave (*Build › Generate Signed Bundle / APK*).
