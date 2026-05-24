"""
base_engine.py

Contrato abstracto que debe cumplir cualquier motor OCR.

Principio: DIP  — el pipeline depende de esta abstracción,
                   nunca de PaddleOCR ni Tesseract directamente.
Principio: ISP  — interfaz mínima, sin métodos que no todos necesiten.
Principio: OCP  — agregar nuevo motor = nueva clase, sin tocar el pipeline.
"""

from abc import ABC, abstractmethod
from typing import Optional

import numpy as np

from ..models.ocr_result import OcrResult, OcrEngine


class BaseEngine(ABC):
    """
    Interfaz abstracta para motores OCR.

    Toda implementación recibe una imagen ya preprocesada
    y devuelve un OcrResult con el texto extraído y metadata.
    """

    @abstractmethod
    def extract(self, image: np.ndarray) -> OcrResult:
        """
        Extrae texto de una imagen preprocesada.

        Args:
            image: Imagen en formato numpy array BGR (ya preprocesada).

        Returns:
            OcrResult con texto crudo, confianza y metadata del motor.

        Raises:
            EngineError: Si el motor falla durante la extracción.
        """
        pass

    @abstractmethod
    def is_available(self) -> bool:
        """
        Verifica que el motor esté instalado y listo para usar.

        Returns:
            True si el motor puede procesar imágenes en este momento.
        """
        pass

    @property
    @abstractmethod
    def engine_type(self) -> OcrEngine:
        """Identificador del motor que implementa esta clase."""
        pass

    def _build_failed_result(self, error_msg: str) -> OcrResult:
        """
        Construye un OcrResult de fallo de forma consistente.
        Método utilitario disponible para todas las implementaciones.
        """
        from ..models.ocr_result import OcrStatus
        return OcrResult(
            raw_text="",
            engine=self.engine_type,
            status=OcrStatus.FAILED,
            confidence=0.0,
            lines=[],
            error_msg=error_msg,
        )


class EngineError(Exception):
    """
    Error de motor OCR.
    Separado de Exception para manejo específico en el pipeline.
    """
    pass