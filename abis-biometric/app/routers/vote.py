"""Router de FastAPI para la delegación transaccional del sufragio en ABIS-UPC.

Este módulo actúa como un proxy/pasarela asíncrona de red. Captura las solicitudes
de emisión de voto validadas por la capa biométrica y las despacha mediante HTTP hacia
el backend maestro desarrollado en Java, el cual orquesta los procedimientos PL/SQL.
"""

import os
from typing import Any

import httpx
from fastapi import APIRouter, HTTPException
from pydantic import BaseModel

# Endpoint del servicio centralizado en Java para la gestión de escrutinios
JAVA_BACKEND_URL: str = os.getenv("JAVA_BACKEND_URL", "http://localhost:7000")

router = APIRouter()


class VoteRequest(BaseModel):
    """Esquema de validación para las solicitudes de asentamiento de sufragio."""
    identificacion: str
    id_eleccion: int | None = None
    id_candidato: int | None = None
    id_puesto: int | None = None


@router.post("/", summary="Registrar y delegar sufragio al backend central")
async def register_vote(data: VoteRequest) -> dict[str, Any]:
    """Recibe la intención de voto y la delega al backend de Java de forma atómica.

    Parsea las propiedades en formato snake_case a la estructura camelCase requerida 
    estrictamente por las entidades de persistencia y DTOs del microservicio Spring/Java.

    Args:
        data: Objeto de petición con la cédula del votante e identificadores de jornada.

    Returns:
        Un diccionario con la respuesta transaccional emitida por el core de Java.

    Raises:
        HTTPException: 502 si el cliente asíncrono HTTPX experimenta un fallo de
            I/O o timeout al conectar con el contenedor de Java.
        HTTPException: Dynamic status si el backend de Java rechaza la petición por
            reglas de negocio insatisfechas (ej. ORA-20070 de votante no habilitado).
    """
    payload: dict[str, Any] = {
        "identificacion": data.identificacion,
    }
    
    # Mapeo explícito a camelCase para asegurar la compatibilidad con Jackson ObjectMapper en Java
    if data.id_eleccion is not None:
        payload["idEleccion"] = data.id_eleccion
    if data.id_candidato is not None:
        payload["idCandidato"] = data.id_candidato
    if data.id_puesto is not None:
        payload["idPuesto"] = data.id_puesto

    try:
        # Se establece un timeout prudencial de 10 segundos para soportar la latencia del MERGE/COMMIT en Oracle
        async with httpx.AsyncClient(timeout=10.0) as client:
            r = await client.post(
                f"{JAVA_BACKEND_URL}/api/votos/registrar",
                json=payload,
            )
    except httpx.RequestError as exc:
        raise HTTPException(
            status_code=502,
            detail=f"No se pudo conectar con el backend de votacion: {exc}",
        )

    if r.status_code not in (200, 201):
        detail = r.json().get("error", r.text) if r.text else "Error desconocido"
        raise HTTPException(status_code=r.status_code, detail=detail)

    response_data: dict[str, Any] = r.json()
    print(f"[Vote] Voto registrado via Java para {data.identificacion}")
    return response_data