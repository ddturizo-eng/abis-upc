import { Resend } from 'resend';
import { config } from '../config/config.js';

function escapeHtml(value) {
  return String(value || '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#039;');
}

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
<!DOCTYPE html>
<html lang="es">
<head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"></head>
<body style="margin:0;padding:0;background:#f2f3f5;font-family:Georgia,serif">
<table width="100%" cellpadding="0" cellspacing="0" style="background:#f2f3f5;padding:40px 0">
<tr><td align="center">
<table width="560" cellpadding="0" cellspacing="0" style="background:#fff;border-radius:16px;overflow:hidden;box-shadow:0 4px 24px rgba(0,0,0,.08)">

  <tr>
    <td style="background:linear-gradient(135deg,#145332,#1a6b3c);padding:28px 32px;text-align:center">
      <h1 style="margin:0;color:#fff;font-size:22px;font-weight:700;letter-spacing:1px">ABIS-UPC</h1>
      <p style="margin:6px 0 0;color:rgba(255,255,255,.8);font-size:13px">Sistema Electoral Universitario</p>
    </td>
  </tr>

  <tr>
    <td style="padding:32px">
      <p style="margin:0 0 12px;color:#333;font-size:16px">Hola <strong>${escapeHtml(payload.nombre)}</strong>,</p>
      <p style="margin:0 0 20px;color:#555;font-size:14px;line-height:1.6">Adjuntamos tu certificado de participacion en la eleccion <strong style="color:#1a6b3c">${escapeHtml(payload.nombreEleccion)}</strong>.</p>

      <table width="100%" cellpadding="0" cellspacing="0" style="background:#f8faf8;border:1px solid #d4e8d4;border-radius:10px;margin-bottom:20px">
        <tr><td style="padding:16px 20px">
          <p style="margin:0 0 6px;color:#888;font-size:11px;text-transform:uppercase;letter-spacing:1px">Codigo de certificado</p>
          <p style="margin:0;color:#145332;font-size:18px;font-weight:700;font-family:'JetBrains Mono',monospace">${escapeHtml(payload.codigoCertificado)}</p>
        </td></tr>
      </table>

      <p style="margin:0 0 0;color:#999;font-size:12px;line-height:1.5">Este mensaje confirma participacion y <strong>no contiene informacion</strong> sobre la seleccion realizada. El secreto del voto esta garantizado por ley.</p>
    </td>
  </tr>

  <tr>
    <td style="background:#f8faf8;border-top:1px solid #e5e7eb;padding:16px 32px;text-align:center">
      <p style="margin:0;color:#999;font-size:11px">Este mensaje es confidencial y esta protegido por la <strong>Ley 1581 de 2012</strong> de proteccion de datos personales.</p>
      <p style="margin:6px 0 0;color:#bbb;font-size:10px">Universidad Popular del Cesar — ABIS-UPC &copy; 2026</p>
    </td>
  </tr>

</table>
</td></tr>
</table>
</body>
</html>`;
}`

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
<!DOCTYPE html>
<html lang="es">
<head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"></head>
<body style="margin:0;padding:0;background:#f2f3f5;font-family:Georgia,serif">
<table width="100%" cellpadding="0" cellspacing="0" style="background:#f2f3f5;padding:40px 0">
<tr><td align="center">
<table width="560" cellpadding="0" cellspacing="0" style="background:#fff;border-radius:16px;overflow:hidden;box-shadow:0 4px 24px rgba(0,0,0,.08)">

  <tr>
    <td style="background:linear-gradient(135deg,#145332,#1a6b3c);padding:28px 32px;text-align:center">
      <h1 style="margin:0;color:#fff;font-size:22px;font-weight:700;letter-spacing:1px">ABIS-UPC</h1>
      <p style="margin:6px 0 0;color:rgba(255,255,255,.8);font-size:13px">Sistema Electoral Universitario</p>
    </td>
  </tr>

  <tr>
    <td style="padding:32px">
      <p style="margin:0 0 12px;color:#333;font-size:16px">Hola <strong>${escapeHtml(payload.nombre)}</strong>,</p>
      <p style="margin:0 0 20px;color:#555;font-size:14px;line-height:1.6">Adjuntamos tu <strong style="color:#1a6b3c">QR de contingencia</strong> para la eleccion <strong style="color:#1a6b3c">${escapeHtml(payload.nombreEleccion)}</strong>.</p>

      <table width="100%" cellpadding="0" cellspacing="0" style="background:#fff8f0;border:1px solid #f0d8b0;border-radius:10px;margin-bottom:20px">
        <tr><td style="padding:16px 20px">
          <p style="margin:0 0 4px;color:#888;font-size:11px;text-transform:uppercase;letter-spacing:1px">Instrucciones</p>
          <p style="margin:0;color:#333;font-size:13px;line-height:1.5">Escanee este codigo QR en el kiosco de votacion. <strong>No comparta este codigo.</strong> Presentelo unicamente si la verificacion biometrica no puede completarse durante la jornada.</p>
        </td></tr>
      </table>

      <table width="100%" cellpadding="0" cellspacing="0" style="background:#f8faf8;border:1px solid #d4e8d4;border-radius:10px;margin-bottom:20px">
        <tr><td style="padding:16px 20px">
          <p style="margin:0 0 6px;color:#888;font-size:11px;text-transform:uppercase;letter-spacing:1px">Codigo de referencia</p>
          <p style="margin:0;color:#145332;font-size:18px;font-weight:700;font-family:'JetBrains Mono',monospace">${escapeHtml(payload.tokenHint)}</p>
        </td></tr>
      </table>

      <p style="margin:0;color:#999;font-size:12px;line-height:1.5">Este codigo es <strong>personal e intransferible</strong>. Su uso por terceros constituye una violacion al reglamento electoral.</p>
    </td>
  </tr>

  <tr>
    <td style="background:#f8faf8;border-top:1px solid #e5e7eb;padding:16px 32px;text-align:center">
      <p style="margin:0;color:#999;font-size:11px">Este mensaje es confidencial y esta protegido por la <strong>Ley 1581 de 2012</strong> de proteccion de datos personales.</p>
      <p style="margin:6px 0 0;color:#bbb;font-size:10px">Universidad Popular del Cesar — ABIS-UPC &copy; 2026</p>
    </td>
  </tr>

</table>
</td></tr>
</table>
</body>
</html>`;
}`

export async function enviarNotificacion(payload) {
  ensureResendConfig();

  const resend = new Resend(config.resendApiKey);
  const { data, error } = await resend.emails.send({
    from: config.resendFromEmail,
    to: [payload.to],
    subject: payload.subject,
    html: payload.body.replace(/\n/g, '<br>')
  });

  if (error) {
    const sendError = new Error(error.message || 'Resend no pudo enviar la notificacion');
    sendError.statusCode = 502;
    sendError.publicMessage = 'No fue posible enviar la notificacion por correo';
    throw sendError;
  }

  return data;
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
