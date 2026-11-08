"""Garantías de diseño: el bot nunca toca WhatsApp y la red vive solo en el cliente."""

from pathlib import Path

SRC = Path(__file__).resolve().parents[1] / "src" / "pronostico_nogoya"
PALABRAS_PROHIBIDAS = [
    "baileys", "whatsapp-web", "whatsapp_web", "selenium", "playwright",
    "web.whatsapp", "wa.me", "api.whatsapp", "pywhatkit", "pyppeteer",
]
MODULOS_DE_RED = (
    "import requests", "import urllib", "import socket",
    "import http", "import httpx", "import aiohttp",
)


def archivos_fuente():
    return sorted(SRC.glob("*.py"))


def test_hay_codigo_para_revisar():
    assert len(archivos_fuente()) >= 10


def test_ningun_archivo_menciona_automatizacion_de_whatsapp():
    for ruta in archivos_fuente():
        texto = ruta.read_text(encoding="utf-8").lower()
        for palabra in PALABRAS_PROHIBIDAS:
            assert palabra not in texto, f"{ruta.name} menciona '{palabra}'"


def test_solo_el_cliente_api_usa_la_red():
    for ruta in archivos_fuente():
        if ruta.name == "cliente_api.py":
            continue
        texto = ruta.read_text(encoding="utf-8")
        for modulo in MODULOS_DE_RED:
            assert modulo not in texto, f"{ruta.name} importa red: {modulo}"


def test_unica_url_externa_es_open_meteo():
    texto = (SRC / "config.py").read_text(encoding="utf-8")
    assert "https://api.open-meteo.com/" in texto
    for ruta in archivos_fuente():
        for linea in ruta.read_text(encoding="utf-8").splitlines():
            if "http" in linea and "api.open-meteo.com" not in linea:
                assert linea.lstrip().startswith(("#", '"')), f"{ruta.name}: {linea}"
