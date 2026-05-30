"""Módulo principal y orquestador del Microservicio Biométrico v3 para ABIS-UPC.

Este módulo inicializa el ciclo de vida de la aplicación FastAPI (Lifespan),
configura el middleware de CORS corporativo, inyecta los enrutadores modulares de la
arquitectura y define el pipeline analítico de OCR basado en Tesseract y OpenCV
para el reconocimiento y parseo de documentos de identidad colombianos.
"""

from dotenv import load_dotenv
from pathlib import Path


load_dotenv(dotenv_path=Path(__file__).resolve().parent.parent.parent / ".env")

# Carga el .env desde la raíz del proyecto (abis-upc/.env)
# __file__ = abis-upc/abis-biometric/app/main.py
# .parent.parent.parent = abis-upc/


import re
import hashlib
import re
from contextlib import asynccontextmanager
from dotenv import load_dotenv
from fastapi import FastAPI, File, HTTPException, UploadFile
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import Response
from pathlib import Path
from typing import Any

import cv2
import fitz
import numpy as np
import pytesseract

from .routers import enroll, face, qr, verify, vote

# Carga de forma determinista el entorno desde la raíz del proyecto (abis-upc/.env)
load_dotenv(dotenv_path=Path(__file__).resolve().parent.parent.parent / ".env")

# Ruta absoluta por defecto para la ejecución del motor binario de Tesseract en Windows
pytesseract.pytesseract.tesseract_cmd = r"C:\Program Files\Tesseract-OCR\tesseract.exe"


# ── LIFESPAN: Inicializar Pool de Datos ─────────────────────────────────────

@asynccontextmanager
async def lifespan(app: FastAPI):
    """Administra el ciclo de vida de la aplicación inicializando recursos globales.

    Args:
        app: Instancia de la aplicación FastAPI.
    """
    from .db.database import init_db

    init_db()
    print("[FastAPI] Pool de Oracle inicializado")
    yield


enroll_progress_state = {
# Estado compartido en memoria para el seguimiento síncrono del enrolamiento
enroll_progress_state: dict[str, Any] = {
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

# ── Inyección de Routers de la Arquitectura ─────────────────────────────────

app.include_router(enroll.router, prefix="/enroll", tags=["Biometrico"])
app.include_router(verify.router, prefix="/verify", tags=["Biometrico"])
app.include_router(vote.router, prefix="/vote", tags=["Voto"])
app.include_router(face.router, prefix="/face", tags=["Rostro"])
app.include_router(qr.router, prefix="/qr", tags=["Documento"])


# ═══════════════════════════════════════════════════════════════════════════
#  OCR — CONSTANTES
# ═══════════════════════════════════════════════════════════════════════════

MIN_CHARS: int = 10
OCR_CONFIG_FULL: str = "--oem 3 --psm 6 -l spa"
OCR_CONFIG_BLOCK: str = "--oem 3 --psm 4 -l spa"


# ═══════════════════════════════════════════════════════════════════════════
#  OCR — PREPROCESAMIENTO
# ═══════════════════════════════════════════════════════════════════════════

def preprocess_image(img_bgr: np.ndarray) -> list[np.ndarray]:
    """Genera variantes matriciales binarias y filtradas para optimizar Tesseract.

    Aplica reescalado bicúbico, binarización de Otsu, umbralización adaptativa,
    ecualización por CLAHE y eliminación de ruido gaussiano para contrarrestar
    brillos o dobleces en las cédulas físicas.

    Args:
        img_bgr: Imagen original leída en formato tridimensional BGR de OpenCV.

    Returns:
        Una lista de arreglos NumPy conteniendo las transformaciones matriciales.
    """
    h, w = img_bgr.shape[:2]
    
    # Se normaliza la densidad de pixeles si el ancho es inferior al umbral mínimo de lectura OCR
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

    # Restringe el contraste local por bloques espaciales para rescatar texto sobre hologramas reflectivos
    clahe = cv2.createCLAHE(clipLimit=2.0, tileGridSize=(8, 8))
    eq = clahe.apply(gray)
    _, clahe_bin = cv2.threshold(eq, 0, 255, cv2.THRESH_BINARY + cv2.THRESH_OTSU)

    denoised = cv2.fastNlMeansDenoisingColored(img_bgr, None, 6, 6, 7, 21)

    return [otsu, adaptive, clahe_bin, denoised]


def run_ocr(variants: list[np.ndarray]) -> str:
    """Evalúa las mutaciones matriciales buscando la mayor densidad de caracteres.

    Args:
        variants: Lista de imágenes preprocesadas en escala de grises o color.

    Returns:
        La cadena de texto más extensa extraída de forma exitosa por el motor.
    """
    best: str = ""
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
    """Elimina caracteres especiales y quita ruidos léxicos del string plano.

    Args:
        texto: Cadena de caracteres cruda devuelta por Tesseract.

    Returns:
        Texto sanitizado en mayúsculas fijas y con espaciados unificados.
    """
    texto = texto.upper()
    texto = re.sub(r"[^\w\s.\-/]", " ", texto)
    texto = re.sub(r"\s+", " ", texto)
    return texto.strip()


def detectar_tipo_documento(texto: str) -> str:
    """Determina el tipo de credencial colombiana examinando patrones léxicos.

    Args:
        texto: Texto normalizado del documento escaneado.

    Returns:
        Acrónimo del documento clasificado ('CC', 'TI', 'CARNET_UPC' o 'DESCONOCIDO').
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
    """Corrige errores comunes de Tesseract sustituyendo homógrafos alfanuméricos.

    Args:
        raw: Segmento numérico extraído por la expresión regular.

    Returns:
        Cadena formateada con puntos electorales colombianos (ej. '1.065.123.456').
    """
    # Diccionario de confusión óptica: mapea caracteres alfabéticos a sus pares numéricos probables
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
    """Busca el número de cédula o NUIP aplicando patrones Regex según el tipo de documento.

    Args:
        texto: Texto normalizado de la captura.
        tipo_doc: Identificador de tipo de credencial previamente evaluada.

    Returns:
        El número del documento depurado o una cadena vacía si no se localiza.
    """
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
    # Búsquedas por descarte de patrones decimales agrupados si las cabeceras fallan
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
def extraer_nombre(texto: str, tipo_doc: str) -> dict[str, str]:
    """Extrae la estructura nominativa segmentada en apellidos y nombres.

    Args:
        texto: Texto normalizado del documento.
        tipo_doc: Tipo de credencial identificada.

    Returns:
        Diccionario conteniendo llaves de apellidos, nombres y nombre_completo.
    """
    apellidos: str = ""
    nombres: str = ""
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
    """Extrae la estampa cronológica de nacimiento usando el formato registral.

    Args:
        texto: Texto normalizado de la captura.

    Returns:
        La fecha identificada (ej. '25 ENE 2000') o cadena vacía.
    """
    m = re.search(
        r"(?:NACIMIENTO|NAC\.?)[\s\n:]*(\d{1,2}\s+[A-Z]{3}\s+\d{4})", texto, re.I
    )
    if m:
        return m.group(1).strip()
    m2 = re.search(r"\b(\d{1,2}\s+[A-Z]{3}\s+\d{4})\b", texto, re.I)
    return m2.group(1).strip() if m2 else ""


def extraer_sexo(texto: str) -> str:
    """Determina el género biológico inscrito en la tarjeta o cédula.

    Args:
        texto: Texto normalizado de la captura.

    Returns:
        Identificador de un solo carácter ('M' o 'F') o cadena vacía.
    """
    m = re.search(r"\bSEXO[\s\n:]*([MF])\b", texto, re.I)
    if m:
        return m.group(1).upper()
    m2 = re.search(r"\b([MF])\b(?=\s*\n|$)", texto, re.I)
    return m2.group(1).upper() if m2 else ""


# ═══════════════════════════════════════════════════════════════════════════
#  OCR — PIPELINE
# ═══════════════════════════════════════════════════════════════════════════

def imagen_a_ndarray(raw_bytes: bytes) -> np.ndarray | None:
    """Decodifica flujos binarios entrantes a arreglos numéricos de OpenCV.

    Args:
        raw_bytes: Cadena de bytes crudos de la imagen.

    Returns:
        Matriz de imagen utilizable o None si el búfer carece de estructura gráfica.
    """
    arr = np.frombuffer(raw_bytes, np.uint8)
    img = cv2.imdecode(arr, cv2.IMREAD_COLOR)
    return img


def pdf_a_imagenes(raw_bytes: bytes) -> list[np.ndarray]:
    """Renderiza páginas de un documento PDF convirtiéndolas en búferes matriciales.

    Utiliza PyMuPDF (fitz) aplicando una matriz de transformación de escala 
    a 2.5x para prevenir la pixelación y asegurar una tasa de éxito alta en OCR.

    Args:
        raw_bytes: Flujo binario estructurado del archivo PDF.

    Returns:
        Lista de matrices de imágenes listas para ser preprocesadas por OpenCV.
    """
    imagenes: list[np.ndarray] = []
    doc = fitz.open(stream=raw_bytes, filetype="pdf")
    for page in doc:
        # Incrementa la resolución base de la página (DPI) para mejorar bordes de texto pequeños
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
def parsear_documento(texto_raw: str) -> dict[str, Any]:
    """Clasifica y desglosa los fragmentos textuales mapeando el JSON final.

    Args:
        texto_raw: Texto íntegro extraído de todas las capas de análisis.

    Returns:
        Diccionario estructurado con campos demográficos unificados para la API.
    """
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

@app.get("/health", summary="Verificar salud técnica")
def health() -> dict[str, str]:
    """Valida la integridad básica del microservicio y versión actual.

    Returns:
        Estado operativo del contenedor.
    """
    return {"status": "ok", "servicio": "abis-biometric", "version": "3.0.0"}


@app.get("/status", summary="Comprobar estado de FastAPI")
def status() -> dict[str, str]:
    """Comprueba la disponibilidad inmediata del servidor HTTP.

    Returns:
        Token de estado.
    """
    return {"service": "fastapi", "status": "ok"}


@app.get("/enroll/progress")
def enroll_progress():
@app.get("/enroll/progress", summary="Consultar progreso biométrico")
def enroll_progress() -> dict[str, Any]:
    """Devuelve el estado intermedio del motor de enrolamiento por huellas.

    Returns:
        Snapshot con las muestras recolectadas de la máquina de estados.
    """
    return enroll_progress_state


@app.post("/ocr/scan", summary="Escanear y parsear documento de identidad")
async def ocr_scan(file: UploadFile = File(...)) -> dict[str, Any]:
    """Analiza imágenes o PDFs mediante visión computacional para extraer censo.

    Soporta archivos binarios directos e infiere de forma segura si la carga útil
    corresponde a firmas estructurales PDF o imágenes estándar en disco.

    Args:
        file: Archivo binario (PDF/PNG/JPG) cargado en la petición HTTP.

    Returns:
        Un JSON unificado con los atributos del ciudadano y texto crudo indexado.
    """
    raw = await file.read()
    content_type = file.content_type or ""

    # Detección multifactor de PDF basada en MIME, extensión nominal y los primeros 4 bytes mágicos corporativos
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

    # Mecanismo de contingencia (Graceful Degradation) por si la calidad focal o lumínica anula los clasificadores
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
            "nota": "Tesseract no extrajo texto suficiente. Verifica iluminacion y resolucion.",
        }

    return parsear_documento(texto_total)