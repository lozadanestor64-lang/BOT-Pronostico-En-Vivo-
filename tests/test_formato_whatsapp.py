from dataclasses import replace
from datetime import datetime, timedelta

from pronostico_nogoya.config import ZONA
from pronostico_nogoya.emojis import clima, nivel_uv
from pronostico_nogoya.formato_whatsapp import (
    _rangos_horas, construir_aviso_error, construir_mensaje,
)
from pronostico_nogoya.procesamiento import procesar

LARGO_MAXIMO_LINEA = 60


def mensaje(crudo, ahora):
    return construir_mensaje(procesar(crudo, ahora))


def test_contiene_todas_las_secciones(crudo, ahora):
    m = mensaje(crudo, ahora)
    for esperado in [
        "PRONÓSTICO NOGOYÁ EN VIVO", "Sáb 07/11", "15:05 hs", "AHORA",
        "30°", "ST 30°", "🔺31°", "🔻17°", "48%", "Rocío 16°", "1012 hPa",
        "UV", "24 km", "17 km/h NE", "ráf. 32", "LLUVIA", "Prob. máx. 60%",
        "Chaparrones", "Tormentas", "05:52", "19:34", "PRÓXIMAS 24 H",
        "Datos 15:00", "vigente hasta 18:05",
    ]:
        assert esperado in m, esperado


def test_horarios_de_chaparrones_y_tormentas(crudo, ahora):
    m = mensaje(crudo, ahora)
    assert "Chaparrones: 18h–19h (1,5 mm)" in m
    assert "Tormentas: 19h–20h" in m


def test_filas_horizontales_de_tres_horas(crudo, ahora):
    m = mensaje(crudo, ahora)
    filas = [l for l in m.splitlines() if l.startswith("16h ") or l.startswith("19h ")]
    assert len(filas) == 2
    assert all(f.count(" · ") == 2 for f in filas)


def test_lineas_cortas(crudo, ahora):
    for linea in mensaje(crudo, ahora).splitlines():
        assert len(linea) <= LARGO_MAXIMO_LINEA, linea


def test_formato_whatsapp_sin_markdown_ajeno(crudo, ahora):
    m = mensaje(crudo, ahora)
    assert "**" not in m and "#" not in m
    assert m.count("*") % 2 == 0


def test_nunca_aparecen_none_nan_ni_espacios_dobles(crudo, ahora):
    m = mensaje(crudo, ahora)
    for malo in ["None", "nan", "inf", "  "]:
        assert malo not in m


def test_sin_datos_opcionales_se_omiten_sin_romper(crudo, ahora):
    for clave in ["uv_index", "visibility", "dew_point_2m", "precipitation_probability",
                  "precipitation", "showers", "weather_code"]:
        del crudo["hourly"][clave]
    del crudo["daily"]["sunrise"]
    del crudo["daily"]["sunset"]
    del crudo["current"]["wind_gusts_10m"]
    m = mensaje(crudo, ahora)
    assert "LLUVIA" not in m
    assert "UV" not in m and "Rocío" not in m and "ráf." not in m and "🌅" not in m
    assert "🌡️ *30°*" in m
    assert "None" not in m and "nan" not in m


def test_sin_lluvia_prevista(crudo, ahora):
    n = len(crudo["hourly"]["time"])
    crudo["hourly"]["precipitation_probability"] = [0] * n
    crudo["hourly"]["precipitation"] = [0.0] * n
    crudo["hourly"]["showers"] = [0.0] * n
    crudo["hourly"]["weather_code"] = [0] * n
    m = mensaje(crudo, ahora)
    assert "Sin lluvia prevista" in m
    assert "Tormentas" not in m and "Chaparrones" not in m


def test_vigencia_cruza_medianoche(crudo):
    ahora = datetime(2026, 11, 7, 15, 5, tzinfo=ZONA)
    p = replace(procesar(crudo, ahora), generado=datetime(2026, 11, 7, 22, 30, tzinfo=ZONA))
    assert "vigente hasta 01:30 (08/11)" in construir_mensaje(p)


def test_rangos_horas_unidos_y_separados():
    base = datetime(2026, 11, 7, 0, 0, tzinfo=ZONA)
    horas = [base + timedelta(hours=h) for h in (19, 20, 21, 23)]
    assert _rangos_horas(horas) == "19h–21h, 23h"
    assert _rangos_horas([]) == ""


def test_emojis_dia_y_noche():
    assert clima(0, True)[0] == "☀️"
    assert clima(0, False)[0] == "🌙"
    assert clima(95)[1] == "Tormenta"
    assert clima(None)[1] == "Sin datos"
    assert clima(1234)[1] == "Condición desconocida"


def test_niveles_uv():
    assert nivel_uv(None) is None
    assert [nivel_uv(x)[1] for x in (1, 4, 7, 9, 12)] == ["Bajo", "Moderado", "Alto", "Muy alto", "Extremo"]


# --- Aviso de error ---

def test_aviso_de_error_con_ultimo_valido(ahora):
    ultimo = ahora - timedelta(hours=3, minutes=5)
    a = construir_aviso_error("sin conexión", ahora, ultimo, "historial/2026-11-07_1200.txt")
    assert "NO DISPONIBLE" in a and "NO PUBLICAR" in a
    assert "sin conexión" in a
    assert "hace 3 h 05 min" in a
    assert "vencido" in a
    assert "historial/2026-11-07_1200.txt" in a


def test_aviso_de_error_sin_historial(ahora):
    a = construir_aviso_error("falla", ahora)
    assert "NO PUBLICAR" in a
    assert "Aún no hay pronósticos válidos" in a


def test_aviso_no_contiene_datos_meteorologicos(ahora):
    a = construir_aviso_error("falla", ahora, ahora - timedelta(hours=1))
    for dato in ["🌡️", "°", "hPa", "km/h", "PRÓXIMAS"]:
        assert dato not in a


def test_formato_coincide_con_el_archivo_de_referencia(crudo, ahora):
    """Contrato con la app Android: si el formato cambia, hay que actualizar
    este archivo Y android/app/src/test/resources/ejemplo_latest.txt."""
    from pathlib import Path

    ref = Path(__file__).parent / "fixtures" / "latest_ejemplo.txt"
    assert mensaje(crudo, ahora) == ref.read_text(encoding="utf-8")
