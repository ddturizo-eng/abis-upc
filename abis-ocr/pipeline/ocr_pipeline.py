"""
ocr_pipeline.py

Orquestador principal del sistema OCR.

Principio: Controller (GRASP) — único punto que conoce el flujo completo.
Principio: DIP  — depende de abstracciones (BaseCapture, BasePreprocessor,
                   BaseEngine, BaseParser), no de implementaciones concretas.
Principio: SRP  — solo orquesta, no hace OCR ni parsing directamente.
"""

import logging
from typing import Optional

from ocr.models.document_input import DocumentInput, DocumentHint
from ocr.models.document_result import DocumentResult, DocumentType, ExtractionStatus
from ocr.models.ocr_result import OcrResult

from ocr.capture.base_capture import BaseCapture
from ocr.preprocessing.base_preprocessor import BasePreprocessor
from ocr.engines.base_engine import BaseEngine
from ocr.parsers.base_parser import BaseParser
from ocr.parsers.parser_factory import ParserFactory
from ocr.classification.document_classifier import (
    DocumentClassifier,
    ClassificationResult,
)

logger = logging.getLogger(__name__)


# NO TOQUEN ESTE ARCHIVO SIN CONSULTAR ANTES CON EL EQUIPO DE DESARROLLO PRINCIPAL.
class OcrPipeline:
    """
    Pipeline que coordina todo el flujo OCR.

    Flujo completo:
        1. Recibir DocumentInput (front + opcional back)
        2. Preprocesar imagen frontal
        3. Extraer texto con OCR engine
        4. Seleccionar parser según document_type
        5. Parsear campos del documento
        6. Si requiere trasera: repetir 2-3 para back
        7. Combinar resultados (si aplica)
        8. Retornar DocumentResult final

    Nota: Usa inyección de dependencias en el constructor
    (Dependency Injection) para cumplir con DIP.
    """

    def __init__(
        self,
        capture: Optional[BaseCapture] = None,
        preprocessor: Optional[BasePreprocessor] = None,
        engine: Optional[BaseEngine] = None,
        parser_factory: Optional[type] = None,
        classifier: Optional[DocumentClassifier] = None,
    ):
        """
        Args:
            capture: Fuente de captura (cámara/archivo). Opcional si se usa DocumentInput.
            preprocessor: Preprocesador de imágenes.
            engine: Motor OCR a usar.
            parser_factory: Clase factory para obtener parsers (default: ParserFactory).
            classifier: Clasificador de documentos (default: DocumentClassifier).
        """
        self._capture = capture
        self._preprocessor = preprocessor
        self._engine = engine
        self._parser_factory = parser_factory or ParserFactory
        self._classifier = classifier or DocumentClassifier()

        self._validate_dependencies()

    # -------------------------------------------------------------------------
    # Interfaz pública
    # -------------------------------------------------------------------------

    def process(self, doc_input: DocumentInput) -> DocumentResult:
        """
        Procesa un documento completo y retorna campos estructurados.

        Args:
            doc_input: Entrada con imagen(es) y metadata del documento.

        Returns:
            DocumentResult con campos extraídos y normalizados.

        Raises:
            ValueError: Si faltan dependencias o el input es inválido.
        """
        self._validate_input(doc_input)

        logger.info(
            "Iniciando procesamiento: tipo=%s, caras=%s",
            doc_input.document_hint.value,
            "frontal+trasera" if doc_input.has_back else "solo frontal",
        )

        # Determinar tipo de documento
        classification = self._classify_document(doc_input)
        doc_type = self._resolve_document_type(doc_input)

        # Procesar cara frontal
        logger.info("Procesando cara frontal...")
        front_result = self._process_side(
            doc_input.front, doc_type, side_name="frontal"
        )

        # Si requiere trasera, procesar cara posterior
        back_result = None
        if doc_input.requires_back and doc_input.has_back:
            logger.info("Procesando cara trasera...")
            back_result = self._process_side(
                doc_input.back, doc_type, side_name="trasera"
            )

        # Combinar resultados si hay cara trasera
        final_result = self._combine_results(front_result, back_result)

        # Asignar confianza de clasificación
        if classification and classification.confidence > 0:
            final_result.classification_confidence = classification.confidence

        # Generar campos individuales (primer_nombre, segundo_nombre, etc.)
        final_result.split_nombres_apellidos()

        logger.info(
            "Procesamiento completado: estado=%s, confianza=%.2f",
            final_result.status.value,
            final_result.overall_confidence,
        )

        return final_result

    def process_image_bytes(
        self,
        image_bytes: bytes,
        doc_type: DocumentType,
        side: str = "front",
    ) -> DocumentResult:
        """
        Procesa una sola imagen desde bytes.

        Útil para casos donde ya tienes los bytes de la imagen
        y no necesitas el flujo completo de DocumentInput.

        Args:
            image_bytes: Bytes de la imagen JPEG/PNG.
            doc_type: Tipo de documento.
            side: "front" o "back".

        Returns:
            DocumentResult con campos extraídos.
        """
        if not self._preprocessor or not self._engine:
            raise ValueError("Pipeline requiere preprocessor y engine configurados.")

        # Preprocesar
        processed_image = self._preprocessor.process(image_bytes)

        # OCR
        ocr_result = self._engine.extract(processed_image)

        if not ocr_result.is_usable:
            return DocumentResult(
                document_type=doc_type,
                status=ExtractionStatus.FAILED,
                errors=[f"OCR falló: {ocr_result.error_msg or 'sin texto útil'}"],
            )

        # Parsear
        parser = self._parser_factory.get_parser(doc_type)
        if not parser:
            return DocumentResult(
                document_type=doc_type,
                status=ExtractionStatus.FAILED,
                raw_ocr_text=ocr_result.raw_text,
                errors=[f"No hay parser registrado para: {doc_type.value}"],
            )

        return parser.parse(ocr_result)

    # -------------------------------------------------------------------------
    # Internos
    # -------------------------------------------------------------------------

    def _process_side(
        self,
        captured_image,
        doc_type: DocumentType,
        side_name: str,
    ) -> DocumentResult:
        """
        Procesa una sola cara del documento.

        Args:
            captured_image: CapturedImage con los bytes.
            doc_type: Tipo de documento.
            side_name: Nombre descriptivo ("frontal" / "trasera").

        Returns:
            DocumentResult de esa cara.
        """
        logger.debug("Procesando cara %s: %s", side_name, captured_image)

        # Decodificar imagen a BGR (PaddleOCR rinde mejor en BGR que en grayscale)
        if not captured_image.data:
            raise ValueError("Imagen sin datos.")

        import numpy as np
        import cv2

        arr = np.frombuffer(captured_image.data, dtype=np.uint8)
        image_bgr = cv2.imdecode(arr, cv2.IMREAD_COLOR)

        if image_bgr is None:
            raise ValueError("No se pudo decodificar la imagen.")

        # Redimensionar si es necesario (heredar lógica del preprocessor)
        if self._preprocessor:
            processed = self._preprocessor.process_from_array(image_bgr)
        else:
            processed = image_bgr

        # Extraer texto con OCR — pasar como BGR, PaddleOCR pierde espacios en grayscale
        if not self._engine:
            raise ValueError("Pipeline requiere engine configurado.")

        ocr_result = self._engine.extract(image_bgr)

        if not ocr_result.is_usable:
            logger.warning(
                "OCR no usable en cara %s: %s",
                side_name,
                ocr_result.error_msg or "sin texto",
            )
            return DocumentResult(
                document_type=doc_type,
                status=ExtractionStatus.FAILED,
                raw_ocr_text=ocr_result.raw_text,
                errors=[f"OCR falló en cara {side_name}"],
            )

        # Obtener parser y parsear
        parser = self._parser_factory.get_parser(doc_type)
        if not parser:
            raise ValueError(f"No hay parser registrado para: {doc_type.value}")

        result = parser.parse(ocr_result)

        logger.debug("Cara %s procesada: estado=%s", side_name, result.status.value)

        return result

    def _resolve_document_type(self, doc_input: DocumentInput) -> DocumentType:
        """
        Determina el tipo de documento a procesar.

        Flujo:
            1. Clasifica automáticamente la imagen
            2. Si hint es AUTO → usar clasificación automática
            3. Si hint es manual → validar contra clasificador
            4. Si discrepancia con alta confianza (>=0.80) → clasificador gana
        """
        hint_to_type = {
            DocumentHint.CEDULA_DIGITAL: DocumentType.CEDULA_DIGITAL,
            DocumentHint.CEDULA_AMARILLA: DocumentType.CEDULA_AMARILLA,
            DocumentHint.TARJETA_IDENTIDAD: DocumentType.TARJETA_IDENTIDAD,
            DocumentHint.CARNET_ESTUDIANTIL: DocumentType.CARNET_ESTUDIANTIL,
        }

        classification = self._classify_document(doc_input)

        if doc_input.document_hint == DocumentHint.AUTO:
            if classification and classification.document_type != DocumentType.UNKNOWN:
                logger.info(
                    "Clasificación automática: %s (confianza=%.2f)",
                    classification.document_type.value,
                    classification.confidence,
                )
                return classification.document_type
            logger.warning(
                "Clasificación automática no pudo determinar el tipo. "
                "Usando CEDULA_DIGITAL por defecto."
            )
            return DocumentType.CEDULA_DIGITAL

        manual_type = hint_to_type.get(
            doc_input.document_hint, DocumentType.CEDULA_DIGITAL
        )

        if (
            classification
            and classification.document_type != DocumentType.UNKNOWN
            and classification.document_type != manual_type
            and classification.confidence >= 0.80
        ):
            logger.warning(
                "DISCREPANCIA operador=%s clasificador=%s (%.2f) → usando clasificador",
                manual_type.value,
                classification.document_type.value,
                classification.confidence,
            )
            return classification.document_type

        return manual_type

    def _classify_document(
        self, doc_input: DocumentInput
    ) -> Optional[ClassificationResult]:
        """Ejecuta el clasificador sobre la imagen frontal."""
        if not doc_input.front or not doc_input.front.data:
            return None
        try:
            import numpy as np
            import cv2

            arr = np.frombuffer(doc_input.front.data, dtype=np.uint8)
            image_bgr = cv2.imdecode(arr, cv2.IMREAD_COLOR)
            if image_bgr is None:
                logger.warning("No se pudo decodificar imagen para clasificación.")
                return None
            return self._classifier.classify_from_array(image_bgr)
        except Exception as e:
            logger.error("Error en clasificación: %s", e, exc_info=True)
            return None

    def _combine_results(
        self,
        front: DocumentResult,
        back: Optional[DocumentResult],
    ) -> DocumentResult:
        """
        Combina resultados de frontal y trasera.

        Los campos de la trasera (ej. fecha_nacimiento, sexo)
        complementan a los de la frontal.

        Args:
            front: Resultado de la cara frontal.
            back: Resultado de la cara trasera (opcional).

        Returns:
            DocumentResult combinado.
        """
        if back is None:
            return front

        logger.debug("Combinando resultados frontal + trasera...")

        # Usar frontal como base, complementar con trasera
        combined = DocumentResult(
            document_type=front.document_type,
            status=ExtractionStatus.PARTIAL,  # Se re-evaluará
            numero=front.numero,
            nombres=front.nombres,
            apellidos=front.apellidos,
            fecha_nacimiento=front.fecha_nacimiento or back.fecha_nacimiento,
            fecha_expiracion=front.fecha_expiracion or back.fecha_expiracion,
            sexo=front.sexo or back.sexo,
            lugar_nacimiento=front.lugar_nacimiento or back.lugar_nacimiento,
            field_confidence={**front.field_confidence, **back.field_confidence},
            raw_ocr_text=front.raw_ocr_text or back.raw_ocr_text,
            errors=front.errors + back.errors,
        )

        logger.debug(
            "Combinado completado: fecha_nac=%s, sexo=%s",
            combined.fecha_nacimiento,
            combined.sexo,
        )

        return combined

    def _validate_dependencies(self) -> None:
        """
        Valida que las dependencias críticas estén configuradas.

        Nota: capture es opcional (se puede usar DocumentInput directamente).
        """
        if not self._preprocessor:
            logger.warning(
                "Pipeline sin preprocessor. "
                "Configure uno con: pipeline.preprocessor = ImagePreprocessor()"
            )

        if not self._engine:
            logger.warning(
                "Pipeline sin engine. "
                "Configure uno con: pipeline.engine = PaddleEngine()"
            )

    def _validate_input(self, doc_input: DocumentInput) -> None:
        """
        Valida el input antes de procesar.

        Args:
            doc_input: Entrada a validar.

        Raises:
            ValueError: Si el input es inválido.
        """
        if not doc_input.front or not doc_input.front.data:
            raise ValueError("DocumentInput debe tener una cara frontal válida.")

        if doc_input.requires_back and not doc_input.has_back:
            logger.warning(
                "El documento requiere cara trasera pero no se proporcionó. "
                "Algunos campos podrían faltar."
            )
