"""
document_result.py

Representa el resultado final del sistema OCR:
campos del documento ya extraídos, normalizados y validados.

Principio: Information Expert (GRASP) — el modelo sabe representarse como dict/JSON.
Principio: SRP — solo representa el dato estructurado, no extrae ni procesa.
"""

from dataclasses import dataclass, field
from enum import Enum
from typing import Optional


class DocumentType(Enum):
    """Tipos de documento soportados por el sistema."""

    CEDULA_DIGITAL = "cedula_digital"  # CC nueva 2023+
    CEDULA_AMARILLA = "cedula_amarilla"  # CC clásica fondo amarillo
    TARJETA_IDENTIDAD = "tarjeta_identidad"  # TI clásica
    CARNET_ESTUDIANTIL = "carnet_estudiantil"
    UNKNOWN = "unknown"  # No se pudo clasificar


class ExtractionStatus(Enum):
    """Estado de la extracción de campos."""

    COMPLETE = "complete"  # Todos los campos críticos extraídos
    PARTIAL = "partial"  # Solo algunos campos extraídos
    FAILED = "failed"  # No se pudo extraer nada útil


@dataclass
class DocumentResult:
    """
    Contrato de salida del sistema de parsing.

    Representa un documento de identidad ya procesado,
    con sus campos normalizados y un nivel de confianza por campo.

    Atributos:
        document_type       : Tipo de documento detectado.
        status              : Estado de la extracción.
        numero              : Número de documento limpio (solo dígitos).
        nombres             : Nombres completos en mayúscula.
        apellidos           : Apellidos completos en mayúscula.
        fecha_nacimiento    : Fecha en formato YYYY-MM-DD.
        fecha_expiracion    : Fecha en formato YYYY-MM-DD (si aplica).
        sexo                : 'M' o 'F'.
        lugar_nacimiento    : Texto libre del lugar.
        field_confidence    : Dict con confianza individual por campo.
        raw_ocr_text        : Texto crudo original (para debug).
        errors              : Lista de errores ocurridos durante extracción.
    """

    document_type: DocumentType
    status: ExtractionStatus

    # Campos del documento
    numero: Optional[str] = None
    nombres: Optional[str] = None
    apellidos: Optional[str] = None
    fecha_nacimiento: Optional[str] = None
    fecha_expiracion: Optional[str] = None
    sexo: Optional[str] = None
    lugar_nacimiento: Optional[str] = None

    # Campos individuales para modelo VOTANTES
    primer_nombre: Optional[str] = None
    segundo_nombre: Optional[str] = None
    primer_apellido: Optional[str] = None
    segundo_apellido: Optional[str] = None

    # Confianza de la clasificación automática
    classification_confidence: Optional[float] = None

    # Metadata de calidad
    field_confidence: dict[str, float] = field(default_factory=dict)
    raw_ocr_text: Optional[str] = None
    errors: list[str] = field(default_factory=list)

    # Campos críticos que DEBEN estar presentes para considerarse completo
    _CRITICAL_FIELDS: tuple = field(
        default=("numero", "nombres", "apellidos"), init=False, repr=False
    )

    def __post_init__(self):
        self._validate()
        self._auto_evaluate_status()

    def _validate(self):
        """Validaciones básicas del modelo."""
        if self.sexo and self.sexo not in ("M", "F"):
            raise ValueError(f"Sexo inválido: {self.sexo}. Debe ser 'M' o 'F'.")

        if self.numero and not self.numero.isdigit():
            raise ValueError(
                f"Número de documento debe contener solo dígitos: {self.numero}"
            )

    def _auto_evaluate_status(self):
        """
        Evalúa automáticamente el status según los campos críticos presentes.
        Solo actúa si el status no es FAILED (ese se asigna explícitamente).
        """
        if self.status == ExtractionStatus.FAILED:
            return

        critical_present = all(
            getattr(self, f) is not None for f in self._CRITICAL_FIELDS
        )

        self.status = (
            ExtractionStatus.COMPLETE if critical_present else ExtractionStatus.PARTIAL
        )

    @property
    def overall_confidence(self) -> float:
        """Confianza promedio de todos los campos extraídos."""
        if not self.field_confidence:
            return 0.0
        return sum(self.field_confidence.values()) / len(self.field_confidence)

    @property
    def is_complete(self) -> bool:
        """True si todos los campos críticos están presentes."""
        return self.status == ExtractionStatus.COMPLETE

    @property
    def missing_fields(self) -> list[str]:
        """Lista de campos críticos ausentes."""
        return [f for f in self._CRITICAL_FIELDS if getattr(self, f) is None]

    def split_nombres_apellidos(self) -> "DocumentResult":
        """
        Separa los bloques nombres/apellidos en campos individuales VOTANTES.

        Lógica:
            "DANIEL DAVID"   → primer_nombre="DANIEL", segundo_nombre="DAVID"
            "TURIZO CHACON"  → primer_apellido="TURIZO", segundo_apellido="CHACON"
            "DANIEL"         → primer_nombre="DANIEL", segundo_nombre=None

        Retorna self para encadenamiento:
            result.split_nombres_apellidos().to_dict()

        Nota: Modifica el objeto in-place Y retorna self.
        """
        if self.nombres:
            partes = self.nombres.strip().split()
            self.primer_nombre = partes[0] if len(partes) >= 1 else None
            self.segundo_nombre = partes[1] if len(partes) >= 2 else None

        if self.apellidos:
            partes = self.apellidos.strip().split()
            self.primer_apellido = partes[0] if len(partes) >= 1 else None
            self.segundo_apellido = partes[1] if len(partes) >= 2 else None

        return self

    def to_dict(self) -> dict:
        """
        Serializa el resultado a dict limpio para JSON / BD.
        Excluye campos internos y el texto crudo.
        """
        return {
            "document_type": self.document_type.value,
            "status": self.status.value,
            "numero": self.numero,
            "nombres": self.nombres,
            "apellidos": self.apellidos,
            "primer_nombre": self.primer_nombre,
            "segundo_nombre": self.segundo_nombre,
            "primer_apellido": self.primer_apellido,
            "segundo_apellido": self.segundo_apellido,
            "fecha_nacimiento": self.fecha_nacimiento,
            "fecha_expiracion": self.fecha_expiracion,
            "sexo": self.sexo,
            "lugar_nacimiento": self.lugar_nacimiento,
            "overall_confidence": round(self.overall_confidence, 3),
            "classification_confidence": self.classification_confidence,
            "field_confidence": self.field_confidence,
            "errors": self.errors,
        }

    def __repr__(self) -> str:
        return (
            f"DocumentResult("
            f"type={self.document_type.value}, "
            f"status={self.status.value}, "
            f"numero={self.numero}, "
            f"nombres={self.nombres}, "
            f"apellidos={self.apellidos}, "
            f"confidence={self.overall_confidence:.2f})"
        )
