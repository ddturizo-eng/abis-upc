import * as templateService from '../services/templateService.js';

const DATOS_DUMMY = {
  certificado_voto: {
    nombre_votante: 'DANIEL TURIZO',
    identificacion: '1234567890',
    fecha: '26 de mayo de 2026',
    hora: '10:35 AM',
    codigo_verificacion: 'ABIS-A3F9B2C1D4',
    nombre_eleccion: 'Eleccion Consejo Estudiantil 2026',
    nombre_puesto: 'Bloque A — Salon 201',
    sede: 'SEDE CENTRAL',
    ciudad: 'Valledupar'
  },
  carta_designacion: {
    nombre_jurado: 'DANIEL TURIZO',
    identificacion: '1234567890',
    cargo: 'Presidente',
    nombre_mesa: 'Mesa 01',
    nombre_eleccion: 'Eleccion Consejo Estudiantil 2026',
    fecha: '26 de mayo de 2026',
    sede: 'SEDE CENTRAL',
    ciudad: 'Valledupar'
  },
  qr_contingencia: {
    nombre: 'Daniel Turizo',
    identificacion: '1234567890',
    token_hint: 'CT-ABCD1234',
    nombre_eleccion: 'Eleccion Consejo Estudiantil 2026'
  },
  acta_ganadores: {
    nombre_eleccion: 'Eleccion Consejo Estudiantil 2026',
    fecha: '26 de mayo de 2026',
    candidatos: [
      { nombre: 'Candidato 1', cargo: 'Rector', votos: 150, porcentaje: 45.5, ganador: true },
      { nombre: 'Candidato 2', cargo: 'Rector', votos: 100, porcentaje: 30.3, ganador: false }
    ],
    total_votos: 330,
    firma1: 'Amilkar Sierra',
    cargo1: 'Docente de Bases de Datos',
    firma2: 'Alfredo Bautista',
    cargo2: 'Asesor y Supervisor de Desarrollo'
  }
};

export async function obtener(req, res, next) {
  try {
    const html = await templateService.leer(req.params.tipo);
    res.json({ tipo: req.params.tipo, html });
  } catch (err) {
    res.status(400).json({ error: err.message });
  }
}

export async function guardar(req, res, next) {
  try {
    const { html } = req.body;
    if (!html && html !== '') {
      return res.status(400).json({ error: 'Campo html requerido' });
    }
    await templateService.guardar(req.params.tipo, html);
    res.json({ ok: true, tipo: req.params.tipo });
  } catch (err) {
    res.status(400).json({ error: err.message });
  }
}

export async function preview(req, res, next) {
  try {
    const variables = DATOS_DUMMY[req.params.tipo] || {};
    const config = (req.body && req.body.config) ? req.body.config : null;
    let html;
    if (config) {
      html = await templateService.renderizarConConfig(req.params.tipo, variables, config);
    } else {
      html = await templateService.renderizar(req.params.tipo, variables);
    }
    res.setHeader('Content-Type', 'text/html; charset=utf-8');
    res.send(html);
  } catch (err) {
    res.status(400).json({ error: err.message });
  }
}

export async function tipos(req, res) {
  res.json({ tipos: templateService.tipos() });
}

export async function obtenerConfig(req, res, next) {
  try {
    const config = await templateService.leerConfig(req.params.tipo);
    res.json({ tipo: req.params.tipo, config });
  } catch (err) {
    res.status(400).json({ error: err.message });
  }
}

export async function guardarConfig(req, res, next) {
  try {
    const { config } = req.body;
    if (!config) {
      return res.status(400).json({ error: 'Campo config requerido' });
    }
    await templateService.guardarConfig(req.params.tipo, config);
    res.json({ ok: true, tipo: req.params.tipo });
  } catch (err) {
    res.status(400).json({ error: err.message });
  }
}
