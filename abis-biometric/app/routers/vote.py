from fastapi import APIRouter, HTTPException
from pydantic import BaseModel
from ..db.database import get_user_by_id, marcar_voto_ejercido

router = APIRouter()


class VoteRequest(BaseModel):
    identificacion: str


@router.post("/")
async def register_vote(data: VoteRequest):
    votante = get_user_by_id(data.identificacion)

    if not votante:
        raise HTTPException(status_code=404, detail="Votante no encontrado")

    if votante["estado_voto"] != "PENDIENTE":
        raise HTTPException(
            status_code=409,
            detail=f"Votante no puede votar (estado: {votante['estado_voto']})",
        )

    ok = marcar_voto_ejercido(data.identificacion)
    if not ok:
        raise HTTPException(
            status_code=500, detail="No se pudo registrar el voto en Oracle"
        )

    print(f"[Vote] Voto registrado para {data.identificacion}")
    return {"success": True, "message": "Voto registrado exitosamente"}
