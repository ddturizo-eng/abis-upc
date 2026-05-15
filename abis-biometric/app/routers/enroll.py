from fastapi import APIRouter, HTTPException
from pydantic import BaseModel
from ..services.native_client import (
    capture_fingerprint,
    enroll_fingerprint,
    enroll_fingerprint_live,
    report_enroll_progress,
)
from ..db.database import save_template, get_user_by_id
import hashlib
from .. import main as app_state

router = APIRouter()


TOTAL_SAMPLES = 4


class EnrollRequest(BaseModel):
    identificacion: str
    re_enroll: bool = False


def set_progress(
    state: str,
    step: int,
    message: str,
    current_sample: int = 0,
    error: str | None = None,
) -> None:
    app_state.enroll_progress_state.update(
        {
            "state": state,
            "step": step,
            "total": TOTAL_SAMPLES,
            "current_sample": current_sample,
            "message": message,
            "error": error,
        }
    )


async def set_live_progress(
    identificacion: str,
    state: str,
    step: int,
    message: str,
    estado: str,
    current_sample: int = 0,
    error: str | None = None,
) -> None:
    set_progress(state, step, message, current_sample, error)
    await report_enroll_progress(
        identificacion,
        estado,
        current_sample,
        int((step / TOTAL_SAMPLES) * 100),
        message,
        error,
    )


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

    await set_live_progress(
        data.identificacion,
        "starting",
        0,
        "Preparando lector biometrico",
        "INICIANDO",
    )
    nombre_completo = (
        f"{votante['primer_nombre']} {votante['segundo_nombre']} "
        f"{votante['primer_apellido']} {votante['segundo_apellido']}"
    ).strip()
    print(f"[Enroll] Iniciando para {data.identificacion} / {nombre_completo}")

    live_result = await enroll_fingerprint_live(data.identificacion)
    if live_result.get("success"):
        template_b64 = live_result["template"]
        hash_sha256 = hashlib.sha256(template_b64.encode()).hexdigest()

        updated = save_template(data.identificacion, template_b64, hash_sha256)
        if not updated:
            detail = "No se pudo actualizar la plantilla en Oracle"
            await set_live_progress(
                data.identificacion,
                "error",
                TOTAL_SAMPLES,
                detail,
                "ERROR",
                TOTAL_SAMPLES,
                detail,
            )
            raise HTTPException(status_code=500, detail=detail)

        await set_live_progress(
            data.identificacion,
            "complete",
            TOTAL_SAMPLES,
            "Huella guardada exitosamente en Oracle DB.",
            "FINALIZADO_EXITOSO",
            TOTAL_SAMPLES,
        )
        print(f"[Enroll] Votante {data.identificacion} enrolado OK (live)")
        return {
            "success": True,
            "persisted": True,
            "identificacion": data.identificacion,
            "nombre_completo": nombre_completo,
            "message": f"Plantilla biometrica registrada para {nombre_completo}",
        }

    if live_result.get("live_supported") is not False:
        detail = live_result.get("error") or "Error en enrolamiento nativo en vivo"
        await set_live_progress(
            data.identificacion,
            "error",
            0,
            detail,
            "ERROR",
            0,
            detail,
        )
        raise HTTPException(status_code=503, detail=detail)

    print("[Enroll] /enroll-live no disponible. Usando flujo legacy con polling.")
    samples = []
    for i in range(TOTAL_SAMPLES):
        sample_number = i + 1
        set_progress(
            "capturing",
            i,
            f"Coloque el dedo en el lector para la muestra {sample_number}/{TOTAL_SAMPLES}",
            sample_number,
        )
        print(f"[Enroll] Capturando sample {sample_number}/{TOTAL_SAMPLES}...")
        capture = await capture_fingerprint()
        if not capture.get("success"):
            detail = f"Error capturando sample {sample_number}: {capture.get('error')}"
            set_progress("error", i, detail, sample_number, detail)
            raise HTTPException(
                status_code=503,
                detail=detail,
            )
        samples.append(capture["sample"])
        set_progress(
            "sample_captured",
            sample_number,
            "Muestra capturada. Levante el dedo y vuelva a colocarlo cuando se indique."
            if sample_number < TOTAL_SAMPLES
            else "Muestras capturadas. Generando plantilla biometrica.",
            sample_number,
        )
        print(f"[Enroll] Sample {sample_number} OK")

    set_progress("processing", TOTAL_SAMPLES, "Generando plantilla biometrica")
    print("[Enroll] Generando template...")
    enroll_result = await enroll_fingerprint(samples)

    if not enroll_result.get("success"):
        detail = f"Error generando template: {enroll_result.get('error')}"
        set_progress("error", TOTAL_SAMPLES, detail, TOTAL_SAMPLES, detail)
        raise HTTPException(
            status_code=500,
            detail=detail,
        )

    template_b64 = enroll_result["template"]
    hash_sha256 = hashlib.sha256(template_b64.encode()).hexdigest()

    updated = save_template(data.identificacion, template_b64, hash_sha256)
    if not updated:
        detail = "No se pudo actualizar la plantilla en Oracle"
        set_progress("error", TOTAL_SAMPLES, detail, TOTAL_SAMPLES, detail)
        raise HTTPException(
            status_code=500, detail=detail
        )

    set_progress("complete", TOTAL_SAMPLES, "Huella capturada correctamente", TOTAL_SAMPLES)
    print(f"[Enroll] Votante {data.identificacion} enrolado OK")
    return {
        "success": True,
        "persisted": True,
        "identificacion": data.identificacion,
        "nombre_completo": nombre_completo,
        "message": f"Plantilla biometrica registrada para {nombre_completo}",
    }
