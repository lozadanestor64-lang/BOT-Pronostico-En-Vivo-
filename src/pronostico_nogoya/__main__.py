"""Punto de entrada: python -m pronostico_nogoya

Genera latest.txt (y el historial) para copiar y publicar MANUALMENTE.
Este programa nunca accede a WhatsApp.
"""

from __future__ import annotations

import argparse
import logging
import sys
from datetime import datetime
from pathlib import Path
from typing import Callable

from . import config, historial
from .cliente_api import obtener_pronostico_crudo
from .errores import ErrorPronostico
from .formato_whatsapp import construir_aviso_error, construir_mensaje
from .procesamiento import procesar

log = logging.getLogger("pronostico_nogoya")

COD_OK, COD_ERROR_CONTROLADO, COD_ERROR_INESPERADO = 0, 1, 2


def _escribir_aviso(raiz: Path, motivo: str, ahora: datetime) -> None:
    """Sustituye latest.txt por un aviso: jamás queda info vieja como actual."""
    carpeta = raiz / config.CARPETA_HISTORIAL
    previo = historial.ultimo(carpeta)
    fecha = historial.fecha_de_archivo(previo) if previo else None
    archivo = f"{config.CARPETA_HISTORIAL}/{previo.name}" if previo else None
    aviso = construir_aviso_error(motivo, ahora, fecha, archivo)
    historial.escribir_atomico(raiz / config.ARCHIVO_SALIDA, aviso)
    print(aviso)


def ejecutar(
    raiz: Path,
    obtener: Callable[[], dict] = obtener_pronostico_crudo,
    ahora: datetime | None = None,
) -> int:
    """Flujo completo. Devuelve 0 si salió bien y distinto de 0 si hubo error."""
    ahora = (ahora or datetime.now(config.ZONA)).astimezone(config.ZONA)
    try:
        crudo = obtener()
        pronostico = procesar(crudo, ahora)
        texto = construir_mensaje(pronostico)
    except ErrorPronostico as exc:
        log.error("%s", exc)
        _escribir_aviso(raiz, str(exc), ahora)
        return COD_ERROR_CONTROLADO
    except Exception:  # noqa: BLE001 - última barrera: nunca dejar latest.txt viejo
        log.exception("Error inesperado")
        _escribir_aviso(raiz, "error interno inesperado (ver registro de ejecución)", ahora)
        return COD_ERROR_INESPERADO

    for aviso in pronostico.advertencias:
        log.warning("Dato parcial: %s", aviso)

    historial.escribir_atomico(raiz / config.ARCHIVO_SALIDA, texto)
    try:
        ruta = historial.guardar(texto, ahora, raiz / config.CARPETA_HISTORIAL)
        log.info("Historial guardado en %s", ruta)
    except OSError:
        log.exception("No se pudo guardar el historial (latest.txt sí se generó)")
    print(texto)
    return COD_OK


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(
        prog="pronostico_nogoya",
        description="Genera latest.txt con el pronóstico de Nogoyá para copiar y publicar a mano.",
    )
    parser.add_argument(
        "--raiz", type=Path, default=Path.cwd(),
        help="carpeta del repositorio (por defecto, la actual)",
    )
    args = parser.parse_args(argv)
    logging.basicConfig(level=logging.INFO, format="%(levelname)s: %(message)s")
    return ejecutar(args.raiz)


if __name__ == "__main__":
    sys.exit(main())
