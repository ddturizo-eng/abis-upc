"""
paddle_engine.py

Implementación del motor OCR usando PaddleOCR.

Responsabilidad única: recibir una imagen numpy y devolver
un OcrResult con el texto detectado por PaddleOCR.

Principio: SRP  — solo extrae texto con Paddle, no parsea ni procesa.
Principio: DIP  — implementa BaseEngine, el pipeline no sabe que es Paddle.
"""

import logging
from typing import Optional

import numpy as np

from .base_engine import BaseEngine, EngineError
from ..models.ocr_result import OcrResult, OcrEngine, OcrStatus

logger = logging.getLogger(__name__)


class PaddleEngine(BaseEngine):
    """
    Motor OCR basado en PaddleOCR.

    Modelos usados:
        - Detection    : PP-OCRv3
        - Recognition  : latin_PP-OCRv3
        - Clasificación: desactivada (CLS=False) por rendimiento

    La instancia de PaddleOCR se crea una sola vez en el constructor
    (patrón singleton implícito) para evitar el costo de inicialización
    en cada extracción.
    """

    def __init__(
        self,
        lang: str = "es",
        use_gpu: bool = False,
        cls: bool = False,
    ):
        """
        Args:
            lang   : Idioma del modelo de reconocimiento.
            use_gpu: True para usar GPU si está disponible.
            cls    : True para activar clasificación de orientación.
                     Desactivado por defecto — mejora rendimiento
                     en capturas controladas donde la orientación es fija.
        """
        self._lang = lang
        self._use_gpu = use_gpu
        self._cls = cls
        self._ocr = None  # Lazy init — se crea al primer uso

    # -------------------------------------------------------------------------
    # Interfaz pública (contrato de BaseEngine)
    # -------------------------------------------------------------------------

    @property
    def engine_type(self) -> OcrEngine:
        return OcrEngine.PADDLE

    def is_available(self) -> bool:
        """Verifica que PaddleOCR esté instalado."""
        try:
            import paddleocr  # noqa: F401

            return True
        except ImportError:
            return False

    def extract(self, image: np.ndarray) -> OcrResult:
        """
        Extrae texto de la imagen usando PaddleOCR.

        Args:
            image: Imagen BGR como numpy array (ya preprocesada).

        Returns:
            OcrResult con texto, líneas y confianza global.
        """
        if not self.is_available():
            return self._build_failed_result(
                "PaddleOCR no está instalado. "
                "Ejecute: pip install paddleocr paddlepaddle"
            )

        try:
            ocr = self._get_ocr_instance()
            # PaddleOCR 2.x API: usar cls para clasificación de orientación
            raw_results = ocr.ocr(image, cls=self._cls)
            return self._parse_paddle_result(raw_results)

        except Exception as e:
            logger.error("PaddleOCR falló durante extracción: %s", e)
            return self._build_failed_result(str(e))

    # -------------------------------------------------------------------------
    # Internos
    # -------------------------------------------------------------------------

    def _get_ocr_instance(self):
        """
        Lazy initialization de PaddleOCR.
        Se inicializa solo una vez y se reutiliza en extracciones siguientes.
        """
        if self._ocr is None:
            logger.info(
                "Inicializando PaddleOCR (lang=%s, use_gpu=%s)...",
                self._lang,
                self._use_gpu,
            )
            from paddleocr import PaddleOCR

            # PaddleOCR 2.x usa use_gpu y show_log
            self._ocr = PaddleOCR(
                lang=self._lang,
                use_gpu=self._use_gpu,
                show_log=False,
            )
            logger.info("PaddleOCR inicializado correctamente.")
        return self._ocr

    def _parse_paddle_result(self, raw_results) -> OcrResult:
        """
        Convierte la salida cruda de PaddleOCR en un OcrResult limpio.

        Estructura de PaddleOCR:
            raw_results = [
                [                          ← página (siempre 1 para imagen)
                    [bbox, (text, score)], ← línea detectada
                    [bbox, (text, score)],
                    ...
                ]
            ]
        """
        if not raw_results or raw_results == [None]:
            logger.warning("PaddleOCR no detectó texto en la imagen.")
            return OcrResult(
                raw_text="",
                engine=OcrEngine.PADDLE,
                status=OcrStatus.PARTIAL,
                confidence=0.0,
                lines=[],
                error_msg="No se detectó texto en la imagen.",
            )

        lines: list[str] = []
        scores: list[float] = []

        # PaddleOCR puede retornar lista de páginas
        pages = raw_results if isinstance(raw_results[0], list) else [raw_results]

        for page in pages:
            if not page:
                continue
            for detection in page:
                # Cada detection: [bbox, (text, confidence)]
                if not detection or len(detection) < 2:
                    continue
                text_info = detection[1]
                if not text_info or len(text_info) < 2:
                    continue

                text = str(text_info[0]).strip()
                score = float(text_info[1])

                if text:
                    lines.append(text)
                    scores.append(score)

        if not lines:
            return OcrResult(
                raw_text="",
                engine=OcrEngine.PADDLE,
                status=OcrStatus.PARTIAL,
                confidence=0.0,
                lines=[],
                error_msg="PaddleOCR detectó regiones pero no extrajo texto.",
            )

        raw_text = "\n".join(lines)
        avg_confidence = sum(scores) / len(scores) if scores else 0.0
        status = OcrStatus.SUCCESS if avg_confidence >= 0.75 else OcrStatus.PARTIAL

        logger.info(
            "PaddleOCR extrajo %d líneas con confianza promedio %.2f",
            len(lines),
            avg_confidence,
        )

        return OcrResult(
            raw_text=raw_text,
            engine=OcrEngine.PADDLE,
            status=status,
            confidence=round(avg_confidence, 3),
            lines=lines,
        )
