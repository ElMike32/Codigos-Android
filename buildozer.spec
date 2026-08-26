[app]
title = Visor Componentes
package.name = appcomponentes
package.domain = com.empresa.escaneo
source.dir = .
source.include_exts = py,png,jpg,kv,atlas,ico
version = 1.0.0

requirements = python3,kivy,pandas,requests,certifi,urllib3,qrcode,pillow,reportlab,openpyxl

orientation = portrait
fullscreen = 0
android.permissions = INTERNET, WRITE_EXTERNAL_STORAGE, READ_EXTERNAL_STORAGE

android.api = 33
android.minapi = 21
android.ndk = 25b
android.archs = arm64-v8a, armeabi-v7a

[buildozer]
log_level = 2
warn_on_buildozer_tag = 1
