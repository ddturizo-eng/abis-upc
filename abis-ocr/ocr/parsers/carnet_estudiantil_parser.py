"""
carnet_estudiantil_parser.py — v1

Layout real observado (Universidad Popular del Cesar / Unicesar):

    UNIVERSIDAD POPULAR DEL CESAR     ← encabezado (logo + nombre)
    [foto]
    TURIZO CHACON                     ← apellidos (línea 1 del nombre, en rojo/gris)
    JOSE DARICK                       ← nombres   (línea 2 del nombre, en rojo/negrita)
    ──────────────────────────────────
    INGENIERIA AMBIENTAL Y SANITARIA  ← programa académico
    ──────────────────────────────────
    CC 1.007.819.137                  ← tipo doc + número en misma línea
    www.unicesar.edu.co

VARIANTES OBSERVADAS EN OTROS CARNETS COLOMBIANOS:
    "T.I 1.052.052.781"   ← tarjeta de identidad
    "TI 1.052.052.781"    ← sin punto
    "C.C. 1.007.819.137"  ← con puntos en el prefijo

PARTICULARIDADES:
    - No hay etiquetas explícitas APELLIDOS / NOMBRES como en CC/TI.
    - El nombre completo aparece en 2 líneas: apellidos arriba, nombres abajo.
    - El número está precedido por el tipo de documento (CC / T.I / TI).
    - No hay fecha de nacimiento ni sexo en el anverso del carnet.
    - El programa/facultad está entre el nombre y el número.
"""

import re
import logging
from typing import Optional

from .base_parser import BaseParser
from ocr.models.document_result import DocumentResult, DocumentType, ExtractionStatus
from ocr.models.ocr_result import OcrResult

logger = logging.getLogger(__name__)

# Patrones de tipo de documento que preceden al número en el carnet
_DOC_TYPE_PATTERN = re.compile(
    r"\b(C\.?C\.?|T\.?I\.?|TI|CC)\s*([\d][.\d\s]{5,16})",
    re.IGNORECASE,
)

# Palabras que indican que una línea es institucional/académica (no nombre)
_INSTITUTIONAL_KEYWORDS = {
    "UNIVERSIDAD", "POLITECNICO", "POLITÉCNICO", "INSTITUCIÓN",
    "INSTITUCION", "INGENIERIA", "INGENIERÍA", "DERECHO", "MEDICINA",
    "SISTEMAS", "ADMINISTRACION", "ADMINISTRACIÓN", "CONTADURIA",
    "CONTADURÍA", "FACULTAD", "PROGRAMA", "ESTUDIANTE", "DOCENTE",
    "ADMINISTRATIVO", "WWW", "HTTP", "EDU", "COM", "CO",
    "POPULAR", "CESAR", "UNICESAR", "NACIONAL", "ANTIOQUIA",
    "ANDES", "BOGOTA", "BOGOTÁ", "MEDELLÍN", "MEDELLIN",
}

# Líneas que son claramente no-nombres
_SKIP_PATTERNS = [
    re.compile(r"www\.", re.IGNORECASE),
    re.compile(r"https?://", re.IGNORECASE),
    re.compile(r"\.(edu|com|co)\b", re.IGNORECASE),
    re.compile(r"\d{4}"),                          # líneas con años
    re.compile(r"^(CC|T\.?I\.?|TI)\s*\d", re.IGNORECASE),  # línea del número
]


class CarnetEstudiantilParser(BaseParser):

    @property
    def document_type(self) -> DocumentType:
        return DocumentType.CARNET_ESTUDIANTIL

    def parse(self, ocr_result: OcrResult) -> DocumentResult:
        if not ocr_result.is_usable:
            return self._build_failed_result(ocr_result.raw_text)

        text = ocr_result.raw_text
        fc = {}
        errors = []
        conf = ocr_result.confidence

        # 1. Número — tiene prefijo de tipo (CC / T.I)
        numero, doc_type_prefix = self._extract_numero(text)
        if numero:
            fc["numero"] = conf
        else:
            errors.append("No se pudo extraer número de documento")

        # 2. Nombre completo — 2 líneas sin etiquetas
        apellidos, nombres = self._extract_nombre_completo(text)

        if apellidos:
            fc["apellidos"] = conf
        else:
            errors.append("No se pudieron extraer apellidos")

        if nombres:
            fc["nombres"] = conf
        else:
            errors.append("No se pudieron extraer nombres")

        # 3. Programa académico (campo extra útil para contexto)
        programa = self._extract_programa(text)

        result = self._build_result(
            numero=numero,
            nombres=nombres,
            apellidos=apellidos,
            fecha_nacimiento=None,    # no disponible en carnet
            fecha_expiracion=None,    # no disponible en carnet
            sexo=None,                # no disponible en carnet
            lugar_nacimiento=None,
            field_confidence=fc,
            raw_text=text,
            errors=errors,
        )

        if programa:
            # Guardar programa en lugar_nacimiento temporalmente como campo extra
            # hasta que DocumentResult soporte campos adicionales
            logger.info("Programa académico detectado: %s", programa)

        logger.info("Carnet parseado: numero=%s nombres=%s estado=%s",
                    numero, nombres, result.status.value)
        return result

    # -----------------------------------------------------------------------
    # Extracción de número con prefijo de tipo
    # -----------------------------------------------------------------------

    def _extract_numero(self, text: str) -> tuple[Optional[str], Optional[str]]:
        """
        Extrae el número de documento y su tipo (CC / TI).

        Formatos soportados:
            "CC 1.007.819.137"
            "C.C. 1.007.819.137"
            "T.I 1.052.052.781"
            "TI 1.052.052.781"
            "T.I. 1.052.052.781"

        Returns:
            (numero_limpio, tipo_prefijo) — ej. ("1007819137", "CC")
            o (None, None) si no se encontró.
        """
        match = _DOC_TYPE_PATTERN.search(text)
        if match:
            prefix_raw = match.group(1)
            number_raw = match.group(2)
            clean = re.sub(r"[^\d]", "", number_raw)
            if 7 <= len(clean) <= 11:
                # Normalizar prefijo
                prefix = re.sub(r"\.", "", prefix_raw).upper()  # "T.I." → "TI"
                return clean, prefix

        return None, None

    # -----------------------------------------------------------------------
    # Extracción de nombre completo
    # -----------------------------------------------------------------------

    def _extract_nombre_completo(
        self, text: str
    ) -> tuple[Optional[str], Optional[str]]:
        """
        Extrae apellidos y nombres del carnet estudiantil.

        Estrategia:
            1. Encontrar la línea del número (ancla inferior).
            2. Encontrar la línea del encabezado institucional (ancla superior).
            3. Entre esas dos anclas, las líneas en mayúsculas sin keywords
               institucionales son el nombre: la primera es apellidos, la segunda nombres.

        Esto es robusto porque no depende de etiquetas — solo de la posición
        relativa al encabezado y al número.

        Returns:
            (apellidos, nombres) en mayúsculas, o (None, None).
        """
        lines = [l.strip() for l in text.split("\n") if l.strip()]

        # Encontrar índice del número (ancla inferior)
        numero_idx = self._find_numero_line(lines)

        # Encontrar índice del encabezado institucional (ancla superior)
        header_idx = self._find_header_line(lines)

        # Zona del nombre: entre header y número
        start = (header_idx + 1) if header_idx is not None else 0
        end = numero_idx if numero_idx is not None else len(lines)

        name_candidates = []
        for line in lines[start:end]:
            if self._is_name_candidate(line):
                name_candidates.append(line.upper())

        if len(name_candidates) >= 2:
            return name_candidates[0], name_candidates[1]
        elif len(name_candidates) == 1:
            # Solo hay una línea — intentar dividirla
            parts = name_candidates[0].split()
            if len(parts) >= 4:
                mid = len(parts) // 2
                return " ".join(parts[:mid]), " ".join(parts[mid:])
            elif len(parts) >= 2:
                return parts[0], " ".join(parts[1:])
            return name_candidates[0], None

        return None, None

    def _find_numero_line(self, lines: list[str]) -> Optional[int]:
        """Retorna el índice de la línea que contiene el número de documento."""
        for i, line in enumerate(lines):
            if _DOC_TYPE_PATTERN.search(line):
                return i
        return None

    def _find_header_line(self, lines: list[str]) -> Optional[int]:
        """
        Retorna el índice de la última línea del encabezado institucional.
        Busca el nombre de la universidad o la palabra ESTUDIANTE.
        """
        header_patterns = [
            re.compile(r"universidad", re.IGNORECASE),
            re.compile(r"politecnico", re.IGNORECASE),
            re.compile(r"estudiante", re.IGNORECASE),
            re.compile(r"unicesar", re.IGNORECASE),
        ]
        last_header = None
        for i, line in enumerate(lines):
            for pattern in header_patterns:
                if pattern.search(line):
                    last_header = i
                    break
        return last_header

    def _is_name_candidate(self, line: str) -> bool:
        """
        True si la línea puede ser apellidos o nombres.

        Criterios:
            - Solo letras y espacios (sin números, sin URLs)
            - No contiene keywords institucionales
            - Longitud razonable (2-40 caracteres)
            - No es una línea de skip conocida
        """
        line = line.strip()
        if not line or len(line) < 2 or len(line) > 50:
            return False

        # Skip patterns explícitos
        for pattern in _SKIP_PATTERNS:
            if pattern.search(line):
                return False

        # No debe tener dígitos
        if re.search(r"\d", line):
            return False

        # No debe ser una keyword institucional
        words = set(line.upper().split())
        if words & _INSTITUTIONAL_KEYWORDS:
            return False

        # Debe tener al menos 2 letras consecutivas
        if not re.search(r"[A-ZÁÉÍÓÚÜÑa-záéíóúüñ]{2,}", line):
            return False

        return True

    # -----------------------------------------------------------------------
    # Extracción de programa académico
    # -----------------------------------------------------------------------

    def _extract_programa(self, text: str) -> Optional[str]:
        """
        Extrae el programa o facultad del carnet.

        Aparece entre el nombre y el número, generalmente en mayúsculas
        y contiene keywords académicas.
        """
        lines = [l.strip() for l in text.split("\n") if l.strip()]
        numero_idx = self._find_numero_line(lines)
        end = numero_idx if numero_idx is not None else len(lines)

        academic_keywords = {
            "INGENIERIA", "INGENIERÍA", "DERECHO", "MEDICINA",
            "SISTEMAS", "ADMINISTRACION", "ADMINISTRACIÓN",
            "CONTADURIA", "CONTADURÍA", "FACULTAD", "PROGRAMA",
            "ENFERMERIA", "ENFERMERÍA", "ECONOMIA", "ECONOMÍA",
            "ARQUITECTURA", "PSICOLOGIA", "PSICOLOGÍA",
        }

        for line in lines[:end]:
            words = set(line.upper().split())
            if words & academic_keywords:
                return line.upper()

        return None

    def _build_failed_result(self, raw_text: str) -> DocumentResult:
        return DocumentResult(
            document_type=self.document_type,
            status=ExtractionStatus.FAILED,
            raw_ocr_text=raw_text,
            errors=["No se pudo extraer información útil del carnet estudiantil"],
        )