"""
ocr/engines/__init__.py

Exportaciones públicas del módulo de engines OCR.

Uso:
    from ocr.engines import PaddleEngine, TesseractEngine, EngineError
"""

from .base_engine import BaseEngine, EngineError
from .paddle_engine import PaddleEngine
from .tesseract_engine import TesseractEngine

__all__ = [
    "BaseEngine",
    "EngineError",
    "PaddleEngine",
    "TesseractEngine",
]