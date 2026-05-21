# Scanner de contingencia ABIS-UPC

Servicio local para leer el YHD9601D en modo Virtual Serial COM y enviar tokens QR propios al backend Java.

## Variables `.env`

```env
SCANNER_PORT=COM5
SCANNER_BAUD=9600
SCANNER_ID=YHD9601D-01
SCANNER_PUESTO_ID=1
ABIS_JAVA_URL=http://localhost:7000
SCANNER_READ_TIMEOUT=0.20
SCANNER_POST_TIMEOUT=2.0
SCANNER_DEDUPE_SECONDS=2.0
```

## Ejecucion

```powershell
.\INICIAR_SCANNER_CONTINGENCIA.bat
```

O directo:

```powershell
python scripts\contingencia_scanner.py --port COM5 --baud 9600
```

## Formato aceptado

El QR debe contener un token ASCII corto:

```text
ABIS-7-K4M8Q2XP9Z6T3N5C
```

El script normaliza mayusculas, elimina espacios, `\x00`, `\r` y `\n`, y descarta lecturas duplicadas durante 2 segundos.
