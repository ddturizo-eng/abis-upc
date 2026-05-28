import { readFile, writeFile } from 'node:fs/promises';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = dirname(fileURLToPath(import.meta.url));
const TEMPLATES_DIR = join(__dirname, '..', 'templates');

const TIPOS_VALIDOS = new Set([
  'certificado_voto',
  'carta_designacion',
  'acta_ganadores'
]);

function validarTipo(tipo) {
  if (!TIPOS_VALIDOS.has(tipo)) {
    throw new Error(`Tipo de plantilla no valido: ${tipo}`);
  }
}

export async function leer(tipo) {
  validarTipo(tipo);
  const filePath = join(TEMPLATES_DIR, `${tipo}.html`);
  try {
    return await readFile(filePath, 'utf-8');
  } catch (err) {
    if (err.code === 'ENOENT') return '<p>Plantilla vacia</p>';
    throw err;
  }
}

export async function guardar(tipo, html) {
  validarTipo(tipo);
  const filePath = join(TEMPLATES_DIR, `${tipo}.html`);

  try {
    const actual = await readFile(filePath, 'utf-8');
    await writeFile(filePath + '.bak', actual, 'utf-8');
  } catch (_) {}

  await writeFile(filePath, html, 'utf-8');
}

export async function leerConfig(tipo) {
  validarTipo(tipo);
  const filePath = join(TEMPLATES_DIR, `${tipo}.config.json`);
  try {
    const raw = await readFile(filePath, 'utf-8');
    return JSON.parse(raw);
  } catch (err) {
    if (err.code === 'ENOENT') return { zonas: {} };
    throw err;
  }
}

export async function guardarConfig(tipo, config) {
  validarTipo(tipo);
  const filePath = join(TEMPLATES_DIR, `${tipo}.config.json`);

  try {
    const actual = await readFile(filePath, 'utf-8');
    await writeFile(filePath + '.bak', actual, 'utf-8');
  } catch (_) {}

  await writeFile(filePath, JSON.stringify(config, null, 2), 'utf-8');
}

function estiloZona(zona) {
  const estilo = [];
  if (zona.color_fondo) estilo.push(`background-color:${zona.color_fondo}`);
  if (zona.color_texto) estilo.push(`color:${zona.color_texto}`);
  if (zona.color_borde) estilo.push(`border-color:${zona.color_borde}`);
  if (zona.alineacion) estilo.push(`text-align:${zona.alineacion}`);
  if (zona.tamanio_fuente) estilo.push(`font-size:${zona.tamanio_fuente}`);
  if (zona.padding) estilo.push(`padding:${zona.padding}`);
  if (zona.mayusculas) estilo.push('text-transform:uppercase');
  return estilo.join(';');
}

export async function renderizarConConfig(tipo, variables, config) {
  let html = await leer(tipo);

  for (const [nombreZona, zona] of Object.entries(config.zonas || {})) {
    html = html.replaceAll(`{{texto_${nombreZona}}}`, zona.texto || '');
    html = html.replaceAll(`{{estilo_${nombreZona}}}`, estiloZona(zona));
    html = html.replaceAll(`{{estilo_color_borde_${nombreZona}}}`, zona.color_borde || '#1a6b3c');

    if (zona.mostrar_badge && zona.texto_badge) {
      const badgeHtml = `<span class="badge-oficial">${zona.texto_badge}</span>`;
      html = html.replaceAll(`{{badge_${nombreZona}}}`, badgeHtml);
    } else {
      html = html.replaceAll(`{{badge_${nombreZona}}}`, '');
    }
  }

  for (const [key, value] of Object.entries(variables)) {
    html = html.replaceAll(`{{${key}}}`, value != null ? String(value) : '');
  }
  return html;
}

export async function renderizar(tipo, variables) {
  const config = await leerConfig(tipo);
  return await renderizarConConfig(tipo, variables, config);
}

export function tipos() {
  return [...TIPOS_VALIDOS];
}
