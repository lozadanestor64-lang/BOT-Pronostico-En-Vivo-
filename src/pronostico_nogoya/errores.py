"""Excepciones propias del proyecto."""


class ErrorPronostico(Exception):
    """Base de todos los errores controlados del bot."""


class ErrorAPI(ErrorPronostico):
    """Fallo de red, HTTP o respuesta no interpretable de Open-Meteo."""


class ErrorDatos(ErrorPronostico):
    """La respuesta llegó, pero sus datos no son confiables o están incompletos."""


class ErrorDatosDesactualizados(ErrorDatos):
    """Los datos son demasiado viejos para presentarse como 'actuales'."""
