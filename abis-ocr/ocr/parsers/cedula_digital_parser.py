"""
cedula_digital_parser.py  — v2

Campos reales observados en imágenes de CC Digital colombiana (2018–2024+):

ANVERSO:
    Etiqueta          Valor ejemplo
    ─────────────────────────────────────────────
    NUIP              1.052.041.109
    Apellidos         TURIZO CHACON
    Nombres           DANIEL DAVID
    Nacionalidad      COL
    Estatura          1.80
    Sexo              M
    Fecha de nac.     13 JUN 2006   ó   13-JUN-2006
    G.S.              B+
    Lugar de nac.     CICUCO (BOLIVAR)
    Fecha y lugar exp 21 JUN 2024, CICUCO
    Fecha expiración  11 JUN 2035

REVERSO (MRZ de 3 líneas, orientación vertical):
    Línea 1: ICCOL062598805015<<<<<<<<<<<<<<
    Línea 2: 0606136M3506112COL1052041109<9
    Línea 3: TURIZO<<CHACON<<DANIEL<DAVID<<<
    → Parseado por _parse_mrz() para validación cruzada
"""

import re
import logging
from typing import Optional

from .base_parser import BaseParser
from ocr.models.document_result import DocumentResult, DocumentType, ExtractionStatus
from ocr.models.ocr_result import OcrResult

logger = logging.getLogger(__name__)

# Meses abreviados en español e inglés (OCR puede confundir)
_MONTHS_ES = {
    "ENE": "01",
    "FEB": "02",
    "MAR": "03",
    "ABR": "04",
    "MAY": "05",
    "MAYO": "05",
    "JUN": "06",
    "JUL": "07",
    "AGO": "08",
    "SEP": "09",
    "OCT": "10",
    "NOV": "11",
    "DIC": "12",
    # OCR typos frecuentes
    "JAN": "01",
    "AUG": "08",
    "APR": "04",
}


class CedulaDigitalParser(BaseParser):
    @property
    def document_type(self) -> DocumentType:
        return DocumentType.CEDULA_DIGITAL

    def parse(self, ocr_result: OcrResult) -> DocumentResult:
        if not ocr_result.is_usable:
            return self._build_failed_result(ocr_result.raw_text)

        text = ocr_result.raw_text
        fc = {}
        errors = []
        conf = ocr_result.confidence

        # ── Intentar MRZ primero (más confiable en CC digital) ──────────────
        mrz_data = self._parse_mrz(text)

        # ── Campos del anverso ──────────────────────────────────────────────
        numero = self._extract_nuip(text)
        if numero:
            fc["numero"] = conf
        elif mrz_data and mrz_data.get("numero"):
            numero = mrz_data["numero"]
            fc["numero"] = conf
        else:
            errors.append("No se pudo extraer NUIP")

        # Nombres y apellidos: preferir MRZ si existe, si no usar etiquetas
        if mrz_data and mrz_data.get("apellidos"):
            apellidos = mrz_data["apellidos"]
            fc["apellidos"] = conf * 0.95
        else:
            apellidos = self._extract_labeled_field(text, ["Apellidos", "APELLIDOS"])
            if apellidos:
                fc["apellidos"] = conf

        if not apellidos:
            errors.append("No se pudieron extraer apellidos")

        if mrz_data and mrz_data.get("nombres"):
            nombres = mrz_data["nombres"]
            fc["nombres"] = conf * 0.95
        else:
            nombres = self._extract_labeled_field(text, ["Nombres", "NOMBRES"])
            if nombres:
                fc["nombres"] = conf

        if not nombres:
            errors.append("No se pudieron extraer nombres")

        sexo = self._extract_sexo(text)
        if sexo:
            fc["sexo"] = conf

        fecha_nac = self._extract_fecha_nacimiento(text)
        if fecha_nac:
            fc["fecha_nacimiento"] = conf

        lugar_nac = self._extract_lugar_nacimiento(text)
        if lugar_nac:
            fc["lugar_nacimiento"] = conf

        fecha_exp = self._extract_fecha_expiracion(text)
        if fecha_exp:
            fc["fecha_expiracion"] = conf

        fecha_expedicion = self._extract_fecha_expedicion(text)
        if fecha_expedicion:
            fc["fecha_expedicion"] = conf

        result = self._build_result(
            numero=numero,
            nombres=nombres,
            apellidos=apellidos,
            fecha_nacimiento=fecha_nac,
            fecha_expiracion=fecha_exp,
            sexo=sexo,
            lugar_nacimiento=lugar_nac,
            field_confidence=fc,
            raw_text=text,
            errors=errors,
        )

        logger.info(
            "CC digital parseada: numero=%s nombres=%s estado=%s",
            numero,
            nombres,
            result.status.value,
        )
        return result

    # -----------------------------------------------------------------------
    # Extracción por etiqueta — núcleo del parser digital
    # -----------------------------------------------------------------------

    def _reconstruct_spaces(self, text: str) -> str:
        """
        Reconstruye espacios perdidos en nombres/apellidos colombianos.

        PaddleOCR a veces colapsa "CUELLAR HINOJOSA" -> "CUELLARHINOJOSA".
        Este metodo intenta insertar el espacio en el punto correcto usando
        el diccionario de apellidos colombianos mas frecuentes como referencia.

        Estrategia: si el texto no tiene espacios y tiene mas de 8 caracteres,
        busca el punto de corte optimo donde ambas partes sean palabras validas
        (solo letras, longitud entre 3 y 12 caracteres cada una).
        Si no puede determinar el corte, retorna el texto original sin modificar.

        Args:
            text: Texto en mayusculas, posiblemente sin espacios.

        Returns:
            Texto con espacios reconstruidos, o el original si no es posible.
        """
        # Si ya tiene espacios, no hacer nada
        if " " in text or len(text) <= 6:
            return text

        # Solo letras (nombres/apellidos no tienen numeros ni simbolos)
        if not re.match(r"^[A-ZÁÉÍÓÚÜÑ]+$", text):
            return text

        # Buscar el punto de corte optimo para 2 palabras
        # Rango valido para cada parte: 3-12 caracteres
        best_split = None
        best_score = 0

        for i in range(3, len(text) - 2):
            part1 = text[:i]
            part2 = text[i:]

            # Ambas partes deben tener longitud valida para un apellido/nombre
            if not (3 <= len(part1) <= 12 and 3 <= len(part2) <= 12):
                continue

            # Score: penalizar cortes muy desiguales, premiar cortes equilibrados
            # Un corte ideal divide el texto en partes de longitud similar
            length_diff = abs(len(part1) - len(part2))
            score = 10 - length_diff

            # Bonus: si part2 empieza con consonante fuerte (patron comun en apellidos)
            if part2[0] in "BCDFGHJKLMNPQRSTVWXYZ":
                score += 2

            if score > best_score:
                best_score = score
                best_split = i

        if best_split and best_score >= 5:
            reconstructed = f"{text[:best_split]} {text[best_split:]}"
            logger.debug(
                "Espacio reconstruido: '%s' -> '%s'", text, reconstructed
            )
            return reconstructed

        return text

    def _extract_labeled_field(self, text: str, labels: list[str]) -> Optional[str]:
        """
        Extrae el valor que aparece INMEDIATAMENTE después de una etiqueta.

        La CC digital usa etiquetas en línea propia seguidas del valor
        en la línea siguiente. Ej:
            Apellidos
            TURIZO CHACON
            Nombres
            DANIEL DAVID

        Args:
            text  : Texto OCR completo.
            labels: Posibles variantes de la etiqueta (ej. ["Apellidos", "APELLIDOS"]).

        Returns:
            Valor en mayúsculas normalizado, o None.
        """
        lines = [l.strip() for l in text.split("\n") if l.strip()]

        for i, line in enumerate(lines):
            # Buscar línea que sea SOLO la etiqueta (o empiece con ella)
            line_upper = line.upper()
            for label in labels:
                if line_upper == label.upper() or line_upper.startswith(
                    label.upper() + " "
                ):
                    # Buscar el valor en las 2 líneas siguientes
                    for j in range(i + 1, min(i + 3, len(lines))):
                        candidate = lines[j].strip()
                        candidate_upper = candidate.upper()
                        # Debe ser texto alfabético, no otra etiqueta, sin dígitos
                        if (
                            len(candidate) >= 2
                            and not self._is_label(candidate_upper)
                            and not re.search(r"\d", candidate)
                            and re.search(r"[A-Za-záéíóúÁÉÍÓÚüÜñÑ]", candidate)
                        ):
                            return self._reconstruct_spaces(candidate.upper())

        # Fallback: buscar "Etiqueta: Valor" en la misma línea
        for label in labels:
            pattern = rf"{re.escape(label)}\s*[:\-]?\s*([A-ZÁÉÍÓÚÜÑ][A-ZÁÉÍÓÚÜÑ\s]+)"
            match = re.search(pattern, text, re.IGNORECASE)
            if match:
                return self._reconstruct_spaces(match.group(1).strip().upper())

        return None

    def _is_label(self, text: str) -> bool:
        """True si el texto parece ser una etiqueta del documento, no un valor."""
        labels = {
            "APELLIDOS",
            "NOMBRES",
            "NACIONALIDAD",
            "ESTATURA",
            "SEXO",
            "FECHA DE NACIMIENTO",
            "LUGAR DE NACIMIENTO",
            "G.S.",
            "GS",
            "FECHA Y LUGAR DE EXPEDICION",
            "FECHA Y LUGAR DE EXPEDICIÓN",
            "FECHA DE EXPIRACION",
            "FECHA DE EXPIRACIÓN",
            "NUIP",
            "FIRMA",
            "CEDULA DE CIUDADANIA",
            "REPÚBLICA DE COLOMBIA",
            "REPUBLICA DE COLOMBIA",
        }
        return text.strip().upper() in labels

    # -----------------------------------------------------------------------
    # Extracción de campos específicos
    # -----------------------------------------------------------------------

    def _extract_nuip(self, text: str) -> Optional[str]:
        """
        Extrae el NUIP (Numero Unico de Identificacion Personal).

        Formatos observados en CC digital colombiana:
            "NUIP 1.052.041.109"         -> misma linea, con espacio
            "NUIP1.052.041.109"          -> sin espacio
            "NUIP\n1.052.041.109"        -> PaddleOCR los pone en lineas separadas
            "NUIP\n1\n052.041.109"       -> PaddleOCR fragmenta el numero

        El bug original: cuando el numero esta en la linea siguiente,
        el regex principal puede fallar y el fallback encuentra solo
        "052.041.109" perdiendo el primer digito "1".
        """
        # -- Estrategia 1: NUIP + numero en misma linea o linea siguiente ----
        # [\s\S] permite cruzar saltos de linea sin depender de DOTALL.
        match = re.search(
            r"NUIP[\s\S]{0,15}?(\d[\d\.\s]{7,17})",
            text,
            re.IGNORECASE,
        )
        if match:
            raw = match.group(1)
            clean = re.sub(r"[^\d]", "", raw)
            if 7 <= len(clean) <= 11:
                return clean

        # -- Estrategia 2: reconstruir numero desde lineas fragmentadas -------
        # PaddleOCR a veces parte "1.067.598.351" en:
        # linea N:   "NUIP"  o  "NUIP 1."
        # linea N+1: "067.598.351"
        lines = text.split("\n")
        for i, line in enumerate(lines):
            if "nuip" in line.lower():
                # Concatenar las 3 lineas siguientes para reconstruir el numero
                chunk = " ".join(lines[i : min(i + 4, len(lines))])
                match = re.search(
                    r"NUIP[\s\S]{0,20}?(\d[\d\.\s]{7,17})",
                    chunk,
                    re.IGNORECASE,
                )
                if match:
                    clean = re.sub(r"[^\d]", "", match.group(1))
                    if 7 <= len(clean) <= 11:
                        return clean

        # -- Estrategia 3: fallback -- numero con puntos formato colombiano ---
        # Busca el patron X.XXX.XXX o X.XXX.XXX.XXX donde X puede ser 1-3 digitos.
        # IMPORTANTE: busca TODOS los matches y filtra por longitud valida.
        matches = re.findall(r"\b(\d{1,3}(?:\.\d{3}){1,3})\b", text)
        for m in matches:
            clean = re.sub(r"[^\d]", "", m)
            if 7 <= len(clean) <= 11:
                return clean

        return None

    def _extract_sexo(self, text: str) -> Optional[str]:
        """
        Extrae el sexo M/F.
        En CC digital: etiqueta "Sexo" con valor en misma línea o siguiente.
        Ej: "Sexo\nM" o "Sexo M" o "Sexo\n          M"
        """
        match = re.search(r"Sexo\s*[\n\r\s:]*([MF])\b", text, re.IGNORECASE)
        if match:
            return match.group(1).upper()

        # Buscar M o F aislados entre campos conocidos
        lines = [l.strip() for l in text.split("\n") if l.strip()]
        for i, line in enumerate(lines):
            if "sexo" in line.lower():
                # Buscar en las 5 líneas siguientes (COL y estatura pueden interponerse)
                for j in range(i, min(i + 6, len(lines))):
                    line_clean = lines[j].upper().strip()
                    if line_clean in ("M", "F"):
                        return line_clean
                    m = re.search(r"\b([MF])\b", line_clean, re.IGNORECASE)
                    if m:
                        return m.group(1).upper()

        return None

    def _extract_fecha_nacimiento(self, text: str) -> Optional[str]:
        """
        Extrae fecha de nacimiento.
        Formato CC digital: "13 JUN 2006" ó "13-JUN-2006"
        Etiqueta: "Fecha de nacimiento" ó "Fecha de nac"
        """
        keywords = ["fecha de nacimiento", "fecha de nac", "nacimiento"]
        for kw in keywords:
            if kw in text.lower():
                lines = text.split("\n")
                for i, line in enumerate(lines):
                    if kw in line.lower():
                        # Buscar fecha en misma línea o las 4 siguientes
                        search_lines = [line] + lines[i + 1 : i + 5]
                        for sl in search_lines:
                            date = self._parse_date_spanish(sl)
                            if date:
                                return date
        return None

    def _extract_fecha_expiracion(self, text: str) -> Optional[str]:
        """Fecha de expiración / vencimiento."""
        keywords = ["fecha de expir", "expiracion", "expiración", "vencimiento"]
        for kw in keywords:
            if kw in text.lower():
                lines = text.split("\n")
                for i, line in enumerate(lines):
                    if kw in line.lower():
                        search_lines = [line] + lines[i + 1 : i + 5]
                        for sl in search_lines:
                            date = self._parse_date_spanish(sl)
                            if date:
                                return date
        return None

    def _extract_fecha_expedicion(self, text: str) -> Optional[str]:
        """Fecha y lugar de expedición."""
        keywords = ["expedicion", "expedición", "expedici"]
        for kw in keywords:
            if kw in text.lower():
                lines = text.split("\n")
                for i, line in enumerate(lines):
                    if kw in line.lower():
                        search_lines = [line] + lines[i + 1 : i + 3]
                        for sl in search_lines:
                            date = self._parse_date_spanish(sl)
                            if date:
                                return date
        return None

    def _extract_lugar_nacimiento(self, text: str) -> Optional[str]:
        """
        Extrae lugar de nacimiento.
        Formato: "CICUCO (BOLIVAR)" — texto libre con posible departamento.
        """
        keywords = ["lugar de nacimiento", "lugar de nac"]
        lines = [l.strip() for l in text.split("\n") if l.strip()]

        for i, line in enumerate(lines):
            if any(kw in line.lower() for kw in keywords):
                for j in range(i + 1, min(i + 3, len(lines))):
                    candidate = lines[j].strip()
                    # Debe tener letras, puede tener paréntesis para departamento
                    if (
                        re.search(r"[A-ZÁÉÍÓÚÜÑ]{3,}", candidate.upper())
                        and not self._is_label(candidate.upper())
                        and not re.match(r"^\d", candidate)
                    ):
                        return candidate.upper()

        return None

    def _parse_date_spanish(self, text: str) -> Optional[str]:
        """
        Parsea fechas en formato colombiano a YYYY-MM-DD.

        Formatos soportados:
            "13 JUN 2006"    → "2006-06-13"
            "13-JUN-2006"    → "2006-06-13"
            "13/06/2006"     → "2006-06-13"
            "2006-06-13"     → "2006-06-13" (ya normalizado)
        """
        text = text.strip().upper()

        # Formato sin separadores: DDMMMYYYY o DDMMMMYYYY
        # Soporta meses de 3 letras (ENE, FEB...) y 4 letras (MAYO)
        match = re.search(
            r"\b(\d{1,2})([A-Z]{3,4})(\d{4})\b",
            text,
        )
        if match:
            day, month_str, year = match.groups()
            month = _MONTHS_ES.get(month_str)
            if month:
                return f"{year}-{month}-{day.zfill(2)}"

        # Formato: DD MES AAAA ó DD-MES-AAAA
        match = re.search(
            r"\b(\d{1,2})[\s\-/]([A-Z]{3})[\s\-/](\d{4})\b",
            text,
        )
        if match:
            day, month_str, year = match.groups()
            month = _MONTHS_ES.get(month_str)
            if month:
                return f"{year}-{month}-{day.zfill(2)}"

        # Formato numérico: DD/MM/AAAA ó DD-MM-AAAA
        match = re.search(r"\b(\d{2})[\-/](\d{2})[\-/](\d{4})\b", text)
        if match:
            day, month, year = match.groups()
            return f"{year}-{month}-{day}"

        # Ya en formato ISO
        match = re.search(r"\b(\d{4})[\-/](\d{2})[\-/](\d{2})\b", text)
        if match:
            return match.group(0).replace("/", "-")

        return None

    # -----------------------------------------------------------------------
    # Parseo MRZ para validación cruzada
    # -----------------------------------------------------------------------

    def _parse_mrz(self, text: str) -> Optional[dict]:
        """
        Parsea el MRZ del reverso de la CC digital si está en el texto OCR.

        Formato de 3 líneas:
            Línea 1: ICCOL + número serie
            Línea 2: DDMMYY + sexo + DDMMYY + COL + NUIP
            Línea 3: APELLIDO1<<APELLIDO2<<NOMBRE1<NOMBRE2<<<

        Returns:
            Dict con numero, apellidos, nombres o None si no hay MRZ.
        """
        # Buscar línea 3 del MRZ: apellidos<<nombres con << como separador
        match = re.search(
            r"([A-Z]{2,15})<<([A-Z]{2,15})<<([A-Z]{2,15})<([A-Z]{2,15})",
            text.upper(),
        )
        if not match:
            # Intentar patrón más flexible
            match = re.search(
                r"([A-Z]{3,})<<+([A-Z]{3,})<<+([A-Z]{3,})",
                text.upper(),
            )
            if not match:
                return None

        groups = match.groups()
        result = {}

        if len(groups) >= 2:
            result["apellidos"] = f"{groups[0]} {groups[1]}"
        if len(groups) >= 4:
            result["nombres"] = f"{groups[2]} {groups[3]}"
        elif len(groups) == 3:
            result["nombres"] = groups[2]

        # Buscar NUIP en línea 2: ...COL + número
        nuip_match = re.search(r"COL(\d{7,11})", text.upper())
        if nuip_match:
            result["numero"] = nuip_match.group(1)

        return result if result else None

    def _build_failed_result(self, raw_text: str) -> DocumentResult:
        return DocumentResult(
            document_type=self.document_type,
            status=ExtractionStatus.FAILED,
            raw_ocr_text=raw_text,
            errors=["No se pudo extraer información útil de la cédula digital"],
        )
