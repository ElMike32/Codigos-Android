[app]
# (str) Title of your application
title = Visor Componentes

# (str) Package name
package.name = appcomponentes

# (str) Package domain (needed for android packaging)
package.domain = com.empresa.escaneo

# (str) Source code where the main.py live
source.dir = .

# (list) Source files to include (include empty to include all the files)
source.include_exts = py,png,jpg,kv,atlas,ico,ttf

# (str) Application versioning
version = 1.0.0

# (list) Application requirements
requirements = python3,kivy,pandas,requests,certifi,urllib3,qrcode,pillow,reportlab,openpyxl

# (str) Supported orientation
orientation = portrait

# (bool) Indicate if the application should be fullscreen or not
fullscreen = 0

# (list) Permissions
android.permissions = INTERNET, READ_EXTERNAL_STORAGE, WRITE_EXTERNAL_STORAGE

# (str) Android SDK path (CRÍTICO: vincula con la ruta donde instalamos sdkmanager en el workflow)
android.sdk_path = ~/.buildozer/android/platform/android-sdk

# (int) Target Android API
android.api = 33

# (int) Minimum API required
android.minapi = 21

# (str) Android NDK version
android.ndk = 25b

# (str) Android SDK build tools version
android.sdk_build_tools_version = 33.0.2

# (bool) Aceptar licencias del SDK automáticamente
android.accept_sdk_licenses = True

# (list) The Android archs to build for
android.archs = arm64-v8a, armeabi-v7a

# (bool) Enable AndroidX support
android.enable_androidx = True

[buildozer]
# (int) Log level (0 = error only, 1 = info, 2 = debug (with command output))
log_level = 2

# (int) Display warning if buildozer is run as root (0 = disable, 1 = enable)
warn_on_buildozer_tag = 1
