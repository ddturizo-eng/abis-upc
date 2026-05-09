"""
file_capture.py

Captura de imagenes desde archivo.

Responsabilidad unica: leer un archivo de imagen del disco
y devolver bytes. No sabe nada de OCR ni de parsers.

Principio: SRP  — solo captura desde archivo.
Principio: OCP  — extensible sin modificar BaseCapture.
Principio: DIP  — devuelve CapturedImage, el pipeline no sabe si fue archivo.
"""

import logging
from pathlib import Path
from typing import Optional

from .base_capture import BaseCapture, CaptureConfig, CaptureError
from ..models.document_input import CapturedImage, DocumentSide, SourceType

logger = logging.getLogger(__name__)


class FileCapture(BaseCapture):
    """
    Captura de imagenes desde archivo del disco.

    Flujo por captura:
        1. Verifica que el archivo existe
        2. Valida la extension
        3. Lee los bytes
        4. Devuelve CapturedImage
    """

    def __init__(self, config: Optional[CaptureConfig] = None):
        super().__init__(config)

    # -------------------------------------------------------------------------
    # Interfaz pública (contrato de BaseCapture)
    # -------------------------------------------------------------------------

    @property
    def source_type(self) -> SourceType:
        return SourceType.FILE

    def is_available(self) -> bool:
        """Siempre esta disponible si el archivo existe."""
        return True

    def capture(self, side: DocumentSide, file_path: str) -> CapturedImage:
        """
        Lee una imagen desde disco.

        Args:
            side: Cara del documento.
            file_path: Ruta al archivo de imagen.

        Returns:
            CapturedImage con los bytes de la imagen.

        Raises:
            CaptureError: Si el archivo no existe o no es valido.
        """
        path = Path(file_path)

        if not path.exists():
            raise CaptureError(f"El archivo no existe: {file_path}")

        if path.suffix.lower() not in self._config.allowed_formats:
            raise CaptureError(
                f"Formato no soportado: {path.suffix}. "
                f"Permitidos: {self._config.allowed_formats}"
            )

        try:
            with open(path, "rb") as f:
                image_bytes = f.read()

            self._validate_size(image_bytes)

            logger.info(
                "Imagen leida desde archivo: %s, tamaño=%.1fKB",
                file_path,
                len(image_bytes) / 1024,
            )

            return CapturedImage(
                data=image_bytes,
                side=side,
                source_type=SourceType.FILE,
                filename=path.name,
            )

        except IOError as e:
            raise CaptureError(f"Error al leer archivo {file_path}: {e}") from e
