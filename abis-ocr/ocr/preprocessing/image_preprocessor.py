"""
image_preprocessor.py

Implementación de preprocesamiento de imágenes para OCR.

Responsabilidad única: recibir bytes de imagen y devolver un numpy array
optimizado para motores OCR (PaddleOCR / Tesseract).

Principio: SRP  — solo preprocesa, no sabe nada de OCR ni parsers.
Principio: DIP  — implementa BasePreprocessor, el pipeline no sabe detalles de OpenCV.
"""

import logging
from typing import Optional

import cv2
import numpy as np

from .base_preprocessor import BasePreprocessor, PreprocessorError

logger = logging.getLogger(__name__)

# Constantes de preprocesamiento
DEFAULT_MAX_WIDTH = 1920  # Ancho máximo para velocidad
DEFAULT_CLAHE_CLIP = 2.0  # Límite de contraste CLAHE
DEFAULT_CLAHE_GRID = (8, 8)  # Tamaño de grid para CLAHE


class ImagePreprocessor(BasePreprocessor):
    """
    Preprocesador de imágenes optimizado para documentos de identidad.

    Pasos de procesamiento (equilibrio velocidad/precisión):
        1. Decodificar bytes → numpy array (BGR)
        2. Redimensionar si excede ancho máximo (mantiene aspecto)
        3. Convertir a escala de grises (mejora OCR general)
        4. Aplicar CLAHE (mejora contraste local para cédulas amarillas)

    Nota: Se omite denoise (Gaussian blur) y binarización (Otsu)
    porque PaddleOCR prefiere escala de grises y ambos pasos son lentos.
    """

    def __init__(
        self,
        max_width: int = DEFAULT_MAX_WIDTH,
        apply_clahe: bool = True,
        clahe_clip: float = DEFAULT_CLAHE_CLIP,
        clahe_grid: tuple[int, int] = DEFAULT_CLAHE_GRID,
    ):
        """
        Args:
            max_width   : Ancho máximo de la imagen. Si es mayor, se escala.
                          Reduce tiempo de inferencia sin perder precisión.
            apply_clahe : True para aplicar ecualización de contraste adaptativo.
                          Útil para cédulas amarillas con bajo contraste.
            clahe_clip  : Límite de corte para CLAHE.
            clahe_grid  : Tamaño de grid para CLAHE.
        """
        self._max_width = max_width
        self._apply_clahe = apply_clahe
        self._clahe = cv2.createCLAHE(clipLimit=clahe_clip, tileGridSize=clahe_grid)

    # -------------------------------------------------------------------------
    # Interfaz pública (contrato de BasePreprocessor)
    # -------------------------------------------------------------------------

    def process(self, image_bytes: bytes) -> np.ndarray:
        """
        Procesa una imagen desde bytes.

        Args:
            image_bytes: Bytes de la imagen (JPEG/PNG/BMP).

        Returns:
            numpy array de la imagen preprocesada (grayscale).

        Raises:
            PreprocessorError: Si la imagen no se puede decodificar.
        """
        if not image_bytes:
            raise PreprocessorError("Los bytes de la imagen están vacíos.")

        # 1. Decodificar bytes → numpy array (BGR)
        image = cv2.imdecode(
            np.frombuffer(image_bytes, dtype=np.uint8),
            cv2.IMREAD_COLOR,
        )

        if image is None:
            raise PreprocessorError(
                "No se pudo decodificar la imagen. Verifique el formato (JPEG/PNG/BMP)."
            )

        logger.debug(
            "Imagen decodificada: shape=%s, dtype=%s", image.shape, image.dtype
        )

        return self.process_from_array(image)

    def process_from_array(self, image: np.ndarray) -> np.ndarray:
        """
        Procesa una imagen ya cargada como numpy array.

        Args:
            image: numpy array BGR de la imagen original.

        Returns:
            numpy array de la imagen preprocesada (grayscale).

        Raises:
            PreprocessorError: Si el array es inválido.
        """
        if image is None or image.size == 0:
            raise PreprocessorError("El array de imagen es inválido o está vacío.")

        # 2. Redimensionar si excede el ancho máximo
        processed = self._resize_if_needed(image)

        # 3. Convertir a escala de grises
        processed = cv2.cvtColor(processed, cv2.COLOR_BGR2GRAY)

        # 4. Aplicar CLAHE para mejorar contraste local
        if self._apply_clahe:
            processed = self._clahe.apply(processed)
            logger.debug("CLAHE aplicado a la imagen")

        logger.debug(
            "Imagen preprocesada: shape=%s, dtype=%s", processed.shape, processed.dtype
        )

        return processed

    @staticmethod
    def crop_document(image_bytes: bytes) -> bytes:
        """
        Detecta y recorta el documento de identidad colombiano de la imagen.

        Usa 4 estrategias en cascada, pasando a la siguiente si la anterior falla:
            1. Canny mejorado con morfología agresiva + upscaling previo
            2. Segmentación HSV (documentos colombianos son blancos/azules/amarillos)
            3. Detección de rectángulo por líneas de Hough
            4. Fallback inteligente: recorte central + upscaling para OCR

        En todos los casos, el resultado es escalado a mínimo 1200px de ancho
        para garantizar que PaddleOCR pueda leer el texto correctamente.

        Args:
            image_bytes: Bytes JPEG/PNG de la imagen capturada.

        Returns:
            Bytes JPEG de la imagen recortada, enderezada y escalada para OCR.
        """

        # ── Decodificar ──────────────────────────────────────────────────────
        arr = np.frombuffer(image_bytes, dtype=np.uint8)
        image = cv2.imdecode(arr, cv2.IMREAD_COLOR)
        if image is None:
            logger.warning("crop_document: no se pudo decodificar la imagen")
            return image_bytes

        original = image.copy()
        h, w = image.shape[:2]
        image_area = h * w

        logger.debug("crop_document: imagen recibida %dx%d", w, h)

        # ── Upscaling previo si la imagen es pequeña ─────────────────────────
        scale_factor = 1.0
        if w < 800:
            scale_factor = 2.0
            image = cv2.resize(image, (w * 2, h * 2), interpolation=cv2.INTER_CUBIC)
            original = image.copy()
            h, w = image.shape[:2]
            image_area = h * w
            logger.debug("crop_document: upscaling x2 aplicado → %dx%d", w, h)

        # ════════════════════════════════════════════════════════════════════
        # ESTRATEGIA 1: Canny mejorado con morfología agresiva
        # ════════════════════════════════════════════════════════════════════
        def strategy_canny(img):
            gray = cv2.cvtColor(img, cv2.COLOR_BGR2GRAY)

            clahe = cv2.createCLAHE(clipLimit=3.0, tileGridSize=(8, 8))
            gray = clahe.apply(gray)

            blurred = cv2.bilateralFilter(gray, 9, 75, 75)

            otsu_thresh, _ = cv2.threshold(
                blurred, 0, 255, cv2.THRESH_BINARY + cv2.THRESH_OTSU
            )
            lower = int(otsu_thresh * 0.5)
            upper = int(otsu_thresh)
            edges = cv2.Canny(blurred, lower, upper)

            kernel_close = cv2.getStructuringElement(cv2.MORPH_RECT, (7, 7))
            edges = cv2.morphologyEx(edges, cv2.MORPH_CLOSE, kernel_close)
            kernel_dilate = cv2.getStructuringElement(cv2.MORPH_RECT, (5, 5))
            edges = cv2.dilate(edges, kernel_dilate, iterations=2)

            contours, _ = cv2.findContours(
                edges, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE
            )
            if not contours:
                return None

            for contour in sorted(contours, key=cv2.contourArea, reverse=True)[:8]:
                area = cv2.contourArea(contour)
                if not (0.08 * image_area < area < 0.97 * image_area):
                    continue
                peri = cv2.arcLength(contour, True)
                approx = cv2.approxPolyDP(contour, 0.04 * peri, True)
                if len(approx) == 4:
                    return approx
                if 4 < len(approx) <= 6:
                    approx2 = cv2.approxPolyDP(contour, 0.06 * peri, True)
                    if len(approx2) == 4:
                        return approx2

            return None

        # ════════════════════════════════════════════════════════════════════
        # ESTRATEGIA 2: Segmentación por color HSV
        # ════════════════════════════════════════════════════════════════════
        def strategy_hsv(img):
            hsv = cv2.cvtColor(img, cv2.COLOR_BGR2HSV)
            h_img, w_img = img.shape[:2]

            mask_white = cv2.inRange(
                hsv, np.array([0, 0, 180]), np.array([180, 50, 255])
            )
            mask_blue = cv2.inRange(
                hsv, np.array([90, 40, 40]), np.array([130, 255, 255])
            )
            mask_yellow = cv2.inRange(
                hsv, np.array([10, 30, 100]), np.array([40, 255, 255])
            )

            mask = cv2.bitwise_or(mask_white, mask_blue)
            mask = cv2.bitwise_or(mask, mask_yellow)

            kernel = cv2.getStructuringElement(cv2.MORPH_RECT, (15, 15))
            mask = cv2.morphologyEx(mask, cv2.MORPH_CLOSE, kernel)
            mask = cv2.morphologyEx(mask, cv2.MORPH_OPEN, kernel)

            contours, _ = cv2.findContours(
                mask, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE
            )
            if not contours:
                return None

            img_area = h_img * w_img
            for contour in sorted(contours, key=cv2.contourArea, reverse=True)[:5]:
                area = cv2.contourArea(contour)
                if not (0.08 * img_area < area < 0.97 * img_area):
                    continue
                peri = cv2.arcLength(contour, True)
                approx = cv2.approxPolyDP(contour, 0.05 * peri, True)
                if len(approx) == 4:
                    return approx
                if area > 0.12 * img_area:
                    x, y, bw, bh = cv2.boundingRect(contour)
                    return np.array(
                        [[[x, y]], [[x + bw, y]], [[x + bw, y + bh]], [[x, y + bh]]],
                        dtype=np.int32,
                    )

            return None

        # ════════════════════════════════════════════════════════════════════
        # ESTRATEGIA 3: Detección por líneas de Hough
        # ════════════════════════════════════════════════════════════════════
        def strategy_hough(img):
            gray = cv2.cvtColor(img, cv2.COLOR_BGR2GRAY)
            clahe = cv2.createCLAHE(clipLimit=2.0, tileGridSize=(8, 8))
            gray = clahe.apply(gray)
            edges = cv2.Canny(gray, 50, 150, apertureSize=3)

            lines = cv2.HoughLinesP(
                edges,
                rho=1,
                theta=np.pi / 180,
                threshold=80,
                minLineLength=min(w, h) * 0.25,
                maxLineGap=30,
            )

            if lines is None or len(lines) < 4:
                return None

            horizontals = []
            verticals = []
            for line in lines:
                x1, y1, x2, y2 = line[0]
                angle = abs(np.degrees(np.arctan2(y2 - y1, x2 - x1)))
                if angle < 20 or angle > 160:
                    horizontals.append(line[0])
                elif 70 < angle < 110:
                    verticals.append(line[0])

            if len(horizontals) < 2 or len(verticals) < 2:
                return None

            h_sorted = sorted(horizontals, key=lambda l: (l[1] + l[3]) / 2)
            top_line = h_sorted[0]
            bottom_line = h_sorted[-1]

            v_sorted = sorted(verticals, key=lambda l: (l[0] + l[2]) / 2)
            left_line = v_sorted[0]
            right_line = v_sorted[-1]

            def line_intersection(l1, l2):
                x1, y1, x2, y2 = l1
                x3, y3, x4, y4 = l2
                denom = (x1 - x2) * (y3 - y4) - (y1 - y2) * (x3 - x4)
                if abs(denom) < 1e-10:
                    return None
                t = ((x1 - x3) * (y3 - y4) - (y1 - y3) * (x3 - x4)) / denom
                x = x1 + t * (x2 - x1)
                y = y1 + t * (y2 - y1)
                return (int(x), int(y))

            tl = line_intersection(top_line, left_line)
            tr = line_intersection(top_line, right_line)
            br = line_intersection(bottom_line, right_line)
            bl = line_intersection(bottom_line, left_line)

            if None in [tl, tr, br, bl]:
                return None

            rect_w = max(abs(tr[0] - tl[0]), abs(br[0] - bl[0]))
            rect_h = max(abs(bl[1] - tl[1]), abs(br[1] - tr[1]))
            rect_area = rect_w * rect_h

            if not (0.08 * image_area < rect_area < 0.97 * image_area):
                return None

            return np.array(
                [
                    [[tl[0], tl[1]]],
                    [[tr[0], tr[1]]],
                    [[br[0], br[1]]],
                    [[bl[0], bl[1]]],
                ],
                dtype=np.int32,
            )

        # ════════════════════════════════════════════════════════════════════
        # MOTOR DE TRANSFORMACIÓN DE PERSPECTIVA
        # ════════════════════════════════════════════════════════════════════
        def apply_perspective_transform(img, contour):
            pts = contour.reshape(4, 2).astype(np.float32)

            rect = np.zeros((4, 2), dtype=np.float32)
            s = pts.sum(axis=1)
            diff = np.diff(pts, axis=1)
            rect[0] = pts[np.argmin(s)]
            rect[2] = pts[np.argmax(s)]
            rect[1] = pts[np.argmin(diff)]
            rect[3] = pts[np.argmax(diff)]

            wa = np.linalg.norm(rect[2] - rect[3])
            wb = np.linalg.norm(rect[1] - rect[0])
            ha = np.linalg.norm(rect[1] - rect[2])
            hb = np.linalg.norm(rect[0] - rect[3])

            out_w = int(max(wa, wb))
            out_h = int(max(ha, hb))

            if out_h > 0:
                actual_ratio = out_w / out_h
                if actual_ratio < 1.0:
                    out_w, out_h = out_h, out_w

            min_ocr_width = 1200
            if out_w < min_ocr_width:
                scale = min_ocr_width / out_w
                out_w = min_ocr_width
                out_h = int(out_h * scale)

            dst = np.array(
                [
                    [0, 0],
                    [out_w - 1, 0],
                    [out_w - 1, out_h - 1],
                    [0, out_h - 1],
                ],
                dtype=np.float32,
            )

            M = cv2.getPerspectiveTransform(rect, dst)
            warped = cv2.warpPerspective(img, M, (out_w, out_h))
            return warped

        # ════════════════════════════════════════════════════════════════════
        # ESTRATEGIA 4: Fallback inteligente
        # ════════════════════════════════════════════════════════════════════
        def strategy_fallback(img):
            fh, fw = img.shape[:2]

            guide_w = int(fw * 0.88)
            guide_h = int(guide_w / (85.6 / 54.0))

            max_h = int(fh * 0.70)
            if guide_h > max_h:
                guide_h = max_h
                guide_w = int(guide_h * (85.6 / 54.0))

            x = (fw - guide_w) // 2
            y = (fh - guide_h) // 2 - int(fh * 0.04)

            x = max(0, min(x, fw - guide_w))
            y = max(0, min(y, fh - guide_h))

            cropped = img[y : y + guide_h, x : x + guide_w]

            if cropped.shape[1] < 1200:
                scale = 1200 / cropped.shape[1]
                new_w = 1200
                new_h = int(cropped.shape[0] * scale)
                cropped = cv2.resize(
                    cropped, (new_w, new_h), interpolation=cv2.INTER_CUBIC
                )

            return cropped

        # ════════════════════════════════════════════════════════════════════
        # EJECUTAR ESTRATEGIAS EN CASCADA
        # ════════════════════════════════════════════════════════════════════
        result_image = None
        strategy_used = None

        # Estrategia 1: Canny mejorado
        contour = strategy_canny(image)
        if contour is not None:
            try:
                result_image = apply_perspective_transform(image, contour)
                strategy_used = "canny"
            except Exception as e:
                logger.debug("Estrategia Canny falló en transformación: %s", e)

        # Estrategia 2: Segmentación HSV
        if result_image is None:
            contour = strategy_hsv(image)
            if contour is not None:
                try:
                    result_image = apply_perspective_transform(image, contour)
                    strategy_used = "hsv"
                except Exception as e:
                    logger.debug("Estrategia HSV falló en transformación: %s", e)

        # Estrategia 3: Líneas de Hough
        if result_image is None:
            contour = strategy_hough(image)
            if contour is not None:
                try:
                    result_image = apply_perspective_transform(image, contour)
                    strategy_used = "hough"
                except Exception as e:
                    logger.debug("Estrategia Hough falló en transformación: %s", e)

        # Estrategia 4: Fallback inteligente
        if result_image is None:
            result_image = strategy_fallback(image)
            strategy_used = "fallback"

        logger.info(
            "crop_document: estrategia usada='%s', output=%dx%d",
            strategy_used,
            result_image.shape[1],
            result_image.shape[0],
        )

        _, buffer = cv2.imencode(".jpg", result_image, [cv2.IMWRITE_JPEG_QUALITY, 95])
        return buffer.tobytes()

    # -------------------------------------------------------------------------
    # Internos
    # -------------------------------------------------------------------------

    def _resize_if_needed(self, image: np.ndarray) -> np.ndarray:
        """
        Redimensiona la imagen si excede el ancho máximo.
        Mantiene la relación de aspecto.
        """
        h, w = image.shape[:2]

        if w <= self._max_width:
            return image

        scale = self._max_width / w
        new_w = self._max_width
        new_h = int(h * scale)

        resized = cv2.resize(
            image,
            (new_w, new_h),
            interpolation=cv2.INTER_AREA,  # Mejor calidad para reducción
        )

        logger.debug("Imagen redimensionada: %dx%d → %dx%d", w, h, new_w, new_h)

        return resized
