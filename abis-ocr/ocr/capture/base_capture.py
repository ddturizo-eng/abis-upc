"""
base_capture.py

Contrato abstracto para cualquier fuente de captura de imágenes.

Principio: DIP — el pipeline depende de esta abstracción,
                  no de cámara ni archivos concretos.
Principio: ISP — interfaz mínima y cohesiva.
Principio: OCP — agregar nueva fuente = nueva clase, sin tocar el pipeline.
"""

from abc import ABC, abstractmethod
from dataclasses import dataclass
from typing import Optional

from ..models.document_input import CapturedImage, DocumentSide, SourceType


@dataclass
class CaptureConfig:
    """
    Configuración compartida para cualquier fuente de captura.

    Atributos:
        max_size_mb     : Tamaño máximo permitido por imagen en MB.
        allowed_formats : Extensiones permitidas (solo para FileCapture).
        camera_index    : Índice de cámara USB (solo para CameraCapture).
        preview_seconds : Segundos de preview antes de capturar.
    """
    max_size_mb: float = 10.0
    allowed_formats: tuple[str, ...] = (".jpg", ".jpeg", ".png", ".bmp")
    camera_index: int = 0
    preview_seconds: int = 3


class BaseCapture(ABC):
    """
    Interfaz abstracta para fuentes de captura de imágenes.

    Toda implementación debe ser capaz de capturar
    una cara específica del documento y devolverla como CapturedImage.
    """

    def __init__(self, config: Optional[CaptureConfig] = None):
        self._config = config or CaptureConfig()

    @abstractmethod
    def capture(self, side: DocumentSide) -> CapturedImage:
        """
        Captura una imagen de la cara indicada del documento.

        Args:
            side: Cara del documento a capturar (FRONT / BACK).

        Returns:
            CapturedImage con los bytes de la imagen capturada.

        Raises:
            CaptureError: Si no se puede obtener la imagen.
        """
        pass

    @abstractmethod
    def is_available(self) -> bool:
        """
        Verifica si la fuente de captura está disponible y lista.

        Returns:
            True si se puede capturar en este momento.
        """
        pass

    @property
    def source_type(self) -> SourceType:
        """Tipo de fuente que implementa esta clase."""
        raise NotImplementedError

    def _validate_size(self, data: bytes) -> None:
        """Valida que la imagen no exceda el tamaño máximo configurado."""
        size_mb = len(data) / (1024 * 1024)
        if size_mb > self._config.max_size_mb:
            raise CaptureError(
                f"Imagen demasiado grande: {size_mb:.1f}MB. "
                f"Máximo permitido: {self._config.max_size_mb}MB."
            )


class CaptureError(Exception):
    """
    Error de captura de imagen.
    Separado de ValueError para permitir manejo específico en el pipeline.
    """
    pass