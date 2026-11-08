"""Emojis y etiquetas en español para códigos WMO, UV y lluvia."""

from __future__ import annotations

CODIGOS_TORMENTA = frozenset({95, 96, 99})
CODIGOS_CHAPARRON = frozenset({80, 81, 82})

_CLIMA: dict[int, tuple[str, str]] = {
    1: ("🌤️", "Mayormente despejado"),
    2: ("⛅", "Parcialmente nublado"),
    3: ("☁️", "Nublado"),
    45: ("🌫️", "Niebla"),
    48: ("🌫️", "Niebla con escarcha"),
    51: ("🌦️", "Llovizna débil"),
    53: ("🌦️", "Llovizna"),
    55: ("🌧️", "Llovizna intensa"),
    56: ("🌧️", "Llovizna helada"),
    57: ("🌧️", "Llovizna helada intensa"),
    61: ("🌧️", "Lluvia débil"),
    63: ("🌧️", "Lluvia"),
    65: ("🌧️", "Lluvia intensa"),
    66: ("🌧️", "Lluvia helada"),
    67: ("🌧️", "Lluvia helada intensa"),
    71: ("🌨️", "Nevada débil"),
    73: ("🌨️", "Nevada"),
    75: ("🌨️", "Nevada intensa"),
    77: ("🌨️", "Granos de nieve"),
    80: ("🌦️", "Chaparrones débiles"),
    81: ("🌧️", "Chaparrones"),
    82: ("🌧️", "Chaparrones fuertes"),
    85: ("🌨️", "Chubascos de nieve"),
    86: ("🌨️", "Chubascos de nieve fuertes"),
    95: ("⛈️", "Tormenta"),
    96: ("⛈️", "Tormenta con granizo"),
    99: ("⛈️", "Tormenta con granizo fuerte"),
}


def clima(codigo: int | None, es_dia: bool = True) -> tuple[str, str]:
    """Devuelve (emoji, descripción) para un código WMO."""
    if codigo is None:
        return "❔", "Sin datos"
    if codigo == 0:
        return ("☀️", "Despejado") if es_dia else ("🌙", "Despejado")
    if codigo == 1 and not es_dia:
        return "🌙", "Mayormente despejado"
    if codigo == 2 and not es_dia:
        return "☁️", "Parcialmente nublado"
    return _CLIMA.get(codigo, ("❔", "Condición desconocida"))


def nivel_uv(uv: float | None) -> tuple[str, str] | None:
    """Devuelve (emoji, etiqueta) según la escala OMS del índice UV."""
    if uv is None:
        return None
    if uv < 3:
        return "🟢", "Bajo"
    if uv < 6:
        return "🟡", "Moderado"
    if uv < 8:
        return "🟠", "Alto"
    if uv < 11:
        return "🔴", "Muy alto"
    return "🟣", "Extremo"
