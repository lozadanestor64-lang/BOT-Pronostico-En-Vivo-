"""Historial de pronósticos válidos y escritura atómica de archivos."""

from __future__ import annotations

import os
import re
from datetime import datetime
from pathlib import Path

from . import config

_PATRON = re.compile(r"^\d{4}-\d{2}-\d{2}_\d{4}\.txt$")


def escribir_atomico(ruta: Path, texto: str) -> None:
    """Escribe en un temporal y reemplaza: nunca queda un archivo a medias."""
    ruta.parent.mkdir(parents=True, exist_ok=True)
    tmp = ruta.with_name(ruta.name + ".tmp")
    tmp.write_text(texto, encoding="utf-8", newline="\n")
    os.replace(tmp, ruta)


def nombre_archivo(ahora: datetime) -> str:
    return f"{ahora:%Y-%m-%d_%H%M}.txt"


def listar(carpeta: Path) -> list[Path]:
    """Snapshots existentes, del más antiguo al más nuevo. Ignora archivos ajenos."""
    if not carpeta.is_dir():
        return []
    return sorted(p for p in carpeta.iterdir() if p.is_file() and _PATRON.match(p.name))


def ultimo(carpeta: Path) -> Path | None:
    archivos = listar(carpeta)
    return archivos[-1] if archivos else None


def fecha_de_archivo(ruta: Path) -> datetime:
    return datetime.strptime(ruta.stem, "%Y-%m-%d_%H%M").replace(tzinfo=config.ZONA)


def podar(carpeta: Path, maximo: int) -> list[Path]:
    """Elimina los snapshots más antiguos que excedan 'maximo'."""
    if maximo < 1:
        raise ValueError("El máximo del historial debe ser al menos 1")
    sobrantes = listar(carpeta)[:-maximo]
    for ruta in sobrantes:
        ruta.unlink()
    return sobrantes


def guardar(texto: str, ahora: datetime, carpeta: Path, maximo: int = config.HISTORIAL_MAXIMO) -> Path:
    """Guarda un pronóstico VÁLIDO en el historial y aplica la retención."""
    ruta = carpeta / nombre_archivo(ahora)
    escribir_atomico(ruta, texto)
    podar(carpeta, maximo)
    return ruta
