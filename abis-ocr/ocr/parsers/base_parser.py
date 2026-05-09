"""
base_parser.py

Contrato abstracto para cualquier parser de documentos.

Principio: DIP — el pipeline depende de esta abstracción,
                   no de parsers concretos.
Principio: ISP — interfaz mínima, solo lo que todo parser necesita.
Principio: OCP — agregar nuevo doc = nueva clase, sin tocar las existentes.
"""

from abc import ABC, abstractmethod
from typing import Optional

from ocr.models.document_result import (
    DocumentResult,
    DocumentType,
    ExtractionStatus,
)
from ocr.models.ocr_result import OcrResult


class BaseParser(ABC):
    """
    Interfaz abstracta para parsers de documentos de identidad.

    Toda implementación recibe un OcrResult (texto + confianza)
    y devuelve un DocumentResult con campos estructurados.
    """

    @property
    @abstractmethod
    def document_type(self) -> DocumentType:
        """Tipo de documento que procesa este parser."""
        pass

    @abstractmethod
    def parse(self, ocr_result: OcrResult) -> DocumentResult:
        """
        Extrae campos del documento desde el resultado OCR.

        Args:
            ocr_result: Resultado del motor OCR con texto y confianza.

        Returns:
            DocumentResult con campos extraídos y normalizados.
        """
        pass

    # -------------------------------------------------------------------------
    # Helpers compartidos (disponibles para todas las implementaciones)
    # -------------------------------------------------------------------------

    def _clean_text(self, text: str) -> str:
        """
        Limpia texto eliminando espacios extra y caracteres no deseados.

        Args:
            text: Texto crudo.

        Returns:
            Texto limpio con espacios normalizados.
        """
        if not text:
            return ""
        return " ".join(text.strip().split())

    def _find_after_label(
        self,
        text: str,
        label: str,
        case_sensitive: bool = False,
    ) -> Optional[str]:
        """
        Busca una etiqueta y devuelve la siguiente línea no vacía.

        Args:
            text: Texto completo del OCR.
            label: Etiqueta a buscar (ej. "Apellidos", "Nombres").
            case_sensitive: Si es True, distingue mayúsculas/minúsculas.

        Returns:
            Siguiente línea no vacía, o None si no se encuentra.
        """
        if not text:
            return None

        lines = text.split("\n")
        label_lower = label.lower() if not case_sensitive else ""

        for i, line in enumerate(lines):
            line_stripped = line.strip()
            if not line_stripped:
                continue

            label_match = (
                label_lower in line_stripped.lower()
                if not case_sensitive
                else label in line_stripped
            )

            if label_match:
                # Buscar la siguiente línea no vacía
                for j in range(i + 1, len(lines)):
                    candidate = lines[j].strip()
                    if candidate:
                        return self._clean_text(candidate)
                return None

        return None

    def _extract_nuip(self, text: str) -> Optional[str]:
        """
        Extrae el número NUIP/número del documento.

        Patrón: NUIP seguido de dígitos con posibles puntos o comas.
        Ejemplo: "NUIP 1.234.567.890" → "1234567890"

        Args:
            text: Texto del OCR.

        Returns:
            Número limpio (solo dígitos), o None.
        """
        import re

        if not text:
            return None

        # Buscar patrón NUIP o NUP
        match = re.search(r"(?:NUIP|NUP)\s*([\d\.\,\s]+)", text, re.IGNORECASE)
        if match:
            raw = match.group(1)
            # Limpiar puntos, comas y espacios
            cleaned = re.sub(r"[^\d]", "", raw)
            if cleaned.isdigit():
                return cleaned

        # Fallback: buscar número con formato XXX.XXX.XXX
        match = re.search(r"\d{1,3}(?:\.\d{3}){2,}", text)
        if match:
            cleaned = re.sub(r"[^\d]", "", match.group(0))
            if cleaned.isdigit():
                return cleaned

        return None

    def _normalize_date(self, date_text: str) -> Optional[str]:
        """
        Convierte fecha de formato DD MMM YYYY a YYYY-MM-DD.

        Ejemplo: "12 DIC 1990" → "1990-12-12"
                 "12 DICIEMBRE 1990" → "1990-12-12"

        Args:
            date_text: Fecha en formato DD MMM YYYY.

        Returns:
            Fecha en formato YYYY-MM-DD, o None si no se puede parsear.
        """
        import re
        from datetime import datetime

        if not date_text:
            return None

        # Limpiar la fecha
        cleaned = self._clean_text(date_text)

        # Patrón: DD MMM YYYY (con o sin espacios extra)
        match = re.match(r"(\d{1,2})\s+([A-ZÁÉÍÓÚÑ]{3,})\s+(\d{4})", cleaned.upper())
        if not match:
            return None

        day = match.group(1).zfill(2)
        month_str = match.group(2)
        year = match.group(3)

        # Mapeo de meses en español
        month_map = {
            "ENE": "01",
            "ENERO": "01",
            "FEB": "02",
            "FEBRERO": "02",
            "MAR": "03",
            "MARZO": "03",
            "ABR": "04",
            "ABRIL": "04",
            "MAY": "05",
            "MAYO": "05",
            "JUN": "06",
            "JUNIO": "06",
            "JUL": "07",
            "JULIO": "07",
            "AGO": "08",
            "AGOSTO": "08",
            "SEP": "09",
            "SEPTIEMBRE": "09",
            "SEPTBRE": "09",
            "OCT": "10",
            "OCTUBRE": "10",
            "OCTUBRE": "10",
            "NOV": "11",
            "NOVIEMBRE": "11",
            "DIC": "12",
            "DICIEMBRE": "12",
        }

        month_key = month_str[:3]  # Tomar primeros 3 caracteres
        month = month_map.get(month_key)

        if not month:
            return None

        try:
            # Validar que la fecha sea válida
            datetime(int(year), int(month), int(day))
            return f"{year}-{month}-{day}"
        except ValueError:
            return None

    def _extract_date(self, text: str) -> Optional[str]:
        """
        Extrae la primera fecha en formato DD MMM YYYY del texto.

        Args:
            text: Texto del OCR.

        Returns:
            Fecha en YYYY-MM-DD, o None.
        """
        import re

        if not text:
            return None

        # Buscar patrón de fecha
        match = re.search(r"\d{1,2}\s+[A-ZÁÉÍÓÚÑ]{3,}\s+\d{4}", text, re.IGNORECASE)
        if match:
            return self._normalize_date(match.group(0))

        return None

    def _is_uppercase_text(self, text: str) -> bool:
        """
        Verifica si el texto está en mayúsculas y tiene al menos 2 palabras.

        Útil para detectar nombres y apellidos.

        Args:
            text: Texto a verificar.

        Returns:
            True si está en mayúsculas y tiene ≥2 palabras.
        """
        if not text:
            return False

        cleaned = text.strip()
        words = cleaned.split()

        return (
            len(words) >= 2
            and cleaned == cleaned.upper()
            and any(c.isalpha() for c in cleaned)
        )

    def _build_result(
        self,
        numero: Optional[str] = None,
        nombres: Optional[str] = None,
        apellidos: Optional[str] = None,
        fecha_nacimiento: Optional[str] = None,
        fecha_expiracion: Optional[str] = None,
        sexo: Optional[str] = None,
        lugar_nacimiento: Optional[str] = None,
        field_confidence: Optional[dict] = None,
        raw_text: Optional[str] = None,
        errors: Optional[list] = None,
    ) -> DocumentResult:
        """
        Construye un DocumentResult de forma consistente.

        Args:
            Campos del documento.
            field_confidence: Confianza por campo (opcional).
            raw_text: Texto crudo del OCR.
            errors: Lista de errores ocurridos.

        Returns:
            DocumentResult configurado.
        """
        return DocumentResult(
            document_type=self.document_type,
            status=ExtractionStatus.PARTIAL,  # Se auto-evalúa en __post_init__
            numero=numero,
            nombres=nombres,
            apellidos=apellidos,
            fecha_nacimiento=fecha_nacimiento,
            fecha_expiracion=fecha_expiracion,
            sexo=sexo,
            lugar_nacimiento=lugar_nacimiento,
            field_confidence=field_confidence or {},
            raw_ocr_text=raw_text,
            errors=errors or [],
        )
