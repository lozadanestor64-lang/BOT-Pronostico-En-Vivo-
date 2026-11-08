from datetime import datetime, timedelta

import pytest

from pronostico_nogoya import historial
from pronostico_nogoya.config import ZONA

BASE = datetime(2026, 11, 7, 0, 0, tzinfo=ZONA)


def momento(horas):
    return BASE + timedelta(hours=horas)


def test_guardar_crea_carpeta_y_archivo_con_nombre_fechado(tmp_path):
    carpeta = tmp_path / "historial"
    ruta = historial.guardar("hola\n", momento(15), carpeta)
    assert ruta.name == "2026-11-07_1500.txt"
    assert ruta.read_text(encoding="utf-8") == "hola\n"


def test_listar_ordena_y_ignora_archivos_ajenos(tmp_path):
    for h in (9, 3, 6):
        historial.guardar(f"t{h}", momento(h), tmp_path)
    (tmp_path / ".gitkeep").write_text("")
    (tmp_path / "notas.txt").write_text("x")
    assert [p.name for p in historial.listar(tmp_path)] == [
        "2026-11-07_0300.txt", "2026-11-07_0600.txt", "2026-11-07_0900.txt",
    ]


def test_listar_carpeta_inexistente(tmp_path):
    assert historial.listar(tmp_path / "no_existe") == []
    assert historial.ultimo(tmp_path / "no_existe") is None


def test_poda_conserva_los_mas_nuevos(tmp_path):
    for h in range(0, 24, 3):
        historial.guardar(f"t{h}", momento(h), tmp_path, maximo=3)
    nombres = [p.name for p in historial.listar(tmp_path)]
    assert nombres == ["2026-11-07_1500.txt", "2026-11-07_1800.txt", "2026-11-07_2100.txt"]


def test_poda_devuelve_los_eliminados(tmp_path):
    for h in (0, 3, 6):
        historial.escribir_atomico(tmp_path / historial.nombre_archivo(momento(h)), "x")
    eliminados = historial.podar(tmp_path, 1)
    assert [p.name for p in eliminados] == ["2026-11-07_0000.txt", "2026-11-07_0300.txt"]


def test_poda_no_borra_si_hay_menos_que_el_maximo(tmp_path):
    historial.guardar("a", momento(0), tmp_path)
    assert historial.podar(tmp_path, 10) == []
    assert len(historial.listar(tmp_path)) == 1


def test_maximo_invalido(tmp_path):
    with pytest.raises(ValueError):
        historial.podar(tmp_path, 0)


def test_ultimo_y_fecha_de_archivo(tmp_path):
    historial.guardar("a", momento(3), tmp_path)
    historial.guardar("b", momento(12), tmp_path)
    ultimo = historial.ultimo(tmp_path)
    assert ultimo.name == "2026-11-07_1200.txt"
    assert historial.fecha_de_archivo(ultimo) == momento(12)


def test_guardar_dos_veces_el_mismo_minuto_reemplaza(tmp_path):
    historial.guardar("viejo", momento(3), tmp_path)
    historial.guardar("nuevo", momento(3), tmp_path)
    assert len(historial.listar(tmp_path)) == 1
    assert historial.ultimo(tmp_path).read_text(encoding="utf-8") == "nuevo"


def test_escritura_atomica_no_deja_temporales_y_usa_utf8(tmp_path):
    destino = tmp_path / "latest.txt"
    historial.escribir_atomico(destino, "Nogoyá 🌦️\n")
    assert destino.read_text(encoding="utf-8") == "Nogoyá 🌦️\n"
    assert not list(tmp_path.glob("*.tmp"))
    historial.escribir_atomico(destino, "otro\n")
    assert destino.read_text(encoding="utf-8") == "otro\n"
