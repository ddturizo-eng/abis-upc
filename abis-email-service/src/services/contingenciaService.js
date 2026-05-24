import { randomUUID } from 'node:crypto';
import { enviarQrContingenciaPorCorreo } from './emailService.js';

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
  required(payload?.tokenHint, 'tokenHint');
  required(payload?.qrPngBase64, 'qrPngBase64');
}

async function enviarQr(payload) {
  validatePayload(payload);
  const qrPng = Buffer.from(String(payload.qrPngBase64), 'base64');
  if (!qrPng.length) {
    const error = new Error('qrPngBase64 invalido');
    error.statusCode = 400;
    error.publicMessage = error.message;
    throw error;
  }

  const email = await enviarQrContingenciaPorCorreo(payload, qrPng);
  return {
    success: true,
    status: 'ENVIADO',
    messageId: email?.id || `resend-${randomUUID()}`
  };
}

export const contingenciaService = {
  enviarQr
};
