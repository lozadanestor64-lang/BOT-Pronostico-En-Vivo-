"""Convierte el JSON de Open-Meteo en modelos validados."""

from __future__ import annotations

from datetime import date, datetime
from typing import Any

from .config import ZONA
from .errores import ErrorDatos
from .modelos import Actual, DiaHoy, HoraPronostico, Pronostico
from .validacion import limpiar_numero, validar_estructura, validar_frescura

_PUNTOS = (
    "N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE",
    "S", "SSO", "SO", "OSO", "O", "ONO", "NO", "NNO",
)


def grados_a_cardinal(grados: float | None) -> str | None:
    """Convierte grados (0-360) a punto cardinal en español (O = oeste)."""
    if grados is None:
        return None
    return _PUNTOS[int((grados % 360) / 22.5 + 0.5) % 16]


def _hora(texto: Any, nombre: str) -> datetime:
    if not isinstance(texto, str):
        raise ErrorDatos(f"Hora inválida en '{nombre}'")
    try:
        dt = datetime.fromisoformat(texto)
    except ValueError as exc:
        raise ErrorDatos(f"Hora inválida en '{nombre}': {texto!r}") from exc
    return dt.replace(tzinfo=ZONA) if dt.tzinfo is None else dt.astimezone(ZONA)


def _hora_opcional(texto: Any) -> datetime | None:
    try:
        return _hora(texto, "opcional") if texto is not None else None
    except ErrorDatos:
        return None


def _serie(seccion: dict, nombre: str, n: int, adv: list[str]) -> list[float | None]:
    valores = seccion.get(nombre)
    if valores is None:
        adv.append(f"Sin datos de '{nombre}'")
        return [None] * n
    if not isinstance(valores, list) or len(valores) != n:
        raise ErrorDatos(f"La serie '{nombre}' tiene una longitud inconsistente")
    return [limpiar_numero(nombre, v, adv) for v in valores]


def _escalar(seccion: dict, nombre: str, adv: list[str]) -> float | None:
    if nombre not in seccion:
        adv.append(f"Sin datos actuales de '{nombre}'")
        return None
    return limpiar_numero(nombre, seccion[nombre], adv)


def _codigo(valor: float | None) -> int | None:
    return None if valor is None else int(valor)


def _es_dia(dt: datetime, sol: dict[date, tuple[datetime | None, datetime | None]]) -> bool:
    amanecer, atardecer = sol.get(dt.date(), (None, None))
    if amanecer and atardecer:
        return amanecer <= dt < atardecer
    return 6 <= dt.hour < 20


def procesar(crudo: dict, ahora: datetime | None = None) -> Pronostico:
    """Valida y transforma la respuesta. Lanza ErrorDatos si no es confiable."""
    ahora = (ahora or datetime.now(ZONA)).astimezone(ZONA)
    validar_estructura(crudo)
    adv: list[str] = []

    # --- Actual (con control de frescura) ---
    cur = crudo["current"]
    hora_datos = _hora(cur.get("time"), "current.time")
    validar_frescura(hora_datos, ahora)
    temperatura = _escalar(cur, "temperature_2m", adv)
    if temperatura is None:
        raise ErrorDatos("Falta la temperatura actual; no se publica un pronóstico sin ella")

    # --- Series horarias ---
    hourly = crudo["hourly"]
    horas = [_hora(t, "hourly.time") for t in hourly["time"]]
    n = len(horas)
    temp_h = _serie(hourly, "temperature_2m", n, adv)
    rocio_h = _serie(hourly, "dew_point_2m", n, adv)
    prob_h = _serie(hourly, "precipitation_probability", n, adv)
    prec_h = _serie(hourly, "precipitation", n, adv)
    chap_h = _serie(hourly, "showers", n, adv)
    cod_h = _serie(hourly, "weather_code", n, adv)
    vis_h = _serie(hourly, "visibility", n, adv)
    uv_h = _serie(hourly, "uv_index", n, adv)

    # --- Diario ---
    daily = crudo["daily"]
    fechas = [_hora(t, "daily.time").date() for t in daily["time"]]
    nd = len(fechas)
    amaneceres = daily.get("sunrise") or [None] * nd
    atardeceres = daily.get("sunset") or [None] * nd
    if len(amaneceres) != nd or len(atardeceres) != nd:
        raise ErrorDatos("Las series de amanecer/atardecer tienen longitud inconsistente")
    sol = {f: (_hora_opcional(a), _hora_opcional(s)) for f, a, s in zip(fechas, amaneceres, atardeceres)}
    maximas = _serie(daily, "temperature_2m_max", nd, adv)
    minimas = _serie(daily, "temperature_2m_min", nd, adv)
    uv_max = _serie(daily, "uv_index_max", nd, adv)
    prec_dia = _serie(daily, "precipitation_sum", nd, adv)
    prob_dia = _serie(daily, "precipitation_probability_max", nd, adv)

    hoy = DiaHoy()
    if ahora.date() in fechas:
        i = fechas.index(ahora.date())
        hoy = DiaHoy(
            fecha=fechas[i], maxima=maximas[i], minima=minimas[i],
            amanecer=sol[fechas[i]][0], atardecer=sol[fechas[i]][1],
            uv_max=uv_max[i], precipitacion_mm=prec_dia[i], prob_lluvia_max=prob_dia[i],
        )
    else:
        adv.append("La respuesta no incluye el día de hoy en 'daily'")

    # --- Hora de los datos: valores que Open-Meteo solo da por hora ---
    base_datos = hora_datos.replace(minute=0, second=0, microsecond=0)
    if base_datos not in horas:
        raise ErrorDatos("El bloque horario de los datos actuales no está en 'hourly'")
    i_dato = horas.index(base_datos)

    es_dia_actual = cur.get("is_day")
    if es_dia_actual not in (0, 1):
        es_dia_actual = _es_dia(hora_datos, sol)

    visibilidad = vis_h[i_dato]
    viento_grados = _escalar(cur, "wind_direction_10m", adv)
    actual = Actual(
        hora=hora_datos,
        temperatura=temperatura,
        sensacion=_escalar(cur, "apparent_temperature", adv),
        humedad=_escalar(cur, "relative_humidity_2m", adv),
        punto_rocio=rocio_h[i_dato],
        presion=_escalar(cur, "pressure_msl", adv),
        uv=uv_h[i_dato],
        visibilidad_km=None if visibilidad is None else visibilidad / 1000,
        viento_kmh=_escalar(cur, "wind_speed_10m", adv),
        viento_grados=viento_grados,
        viento_dir=grados_a_cardinal(viento_grados),
        rafagas_kmh=_escalar(cur, "wind_gusts_10m", adv),
        precipitacion_mm=_escalar(cur, "precipitation", adv),
        codigo=_codigo(_escalar(cur, "weather_code", adv)),
        es_dia=bool(es_dia_actual),
    )

    # --- Próximas 24 horas, a partir de la hora siguiente a 'ahora' ---
    base_ahora = ahora.replace(minute=0, second=0, microsecond=0)
    if base_ahora not in horas:
        raise ErrorDatos("La hora actual no está en el pronóstico horario recibido")
    i0 = horas.index(base_ahora) + 1
    proximas = [
        HoraPronostico(
            hora=horas[i], temperatura=temp_h[i], prob_lluvia=prob_h[i],
            precipitacion_mm=prec_h[i], chaparrones_mm=chap_h[i],
            codigo=_codigo(cod_h[i]), es_dia=_es_dia(horas[i], sol),
        )
        for i in range(i0, min(i0 + 24, n))
    ]
    if not proximas:
        raise ErrorDatos("No hay horas futuras en el pronóstico recibido")
    if len(proximas) < 24:
        adv.append(f"Solo hay {len(proximas)} de 24 horas futuras disponibles")

    return Pronostico(generado=ahora, actual=actual, hoy=hoy, horas=proximas, advertencias=adv)
