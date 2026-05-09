"""
document_classifier.py

Clasificador de documentos de identidad colombianos usando reglas visuales OpenCV.

Estrategia: dos señales combinadas
    1. Color dominante en HSV  → distingue amarilla / azul / verde/beige (TI)
    2. Texto ancla vía OCR rápido (Tesseract --psm 6) → confirma el tipo

Principio: SRP  — solo clasifica, no extrae campos ni preprocesa para OCR.
Principio: OCP  — agregar un nuevo tipo = agregar una regla en _RULES, no tocar lógica.

IMPORTANTE: Recibe imagen BGR original (con color). No pasar imagen ya en grayscale.
"""

import logging
from dataclasses import dataclass
from typing import Optional

import cv2
import numpy as np

from ocr.models.document_result import DocumentType

logger = logging.getLogger(__name__)


# ---------------------------------------------------------------------------
# Rangos HSV calibrados para documentos colombianos
# Ajustar con tus imágenes de muestra si los resultados no son precisos.
# Herramienta: scripts/calibrate_hsv.py (ver al final de este archivo)
# ---------------------------------------------------------------------------
_HSV_RANGES = {
    # Cédula amarilla: fondo dorado/amarillo, acepta versiones envejecidas con baja saturación
    DocumentType.CEDULA_AMARILLA: [
        (np.array([10, 15, 100]), np.array([42, 255, 255])),
    ],
    # Cédula digital 2023+: azul oscuro / azul medio
    DocumentType.CEDULA_DIGITAL: [
        (np.array([90, 50, 50]), np.array([130, 255, 255])),
    ],
    # Tarjeta de identidad clásica: verde institucional + beige
    DocumentType.TARJETA_IDENTIDAD: [
        (np.array([35, 30, 60]), np.array([85, 200, 220])),  # verde
        (np.array([15, 10, 180]), np.array([30, 60, 255])),  # beige/crema
    ],
    # Carnet UPC: azul institucional UPC (similar al digital pero con logo)
    DocumentType.CARNET_ESTUDIANTIL: [
        (np.array([100, 60, 80]), np.array([125, 255, 220])),
    ],
}

# Texto ancla por tipo de documento (subcadenas que aparecen en el documento)
# Se usa OCR rápido sobre región superior del documento para confirmar el tipo.
_TEXT_ANCHORS: dict[DocumentType, list[str]] = {
    DocumentType.CEDULA_DIGITAL: ["NUIP", "nuip", "Republica", "REPÚBLICA"],
    DocumentType.CEDULA_AMARILLA: ["NÚMERO", "NUMERO", "APELLIDOS", "NOMBRES"],
    DocumentType.TARJETA_IDENTIDAD: ["TARJETA DE IDENTIDAD", "IDENTIDAD"],
    DocumentType.CARNET_ESTUDIANTIL: ["UPC", "POLITECNICO", "POLITÉCNICO"],
}

# Umbral mínimo de píxeles del color dominante para considerar match (0.0 - 1.0)
_COLOR_THRESHOLD = 0.08  # 8% del área de la imagen


@dataclass
class ClassificationResult:
    """Resultado de la clasificación de un documento."""

    document_type: DocumentType
    confidence: float  # 0.0 – 1.0
    color_score: float  # Proporción del color dominante detectado
    anchor_found: bool  # Si se encontró texto ancla confirmatorio
    anchor_text: str = ""  # Texto ancla encontrado (para diagnóstico)
    method: str = ""  # "color+anchor" | "color_only" | "anchor_only" | "fallback"


class DocumentClassifier:
    """
    Clasifica documentos de identidad colombianos a partir de su imagen BGR.

    Uso:
        classifier = DocumentClassifier()
        result = classifier.classify(image_bytes)
        print(result.document_type)   # DocumentType.CEDULA_DIGITAL
        print(result.confidence)      # 0.87
    """

    def __init__(self, use_ocr_anchor: bool = True):
        """
        Args:
            use_ocr_anchor: Si True, usa Tesseract para confirmar el tipo
                            con texto ancla. Más preciso pero más lento (~0.3s).
                            Si False, solo usa color (más rápido, menos preciso).
        """
        self._use_ocr_anchor = use_ocr_anchor
        self._tesseract_available = self._check_tesseract()

        if use_ocr_anchor and not self._tesseract_available:
            logger.warning(
                "Tesseract no disponible. Clasificador usará solo análisis de color."
            )
            self._use_ocr_anchor = False

    # -----------------------------------------------------------------------
    # Interfaz pública
    # -----------------------------------------------------------------------

    def classify(self, image_bytes: bytes) -> ClassificationResult:
        """
        Clasifica el tipo de documento desde bytes de imagen.

        Args:
            image_bytes: Bytes de imagen JPEG/PNG. Debe ser imagen original BGR,
                         NO procesada a grayscale.

        Returns:
            ClassificationResult con el tipo detectado y nivel de confianza.
        """
        image = self._decode(image_bytes)
        if image is None:
            logger.error("No se pudo decodificar la imagen para clasificación.")
            return self._fallback_result()

        return self.classify_from_array(image)

    def classify_from_array(self, image: np.ndarray) -> ClassificationResult:
        """
        Clasifica desde numpy array BGR (útil para integración con camera_capture).

        Args:
            image: numpy array BGR. Debe tener 3 canales (color).

        Returns:
            ClassificationResult con el tipo detectado.
        """
        if image is None or image.ndim < 3:
            logger.error(
                "La imagen no tiene canales de color (¿ya está en grayscale?)."
            )
            return self._fallback_result()

        # 1. Análisis de color dominante
        color_scores = self._analyze_color(image)
        logger.debug(
            "Color scores: %s", {k.value: round(v, 3) for k, v in color_scores.items()}
        )

        # 2. Candidato por color
        color_winner, color_score = self._pick_winner(color_scores)

        # 3. Confirmación por texto ancla (si está habilitado)
        anchor_winner = None
        anchor_found = False

        if self._use_ocr_anchor:
            anchor_winner, anchor_found, anchor_text = self._check_anchors(
                image, color_winner
            )
        else:
            anchor_text = ""

        # 4. Decisión final
        return self._decide(
            color_winner, color_score, anchor_winner, anchor_found, anchor_text
        )

    # -----------------------------------------------------------------------
    # Análisis de color
    # -----------------------------------------------------------------------

    def _analyze_color(self, image: np.ndarray) -> dict[DocumentType, float]:
        """
        Calcula la proporción de píxeles que coinciden con el color de cada tipo.

        Returns:
            Dict {DocumentType: proporción 0.0–1.0}
        """
        hsv = cv2.cvtColor(image, cv2.COLOR_BGR2HSV)
        total_pixels = hsv.shape[0] * hsv.shape[1]
        scores: dict[DocumentType, float] = {}

        for doc_type, ranges in _HSV_RANGES.items():
            mask = np.zeros(hsv.shape[:2], dtype=np.uint8)
            for lower, upper in ranges:
                mask |= cv2.inRange(hsv, lower, upper)

            # Suavizar para reducir ruido
            kernel = cv2.getStructuringElement(cv2.MORPH_ELLIPSE, (5, 5))
            mask = cv2.morphologyEx(mask, cv2.MORPH_OPEN, kernel)

            pixel_count = cv2.countNonZero(mask)
            scores[doc_type] = pixel_count / total_pixels

        return scores

    def _pick_winner(
        self, scores: dict[DocumentType, float]
    ) -> tuple[Optional[DocumentType], float]:
        """Retorna el tipo con mayor score, o None si ninguno supera el umbral."""
        if not scores:
            return None, 0.0

        winner = max(scores, key=lambda k: scores[k])
        score = scores[winner]

        if score < _COLOR_THRESHOLD:
            logger.debug(
                "Color score %.3f < umbral %.3f — sin match de color",
                score,
                _COLOR_THRESHOLD,
            )
            return None, score

        return winner, score

    # -----------------------------------------------------------------------
    # Confirmación por texto ancla
    # -----------------------------------------------------------------------

    def _check_anchors(
        self, image: np.ndarray, color_candidate: Optional[DocumentType]
    ) -> tuple[Optional[DocumentType], bool, str]:
        """
        Busca texto ancla en la zona superior del documento (primer 30%).

        Args:
            image: Imagen BGR completa.
            color_candidate: Tipo candidato por color (se prioriza en la búsqueda).

        Returns:
            (tipo confirmado o None, se encontró ancla)
        """
        # Recortar zona superior donde suelen estar los identificadores
        h = image.shape[0]
        roi = image[: int(h * 0.30), :]

        # OCR rápido: grayscale sin CLAHE (velocidad > precisión aquí)
        gray = cv2.cvtColor(roi, cv2.COLOR_BGR2GRAY)

        try:
            import pytesseract

            text = pytesseract.image_to_string(
                gray,
                config="--psm 6 --oem 1 -l spa",
                timeout=2,
            ).upper()
        except Exception as e:
            logger.warning("OCR ancla falló: %s", e)
            return None, False

        logger.debug("Texto ancla detectado: %r", text[:200])

        # Priorizar el candidato por color
        candidates = list(_TEXT_ANCHORS.keys())
        if color_candidate and color_candidate in candidates:
            candidates.remove(color_candidate)
            candidates.insert(0, color_candidate)

        for doc_type in candidates:
            anchors = _TEXT_ANCHORS[doc_type]
            for anchor in anchors:
                if anchor.upper() in text:
                    logger.debug("Ancla '%s' encontrada → %s", anchor, doc_type.value)
                    return doc_type, True, anchor

        return None, False, ""

    # -----------------------------------------------------------------------
    # Decisión final
    # -----------------------------------------------------------------------

    def _decide(
        self,
        color_winner: Optional[DocumentType],
        color_score: float,
        anchor_winner: Optional[DocumentType],
        anchor_found: bool,
        anchor_text: str = "",
    ) -> ClassificationResult:
        """
        Combina señales de color y texto ancla para la decisión final.

        Reglas de decisión (en orden de prioridad):
            1. Color + ancla coinciden → alta confianza
            2. Ancla sola (color no resolvió) → confianza media
            3. Color solo (ancla no resolvió) → confianza media-baja
            4. Ninguno → UNKNOWN
        """
        # Caso 1: ambas señales coinciden
        if color_winner and anchor_found and color_winner == anchor_winner:
            return ClassificationResult(
                document_type=color_winner,
                confidence=min(0.95, 0.60 + color_score),
                color_score=color_score,
                anchor_found=True,
                anchor_text=anchor_text,
                method="color+anchor",
            )

        # Caso 2: ancla confirma pero color difiere (ancla gana — más específica)
        if anchor_found and anchor_winner:
            confidence = 0.75 if not color_winner else 0.65
            return ClassificationResult(
                document_type=anchor_winner,
                confidence=confidence,
                color_score=color_score,
                anchor_found=True,
                anchor_text=anchor_text,
                method="anchor_only",
            )

        # Caso 3: solo color resolvió
        if color_winner:
            return ClassificationResult(
                document_type=color_winner,
                confidence=min(0.70, 0.40 + color_score),
                color_score=color_score,
                anchor_found=False,
                anchor_text="",
                method="color_only",
            )

        # Caso 4: sin señales claras
        logger.warning("No se pudo clasificar el documento — retornando UNKNOWN")
        return self._fallback_result()

    # -----------------------------------------------------------------------
    # Utilidades
    # -----------------------------------------------------------------------

    def _decode(self, image_bytes: bytes) -> Optional[np.ndarray]:
        """Decodifica bytes a numpy array BGR."""
        try:
            arr = np.frombuffer(image_bytes, dtype=np.uint8)
            img = cv2.imdecode(arr, cv2.IMREAD_COLOR)
            return img
        except Exception as e:
            logger.error("Error decodificando imagen: %s", e)
            return None

    def _fallback_result(self) -> ClassificationResult:
        return ClassificationResult(
            document_type=DocumentType.UNKNOWN,
            confidence=0.0,
            color_score=0.0,
            anchor_found=False,
            anchor_text="",
            method="fallback",
        )

    @staticmethod
    def _check_tesseract() -> bool:
        """Verifica si Tesseract está disponible en el sistema."""
        try:
            import pytesseract

            pytesseract.get_tesseract_version()
            return True
        except Exception:
            return False


# ---------------------------------------------------------------------------
# Script de calibración HSV (ejecutar directamente para ajustar rangos)
# Uso: python -m ocr.classification.document_classifier <ruta_imagen>
# ---------------------------------------------------------------------------
if __name__ == "__main__":
    import sys

    if len(sys.argv) < 2:
        print("Uso: python document_classifier.py <ruta_imagen>")
        sys.exit(1)

    img_path = sys.argv[1]
    img = cv2.imread(img_path)
    if img is None:
        print(f"No se pudo cargar: {img_path}")
        sys.exit(1)

    hsv = cv2.cvtColor(img, cv2.COLOR_BGR2HSV)
    h, w = img.shape[:2]

    # Muestrear centro de la imagen
    cx, cy = w // 2, h // 2
    sample = hsv[cy - 20 : cy + 20, cx - 20 : cx + 20]
    mean_hsv = sample.mean(axis=(0, 1))

    print(f"Imagen: {img_path} ({w}x{h})")
    print(
        f"HSV promedio en el centro: H={mean_hsv[0]:.1f}, S={mean_hsv[1]:.1f}, V={mean_hsv[2]:.1f}"
    )
    print(
        "\nCopia estos valores como referencia para ajustar _HSV_RANGES en document_classifier.py"
    )

    clf = DocumentClassifier(use_ocr_anchor=False)
    result = clf.classify_from_array(img)
    print(
        f"\nClasificación: {result.document_type.value} (confianza: {result.confidence:.2f})"
    )
    print(f"Color score: {result.color_score:.3f}")
