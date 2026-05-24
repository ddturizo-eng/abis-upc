import puppeteer from 'puppeteer';
import { renderCertificadoHtml } from '../templates/certificadoTemplate.js';

export async function generarCertificadoPdf(payload) {
  const browser = await puppeteer.launch({
    headless: 'new',
    args: ['--no-sandbox', '--disable-setuid-sandbox']
  });

  try {
    const page = await browser.newPage();
    await page.setContent(renderCertificadoHtml(payload), {
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
