from datetime import timedelta

import pytest

from pronostico_nogoya.errores import ErrorDatos, ErrorDatosDesactualizados
from pronostico_nogoya.validacion import (
    limpiar_numero, validar_estructura, validar_frescura,
)


def test_fixture_valido(crudo):
    validar_estructura(crudo)


@pytest.mark.parametrize(
    "seccion, variable, unidad_mala",
    [
        ("current", "temperature_2m", "°F"),
        ("current", "wind_speed_10m", "mp/h"),
        ("hourly", "visibility", "ft"),
        ("hourly", "precipitation", "inch"),
        ("daily", "temperature_2m_max", "K"),
        ("current", "pressure_msl", "inHg"),
    ],
)
def test_unidad_inesperada_se_rechaza(crudo, seccion, variable, unidad_mala):
    crudo[f"{seccion}_units"][variable] = unidad_mala
    with pytest.raises(ErrorDatos, match="Unidad inesperada"):
        validar_estructura(crudo)


def test_variable_sin_unidad_se_rechaza(crudo):
    del crudo["current_units"]["temperature_2m"]
    with pytest.raises(ErrorDatos, match="Unidad inesperada"):
        validar_estructura(crudo)


@pytest.mark.parametrize(
    "clave",
    ["current", "hourly", "daily", "current_units", "hourly_units", "daily_units"],
)
def test_falta_seccion(crudo, clave):
    del crudo[clave]
    with pytest.raises(ErrorDatos):
        validar_estructura(crudo)


def test_zona_horaria_distinta(crudo):
    crudo["timezone"] = "UTC"
    with pytest.raises(ErrorDatos, match="Zona horaria"):
        validar_estructura(crudo)


def test_hourly_time_vacio(crudo):
    crudo["hourly"]["time"] = []
    with pytest.raises(ErrorDatos, match="hourly.time"):
        validar_estructura(crudo)


def test_respuesta_no_es_dict():
    with pytest.raises(ErrorDatos):
        validar_estructura([])


@pytest.mark.parametrize(
    "nombre, valor",
    [
        ("relative_humidity_2m", 140),
        ("relative_humidity_2m", -5),
        ("temperature_2m", 999),
        ("pressure_msl", 12),
        ("uv_index", 80),
        ("wind_direction_10m", 400),
        ("precipitation", -1),
        ("temperature_2m", float("nan")),
        ("temperature_2m", float("inf")),
        ("temperature_2m", "25"),
        ("temperature_2m", True),
    ],
)
def test_valores_invalidos_se_descartan(nombre, valor):
    adv = []
    assert limpiar_numero(nombre, valor, adv) is None
    assert len(adv) == 1


def test_valor_none_es_dato_faltante_sin_advertencia():
    adv = []
    assert limpiar_numero("temperature_2m", None, adv) is None
    assert adv == []


def test_valor_valido_se_conserva_y_cero_no_es_faltante():
    adv = []
    assert limpiar_numero("precipitation", 0, adv) == 0.0
    assert limpiar_numero("temperature_2m", -3.5, adv) == -3.5
    assert adv == []


def test_advertencias_no_se_duplican():
    adv = []
    for _ in range(5):
        limpiar_numero("relative_humidity_2m", 500, adv)
    assert len(adv) == 1


def test_frescura_ok(ahora):
    validar_frescura(ahora - timedelta(minutes=5), ahora)
    validar_frescura(ahora - timedelta(minutes=90), ahora)


def test_datos_viejos_se_rechazan(ahora):
    with pytest.raises(ErrorDatosDesactualizados, match="hace 91 min"):
        validar_frescura(ahora - timedelta(minutes=91), ahora)


def test_datos_del_futuro_se_rechazan(ahora):
    with pytest.raises(ErrorDatos, match="posterior"):
        validar_frescura(ahora + timedelta(hours=3), ahora)
