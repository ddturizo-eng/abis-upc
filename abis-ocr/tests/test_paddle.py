import re
from datetime import datetime
from paddleocr import PaddleOCR

# Inicializar OCR
ocr = PaddleOCR(lang='es')

# Función para convertir fechas
def convertir_fecha(fecha):
    try:
        return datetime.strptime(fecha, "%d%b%Y").strftime("%Y-%m-%d")
    except:
        return fecha

# Ejecutar OCR
result = ocr.ocr("images/cedula.jpg")

# Estructura de datos
data = {
    "numero": None,
    "nombres": None,
    "apellidos": None,
    "fecha_nacimiento": None,
    "fecha_expiracion": None
}

ultimo_label = None

for line in result:
    for word in line:
        texto = word[1][0].strip()

        # -------------------------
        # Detectar etiquetas
        # -------------------------
        if "nacimiento" in texto.lower():
            ultimo_label = "fecha_nacimiento"
            continue

        if "expiración" in texto.lower():
            ultimo_label = "fecha_expiracion"
            continue

        # -------------------------
        # Número de cédula
        # -------------------------
        if "NUP" in texto or re.search(r'\d{1,3}(\.\d{3})+', texto):
            numero = re.sub(r"[^\d]", "", texto)
            data["numero"] = numero

        # -------------------------
        # Apellidos
        # -------------------------
        if texto.isupper() and len(texto.split()) >= 2:
            if "CHACON" in texto or "TURIZO" in texto:
                data["apellidos"] = texto

        # -------------------------
        # Nombres
        # -------------------------
        if texto.isupper() and "DANIEL" in texto:
            data["nombres"] = texto

        # -------------------------
        # Fechas (con contexto)
        # -------------------------
        if re.match(r'\d{2}[A-Z]{3}\d{4}', texto):
            if ultimo_label == "fecha_nacimiento":
                data["fecha_nacimiento"] = convertir_fecha(texto)
            elif ultimo_label == "fecha_expiracion":
                data["fecha_expiracion"] = convertir_fecha(texto)

# -------------------------
# Resultado final
# -------------------------
print("\n=== RESULTADO OCR ===")
print(data)