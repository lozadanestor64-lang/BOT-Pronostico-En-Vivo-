"""Modelos tipados. Un valor sin dato es None, nunca cero."""

from __future__ import annotations

from dataclasses import dataclass, field
from datetime import date, datetime


@dataclass(frozen=True, slots=True)
class Actual:
    hora: datetime                      # hora de los datos (no la de generación)
    temperatura: float
    sensacion: float | None = None
    humedad: float | None = None
    punto_rocio: float | None = None
    presion: float | None = None
    uv: float | None = None
    visibilidad_km: float | None = None
    viento_kmh: float | None = None
    viento_grados: float | None = None
    viento_dir: str | None = None       # punto cardinal en español
    rafagas_kmh: float | None = None
    precipitacion_mm: float | None = None
    codigo: int | None = None
    es_dia: bool = True


@dataclass(frozen=True, slots=True)
class DiaHoy:
    fecha: date | None = None
    maxima: float | None = None
    minima: float | None = None
    amanecer: datetime | None = None
    atardecer: datetime | None = None
    uv_max: float | None = None
    precipitacion_mm: float | None = None
    prob_lluvia_max: float | None = None


@dataclass(frozen=True, slots=True)
class HoraPronostico:
    hora: datetime
    temperatura: float | None = None
    prob_lluvia: float | None = None
    precipitacion_mm: float | None = None
    chaparrones_mm: float | None = None
    codigo: int | None = None
    es_dia: bool = True


@dataclass(frozen=True, slots=True)
class Pronostico:
    generado: datetime
    actual: Actual
    hoy: DiaHoy
    horas: list[HoraPronostico]
    advertencias: list[str] = field(default_factory=list)
