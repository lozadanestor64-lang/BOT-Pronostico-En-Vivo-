import pytest
import requests

from pronostico_nogoya import config
from pronostico_nogoya.cliente_api import obtener_pronostico_crudo
from pronostico_nogoya.errores import ErrorAPI


class RespuestaFalsa:
    def __init__(self, status=200, datos=None, json_invalido=False):
        self.status_code = status
        self._datos = datos if datos is not None else {"ok": True}
        self._invalido = json_invalido

    def json(self):
        if self._invalido:
            raise ValueError("no es JSON")
        return self._datos


class SesionFalsa:
    def __init__(self, *respuestas):
        self.respuestas = list(respuestas)
        self.llamadas = []

    def get(self, url, **kwargs):
        self.llamadas.append((url, kwargs))
        r = self.respuestas.pop(0)
        if isinstance(r, Exception):
            raise r
        return r

    def close(self):
        pass


def ejecutar(sesion, **kw):
    esperas = []
    datos = obtener_pronostico_crudo(sesion, esperar=esperas.append, **kw)
    return datos, esperas


def test_respuesta_correcta_sin_esperas():
    sesion = SesionFalsa(RespuestaFalsa(datos={"a": 1}))
    datos, esperas = ejecutar(sesion)
    assert datos == {"a": 1}
    assert esperas == []


def test_consulta_pide_unidades_y_zona_explicitas():
    sesion = SesionFalsa(RespuestaFalsa())
    ejecutar(sesion)
    url, kwargs = sesion.llamadas[0]
    params = kwargs["params"]
    assert url == config.URL_API
    assert params["timezone"] == "America/Argentina/Cordoba"
    assert params["temperature_unit"] == "celsius"
    assert params["wind_speed_unit"] == "kmh"
    assert params["precipitation_unit"] == "mm"
    assert kwargs["timeout"] == config.TIMEOUT_SEGUNDOS


def test_reintenta_ante_500_con_espera_creciente():
    sesion = SesionFalsa(RespuestaFalsa(500), RespuestaFalsa(503), RespuestaFalsa(datos={"ok": 1}))
    datos, esperas = ejecutar(sesion)
    assert datos == {"ok": 1}
    assert esperas == [2, 4]


def test_reintenta_ante_errores_de_red():
    sesion = SesionFalsa(requests.ConnectionError("x"), requests.Timeout("y"), RespuestaFalsa())
    datos, _ = ejecutar(sesion)
    assert datos == {"ok": True}


def test_agota_reintentos_y_lanza_error_api():
    sesion = SesionFalsa(*[requests.ConnectionError("x")] * 3)
    with pytest.raises(ErrorAPI, match="3 intentos"):
        ejecutar(sesion)
    assert len(sesion.llamadas) == 3


def test_http_4xx_no_se_reintenta():
    sesion = SesionFalsa(RespuestaFalsa(400))
    with pytest.raises(ErrorAPI, match="400"):
        ejecutar(sesion)
    assert len(sesion.llamadas) == 1


def test_http_429_si_se_reintenta():
    sesion = SesionFalsa(RespuestaFalsa(429), RespuestaFalsa())
    datos, _ = ejecutar(sesion)
    assert datos == {"ok": True}


def test_json_invalido_se_reintenta_y_luego_falla():
    sesion = SesionFalsa(*[RespuestaFalsa(json_invalido=True)] * 3)
    with pytest.raises(ErrorAPI, match="JSON"):
        ejecutar(sesion)


def test_respuesta_que_no_es_objeto():
    with pytest.raises(ErrorAPI, match="objeto"):
        ejecutar(SesionFalsa(RespuestaFalsa(datos=[1, 2])))


def test_error_informado_por_la_api():
    sesion = SesionFalsa(RespuestaFalsa(datos={"error": True, "reason": "parámetro inválido"}))
    with pytest.raises(ErrorAPI, match="parámetro inválido"):
        ejecutar(sesion)
