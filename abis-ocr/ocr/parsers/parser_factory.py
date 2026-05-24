"""
parser_factory.py

Fábrica de parsers que selecciona el parser adecuado
según el tipo de documento.

Principio: DIP  — el pipeline usa esta abstracción,
                   no instancia parsers concretos directamente.
Principio: OCP  — agregar nuevo parser = registrar en el dict,
                   no modificar el pipeline.
"""

from typing import Optional, Type

from ocr.models.document_result import DocumentType
from ocr.parsers.base_parser import BaseParser

from ocr.parsers.cedula_digital_parser import CedulaDigitalParser
from ocr.parsers.cedula_amarilla_parser import CedulaAmarillaParser
from ocr.parsers.tarjeta_identidad_parser import TarjetaIdentidadParser
from ocr.parsers.carnet_estudiantil_parser import CarnetEstudiantilParser  # ← NUEVO


class ParserFactory:
    """
    Fábrica que mapea DocumentType → Parser concreto.

    Uso:
        parser = ParserFactory.get_parser(DocumentType.CEDULA_DIGITAL)
        result = parser.parse(ocr_result)
    """

    _registry: dict[DocumentType, Type[BaseParser]] = {
        DocumentType.CEDULA_DIGITAL:     CedulaDigitalParser,
        DocumentType.CEDULA_AMARILLA:    CedulaAmarillaParser,
        DocumentType.TARJETA_IDENTIDAD:  TarjetaIdentidadParser,
        DocumentType.CARNET_ESTUDIANTIL: CarnetEstudiantilParser,   # ← NUEVO
    }

    @classmethod
    def get_parser(cls, doc_type: DocumentType) -> Optional[BaseParser]:
        """
        Retorna una instancia del parser para el tipo de documento.

        Args:
            doc_type: Tipo de documento a procesar.

        Returns:
            Instancia de BaseParser, o None si no hay parser registrado.
        """
        parser_class = cls._registry.get(doc_type)
        if parser_class is None:
            return None
        return parser_class()

    @classmethod
    def register_parser(
        cls,
        doc_type: DocumentType,
        parser_class: Type[BaseParser],
    ) -> None:
        """
        Registra un nuevo parser en tiempo de ejecución.

        Útil para extender el sistema con nuevos tipos sin modificar
        este archivo (ej. desde un plugin o test).

        Args:
            doc_type    : Tipo de documento.
            parser_class: Clase que hereda de BaseParser.
        """
        cls._registry[doc_type] = parser_class

    @classmethod
    def get_available_types(cls) -> list[DocumentType]:
        """Retorna los tipos de documento con parser registrado."""
        return list(cls._registry.keys())

    @classmethod
    def has_parser(cls, doc_type: DocumentType) -> bool:
        """True si existe un parser registrado para el tipo dado."""
        return doc_type in cls._registry