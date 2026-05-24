import pytesseract
import cv2
from parser import extraer_datos

# Ajusta esta ruta si es necesario
pytesseract.pytesseract.tesseract_cmd = r'C:\Program Files\Tesseract-OCR\tesseract.exe'

img = cv2.imread("images/cedula.jpg")

text = pytesseract.image_to_string(img, lang='spa')



# después del OCR
print("=== TEXTO OCR ===")
print(text)

datos = extraer_datos(text)

print("\n=== DATOS EXTRAIDOS ===")
print(datos)

