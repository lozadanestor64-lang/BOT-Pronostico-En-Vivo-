package ar.nogoya.pronostico.aviso

import android.content.Context

class Preferencias(contexto: Context) {
    private val prefs = contexto.applicationContext
        .getSharedPreferences("ajustes", Context.MODE_PRIVATE)

    /** Falso hasta que la app configure los avisos por primera vez. */
    var inicializado: Boolean
        get() = prefs.getBoolean("inicializado", false)
        set(valor) = prefs.edit().putBoolean("inicializado", valor).apply()

    var avisosActivados: Boolean
        get() = prefs.getBoolean("avisos", false)
        set(valor) = prefs.edit().putBoolean("avisos", valor).apply()

    /** Instante (ms) del último informe del que ya se avisó. */
    var ultimoAvisado: Long
        get() = prefs.getLong("ultimo_avisado", 0L)
        set(valor) = prefs.edit().putLong("ultimo_avisado", valor).apply()

    /** Último evento de alerta ya enviado ("tormenta|2026-11-09"); vacío si no hay alerta activa. */
    var ultimaAlerta: String
        get() = prefs.getString("ultima_alerta", "") ?: ""
        set(valor) = prefs.edit().putString("ultima_alerta", valor).apply()

    /** Si es true, al copiar el texto la app abre WhatsApp (solo lo abre; no pega ni publica nada). */
    var abrirWhatsApp: Boolean
        get() = prefs.getBoolean("abrir_whatsapp", true)
        set(valor) = prefs.edit().putBoolean("abrir_whatsapp", valor).apply()
}
