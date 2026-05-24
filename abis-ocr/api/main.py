"""
api/main.py

API REST para el sistema OCR-Lab.
Expone el pipeline de extracción de documentos de identidad colombianos.

Endpoints:
    POST /scan          → procesar imagen(es) y retornar campos extraídos
    GET  /health        → estado del servicio
    GET  /types         → tipos de documento soportados

Ejecutar:
    uvicorn api.main:app --reload --port 8002

Desde el frontend:
    POST http://localhost:8002/scan
    Content-Type: multipart/form-data
    Body: front=<archivo>, back=<archivo_opcional>, doc_type=auto
"""

import logging
from contextlib import asynccontextmanager
from typing import Optional, Annotated

from fastapi import FastAPI, File, Form, UploadFile, HTTPException, status
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel

from ocr.models.document_result import DocumentType, ExtractionStatus
from ocr.models.document_input import (
    DocumentInput,
    DocumentHint,
    CapturedImage,
    DocumentSide,
    SourceType,
)
from ocr.preprocessing.image_preprocessor import ImagePreprocessor
from ocr.classification.document_classifier import DocumentClassifier
from pipeline.ocr_pipeline import OcrPipeline

logger = logging.getLogger(__name__)

# Configurar el engine - preferir PaddleOCR (mejor precisión)
# Fallback a Tesseract si PaddleOCR no está disponible
try:
    from ocr.engines.paddle_engine import PaddleEngine as OcrEngine

    ENGINE_NAME = "PaddleOCR"
except ImportError:
    try:
        from ocr.engines.tesseract_engine import TesseractEngine as OcrEngine

        ENGINE_NAME = "Tesseract"
    except Exception as e:
        logger.warning(f"OCR engines no disponibles: {e}")
        raise ImportError("Ningún motor OCR disponible")

# ---------------------------------------------------------------------------
# Modelos de respuesta Pydantic
# ---------------------------------------------------------------------------


class FieldConfidence(BaseModel):
    numero: Optional[float] = None
    nombres: Optional[float] = None
    apellidos: Optional[float] = None
    fecha_nacimiento: Optional[float] = None
    fecha_expiracion: Optional[float] = None
    sexo: Optional[float] = None
    lugar_nacimiento: Optional[float] = None


class ScanResponse(BaseModel):
    """Respuesta del endpoint /scan."""

    # Metadatos de la extracción
    document_type: str
    status: str
    overall_confidence: float
    classification_confidence: Optional[float] = None
    errors: list[str] = []

    # Campos del documento
    numero: Optional[str] = None
    nombres: Optional[str] = None
    apellidos: Optional[str] = None

    # Campos individuales para modelo VOTANTES
    primer_nombre: Optional[str] = None
    segundo_nombre: Optional[str] = None
    primer_apellido: Optional[str] = None
    segundo_apellido: Optional[str] = None

    # Campos adicionales
    fecha_nacimiento: Optional[str] = None
    fecha_expiracion: Optional[str] = None
    sexo: Optional[str] = None
    lugar_nacimiento: Optional[str] = None

    # Confianza por campo
    field_confidence: dict = {}


class HealthResponse(BaseModel):
    status: str
    engine: str
    supported_types: list[str]


class DocumentTypeInfo(BaseModel):
    value: str
    label: str
    description: str


# ---------------------------------------------------------------------------
# Estado global del pipeline (inicializado en startup)
# ---------------------------------------------------------------------------

_pipeline: Optional[OcrPipeline] = None


@asynccontextmanager
async def lifespan(app: FastAPI):
    """Inicializa el pipeline al arrancar y lo limpia al cerrar."""
    global _pipeline
    logger.info("Inicializando pipeline OCR con engine: %s", ENGINE_NAME)

    try:
        _pipeline = OcrPipeline(
            preprocessor=ImagePreprocessor(),
            engine=OcrEngine(),
            classifier=DocumentClassifier(use_ocr_anchor=True),
        )
        logger.info("Pipeline OCR listo.")
    except Exception as e:
        logger.error("Error inicializando pipeline: %s", e, exc_info=True)
        # El pipeline queda None — los endpoints retornarán 503

    yield

    # Cleanup al cerrar
    logger.info("Cerrando API OCR-Lab.")
    _pipeline = None


# ---------------------------------------------------------------------------
# App FastAPI
# ---------------------------------------------------------------------------

app = FastAPI(
    title="OCR-Lab API",
    description="Extracción de datos de documentos de identidad colombianos",
    version="1.0.0",
    lifespan=lifespan,
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],  # En producción: especificar el dominio del frontend
    allow_methods=["GET", "POST"],
    allow_headers=["*"],
)

# Mapeo de string → DocumentHint para el parámetro doc_type
_HINT_MAP: dict[str, DocumentHint] = {
    "auto": DocumentHint.AUTO,
    "cc": DocumentHint.AUTO,
    "ce": DocumentHint.CARNET_ESTUDIANTIL,
    "c.e.": DocumentHint.CARNET_ESTUDIANTIL,
    "cedula_digital": DocumentHint.CEDULA_DIGITAL,
    "cedula_amarilla": DocumentHint.CEDULA_AMARILLA,
    "tarjeta_identidad": DocumentHint.TARJETA_IDENTIDAD,
    "ti": DocumentHint.TARJETA_IDENTIDAD,
    "t.i.": DocumentHint.TARJETA_IDENTIDAD,
    "carnet_estudiantil": DocumentHint.CARNET_ESTUDIANTIL,
}


# ---------------------------------------------------------------------------
# Endpoints
# ---------------------------------------------------------------------------


@app.get("/health", response_model=HealthResponse, tags=["Sistema"])
async def health():
    """Verifica el estado del servicio y el engine OCR disponible."""
    if _pipeline is None:
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail="Pipeline OCR no inicializado. Revise los logs del servidor.",
        )
    return HealthResponse(
        status="ok",
        engine=ENGINE_NAME,
        supported_types=list(_HINT_MAP.keys()),
    )


@app.post("/crop", tags=["OCR"])
async def crop_document_endpoint(
    front: UploadFile = File(...),
):
    """
    Recibe una imagen, detecta el documento con OpenCV y retorna
    la imagen recortada en base64 lista para previsualizar en el frontend.

    Este endpoint es llamado por el frontend después de capturar
    para obtener la imagen limpia antes de enviarla al OCR completo.

    Returns:
        JSON con:
            - cropped_image: string base64 de la imagen recortada (JPEG)
            - success: bool
            - message: descripción del resultado
    """
    import base64

    allowed = {"image/jpeg", "image/png", "image/bmp", "image/webp"}
    if front.content_type not in allowed:
        raise HTTPException(
            status_code=400,
            detail=f"Formato no soportado: {front.content_type}",
        )

    image_bytes = await front.read()

    try:
        cropped_bytes = ImagePreprocessor.crop_document(image_bytes)
        cropped_b64 = base64.b64encode(cropped_bytes).decode("utf-8")

        return {
            "success": True,
            "cropped_image": f"data:image/jpeg;base64,{cropped_b64}",
            "message": "Documento detectado y recortado correctamente",
        }
    except Exception as e:
        logger.error("Error en /crop: %s", e, exc_info=True)
        original_b64 = base64.b64encode(image_bytes).decode("utf-8")
        return {
            "success": False,
            "cropped_image": f"data:image/jpeg;base64,{original_b64}",
            "message": f"No se pudo detectar el documento: {str(e)}",
        }


@app.get("/types", response_model=list[DocumentTypeInfo], tags=["Sistema"])
async def document_types():
    """Lista los tipos de documento soportados con su descripción."""
    return [
        DocumentTypeInfo(
            value="auto",
            label="Detección automática",
            description="El sistema clasifica el documento automáticamente",
        ),
        DocumentTypeInfo(
            value="cedula_digital",
            label="Cédula Digital",
            description="Cédula de Ciudadanía digital (policarbonato, 2018+)",
        ),
        DocumentTypeInfo(
            value="cedula_amarilla",
            label="Cédula Amarilla",
            description="Cédula de Ciudadanía clásica con fondo amarillo",
        ),
        DocumentTypeInfo(
            value="tarjeta_identidad",
            label="Tarjeta de Identidad",
            description="Tarjeta de Identidad para menores de edad",
        ),
        DocumentTypeInfo(
            value="carnet_estudiantil",
            label="Carnet Estudiantil",
            description="Carnet estudiantil universitario colombiano",
        ),
    ]


@app.post("/scan", response_model=ScanResponse, tags=["OCR"])
async def scan_document(
    front: Annotated[
        UploadFile, File(description="Imagen del anverso del documento (JPEG/PNG)")
    ],
    back: Annotated[
        Optional[UploadFile], File(description="Imagen del reverso (opcional)")
    ] = None,
    doc_type: Annotated[
        str, Form(description="Tipo de documento. 'auto' para detección automática")
    ] = "auto",
):
    """
    Procesa un documento de identidad colombiano y extrae sus campos.

    - **front**: imagen del anverso (obligatoria)
    - **back**: imagen del reverso (opcional, mejora extracción de CC amarilla y TI)
    - **doc_type**: tipo de documento o 'auto' para clasificación automática

    Retorna los campos estructurados del documento con nivel de confianza.
    """
    if _pipeline is None:
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail="Pipeline OCR no disponible.",
        )

    # Validar doc_type
    hint = _HINT_MAP.get(doc_type.lower())
    if hint is None:
        raise HTTPException(
            status_code=status.HTTP_422_UNPROCESSABLE_ENTITY,
            detail=f"doc_type inválido: '{doc_type}'. "
            f"Valores válidos: {list(_HINT_MAP.keys())}",
        )

    # Validar tipo de archivo
    _validate_image_file(front)
    if back:
        _validate_image_file(back)

    # Leer bytes
    try:
        front_bytes = await front.read()
        back_bytes = await back.read() if back else None
    except Exception as e:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail=f"Error leyendo archivos: {e}",
        )

    if not front_bytes:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="El archivo del anverso está vacío.",
        )

    # Construir DocumentInput
    doc_input = DocumentInput(
        front=CapturedImage(
            data=front_bytes,
            side=DocumentSide.FRONT,
            source_type=SourceType.FILE,
            filename=front.filename or "front.jpg",
        ),
        back=CapturedImage(
            data=back_bytes,
            side=DocumentSide.BACK,
            source_type=SourceType.FILE,
            filename=back.filename or "back.jpg",
        )
        if back_bytes
        else None,
        document_hint=hint,
    )

    # Procesar
    try:
        result = _pipeline.process(doc_input)
    except ValueError as e:
        raise HTTPException(
            status_code=status.HTTP_422_UNPROCESSABLE_ENTITY,
            detail=str(e),
        )
    except Exception as e:
        logger.error("Error procesando documento: %s", e, exc_info=True)
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail="Error interno procesando el documento.",
        )

    # Separar nombres y apellidos en campos individuales para VOTANTES
    result.split_nombres_apellidos()

    # Serializar respuesta
    return ScanResponse(
        document_type=result.document_type.value,
        status=result.status.value,
        overall_confidence=round(result.overall_confidence, 3),
        classification_confidence=result.classification_confidence,
        errors=result.errors,
        numero=result.numero,
        nombres=result.nombres,
        apellidos=result.apellidos,
        primer_nombre=result.primer_nombre,
        segundo_nombre=result.segundo_nombre,
        primer_apellido=result.primer_apellido,
        segundo_apellido=result.segundo_apellido,
        fecha_nacimiento=result.fecha_nacimiento,
        fecha_expiracion=result.fecha_expiracion,
        sexo=result.sexo,
        lugar_nacimiento=result.lugar_nacimiento,
        field_confidence=result.field_confidence,
    )


# ---------------------------------------------------------------------------
# Utilidades internas
# ---------------------------------------------------------------------------


def _validate_image_file(file: UploadFile) -> None:
    """Valida que el archivo sea una imagen soportada."""
    ALLOWED_CONTENT_TYPES = {
        "image/jpeg",
        "image/jpg",
        "image/png",
        "image/bmp",
        "image/webp",
    }
    ALLOWED_EXTENSIONS = {".jpg", ".jpeg", ".png", ".bmp", ".webp"}

    content_type = file.content_type or ""
    filename = file.filename or ""
    ext = "." + filename.rsplit(".", 1)[-1].lower() if "." in filename else ""

    if content_type not in ALLOWED_CONTENT_TYPES and ext not in ALLOWED_EXTENSIONS:
        raise HTTPException(
            status_code=status.HTTP_415_UNSUPPORTED_MEDIA_TYPE,
            detail=f"Formato no soportado: '{content_type}'. "
            f"Use JPEG, PNG, BMP o WebP.",
        )
