# 🌦️ BOT Pronóstico Nogoyá En Vivo

Genera cada 3 horas un pronóstico de **Nogoyá, Entre Ríos** con datos de [Open-Meteo](https://open-meteo.com/), en un texto con formato WhatsApp listo para **copiar y publicar manualmente**.

> ## 🚫 Este programa NUNCA entra a WhatsApp
> No usa Baileys, whatsapp-web.js, Selenium, Playwright, WhatsApp Web, QR, clientes no oficiales ni ningún método para publicar estados de forma automática. Solo escribe un archivo de texto. **Vos copiás y publicás a mano.** Una prueba automática (`tests/test_sin_whatsapp.py`) verifica que el código no contenga nada de eso y que solo `cliente_api.py` acceda a la red.

## Qué incluye el mensaje

Temperatura actual, sensación térmica, máxima y mínima, humedad, punto de rocío, presión, UV, visibilidad, viento, ráfagas, probabilidad y cantidad de lluvia, chaparrones y tormentas (solo cuando hay datos), amanecer, atardecer y las próximas 24 horas en filas horizontales de 3 horas.

## Cómo usar el resultado

1. Abre `latest.txt` en GitHub (en el celular: repositorio → `latest.txt` → botón de copiar).
2. **Revisa la última línea**: `vigente hasta HH:MM`. Pasada esa hora, no lo publiques.
3. Si el archivo empieza con **⚠️ PRONÓSTICO NO DISPONIBLE**, no hay datos confiables: no lo publiques.
4. Pega el texto en WhatsApp y publícalo manualmente.

## Cómo evita mostrar información vieja como actual

- Si Open-Meteo falla (red, HTTP, JSON inválido) o los datos no son confiables, **`latest.txt` se reemplaza por un aviso "NO PUBLICAR"**; nunca queda el pronóstico anterior pasando por actual. El aviso indica el motivo y cuándo fue el último pronóstico válido (sin copiar su contenido).
- Los datos "actuales" se rechazan si tienen más de **90 minutos** de antigüedad o si figuran en el futuro (posible error de zona horaria).
- Cada mensaje muestra la hora de los datos y hasta cuándo es válido (3 horas).
- La ejecución de GitHub Actions se marca como **fallida** (❌) cuando se genera un aviso.
- Reintentos: 3 intentos con espera creciente (2 s, 4 s) ante fallos de red, HTTP 429/5xx o JSON inválido.

## Validación de unidades y datos faltantes

- Las unidades se piden explícitamente (°C, km/h, mm) y se **verifican** en la respuesta; si alguna no coincide (por ejemplo °F o mph), se rechaza todo en lugar de mostrar números mal interpretados.
- Se verifican la zona horaria (`America/Argentina/Cordoba`), la presencia de secciones y la consistencia de las series.
- Valores fuera de rango físico (humedad 140 %, presión 12 hPa, etc.), `NaN`, textos o booleanos se **descartan** como "sin dato" y se registran como advertencia.
- Un dato faltante es `None`, **nunca cero**: en el mensaje se omite esa parte. Solo es obligatoria la temperatura actual.
- La visibilidad se convierte de metros a km; el viento de grados a punto cardinal.
- Lluvia, chaparrones y tormentas aparecen solo si hay datos. Open-Meteo no ofrece "probabilidad de tormenta": se detectan por los códigos WMO 95, 96 y 99; los chaparrones, por `showers` y los códigos 80 a 82.

## Historial

Cada pronóstico **válido** se guarda en `historial/AAAA-MM-DD_HHMM.txt` (hora de Córdoba). Se conservan los últimos **240** (unos 30 días a 8 por día) y se borran los más antiguos. Los avisos de error **no** se guardan en el historial.

## Estructura

```
.github/workflows/pronostico.yml   Cron cada 3 h + ejecución manual
src/pronostico_nogoya/
  config.py            Coordenadas, zona horaria, URL, límites
  errores.py           ErrorAPI, ErrorDatos, ErrorDatosDesactualizados
  cliente_api.py       Única parte que usa la red (reintentos y timeout)
  validacion.py        Unidades, rangos, estructura y frescura
  modelos.py           Dataclasses tipadas (dato faltante = None)
  procesamiento.py     JSON → modelos; próximas 24 h; cardinales
  emojis.py            Códigos WMO, UV → emojis y etiquetas en español
  formato_whatsapp.py  Texto final y aviso de error (funciones puras)
  historial.py         Snapshots, retención y escritura atómica
  __main__.py          Punto de entrada
tests/                 Pruebas con pytest y una respuesta simulada (sin red)
latest.txt             Último resultado (pronóstico o aviso)
historial/             Pronósticos válidos anteriores
```

## Instalación y ejecución local

Requiere Python 3.12.

```bash
python -m venv .venv
source .venv/bin/activate          # Windows: .venv\Scripts\activate
pip install -r requirements-dev.txt
python -m pronostico_nogoya        # genera latest.txt e historial/
```

Código de salida: `0` correcto, `1` error controlado (API o datos), `2` error inesperado. En los errores también se escribe el aviso en `latest.txt`.

## Pruebas

```bash
python -m pytest
ruff check .
```

Cubren: cliente (reintentos, 4xx/5xx, JSON inválido), validación de unidades y rangos, datos faltantes, datos desactualizados o del futuro, formato del texto (largo de línea, sin `None`/`nan`), historial y retención, aviso de error, flujo completo y la ausencia de automatización de WhatsApp.

## GitHub Actions

- **Automático:** cron `0 */3 * * *` (UTC). Argentina es UTC-3, así que corre a las 21, 0, 3, 6, 9, 12, 15 y 18 h locales. GitHub puede demorar unos minutos.
- **Manual:** pestaña **Actions** → *Pronóstico Nogoyá En Vivo* → **Run workflow**.
- **Permisos mínimos:** solo `contents: write`, con el `GITHUB_TOKEN` integrado (no hay secretos que configurar).
- **Commit:** solo si `latest.txt` o `historial/` cambiaron. `concurrency` evita ejecuciones solapadas.
- En repositorios con rama protegida, habilita que `github-actions[bot]` pueda hacer push, o el último paso fallará.
- En repositorios públicos, GitHub puede desactivar los workflows programados tras 60 días sin actividad. Si el bot deja de correr, reactívalo desde la pestaña *Actions*.

## Solución de problemas

| Síntoma | Qué revisar |
|---|---|
| `latest.txt` dice "NO DISPONIBLE" | Lee la línea *Motivo*; suele ser un corte temporal de red o de Open-Meteo. Se reintenta solo. |
| "Unidad inesperada" | Open-Meteo cambió unidades; revisa `config.parametros()` y `validacion.py`. |
| "Los datos son de las HH:MM" | Datos con más de 90 min de antigüedad; espera la próxima ejecución. |
| El workflow falla en *push* | Permisos del repositorio: Settings → Actions → General → *Read and write permissions*. |
| No corre cada 3 h | Demoras normales del cron, o workflow desactivado por inactividad. |

## Datos y licencia

Datos meteorológicos: [Open-Meteo](https://open-meteo.com/) (uso no comercial gratuito, con atribución, bajo CC BY 4.0). Para uso comercial, revisa sus términos.
