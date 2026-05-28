"""Módulo de persistencia y conexión de datos para ABIS-UPC.

Este módulo implementa el pool de conexiones relacionales optimizado para el driver
`oracledb` de Oracle en modo Thick/Thin. Centraliza las operaciones transaccionales
de enrolamiento dactilar, lectura de plantillas biométricas codificadas en base64,
registro atómico de consentimiento informado y la mutación de estados del sufragante.
"""

import os
from typing import Any

import oracledb

# Configuración del pool de conexiones para alta concurrencia electoral
POOL_MIN: int = 2
POOL_MAX: int = 10
POOL_INC: int = 1

_pool: oracledb.ConnectionPool | None = None


def init_db() -> None:
    """Inicializa el pool global de conexiones a la base de datos Oracle.

    Lee las variables de entorno para autenticación y parsea la URL JDBC estándar
    convirtiéndola al formato DSN nativo requerido por la biblioteca `oracledb`.

    Raises:
        RuntimeError: Si las variables de entorno críticas ABIS_DB_USER o
            ABIS_DB_PASSWORD no están configuradas en el entorno.
    """
    global _pool
    db_user = os.getenv("ABIS_DB_USER")
    db_password = os.getenv("ABIS_DB_PASSWORD")
    jdbc_url = os.getenv("ABIS_DB_URL", "jdbc:oracle:thin:@localhost:1521/XEPDB1")

    # Se limpia el prefijo JDBC para extraer la cadena de conexión pura (Host/Port/Service)
    dsn = jdbc_url.replace("jdbc:oracle:thin:@", "")

    if not db_user or not db_password:
        raise RuntimeError(
            "Variables de Oracle requeridas para abis-biometric: "
            "ABIS_DB_USER y ABIS_DB_PASSWORD"
        )

    _pool = oracledb.create_pool(
        user=db_user,
        password=db_password,
        dsn=dsn,
        min=POOL_MIN,
        max=POOL_MAX,
        increment=POOL_INC,
    )
    print(f"[Oracle] Pool creado: min={POOL_MIN} max={POOL_MAX} dsn={dsn}")


def _get_connection() -> oracledb.Connection:
    """Adquiere una conexión activa desde el pool de conexiones global.

    Returns:
        oracledb.Connection: Instancia de conexión transaccional de Oracle.

    Raises:
        RuntimeError: Si se intenta solicitar un canal antes de inicializar
            el recurso compartido mediante init_db().
    """
    if _pool is None:
        raise RuntimeError("Pool de Oracle no inicializado. Ejecute init_db() primero.")
    return _pool.acquire()


def save_template(identificacion: str, template_b64: str, hash_sha256: str) -> bool:
    """Guarda o actualiza la plantilla biométrica e impacta el consentimiento informado.

    Ejecuta una operación MERGE de manera atómica para evitar colisiones de hilos
    concurrentes de enrolamiento. Simultáneamente, asienta la estampa de tiempo de
    aceptación de términos legales en la tabla base de votantes.

    Args:
        identificacion: Documento de identidad del ciudadano a enrolar.
        template_b64: Cadena que representa el vector o minucias de la huella en Base64.
        hash_sha256: Firma digital SHA-256 para validación de integridad criptográfica.

    Returns:
        True si los datos maestros del votante fueron alterados con éxito en la
        operación, False en caso contrario (ej. cédula inexistente en el censo).
    """
    conn = _get_connection()
    try:
        cur = conn.cursor()
        # Se codifica a bytes nativos para almacenar la firma en columnas BLOB/RAW
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


def get_all_templates() -> list[dict[str, Any]]:
    """Recupera el universo de votantes con plantillas activas listos para sufragar.

    Filtra rigurosamente los ciudadanos que tengan su biometría vigente ('S') y 
    cuyo estado de participación sea 'PENDIENTE', excluyendo a quienes ya ejercieron
    el sufragio o fueron descalificados del proceso concurrente.

    Returns:
        Una lista de diccionarios con la estructura interna mapeada de datos
        básicos y la plantilla biométrica restaurada a formato textual.
    """
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


def get_user_by_id(identificacion: str) -> dict[str, Any] | None:
    """Extrae el perfil unificado de un sufragante acoplando su estado biométrico.

    Args:
        identificacion: Cédula o número identificador del sufragante.

    Returns:
        Un diccionario estructurado conteniendo los parámetros demográficos y de
        ubicación (Mesa/Puesto) junto a su plantilla, o None si el usuario no
        se encuentra en el censo electoral nacional.
    """
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


def get_votante_completo(identificacion: str) -> dict[str, Any] | None:
    """Retorna los atributos extendidos del votante excluyendo datos biométricos.

    Optimiza el ancho de banda del canal de red omitiendo los arreglos de bytes de
    las huellas dactilares cuando solo se requiere validación administrativa de perfiles.

    Args:
        identificacion: Documento de identidad del ciudadano.

    Returns:
        Diccionario con datos de contacto, firma de consentimiento e índices de
        rol/puesto físico, o None si el registro es inexistente.
    """
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
            # Se convierte explícitamente a string para evitar fallas de serialización JSON con datetime
            "fecha_consentimiento": str(r[8]) if r[8] else None,
            "rol_id": r[9],
            "puesto_id": r[10],
        }
    finally:
        conn.close()


def marcar_voto_ejercido(identificacion: str) -> bool:
    """Bloquea y cambia el estado electoral de un ciudadano a 'EJERCIDO'.

    Mecanismo Anti-Fraude: Evalúa mediante la cláusula WHERE que el estado actual 
    sea exactamente 'PENDIENTE'. Esto mitiga de forma estricta los ataques de doble 
    sufragio por concurrencia o re-ejecución de peticiones HTTP.

    Args:
        identificacion: Documento de identidad del ciudadano que acaba de votar.

    Returns:
        True si la transición de estado fue exitosa en la base de datos; False si
        el votante ya figuraba previamente como 'EJERCIDO'.
    """
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
    """Actualiza o asocia el enlace de almacenamiento del rostro capturado por OCR.

    Args:
        identificacion: Documento de identidad del ciudadano.
        foto_url: Dirección URL o URI del bucket de almacenamiento con el rostro indexado.

    Returns:
        True si el registro maestro del sufragante fue modificado, False de lo contrario.
    """
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
    """Extrae la plantilla dactilar en formato texto nativo para un votante activo.

    Args:
        identificacion: Documento del ciudadano consulta.

    Returns:
        Cadena Base64 de la plantilla recuperada, o None si no cuenta con biometría vinculada.
    """
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


def _blob_to_text(value: Any) -> str | None:
    """Transforma objetos e instancias LOB/BLOB de Oracle a texto UTF-8 plano.

    Resuelve de forma genérica si el driver entrega un objeto de streaming LOB
    con soporte para operaciones `.read()` u objetos nativos de bytes binarios.

    Args:
        value: Objeto, descriptor LOB o arreglo de bytes recuperado del cursor de Oracle.

    Returns:
        Cadena decodificada en UTF-8 o None si la columna original almacenaba un valor nulo.
    """
    if value is None:
        return None
    # oracledb puede retornar un objeto LOB con soporte a lectura en streaming
    if hasattr(value, "read"):
        value = value.read()
    if isinstance(value, bytes):
        return value.decode("utf-8")
    return str(value)