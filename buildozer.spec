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

# (list) Application requirements - FIXED: Use Cython 0.29.37 for Python 3.9 compatibility
requirements = python3,cython==0.29.37,kivy==2.1.0,requests,certifi,urllib3

# (str) Supported orientation
orientation = portrait

# (bool) Indicate if the application should be fullscreen or not
fullscreen = 0

# (list) Permissions
android.permissions = INTERNET, READ_EXTERNAL_STORAGE, WRITE_EXTERNAL_STORAGE

# (int) Target Android API
android.api = 33

# (int) Minimum API required
android.minapi = 24

# (str) Android NDK version
android.ndk = 25b

# (str) Android SDK build tools version
android.sdk_build_tools_version = 34.0.0

# (bool) Accept SDK licenses automatically
android.accept_sdk_licenses = True

# (list) The Android archs to build for
android.archs = arm64-v8a,armeabi-v7a

# (bool) Enable AndroidX support
android.enable_androidx = True

# PYTHON VERSION FOR ANDROID BUILD - IMPORTANT
android.python_version = 3.9

# CRITICAL: Fix for NDK r25b header conflicts
# These flags prevent mixing host system headers with NDK sysroot
android.add_src = 
android.add_libs_armeabi_v7a = 
android.add_libs_arm64_v8a = 

# Cython configuration for better compatibility
android.cython_cflags = -O0

# Skip Java compilation issues
android.skip_update = False

# Setup logging
[buildozer]
# (int) Log level (0 = error only, 1 = info, 2 = debug (with command output))
log_level = 2

# (int) Display warning if buildozer is run as root (0 = disable, 1 = enable)
warn_on_buildozer_tag = 1
