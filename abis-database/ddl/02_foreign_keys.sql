-- =============================================================================
-- ABIS-UPC | DDL - Foreign Keys y Constraints
-- Universidad Popular del Cesar | Programación III | 2026
-- EJECUTAR SOLO CUANDO EL PROFESOR EXPLIQUE FOREIGN KEYS
-- Verificar que todas las tablas existan antes de ejecutar.
-- =============================================================================

-- Verificar tablas existentes antes de ejecutar:
-- SELECT table_name FROM user_tables ORDER BY table_name;

-- =============================================================================
-- FOREIGN KEYS — Orden de ejecución importante (tabla padre primero)
-- =============================================================================

-- 1. VOTANTES → ROLES
ALTER TABLE VOTANTES
    ADD CONSTRAINT fk_votantes_roles
    FOREIGN KEY (ROLES_IDROL)
    REFERENCES ROLES(ID_ROL);

-- 2. VOTANTES → PUESTOS_VOTACION
ALTER TABLE VOTANTES
    ADD CONSTRAINT fk_votantes_puestos
    FOREIGN KEY (PUESTOS_VOTACION_IDPUESTOS)
    REFERENCES PUESTOS_VOTACION(ID_PUESTOS);

-- 3. MESA_JURADOS → PUESTOS_VOTACION
ALTER TABLE MESA_JURADOS
    ADD CONSTRAINT fk_mesa_puestos
    FOREIGN KEY (PUESTOS_VOTACION_IDPUESTOS)
    REFERENCES PUESTOS_VOTACION(ID_PUESTOS);

-- 4. JURADOS → MESA_JURADOS (tabla padre debe existir)
ALTER TABLE JURADOS
    ADD CONSTRAINT fk_jurados_mesa
    FOREIGN KEY (MESA_JURADOS_IDMESA)
    REFERENCES MESA_JURADOS(ID_MESA);

-- 5. JURADOS → VOTANTES
ALTER TABLE JURADOS
    ADD CONSTRAINT fk_jurados_votantes
    FOREIGN KEY (VOTANTES_IDENTIFICACION)
    REFERENCES VOTANTES(IDENTIFICACION);

-- 6. CANDIDATOS → ELECCIONES
ALTER TABLE CANDIDATOS
    ADD CONSTRAINT fk_candidatos_elecciones
    FOREIGN KEY (ELECCIONES_IDELECCION)
    REFERENCES ELECCIONES(ID_ELECCION);

-- 7. VOTOS → ROLES
ALTER TABLE VOTOS
    ADD CONSTRAINT fk_votos_roles
    FOREIGN KEY (ROLES_IDROL)
    REFERENCES ROLES(ID_ROL);

-- 8. VOTOS → ELECCIONES
ALTER TABLE VOTOS
    ADD CONSTRAINT fk_votos_elecciones
    FOREIGN KEY (ELECCIONES_IDELECCION)
    REFERENCES ELECCIONES(ID_ELECCION);

-- 9. VOTOS → CANDIDATOS
ALTER TABLE VOTOS
    ADD CONSTRAINT fk_votos_candidatos
    FOREIGN KEY (IDCANDIDATO)
    REFERENCES CANDIDATOS(ID_CANDIDATO);

-- 10. REGISTRO_VOTOS → VOTANTES
ALTER TABLE REGISTRO_VOTOS
    ADD CONSTRAINT fk_registro_votantes
    FOREIGN KEY (VOTANTES_IDENTIFICACION)
    REFERENCES VOTANTES(IDENTIFICACION);

-- 11. REGISTRO_VOTOS → PUESTOS_VOTACION
ALTER TABLE REGISTRO_VOTOS
    ADD CONSTRAINT fk_registro_puestos
    FOREIGN KEY (PUESTOS_VOTACION_IDPUESTOS)
    REFERENCES PUESTOS_VOTACION(ID_PUESTOS);

-- 12. REGISTRO_VOTOS → ELECCIONES
ALTER TABLE REGISTRO_VOTOS
    ADD CONSTRAINT fk_registro_elecciones
    FOREIGN KEY (ELECCIONES_IDELECCION)
    REFERENCES ELECCIONES(ID_ELECCION);

-- 13. SESIONES → ADMINISTRADORES
ALTER TABLE SESIONES
    ADD CONSTRAINT fk_sesiones_admin
    FOREIGN KEY (ADMINISTRADORES_IDADMIN)
    REFERENCES ADMINISTRADORES(ID_ADMIN);


-- =============================================================================
-- CONSTRAINTS DE INTEGRIDAD ADICIONALES
-- =============================================================================

-- UNIQUE: un votante solo puede votar UNA VEZ POR ELECCIÓN
-- (no en todo el sistema — puede votar en elecciones futuras)
ALTER TABLE REGISTRO_VOTOS
    ADD CONSTRAINT uq_votante_por_eleccion
    UNIQUE (VOTANTES_IDENTIFICACION, ELECCIONES_IDELECCION);


-- =============================================================================
-- VERIFICACIÓN — Ejecutar después para confirmar
-- =============================================================================
-- SELECT constraint_name, constraint_type, table_name
-- FROM user_constraints
-- WHERE constraint_type IN ('R', 'U')
-- ORDER BY table_name, constraint_type;
