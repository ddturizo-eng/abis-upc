import puppeteer from 'puppeteer';
import QRCode from 'qrcode';
import { renderCertificadoHtml } from '../templates/certificadoTemplate.js';
import { renderActaHtml } from '../templates/actaTemplate.js';

let browserPromise = null;

function getBrowser() {
  if (!browserPromise) {
    browserPromise = puppeteer.launch({
      headless: 'new',
      args: ['--no-sandbox', '--disable-setuid-sandbox']
    });
  }
  return browserPromise;
}

export async function generarCertificadoPdf(payload) {
  const qrDataUri = await QRCode.toDataURL(
    `https://abis-upc.vercel.app/verificar/${payload.codigoCertificado}`,
    { width: 180, margin: 2, color: { dark: '#1a3a2a', light: '#ffffff' } }
  );

  const browser = await getBrowser();
  const page = await browser.newPage();
  try {
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
    await page.close();
  }
}

export async function generarActaPdf(payload) {
  const browser = await getBrowser();
  const page = await browser.newPage();
  try {
    await page.setContent(renderActaHtml(payload), {
      waitUntil: 'networkidle0'
    });

    return await page.pdf({
      format: 'Letter',
      printBackground: true,
      preferCSSPageSize: true
    });
  } finally {
    await page.close();
  }
}

export async function shutdownBrowser() {
  if (browserPromise) {
    const browser = await browserPromise;
    await browser.close();
    browserPromise = null;
  }
}
