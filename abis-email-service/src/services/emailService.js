import { Resend } from 'resend';
import { config } from '../config/config.js';

function ensureResendConfig() {
  if (!config.resendApiKey) {
    const error = new Error('ABIS_RESEND_API_KEY no configurada');
    error.statusCode = 503;
    error.publicMessage = error.message;
    throw error;
  }

  if (!config.resendFromEmail) {
    const error = new Error('ABIS_RESEND_FROM_EMAIL no configurado');
    error.statusCode = 503;
    error.publicMessage = error.message;
    throw error;
  }
}

function emailHtml(payload) {
  return `
    <p>Hola ${payload.nombre},</p>
    <p>Adjuntamos tu certificado de participacion en <strong>${payload.nombreEleccion}</strong>.</p>
    <p>Codigo de certificado: <strong>${payload.codigoCertificado}</strong></p>
    <p>Este mensaje confirma participacion y no contiene informacion sobre la seleccion realizada.</p>
  `;
}

export async function enviarCertificadoPorCorreo(payload, pdfBuffer) {
  ensureResendConfig();

  const resend = new Resend(config.resendApiKey);
  const { data, error } = await resend.emails.send({
    from: config.resendFromEmail,
    to: [payload.correo],
    subject: `Certificado de participacion - ${payload.nombreEleccion}`,
    html: emailHtml(payload),
    attachments: [
      {
        filename: `certificado-${payload.codigoCertificado}.pdf`,
        content: Buffer.from(pdfBuffer).toString('base64')
      }
    ]
  });

  if (error) {
    const sendError = new Error(error.message || 'Resend no pudo enviar el certificado');
    sendError.statusCode = 502;
    sendError.publicMessage = 'No fue posible enviar el certificado por correo';
    throw sendError;
  }

  return data;
}

function contingenciaHtml(payload) {
  return `
    <p>Hola ${payload.nombre},</p>
    <p>Adjuntamos tu QR de contingencia para <strong>${payload.nombreEleccion}</strong>.</p>
    <p>Presenta este QR unicamente si la verificacion biometrica no puede completarse durante la jornada.</p>
    <p>Codigo de referencia: <strong>${payload.tokenHint}</strong></p>
    <p>Este codigo es personal e intransferible.</p>
  `;
}

export async function enviarQrContingenciaPorCorreo(payload, qrPngBuffer) {
  ensureResendConfig();

  const resend = new Resend(config.resendApiKey);
  const { data, error } = await resend.emails.send({
    from: config.resendFromEmail,
    to: [payload.correo],
    subject: `QR de contingencia - ${payload.nombreEleccion}`,
    html: contingenciaHtml(payload),
    attachments: [
      {
        filename: `qr-contingencia-${payload.identificacion}.png`,
        content: Buffer.from(qrPngBuffer).toString('base64')
      }
    ]
  });

  if (error) {
    const sendError = new Error(error.message || 'Resend no pudo enviar el QR de contingencia');
    sendError.statusCode = 502;
    sendError.publicMessage = 'No fue posible enviar el QR de contingencia por correo';
    throw sendError;
  }

  return data;
}
