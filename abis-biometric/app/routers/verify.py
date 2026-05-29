from fastapi import APIRouter, HTTPException
from ..services.native_client import capture_fingerprint, identify_fingerprint
from ..db.database import get_all_templates, get_votante_completo

router = APIRouter()


@router.post("/")
async def verify():
    print("[Verify] Capturando huella...")
    capture = await capture_fingerprint()

    if not capture.get("success"):
        raise HTTPException(
            status_code=503, detail=f"Error capturando huella: {capture.get('error')}"
        )

    print("[Verify] Cargando templates de Oracle...")
    usuarios = get_all_templates()

    if not usuarios:
        raise HTTPException(
            status_code=404, detail="No hay votantes enrolados con plantilla biometrica"
        )

    print(f"[Verify] Comparando contra {len(usuarios)} votantes...")
    user_ids = [u["identificacion"] for u in usuarios]
    templates = [u["template_b64"] for u in usuarios]

    result = await identify_fingerprint(capture["sample"], templates, user_ids)

    if result.get("matched"):
        matched_id = result["user_id"]
        votante = get_votante_completo(matched_id)
        if not votante:
            raise HTTPException(
                status_code=500,
                detail="Votante encontrado en biometria pero no en Oracle",
            )
        print(
            f"[Verify] Match: {votante['identificacion']} - {votante['primer_nombre']}"
        )
        return {
            "matched": True,
            "identificacion": votante["identificacion"],
            "primer_nombre": votante["primer_nombre"],
            "segundo_nombre": votante["segundo_nombre"],
            "primer_apellido": votante["primer_apellido"],
            "segundo_apellido": votante["segundo_apellido"],
            "estado_voto": votante["estado_voto"],
            "rol_id": votante["rol_id"],
            "puesto_id": votante["puesto_id"],
        }

    print("[Verify] No se encontro match")
    return {"matched": False}
