import argparse
import json
import os
import re
import signal
import sys
import time
from dataclasses import dataclass
from pathlib import Path
from typing import Optional

try:
    from dotenv import load_dotenv
except ModuleNotFoundError:
    load_dotenv = None

try:
    import requests
except ModuleNotFoundError:
    requests = None

try:
    import serial
except ModuleNotFoundError:
    serial = None


TOKEN_PATTERN = re.compile(r"^ABIS-[0-9]+-[A-Z0-9]{12,32}$")


@dataclass
class ScannerConfig:
    port: str
    baudrate: int
    java_url: str
    scanner_id: str
    puesto_id: Optional[int]
    read_timeout: float
    post_timeout: float
    dedupe_seconds: float


class ContingencyScanner:
    def __init__(self, config: ScannerConfig) -> None:
        self.config = config
        self.running = True
        self.last_token = ""
        self.last_token_at = 0.0

    def stop(self, *_args: object) -> None:
        self.running = False

    def run(self) -> None:
        if serial is None:
            raise RuntimeError("Falta instalar pyserial: python -m pip install pyserial")
        if requests is None:
            raise RuntimeError("Falta instalar requests: python -m pip install requests")

        print(f"[scanner] Puerto={self.config.port} baud={self.config.baudrate}")
        print(f"[scanner] Backend={self.config.java_url}")
        with serial.Serial(
            port=self.config.port,
            baudrate=self.config.baudrate,
            timeout=self.config.read_timeout,
            bytesize=serial.EIGHTBITS,
            parity=serial.PARITY_NONE,
            stopbits=serial.STOPBITS_ONE,
        ) as connection:
            connection.reset_input_buffer()
            print("[scanner] Escuchando QR de contingencia...")
            while self.running:
                raw = self.read_frame(connection)
                if raw is None:
                    continue
                token = normalize_token(raw)
                if not token:
                    continue
                if self.is_duplicate(token):
                    print(f"[scanner] Lectura duplicada ignorada: {mask(token)}")
                    continue
                if not TOKEN_PATTERN.match(token):
                    print(f"[scanner] Formato invalido: {repr(token)}")
                    continue
                self.post_scan(token)

    def read_frame(self, connection: serial.Serial) -> Optional[bytes]:
        buffer = bytearray()
        started_at = time.monotonic()
        while self.running:
            chunk = connection.read(1)
            if chunk:
                if chunk in (b"\r", b"\n"):
                    if buffer:
                        return bytes(buffer)
                    continue
                buffer.extend(chunk)
                if len(buffer) > 128:
                    return bytes(buffer)
                continue

            if buffer and time.monotonic() - started_at >= self.config.read_timeout:
                return bytes(buffer)
            if not buffer:
                return None
        return None

    def is_duplicate(self, token: str) -> bool:
        now = time.monotonic()
        if token == self.last_token and now - self.last_token_at < self.config.dedupe_seconds:
            return True
        self.last_token = token
        self.last_token_at = now
        return False

    def post_scan(self, token: str) -> None:
        payload = {
            "token": token,
            "scannerId": self.config.scanner_id,
            "puestoId": self.config.puesto_id,
        }
        url = f"{self.config.java_url.rstrip('/')}/api/contingencia/scan"
        print(f"[scanner] Enviando token {mask(token)}")
        try:
            response = requests.post(url, json=payload, timeout=self.config.post_timeout)
            data = parse_response(response)
            if response.ok:
                votante = data.get("votante", {})
                print(f"[scanner] OK: {votante.get('identificacion', '--')} {votante.get('nombre', '')}")
            else:
                print(f"[scanner] Rechazado HTTP {response.status_code}: {data.get('message') or data.get('error') or data}")
        except requests.RequestException as exc:
            print(f"[scanner] Error HTTP: {exc}")


def normalize_token(raw: bytes) -> str:
    text = raw.decode("ascii", errors="ignore")
    return (
        text.replace("\x00", "")
        .replace("\r", "")
        .replace("\n", "")
        .replace(" ", "")
        .strip()
        .upper()
    )


def parse_response(response: requests.Response) -> dict:
    try:
        return response.json()
    except json.JSONDecodeError:
        return {"error": response.text}


def mask(token: str) -> str:
    if len(token) <= 8:
        return token
    return f"{token[:8]}...{token[-6:]}"


def load_config() -> ScannerConfig:
    root = Path(__file__).resolve().parents[1]
    if load_dotenv is not None:
        load_dotenv(root / ".env")

    parser = argparse.ArgumentParser(description="ABIS-UPC scanner serial de contingencia")
    parser.add_argument("--port", default=os.getenv("SCANNER_PORT", "COM5"))
    parser.add_argument("--baud", type=int, default=int(os.getenv("SCANNER_BAUD", "9600")))
    parser.add_argument("--java-url", default=os.getenv("ABIS_JAVA_URL", "http://localhost:7000"))
    parser.add_argument("--scanner-id", default=os.getenv("SCANNER_ID", "YHD9601D-01"))
    parser.add_argument("--puesto-id", type=int, default=optional_int(os.getenv("SCANNER_PUESTO_ID")))
    parser.add_argument("--read-timeout", type=float, default=float(os.getenv("SCANNER_READ_TIMEOUT", "0.20")))
    parser.add_argument("--post-timeout", type=float, default=float(os.getenv("SCANNER_POST_TIMEOUT", "2.0")))
    parser.add_argument("--dedupe-seconds", type=float, default=float(os.getenv("SCANNER_DEDUPE_SECONDS", "2.0")))
    args = parser.parse_args()

    return ScannerConfig(
        port=args.port,
        baudrate=args.baud,
        java_url=args.java_url,
        scanner_id=args.scanner_id,
        puesto_id=args.puesto_id,
        read_timeout=args.read_timeout,
        post_timeout=args.post_timeout,
        dedupe_seconds=args.dedupe_seconds,
    )


def optional_int(value: Optional[str]) -> Optional[int]:
    if value is None or not str(value).strip():
        return None
    return int(value)


def main() -> int:
    config = load_config()
    scanner = ContingencyScanner(config)
    signal.signal(signal.SIGINT, scanner.stop)
    signal.signal(signal.SIGTERM, scanner.stop)
    try:
        scanner.run()
        return 0
    except RuntimeError as exc:
        print(f"[scanner] {exc}")
        return 2
    except KeyboardInterrupt:
        return 0
    except Exception as exc:
        if serial is not None and isinstance(exc, serial.SerialException):
            print(f"[scanner] No se pudo abrir el puerto {config.port}: {exc}")
            return 2
        raise


if __name__ == "__main__":
    sys.exit(main())
