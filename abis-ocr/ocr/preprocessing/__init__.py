"""
ocr/preprocessing/__init__.py

Exportaciones públicas del módulo de preprocesamiento.

Uso:
    from ocr.preprocessing import ImagePreprocessor, BasePreprocessor, PreprocessorError
"""

from .base_preprocessor import BasePreprocessor, PreprocessorError
from .image_preprocessor import ImagePreprocessor

__all__ = [
    "BasePreprocessor",
    "PreprocessorError",
    "ImagePreprocessor",
]
