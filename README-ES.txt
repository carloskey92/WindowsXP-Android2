WINDOWS XP SIMULATOR PARA ANDROID
================================

Este proyecto empaqueta el simulador Windows XP como una aplicación Android.
Incluye escritorio XP, menú Inicio, barra de tareas, Bloc de notas, Calculadora
 e Internet Explorer simulado.

MEJORAS DE ESTA VERSIÓN
-----------------------
- Internet Explorer usa un WebView nativo de Android para abrir sitios HTTP/HTTPS
  directamente, evitando la limitación habitual de los iframes que muchos sitios
  bloquean.
- Cada ventana de Internet Explorer tiene su propia ventana de navegación nativa.
- Incluye controles Atrás, Adelante, Recargar, barra de direcciones y Cerrar.
- Se conservan las ventanas abiertas y sus direcciones para poder recuperarlas
  después de que Android vuelva a crear la pantalla.
- Se conserva el estado del escritorio y el Bloc de notas mediante almacenamiento
  local del WebView.
- Incluye una notificación de servicio en primer plano para indicar que la sesión
  está activa mientras el usuario cambia temporalmente de aplicación.
- Maneja la terminación del proceso de renderizado del WebView y vuelve a crear
  la pantalla del simulador.

IMPORTANTE SOBRE SEGUNDO PLANO
------------------------------
Android puede detener cualquier aplicación si necesita memoria, si el usuario la
fuerza a detener o por políticas de batería del fabricante. El servicio en primer
plano reduce la probabilidad de cierre, pero no puede garantizar ejecución eterna.
La sesión del escritorio se guarda para recuperarla al volver a abrir la app.

COMPILAR EN GITHUB ACTIONS
-------------------------
1. Sube el contenido de esta carpeta a la raíz de tu repositorio GitHub.
2. Comprueba que exista .github/workflows/build-apk.yml en esa ruta exacta.
3. Abre la pestaña Actions y ejecuta "Compilar APK Windows XP".
4. Cuando termine en verde, abre la ejecución y descarga el artefacto
   WindowsXP-Android-APK.

El APK de depuración se encuentra en:
app/build/outputs/apk/debug/app-debug.apk
