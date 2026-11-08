"""Constantes del proyecto. Open-Meteo no requiere clave de API."""

from zoneinfo import ZoneInfo

NOMBRE_LUGAR = "Nogoyá"
LATITUD = -32.39
LONGITUD = -59.79
ZONA_HORARIA = "America/Argentina/Cordoba"
ZONA = ZoneInfo(ZONA_HORARIA)

URL_API = "https://api.open-meteo.com/v1/forecast"
USER_AGENT = "bot-pronostico-nogoya/1.0 (+generador de texto; sin acceso a WhatsApp)"
TIMEOUT_SEGUNDOS = 15
REINTENTOS = 3
ESPERA_BASE_SEGUNDOS = 2  # espera creciente: 2 s, 4 s, ...

# Frescura: los datos "actuales" no pueden ser más viejos que esto.
FRESCURA_MAX_MINUTOS = 90
TOLERANCIA_FUTURO_MINUTOS = 15
# Tras este lapso el mensaje deja de ser publicable (se imprime en el texto).
VIGENCIA_HORAS = 3

ARCHIVO_SALIDA = "latest.txt"
CARPETA_HISTORIAL = "historial"
HISTORIAL_MAXIMO = 240  # 30 días a razón de 8 ejecuciones diarias

VARIABLES_ACTUALES = (
    "temperature_2m", "relative_humidity_2m", "apparent_temperature", "is_day",
    "precipitation", "rain", "showers", "weather_code", "pressure_msl",
    "wind_speed_10m", "wind_direction_10m", "wind_gusts_10m",
)
VARIABLES_HORARIAS = (
    "temperature_2m", "dew_point_2m", "precipitation_probability",
    "precipitation", "rain", "showers", "weather_code", "visibility",
    "wind_speed_10m", "wind_gusts_10m", "uv_index",
)
VARIABLES_DIARIAS = (
    "temperature_2m_max", "temperature_2m_min", "sunrise", "sunset",
    "uv_index_max", "precipitation_sum", "precipitation_probability_max",
)


def parametros() -> dict[str, str | float | int]:
    """Parámetros de la consulta. Las unidades se piden de forma explícita."""
    return {
        "latitude": LATITUD,
        "longitude": LONGITUD,
        "timezone": ZONA_HORARIA,
        "forecast_days": 2,
        "timeformat": "iso8601",
        "temperature_unit": "celsius",
        "wind_speed_unit": "kmh",
        "precipitation_unit": "mm",
        "current": ",".join(VARIABLES_ACTUALES),
        "hourly": ",".join(VARIABLES_HORARIAS),
        "daily": ",".join(VARIABLES_DIARIAS),
    }
