"""Cliente de red asíncrono para la intercomunicación del SDK nativo en ABIS-UPC.

Este módulo centraliza las peticiones HTTP de bajo nivel orientadas al wrapper 
nativo del lector DigitalPersona. Gestiona la captura, enrolamiento masivo,
identificación 1:N y los canales de notificación de progreso en tiempo real.
"""

import os
from typing import Any

import httpx

# Dirección del servicio nativo (C++/Go wrapper) que controla el hardware DigitalPersona
NATIVE_URL: str = "http://localhost:8765"
JAVA_BACKEND_URL: str = os.getenv("JAVA_BACKEND_URL", "http://localhost:7000")


async def get_reader_status() -> dict[str, Any]:
    """Consulta el estado operativo actual y conectividad del lector físico.

    Returns:
        Diccionario con los parámetros de diagnóstico del hardware.
    """
    # Timeout corto de 5s para evitar bloqueos prolongados en la comprobación del hilo principal
    async with httpx.AsyncClient(timeout=5.0) as client:
        r = await client.get(f"{NATIVE_URL}/status")
        return r.json()


async def capture_fingerprint() -> dict[str, Any]:
    """Dispara el evento de escucha en el sensor para capturar una única muestra.

    Returns:
        Diccionario conteniendo el resultado de la captura y el buffer de la imagen.
    """
    # Se otorgan 8 segundos para dar margen de tiempo a que el votante pose el dedo en el sensor
    async with httpx.AsyncClient(timeout=8.0) as client:
        r = await client.post(f"{NATIVE_URL}/capture")
        return r.json()


async def enroll_fingerprint(samples: list[str]) -> dict[str, Any]:
    """Consolida un lote de muestras dactilares estáticas para generar una plantilla.

    Args:
        samples: Lista de cadenas binarias o Base64 que representan las muestras.

    Returns:
        Diccionario con el estado de la generación de la plantilla unificada.
    """
    # Timeout amplio de 30s debido a la carga computacional del procesamiento matemático de minucias
    async with httpx.AsyncClient(timeout=30.0) as client:
        r = await client.post(f"{NATIVE_URL}/enroll", json={"samples": samples})
        return r.json()


async def enroll_fingerprint_live(identificacion: str) -> dict[str, Any]:
    """Inicia el procedimiento interactivo de enrolamiento dactilar en vivo.

    Args:
        identificacion: Documento de identidad del ciudadano en enrolamiento.

    Returns:
        Diccionario con la respuesta del emparejamiento o banderas de contingencia.
    """
    # Se parametrizan 45s de tolerancia extrema para soportar los reintentos físicos del ciudadano en lona
    async with httpx.AsyncClient(timeout=45.0) as client:
        r = await client.post(
            f"{NATIVE_URL}/enroll-live",
            json={
                "identificacion": identificacion,
                "progressCallbackUrl": f"{JAVA_BACKEND_URL}/api/interno/reportar-progreso",
            },
        )
        data: dict[str, Any] = r.json()
        
        # Validación de contingencia por si el sub-sistema nativo corre una versión desactualizada
        if r.status_code == 404 or data.get("error") == "endpoint no encontrado":
            data["live_supported"] = False
        return data


async def report_enroll_progress(
    identificacion: str,
    estado: str,
    samples: int,
    progreso: int,
    mensaje: str,
    error: str | None = None,
) -> None:
    """Envía actualizaciones asíncronas de la máquina de estados hacia el core de Java.

    Args:
        identificacion: Cédula del sufragante evaluado.
        estado: Token o etiqueta de control de flujo intermedio.
        samples: Conteo actual de huellas procesadas con éxito.
        progreso: Porcentaje numérico (0-100) para control de la barra de progreso.
        mensaje: Glosa informativa mapeada para la vista.
        error: Mensaje de error descriptivo en caso de anomalías mecánicas o de calidad.
    """
    payload: dict[str, Any] = {
        "identificacion": identificacion,
        "estado": estado,
        "samples": samples,
        "progreso": progreso,
        "mensaje": mensaje,
        "error": error,
    }
    try:
        # Timeout ultra corto de 2s para no degradar el rendimiento del bucle principal ante caídas de red
        async with httpx.AsyncClient(timeout=2.0) as client:
            await client.post(
                f"{JAVA_BACKEND_URL}/api/interno/reportar-progreso",
                json=payload,
            )
    except Exception as exc:
        print(f"[NativeClient] No se pudo reportar progreso a Java: {exc}")


async def identify_fingerprint(
    sample: str, templates: list[str], user_ids: list[str]
) -> dict[str, Any]:
    """Remite una muestra dactilar aislada para emparejamiento 1:N sobre vectores.

    Args:
        sample: Imagen o minucias de la huella recién capturada en formato texto.
        templates: Universo completo de vectores biométricos activos recuperados.
        user_ids: Lista paralela correlacionada de llaves primarias de identidad.

    Returns:
        Diccionario dictaminando si existió coincidencia y el ID de usuario correspondiente.
    """
    # Margen intermedio de 20s para la evaluación iterativa de miles de registros en memoria C++
    async with httpx.AsyncClient(timeout=20.0) as client:
        r = await client.post(
            f"{NATIVE_URL}/identify",
            json={"sample": sample, "templates": templates, "userIds": user_ids},
        )
        return r.json()