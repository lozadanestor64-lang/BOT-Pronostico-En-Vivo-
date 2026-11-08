from datetime import datetime, timedelta, timezone

import pytest

from pronostico_nogoya.config import ZONA
from pronostico_nogoya.errores import ErrorDatos, ErrorDatosDesactualizados
from pronostico_nogoya.procesamiento import grados_a_cardinal, procesar


def test_valores_actuales(crudo, ahora):
    a = procesar(crudo, ahora).actual
    assert a.temperatura == 29.6
    assert a.sensacion == 30.4
    assert a.humedad == 48
    assert a.punto_rocio == 16.2
    assert a.presion == 1011.8
    assert a.viento_kmh == 17.3
    assert a.rafagas_kmh == 31.7
    assert a.viento_dir == "NE"
    assert a.codigo == 2
    assert a.es_dia is True
    assert a.hora == datetime(2026, 11, 7, 15, 0, tzinfo=ZONA)


def test_visibilidad_se_convierte_de_metros_a_km(crudo, ahora):
    assert procesar(crudo, ahora).actual.visibilidad_km == pytest.approx(24.14)


def test_uv_actual_viene_de_la_hora_correspondiente(crudo, ahora):
    esperado = crudo["hourly"]["uv_index"][15]
    assert procesar(crudo, ahora).actual.uv == esperado


def test_dia_amanecer_atardecer(crudo, ahora):
    hoy = procesar(crudo, ahora).hoy
    assert (hoy.maxima, hoy.minima) == (31.2, 17.4)
    assert hoy.amanecer == datetime(2026, 11, 7, 5, 52, tzinfo=ZONA)
    assert hoy.atardecer == datetime(2026, 11, 7, 19, 34, tzinfo=ZONA)
    assert hoy.prob_lluvia_max == 60


def test_proximas_24_horas(crudo, ahora):
    horas = procesar(crudo, ahora).horas
    assert len(horas) == 24
    assert horas[0].hora == datetime(2026, 11, 7, 16, 0, tzinfo=ZONA)
    assert horas[-1].hora == datetime(2026, 11, 8, 15, 0, tzinfo=ZONA)
    assert all(b.hora - a.hora == timedelta(hours=1) for a, b in zip(horas, horas[1:]))


def test_es_dia_usa_amanecer_y_atardecer(crudo, ahora):
    por_hora = {h.hora.hour: h for h in procesar(crudo, ahora).horas[:12]}
    assert por_hora[18].es_dia is True    # antes del atardecer 19:34
    assert por_hora[20].es_dia is False
    assert por_hora[23].es_dia is False


def test_tormenta_y_chaparron_llegan_al_modelo(crudo, ahora):
    horas = {h.hora.hour: h for h in procesar(crudo, ahora).horas[:6]}
    assert horas[18].codigo == 80
    assert horas[19].codigo == 95
    assert horas[19].chaparrones_mm == 0.9


@pytest.mark.parametrize(
    "grados, esperado",
    [(0, "N"), (45, "NE"), (90, "E"), (180, "S"), (270, "O"), (315, "NO"),
     (360, "N"), (350, "N"), (22.5, "NNE"), (None, None)],
)
def test_grados_a_cardinal(grados, esperado):
    assert grados_a_cardinal(grados) == esperado


# --- Datos faltantes ---

def test_variable_opcional_ausente_queda_en_none(crudo, ahora):
    del crudo["hourly"]["uv_index"]
    del crudo["hourly"]["visibility"]
    p = procesar(crudo, ahora)
    assert p.actual.uv is None
    assert p.actual.visibilidad_km is None
    assert any("uv_index" in a for a in p.advertencias)


def test_valor_none_puntual_no_se_vuelve_cero(crudo, ahora):
    crudo["current"]["relative_humidity_2m"] = None
    crudo["hourly"]["dew_point_2m"][15] = None
    p = procesar(crudo, ahora)
    assert p.actual.humedad is None
    assert p.actual.punto_rocio is None


def test_valor_fuera_de_rango_se_descarta_con_advertencia(crudo, ahora):
    crudo["current"]["relative_humidity_2m"] = 250
    p = procesar(crudo, ahora)
    assert p.actual.humedad is None
    assert any("relative_humidity_2m" in a for a in p.advertencias)


def test_sin_amanecer_atardecer(crudo, ahora):
    del crudo["daily"]["sunrise"]
    del crudo["daily"]["sunset"]
    hoy = procesar(crudo, ahora).hoy
    assert hoy.amanecer is None and hoy.atardecer is None


def test_sin_temperatura_actual_es_error(crudo, ahora):
    crudo["current"]["temperature_2m"] = None
    with pytest.raises(ErrorDatos, match="temperatura actual"):
        procesar(crudo, ahora)


def test_serie_de_longitud_inconsistente(crudo, ahora):
    crudo["hourly"]["temperature_2m"] = crudo["hourly"]["temperature_2m"][:10]
    with pytest.raises(ErrorDatos, match="longitud"):
        procesar(crudo, ahora)


def test_hora_actual_fuera_del_pronostico(crudo):
    lejos = datetime(2026, 11, 20, 12, 0, tzinfo=ZONA)
    crudo["current"]["time"] = "2026-11-20T12:00"
    with pytest.raises(ErrorDatos):
        procesar(crudo, lejos)


def test_hora_invalida(crudo, ahora):
    crudo["current"]["time"] = "ayer"
    with pytest.raises(ErrorDatos, match="Hora inválida"):
        procesar(crudo, ahora)


def test_pocas_horas_futuras_genera_advertencia(crudo):
    for clave, valores in crudo["hourly"].items():
        crudo["hourly"][clave] = valores[:30]
    ahora = datetime(2026, 11, 7, 15, 5, tzinfo=ZONA)
    p = procesar(crudo, ahora)
    assert len(p.horas) == 14
    assert any("14 de 24" in a for a in p.advertencias)


# --- Información vieja ---

def test_datos_desactualizados_se_rechazan(crudo):
    tarde = datetime(2026, 11, 7, 18, 0, tzinfo=ZONA)
    with pytest.raises(ErrorDatosDesactualizados):
        procesar(crudo, tarde)


def test_datos_del_futuro_se_rechazan(crudo):
    temprano = datetime(2026, 11, 7, 9, 0, tzinfo=ZONA)
    with pytest.raises(ErrorDatos, match="posterior"):
        procesar(crudo, temprano)


def test_unidades_incorrectas_se_rechazan(crudo, ahora):
    crudo["current_units"]["wind_speed_10m"] = "mph"
    with pytest.raises(ErrorDatos, match="Unidad inesperada"):
        procesar(crudo, ahora)


def test_hora_actual_en_utc_se_normaliza_a_cordoba(crudo):
    en_utc = datetime(2026, 11, 7, 15, 5, tzinfo=ZONA).astimezone(timezone.utc)
    p = procesar(crudo, en_utc)
    assert p.generado.hour == 15
    assert p.generado.tzinfo == ZONA
