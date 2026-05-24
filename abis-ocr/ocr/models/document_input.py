"""
document_input.py

Representa la entrada al sistema OCR: una o dos imágenes
de un documento de identidad, con metadata de captura.

Principio: SRP  — solo representa el dato de entrada, no lo procesa.
Principio: Information Expert (GRASP) — sabe si necesita trasera
           según su propio tipo de documento.
"""

from dataclasses import dataclass, field
from enum import Enum
from typing import Optional


class SourceType(Enum):
    """Origen físico de la imagen."""
    CAMERA = "camera"   # Captura en vivo desde cámara USB
    FILE   = "file"     # Imagen cargada desde directorio


class DocumentSide(Enum):
    """Cara del documento capturada."""
    FRONT = "front"
    BACK  = "back"


class DocumentHint(Enum):
    """
    Pista opcional sobre el tipo de documento.
    Si es AUTO, el clasificador lo determina solo.
    """
    AUTO                = "auto"
    CEDULA_DIGITAL      = "cedula_digital"
    CEDULA_AMARILLA     = "cedula_amarilla"
    TARJETA_IDENTIDAD   = "tarjeta_identidad"
    CARNET_ESTUDIANTIL  = "carnet_estudiantil"


# Documentos que REQUIEREN trasera para extracción completa
DOCUMENTS_REQUIRING_BACK: frozenset[DocumentHint] = frozenset({
    DocumentHint.CEDULA_AMARILLA,
    DocumentHint.TARJETA_IDENTIDAD,
})


@dataclass
class CapturedImage:
    """
    Imagen capturada de una cara del documento.

    Atributos:
        data        : Imagen en bytes (agnóstico a fuente).
        side        : Cara del documento (FRONT / BACK).
        source_type : Origen de la captura (cámara / archivo).
        filename    : Nombre del archivo si source_type == FILE.
    """
    data: bytes
    side: DocumentSide
    source_type: SourceType
    filename: Optional[str] = None

    def __post_init__(self):
        if not self.data:
            raise ValueError(
                f"La imagen de la cara {self.side.value} no puede estar vacía."
            )

    @property
    def size_kb(self) -> float:
        """Tamaño de la imagen en kilobytes."""
        return len(self.data) / 1024

    def __repr__(self) -> str:
        return (
            f"CapturedImage("
            f"side={self.side.value}, "
            f"source={self.source_type.value}, "
            f"size={self.size_kb:.1f}kb)"
        )


@dataclass
class DocumentInput:
    """
    Entrada completa al pipeline OCR.

    Encapsula una o dos imágenes del documento junto con
    metadata necesaria para el procesamiento.

    Atributos:
        front           : Imagen frontal del documento (obligatoria).
        back            : Imagen trasera (obligatoria para CC amarilla y TI).
        document_hint   : Pista del tipo de documento (default AUTO).
        errors          : Errores de validación detectados.
    """
    front: CapturedImage
    back: Optional[CapturedImage] = None
    document_hint: DocumentHint = DocumentHint.AUTO
    errors: list[str] = field(default_factory=list)

    def __post_init__(self):
        self._validate()

    def _validate(self):
        """Valida coherencia del input."""
        if self.front.side != DocumentSide.FRONT:
            raise ValueError(
                "El campo 'front' debe ser una imagen de la cara FRONT."
            )

        if self.back is not None and self.back.side != DocumentSide.BACK:
            raise ValueError(
                "El campo 'back' debe ser una imagen de la cara BACK."
            )

    @property
    def requires_back(self) -> bool:
        """
        True si el tipo de documento indicado requiere trasera.
        Si el hint es AUTO, retorna False (se evaluará después de clasificar).
        """
        return self.document_hint in DOCUMENTS_REQUIRING_BACK

    @property
    def has_back(self) -> bool:
        """True si se proporcionó imagen trasera."""
        return self.back is not None

    @property
    def is_complete(self) -> bool:
        """
        True si el input tiene todo lo necesario para procesar.
        - Si requiere trasera: debe tener ambas caras.
        - Si no requiere trasera: solo frontal es suficiente.
        """
        if self.requires_back:
            return self.has_back
        return True

    @property
    def missing_back_warning(self) -> Optional[str]:
        """Mensaje de advertencia si falta la trasera cuando se requiere."""
        if self.requires_back and not self.has_back:
            return (
                f"El documento '{self.document_hint.value}' requiere imagen trasera "
                f"para extracción completa (sexo, grupo sanguíneo, fecha nacimiento)."
            )
        return None

    def all_images(self) -> list[CapturedImage]:
        """Retorna todas las imágenes disponibles en orden [front, back]."""
        images = [self.front]
        if self.back:
            images.append(self.back)
        return images

    def __repr__(self) -> str:
        caras = "frontal+trasera" if self.has_back else "solo frontal"
        return (
            f"DocumentInput("
            f"hint={self.document_hint.value}, "
            f"caras={caras}, "
            f"complete={self.is_complete})"
        )