"""
ocr/__init__.py

Exportaciones de alto nivel del paquete OCR.
"""

from .models import *
from .engines import *
from .parsers import *
from .preprocessing import *
from .capture import *

__all__ = [
    # Models
    "OcrResult",
    "OcrEngine",
    "OcrStatus",
    "DocumentResult",
    "DocumentType",
    "ExtractionStatus",
    "DocumentInput",
    "DocumentHint",
    "DocumentSide",
    "SourceType",
    "CapturedImage",
    # Engines
    "BaseEngine",
    "EngineError",
    "PaddleEngine",
    "TesseractEngine",
    # Parsers
    "BaseParser",
    "CedulaDigitalParser",
    "CedulaAmarillaParser",
    "TarjetaIdentidadParser",
    "ParserFactory",
    # Preprocessing
    "BasePreprocessor",
    "PreprocessorError",
    "ImagePreprocessor",
    # Capture
    "BaseCapture",
    "CaptureConfig",
    "CaptureError",
    "CameraCapture",
    "FileCapture",
]
