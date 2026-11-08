import copy
import json
from datetime import datetime
from pathlib import Path

import pytest

from pronostico_nogoya.config import ZONA

FIXTURE = Path(__file__).parent / "fixtures" / "respuesta_openmeteo.json"


@pytest.fixture
def crudo():
    """Respuesta simulada de Open-Meteo (copia nueva en cada prueba)."""
    with FIXTURE.open(encoding="utf-8") as f:
        return copy.deepcopy(json.load(f))


@pytest.fixture
def ahora():
    """Cinco minutos después de la hora de los datos del fixture."""
    return datetime(2026, 11, 7, 15, 5, tzinfo=ZONA)
