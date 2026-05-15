import os

import oracledb

POOL_MIN = 2
POOL_MAX = 10
POOL_INC = 1

_pool = None


def init_db():
    global _pool
    db_user = os.getenv("ABIS_DB_USER", "abis")
    db_password = os.getenv("ABIS_DB_PASSWORD", "")
    jdbc_url = os.getenv("ABIS_DB_URL", "jdbc:oracle:thin:@localhost:1521/XEPDB1")
    dsn = jdbc_url.replace("jdbc:oracle:thin:@", "")

    _pool = oracledb.create_pool(
        user=db_user,
        password=db_password,
        dsn=dsn,
        min=POOL_MIN,
        max=POOL_MAX,
        increment=POOL_INC,
    )
    print(f"[Oracle] Pool creado: min={POOL_MIN} max={POOL_MAX} dsn={dsn}")


def _get_connection():
    if _pool is None:
        raise RuntimeError("Pool de Oracle no inicializado. Ejecute init_db() primero.")
    return _pool.acquire()


def save_template(identificacion: str, template_b64: str, hash_sha256: str) -> bool:
    """Guarda o actualiza plantilla biometrica en BIOMETRIA_VOTANTES."""
    conn = _get_connection()
    try:
        cur = conn.cursor()
        template_bytes = template_b64.encode("utf-8")
        cur.execute(
            """MERGE INTO BIOMETRIA_VOTANTES b
               USING (SELECT :identificacion AS IDENTIFICACION FROM DUAL) src
               ON (b.IDENTIFICACION = src.IDENTIFICACION)
               WHEN MATCHED THEN
                 UPDATE SET b.PLANTILLA_BIOMETRICA = :template,
                            b.HASHINTEGRIDADBIOMETRICA = :hash,
                            b.FECHA_ENROLAMIENTO = SYSDATE,
                            b.ACTIVO = 'S'
               WHEN NOT MATCHED THEN
                 INSERT (ID_BIOMETRIA,
                         IDENTIFICACION,
                         PLANTILLA_BIOMETRICA,
                         HASHINTEGRIDADBIOMETRICA,
                         FECHA_ENROLAMIENTO,
                         ACTIVO)
                 VALUES (seq_biometria_votantes.NEXTVAL,
                         :identificacion,
                         :template,
                         :hash,
                         SYSDATE,
                         'S')""",
            {
                "identificacion": identificacion,
                "template": template_bytes,
                "hash": hash_sha256,
            },
        )
        cur.execute(
            """UPDATE VOTANTES
               SET FECHA_CONSENTIMIENTO = SYSDATE
               WHERE IDENTIFICACION = :1""",
            [identificacion],
        )
        updated = cur.rowcount > 0
        conn.commit()
        return updated
    finally:
        conn.close()


def get_all_templates() -> list[dict]:
    """Retorna votantes con plantilla activa y estado PENDIENTE."""
    conn = _get_connection()
    try:
        cur = conn.cursor()
        cur.execute(
            """SELECT v.IDENTIFICACION,
                      v.PRIMER_NOMBRE,
                      v.SEGUNDO_NOMBRE,
                      v.PRIMER_APELLIDO,
                      v.SEGUNDO_APELLIDO,
                      b.PLANTILLA_BIOMETRICA
               FROM VOTANTES v
               JOIN BIOMETRIA_VOTANTES b
                 ON b.IDENTIFICACION = v.IDENTIFICACION
                AND b.ACTIVO = 'S'
               WHERE v.ESTADO_VOTO = 'PENDIENTE'"""
        )
        rows = cur.fetchall()
        return [
            {
                "identificacion": r[0],
                "primer_nombre": r[1],
                "segundo_nombre": r[2] or "",
                "primer_apellido": r[3],
                "segundo_apellido": r[4] or "",
                "template_b64": _blob_to_text(r[5]),
            }
            for r in rows
        ]
    finally:
        conn.close()


def get_user_by_id(identificacion: str) -> dict | None:
    """Retorna datos basicos del votante y plantilla activa si existe."""
    conn = _get_connection()
    try:
        cur = conn.cursor()
        cur.execute(
            """SELECT IDENTIFICACION,
                      PRIMER_NOMBRE,
                      SEGUNDO_NOMBRE,
                      PRIMER_APELLIDO,
                      SEGUNDO_APELLIDO,
                      ESTADO_VOTO,
                      ID_ROL,
                      ID_PUESTO
               FROM VOTANTES
               WHERE IDENTIFICACION = :1""",
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
            "template_b64": _get_template_by_identificacion(identificacion),
        }
    finally:
        conn.close()


def get_votante_completo(identificacion: str) -> dict | None:
    """Retorna todos los campos del votante sin plantilla biometrica."""
    conn = _get_connection()
    try:
        cur = conn.cursor()
        cur.execute(
            """SELECT IDENTIFICACION,
                      PRIMER_NOMBRE,
                      SEGUNDO_NOMBRE,
                      PRIMER_APELLIDO,
                      SEGUNDO_APELLIDO,
                      CORREO,
                      ESTADO_VOTO,
                      FOTO_URL,
                      FECHA_CONSENTIMIENTO,
                      ID_ROL,
                      ID_PUESTO
               FROM VOTANTES
               WHERE IDENTIFICACION = :1""",
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
    """Cambia ESTADO_VOTO de PENDIENTE a EJERCIDO."""
    conn = _get_connection()
    try:
        cur = conn.cursor()
        cur.execute(
            """UPDATE VOTANTES
               SET ESTADO_VOTO = 'EJERCIDO'
               WHERE IDENTIFICACION = :1
                 AND ESTADO_VOTO = 'PENDIENTE'""",
            [identificacion],
        )
        conn.commit()
        return cur.rowcount > 0
    finally:
        conn.close()


def actualizar_foto(identificacion: str, foto_url: str) -> bool:
    """Guarda la URL de la foto del rostro del votante."""
    conn = _get_connection()
    try:
        cur = conn.cursor()
        cur.execute(
            """UPDATE VOTANTES
               SET FOTO_URL = :1
               WHERE IDENTIFICACION = :2""",
            [foto_url, identificacion],
        )
        conn.commit()
        return cur.rowcount > 0
    finally:
        conn.close()


def _get_template_by_identificacion(identificacion: str) -> str | None:
    conn = _get_connection()
    try:
        cur = conn.cursor()
        cur.execute(
            """SELECT PLANTILLA_BIOMETRICA
               FROM BIOMETRIA_VOTANTES
               WHERE IDENTIFICACION = :1
                 AND ACTIVO = 'S'""",
            [identificacion],
        )
        row = cur.fetchone()
        return _blob_to_text(row[0]) if row else None
    finally:
        conn.close()


def _blob_to_text(value) -> str | None:
    if value is None:
        return None
    if hasattr(value, "read"):
        value = value.read()
    if isinstance(value, bytes):
        return value.decode("utf-8")
    return str(value)
