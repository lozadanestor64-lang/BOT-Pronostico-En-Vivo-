"""Validación de estructura, unidades, rangos y frescura de los datos."""

from __future__ import annotations

import math
from datetime import datetime

from . import config
from .errores import ErrorDatos, ErrorDatosDesactualizados

UNIDADES_ESPERADAS: dict[str, str] = {
    "temperature_2m": "°C",
    "apparent_temperature": "°C",
    "dew_point_2m": "°C",
    "temperature_2m_max": "°C",
    "temperature_2m_min": "°C",
    "relative_humidity_2m": "%",
    "precipitation_probability": "%",
    "precipitation_probability_max": "%",
    "pressure_msl": "hPa",
    "wind_speed_10m": "km/h",
    "wind_gusts_10m": "km/h",
    "wind_direction_10m": "°",
    "precipitation": "mm",
    "rain": "mm",
    "showers": "mm",
    "precipitation_sum": "mm",
    "visibility": "m",
}

RANGOS_VALIDOS: dict[str, tuple[float, float]] = {
    "temperature_2m": (-60, 60),
    "apparent_temperature": (-90, 80),
    "dew_point_2m": (-90, 40),
    "temperature_2m_max": (-60, 60),
    "temperature_2m_min": (-60, 60),
    "relative_humidity_2m": (0, 100),
    "precipitation_probability": (0, 100),
    "precipitation_probability_max": (0, 100),
    "pressure_msl": (850, 1090),
    "wind_speed_10m": (0, 400),
    "wind_gusts_10m": (0, 500),
    "wind_direction_10m": (0, 360),
    "precipitation": (0, 500),
    "rain": (0, 500),
    "showers": (0, 500),
    "precipitation_sum": (0, 1000),
    "visibility": (0, 200_000),
    "uv_index": (0, 20),
    "uv_index_max": (0, 20),
    "weather_code": (0, 99),
}

SECCIONES = ("current", "hourly", "daily")


def validar_estructura(crudo: dict) -> None:
    """Verifica zona horaria, secciones, listas de tiempo y unidades."""
    if not isinstance(crudo, dict):
        raise ErrorDatos("La respuesta no es un objeto JSON")

    zona = crudo.get("timezone")
    if zona != config.ZONA_HORARIA:
        raise ErrorDatos(f"Zona horaria inesperada: {zona!r} (se esperaba {config.ZONA_HORARIA})")

    for seccion in SECCIONES:
        datos = crudo.get(seccion)
        unidades = crudo.get(f"{seccion}_units")
        if not isinstance(datos, dict) or not datos:
            raise ErrorDatos(f"Falta la sección '{seccion}' en la respuesta")
        if not isinstance(unidades, dict):
            raise ErrorDatos(f"Faltan las unidades de '{seccion}' en la respuesta")
        verificar_unidades(seccion, datos, unidades)

    for seccion in ("hourly", "daily"):
        tiempos = crudo[seccion].get("time")
        if not isinstance(tiempos, list) or not tiempos:
            raise ErrorDatos(f"'{seccion}.time' falta o está vacío")


def verificar_unidades(seccion: str, datos: dict, unidades: dict) -> None:
    """Toda variable con unidad conocida debe venir en la unidad esperada."""
    for nombre in datos:
        esperada = UNIDADES_ESPERADAS.get(nombre)
        if esperada is None:
            continue
        recibida = unidades.get(nombre)
        if recibida != esperada:
            raise ErrorDatos(
                f"Unidad inesperada en {seccion}.{nombre}: {recibida!r} (se esperaba {esperada!r})"
            )


def limpiar_numero(nombre: str, valor: object, advertencias: list[str]) -> float | None:
    """Devuelve un float válido o None. Registra una advertencia si lo descarta."""

    def avisar(mensaje: str) -> None:
        if mensaje not in advertencias:
            advertencias.append(mensaje)

    if valor is None:
        return None
    if isinstance(valor, bool) or not isinstance(valor, (int, float)):
        avisar(f"Valor no numérico descartado en '{nombre}'")
        return None
    if not math.isfinite(valor):
        avisar(f"Valor no finito descartado en '{nombre}'")
        return None
    rango = RANGOS_VALIDOS.get(nombre)
    if rango and not rango[0] <= valor <= rango[1]:
        avisar(f"Valor fuera de rango descartado en '{nombre}'")
        return None
    return float(valor)


def validar_frescura(hora_datos: datetime, ahora: datetime) -> None:
    """Rechaza datos 'actuales' demasiado viejos o provenientes del futuro."""
    minutos = (ahora - hora_datos).total_seconds() / 60
    if minutos > config.FRESCURA_MAX_MINUTOS:
        raise ErrorDatosDesactualizados(
            f"Los datos de Open-Meteo son de las {hora_datos:%H:%M} "
            f"(hace {int(minutos)} min; máximo permitido {config.FRESCURA_MAX_MINUTOS} min)"
        )
    if minutos < -config.TOLERANCIA_FUTURO_MINUTOS:
        raise ErrorDatos(
            f"Los datos figuran a las {hora_datos:%H:%M}, posterior a la hora actual "
            f"({ahora:%H:%M}); posible error de zona horaria o reloj"
        )
