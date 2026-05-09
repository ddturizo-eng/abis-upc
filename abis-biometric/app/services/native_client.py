import httpx

NATIVE_URL = "http://localhost:8765"


async def get_reader_status():
    async with httpx.AsyncClient(timeout=5.0) as client:
        r = await client.get(f"{NATIVE_URL}/status")
        return r.json()


async def capture_fingerprint():
    async with httpx.AsyncClient(timeout=20.0) as client:
        r = await client.post(f"{NATIVE_URL}/capture")
        return r.json()


async def enroll_fingerprint(samples: list):
    async with httpx.AsyncClient(timeout=30.0) as client:
        r = await client.post(f"{NATIVE_URL}/enroll", json={"samples": samples})
        return r.json()


async def identify_fingerprint(sample: str, templates: list, user_ids: list):
    async with httpx.AsyncClient(timeout=20.0) as client:
        r = await client.post(
            f"{NATIVE_URL}/identify",
            json={"sample": sample, "templates": templates, "userIds": user_ids},
        )
        return r.json()
