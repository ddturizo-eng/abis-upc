-- ============================================================================
-- Nombre: 02_fnc_votante_puede_votar.sql
-- Descripcion: Función de validación completa antes de mostrar pantalla de
--              votación. Verifica existencia, estado (solo INHABILITADO global,
--              ya no EJERCIDO porque el voto es por elección), estado de elección,
--              registro previo en REGISTRO_VOTOS (per-elección) y si el rol del
--              votante está configurado en ELECCION_ROLES para esta elección.
-- Retorna: VARCHAR2 ('S' o 'N')
-- Parámetro OUT: p_motivo VARCHAR2 — mensaje para mostrar al usuario
-- Fecha: Mayo 2026  |  Ultima modificacion: Mayo 2026 (eliminado bloque EJERCIDO
--        global + agregada validacion ELECCION_ROLES)
-- ============================================================================

CREATE OR REPLACE FUNCTION fnc_votante_puede_votar(
    p_identificacion IN  VARCHAR2,
    p_id_eleccion    IN  NUMBER,
    p_motivo         OUT VARCHAR2
) RETURN VARCHAR2 AS
    v_estado_votante  VOTANTES.estado_voto%TYPE;
    v_estado_eleccion ELECCIONES.estado%TYPE;
    v_nombre          VARCHAR2(100);
    v_ya_registro     NUMBER;
BEGIN
    -- 1. El votante existe?
    BEGIN
        SELECT estado_voto, primer_nombre || ' ' || primer_apellido
        INTO   v_estado_votante, v_nombre
        FROM   VOTANTES WHERE identificacion = p_identificacion;
    EXCEPTION
        WHEN NO_DATA_FOUND THEN
            p_motivo := 'El votante ' || p_identificacion ||
                        ' no esta registrado en el sistema.';
            RETURN 'N';
    END;

    -- 2. Votante INHABILITADO administrativamente?
    IF v_estado_votante = 'INHABILITADO' THEN
        p_motivo := v_nombre || ' esta INHABILITADO. ' ||
                    'Contacte al administrador del sistema.';
        RETURN 'N';
    END IF;

    -- NOTA: El bloque EJERCIDO global fue eliminado (Mayo 2026).
    -- El voto se valida por elección via REGISTRO_VOTOS más abajo.
    -- ESTADO_VOTO solo se usa para PENDIENTE / INHABILITADO.

    -- 3. La eleccion existe?
    BEGIN
        SELECT estado INTO v_estado_eleccion
        FROM ELECCIONES WHERE id_eleccion = p_id_eleccion;
    EXCEPTION
        WHEN NO_DATA_FOUND THEN
            p_motivo := 'La eleccion ' || p_id_eleccion ||
                        ' no existe en el sistema.';
            RETURN 'N';
    END;

    -- 4. Eleccion PROGRAMADA (aún no iniciada)?
    IF v_estado_eleccion = 'PROGRAMADA' THEN
        p_motivo := 'La eleccion aun no ha iniciado.';
        RETURN 'N';
    END IF;

    -- 5. Eleccion CERRADA?
    IF v_estado_eleccion = 'CERRADA' THEN
        p_motivo := 'La eleccion ya fue cerrada. No se aceptan mas votos.';
        RETURN 'N';
    END IF;

    -- 6. Ya votó en ESTA elección? (validación per-elección)
    SELECT COUNT(*) INTO v_ya_registro
    FROM REGISTRO_VOTOS
    WHERE identificacion = p_identificacion AND id_eleccion = p_id_eleccion;

    IF v_ya_registro > 0 THEN
        p_motivo := v_nombre || ' ya tiene registro de participacion ' ||
                    'en esta eleccion.';
        RETURN 'N';
    END IF;

    -- 7. El rol del votante está configurado en ELECCION_ROLES?
    DECLARE
        v_id_rol_votante NUMBER;
        v_rol_config     NUMBER;
    BEGIN
        SELECT id_rol INTO v_id_rol_votante
        FROM VOTANTES WHERE identificacion = p_identificacion;

        SELECT COUNT(*) INTO v_rol_config
        FROM ELECCION_ROLES
        WHERE id_eleccion = p_id_eleccion AND id_rol = v_id_rol_votante;

        IF v_rol_config = 0 THEN
            p_motivo := v_nombre || ', su rol no esta habilitado para votar en esta eleccion.';
            RETURN 'N';
        END IF;
    END;

    p_motivo := 'Bienvenido, ' || v_nombre || '. Puede proceder a votar.';
    RETURN 'S';
EXCEPTION
    WHEN OTHERS THEN
        p_motivo := 'Error inesperado: ' || SQLERRM;
        RETURN 'N';
END fnc_votante_puede_votar;
/

-- Uso desde backend:
-- DECLARE
--     v_puede  VARCHAR2(1);
--     v_motivo VARCHAR2(500);
-- BEGIN
--     v_puede := fnc_votante_puede_votar('1001001001', 1, v_motivo);
--     -- v_puede = 'S' o 'N'
--     -- v_motivo = mensaje para mostrar al usuario
-- END;
