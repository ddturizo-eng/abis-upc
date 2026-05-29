"""
camera_capture.py

Captura de imágenes desde cámara USB (Sony ZV-E10 en modo webcam u otra).

Responsabilidad única: abrir la cámara, mostrar preview, capturar frame
y devolver bytes. No sabe nada de OCR ni de parsers.

Principio: SRP  — solo captura desde cámara.
Principio: OCP  — extensible sin modificar BaseCapture.
Principio: DIP  — devuelve CapturedImage, el pipeline no sabe si fue cámara.
"""

import logging
import time
from typing import Optional

import cv2
import numpy as np

from .base_capture import BaseCapture, CaptureConfig, CaptureError
from ..models.document_input import CapturedImage, DocumentSide, SourceType

logger = logging.getLogger(__name__)


class CameraCapture(BaseCapture):
    """
    Captura de imágenes desde cámara USB.

    Flujo por captura:
        1. Abre la cámara (índice configurable)
        2. Muestra ventana de preview con instrucciones
        3. Espera que el operador presione ESPACIO para capturar
           o ESC para cancelar
        4. Cierra la cámara
        5. Devuelve CapturedImage con los bytes

    Nota: La cámara se abre y cierra en cada captura intencionalemente,
    para liberar el recurso entre frontal y trasera. Así el operador
    tiene tiempo de voltear el documento sin bloquear el hilo.
    """

    # Teclas de control
    KEY_CAPTURE = 32   # ESPACIO
    KEY_CANCEL  = 27   # ESC

    # Parámetros de visualización del preview
    PREVIEW_WINDOW  = "OCR - Captura de documento"
    FONT            = cv2.FONT_HERSHEY_SIMPLEX
    COLOR_OK        = (0, 200, 0)    # Verde — instrucción principal
    COLOR_CANCEL    = (0, 0, 200)    # Rojo  — instrucción de cancelar
    COLOR_OVERLAY   = (0, 0, 0)      # Negro — fondo del texto

    def __init__(self, config: Optional[CaptureConfig] = None):
        super().__init__(config)

    # -------------------------------------------------------------------------
    # Interfaz pública (contrato de BaseCapture)
    # -------------------------------------------------------------------------

    @property
    def source_type(self) -> SourceType:
        return SourceType.CAMERA

    def is_available(self) -> bool:
        """Verifica que haya una cámara disponible en el índice configurado."""
        cap = cv2.VideoCapture(self._config.camera_index)
        available = cap.isOpened()
        cap.release()
        return available

    def capture(self, side: DocumentSide) -> CapturedImage:
        """
        Abre la cámara, muestra preview interactivo y captura cuando
        el operador presiona ESPACIO.

        Args:
            side: Cara del documento a capturar.

        Returns:
            CapturedImage con la imagen capturada en bytes JPEG.

        Raises:
            CaptureError: Si la cámara no está disponible o el operador cancela.
        """
        cap = self._open_camera()

        try:
            frame = self._interactive_preview(cap, side)
        finally:
            cap.release()
            cv2.destroyAllWindows()

        if frame is None:
            raise CaptureError(
                f"Captura cancelada por el operador "
                f"(cara: {side.value})."
            )

        image_bytes = self._frame_to_bytes(frame)
        self._validate_size(image_bytes)

        logger.info(
            "Imagen capturada desde cámara: cara=%s, tamaño=%.1fKB",
            side.value,
            len(image_bytes) / 1024,
        )

        return CapturedImage(
            data=image_bytes,
            side=side,
            source_type=SourceType.CAMERA,
        )

    # -------------------------------------------------------------------------
    # Internos
    # -------------------------------------------------------------------------

    def _open_camera(self) -> cv2.VideoCapture:
        """Abre la cámara y configura resolución."""
        cap = cv2.VideoCapture(self._config.camera_index)

        if not cap.isOpened():
            raise CaptureError(
                f"No se pudo abrir la cámara en el índice "
                f"{self._config.camera_index}. "
                f"Verifique la conexión USB."
            )

        # Preferir alta resolución si la cámara lo soporta
        cap.set(cv2.CAP_PROP_FRAME_WIDTH, 1920)
        cap.set(cv2.CAP_PROP_FRAME_HEIGHT, 1080)

        logger.debug(
            "Cámara abierta: índice=%d, resolución=%dx%d",
            self._config.camera_index,
            int(cap.get(cv2.CAP_PROP_FRAME_WIDTH)),
            int(cap.get(cv2.CAP_PROP_FRAME_HEIGHT)),
        )

        return cap

    def _interactive_preview(
        self,
        cap: cv2.VideoCapture,
        side: DocumentSide,
    ) -> Optional[np.ndarray]:
        """
        Muestra el preview en vivo con instrucciones superpuestas.

        Returns:
            Frame capturado, o None si el operador canceló.
        """
        side_label = "FRONTAL" if side == DocumentSide.FRONT else "TRASERA"
        instruction = f"Cara {side_label} | ESPACIO = capturar | ESC = cancelar"

        while True:
            ret, frame = cap.read()

            if not ret or frame is None:
                raise CaptureError(
                    "No se pudo leer frame de la cámara. "
                    "Verifique la conexión."
                )

            display = frame.copy()
            self._draw_overlay(display, instruction, side)

            cv2.imshow(self.PREVIEW_WINDOW, display)

            key = cv2.waitKey(1) & 0xFF

            if key == self.KEY_CAPTURE:
                logger.debug("Operador confirmó captura (ESPACIO).")
                return frame

            if key == self.KEY_CANCEL:
                logger.debug("Operador canceló captura (ESC).")
                return None

    def _draw_overlay(
        self,
        frame: np.ndarray,
        instruction: str,
        side: DocumentSide,
    ) -> None:
        """Dibuja instrucciones sobre el frame del preview."""
        h, w = frame.shape[:2]

        # Rectángulo guía para posicionar el documento (proporción ID-1)
        guide_w = int(w * 0.70)
        guide_h = int(guide_w / 1.586)
        x1 = (w - guide_w) // 2
        y1 = (h - guide_h) // 2
        x2 = x1 + guide_w
        y2 = y1 + guide_h
        cv2.rectangle(frame, (x1, y1), (x2, y2), self.COLOR_OK, 2)

        # Texto de instrucción — fondo semitransparente
        overlay = frame.copy()
        cv2.rectangle(overlay, (0, h - 50), (w, h), self.COLOR_OVERLAY, -1)
        cv2.addWeighted(overlay, 0.6, frame, 0.4, 0, frame)

        cv2.putText(
            frame, instruction,
            (10, h - 15),
            self.FONT, 0.65,
            self.COLOR_OK, 2,
            cv2.LINE_AA,
        )

    def _frame_to_bytes(self, frame: np.ndarray) -> bytes:
        """Convierte un frame numpy a bytes JPEG de alta calidad."""
        encode_params = [cv2.IMWRITE_JPEG_QUALITY, 95]
        success, buffer = cv2.imencode(".jpg", frame, encode_params)

        if not success:
            raise CaptureError("Error al codificar el frame capturado a JPEG.")

        return buffer.tobytes()