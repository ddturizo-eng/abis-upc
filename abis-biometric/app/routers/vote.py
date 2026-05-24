import os
from typing import Optional

import httpx
from fastapi import APIRouter, HTTPException
from pydantic import BaseModel

JAVA_BACKEND_URL = os.getenv("JAVA_BACKEND_URL", "http://localhost:7000")

router = APIRouter()


class VoteRequest(BaseModel):
    identificacion: str
    id_eleccion: Optional[int] = None
    id_candidato: Optional[int] = None
    id_puesto: Optional[int] = None


@router.post("/")
async def register_vote(data: VoteRequest):
    payload = {
        "identificacion": data.identificacion,
    }
    if data.id_eleccion is not None:
        payload["idEleccion"] = data.id_eleccion
    if data.id_candidato is not None:
        payload["idCandidato"] = data.id_candidato
    if data.id_puesto is not None:
        payload["idPuesto"] = data.id_puesto

    try:
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

    response_data = r.json()
    print(f"[Vote] Voto registrado via Java para {data.identificacion}")
    return response_data
