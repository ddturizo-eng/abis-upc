"""
tesseract_engine.py

Implementación del motor OCR usando Tesseract (via pytesseract).

Responsabilidad única: recibir una imagen numpy y devolver
un OcrResult con el texto detectado por Tesseract.

Principio: SRP  — solo extrae texto con Tesseract, no parsea ni procesa.
Principio: DIP  — implementa BaseEngine, el pipeline no sabe que es Tesseract.
"""

import logging
import platform
from pathlib import Path
from typing import Optional

import numpy as np

from .base_engine import BaseEngine, EngineError
from ..models.ocr_result import OcrResult, OcrEngine, OcrStatus

logger = logging.getLogger(__name__)

# Rutas por defecto según sistema operativo
_TESSERACT_PATHS: dict[str, list[str]] = {
    "Windows": [
        r"C:\Program Files\Tesseract-OCR\tesseract.exe",
        r"C:\Program Files (x86)\Tesseract-OCR\tesseract.exe",
    ],
    "Linux": [
        "/usr/bin/tesseract",
        "/usr/local/bin/tesseract",
    ],
    "Darwin": [  # macOS
        "/usr/local/bin/tesseract",
        "/opt/homebrew/bin/tesseract",
    ],
}


class TesseractEngine(BaseEngine):
    """
    Motor OCR basado en Tesseract (via pytesseract).

    Configuración por defecto optimizada para documentos de identidad:
        - PSM 3  : Segmentación automática de página (mejor para documentos
                   con múltiples bloques de texto como cédulas)
        - OEM 3  : Motor LSTM (más preciso que el legacy)
        - lang   : 'spa' (español)

    Modos PSM relevantes para este proyecto:
        PSM 3  → segmentación automática (default, recomendado)
        PSM 6  → bloque de texto uniforme (útil si hay ruido de fondo)
        PSM 11 → texto disperso (útil para MRZ)
    """

    # PSM codes para Tesseract
    PSM_AUTO = 3
    PSM_UNIFORM_BLOCK = 6
    PSM_SPARSE_TEXT = 11

    def __init__(
        self,
        lang: str = "spa",
        psm: int = PSM_AUTO,
        tesseract_cmd: Optional[str] = None,
    ):
        """
        Args:
            lang          : Idioma Tesseract ('spa' para español).
            psm           : Page Segmentation Mode (default 3 = auto).
            tesseract_cmd : Ruta al ejecutable. Si es None, se detecta
                            automáticamente según el SO.
        """
        self._lang = lang
        self._psm = psm
        self._tesseract_cmd = tesseract_cmd
        self._configured = False

    # -------------------------------------------------------------------------
    # Interfaz pública (contrato de BaseEngine)
    # -------------------------------------------------------------------------

    @property
    def engine_type(self) -> OcrEngine:
        return OcrEngine.TESSERACT

    def is_available(self) -> bool:
        """Verifica que pytesseract y el ejecutable estén disponibles."""
        try:
            import pytesseract

            self._configure_tesseract_cmd()
            pytesseract.get_tesseract_version()
            return True
        except Exception:
            return False

    def extract(self, image: np.ndarray) -> OcrResult:
        """
        Extrae texto de la imagen usando Tesseract.

        Args:
            image: Imagen BGR como numpy array (ya preprocesada).

        Returns:
            OcrResult con texto, líneas y confianza global.
        """
        if not self.is_available():
            return self._build_failed_result(
                "Tesseract no está disponible. Instale Tesseract-OCR y pytesseract."
            )

        try:
            import pytesseract

            self._configure_tesseract_cmd()

            config = self._build_config()

            # Tesseract trabaja mejor en escala de grises
            import cv2

            # Verificar si la imagen ya es grayscale (1 canal)
            if len(image.shape) == 2 or (len(image.shape) == 3 and image.shape[2] == 1):
                gray = image if len(image.shape) == 2 else image[:, :, 0]
            else:
                gray = cv2.cvtColor(image, cv2.COLOR_BGR2GRAY)

            # Extracción de texto plano
            raw_text = pytesseract.image_to_string(
                gray,
                lang=self._lang,
                config=config,
            )

            # Datos por palabra con confianza individual
            data = pytesseract.image_to_data(
                gray,
                lang=self._lang,
                config=config,
                output_type=pytesseract.Output.DICT,
            )

            return self._parse_tesseract_result(raw_text, data)

        except Exception as e:
            logger.error("Tesseract falló durante extracción: %s", e)
            return self._build_failed_result(str(e))

    # -------------------------------------------------------------------------
    # Internos
    # -------------------------------------------------------------------------

    def _configure_tesseract_cmd(self) -> None:
        """
        Configura la ruta al ejecutable de Tesseract.
        Se ejecuta una sola vez gracias al flag _configured.
        """
        if self._configured:
            return

        import pytesseract

        if self._tesseract_cmd:
            pytesseract.pytesseract.tesseract_cmd = self._tesseract_cmd
            self._configured = True
            return

        # Auto-detección según sistema operativo
        os_name = platform.system()
        candidates = _TESSERACT_PATHS.get(os_name, [])

        for path in candidates:
            if Path(path).exists():
                pytesseract.pytesseract.tesseract_cmd = path
                logger.info("Tesseract encontrado en: %s", path)
                self._configured = True
                return

        # Si no se encontró en rutas conocidas, dejar el default
        # (puede estar en PATH del sistema)
        logger.warning(
            "Tesseract no encontrado en rutas conocidas para %s. "
            "Usando PATH del sistema.",
            os_name,
        )
        self._configured = True

    def _build_config(self) -> str:
        """Construye el string de configuración para pytesseract."""
        return f"--psm {self._psm} --oem 3"

    def _parse_tesseract_result(
        self,
        raw_text: str,
        data: dict,
    ) -> OcrResult:
        """
        Convierte la salida de Tesseract en un OcrResult limpio.

        Calcula confianza promedio filtrando palabras con confidence == -1
        (espacios y separadores que Tesseract incluye en el output).
        """
        clean_text = raw_text.strip()

        if not clean_text:
            logger.warning("Tesseract no detectó texto en la imagen.")
            return OcrResult(
                raw_text="",
                engine=OcrEngine.TESSERACT,
                status=OcrStatus.PARTIAL,
                confidence=0.0,
                lines=[],
                error_msg="No se detectó texto en la imagen.",
            )

        # Calcular confianza promedio de palabras reales (conf > 0)
        confidences = [
            float(c)
            for c in data.get("conf", [])
            if str(c).lstrip("-").isdigit() and int(c) > 0
        ]
        avg_confidence = (
            sum(confidences) / len(confidences) / 100.0  # Tesseract da 0-100
            if confidences
            else 0.0
        )

        # Extraer líneas no vacías
        lines = [line.strip() for line in clean_text.split("\n") if line.strip()]

        status = OcrStatus.SUCCESS if avg_confidence >= 0.75 else OcrStatus.PARTIAL

        logger.info(
            "Tesseract extrajo %d líneas con confianza promedio %.2f",
            len(lines),
            avg_confidence,
        )

        return OcrResult(
            raw_text=clean_text,
            engine=OcrEngine.TESSERACT,
            status=status,
            confidence=round(avg_confidence, 3),
            lines=lines,
        )
