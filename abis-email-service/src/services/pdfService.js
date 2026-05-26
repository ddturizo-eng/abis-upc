import puppeteer from 'puppeteer';
import QRCode from 'qrcode';
import { renderCertificadoHtml } from '../templates/certificadoTemplate.js';

export async function generarCertificadoPdf(payload) {
  const qrDataUri = await QRCode.toDataURL(
    `https://abis-upc.vercel.app/verificar/${payload.codigoCertificado}`,
    { width: 180, margin: 2, color: { dark: '#1a3a2a', light: '#ffffff' } }
  );

  const browser = await puppeteer.launch({
    headless: 'new',
    args: ['--no-sandbox', '--disable-setuid-sandbox']
  });

  try {
    const page = await browser.newPage();
    await page.setContent(renderCertificadoHtml(payload, qrDataUri), {
      waitUntil: 'networkidle0'
    });

    return await page.pdf({
      format: 'Letter',
      landscape: true,
      printBackground: true,
      preferCSSPageSize: true
    });
  } finally {
    await browser.close();
  }
}
