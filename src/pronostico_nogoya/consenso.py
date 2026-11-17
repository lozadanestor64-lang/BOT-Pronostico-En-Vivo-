"""Comparación de modelos para la lluvia. Nunca debe romper el pronóstico principal."""

from __future__ import annotations

from datetime import datetime

from . import config
from .modelos import Consenso, ResultadoModelo
from .procesamiento import _hora_opcional
from .validacion import limpiar_numero


def calcular_consenso(crudo: dict, horas: list[datetime]) -> Consenso | None:
    """Para cada modelo con datos válidos, qué horas de [horas] llueven (>= umbral) y cuántos mm.

    Devuelve None si hay menos de 2 modelos utilizables: sin comparación no hay consenso.
    """
    if not isinstance(crudo, dict) or not horas:
        return None
    hourly, unidades = crudo.get("hourly"), crudo.get("hourly_units")
    if not isinstance(hourly, dict) or not isinstance(unidades, dict):
        return None
    tiempos = hourly.get("time")
    if not isinstance(tiempos, list) or not tiempos:
        return None
    indice = {}
    for k, t in enumerate(tiempos):
        hora = _hora_opcional(t)
        if hora is not None:
            indice[hora] = k

    descartes: list[str] = []
    resultados: list[ResultadoModelo] = []
    for modelo, nombre in config.MODELOS_COMPARACION.items():
        clave = f"precipitation_{modelo}"
        serie = hourly.get(clave)
        if not isinstance(serie, list) or len(serie) != len(tiempos):
            continue
        if unidades.get(clave) != "mm":   # unidad distinta: se descarta el modelo entero
            continue
        lluvia: list[datetime] = []
        total, con_dato = 0.0, 0
        for h in horas:
            k = indice.get(h)
            if k is None:
                continue
            mm = limpiar_numero("precipitation", serie[k], descartes)
            if mm is None:
                continue
            con_dato += 1
            total += mm
            if mm >= config.UMBRAL_LLUVIA_MM:
                lluvia.append(h)
        if con_dato * 2 >= len(horas):    # al menos la mitad de la ventana con datos
            resultados.append(ResultadoModelo(nombre, lluvia, round(total, 1)))

    if len(resultados) < 2:
        return None
    conjuntos = [set(r.horas_lluvia) for r in resultados]
    mayoria = [h for h in horas if sum(h in c for c in conjuntos) * 2 > len(resultados)]
    return Consenso(resultados, mayoria)
