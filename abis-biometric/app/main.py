"""
ABIS-UPC | Microservicio Biométrico v2
OCR inteligente para documentos colombianos:
  - T.I  (Tarjeta de Identidad)
  - C.C  (Cédula de Ciudadanía - nuevo modelo con NUIP)
  - Carnet Estudiantil UPC
"""

import re
import io
import cv2
import fitz          # PyMuPDF — para PDFs
import numpy as np
import pytesseract
from fastapi import FastAPI, File, UploadFile, HTTPException
from fastapi.middleware.cors import CORSMiddleware

pytesseract.pytesseract.tesseract_cmd = r"C:\Program Files\Tesseract-OCR\tesseract.exe"

app = FastAPI(
    title="ABIS Biometric Service",
    description="Microservicio OCR para documentos colombianos",
    version="2.0.0",
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["http://localhost:7000", "http://127.0.0.1:7000"],
    allow_methods=["POST"],
    allow_headers=["*"],
)

# ── Constantes ──────────────────────────────────────────────────────────────
MIN_CHARS        = 10          # mínimo de texto útil para intentar parsear
OCR_CONFIG_FULL  = "--oem 3 --psm 6 -l spa"
OCR_CONFIG_BLOCK = "--oem 3 --psm 4 -l spa"


# ═══════════════════════════════════════════════════════════════════════════
#  PREPROCESAMIENTO DE IMAGEN
# ═══════════════════════════════════════════════════════════════════════════

def preprocess_image(img_bgr: np.ndarray) -> list[np.ndarray]:
    """
    Devuelve varias versiones preprocesadas para maximizar el OCR.
    Tesseract vota sobre la mejor.
    """
    # Escalar si la imagen es pequeña
    h, w = img_bgr.shape[:2]
    if w < 1200:
        scale = 1200 / w
        img_bgr = cv2.resize(img_bgr, None, fx=scale, fy=scale,
                             interpolation=cv2.INTER_CUBIC)

    gray = cv2.cvtColor(img_bgr, cv2.COLOR_BGR2GRAY)

    # 1. Otsu básico
    _, otsu = cv2.threshold(gray, 0, 255,
                            cv2.THRESH_BINARY + cv2.THRESH_OTSU)

    # 2. Adaptive threshold (útil en documentos con fondo jaspeado)
    adaptive = cv2.adaptiveThreshold(
        gray, 255,
        cv2.ADAPTIVE_THRESH_GAUSSIAN_C,
        cv2.THRESH_BINARY, 25, 11
    )

    # 3. CLAHE + Otsu (mejora contraste antes de binarizar)
    clahe = cv2.createCLAHE(clipLimit=2.0, tileGridSize=(8, 8))
    eq    = clahe.apply(gray)
    _, clahe_bin = cv2.threshold(eq, 0, 255,
                                 cv2.THRESH_BINARY + cv2.THRESH_OTSU)

    # 4. Versión a color para Tesseract (a veces funciona mejor)
    # Denoising leve
    denoised = cv2.fastNlMeansDenoisingColored(img_bgr, None, 6, 6, 7, 21)

    return [otsu, adaptive, clahe_bin, denoised]


def run_ocr(variants: list[np.ndarray]) -> str:
    """Corre OCR sobre todas las variantes y devuelve el texto más largo."""
    best = ""
    for v in variants:
        try:
            txt = pytesseract.image_to_string(v, config=OCR_CONFIG_FULL)
            if len(txt.strip()) > len(best.strip()):
                best = txt
            # Segunda pasada con PSM 4 en la misma variante
            txt2 = pytesseract.image_to_string(v, config=OCR_CONFIG_BLOCK)
            if len(txt2.strip()) > len(best.strip()):
                best = txt2
        except Exception:
            pass
    return best.strip()


# ═══════════════════════════════════════════════════════════════════════════
#  DETECCIÓN Y PARSEO DE DOCUMENTOS
# ═══════════════════════════════════════════════════════════════════════════

def normalizar(texto: str) -> str:
    """Normaliza el texto: mayúsculas, colapsa espacios, quita chars raros."""
    texto = texto.upper()
    texto = re.sub(r"[^\w\s.\-/]", " ", texto)
    texto = re.sub(r"\s+", " ", texto)
    return texto.strip()


def detectar_tipo_documento(texto: str) -> str:
    """
    Detecta el tipo de documento a partir del texto extraído.
    Retorna: 'TI' | 'CC' | 'CARNET_UPC' | 'DESCONOCIDO'
    """
    t = texto.upper()
    if "TARJETA DE IDENTIDAD" in t or "IDENTIFICACION PERSONAL" in t:
        return "TI"
    if "CEDULA DE CIUDADANIA" in t or "CEDULA" in t or "NUIP" in t:
        return "CC"
    if "UNIVERSIDAD POPULAR DEL CESAR" in t or "UNICESAR" in t or "UPC" in t:
        return "CARNET_UPC"
    return "DESCONOCIDO"


def limpiar_numero(raw: str) -> str:
    """
    Extrae solo dígitos de una cadena y formatea como número colombiano
    (grupos de 3 separados por puntos: 1.052.041.109).
    Corrige confusiones comunes de Tesseract: O→0, l/I→1, S→5.
    """
    sustituir = {"O": "0", "o": "0", "l": "1", "I": "1", "S": "5",
                 "s": "5", "B": "8", "G": "9", "Z": "2"}
    limpio = ""
    for c in raw:
        if c.isdigit():
            limpio += c
        elif c in sustituir:
            limpio += sustituir[c]
    # Si tiene menos de 5 dígitos, probablemente no es un ID
    if len(limpio) < 5:
        return ""
    # Formatear con puntos
    # Ej: 1052041109 → 1.052.041.109
    n = int(limpio)
    return f"{n:,}".replace(",", ".")


def extraer_numero_id(texto: str, tipo_doc: str) -> str:
    """
    Estrategia de extracción según el tipo de documento.
    """
    # CC moderno: buscar "NUIP" seguido de número
    if tipo_doc == "CC":
        m = re.search(r"NUIP[\s.:]*([0-9OolISsBGZ.\s]{7,20})", texto, re.I)
        if m:
            return limpiar_numero(m.group(1))

    # T.I.: buscar "NUMERO" o "No." seguido de número
    if tipo_doc == "TI":
        m = re.search(r"N[UÚ]MERO[\s.:]*([0-9OolISsBGZ.\s]{7,20})", texto, re.I)
        if m:
            return limpiar_numero(m.group(1))

    # Carnet UPC: buscar "CC" seguido de número
    if tipo_doc == "CARNET_UPC":
        m = re.search(r"\bCC\b[\s.:]*([0-9OolISsBGZ.\s]{7,20})", texto, re.I)
        if m:
            return limpiar_numero(m.group(1))

    # Fallback genérico: número largo con puntos
    m = re.search(r"\b(\d{1,3}[.\s]\d{3}[.\s]\d{3}[.\s]\d{3})\b", texto)
    if m:
        return limpiar_numero(m.group(1).replace(" ", ""))

    # Fallback: cualquier secuencia de ≥7 dígitos
    m = re.search(r"\b(\d{7,12})\b", texto)
    if m:
        return limpiar_numero(m.group(1))

    return ""


def extraer_nombre(texto: str, tipo_doc: str) -> dict:
    """
    Extrae apellidos y nombres por separado.
    Retorna {"apellidos": ..., "nombres": ..., "nombre_completo": ...}
    """
    apellidos = ""
    nombres   = ""

    if tipo_doc in ("CC", "TI", "CARNET_UPC"):
        # Buscar label "Apellidos" / "APELLIDOS"
        m_ap = re.search(
            r"APELLIDOS[\s\n:]*([A-ZÁÉÍÓÚÑ][A-ZÁÉÍÓÚÑ\s]{3,40})",
            texto, re.I
        )
        if m_ap:
            apellidos = m_ap.group(1).strip().split("\n")[0].strip()

        # Buscar label "Nombres" / "NOMBRES"
        m_no = re.search(
            r"NOMBRES[\s\n:]*([A-ZÁÉÍÓÚÑ][A-ZÁÉÍÓÚÑ\s]{2,30})",
            texto, re.I
        )
        if m_no:
            nombres = m_no.group(1).strip().split("\n")[0].strip()

    nombre_completo = " ".join(filter(None, [apellidos, nombres])).strip()
    if not nombre_completo:
        nombre_completo = "No detectado"

    return {
        "apellidos": apellidos or "No detectado",
        "nombres"  : nombres   or "No detectado",
        "nombre_completo": nombre_completo,
    }


def extraer_fecha_nacimiento(texto: str) -> str:
    """Extrae fecha de nacimiento (ej: 13 JUN 2006)."""
    m = re.search(
        r"(?:NACIMIENTO|NAC\.?)[\s\n:]*(\d{1,2}\s+[A-Z]{3}\s+\d{4})",
        texto, re.I
    )
    if m:
        return m.group(1).strip()
    # Fallback: cualquier fecha tipo DD MMM YYYY
    m2 = re.search(r"\b(\d{1,2}\s+[A-Z]{3}\s+\d{4})\b", texto, re.I)
    return m2.group(1).strip() if m2 else ""


def extraer_sexo(texto: str) -> str:
    m = re.search(r"\bSEXO[\s\n:]*([MF])\b", texto, re.I)
    if m:
        return m.group(1).upper()
    m2 = re.search(r"\b([MF])\b(?=\s*\n|$)", texto, re.I)
    return m2.group(1).upper() if m2 else ""


# ═══════════════════════════════════════════════════════════════════════════
#  PIPELINE PRINCIPAL
# ═══════════════════════════════════════════════════════════════════════════

def imagen_a_ndarray(raw_bytes: bytes) -> np.ndarray | None:
    arr = np.frombuffer(raw_bytes, np.uint8)
    img = cv2.imdecode(arr, cv2.IMREAD_COLOR)
    return img


def pdf_a_imagenes(raw_bytes: bytes) -> list[np.ndarray]:
    """
    Convierte cada página del PDF a imagen OpenCV.
    Usa PyMuPDF (fitz) — sin dependencia de Poppler.
    """
    imagenes = []
    doc = fitz.open(stream=raw_bytes, filetype="pdf")
    for page in doc:
        mat  = fitz.Matrix(2.5, 2.5)   # 2.5× zoom → ~180–200 DPI
        pix  = page.get_pixmap(matrix=mat, alpha=False)
        img_bytes = pix.tobytes("png")
        nparr = np.frombuffer(img_bytes, np.uint8)
        img   = cv2.imdecode(nparr, cv2.IMREAD_COLOR)
        if img is not None:
            imagenes.append(img)
    doc.close()
    return imagenes


def parsear_documento(texto_raw: str) -> dict:
    """
    Recibe el texto crudo de Tesseract y retorna un dict estructurado.
    """
    texto = normalizar(texto_raw)

    tipo_doc = detectar_tipo_documento(texto)
    nombres_dict = extraer_nombre(texto, tipo_doc)
    numero_id    = extraer_numero_id(texto, tipo_doc)
    fecha_nac    = extraer_fecha_nacimiento(texto)
    sexo         = extraer_sexo(texto)

    label_tipo = {
        "TI"         : "Tarjeta de Identidad",
        "CC"         : "Cédula de Ciudadanía",
        "CARNET_UPC" : "Carnet Estudiantil UPC",
        "DESCONOCIDO": "Documento desconocido",
    }.get(tipo_doc, "Documento desconocido")

    return {
        "tipo_doc"        : tipo_doc,
        "label_tipo"      : label_tipo,
        "nombres"         : nombres_dict["nombres"],
        "apellidos"       : nombres_dict["apellidos"],
        "nombre_completo" : nombres_dict["nombre_completo"],
        "numero_id"       : numero_id or "No detectado",
        "fecha_nacimiento": fecha_nac  or "",
        "sexo"            : sexo       or "",
        "texto_raw"       : texto_raw,
        "fuente"          : "tesseract",
    }


# ═══════════════════════════════════════════════════════════════════════════
#  ENDPOINTS
# ═══════════════════════════════════════════════════════════════════════════

@app.get("/health")
def health():
    return {"status": "ok", "servicio": "abis-biometric", "version": "2.0.0"}


@app.post("/ocr/scan")
async def ocr_scan(file: UploadFile = File(...)):
    """
    Acepta imagen (PNG/JPG/WEBP) o PDF.
    Retorna datos estructurados del documento colombiano detectado.
    """
    raw = await file.read()
    content_type = file.content_type or ""

    # ── Determinar si es PDF ───────────────────────────────────────────────
    is_pdf = (
            content_type == "application/pdf"
            or (file.filename or "").lower().endswith(".pdf")
            or raw[:4] == b"%PDF"
    )

    # ── Obtener imagen(s) para OCR ─────────────────────────────────────────
    if is_pdf:
        imagenes = pdf_a_imagenes(raw)
        if not imagenes:
            return {"error": "No se pudo extraer ninguna página del PDF", "fuente": "error"}
    else:
        img = imagen_a_ndarray(raw)
        if img is None:
            return {"error": "No se pudo decodificar la imagen", "fuente": "error"}
        imagenes = [img]

    # ── OCR sobre todas las imágenes / páginas ─────────────────────────────
    texto_total = ""
    for img in imagenes:
        variantes  = preprocess_image(img)
        texto_pag  = run_ocr(variantes)
        texto_total += texto_pag + "\n"

    if len(texto_total.strip()) < MIN_CHARS:
        return {
            "tipo_doc"        : "DESCONOCIDO",
            "label_tipo"      : "No detectado",
            "nombres"         : "—",
            "apellidos"       : "—",
            "nombre_completo" : "—",
            "numero_id"       : "—",
            "fecha_nacimiento": "",
            "sexo"            : "",
            "fuente"          : "mock",
            "nota"            : "Tesseract no extrajo texto suficiente. "
                                "Verifica iluminación y resolución.",
        }

    return parsear_documento(texto_total)