import { randomUUID } from 'node:crypto';

function required(value, field) {
  if (value === undefined || value === null || String(value).trim() === '') {
    const error = new Error(`${field} requerido`);
    error.statusCode = 400;
    error.publicMessage = error.message;
    throw error;
  }
}

function validateEmail(value) {
  required(value, 'correo');
  const email = String(value).trim();
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
    const error = new Error('correo invalido');
    error.statusCode = 400;
    error.publicMessage = error.message;
    throw error;
  }
}

function validatePayload(payload) {
  required(payload?.identificacion, 'identificacion');
  required(payload?.nombre, 'nombre');
  validateEmail(payload?.correo);
  required(payload?.idEleccion, 'idEleccion');
  required(payload?.nombreEleccion, 'nombreEleccion');
  required(payload?.fechaVoto, 'fechaVoto');
  required(payload?.codigoCertificado, 'codigoCertificado');
}

async function enviar(payload) {
  validatePayload(payload);

  return {
    success: true,
    status: 'ENVIADO',
    messageId: `mock-${randomUUID()}`
  };
}

export const certificadoService = {
  enviar
};
