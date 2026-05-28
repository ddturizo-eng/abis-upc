import { getLogosDataUri } from '../utils/logos.js';

function escapeHtml(value) {
  return String(value || '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');
}

function formatDate(value) {
  if (!value) return '';
  const d = new Date(value);
  if (isNaN(d)) return String(value);
  return new Intl.DateTimeFormat('es-CO', { day:'numeric', month:'long', year:'numeric', timeZone:'America/Bogota' }).format(d);
}

function firmasConfig() {
  return {
    firma1: process.env.ABIS_CERT_FIRMA1 || 'Amilkar Sierra',
    cargo1: process.env.ABIS_CERT_CARGO1 || 'Docente de Bases de Datos',
    firma2: process.env.ABIS_CERT_FIRMA2 || 'Alfredo Bautista',
    cargo2: process.env.ABIS_CERT_CARGO2 || 'Asesor y Supervisor de Desarrollo'
  };
}

export function mapearVariablesActa(payload) {
  const totalVotos = Number(payload.totalVotos || 0);
  const candidatos = (payload.candidatos || []);
  const votoBlanco = payload.votoBlanco || {};
  const vBlanco = Number(votoBlanco.votos || 0);
  const envFirmas = firmasConfig();
  const firmas = payload.firmas || [
    { nombre: envFirmas.firma1, cargo: envFirmas.cargo1 },
    { nombre: envFirmas.firma2, cargo: envFirmas.cargo2 }
  ];
  const logos = getLogosDataUri();

  const cargos = {};
  candidatos.forEach(function(c) {
    var cargo = c.cargo || 'Sin cargo';
    if (!cargos[cargo]) cargos[cargo] = [];
    cargos[cargo].push(c);
  });
  if (vBlanco > 0) {
    if (!cargos['VOTO EN BLANCO']) cargos['VOTO EN BLANCO'] = [];
    cargos['VOTO EN BLANCO'].push({ nombre: 'Voto en blanco', cargo: 'VOTO EN BLANCO', votos: vBlanco, ganador: false });
  }

  var tablaHtml = Object.keys(cargos).map(function(cargo) {
    var items = cargos[cargo].sort(function(a, b) { return (b.votos || 0) - (a.votos || 0); });
    var rows = items.map(function(c, i) {
      var votos = Number(c.votos || 0);
      var pct = totalVotos > 0 ? ((votos / totalVotos) * 100).toFixed(1) : '0.0';
      var esGanador = i === 0 && cargo !== 'VOTO EN BLANCO';
      return '<tr' + (esGanador ? ' class="winner"' : '') + '>'
        + '<td>' + (esGanador ? '&#9733; ' : '') + escapeHtml(c.nombre) + '</td>'
        + '<td>' + escapeHtml(cargo) + '</td>'
        + '<td style="text-align:right">' + votos.toFixed(1) + '</td>'
        + '<td style="text-align:right">' + pct + '%</td>'
        + '</tr>';
    }).join('');
    return rows;
  }).join('');

  var firmasHtml = firmas.map(function(f) {
    return '<div class="firma"><div class="firma-linea"></div><p class="nombre">' + escapeHtml(f.nombre) + '</p><p class="cargo">' + escapeHtml(f.cargo) + '</p></div>';
  }).join('');

  return {
    nombre_eleccion: escapeHtml(payload.nombre),
    fecha_eleccion: formatDate(payload.fecha) || escapeHtml(payload.fecha),
    sede: escapeHtml(payload.sede || 'SEDE CENTRAL'),
    ciudad: escapeHtml(payload.ciudad || 'Valledupar'),
    total_votos: totalVotos.toFixed(1),
    total_candidatos: candidatos.length + (vBlanco > 0 ? 1 : 0),
    votos_blanco: vBlanco.toFixed(1),
    porcentaje_participacion: '—',
    tabla_resultados: tablaHtml,
    firmas_html: firmasHtml,
    fecha_emision: new Date().toLocaleDateString('es-CO', { day:'numeric', month:'long', year:'numeric' }),
    codigo_verificacion: 'ABIS-' + (payload.nombre || 'ACTA').replace(/\s/g, '').substring(0, 8).toUpperCase(),
    watermark_url: logos.simboloUPC,
    logo_upc_url: logos.simboloUPC,
    logo_abis_url: logos.abisLogo
  };
}

export function renderActaHtml(payload) {
  return mapearVariablesActa(payload);
}
