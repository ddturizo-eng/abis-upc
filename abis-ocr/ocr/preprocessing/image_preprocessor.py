"""
image_preprocessor.py

Implementación de preprocesamiento de imágenes para OCR.

Responsabilidad única: recibir bytes de imagen y devolver un numpy array
optimizado para motores OCR (PaddleOCR / Tesseract).

Principio: SRP  — solo preprocesa, no sabe nada de OCR ni parsers.
Principio: DIP  — implementa BasePreprocessor, el pipeline no sabe detalles de OpenCV.
"""

import logging
from typing import Optional

import cv2
import numpy as np

from .base_preprocessor import BasePreprocessor, PreprocessorError

logger = logging.getLogger(__name__)

# Constantes de preprocesamiento
DEFAULT_MAX_WIDTH = 1920  # Ancho máximo para velocidad
DEFAULT_CLAHE_CLIP = 2.0  # Límite de contraste CLAHE
DEFAULT_CLAHE_GRID = (8, 8)  # Tamaño de grid para CLAHE


class ImagePreprocessor(BasePreprocessor):
    """
    Preprocesador de imágenes optimizado para documentos de identidad.

    Pasos de procesamiento (equilibrio velocidad/precisión):
        1. Decodificar bytes → numpy array (BGR)
        2. Redimensionar si excede ancho máximo (mantiene aspecto)
        3. Convertir a escala de grises (mejora OCR general)
        4. Aplicar CLAHE (mejora contraste local para cédulas amarillas)

    Nota: Se omite denoise (Gaussian blur) y binarización (Otsu)
    porque PaddleOCR prefiere escala de grises y ambos pasos son lentos.
    """

    def __init__(
        self,
        max_width: int = DEFAULT_MAX_WIDTH,
        apply_clahe: bool = True,
        clahe_clip: float = DEFAULT_CLAHE_CLIP,
        clahe_grid: tuple[int, int] = DEFAULT_CLAHE_GRID,
    ):
        """
        Args:
            max_width   : Ancho máximo de la imagen. Si es mayor, se escala.
                          Reduce tiempo de inferencia sin perder precisión.
            apply_clahe : True para aplicar ecualización de contraste adaptativo.
                          Útil para cédulas amarillas con bajo contraste.
            clahe_clip  : Límite de corte para CLAHE.
            clahe_grid  : Tamaño de grid para CLAHE.
        """
        self._max_width = max_width
        self._apply_clahe = apply_clahe
        self._clahe = cv2.createCLAHE(clipLimit=clahe_clip, tileGridSize=clahe_grid)

    # -------------------------------------------------------------------------
    # Interfaz pública (contrato de BasePreprocessor)
    # -------------------------------------------------------------------------

    def process(self, image_bytes: bytes) -> np.ndarray:
        """
        Procesa una imagen desde bytes.

        Args:
            image_bytes: Bytes de la imagen (JPEG/PNG/BMP).

        Returns:
            numpy array de la imagen preprocesada (grayscale).

        Raises:
            PreprocessorError: Si la imagen no se puede decodificar.
        """
        if not image_bytes:
            raise PreprocessorError("Los bytes de la imagen están vacíos.")

        # 1. Decodificar bytes → numpy array (BGR)
        image = cv2.imdecode(
            np.frombuffer(image_bytes, dtype=np.uint8),
            cv2.IMREAD_COLOR,
        )

        if image is None:
            raise PreprocessorError(
                "No se pudo decodificar la imagen. Verifique el formato (JPEG/PNG/BMP)."
            )

        logger.debug(
            "Imagen decodificada: shape=%s, dtype=%s", image.shape, image.dtype
        )

        return self.process_from_array(image)

    def process_from_array(self, image: np.ndarray) -> np.ndarray:
        """
        Procesa una imagen ya cargada como numpy array.

        Args:
            image: numpy array BGR de la imagen original.

        Returns:
            numpy array de la imagen preprocesada (grayscale).

        Raises:
            PreprocessorError: Si el array es inválido.
        """
        if image is None or image.size == 0:
            raise PreprocessorError("El array de imagen es inválido o está vacío.")

        # 2. Redimensionar si excede el ancho máximo
        processed = self._resize_if_needed(image)

        # 3. Convertir a escala de grises
        processed = cv2.cvtColor(processed, cv2.COLOR_BGR2GRAY)

        # 4. Aplicar CLAHE para mejorar contraste local
        if self._apply_clahe:
            processed = self._clahe.apply(processed)
            logger.debug("CLAHE aplicado a la imagen")

        logger.debug(
            "Imagen preprocesada: shape=%s, dtype=%s", processed.shape, processed.dtype
        )

        return processed

    # -------------------------------------------------------------------------
    # Internos
    # -------------------------------------------------------------------------

    def _resize_if_needed(self, image: np.ndarray) -> np.ndarray:
        """
        Redimensiona la imagen si excede el ancho máximo.
        Mantiene la relación de aspecto.
        """
        h, w = image.shape[:2]

        if w <= self._max_width:
            return image

        scale = self._max_width / w
        new_w = self._max_width
        new_h = int(h * scale)

        resized = cv2.resize(
            image,
            (new_w, new_h),
            interpolation=cv2.INTER_AREA,  # Mejor calidad para reducción
        )

        logger.debug("Imagen redimensionada: %dx%d → %dx%d", w, h, new_w, new_h)

        return resized
