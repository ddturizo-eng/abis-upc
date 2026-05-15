import cv2
import numpy as np
from fastapi import APIRouter, File, HTTPException, UploadFile
from fastapi.responses import Response
from pydantic import BaseModel

router = APIRouter()


class QrRenderRequest(BaseModel):
    value: str


@router.post("/decode")
async def decode_qr(file: UploadFile = File(...)):
    raw = await file.read()
    arr = np.frombuffer(raw, np.uint8)
    image = cv2.imdecode(arr, cv2.IMREAD_COLOR)
    if image is None:
        raise HTTPException(status_code=400, detail="No se pudo leer la imagen")

    detector = cv2.QRCodeDetector()
    value, points, _ = detector.detectAndDecode(image)
    if not value:
        raise HTTPException(status_code=422, detail="No se detecto QR en el reverso")

    bbox = None
    if points is not None:
        pts = points.reshape(-1, 2)
        x, y, w, h = cv2.boundingRect(pts.astype(np.int32))
        bbox = {"x": x, "y": y, "width": w, "height": h}

    return {"success": True, "qr": value[:500], "bbox": bbox}


@router.post("/render")
async def render_qr(payload: QrRenderRequest):
    if not payload.value:
        raise HTTPException(status_code=400, detail="Contenido QR requerido")

    try:
        import qrcode
        from io import BytesIO
    except ImportError as exc:
        raise HTTPException(
            status_code=500,
            detail="Dependencia qrcode no instalada",
        ) from exc

    qr = qrcode.QRCode(box_size=10, border=4)
    qr.add_data(payload.value[:500])
    qr.make(fit=True)
    image = qr.make_image(fill_color="#1a3a2a", back_color="white")
    output = BytesIO()
    image.save(output, format="PNG")
    return Response(content=output.getvalue(), media_type="image/png")
