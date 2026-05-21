"""
cedula_amarilla_parser.py — v2

Layout real observado en imágenes de CC Amarilla colombiana:

ANVERSO:
    REPUBLICA DE COLOMBIA
    IDENTIFICACION PERSONAL
    CEDULA DE CIUDADANIA
    ─────────────────────────────────────
    NUMERO   22.994.874          ← número en misma línea que "NUMERO"
    CHACON LARA                  ← apellidos SIN etiqueta propia (van aquí)
    APELLIDOS                    ← etiqueta debajo de los apellidos
    LEDYS                        ← nombres SIN etiqueta propia
    NOMBRES                      ← etiqueta debajo de los nombres
    [firma]
    FIRMA

REVERSO:
    FECHA DE NACIMIENTO  06-SEP-1970   ← fecha en misma línea que etiqueta
    EL LIMON                           ← lugar: ciudad
    TALAIGUA NUEVO (BOLIVAR)           ← lugar: municipio (departamento)
    LUGAR DE NACIMIENTO                ← etiqueta debajo del lugar
    1.63          A-          F        ← ESTATURA, G.S. RH, SEXO en misma fila
    ESTATURA      G.S. RH    SEXO      ← etiquetas debajo de los valores
    30-ENE-1989 TALAIGUA NUEVO         ← fecha y ciudad expedición en misma línea
    FECHA Y LUGAR DE EXPEDICION        ← etiqueta debajo
    [PDF417 horizontal]
    A-0501500-00227715-F-0022994874-20100324  ← código de barras decodificado

NOTA: Los apellidos y nombres están ANTES de su etiqueta (layout invertido).
      El valor está encima de la etiqueta — diferente a la CC digital.
"""

import re
import logging
from typing import Optional

from .base_parser import BaseParser
from ocr.models.document_result import DocumentResult, DocumentType, ExtractionStatus
from ocr.models.ocr_result import OcrResult

logger = logging.getLogger(__name__)

_MONTHS_ES = {
    "ENE": "01", "FEB": "02", "MAR": "03", "ABR": "04",
    "MAY": "05", "JUN": "06", "JUL": "07", "AGO": "08",
    "SEP": "09", "OCT": "10", "NOV": "11", "DIC": "12",
    "JAN": "01", "AUG": "08", "APR": "04",
}

# Etiquetas que delimitan zonas del documento (no son valores)
_DOCUMENT_LABELS = {
    "APELLIDOS", "NOMBRES", "NUMERO", "NÚMERO", "FIRMA",
    "REPUBLICA DE COLOMBIA", "REPÚBLICA DE COLOMBIA",
    "IDENTIFICACION PERSONAL", "IDENTIFICACIÓN PERSONAL",
    "CEDULA DE CIUDADANIA", "CÉDULA DE CIUDADANÍA",
    "FECHA DE NACIMIENTO", "LUGAR DE NACIMIENTO",
    "ESTATURA", "G.S. RH", "SEXO", "G.S", "GS",
    "FECHA Y LUGAR DE EXPEDICION", "FECHA Y LUGAR DE EXPEDICIÓN",
    "INDICE DERECHO", "ÍNDICE DERECHO",
    "REGISTRADOR NACIONAL",
}


class CedulaAmarillaParser(BaseParser):

    @property
    def document_type(self) -> DocumentType:
        return DocumentType.CEDULA_AMARILLA

    def parse(self, ocr_result: OcrResult) -> DocumentResult:
        if not ocr_result.is_usable:
            return self._build_failed_result(ocr_result.raw_text)

        text = ocr_result.raw_text
        fc = {}
        errors = []
        conf = ocr_result.confidence

        numero = self._extract_numero(text)
        if numero:
            fc["numero"] = conf
        else:
            errors.append("No se pudo extraer el número de documento")

        apellidos = self._extract_before_label(text, ["APELLIDOS"])
        if apellidos:
            fc["apellidos"] = conf
        else:
            errors.append("No se pudieron extraer apellidos")

        nombres = self._extract_before_label(text, ["NOMBRES"])
        if nombres:
            fc["nombres"] = conf
        else:
            errors.append("No se pudieron extraer nombres")

        # Campos del reverso
        fecha_nac = self._extract_fecha_nacimiento(text)
        if fecha_nac:
            fc["fecha_nacimiento"] = conf

        lugar_nac = self._extract_lugar_nacimiento(text)
        if lugar_nac:
            fc["lugar_nacimiento"] = conf

        sexo = self._extract_sexo(text)
        if sexo:
            fc["sexo"] = conf

        fecha_exp = self._extract_fecha_expedicion(text)
        if fecha_exp:
            fc["fecha_expedicion"] = conf

        result = self._build_result(
            numero=numero,
            nombres=nombres,
            apellidos=apellidos,
            fecha_nacimiento=fecha_nac,
            fecha_expiracion=None,   # CC amarilla no tiene fecha expiración
            sexo=sexo,
            lugar_nacimiento=lugar_nac,
            field_confidence=fc,
            raw_text=text,
            errors=errors,
        )

        logger.info("CC amarilla parseada: numero=%s nombres=%s estado=%s",
                    numero, nombres, result.status.value)
        return result

    # -----------------------------------------------------------------------
    # Extracción por layout invertido (valor ANTES de su etiqueta)
    # -----------------------------------------------------------------------

    def _extract_before_label(
        self, text: str, labels: list[str]
    ) -> Optional[str]:
        """
        Extrae el valor que aparece INMEDIATAMENTE ANTES de una etiqueta.

        En CC amarilla el layout es:
            CHACON LARA      ← valor (esta línea)
            APELLIDOS        ← etiqueta (la buscamos)

        Args:
            text  : Texto OCR completo.
            labels: Etiquetas a buscar (ej. ["APELLIDOS"]).

        Returns:
            Valor en mayúsculas, o None.
        """
        lines = [l.strip() for l in text.split("\n") if l.strip()]

        for i, line in enumerate(lines):
            line_upper = line.upper().strip()
            for label in labels:
                if line_upper == label.upper():
                    # Buscar hacia atrás: la línea previa no-vacía que sea un valor
                    for j in range(i - 1, max(i - 4, -1), -1):
                        candidate = lines[j].strip()
                        if self._is_valid_name(candidate):
                            return candidate.upper()

        return None

    def _is_valid_name(self, text: str) -> bool:
        """
        True si el texto parece un nombre/apellido válido.
        Debe ser alfabético, en mayúsculas, sin ser una etiqueta conocida.
        """
        t = text.strip().upper()
        if not t:
            return False
        if t in _DOCUMENT_LABELS:
            return False
        if re.match(r"^\d", t):             # empieza con número
            return False
        if not re.search(r"[A-ZÁÉÍÓÚÜÑ]", t):  # sin letras
            return False
        if len(t) < 2:
            return False
        return True

    # -----------------------------------------------------------------------
    # Extracción de campos específicos
    # -----------------------------------------------------------------------

    def _extract_numero(self, text: str) -> Optional[str]:
        """
        Extrae el número de la CC amarilla.

        Layout: "NUMERO   22.994.874" — etiqueta y valor en la misma línea.
        También puede aparecer como "NÚMERO" con tilde.
        """
        # Patrón principal: NUMERO/NÚMERO seguido del número
        match = re.search(
            r"N[ÚU]MERO\s+([\d][\d\.\s]{4,14})",
            text,
            re.IGNORECASE,
        )
        if match:
            clean = re.sub(r"[^\d]", "", match.group(1))
            if 6 <= len(clean) <= 11:
                return clean

        # Fallback: número con puntos en formato colombiano
        # Evitar capturar fechas (que tienen meses en texto)
        match = re.search(r"\b(\d{1,3}(?:\.\d{3}){1,3})\b", text)
        if match:
            clean = re.sub(r"[^\d]", "", match.group(1))
            if 6 <= len(clean) <= 11:
                return clean

        return None

    def _extract_fecha_nacimiento(self, text: str) -> Optional[str]:
        """
        Extrae fecha de nacimiento del reverso.

        Layout: "FECHA DE NACIMIENTO  06-SEP-1970" — todo en la misma línea.
        """
        match = re.search(
            r"FECHA\s+DE\s+NACIMIENTO\s+([\d]{1,2}[\-\s][A-Z]{3}[\-\s][\d]{4})",
            text,
            re.IGNORECASE,
        )
        if match:
            return self._parse_date_spanish(match.group(1))

        # Fallback: buscar en líneas siguientes a la etiqueta
        lines = text.split("\n")
        for i, line in enumerate(lines):
            if "nacimiento" in line.lower() and "lugar" not in line.lower():
                # Buscar en misma línea primero
                date = self._parse_date_spanish(line)
                if date:
                    return date
                # Luego en las siguientes
                for j in range(i + 1, min(i + 3, len(lines))):
                    date = self._parse_date_spanish(lines[j])
                    if date:
                        return date

        return None

    def _extract_lugar_nacimiento(self, text: str) -> Optional[str]:
        """
        Extrae el lugar de nacimiento del reverso.

        Layout:
            EL LIMON                     ← ciudad/corregimiento
            TALAIGUA NUEVO (BOLIVAR)     ← municipio (departamento)
            LUGAR DE NACIMIENTO          ← etiqueta debajo

        Retorna la línea más informativa (municipio + departamento si existe).
        """
        lines = [l.strip() for l in text.split("\n") if l.strip()]

        for i, line in enumerate(lines):
            if "lugar de nacimiento" in line.lower():
                # Buscar hacia atrás: las 2 líneas previas son el lugar
                lugar_parts = []
                for j in range(i - 1, max(i - 3, -1), -1):
                    candidate = lines[j].strip().upper()
                    if (
                        candidate
                        and candidate not in _DOCUMENT_LABELS
                        and not re.match(r"^\d[\d\.\-]", candidate)
                        and re.search(r"[A-ZÁÉÍÓÚ]{3,}", candidate)
                    ):
                        lugar_parts.insert(0, candidate)
                    else:
                        break

                if lugar_parts:
                    # Preferir la línea que tenga departamento entre paréntesis
                    for part in reversed(lugar_parts):
                        if "(" in part:
                            return part
                    return lugar_parts[-1]

        return None

    def _extract_sexo(self, text: str) -> Optional[str]:
        """
        Extrae el sexo del reverso.

        Layout: "1.63    A-    F" — valores en fila horizontal
                "ESTATURA  G.S. RH  SEXO" — etiquetas debajo

        El sexo (M/F) aparece en la misma fila que estatura y grupo sanguíneo,
        ANTES de la etiqueta SEXO.
        """
        # Buscar patrón: valor numérico + grupo sanguíneo + M/F en la misma línea
        match = re.search(
            r"\b\d[\d\.]+\s+[A-Z0-9][+-]?\s+([MF])\b",
            text,
            re.IGNORECASE,
        )
        if match:
            return match.group(1).upper()

        # Patrón directo: SEXO seguido o precedido de M/F
        match = re.search(r"SEXO\s*[\n\r\s]*([MF])\b", text, re.IGNORECASE)
        if match:
            return match.group(1).upper()

        # Buscar línea con SEXO y encontrar M/F en misma o línea previa
        lines = [l.strip() for l in text.split("\n") if l.strip()]
        for i, line in enumerate(lines):
            if "sexo" in line.lower():
                for j in range(max(0, i - 2), i + 2):
                    m = re.search(r"\b([MF])\b", lines[j], re.IGNORECASE)
                    if m:
                        return m.group(1).upper()

        return None

    def _extract_fecha_expedicion(self, text: str) -> Optional[str]:
        """
        Extrae fecha de expedición del reverso.

        Layout: "30-ENE-1989 TALAIGUA NUEVO" — fecha seguida de ciudad.
                "FECHA Y LUGAR DE EXPEDICION" — etiqueta debajo.
        """
        keywords = ["expedicion", "expedición", "expedici"]
        lines = text.split("\n")

        for i, line in enumerate(lines):
            if any(kw in line.lower() for kw in keywords):
                # Buscar hacia atrás la línea con la fecha
                for j in range(i - 1, max(i - 3, -1), -1):
                    date = self._parse_date_spanish(lines[j])
                    if date:
                        return date
                # También en misma línea
                date = self._parse_date_spanish(line)
                if date:
                    return date

        return None

    def _parse_date_spanish(self, text: str) -> Optional[str]:
        """Parsea fechas colombianas a YYYY-MM-DD."""
        text = text.strip().upper()

        # DD-MES-AAAA o DD MES AAAA
        match = re.search(
            r"\b(\d{1,2})[\s\-/]([A-Z]{3})[\s\-/](\d{4})\b",
            text,
        )
        if match:
            day, month_str, year = match.groups()
            month = _MONTHS_ES.get(month_str)
            if month:
                return f"{year}-{month}-{day.zfill(2)}"

        # DD/MM/AAAA
        match = re.search(r"\b(\d{2})[\-/](\d{2})[\-/](\d{4})\b", text)
        if match:
            day, month, year = match.groups()
            return f"{year}-{month}-{day}"

        # ISO AAAA-MM-DD
        match = re.search(r"\b(\d{4})[\-/](\d{2})[\-/](\d{2})\b", text)
        if match:
            return match.group(0).replace("/", "-")

        return None

    def _build_failed_result(self, raw_text: str) -> DocumentResult:
        return DocumentResult(
            document_type=self.document_type,
            status=ExtractionStatus.FAILED,
            raw_ocr_text=raw_text,
            errors=["No se pudo extraer información útil de la cédula amarilla"],
        )
