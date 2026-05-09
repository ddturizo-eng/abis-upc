import oracledb
import os

POOL_MIN = 2
POOL_MAX = 10
POOL_INC = 1

DB_USER = os.getenv("ABIS_DB_USER", "abis")
DB_PASSWORD = os.getenv("ABIS_DB_PASSWORD", "")
_JDBC_URL = os.getenv("ABIS_DB_URL", "jdbc:oracle:thin:@localhost:1521/xepdb1")
_DSN = _JDBC_URL.replace("jdbc:oracle:thin:@", "")

_pool = None


def init_db():
    global _pool
    _pool = oracledb.create_pool(
        user=DB_USER,
        password=DB_PASSWORD,
        dsn=_DSN,
        min=POOL_MIN,
        max=POOL_MAX,
        increment=POOL_INC,
    )
    print(f"[Oracle] Pool creado: min={POOL_MIN} max={POOL_MAX} dsn={_DSN}")


def _get_connection():
    if _pool is None:
        raise RuntimeError("Pool de Oracle no inicializado. Ejecute init_db() primero.")
    return _pool.acquire()


def save_template(identificacion: str, template_b64: str, hash_sha256: str) -> bool:
    conn = _get_connection()
    try:
        cur = conn.cursor()
        cur.execute(
            """UPDATE Votantes
               SET plantillaBiometrica = :1,
                   hashIntegridadBiometrica = :2,
                   fechaConsentimiento = SYSDATE
               WHERE identificacion = :3""",
            [template_b64, hash_sha256, identificacion],
        )
        conn.commit()
        return cur.rowcount > 0
    finally:
        conn.close()


def get_all_templates() -> list[dict]:
    conn = _get_connection()
    try:
        cur = conn.cursor()
        cur.execute(
            """SELECT identificacion,
                      primerNombre,
                      segundoNombre,
                      primerApellido,
                      segundoApellido,
                      plantillaBiometrica
               FROM Votantes
               WHERE plantillaBiometrica IS NOT NULL
                 AND estadoVoto = 'PENDIENTE'"""
        )
        rows = cur.fetchall()
        return [
            {
                "identificacion": r[0],
                "primer_nombre": r[1],
                "segundo_nombre": r[2] or "",
                "primer_apellido": r[3],
                "segundo_apellido": r[4] or "",
                "template_b64": r[5],
            }
            for r in rows
        ]
    finally:
        conn.close()


def get_user_by_id(identificacion: str) -> dict | None:
    conn = _get_connection()
    try:
        cur = conn.cursor()
        cur.execute(
            """SELECT identificacion, primerNombre, segundoNombre,
                      primerApellido, segundoApellido, estadoVoto,
                      Roles_idRol, Puestos_votacion_idPuestos,
                      plantillaBiometrica
               FROM Votantes
               WHERE identificacion = :1""",
            [identificacion],
        )
        r = cur.fetchone()
        if not r:
            return None
        return {
            "identificacion": r[0],
            "primer_nombre": r[1],
            "segundo_nombre": r[2] or "",
            "primer_apellido": r[3],
            "segundo_apellido": r[4] or "",
            "estado_voto": r[5],
            "rol_id": r[6],
            "puesto_id": r[7],
            "template_b64": r[8],
        }
    finally:
        conn.close()


def get_votante_completo(identificacion: str) -> dict | None:
    conn = _get_connection()
    try:
        cur = conn.cursor()
        cur.execute(
            """SELECT identificacion, primerNombre, segundoNombre,
                      primerApellido, segundoApellido, correo,
                      estadoVoto, fotoUrl, fechaConsentimiento,
                      Roles_idRol, Puestos_votacion_idPuestos
               FROM Votantes
               WHERE identificacion = :1""",
            [identificacion],
        )
        r = cur.fetchone()
        if not r:
            return None
        return {
            "identificacion": r[0],
            "primer_nombre": r[1],
            "segundo_nombre": r[2] or "",
            "primer_apellido": r[3],
            "segundo_apellido": r[4] or "",
            "correo": r[5],
            "estado_voto": r[6],
            "foto_url": r[7],
            "fecha_consentimiento": str(r[8]) if r[8] else None,
            "rol_id": r[9],
            "puesto_id": r[10],
        }
    finally:
        conn.close()


def marcar_voto_ejercido(identificacion: str) -> bool:
    conn = _get_connection()
    try:
        cur = conn.cursor()
        cur.execute(
            """UPDATE Votantes SET estadoVoto = 'EJERCIDO'
               WHERE identificacion = :1 AND estadoVoto = 'PENDIENTE'""",
            [identificacion],
        )
        conn.commit()
        return cur.rowcount > 0
    finally:
        conn.close()
