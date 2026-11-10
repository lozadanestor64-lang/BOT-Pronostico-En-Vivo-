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
}
