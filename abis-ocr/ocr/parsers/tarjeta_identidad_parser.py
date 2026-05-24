"""
tarjeta_identidad_parser.py — v2

Layout real observado en imágenes de TI colombiana:

ANVERSO:
    REPÚBLICA DE COLOMBIA
    IDENTIFICACIÓN PERSONAL
    TARJETA DE IDENTIDAD           ← ancla de clasificación
    ──────────────────────────────────────────────────────
    NÚMERO   1.052.052.781         ← número en misma línea que "NÚMERO"
    TURIZO CHACON                  ← apellidos (antes de etiqueta — layout invertido)
    APELLIDOS
    JUAN PABLO                     ← nombres (antes de etiqueta)
    NOMBRES
    [firma]
    FIRMA

REVERSO:
    FECHA DE NACIMIENTO  23-JUN-2009   ← fecha en misma línea
    CICUCO                             ← ciudad de nacimiento
    (BOLIVAR)                          ← departamento (puede estar en línea separada)
    LUGAR DE NACIMIENTO
    23-JUN-2027                        ← fecha de vencimiento   ← DIFERENCIA VS CC AMARILLA
    FECHA DE VENCIMIENTO               ← etiqueta debajo
    AB+          M                     ← G.S. RH y SEXO en misma fila
    G S RH      SEXO
    09-AGO-2016 CICUCO                 ← fecha y lugar expedición
    FECHA Y LUGAR DE EXPEDICIÓN

DIFERENCIAS CLAVE vs CC Amarilla:
    - Encabezado: "TARJETA DE IDENTIDAD" en lugar de "CEDULA DE CIUDADANIA"
    - Reverso tiene "FECHA DE VENCIMIENTO" (TI) — CC amarilla no la tiene
    - Números TI: 10-11 dígitos, comúnmente inician con 1
    - Números CC: generalmente 6-10 dígitos, más cortos en cédulas antiguas
    - La fila de ESTATURA/G.S./SEXO puede no incluir estatura en TI más recientes
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

_DOCUMENT_LABELS = {
    "APELLIDOS", "NOMBRES", "NUMERO", "NÚMERO", "FIRMA",
    "REPUBLICA DE COLOMBIA", "REPÚBLICA DE COLOMBIA",
    "IDENTIFICACION PERSONAL", "IDENTIFICACIÓN PERSONAL",
    "TARJETA DE IDENTIDAD",
    "FECHA DE NACIMIENTO", "LUGAR DE NACIMIENTO",
    "FECHA DE VENCIMIENTO",
    "ESTATURA", "G.S. RH", "G S RH", "SEXO", "G.S", "GS",
    "FECHA Y LUGAR DE EXPEDICION", "FECHA Y LUGAR DE EXPEDICIÓN",
    "INDICE DERECHO", "ÍNDICE DERECHO",
    "REGISTRADOR NACIONAL",
}


class TarjetaIdentidadParser(BaseParser):

    @property
    def document_type(self) -> DocumentType:
        return DocumentType.TARJETA_IDENTIDAD

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
            errors.append("No se pudo extraer número de documento")

        # Layout invertido: valor ANTES de la etiqueta
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

        fecha_venc = self._extract_fecha_vencimiento(text)
        if fecha_venc:
            fc["fecha_expiracion"] = conf

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
            fecha_expiracion=fecha_venc,
            sexo=sexo,
            lugar_nacimiento=lugar_nac,
            field_confidence=fc,
            raw_text=text,
            errors=errors,
        )

        logger.info("TI parseada: numero=%s nombres=%s estado=%s",
                    numero, nombres, result.status.value)
        return result

    # -----------------------------------------------------------------------
    # Layout invertido — igual que CC amarilla
    # -----------------------------------------------------------------------

    def _extract_before_label(
        self, text: str, labels: list[str]
    ) -> Optional[str]:
        """
        Extrae el valor que aparece ANTES de su etiqueta.

        TI layout (igual a CC amarilla):
            TURIZO CHACON    ← valor
            APELLIDOS        ← etiqueta que buscamos
        """
        lines = [l.strip() for l in text.split("\n") if l.strip()]

        for i, line in enumerate(lines):
            if line.upper().strip() in [l.upper() for l in labels]:
                for j in range(i - 1, max(i - 4, -1), -1):
                    candidate = lines[j].strip()
                    if self._is_valid_name(candidate):
                        return candidate.upper()

        return None

    def _is_valid_name(self, text: str) -> bool:
        t = text.strip().upper()
        if not t or t in _DOCUMENT_LABELS:
            return False
        if re.match(r"^\d", t):
            return False
        if not re.search(r"[A-ZÁÉÍÓÚÜÑ]", t):
            return False
        if len(t) < 2:
            return False
        return True

    # -----------------------------------------------------------------------
    # Extracción de campos específicos
    # -----------------------------------------------------------------------

    def _extract_numero(self, text: str) -> Optional[str]:
        """
        Extrae el número de la TI.

        Layout: "NÚMERO   1.052.052.781" — etiqueta y valor en misma línea.
        Números TI: 10-11 dígitos, frecuentemente inician con 1.
        """
        # Patrón con etiqueta explícita
        match = re.search(
            r"N[ÚU]MERO\s+([\d][\d\.\s]{6,16})",
            text,
            re.IGNORECASE,
        )
        if match:
            clean = re.sub(r"[^\d]", "", match.group(1))
            if 8 <= len(clean) <= 11:
                return clean

        # Fallback: número con puntos de 10-11 dígitos
        for match in re.finditer(r"\b(\d{1,3}(?:\.\d{3}){2,3})\b", text):
            clean = re.sub(r"[^\d]", "", match.group(1))
            if 10 <= len(clean) <= 11:
                return clean

        return None

    def _extract_fecha_nacimiento(self, text: str) -> Optional[str]:
        """
        Extrae fecha de nacimiento del reverso.
        Layout: "FECHA DE NACIMIENTO  23-JUN-2009"
        """
        match = re.search(
            r"FECHA\s+DE\s+NACIMIENTO\s+([\d]{1,2}[\-\s][A-Z]{3}[\-\s][\d]{4})",
            text,
            re.IGNORECASE,
        )
        if match:
            return self._parse_date_spanish(match.group(1))

        lines = text.split("\n")
        for i, line in enumerate(lines):
            if "nacimiento" in line.lower() and "lugar" not in line.lower():
                date = self._parse_date_spanish(line)
                if date:
                    return date
                for j in range(i + 1, min(i + 3, len(lines))):
                    date = self._parse_date_spanish(lines[j])
                    if date:
                        return date

        return None

    def _extract_lugar_nacimiento(self, text: str) -> Optional[str]:
        """
        Extrae lugar de nacimiento del reverso.

        Layout TI (puede variar):
            CICUCO           ← ciudad
            (BOLIVAR)        ← departamento en paréntesis (misma o siguiente línea)
            LUGAR DE NACIMIENTO

        A veces la ciudad y el departamento están juntos: "CICUCO (BOLIVAR)"
        """
        lines = [l.strip() for l in text.split("\n") if l.strip()]

        for i, line in enumerate(lines):
            if "lugar de nacimiento" in line.lower():
                lugar_parts = []
                for j in range(i - 1, max(i - 4, -1), -1):
                    candidate = lines[j].strip().upper()
                    if (
                        candidate
                        and candidate not in _DOCUMENT_LABELS
                        and not re.match(r"^\d{2}[\-\s][A-Z]", candidate)  # no es fecha
                        and not re.match(r"^\d[\d\.]+\s", candidate)       # no es estatura
                        and re.search(r"[A-ZÁÉÍÓÚ]{2,}", candidate)
                    ):
                        lugar_parts.insert(0, candidate)
                    else:
                        break

                if lugar_parts:
                    # Unir ciudad y departamento si están separados
                    # Priorizar la línea con paréntesis (municipio + dpto)
                    for part in lugar_parts:
                        if "(" in part:
                            return part
                    # Si no hay paréntesis, unir lo que tenemos
                    return " ".join(lugar_parts)

        return None

    def _extract_fecha_vencimiento(self, text: str) -> Optional[str]:
        """
        Extrae la fecha de vencimiento de la TI.

        Esta es la diferencia clave vs CC amarilla: la TI tiene
        "FECHA DE VENCIMIENTO" en el reverso, la CC amarilla no.

        Layout: "23-JUN-2027" en línea previa a "FECHA DE VENCIMIENTO"
        """
        keywords = ["vencimiento", "fecha de venc"]
        lines = text.split("\n")

        for i, line in enumerate(lines):
            if any(kw in line.lower() for kw in keywords):
                # Misma línea
                date = self._parse_date_spanish(line)
                if date:
                    return date
                # Línea anterior (layout: fecha antes de etiqueta)
                for j in range(i - 1, max(i - 3, -1), -1):
                    date = self._parse_date_spanish(lines[j])
                    if date:
                        return date
                # Línea siguiente (layout alternativo)
                for j in range(i + 1, min(i + 3, len(lines))):
                    date = self._parse_date_spanish(lines[j])
                    if date:
                        return date

        return None

    def _extract_sexo(self, text: str) -> Optional[str]:
        """
        Extrae el sexo del reverso.

        Layout TI (dos variantes observadas):
            Variante 1: "AB+   M"   con etiquetas "G S RH  SEXO" debajo
            Variante 2: "1.70  O+   M"  con "ESTATURA  G.S. RH  SEXO" debajo

        Busca M/F aislado que NO sea parte de un nombre o fecha.
        """
        # Variante con grupo sanguíneo + sexo en misma línea
        match = re.search(
            r"\b[A-Z0-9]{1,3}[+-]\s+([MF])\b",
            text,
            re.IGNORECASE,
        )
        if match:
            return match.group(1).upper()

        # Variante con estatura + grupo + sexo
        match = re.search(
            r"\b\d[\d\.]+\s+[A-Z0-9]+[+-]?\s+([MF])\b",
            text,
            re.IGNORECASE,
        )
        if match:
            return match.group(1).upper()

        # Buscar en la línea que contiene la etiqueta SEXO
        lines = [l.strip() for l in text.split("\n") if l.strip()]
        for i, line in enumerate(lines):
            if re.search(r"\bSEXO\b", line, re.IGNORECASE):
                # Buscar M/F en línea previa
                for j in range(max(0, i - 2), i + 1):
                    m = re.search(r"\b([MF])\b", lines[j])
                    if m:
                        # Verificar que no sea parte de una palabra
                        start = m.start()
                        ctx = lines[j]
                        if (start == 0 or not ctx[start-1].isalpha()) and \
                           (m.end() == len(ctx) or not ctx[m.end()].isalpha()):
                            return m.group(1).upper()

        return None

    def _extract_fecha_expedicion(self, text: str) -> Optional[str]:
        """
        Extrae fecha de expedición del reverso.
        Layout: "09-AGO-2016 CICUCO" antes de "FECHA Y LUGAR DE EXPEDICIÓN"
        """
        keywords = ["expedicion", "expedición", "expedici"]
        lines = text.split("\n")

        for i, line in enumerate(lines):
            if any(kw in line.lower() for kw in keywords):
                # Buscar hacia atrás
                for j in range(i - 1, max(i - 3, -1), -1):
                    date = self._parse_date_spanish(lines[j])
                    if date:
                        return date
                date = self._parse_date_spanish(line)
                if date:
                    return date

        return None

    def _parse_date_spanish(self, text: str) -> Optional[str]:
        """Parsea fechas colombianas a YYYY-MM-DD."""
        text = text.strip().upper()

        match = re.search(
            r"\b(\d{1,2})[\s\-/]([A-Z]{3})[\s\-/](\d{4})\b",
            text,
        )
        if match:
            day, month_str, year = match.groups()
            month = _MONTHS_ES.get(month_str)
            if month:
                return f"{year}-{month}-{day.zfill(2)}"

        match = re.search(r"\b(\d{2})[\-/](\d{2})[\-/](\d{4})\b", text)
        if match:
            day, month, year = match.groups()
            return f"{year}-{month}-{day}"

        match = re.search(r"\b(\d{4})[\-/](\d{2})[\-/](\d{2})\b", text)
        if match:
            return match.group(0).replace("/", "-")

        return None

    def _build_failed_result(self, raw_text: str) -> DocumentResult:
        return DocumentResult(
            document_type=self.document_type,
            status=ExtractionStatus.FAILED,
            raw_ocr_text=raw_text,
            errors=["No se pudo extraer información útil de la tarjeta de identidad"],
        )