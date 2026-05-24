"""
base_preprocessor.py

Contrato abstracto para cualquier preprocesador de imágenes.

Principio: DIP — el pipeline depende de esta abstracción,
                   no de OpenCV ni de implementaciones concretas.
Principio: ISP — interfaz mínima, solo lo que todo preprocesador necesita.
Principio: OCP — agregar nuevo preprocesador = nueva clase, sin tocar el pipeline.
"""

from abc import ABC, abstractmethod
from typing import Optional

import numpy as np


class BasePreprocessor(ABC):
    """
    Interfaz abstracta para preprocesamiento de imágenes.

    Toda implementación recibe bytes de una imagen y devuelve
    un numpy array BGR/GRY listo para el motor OCR.
    """

    @abstractmethod
    def process(self, image_bytes: bytes) -> np.ndarray:
        """
        Procesa una imagen desde bytes y devuelve array numpy.

        Args:
            image_bytes: Bytes de la imagen capturada (JPEG/PNG/BMP).

        Returns:
            numpy array de la imagen preprocesada (BGR o Grayscale).

        Raises:
            PreprocessorError: Si la imagen no se puede procesar.
        """
        pass

    @abstractmethod
    def process_from_array(self, image: np.ndarray) -> np.ndarray:
        """
        Procesa una imagen ya cargada como numpy array.

        Útil cuando la imagen viene de cámara y ya está en memoria.

        Args:
            image: numpy array BGR de la imagen original.

        Returns:
            numpy array de la imagen preprocesada.
        """
        pass


class PreprocessorError(Exception):
    """
    Error de preprocesamiento de imagen.
    Separado de Exception para manejo específico en el pipeline.
    """

    pass
