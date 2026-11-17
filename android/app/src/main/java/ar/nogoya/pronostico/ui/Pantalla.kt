package ar.nogoya.pronostico.ui

import android.Manifest
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import ar.nogoya.pronostico.aviso.Preferencias
import ar.nogoya.pronostico.aviso.ProgramadorAvisos
import ar.nogoya.pronostico.datos.HoraPrevista
import ar.nogoya.pronostico.datos.Informe
import ar.nogoya.pronostico.datos.ResultadoLatest
import ar.nogoya.pronostico.datos.Vigencia
import ar.nogoya.pronostico.datos.ZONA_NOGOYA
import ar.nogoya.pronostico.datos.edad
import ar.nogoya.pronostico.datos.formatearDuracion
import ar.nogoya.pronostico.datos.construirTextoCorto
import ar.nogoya.pronostico.datos.formatoNumero
import ar.nogoya.pronostico.datos.vigencia
import ar.nogoya.pronostico.widget.PronosticoWidget
import kotlinx.coroutines.delay
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

private val VERDE = Color(0xFF1B8F5A)
private val ROJO = Color(0xFFD64545)
private val AMBAR = Color(0xFFC98A12)
private val AZUL = Color(0xFF4DA3FF)
private val AMBAR_CLARO = Color(0xFFFFC857)
private val FORMATO_GENERADO: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM HH:mm")
private val FORMATO_HORA: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val FORMATO_DIA: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM")

@Composable
fun PantallaPrincipal(vm: PronosticoViewModel = viewModel()) {
    val estado by vm.estado.collectAsState()
    val ahora by produceState(initialValue = ZonedDateTime.now(ZONA_NOGOYA)) {
        while (true) {
            delay(30_000)
            value = ZonedDateTime.now(ZONA_NOGOYA)
        }
    }
    val contexto = LocalContext.current

    // --- Avisos de pronóstico nuevo (notificación) ---
    val prefs = remember { Preferencias(contexto) }
    var avisos by remember { mutableStateOf(prefs.avisosActivados) }
    var abrirWa by remember { mutableStateOf(prefs.abrirWhatsApp) }
    var ajustes by remember { mutableStateOf(false) }
    val pedirPermiso = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { concedido ->
        if (concedido) {
            prefs.avisosActivados = true
            avisos = true
            ProgramadorAvisos.programar(contexto)
        } else {
            prefs.avisosActivados = false
            avisos = false
            Toast.makeText(
                contexto,
                "Sin permiso de notificaciones no puedo avisarte. Actívalo en Ajustes del teléfono.",
                Toast.LENGTH_LONG,
            ).show()
        }
    }
    fun activarAvisos() {
        val necesitaPermiso = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(contexto, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        if (necesitaPermiso) {
            pedirPermiso.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            prefs.avisosActivados = true
            avisos = true
            ProgramadorAvisos.programar(contexto)
        }
    }
    fun desactivarAvisos() {
        prefs.avisosActivados = false
        avisos = false
        // El widget también necesita la revisión periódica: solo se cancela si no hay widgets.
        if (!PronosticoWidget.hayWidgets(contexto)) ProgramadorAvisos.cancelar(contexto)
    }
    LaunchedEffect(Unit) {
        if (!prefs.inicializado) {
            // Primera vez: se activan los avisos (pidiendo el permiso si hace falta).
            prefs.inicializado = true
            activarAvisos()
        } else if (prefs.avisosActivados) {
            ProgramadorAvisos.programar(contexto)
        }
    }
    val informe = (estado.resultado as? ResultadoLatest.Pronostico)?.informe
    val vigente = informe != null && informe.vigencia(ahora) == Vigencia.VIGENTE
    val horizontal = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    // Copiar y compartir se revalidan al tocar: nunca se publica un informe vencido.
    fun conInformeVigente(accion: (Informe) -> Unit) {
        val actual = informe
        if (actual != null && actual.vigencia(ZonedDateTime.now(ZONA_NOGOYA)) == Vigencia.VIGENTE) {
            accion(actual)
        } else {
            Toast.makeText(contexto, "El informe está vencido. Actualiza primero.", Toast.LENGTH_LONG).show()
        }
    }

    // Copia y, si el ajuste está activo, abre WhatsApp (solo lo abre; la persona pega y publica).
    fun copiarYAbrir(texto: String) {
        copiarTexto(contexto, texto)
        if (abrirWa) abrirWhatsApp(contexto)
    }

    CompositionLocalProvider(LocalContentColor provides Color.White) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(Color(0xFF0A1020), Color(0xFF12203D))))
                .systemBarsPadding()
        ) {
            Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) { Encabezado(informe) }
                    TextButton(onClick = { ajustes = !ajustes }) { Text("⚙️", fontSize = 22.sp) }
                }
                if (ajustes) {
                    FilaAjuste("🔔 Avisos y alertas de lluvia", avisos) { marcado ->
                        if (marcado) activarAvisos() else desactivarAvisos()
                    }
                    FilaAjuste("📲 Abrir WhatsApp después de copiar", abrirWa) { marcado ->
                        abrirWa = marcado
                        prefs.abrirWhatsApp = marcado
                    }
                }
                if (estado.cargando) {
                    LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 4.dp))
                }
                Box(Modifier.weight(1f)) {
                    Contenido(estado, informe, ahora, horizontal)
                }
                BarraAcciones(
                    cargando = estado.cargando,
                    hayInforme = informe != null,
                    puedePublicar = vigente,
                    onActualizar = { vm.actualizar() },
                    onCopiarCorto = { conInformeVigente { i -> copiarYAbrir(construirTextoCorto(i)) } },
                    onCopiarCompleto = { conInformeVigente { i -> copiarYAbrir(i.textoOriginal) } },
                    onCompartir = { conInformeVigente { i -> compartirTexto(contexto, i.textoOriginal) } },
                )
            }
        }
    }
}

@Composable
private fun Encabezado(informe: Informe?) {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text("Nogoyá, Entre Ríos", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        val detalle = if (informe != null) {
            "Actualizado ${informe.generado.format(FORMATO_GENERADO)} hs" +
                (informe.datosHora?.let { " · datos de las $it" } ?: "")
        } else {
            "Pronóstico en vivo"
        }
        Text(detalle, fontSize = 13.sp, modifier = Modifier.alpha(0.7f))
    }
}

@Composable
private fun Contenido(estado: UiEstado, informe: Informe?, ahora: ZonedDateTime, horizontal: Boolean) {
    val resultado = estado.resultado
    when {
        resultado is ResultadoLatest.NoDisponible -> {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Avisos(estado, null, ahora)
                TarjetaNoDisponible(resultado)
            }
        }
        informe != null -> {
            if (horizontal) {
                Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(
                        Modifier.weight(0.9f).fillMaxHeight().verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Avisos(estado, informe, ahora)
                        Hero(informe, informe.vigencia(ahora) != Vigencia.VIGENTE)
                        TarjetaSol(informe)
                    }
                    Column(
                        Modifier.weight(1.1f).fillMaxHeight().verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Metricas(informe, 3)
                        TarjetaLluvia(informe)
                        TarjetaModelos(informe)
                        FranjaHoras(informe.horas)
                        GraficoHoras(informe.horas)
                        TarjetaDias(informe)
                    }
                }
            } else {
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Avisos(estado, informe, ahora)
                    Hero(informe, informe.vigencia(ahora) != Vigencia.VIGENTE)
                    Metricas(informe, 2)
                    TarjetaLluvia(informe)
                    TarjetaModelos(informe)
                    TarjetaSol(informe)
                    FranjaHoras(informe.horas)
                    GraficoHoras(informe.horas)
                    TarjetaDias(informe)
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
        estado.cargando -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Descargando pronóstico…", fontSize = 16.sp)
            }
        }
        else -> {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Avisos(estado, null, ahora)
                Tarjeta {
                    Text("⚠️ No hay datos para mostrar", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    Text("Toca Actualizar para intentarlo de nuevo.", fontSize = 14.sp)
                }
            }
        }
    }
}

@Composable
private fun Avisos(estado: UiEstado, informe: Informe?, ahora: ZonedDateTime) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (informe != null) {
            if (informe.vigencia(ahora) == Vigencia.VIGENTE) {
                Banner("✅ Vigente ${textoVigencia(informe)}", VERDE)
            } else {
                Banner(
                    "⛔ VENCIDO: no es el clima actual (informe de hace " +
                        "${formatearDuracion(informe.edad(ahora))}). Actualiza antes de publicar.",
                    ROJO,
                )
            }
        }
        estado.aviso?.let { Banner("📡 $it", AMBAR) }
        estado.error?.let { Banner("⚠️ $it", AMBAR) }
    }
}

private fun textoVigencia(informe: Informe): String {
    val limite = informe.vigenteHasta ?: return ""
    val hora = limite.format(FORMATO_HORA)
    return if (limite.toLocalDate() != informe.generado.toLocalDate()) {
        "hasta las $hora (${limite.format(FORMATO_DIA)})"
    } else {
        "hasta las $hora"
    }
}

@Composable
private fun Banner(texto: String, color: Color) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(color.copy(alpha = 0.85f))
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Text(texto, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun Tarjeta(contenido: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White.copy(alpha = 0.08f))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        contenido()
    }
}

@Composable
private fun TarjetaNoDisponible(resultado: ResultadoLatest.NoDisponible) {
    Tarjeta {
        Text("⚠️ Pronóstico no disponible", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Text(resultado.motivo ?: "El motor no pudo generar un pronóstico confiable.", fontSize = 15.sp)
        Text(
            "No se publica nada hasta que haya datos confiables. Intenta Actualizar más tarde.",
            fontSize = 14.sp,
            modifier = Modifier.alpha(0.75f),
        )
    }
}

@Composable
private fun Hero(informe: Informe, atenuado: Boolean) {
    val colores = degradado(ambienteDe(informe.emojiClima))
    Box(
        Modifier
            .fillMaxWidth()
            .alpha(if (atenuado) 0.55f else 1f)
            .clip(RoundedCornerShape(28.dp))
            .background(Brush.linearGradient(colores))
            .padding(20.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(informe.emojiClima, fontSize = 60.sp)
                Spacer(Modifier.width(16.dp))
                Column {
                    Text("${informe.temperatura}°", fontSize = 64.sp, fontWeight = FontWeight.Bold)
                    Text(informe.descripcion, fontSize = 18.sp)
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                informe.sensacion?.let { Text("Sensación $it°", fontSize = 15.sp) }
                informe.maxima?.let { Text("🔺$it°", fontSize = 15.sp) }
                informe.minima?.let { Text("🔻$it°", fontSize = 15.sp) }
            }
        }
    }
}

private data class Metrica(val emoji: String, val titulo: String, val valor: String)

private fun metricas(i: Informe): List<Metrica> = buildList {
    i.humedad?.let { add(Metrica("💧", "Humedad", "$it%")) }
    i.puntoRocio?.let { add(Metrica("🌫️", "Punto de rocío", "$it°")) }
    i.presion?.let { add(Metrica("📊", "Presión", "$it hPa")) }
    i.uv?.let {
        val nivel = i.uvNivel?.let { n -> " · $n" } ?: ""
        add(Metrica(emojiUv(i.uvNivel), "Índice UV", formatoNumero(it, 1) + nivel))
    }
    i.visibilidadKm?.let {
        add(Metrica("👁️", "Visibilidad", "${formatoNumero(it, if (it >= 10) 0 else 1)} km"))
    }
    i.vientoKmh?.let {
        val dir = i.vientoDir?.let { d -> " $d" } ?: ""
        add(Metrica("💨", "Viento", "$it km/h$dir"))
    }
    i.rafagasKmh?.let { add(Metrica("🌬️", "Ráfagas", "$it km/h")) }
}

@Composable
private fun Metricas(informe: Informe, columnas: Int) {
    val lista = metricas(informe)
    if (lista.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        lista.chunked(columnas).forEach { fila ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                fila.forEach { metrica -> TarjetaMetrica(metrica, Modifier.weight(1f)) }
                repeat(columnas - fila.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun TarjetaMetrica(metrica: Metrica, modifier: Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = 0.08f))
            .padding(14.dp)
    ) {
        Text("${metrica.emoji} ${metrica.titulo}", fontSize = 12.sp, modifier = Modifier.alpha(0.75f))
        Spacer(Modifier.height(4.dp))
        Text(metrica.valor, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun TarjetaLluvia(informe: Informe) {
    val hayDatos = informe.sinLluvia || informe.probLluviaMax != null || informe.lluviaMm != null ||
        informe.chaparrones != null || informe.tormentas != null
    if (!hayDatos) return
    Tarjeta {
        Text("🌧️ Lluvia · próximas 24 h", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        if (informe.sinLluvia) {
            Text("☀️ Sin lluvia prevista", fontSize = 15.sp)
        } else {
            informe.probLluviaMax?.let { prob ->
                Text("Probabilidad máxima: $prob%", fontSize = 14.sp)
                BarraProbabilidad(prob)
            }
            informe.lluviaMm?.let { Text("💦 ${formatoNumero(it, 1)} mm acumulados", fontSize = 15.sp) }
            informe.chaparrones?.let { Text("🌦️ Chaparrones: $it", fontSize = 15.sp) }
            informe.tormentas?.let { AvisoTormenta("⛈️ Tormentas: $it") }
        }
    }
}

@Composable
private fun BarraProbabilidad(porcentaje: Int) {
    val fraccion = porcentaje.coerceIn(0, 100) / 100f
    Box(
        Modifier
            .fillMaxWidth()
            .height(10.dp)
            .clip(RoundedCornerShape(5.dp))
            .background(Color.White.copy(alpha = 0.12f))
    ) {
        Box(Modifier.fillMaxWidth(fraccion).fillMaxHeight().background(AZUL))
    }
}

@Composable
private fun AvisoTormenta(texto: String) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF6B3FA0).copy(alpha = 0.6f))
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(texto, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun TarjetaSol(informe: Informe) {
    if (informe.amanecer == null && informe.atardecer == null) return
    Tarjeta {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            informe.amanecer?.let { Text("🌅 Amanecer $it", fontSize = 15.sp) }
            informe.atardecer?.let { Text("🌇 Atardecer $it", fontSize = 15.sp) }
        }
    }
}

@Composable
private fun FranjaHoras(horas: List<HoraPrevista>) {
    if (horas.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("⏱️ Próximas 24 horas · desliza →", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(horas) { hora -> TarjetaHora(hora) }
        }
    }
}

@Composable
private fun TarjetaHora(hora: HoraPrevista) {
    val lluvioso = (hora.probLluvia ?: 0) >= 40
    val fondo = if (lluvioso) AZUL.copy(alpha = 0.30f) else Color.White.copy(alpha = 0.08f)
    val etiquetaDia = etiquetaCorta(hora.dia)
    val esHoy = etiquetaDia == "Hoy"
    Column(
        Modifier
            .width(70.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(fondo)
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            etiquetaDia.ifEmpty { " " },
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (esHoy) Color.White.copy(alpha = 0.6f) else AMBAR_CLARO,
        )
        Text("${hora.hora.toString().padStart(2, '0')}h", fontSize = 13.sp, modifier = Modifier.alpha(0.8f))
        Text(hora.emoji, fontSize = 28.sp)
        Text(hora.temperatura?.let { "$it°" } ?: "s/d", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text(hora.probLluvia?.let { "💧$it%" } ?: "💧–", fontSize = 12.sp)
    }
}

@Composable
private fun BarraAcciones(
    cargando: Boolean,
    hayInforme: Boolean,
    puedePublicar: Boolean,
    onActualizar: () -> Unit,
    onCopiarCorto: () -> Unit,
    onCopiarCompleto: () -> Unit,
    onCompartir: () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onActualizar,
                enabled = !cargando,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 6.dp),
            ) { Text("🔄 Actualizar", fontSize = 14.sp, maxLines = 1) }
            FilledTonalButton(
                onClick = onCompartir,
                enabled = puedePublicar,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 6.dp),
            ) { Text("📤 Compartir", fontSize = 14.sp, maxLines = 1) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(
                onClick = onCopiarCorto,
                enabled = puedePublicar,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 6.dp),
            ) { Text("📝 Copiar corto", fontSize = 14.sp, maxLines = 1) }
            FilledTonalButton(
                onClick = onCopiarCompleto,
                enabled = puedePublicar,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 6.dp),
            ) { Text("📋 Copiar completo", fontSize = 14.sp, maxLines = 1) }
        }
        if (hayInforme && !puedePublicar) {
            Text(
                "Copiar y Compartir se activan solo con un informe vigente.",
                fontSize = 12.sp,
                modifier = Modifier.alpha(0.7f),
            )
        }
    }
}

@Composable
private fun FilaAjuste(texto: String, activado: Boolean, alCambiar: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(texto, fontSize = 14.sp, modifier = Modifier.weight(1f).alpha(0.9f))
        Switch(checked = activado, onCheckedChange = alCambiar)
    }
}

@Composable
private fun GraficoHoras(horas: List<HoraPrevista>) {
    val temperaturas = horas.mapNotNull { it.temperatura }
    if (horas.size < 2 || temperaturas.isEmpty()) return
    val minimo = temperaturas.min()
    val maximo = temperaturas.max()
    Tarjeta {
        Text("📈 Temperatura y lluvia · 24 h", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Canvas(Modifier.fillMaxWidth().height(150.dp)) {
            val ancho = size.width
            val alto = size.height
            val zonaLinea = alto * 0.68f
            val margen = 12f
            val margenX = 10f
            val rango = (maximo - minimo).coerceAtLeast(1).toFloat()
            val paso = (ancho - 2 * margenX) / (horas.size - 1)

            fun xDe(indice: Int): Float = margenX + indice * paso
            fun yDe(t: Int): Float = zonaLinea - margen - (t - minimo) / rango * (zonaLinea - 2 * margen)

            // Barras: probabilidad de lluvia de cada hora
            val alturaBarras = alto - zonaLinea - 4f
            val anchoBarra = paso * 0.6f
            horas.forEachIndexed { indice, hora ->
                val prob = (hora.probLluvia ?: 0).coerceIn(0, 100)
                val altoBarra = alturaBarras * prob / 100f
                drawRect(
                    color = AZUL.copy(alpha = 0.55f),
                    topLeft = Offset(xDe(indice) - anchoBarra / 2f, alto - altoBarra),
                    size = Size(anchoBarra, altoBarra),
                )
            }

            // Separador entre hoy y mañana
            horas.forEachIndexed { indice, hora ->
                if (indice > 0 && hora.dia != horas[indice - 1].dia) {
                    val x = xDe(indice) - paso / 2f
                    drawLine(
                        color = Color.White.copy(alpha = 0.35f),
                        start = Offset(x, 0f),
                        end = Offset(x, alto),
                        strokeWidth = 2f,
                    )
                }
            }

            // Línea de temperatura
            for (indice in 0 until horas.size - 1) {
                val a = horas[indice].temperatura
                val b = horas[indice + 1].temperatura
                if (a != null && b != null) {
                    drawLine(
                        color = AMBAR_CLARO,
                        start = Offset(xDe(indice), yDe(a)),
                        end = Offset(xDe(indice + 1), yDe(b)),
                        strokeWidth = 5f,
                        cap = StrokeCap.Round,
                    )
                }
            }
            horas.forEachIndexed { indice, hora ->
                hora.temperatura?.let { t ->
                    drawCircle(color = AMBAR_CLARO, radius = 5f, center = Offset(xDe(indice), yDe(t)))
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(etiquetaHora(horas.first()), fontSize = 12.sp, modifier = Modifier.alpha(0.75f))
            Text("Mín $minimo° · Máx $maximo°", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Text(etiquetaHora(horas.last()), fontSize = 12.sp, modifier = Modifier.alpha(0.75f))
        }
        Text(
            "Línea: temperatura · Barras: probabilidad de lluvia · Raya vertical: cambio de día",
            fontSize = 11.sp,
            modifier = Modifier.alpha(0.6f),
        )
    }
}

private fun etiquetaHora(hora: HoraPrevista): String =
    "${etiquetaCorta(hora.dia)} ${hora.hora.toString().padStart(2, '0')}h".trim()

@Composable
private fun TarjetaLista(titulo: String, lineas: List<String>, pie: String? = null) {
    if (lineas.isEmpty()) return
    Tarjeta {
        Text(titulo, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        lineas.forEach { linea -> Text(linea, fontSize = 14.sp) }
        if (pie != null) Text(pie, fontSize = 11.sp, modifier = Modifier.alpha(0.6f))
    }
}

@Composable
private fun TarjetaModelos(informe: Informe) = TarjetaLista(
    titulo = "🧪 Comparación de modelos · lluvia",
    lineas = informe.modelos,
    pie = "Cada modelo de pronóstico por separado. Si coinciden, hay más confianza en el horario.",
)

@Composable
private fun TarjetaDias(informe: Informe) = TarjetaLista(
    titulo = "📆 Próximos días",
    lineas = informe.dias,
)
