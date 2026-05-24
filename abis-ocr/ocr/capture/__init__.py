"""
ocr/capture/__init__.py

Exportaciones públicas del módulo de captura.

Uso:
    from ocr.capture import CameraCapture, FileCapture, BaseCapture
"""

from .base_capture import BaseCapture, CaptureConfig, CaptureError
from .camera_capture import CameraCapture
from .file_capture import FileCapture

__all__ = [
    "BaseCapture",
    "CaptureConfig",
    "CaptureError",
    "CameraCapture",
    "FileCapture",
]
