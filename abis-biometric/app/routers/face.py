from pathlib import Path

import cv2
import numpy as np
from fastapi import APIRouter, File, HTTPException, UploadFile
from fastapi.responses import Response

router = APIRouter()

CASCADE_PATH = Path(cv2.data.haarcascades) / "haarcascade_frontalface_default.xml"
FACE_CASCADE = cv2.CascadeClassifier(str(CASCADE_PATH))


def _decode_image(raw: bytes) -> np.ndarray:
    arr = np.frombuffer(raw, np.uint8)
    image = cv2.imdecode(arr, cv2.IMREAD_COLOR)
    if image is None:
        raise HTTPException(status_code=400, detail="No se pudo leer la imagen")
    return image


def _detect_largest_face(image: np.ndarray) -> tuple[int, int, int, int]:
    gray = cv2.cvtColor(image, cv2.COLOR_BGR2GRAY)
    faces = FACE_CASCADE.detectMultiScale(
        gray,
        scaleFactor=1.1,
        minNeighbors=5,
        minSize=(80, 80),
    )
    if len(faces) == 0:
        raise HTTPException(
            status_code=422,
            detail="No se detecta rostro, ajuste la posicion",
        )
    return max(faces, key=lambda box: box[2] * box[3])


def _pad_box(
    box: tuple[int, int, int, int],
    image_shape: tuple[int, int, int],
    padding_ratio: float = 0.20,
) -> tuple[int, int, int, int]:
    x, y, w, h = box
    img_h, img_w = image_shape[:2]
    pad_x = int(w * padding_ratio)
    pad_y = int(h * padding_ratio)
    x1 = max(0, x - pad_x)
    y1 = max(0, y - pad_y)
    x2 = min(img_w, x + w + pad_x)
    y2 = min(img_h, y + h + pad_y)
    return x1, y1, x2, y2


@router.post("/detect")
async def detect_face(foto: UploadFile = File(...)):
    image = _decode_image(await foto.read())
    x, y, w, h = _detect_largest_face(image)
    x1, y1, x2, y2 = _pad_box((x, y, w, h), image.shape)
    img_h, img_w = image.shape[:2]
    return {
        "success": True,
        "message": "Rostro detectado",
        "bbox": {
            "x": x1,
            "y": y1,
            "width": x2 - x1,
            "height": y2 - y1,
            "image_width": img_w,
            "image_height": img_h,
        },
    }


@router.post("/crop")
async def crop_face(foto: UploadFile = File(...)):
    image = _decode_image(await foto.read())
    x, y, w, h = _detect_largest_face(image)
    x1, y1, x2, y2 = _pad_box((x, y, w, h), image.shape)
    crop = image[y1:y2, x1:x2]

    ok, buffer = cv2.imencode(".jpg", crop, [cv2.IMWRITE_JPEG_QUALITY, 92])
    if not ok:
        raise HTTPException(status_code=500, detail="No se pudo generar el recorte")

    return Response(content=buffer.tobytes(), media_type="image/jpeg")
