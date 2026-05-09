"""
ocr/models/__init__.py

Exportaciones públicas del módulo de modelos.

Uso:
    from ocr.models import OcrResult, DocumentResult, DocumentInput
"""

from .ocr_result import OcrResult, OcrEngine, OcrStatus
from .document_result import DocumentResult, DocumentType, ExtractionStatus
from .document_input import (
    DocumentInput,
    DocumentHint,
    DocumentSide,
    SourceType,
    CapturedImage,
)

__all__ = [
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
]
