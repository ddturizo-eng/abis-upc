import argparse
import time
from pathlib import Path

try:
    import serial
except ModuleNotFoundError:
    serial = None


def normalize(data: bytes) -> str:
    text = data.decode("ascii", errors="ignore")
    return (
        text.replace("\x00", "")
        .replace("\r", "")
        .replace("\n", "")
        .replace(" ", "")
        .strip()
        .upper()
    )


def read_frame(connection: "serial.Serial", timeout: float, max_len: int) -> bytes | None:
    buffer = bytearray()
    started_at = time.monotonic()

    while True:
        chunk = connection.read(1)
        if chunk:
            if chunk in (b"\r", b"\n"):
                if buffer:
                    return bytes(buffer)
                continue

            buffer.extend(chunk)
            if len(buffer) >= max_len:
                return bytes(buffer)
            continue

        if buffer and time.monotonic() - started_at >= timeout:
            return bytes(buffer)

        if not buffer:
            return None


def load_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Prueba de lectura serial del escaner ABIS-UPC")
    parser.add_argument("--port", default="COM9", help="Puerto serial del escaner, por ejemplo COM9")
    parser.add_argument("--baud", type=int, default=9600, help="Velocidad serial")
    parser.add_argument("--timeout", type=float, default=0.20, help="Tiempo de espera para cerrar trama")
    parser.add_argument("--max-len", type=int, default=128, help="Tamano maximo de lectura")
    return parser.parse_args()


def main() -> int:
    args = load_args()

    if serial is None:
        print("Falta instalar pyserial: python -m pip install pyserial")
        return 2

    root = Path(__file__).resolve().parents[1]
    print(f"Raiz del proyecto: {root}")
    print(f"Abriendo {args.port} a {args.baud} baudios...")

    try:
        with serial.Serial(
            port=args.port,
            baudrate=args.baud,
            timeout=args.timeout,
            bytesize=serial.EIGHTBITS,
            parity=serial.PARITY_NONE,
            stopbits=serial.STOPBITS_ONE,
        ) as connection:
            connection.reset_input_buffer()
            print("Listo. Escanea un codigo para ver la lectura.")

            while True:
                raw = read_frame(connection, args.timeout, args.max_len)
                if raw is None:
                    continue

                print(f"RAW HEX: {raw.hex(' ')}")
                print(f"RAW BYTES: {raw!r}")
                print(f"NORMALIZADO: {normalize(raw)}")
                print("-" * 60)
    except serial.SerialException as exc:
        print(f"No se pudo abrir el puerto {args.port}: {exc}")
        return 2
    except KeyboardInterrupt:
        print("Lectura detenida por el usuario.")
        return 0


if __name__ == "__main__":
    raise SystemExit(main())
