"""Router de FastAPI para la verificación e identificación biométrica en ABIS-UPC.

Este módulo expone el endpoint crítico de autenticación dactilar un-contra-muchos (1:N).
Captura las minucias en vivo desde el sensor DigitalPersona, extrae el censo biométrico
activo mapeado en Oracle y delega al SDK nativo la comparación vectorial para
convalidar la identidad jurídica del sufragante.
"""

from typing import Any

from fastapi import APIRouter, HTTPException

from ..db.database import get_all_templates, get_votante_completo
from ..services.native_client import capture_fingerprint, identify_fingerprint

router = APIRouter()


@router.post("/", summary="Verificar identidad de votante por huella dactilar")
async def verify() -> dict[str, Any]:
    """Ejecuta la identificación biométrica 1:N contra el censo electoral activo.

    Captura una muestra viva del dedo, descarga todas las plantillas biometrizadas de
    Oracle que tengan derecho al voto pendiente y efectúa la búsqueda secuencial. Si
    existe concordancia espacial por encima del umbral operativo, consolida el expediente.

    Returns:
        Un diccionario indicando si se halló coincidencia (matched=True) junto al perfil
        completo demográfico y de mesa del sufragante, o matched=False si no hay coincidencia.

    Raises:
        HTTPException: 404 si el censo carece de ciudadanos enrolados con biometría activa.
        HTTPException: 500 si el ciudadano es autenticado por el SDK biométrico pero su
            registro maestro fue purgado o no coincide de manera consistente en Oracle.
        HTTPException: 503 si ocurre una falla en el canal del hardware DigitalPersona.
    """
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
    
    # Se disocian las estructuras matriciales en listas paralelas planas para maximizar la velocidad de mapeo del SDK C++ subyacente
    user_ids = [u["identificacion"] for u in usuarios]
    templates = [u["template_b64"] for u in usuarios]

    result = await identify_fingerprint(capture["sample"], templates, user_ids)

    if result.get("matched"):
        matched_id = result["user_id"]
        votante = get_votante_completo(matched_id)
        
        # Salvaguarda de integridad relacional: evita fugas o inconsistencias entre el motor ABIS y las tablas maestras
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