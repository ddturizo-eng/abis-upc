INSERT INTO Roles (idRol, nombre, pesoVoto) VALUES (seq_roles.NEXTVAL, 'Estudiante', 1);
INSERT INTO Roles (idRol, nombre, pesoVoto) VALUES (seq_roles.NEXTVAL, 'Docente', 2);
INSERT INTO Roles (idRol, nombre, pesoVoto) VALUES (seq_roles.NEXTVAL, 'Egresado', 1);
INSERT INTO Roles (idRol, nombre, pesoVoto) VALUES (seq_roles.NEXTVAL, 'Administrativo', 1);

INSERT INTO Puestos_votacion (idPuestos, ciudad, sede, nombrePuesto, horaInicio, horaSalida)
VALUES (seq_puestos_votacion.NEXTVAL, 'Valledupar', 'Sede Sabanas', 'Sala de Sistemas 204',
        TO_TIMESTAMP('2026-01-01 08:00:00', 'YYYY-MM-DD HH24:MI:SS'),
        TO_TIMESTAMP('2026-01-01 18:00:00', 'YYYY-MM-DD HH24:MI:SS'));

INSERT INTO Puestos_votacion (idPuestos, ciudad, sede, nombrePuesto, horaInicio, horaSalida)
VALUES (seq_puestos_votacion.NEXTVAL, 'Valledupar', 'Sede Hurtado', 'Auditorio Principal',
        TO_TIMESTAMP('2026-01-01 08:00:00', 'YYYY-MM-DD HH24:MI:SS'),
        TO_TIMESTAMP('2026-01-01 18:00:00', 'YYYY-MM-DD HH24:MI:SS'));

INSERT INTO Puestos_votacion (idPuestos, ciudad, sede, nombrePuesto, horaInicio, horaSalida)
VALUES (seq_puestos_votacion.NEXTVAL, 'Aguachica', 'Sede Aguachica', 'Centro de Convenciones',
        TO_TIMESTAMP('2026-01-01 08:00:00', 'YYYY-MM-DD HH24:MI:SS'),
        TO_TIMESTAMP('2026-01-01 18:00:00', 'YYYY-MM-DD HH24:MI:SS'));

INSERT INTO Administradores (idAdmin, usuario, passwordHash, nombre, correo)
VALUES (seq_administradores.NEXTVAL, 'abisadmin',
        '8737f1c27602dd297b416cd4bc42707b3b96e8a26923d29bfccfb395cefc2339',
        'Administrador Principal', 'ddturizo@unicesar.edu.co');

COMMIT;
