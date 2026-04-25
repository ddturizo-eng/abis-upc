# ABIS-UPC — Sistema Biométrico Electoral
Universidad Popular del Cesar | Programación III |Bases de datos | 2026-1

## Requisitos
- Java JDK 24/ ecplise termurin 21 (java 21 inicial ) JDK 24 para jks (`C:/Program Files/Java/jdk-24`)
- Python 3.11
- Oracle XE 21c (PDB: xepdb1)
- Maven 3.9+

## Setup inicial (una vez por máquina)

### 1. Clonar y configurar variables de entorno
git clone <https://github.com/ddturizo-eng/abis-upc.git>
cd abis-upc
cp .env.example .env
# Editar .env con las credenciales reales

### 2. Generar el KeyStore AES-256 (abrir CMD, no PowerShell)
"C:\Program Files\Java\jdk-24\bin\keytool.exe" -genseckey -alias abis-aes-key -keyalg AES -keysize 256 -storetype JCEKS -keystore abis-upc.jks
# Guardar la contraseña en .env → ABIS_KEYSTORE_PASSWORD

### 3. Configurar JAVA_HOME (PowerShell como Administrador)
[System.Environment]::SetEnvironmentVariable("JAVA_HOME","C:\Program Files\Java\jdk-24","Machine")

### 4. Backend Java
cd abis-backend
mvn clean install
mvn exec:java

### 5. Microservicio Python
cd abis-biometric
python -m venv venv
venv\Scripts\activate
pip install -r requirements.txt
uvicorn app.main:app --reload --port 8000

## Estructura del proyecto
abis-backend/    → Java 21 + Javalin :7000
abis-biometric/  → Python 3.11 + FastAPI :8000
abis-database/   → DDL y DML Oracle
abis-frontend/   → HTML + CSS + JS puro
docs/            → UML y documentación

## Equipo
| Desarrollador    | Rama                  |
|------------------|-----------------------|
| Daniel Turizo    | develop, feature/biometric, feature/services |
| Daniel Flórez    | feature/security      |
| Mateo Calderón   | feature/dao-oracle    |
| Jorge Herrera    | feature/dao-oracle    |
| Ana Laura Cuéllar| feature/frontend      |