from fastapi import APIRouter, HTTPException
from pydantic import BaseModel
from ..services.native_client import capture_fingerprint, enroll_fingerprint
from ..db.database import save_template, get_user_by_id
import hashlib
from .. import main as app_state

router = APIRouter()


class EnrollRequest(BaseModel):
    identificacion: str
    re_enroll: bool = False


@router.post("/")
async def enroll(data: EnrollRequest):
    votante = get_user_by_id(data.identificacion)

    if not votante:
        raise HTTPException(
            status_code=404, detail="Votante no registrado en el sistema"
        )

    if votante["estado_voto"] == "EJERCIDO":
        raise HTTPException(status_code=409, detail="Votante ya ejercio su voto")

    if votante.get("template_b64") and not data.re_enroll:
        raise HTTPException(
            status_code=409,
            detail="El votante ya tiene plantilla biometrica. Envie re_enroll=true para re-enrolar",
        )

    app_state.enroll_step = 0
    nombre_completo = (
        f"{votante['primer_nombre']} {votante['segundo_nombre']} "
        f"{votante['primer_apellido']} {votante['segundo_apellido']}"
    ).strip()
    print(f"[Enroll] Iniciando para {data.identificacion} / {nombre_completo}")

    samples = []
    for i in range(4):
        print(f"[Enroll] Capturando sample {i + 1}/4...")
        capture = await capture_fingerprint()
        if not capture.get("success"):
            raise HTTPException(
                status_code=503,
                detail=f"Error capturando sample {i + 1}: {capture.get('error')}",
            )
        samples.append(capture["sample"])
        app_state.enroll_step = i + 1
        print(f"[Enroll] Sample {i + 1} OK")

    print("[Enroll] Generando template...")
    enroll_result = await enroll_fingerprint(samples)

    if not enroll_result.get("success"):
        raise HTTPException(
            status_code=500,
            detail=f"Error generando template: {enroll_result.get('error')}",
        )

    template_b64 = enroll_result["template"]
    hash_sha256 = hashlib.sha256(template_b64.encode()).hexdigest()

    updated = save_template(data.identificacion, template_b64, hash_sha256)
    if not updated:
        raise HTTPException(
            status_code=500, detail="No se pudo actualizar la plantilla en Oracle"
        )

    print(f"[Enroll] Votante {data.identificacion} enrolado OK")
    return {
        "success": True,
        "identificacion": data.identificacion,
        "nombre_completo": nombre_completo,
        "message": f"Plantilla biometrica registrada para {nombre_completo}",
    }
