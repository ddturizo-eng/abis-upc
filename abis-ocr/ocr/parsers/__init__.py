"""
ocr/parsers/__init__.py

Exportaciones públicas del módulo de parsers.

Uso:
    from ocr.parsers import (
        BaseParser,
        CedulaDigitalParser,
        CedulaAmarillaParser,
        TarjetaIdentidadParser,
        ParserFactory,
    )
"""

from .base_parser import BaseParser
from .cedula_digital_parser import CedulaDigitalParser
from .cedula_amarilla_parser import CedulaAmarillaParser
from .tarjeta_identidad_parser import TarjetaIdentidadParser
from .parser_factory import ParserFactory

__all__ = [
    "BaseParser",
    "CedulaDigitalParser",
    "CedulaAmarillaParser",
    "TarjetaIdentidadParser",
    "ParserFactory",
]
