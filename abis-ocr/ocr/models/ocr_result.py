"""
ocr_result.py

Representa la salida cruda de cualquier motor OCR.
Principio: Information Expert (GRASP) — el modelo conoce y valida sus propios datos.
Principio: SRP — solo representa el resultado del OCR, no lo procesa.
"""

from dataclasses import dataclass, field
from enum import Enum
from typing import Optional


class OcrEngine(Enum):
    """Motores OCR soportados por el sistema."""
    PADDLE = "paddle"
    TESSERACT = "tesseract"


class OcrStatus(Enum):
    """Estado del proceso OCR."""
    SUCCESS = "success"
    PARTIAL = "partial"      # OCR corrió pero con baja confianza
    FAILED = "failed"        # OCR no pudo extraer texto útil


@dataclass
class OcrResult:
    """
    Contrato de salida de cualquier motor OCR.

    Toda implementación de engine debe devolver esta estructura.
    Nunca contiene lógica de negocio — solo representa el dato.

    Atributos:
        raw_text    : Texto plano extraído por el OCR.
        engine      : Motor que generó este resultado.
        status      : Estado del proceso (SUCCESS, PARTIAL, FAILED).
        confidence  : Score de confianza global 0.0 - 1.0.
        lines       : Líneas de texto individuales detectadas.
        error_msg   : Mensaje de error si status == FAILED.
    """
    raw_text: str
    engine: OcrEngine
    status: OcrStatus
    confidence: float = 0.0
    lines: list[str] = field(default_factory=list)
    error_msg: Optional[str] = None

    def __post_init__(self):
        self._validate()

    def _validate(self):
        """Validación interna — el modelo conoce sus propias reglas."""
        if not isinstance(self.engine, OcrEngine):
            raise ValueError(f"Engine inválido: {self.engine}")

        if not isinstance(self.status, OcrStatus):
            raise ValueError(f"Status inválido: {self.status}")

        if not (0.0 <= self.confidence <= 1.0):
            raise ValueError(
                f"Confidence debe estar entre 0.0 y 1.0, recibido: {self.confidence}"
            )

    @property
    def is_usable(self) -> bool:
        """True si el resultado tiene suficiente texto para intentar parsear."""
        return (
            self.status != OcrStatus.FAILED
            and bool(self.raw_text.strip())
        )

    @property
    def is_high_confidence(self) -> bool:
        """True si la confianza es suficiente para confiar en el resultado."""
        return self.confidence >= 0.75

    def __repr__(self) -> str:
        preview = self.raw_text[:80].replace("\n", " ") if self.raw_text else ""
        return (
            f"OcrResult("
            f"engine={self.engine.value}, "
            f"status={self.status.value}, "
            f"confidence={self.confidence:.2f}, "
            f"preview='{preview}...')"
        )