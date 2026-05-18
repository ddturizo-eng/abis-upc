import { randomUUID } from 'node:crypto';
import { generarCertificadoPdf } from './pdfService.js';
import { enviarCertificadoPorCorreo } from './emailService.js';

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
  required(payload?.nombrePuesto, 'nombrePuesto');
  required(payload?.sede, 'sede');
  required(payload?.ciudad, 'ciudad');
}

async function enviar(payload) {
  validatePayload(payload);
  const pdf = await generarCertificadoPdf(payload);

  if (!pdf || pdf.length === 0) {
    const error = new Error('No fue posible generar el PDF del certificado');
    error.statusCode = 500;
    error.publicMessage = error.message;
    throw error;
  }

  const email = await enviarCertificadoPorCorreo(payload, pdf);

  return {
    success: true,
    status: 'ENVIADO',
    messageId: email?.id || `resend-${randomUUID()}`
  };
}

export const certificadoService = {
  enviar
};
