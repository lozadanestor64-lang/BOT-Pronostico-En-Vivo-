"""Cliente de Open-Meteo. Es la única parte del proyecto que usa la red."""

from __future__ import annotations

import logging
import time
from typing import Any, Callable

import requests

from . import config
from .errores import ErrorAPI

log = logging.getLogger(__name__)


def obtener_pronostico_crudo(
    sesion: Any = None,
    esperar: Callable[[float], None] = time.sleep,
    reintentos: int = config.REINTENTOS,
) -> dict:
    """Consulta Open-Meteo con timeout y reintentos con espera creciente.

    Reintenta ante fallos de red, HTTP 429/5xx y JSON inválido.
    Un HTTP 4xx distinto de 429 es un error definitivo (no se reintenta).
    """
    propia = sesion is None
    sesion = sesion or requests.Session()
    ultimo_error = "sin detalle"
    try:
        for intento in range(1, reintentos + 1):
            try:
                resp = sesion.get(
                    config.URL_API,
                    params=config.parametros(),
                    timeout=config.TIMEOUT_SEGUNDOS,
                    headers={"User-Agent": config.USER_AGENT},
                )
            except requests.RequestException as exc:
                ultimo_error = f"error de red ({type(exc).__name__})"
            else:
                if resp.status_code == 200:
                    try:
                        datos = resp.json()
                    except ValueError:
                        ultimo_error = "la respuesta no es JSON válido"
                    else:
                        if not isinstance(datos, dict):
                            raise ErrorAPI("La respuesta de Open-Meteo no es un objeto JSON")
                        if datos.get("error"):
                            motivo = datos.get("reason", "sin detalle")
                            raise ErrorAPI(f"Open-Meteo rechazó la consulta: {motivo}")
                        return datos
                elif resp.status_code == 429 or resp.status_code >= 500:
                    ultimo_error = f"HTTP {resp.status_code}"
                else:
                    raise ErrorAPI(f"Open-Meteo respondió HTTP {resp.status_code} (no recuperable)")

            log.warning("Intento %d/%d fallido: %s", intento, reintentos, ultimo_error)
            if intento < reintentos:
                esperar(config.ESPERA_BASE_SEGUNDOS * 2 ** (intento - 1))
    finally:
        if propia:
            sesion.close()
    raise ErrorAPI(f"No se pudo consultar Open-Meteo tras {reintentos} intentos: {ultimo_error}")
