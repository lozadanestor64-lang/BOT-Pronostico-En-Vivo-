"""Arma el texto para copiar a mano en WhatsApp. Funciones puras, sin red ni E/S."""

from __future__ import annotations

import math
from datetime import datetime, timedelta

from . import config
from .emojis import CODIGOS_CHAPARRON, CODIGOS_TORMENTA, clima, nivel_uv
from .modelos import HoraPronostico, Pronostico

SEPARADOR = "━" * 16
_DIAS = ("Lun", "Mar", "Mié", "Jue", "Vie", "Sáb", "Dom")
POR_FILA = 3


def _red(x: float) -> int:
    return int(math.floor(x + 0.5))


def _grados(x: float | None) -> str:
    return "s/d" if x is None else f"{_red(x)}°"


def _dec(x: float, decimales: int = 1) -> str:
    return f"{x:.{decimales}f}".replace(".", ",")


def _unir(partes: list[str | None], sep: str = " · ") -> str:
    return sep.join(p for p in partes if p)


def _fecha_corta(dt: datetime) -> str:
    return f"{_DIAS[dt.weekday()]} {dt:%d/%m}"


def _rangos_horas(horas: list[datetime]) -> str:
    """Une horas consecutivas: [19, 20, 21] -> '19h–21h'."""
    if not horas:
        return ""
    tramos: list[list[datetime]] = [[horas[0], horas[0]]]
    for h in horas[1:]:
        if h - tramos[-1][1] == timedelta(hours=1):
            tramos[-1][1] = h
        else:
            tramos.append([h, h])
    return ", ".join(
        f"{a:%H}h" if a == b else f"{a:%H}h–{b:%H}h" for a, b in tramos
    )


def _bloque_ahora(p: Pronostico) -> list[str]:
    a, hoy = p.actual, p.hoy
    emoji, texto = clima(a.codigo, a.es_dia)
    lineas = [f"{emoji} *AHORA: {texto}*"]

    maxmin = _unir(
        [
            f"🔺{_grados(hoy.maxima)}" if hoy.maxima is not None else None,
            f"🔻{_grados(hoy.minima)}" if hoy.minima is not None else None,
        ],
        " ",
    )
    lineas.append(
        _unir([
            f"🌡️ *{_grados(a.temperatura)}*",
            f"ST {_grados(a.sensacion)}" if a.sensacion is not None else None,
            maxmin or None,
        ])
    )
    lineas.append(
        _unir([
            f"💧 {_red(a.humedad)}%" if a.humedad is not None else None,
            f"🌫️ Rocío {_grados(a.punto_rocio)}" if a.punto_rocio is not None else None,
        ])
    )
    uv = nivel_uv(a.uv)
    lineas.append(
        _unir([
            f"📊 {_red(a.presion)} hPa" if a.presion is not None else None,
            f"{uv[0]} UV {_dec(a.uv)} ({uv[1]})" if uv and a.uv is not None else None,
        ])
    )
    vis = None
    if a.visibilidad_km is not None:
        km = _red(a.visibilidad_km) if a.visibilidad_km >= 10 else _dec(a.visibilidad_km)
        vis = f"👁️ {km} km"
    viento = None
    if a.viento_kmh is not None:
        viento = f"💨 {_red(a.viento_kmh)} km/h" + (f" {a.viento_dir}" if a.viento_dir else "")
    lineas.append(
        _unir([
            vis,
            viento,
            f"🌬️ ráf. {_red(a.rafagas_kmh)}" if a.rafagas_kmh is not None else None,
        ])
    )
    return [linea for linea in lineas if linea]


def _bloque_lluvia(horas: list[HoraPronostico]) -> list[str]:
    """Lluvia, chaparrones y tormentas. Vacío si no hay ningún dato disponible."""
    probs = [h.prob_lluvia for h in horas if h.prob_lluvia is not None]
    cantidades = [h.precipitacion_mm for h in horas if h.precipitacion_mm is not None]
    prob_max = max(probs) if probs else None
    total = sum(cantidades) if cantidades else None

    chaparrones = [
        h for h in horas
        if (h.chaparrones_mm or 0) > 0 or h.codigo in CODIGOS_CHAPARRON
    ]
    tormentas = [h for h in horas if h.codigo in CODIGOS_TORMENTA]

    if prob_max is None and total is None and not chaparrones and not tormentas:
        return []

    hay_lluvia = bool(prob_max) or bool(total) or chaparrones or tormentas
    lineas = ["🌧️ *LLUVIA (24 h)*"]
    if not hay_lluvia:
        lineas.append("☀️ Sin lluvia prevista")
        return lineas

    lineas.append(
        _unir([
            f"☔ Prob. máx. {_red(prob_max)}%" if prob_max is not None else None,
            f"💦 {_dec(total)} mm" if total is not None else None,
        ])
    )
    if chaparrones:
        mm = sum(h.chaparrones_mm or 0 for h in chaparrones)
        detalle = f" ({_dec(mm)} mm)" if mm > 0 else ""
        lineas.append(f"🌦️ Chaparrones: {_rangos_horas([h.hora for h in chaparrones])}{detalle}")
    if tormentas:
        lineas.append(f"⛈️ Tormentas: {_rangos_horas([h.hora for h in tormentas])}")
    return lineas


def _bloque_sol(p: Pronostico) -> list[str]:
    hoy = p.hoy
    linea = _unir([
        f"🌅 {hoy.amanecer:%H:%M}" if hoy.amanecer else None,
        f"🌇 {hoy.atardecer:%H:%M}" if hoy.atardecer else None,
    ])
    return [linea] if linea else []


def _celda(h: HoraPronostico) -> str:
    emoji, _ = clima(h.codigo, h.es_dia)
    prob = f"{_red(h.prob_lluvia)}%" if h.prob_lluvia is not None else "–"
    return f"{h.hora:%H}h {emoji}{_grados(h.temperatura)} {prob}"


def _bloque_horas(horas: list[HoraPronostico]) -> list[str]:
    lineas = ["⏱️ *PRÓXIMAS 24 H*"]
    for i in range(0, len(horas), POR_FILA):
        lineas.append(" · ".join(_celda(h) for h in horas[i:i + POR_FILA]))
    lineas.append("_hora · clima · °C · % lluvia_")
    return lineas


def construir_mensaje(p: Pronostico, vigencia_horas: int = config.VIGENCIA_HORAS) -> str:
    """Texto final listo para copiar. Incluye hora de los datos y vigencia."""
    vence = p.generado + timedelta(hours=vigencia_horas)
    sufijo_dia = f" ({vence:%d/%m})" if vence.date() != p.generado.date() else ""
    bloques = [
        [
            f"🌦️ *PRONÓSTICO {config.NOMBRE_LUGAR.upper()} EN VIVO*",
            f"📅 {_fecha_corta(p.generado)} · 🕒 {p.generado:%H:%M} hs",
        ],
        _bloque_ahora(p),
        _bloque_lluvia(p.horas),
        _bloque_sol(p),
        _bloque_horas(p.horas),
        [f"🔄 Datos {p.actual.hora:%H:%M} · ⏳ vigente hasta {vence:%H:%M}{sufijo_dia}"],
    ]
    partes = ["\n".join(b) for b in bloques if b]
    return f"\n{SEPARADOR}\n".join(partes) + "\n"


def _edad(delta: timedelta) -> str:
    minutos = max(int(delta.total_seconds() // 60), 0)
    horas, resto = divmod(minutos, 60)
    return f"{horas} h {resto:02d} min" if horas else f"{resto} min"


def construir_aviso_error(
    motivo: str,
    ahora: datetime,
    ultimo_valido: datetime | None = None,
    archivo: str | None = None,
) -> str:
    """Reemplaza latest.txt cuando no hay datos confiables.

    Nunca incluye el contenido de pronósticos anteriores, para que no se
    publique información vieja como si fuera actual.
    """
    lineas = [
        "⚠️ *PRONÓSTICO NO DISPONIBLE*",
        "🚫 *NO PUBLICAR*: no hay datos confiables en este momento.",
        f"🕒 Intento: {_fecha_corta(ahora)} {ahora:%H:%M} hs",
        f"❗ Motivo: {motivo}",
    ]
    if ultimo_valido is not None:
        edad = _edad(ahora - ultimo_valido)
        detalle = f"\n📁 Archivo: {archivo}" if archivo else ""
        lineas.append(
            f"📜 Último pronóstico válido: {_fecha_corta(ultimo_valido)} "
            f"{ultimo_valido:%H:%M} hs (hace {edad}). Está vencido y solo consta en el historial.{detalle}"
        )
    else:
        lineas.append("📜 Aún no hay pronósticos válidos en el historial.")
    lineas.append("🔁 Se reintentará en la próxima ejecución programada.")
    return "\n".join(lineas) + "\n"
