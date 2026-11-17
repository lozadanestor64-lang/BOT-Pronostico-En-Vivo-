import copy
import json
from datetime import datetime
from pathlib import Path

import pytest

from pronostico_nogoya.config import ZONA
from pronostico_nogoya.consenso import calcular_consenso
from pronostico_nogoya.procesamiento import procesar

FIXTURE = Path(__file__).parent / "fixtures" / "respuesta_modelos.json"


@pytest.fixture
def modelos():
    with FIXTURE.open(encoding="utf-8") as f:
        return json.load(f)


@pytest.fixture
def horas(crudo, ahora):
    return [h.hora for h in procesar(crudo, ahora).horas]


def horas_de(resultado):
    return [h.strftime("%d %H") for h in resultado.horas_lluvia]


def test_cada_modelo_con_sus_horas_y_milimetros(modelos, horas):
    c = calcular_consenso(modelos, horas)
    por_nombre = {r.nombre: r for r in c.modelos}
    assert set(por_nombre) == {"ECMWF", "GFS", "ICON"}      # GEM no vino: se omite
    assert horas_de(por_nombre["ECMWF"]) == ["08 00", "08 01", "08 02"]
    assert horas_de(por_nombre["GFS"]) == ["08 01", "08 02", "08 03", "08 04", "08 05"]
    assert horas_de(por_nombre["ICON"]) == ["08 02", "08 03", "08 04"]
    assert por_nombre["ECMWF"].total_mm == 9.8
    assert por_nombre["GFS"].total_mm == 5.9


def test_mayoria_es_mas_de_la_mitad_de_los_modelos(modelos, horas):
    c = calcular_consenso(modelos, horas)
    assert [h.strftime("%d %H") for h in c.mayoria] == ["08 01", "08 02", "08 03", "08 04"]


def test_el_umbral_descarta_lloviznas_minimas(modelos, horas):
    c = calcular_consenso(modelos, horas)
    ecmwf = next(r for r in c.modelos if r.nombre == "ECMWF")
    assert datetime(2026, 11, 8, 3, tzinfo=ZONA) not in ecmwf.horas_lluvia   # 0,2 mm < 0,3


def test_un_solo_modelo_no_alcanza(modelos, horas):
    del modelos["hourly"]["precipitation_gfs_seamless"]
    del modelos["hourly"]["precipitation_icon_seamless"]
    assert calcular_consenso(modelos, horas) is None


def test_unidad_distinta_descarta_ese_modelo(modelos, horas):
    modelos["hourly_units"]["precipitation_gfs_seamless"] = "inch"
    c = calcular_consenso(modelos, horas)
    assert {r.nombre for r in c.modelos} == {"ECMWF", "ICON"}


def test_serie_de_largo_inconsistente_descarta_ese_modelo(modelos, horas):
    modelos["hourly"]["precipitation_icon_seamless"] = [0.0] * 10
    assert {r.nombre for r in calcular_consenso(modelos, horas).modelos} == {"ECMWF", "GFS"}


def test_valores_invalidos_no_cuentan_como_lluvia(modelos, horas):
    modelos["hourly"]["precipitation_gfs_seamless"][25] = -5          # fuera de rango
    modelos["hourly"]["precipitation_gfs_seamless"][26] = "mucha"     # no numérico
    gfs = next(r for r in calcular_consenso(modelos, horas).modelos if r.nombre == "GFS")
    assert "08 01" not in horas_de(gfs) and "08 02" not in horas_de(gfs)


def test_modelo_con_pocos_datos_se_descarta(modelos, horas):
    modelos["hourly"]["precipitation_gfs_seamless"] = [None] * 48
    assert {r.nombre for r in calcular_consenso(modelos, horas).modelos} == {"ECMWF", "ICON"}


@pytest.mark.parametrize("roto", [None, [], "texto", {"hourly": None}, {"hourly": {}, "hourly_units": {}},
                                  {"hourly": {"time": []}, "hourly_units": {}}])
def test_respuestas_rotas_devuelven_none_sin_lanzar(roto, horas):
    assert calcular_consenso(roto, horas) is None


def test_sin_horas_devuelve_none(modelos):
    assert calcular_consenso(modelos, []) is None


def test_todos_sin_lluvia(modelos, horas):
    for clave in list(modelos["hourly"]):
        if clave.startswith("precipitation_"):
            modelos["hourly"][clave] = [0.0] * 48
    c = calcular_consenso(modelos, horas)
    assert c.mayoria == [] and all(r.horas_lluvia == [] for r in c.modelos)


def test_hora_desconocida_se_ignora(modelos, horas):
    m = copy.deepcopy(modelos)
    m["hourly"]["time"][0] = "no es una hora"
    assert calcular_consenso(m, horas) is not None
