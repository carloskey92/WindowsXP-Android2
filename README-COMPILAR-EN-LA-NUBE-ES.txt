COMPILAR WINDOWS XP ANDROID EN GITHUB ACTIONS

El flujo de trabajo está en .github/workflows/build-apk.yml.

Para usarlo:
1. Sube el contenido completo del proyecto a la raíz del repositorio.
2. En GitHub abre Actions.
3. Selecciona "Compilar APK Windows XP" y pulsa Run workflow, o haz un commit
   para que se ejecute automáticamente.
4. Espera a que todos los pasos terminen correctamente.
5. En la ejecución abre Artifacts y descarga WindowsXP-Android-APK.
6. Extrae el ZIP descargado para obtener app-debug.apk.

El workflow prepara el archivo de licencia del Android SDK antes de invocar
sdkmanager, instala platform-tools, Android 35 y build-tools 35.0.0, y compila
con Gradle 8.11.1.

Si falla, abre el primer paso rojo y comparte el texto que aparece debajo de
"Error:". Las advertencias de versión de Node.js no equivalen por sí solas a
un fallo de compilación.
