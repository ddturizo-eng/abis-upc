import os
from dotenv import load_dotenv
from pathlib import Path

load_dotenv(dotenv_path=Path(__file__).resolve().parent.parent / ".env")
print("USER:", os.getenv("ABIS_DB_USER"))
print("PASS:", os.getenv("ABIS_DB_PASSWORD"))
print("URL:", os.getenv("ABIS_DB_URL"))
