function escapeHtml(value) {
  return String(value ?? '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');
}

import { getLogosDataUri } from '../utils/logos.js';

function formatDate(value) {
  const date = new Date(value);
  if (isNaN(date.getTime())) return escapeHtml(value);
  return new Intl.DateTimeFormat('es-CO', {
    day: 'numeric', month: 'long', year: 'numeric', timeZone: 'America/Bogota'
  }).format(date);
}

function formatTime(value) {
  const date = new Date(value);
  if (isNaN(date.getTime())) return '--';
  return new Intl.DateTimeFormat('es-CO', {
    hour: '2-digit', minute: '2-digit', hour12: true, timeZone: 'America/Bogota'
  }).format(date);
}

function firmasConfig() {
  return {
    firma1: process.env.ABIS_CERT_FIRMA1 || 'Amilkar Sierra',
    cargo1: process.env.ABIS_CERT_CARGO1 || 'Docente de Bases de Datos',
    firma2: process.env.ABIS_CERT_FIRMA2 || 'Alfredo Bautista',
    cargo2: process.env.ABIS_CERT_CARGO2 || 'Asesor y Supervisor de Desarrollo'
  };
}

export function mapearVariablesCertificado(payload) {
  const firmas = firmasConfig();
  const logos = getLogosDataUri();

  const watermarks = `
    <img style="position:fixed;top:50%;left:50%;transform:translate(-50%,-50%);opacity:0.04;pointer-events:none;z-index:-1;max-width:360px" src="${logos.simboloUPC}" alt="">
    <img style="position:fixed;bottom:30px;left:30px;opacity:0.06;pointer-events:none;z-index:-1;width:80px" src="${logos.abisLogo}" alt="">
  `;

  return {
    nombre_votante: escapeHtml(payload.nombre).toUpperCase(),
    identificacion: escapeHtml(payload.identificacion),
    fecha: formatDate(payload.fechaVoto),
    hora: formatTime(payload.fechaVoto),
    codigo_verificacion: escapeHtml(payload.codigoCertificado),
    nombre_eleccion: escapeHtml(payload.nombreEleccion),
    puesto_votacion: escapeHtml(payload.nombrePuesto),
    sede: escapeHtml(payload.sede),
    ciudad: escapeHtml(payload.ciudad),
    firma1: escapeHtml(firmas.firma1),
    cargo1: escapeHtml(firmas.cargo1),
    firma2: escapeHtml(firmas.firma2),
    cargo2: escapeHtml(firmas.cargo2),
    watermarks: watermarks,
    qr_code_img: payload._qrDataUri
      ? `<img src="${payload._qrDataUri}" alt="QR de verificacion" width="120" height="120">`
      : ''
  };
}

export function renderCertificadoHtml(payload, qrDataUri) {
  const qrPayload = { ...payload, _qrDataUri: qrDataUri };
  return mapearVariablesCertificado(qrPayload);
}
