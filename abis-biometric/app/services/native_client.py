import httpx
import os

NATIVE_URL = "http://localhost:8765"
JAVA_BACKEND_URL = os.getenv("JAVA_BACKEND_URL", "http://localhost:7000")


async def get_reader_status():
    async with httpx.AsyncClient(timeout=5.0) as client:
        r = await client.get(f"{NATIVE_URL}/status")
        return r.json()


async def capture_fingerprint():
    async with httpx.AsyncClient(timeout=8.0) as client:
        r = await client.post(f"{NATIVE_URL}/capture")
        return r.json()


async def enroll_fingerprint(samples: list):
    async with httpx.AsyncClient(timeout=30.0) as client:
        r = await client.post(f"{NATIVE_URL}/enroll", json={"samples": samples})
        return r.json()


async def enroll_fingerprint_live(identificacion: str):
    async with httpx.AsyncClient(timeout=45.0) as client:
        r = await client.post(
            f"{NATIVE_URL}/enroll-live",
            json={
                "identificacion": identificacion,
                "progressCallbackUrl": f"{JAVA_BACKEND_URL}/api/interno/reportar-progreso",
            },
        )
        data = r.json()
        if r.status_code == 404 or data.get("error") == "endpoint no encontrado":
            data["live_supported"] = False
        return data


async def report_enroll_progress(
    identificacion: str,
    estado: str,
    samples: int,
    progreso: int,
    mensaje: str,
    error: str | None = None,
):
    payload = {
        "identificacion": identificacion,
        "estado": estado,
        "samples": samples,
        "progreso": progreso,
        "mensaje": mensaje,
        "error": error,
    }
    try:
        async with httpx.AsyncClient(timeout=2.0) as client:
            await client.post(
                f"{JAVA_BACKEND_URL}/api/interno/reportar-progreso",
                json=payload,
            )
    except Exception as exc:
        print(f"[NativeClient] No se pudo reportar progreso a Java: {exc}")


async def identify_fingerprint(sample: str, templates: list, user_ids: list):
    async with httpx.AsyncClient(timeout=20.0) as client:
        r = await client.post(
            f"{NATIVE_URL}/identify",
            json={"sample": sample, "templates": templates, "userIds": user_ids},
        )
        return r.json()
