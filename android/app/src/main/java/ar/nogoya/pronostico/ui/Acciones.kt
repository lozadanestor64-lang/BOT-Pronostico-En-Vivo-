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
