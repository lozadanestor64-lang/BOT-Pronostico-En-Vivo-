package ar.nogoya.pronostico.aviso

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import ar.nogoya.pronostico.MainActivity
import ar.nogoya.pronostico.R

object Notificaciones {
    private const val CANAL = "pronostico_nuevo"
    private const val ID_NOTIFICACION = 1001

    fun tienePermiso(contexto: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(contexto, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    /** Devuelve true si la notificación se publicó. Una nueva reemplaza a la anterior. */
    @SuppressLint("MissingPermission")
    fun publicar(contexto: Context, contenido: ContenidoAviso, silenciosa: Boolean): Boolean {
        if (!tienePermiso(contexto)) return false
        val gestor = NotificationManagerCompat.from(contexto)
        if (!gestor.areNotificationsEnabled()) return false

        gestor.createNotificationChannel(
            NotificationChannelCompat.Builder(CANAL, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                .setName("Pronóstico nuevo")
                .setDescription("Aviso cada vez que hay un pronóstico nuevo de Nogoyá")
                .build()
        )

        val abrirApp = PendingIntent.getActivity(
            contexto,
            0,
            Intent(contexto, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notificacion = NotificationCompat.Builder(contexto, CANAL)
            .setSmallIcon(R.drawable.ic_notificacion)
            .setContentTitle(contenido.titulo)
            .setContentText(contenido.resumen)
            .setStyle(NotificationCompat.BigTextStyle().bigText(contenido.detalle))
            .setContentIntent(abrirApp)
            .setAutoCancel(true)
            .setSilent(silenciosa)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        gestor.notify(ID_NOTIFICACION, notificacion)
        return true
    }
}
