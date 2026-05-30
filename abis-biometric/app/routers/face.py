from pathlib import Path
"""Router de FastAPI para el procesamiento y detección facial (OCR) en ABIS-UPC.

Este módulo expone endpoints para interactuar con flujos de visión artificial
mediante OpenCV. Permite la detección anatómica de rostros en imágenes binarias,
el cálculo de cajas delimitadoras (Bounding Boxes) con márgenes dinámicos y el
recorte optimizado de perfiles para el registro fotográfico electoral.
"""

from pathlib import Path
from typing import Any
import cv2
import numpy as np
from fastapi import APIRouter, File, HTTPException, UploadFile
from fastapi.responses import Response

router = APIRouter()

CASCADE_PATH = Path(cv2.data.haarcascades) / "haarcascade_frontalface_default.xml"
FACE_CASCADE = cv2.CascadeClassifier(str(CASCADE_PATH))


def _decode_image(raw: bytes) -> np.ndarray:
# Carga el clasificador preentrenado Haar Cascade provisto por la distribución de OpenCV
CASCADE_PATH: Path = Path(cv2.data.haarcascades) / "haarcascade_frontalface_default.xml"
FACE_CASCADE: cv2.CascadeClassifier = cv2.CascadeClassifier(str(CASCADE_PATH))


def _decode_image(raw: bytes) -> np.ndarray:
    """Decodifica un flujo de bytes crudos convirtiéndolo en una matriz de imagen.

    Args:
        raw: Arreglo de bytes binarios provenientes de una solicitud HTTP u OCR.

    Returns:
        Matriz bidimensional o tridimensional (NumPy ndarray) en formato BGR.

    Raises:
        HTTPException: 400 si el búfer de bytes no puede parsearse a formato de imagen.
    """
    arr = np.frombuffer(raw, np.uint8)
    image = cv2.imdecode(arr, cv2.IMREAD_COLOR)
    if image is None:
        raise HTTPException(status_code=400, detail="No se pudo leer la imagen")
    return image


def _detect_largest_face(image: np.ndarray) -> tuple[int, int, int, int]:
    """Detecta múltiples rostros y filtra el área predominante de la toma.

    Aplica ecualización espacial en escala de grises para mitigar variaciones 
    de luminancia y optimizar la precisión matemática del algoritmo Haar.

    Args:
        image: Matriz de imagen en formato nativo OpenCV (BGR).

    Returns:
        Una tupla con las coordenadas espaciales base del rostro (x, y, ancho, alto).

    Raises:
        HTTPException: 422 si el vector espacial de detección retorna vacío (longitud cero).
    """
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
    # Se evalúa el área multiplicando base por altura para discriminar ruidos de fondo
    return max(faces, key=lambda box: box[2] * box[3])


def _pad_box(
    box: tuple[int, int, int, int],
    image_shape: tuple[int, int, int],
    padding_ratio: float = 0.20,
) -> tuple[int, int, int, int]:
    image_shape: tuple[int, int, ...],
    padding_ratio: float = 0.20,
) -> tuple[int, int, int, int]:
    """Calcula un margen de holgura dinámico alrededor del rostro detectado.

    Previene cortes abruptos en orejas, cabello o barbilla incrementando de manera
    proporcional las dimensiones del recuadro sin desbordar los límites reales del canvas.

    Args:
        box: Tupla de coordenadas de origen (x, y, w, h).
        image_shape: Dimensiones de resolución nativas de la imagen de origen.
        padding_ratio: Coeficiente porcentual de expansión periférica.

    Returns:
        Tupla con los vértices absolutos recalculados (x1, y1, x2, y2).
    """
    x, y, w, h = box
    img_h, img_w = image_shape[:2]
    pad_x = int(w * padding_ratio)
    pad_y = int(h * padding_ratio)
    # Restringe los índices usando límites matriciales [0, max_dim] para evitar desbordamiento negativo
    x1 = max(0, x - pad_x)
    y1 = max(0, y - pad_y)
    x2 = min(img_w, x + w + pad_x)
    y2 = min(img_h, y + h + pad_y)
    return x1, y1, x2, y2


@router.post("/detect")
async def detect_face(foto: UploadFile = File(...)):
@router.post("/detect", summary="Detectar coordenadas de rostro")
async def detect_face(foto: UploadFile = File(...)) -> dict[str, Any]:
    """Analiza la carga de una imagen y devuelve las coordenadas espaciales del rostro.

    Args:
        foto: Archivo de imagen adjunto en la solicitud multiparte (multipart/form-data).

    Returns:
        Un objeto estructurado con las dimensiones escaladas de la Bounding Box y
        las resoluciones de lona asociadas.
    """
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

@router.post("/crop", summary="Recortar y codificar rostro")
async def crop_face(foto: UploadFile = File(...)) -> Response:
    """Extrae la submatriz facial de la imagen y la retorna codificada en formato JPEG.

    Args:
        foto: Archivo de imagen con el rostro del ciudadano a procesar.

    Returns:
        Respuesta binaria directa con el tipo de medio configurado como image/jpeg.

    Raises:
        HTTPException: 500 si la compresión matricial de OpenCV falla en el codificador.
    """
    image = _decode_image(await foto.read())
    x, y, w, h = _detect_largest_face(image)
    x1, y1, x2, y2 = _pad_box((x, y, w, h), image.shape)
    
    # Slicing directo sobre la matriz NumPy para extraer el sub-búfer de interés
    crop = image[y1:y2, x1:x2]

    # Se parametriza la compresión al 92% para preservar nitidez biométrica óptima reduciendo peso
    ok, buffer = cv2.imencode(".jpg", crop, [cv2.IMWRITE_JPEG_QUALITY, 92])
    if not ok:
        raise HTTPException(status_code=500, detail="No se pudo generar el recorte")

    return Response(content=buffer.tobytes(), media_type="image/jpeg")
