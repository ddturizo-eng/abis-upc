"""
ABIS-UPC | Microservicio Biométrico v3
Integracion completa:
  - OCR inteligente para documentos colombianos (Tesseract)
  - Enrolamiento y verificacion biometrica (DigitalPersona NativeService)
  - Persistencia en Oracle (oracledb)
"""

from dotenv import load_dotenv
from pathlib import Path


load_dotenv(dotenv_path=Path(__file__).resolve().parent.parent.parent / ".env")

# Carga el .env desde la raíz del proyecto (abis-upc/.env)
# __file__ = abis-upc/abis-biometric/app/main.py
# .parent.parent.parent = abis-upc/


import re
import hashlib
import cv2
import fitz
import numpy as np
import pytesseract
from contextlib import asynccontextmanager
from fastapi import FastAPI, File, UploadFile, HTTPException
from fastapi.middleware.cors import CORSMiddleware

from .routers import enroll, face, qr, verify, vote

pytesseract.pytesseract.tesseract_cmd = r"C:\Program Files\Tesseract-OCR\tesseract.exe"


# ── LIFESPAN: inicializar Oracle al arrancar ──────────────────────────────


@asynccontextmanager
async def lifespan(app: FastAPI):
    from .db.database import init_db

    init_db()
    print("[FastAPI] Pool de Oracle inicializado")
    yield


enroll_progress_state = {
    "state": "idle",
    "step": 0,
    "total": 4,
    "current_sample": 0,
    "message": "Listo para iniciar enrolamiento",
    "error": None,
}

app = FastAPI(
    title="ABIS Biometric Service",
    description="OCR + enrolamiento y verificacion biometrica sobre Oracle",
    version="3.0.0",
    lifespan=lifespan,
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=[
        "http://localhost:7000",
        "http://127.0.0.1:7000",
        "http://localhost:5500",
        "http://127.0.0.1:5500",
    ],
    allow_methods=["GET", "POST"],
    allow_headers=["*"],
)

# ── Routers ─────────────────────────────────────────────────────────────────

app.include_router(enroll.router, prefix="/enroll", tags=["Biometrico"])
app.include_router(verify.router, prefix="/verify", tags=["Biometrico"])
app.include_router(vote.router, prefix="/vote", tags=["Voto"])
app.include_router(face.router, prefix="/face", tags=["Rostro"])
app.include_router(qr.router, prefix="/qr", tags=["Documento"])


# ═══════════════════════════════════════════════════════════════════════════
#  OCR — CONSTANTES
# ═══════════════════════════════════════════════════════════════════════════

MIN_CHARS = 10
OCR_CONFIG_FULL = "--oem 3 --psm 6 -l spa"
OCR_CONFIG_BLOCK = "--oem 3 --psm 4 -l spa"


# ═══════════════════════════════════════════════════════════════════════════
#  OCR — PREPROCESAMIENTO
# ═══════════════════════════════════════════════════════════════════════════


def preprocess_image(img_bgr: np.ndarray) -> list[np.ndarray]:
    h, w = img_bgr.shape[:2]
    if w < 1200:
        scale = 1200 / w
        img_bgr = cv2.resize(
            img_bgr, None, fx=scale, fy=scale, interpolation=cv2.INTER_CUBIC
        )

    gray = cv2.cvtColor(img_bgr, cv2.COLOR_BGR2GRAY)

    _, otsu = cv2.threshold(gray, 0, 255, cv2.THRESH_BINARY + cv2.THRESH_OTSU)

    adaptive = cv2.adaptiveThreshold(
        gray, 255, cv2.ADAPTIVE_THRESH_GAUSSIAN_C, cv2.THRESH_BINARY, 25, 11
    )

    clahe = cv2.createCLAHE(clipLimit=2.0, tileGridSize=(8, 8))
    eq = clahe.apply(gray)
    _, clahe_bin = cv2.threshold(eq, 0, 255, cv2.THRESH_BINARY + cv2.THRESH_OTSU)

    denoised = cv2.fastNlMeansDenoisingColored(img_bgr, None, 6, 6, 7, 21)

    return [otsu, adaptive, clahe_bin, denoised]


def run_ocr(variants: list[np.ndarray]) -> str:
    best = ""
    for v in variants:
        try:
            txt = pytesseract.image_to_string(v, config=OCR_CONFIG_FULL)
            if len(txt.strip()) > len(best.strip()):
                best = txt
            txt2 = pytesseract.image_to_string(v, config=OCR_CONFIG_BLOCK)
            if len(txt2.strip()) > len(best.strip()):
                best = txt2
        except Exception:
            pass
    return best.strip()


# ═══════════════════════════════════════════════════════════════════════════
#  OCR — DETECCION Y PARSEO
# ═══════════════════════════════════════════════════════════════════════════


def normalizar(texto: str) -> str:
    texto = texto.upper()
    texto = re.sub(r"[^\w\s.\-/]", " ", texto)
    texto = re.sub(r"\s+", " ", texto)
    return texto.strip()


def detectar_tipo_documento(texto: str) -> str:
    t = texto.upper()
    if "TARJETA DE IDENTIDAD" in t or "IDENTIFICACION PERSONAL" in t:
        return "TI"
    if "CEDULA DE CIUDADANIA" in t or "CEDULA" in t or "NUIP" in t:
        return "CC"
    if "UNIVERSIDAD POPULAR DEL CESAR" in t or "UNICESAR" in t or "UPC" in t:
        return "CARNET_UPC"
    return "DESCONOCIDO"


def limpiar_numero(raw: str) -> str:
    sustituir = {
        "O": "0", "o": "0", "l": "1", "I": "1",
        "S": "5", "s": "5", "B": "8", "G": "9", "Z": "2",
    }
    limpio = ""
    for c in raw:
        if c.isdigit():
            limpio += c
        elif c in sustituir:
            limpio += sustituir[c]
    if len(limpio) < 5:
        return ""
    n = int(limpio)
    return f"{n:,}".replace(",", ".")


def extraer_numero_id(texto: str, tipo_doc: str) -> str:
    if tipo_doc == "CC":
        m = re.search(r"NUIP[\s.:]*([0-9OolISsBGZ.\s]{7,20})", texto, re.I)
        if m:
            return limpiar_numero(m.group(1))
    if tipo_doc == "TI":
        m = re.search(r"N[UÚ]MERO[\s.:]*([0-9OolISsBGZ.\s]{7,20})", texto, re.I)
        if m:
            return limpiar_numero(m.group(1))
    if tipo_doc == "CARNET_UPC":
        m = re.search(r"\bCC\b[\s.:]*([0-9OolISsBGZ.\s]{7,20})", texto, re.I)
        if m:
            return limpiar_numero(m.group(1))
    m = re.search(r"\b(\d{1,3}[.\s]\d{3}[.\s]\d{3}[.\s]\d{3})\b", texto)
    if m:
        return limpiar_numero(m.group(1).replace(" ", ""))
    m = re.search(r"\b(\d{7,12})\b", texto)
    if m:
        return limpiar_numero(m.group(1))
    return ""


def extraer_nombre(texto: str, tipo_doc: str) -> dict:
    apellidos = ""
    nombres = ""
    if tipo_doc in ("CC", "TI", "CARNET_UPC"):
        m_ap = re.search(
            r"APELLIDOS[\s\n:]*([A-ZÁÉÍÓÚÑ][A-ZÁÉÍÓÚÑ\s]{3,40})", texto, re.I
        )
        if m_ap:
            apellidos = m_ap.group(1).strip().split("\n")[0].strip()
        m_no = re.search(
            r"NOMBRES[\s\n:]*([A-ZÁÉÍÓÚÑ][A-ZÁÉÍÓÚÑ\s]{2,30})", texto, re.I
        )
        if m_no:
            nombres = m_no.group(1).strip().split("\n")[0].strip()
    nombre_completo = " ".join(filter(None, [apellidos, nombres])).strip()
    if not nombre_completo:
        nombre_completo = "No detectado"
    return {
        "apellidos": apellidos or "No detectado",
        "nombres": nombres or "No detectado",
        "nombre_completo": nombre_completo,
    }


def extraer_fecha_nacimiento(texto: str) -> str:
    m = re.search(
        r"(?:NACIMIENTO|NAC\.?)[\s\n:]*(\d{1,2}\s+[A-Z]{3}\s+\d{4})", texto, re.I
    )
    if m:
        return m.group(1).strip()
    m2 = re.search(r"\b(\d{1,2}\s+[A-Z]{3}\s+\d{4})\b", texto, re.I)
    return m2.group(1).strip() if m2 else ""


def extraer_sexo(texto: str) -> str:
    m = re.search(r"\bSEXO[\s\n:]*([MF])\b", texto, re.I)
    if m:
        return m.group(1).upper()
    m2 = re.search(r"\b([MF])\b(?=\s*\n|$)", texto, re.I)
    return m2.group(1).upper() if m2 else ""


# ═══════════════════════════════════════════════════════════════════════════
#  OCR — PIPELINE
# ═══════════════════════════════════════════════════════════════════════════


def imagen_a_ndarray(raw_bytes: bytes) -> np.ndarray | None:
    arr = np.frombuffer(raw_bytes, np.uint8)
    img = cv2.imdecode(arr, cv2.IMREAD_COLOR)
    return img


def pdf_a_imagenes(raw_bytes: bytes) -> list[np.ndarray]:
    imagenes = []
    doc = fitz.open(stream=raw_bytes, filetype="pdf")
    for page in doc:
        mat = fitz.Matrix(2.5, 2.5)
        pix = page.get_pixmap(matrix=mat, alpha=False)
        img_bytes = pix.tobytes("png")
        nparr = np.frombuffer(img_bytes, np.uint8)
        img = cv2.imdecode(nparr, cv2.IMREAD_COLOR)
        if img is not None:
            imagenes.append(img)
    doc.close()
    return imagenes


def parsear_documento(texto_raw: str) -> dict:
    texto = normalizar(texto_raw)
    tipo_doc = detectar_tipo_documento(texto)
    nombres_dict = extraer_nombre(texto, tipo_doc)
    numero_id = extraer_numero_id(texto, tipo_doc)
    fecha_nac = extraer_fecha_nacimiento(texto)
    sexo = extraer_sexo(texto)
    label_tipo = {
        "TI": "Tarjeta de Identidad",
        "CC": "Cedula de Ciudadania",
        "CARNET_UPC": "Carnet Estudiantil UPC",
        "DESCONOCIDO": "Documento desconocido",
    }.get(tipo_doc, "Documento desconocido")
    return {
        "tipo_doc": tipo_doc,
        "label_tipo": label_tipo,
        "nombres": nombres_dict["nombres"],
        "apellidos": nombres_dict["apellidos"],
        "nombre_completo": nombres_dict["nombre_completo"],
        "numero_id": numero_id or "No detectado",
        "fecha_nacimiento": fecha_nac or "",
        "sexo": sexo or "",
        "texto_raw": texto_raw,
        "fuente": "tesseract",
    }


# ═══════════════════════════════════════════════════════════════════════════
#  ENDPOINTS
# ═══════════════════════════════════════════════════════════════════════════


@app.get("/health")
def health():
    return {"status": "ok", "servicio": "abis-biometric", "version": "3.0.0"}


@app.get("/status")
def status():
    return {"service": "fastapi", "status": "ok"}


@app.get("/enroll/progress")
def enroll_progress():
    return enroll_progress_state


@app.post("/ocr/scan")
async def ocr_scan(file: UploadFile = File(...)):
    raw = await file.read()
    content_type = file.content_type or ""

    is_pdf = (
            content_type == "application/pdf"
            or (file.filename or "").lower().endswith(".pdf")
            or raw[:4] == b"%PDF"
    )

    if is_pdf:
        imagenes = pdf_a_imagenes(raw)
        if not imagenes:
            return {
                "error": "No se pudo extraer ninguna pagina del PDF",
                "fuente": "error",
            }
    else:
        img = imagen_a_ndarray(raw)
        if img is None:
            return {"error": "No se pudo decodificar la imagen", "fuente": "error"}
        imagenes = [img]

    texto_total = ""
    for img in imagenes:
        variantes = preprocess_image(img)
        texto_pag = run_ocr(variantes)
        texto_total += texto_pag + "\n"

    if len(texto_total.strip()) < MIN_CHARS:
        return {
            "tipo_doc": "DESCONOCIDO",
            "label_tipo": "No detectado",
            "nombres": "—",
            "apellidos": "—",
            "nombre_completo": "—",
            "numero_id": "—",
            "fecha_nacimiento": "",
            "sexo": "",
            "fuente": "mock",
            "nota": "Tesseract no extrajo texto suficiente. "
                    "Verifica iluminacion y resolucion.",
        }

    return parsear_documento(texto_total)
