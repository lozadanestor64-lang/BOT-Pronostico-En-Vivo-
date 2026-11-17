from datetime import datetime, timedelta

from pronostico_nogoya import historial
from pronostico_nogoya.__main__ import ejecutar
from pronostico_nogoya.config import ZONA
from pronostico_nogoya.errores import ErrorAPI

VIEJO = "🌡️ *99°* TEMPERATURA VIEJA QUE NO DEBE SOBREVIVIR\n"


def fallar(error):
    def _obtener():
        raise error
    return _obtener


def test_exito_escribe_latest_e_historial(tmp_path, crudo, ahora):
    codigo = ejecutar(tmp_path, obtener=lambda: crudo, ahora=ahora)
    assert codigo == 0
    texto = (tmp_path / "latest.txt").read_text(encoding="utf-8")
    assert "PRONÓSTICO NOGOYÁ EN VIVO" in texto
    snapshots = historial.listar(tmp_path / "historial")
    assert [p.name for p in snapshots] == ["2026-11-07_1505.txt"]
    assert snapshots[0].read_text(encoding="utf-8") == texto


def test_error_de_api_reemplaza_latest_por_aviso(tmp_path, ahora):
    (tmp_path / "latest.txt").write_text(VIEJO, encoding="utf-8")
    codigo = ejecutar(tmp_path, obtener=fallar(ErrorAPI("sin conexión")), ahora=ahora)
    assert codigo == 1
    texto = (tmp_path / "latest.txt").read_text(encoding="utf-8")
    assert "NO PUBLICAR" in texto and "sin conexión" in texto
    assert "99°" not in texto and "VIEJA" not in texto


def test_el_error_no_guarda_nada_en_el_historial(tmp_path, ahora):
    ejecutar(tmp_path, obtener=fallar(ErrorAPI("x")), ahora=ahora)
    assert historial.listar(tmp_path / "historial") == []


def test_datos_desactualizados_producen_aviso(tmp_path, crudo):
    tarde = datetime(2026, 11, 7, 18, 0, tzinfo=ZONA)
    codigo = ejecutar(tmp_path, obtener=lambda: crudo, ahora=tarde)
    assert codigo == 1
    texto = (tmp_path / "latest.txt").read_text(encoding="utf-8")
    assert "NO PUBLICAR" in texto and "hace 180 min" in texto
    assert "PRÓXIMAS 24 H" not in texto


def test_unidades_incorrectas_producen_aviso(tmp_path, crudo, ahora):
    crudo["current_units"]["temperature_2m"] = "°F"
    assert ejecutar(tmp_path, obtener=lambda: crudo, ahora=ahora) == 1
    assert "Unidad inesperada" in (tmp_path / "latest.txt").read_text(encoding="utf-8")


def test_error_inesperado_tambien_deja_aviso(tmp_path, ahora):
    (tmp_path / "latest.txt").write_text(VIEJO, encoding="utf-8")
    codigo = ejecutar(tmp_path, obtener=fallar(RuntimeError("bug")), ahora=ahora)
    assert codigo == 2
    texto = (tmp_path / "latest.txt").read_text(encoding="utf-8")
    assert "NO PUBLICAR" in texto and "VIEJA" not in texto


def test_aviso_menciona_el_ultimo_pronostico_valido(tmp_path, crudo, ahora):
    ejecutar(tmp_path, obtener=lambda: crudo, ahora=ahora)
    despues = ahora + timedelta(hours=3)
    ejecutar(tmp_path, obtener=fallar(ErrorAPI("caído")), ahora=despues)
    texto = (tmp_path / "latest.txt").read_text(encoding="utf-8")
    assert "hace 3 h 00 min" in texto
    assert "historial/2026-11-07_1505.txt" in texto
    assert "🌡️" not in texto  # no se copia el contenido viejo


def test_recuperacion_tras_un_error(tmp_path, crudo, ahora):
    ejecutar(tmp_path, obtener=fallar(ErrorAPI("caído")), ahora=ahora)
    assert ejecutar(tmp_path, obtener=lambda: crudo, ahora=ahora) == 0
    assert "NO PUBLICAR" not in (tmp_path / "latest.txt").read_text(encoding="utf-8")


# ---------- Comparación de modelos ----------

def _modelos():
    import json
    from pathlib import Path

    ruta = Path(__file__).parent / "fixtures" / "respuesta_modelos.json"
    return json.loads(ruta.read_text(encoding="utf-8"))


def test_con_modelos_el_texto_incluye_la_comparacion(tmp_path, crudo, ahora):
    codigo = ejecutar(tmp_path, obtener=lambda: crudo, ahora=ahora, obtener_modelos=_modelos)
    assert codigo == 0
    texto = (tmp_path / "latest.txt").read_text(encoding="utf-8")
    assert "🧪 *MODELOS" in texto and "La mayoría coincide" in texto


def test_si_los_modelos_fallan_el_pronostico_sale_igual(tmp_path, crudo, ahora):
    codigo = ejecutar(tmp_path, obtener=lambda: crudo, ahora=ahora, obtener_modelos=fallar(ErrorAPI("sin red")))
    assert codigo == 0
    texto = (tmp_path / "latest.txt").read_text(encoding="utf-8")
    assert "PRONÓSTICO NOGOYÁ EN VIVO" in texto and "MODELOS" not in texto
    assert "NO PUBLICAR" not in texto


def test_un_error_inesperado_en_los_modelos_tampoco_rompe(tmp_path, crudo, ahora):
    codigo = ejecutar(tmp_path, obtener=lambda: crudo, ahora=ahora, obtener_modelos=fallar(RuntimeError("bug")))
    assert codigo == 0
    assert "MODELOS" not in (tmp_path / "latest.txt").read_text(encoding="utf-8")


def test_respuesta_de_modelos_basura_se_ignora(tmp_path, crudo, ahora):
    codigo = ejecutar(tmp_path, obtener=lambda: crudo, ahora=ahora, obtener_modelos=lambda: {"hourly": "x"})
    assert codigo == 0
    assert "MODELOS" not in (tmp_path / "latest.txt").read_text(encoding="utf-8")


def test_sin_obtener_modelos_no_se_consulta_nada(tmp_path, crudo, ahora):
    llamadas = []
    ejecutar(tmp_path, obtener=lambda: crudo, ahora=ahora)
    assert llamadas == []
