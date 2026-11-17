package ar.nogoya.pronostico.ui

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast

/** Copia el texto al portapapeles. La persona lo pega y publica a mano. */
fun copiarTexto(contexto: Context, texto: String) {
    val portapapeles = contexto.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    portapapeles.setPrimaryClip(ClipData.newPlainText("Pronóstico Nogoyá", texto))
    Toast.makeText(contexto, "Pronóstico copiado", Toast.LENGTH_SHORT).show()
}

/**
 * Abre el menú de compartir oficial de Android. No apunta a ninguna aplicación en particular
 * (tampoco a WhatsApp): la persona elige el destino y publica manualmente.
 */
fun compartirTexto(contexto: Context, texto: String) {
    val envio = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, texto)
    }
    val selector = Intent.createChooser(envio, "Compartir pronóstico")
    if (contexto !is Activity) selector.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    contexto.startActivity(selector)
}

/**
 * Abre WhatsApp (o WhatsApp Business) con su intent de lanzamiento normal de Android.
 * Solo abre la aplicación: no escribe, no pega ni publica nada. El Estado lo publica la persona a mano.
 */
fun abrirWhatsApp(contexto: Context): Boolean {
    val gestor = contexto.packageManager
    val intento = gestor.getLaunchIntentForPackage("com.whatsapp")
        ?: gestor.getLaunchIntentForPackage("com.whatsapp.w4b")
    if (intento == null) {
        Toast.makeText(contexto, "WhatsApp no está instalado", Toast.LENGTH_SHORT).show()
        return false
    }
    intento.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    contexto.startActivity(intento)
    return true
}
