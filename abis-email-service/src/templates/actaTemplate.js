function escapeHtml(value) {
  return String(value || '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');
}

function firmasConfig() {
  return {
    firma1: process.env.ABIS_CERT_FIRMA1 || 'Amilkar Sierra',
    cargo1: process.env.ABIS_CERT_CARGO1 || 'Docente de Bases de Datos',
    firma2: process.env.ABIS_CERT_FIRMA2 || 'Alfredo Bautista',
    cargo2: process.env.ABIS_CERT_CARGO2 || 'Asesor y Supervisor de Desarrollo'
  };
}

export function renderActaHtml(payload) {
  const nombre = escapeHtml(payload.nombre);
  const fecha = escapeHtml(payload.fecha);
  const totalVotos = Number(payload.totalVotos || 0);
  const candidatos = (payload.candidatos || []);
  const votoBlanco = payload.votoBlanco || {};
  const envFirmas = firmasConfig();
  const firmas = payload.firmas || [
    { nombre: envFirmas.firma1, cargo: envFirmas.cargo1 },
    { nombre: envFirmas.firma2, cargo: envFirmas.cargo2 }
  ];

  const filas = candidatos.map(c => {
    const nombreC = escapeHtml(c.nombre);
    const cargo = escapeHtml(c.cargo);
    const votos = Number(c.votos || 0);
    const porcentaje = totalVotos > 0 ? ((votos / totalVotos) * 100).toFixed(1) : '0.0';
    const esGanador = c.ganador === true;
    return `<tr class="${esGanador ? 'winner' : ''}">
      <td>${nombreC}</td>
      <td>${cargo}</td>
      <td class="num">${votos.toFixed(1)}</td>
      <td class="num">${porcentaje}%</td>
    </tr>`;
  }).join('');

  let filaBlanco = '';
  if (votoBlanco.votos > 0) {
    const vBlanco = Number(votoBlanco.votos);
    const pBlanco = totalVotos > 0 ? ((vBlanco / totalVotos) * 100).toFixed(1) : '0.0';
    filaBlanco = `<tr>
      <td>Voto en blanco</td>
      <td>VOTO EN BLANCO</td>
      <td class="num">${vBlanco.toFixed(1)}</td>
      <td class="num">${pBlanco}%</td>
    </tr>`;
  }

  const firmasHtml = firmas.map(f => `
    <div class="firma-box">
      <div class="firma-line"></div>
      <strong>${escapeHtml(f.nombre)}</strong>
      <span>${escapeHtml(f.cargo)}</span>
    </div>
  `).join('');

  return `<!doctype html>
<html lang="es">
<head>
  <meta charset="utf-8">
  <title>Acta de Ganadores — ${nombre}</title>
  <style>
    * { margin: 0; padding: 0; box-sizing: border-box; }
    body {
      font-family: Georgia, 'Segoe UI', serif;
      color: #145332;
      max-width: 800px;
      margin: 0 auto;
      padding: 42px 28px;
    }

    .watermark {
      position: fixed;
      top: 50%;
      left: 50%;
      transform: translate(-50%, -50%);
      opacity: 0.04;
      font-size: 180px;
      font-weight: 900;
      pointer-events: none;
      z-index: -1;
      color: #145332;
    }

    .header {
      text-align: center;
      border-bottom: 3px double #1a6b3c;
      padding-bottom: 22px;
      margin-bottom: 28px;
    }

    .header .logos {
      display: flex;
      justify-content: center;
      align-items: center;
      gap: 32px;
      margin-bottom: 16px;
    }

    .header .logos img {
      height: 56px;
    }

    .header h1 {
      font-size: 1.6rem;
      font-weight: 800;
      color: #145332;
      letter-spacing: 0.04em;
      text-transform: uppercase;
    }

    .header .badge {
      display: inline-block;
      background: #145332;
      color: #fff;
      border-radius: 6px;
      padding: 5px 16px;
      font-size: 0.7rem;
      font-weight: 700;
      margin-top: 8px;
      letter-spacing: 1px;
      text-transform: uppercase;
    }

    .header .meta {
      color: #6b7280;
      font-size: 0.82rem;
      margin-top: 6px;
    }

    table {
      width: 100%;
      border-collapse: collapse;
      margin: 20px 0;
      font-size: 0.82rem;
    }

    th {
      background: #f0f4f2;
      border-bottom: 2px solid #1a6b3c;
      color: #145332;
      font-size: 0.68rem;
      font-weight: 700;
      letter-spacing: 0.05em;
      padding: 10px 10px;
      text-align: left;
      text-transform: uppercase;
    }

    td {
      border-bottom: 1px solid #e5e7eb;
      padding: 9px 10px;
    }

    td.num {
      text-align: right;
      font-family: 'JetBrains Mono', monospace;
    }

    tr.winner td {
      background: #f0fdf4;
      font-weight: 700;
    }

    tr.winner td:first-child::before {
      content: "★ ";
      color: #158759;
    }

    .totals {
      background: #f8faf9;
      border: 1px solid #d4e8d4;
      border-radius: 8px;
      padding: 14px 20px;
      margin-top: 16px;
      display: flex;
      gap: 32px;
    }

    .totals div strong {
      display: block;
      font-size: 1.1rem;
      color: #145332;
    }

    .totals div small {
      color: #6b7280;
      font-size: 0.68rem;
    }

    .firmas {
      display: flex;
      justify-content: center;
      gap: 64px;
      margin-top: 42px;
      padding-top: 20px;
      border-top: 1px solid #e5e7eb;
    }

    .firma-box {
      text-align: center;
      width: 180px;
    }

    .firma-line {
      border-top: 1px solid #145332;
      margin: 0 auto 8px;
      width: 100%;
    }

    .firma-box strong {
      display: block;
      color: #145332;
      font-size: 12px;
      margin-bottom: 2px;
    }

    .firma-box span {
      color: #6b7280;
      font-size: 10px;
    }

    .footer {
      border-top: 1px solid #eee;
      margin-top: 32px;
      padding-top: 16px;
      text-align: center;
      color: #9ca3af;
      font-size: 0.7rem;
    }

    .qr-section {
      margin: 18px auto 0;
      text-align: center;
    }

    .qr-section .qr-code { margin-bottom: 4px; }

    .qr-label {
      color: #145332;
      font-size: 9px;
      font-weight: 600;
      text-transform: uppercase;
      letter-spacing: 1px;
    }
  </style>
</head>
<body>
  <div class="watermark">UP</div>

  <div class="header">
    <h1>Acta de Ganadores</h1>
    <p class="meta"><strong>${nombre}</strong></p>
    <p class="meta">Fecha de generacion: ${fecha}</p>
    <span class="badge">Documento oficial</span>
  </div>

  <table>
    <thead>
      <tr>
        <th>Candidato</th>
        <th>Cargo</th>
        <th>Votos ponderados</th>
        <th>Porcentaje</th>
      </tr>
    </thead>
    <tbody>
      ${filas}
      ${filaBlanco}
    </tbody>
  </table>

  <div class="totals">
    <div>
      <small>Candidatos</small>
      <strong>${candidatos.length + (votoBlanco.votos > 0 ? 1 : 0)}</strong>
    </div>
    <div>
      <small>Total votos ponderados</small>
      <strong>${totalVotos.toFixed(1)}</strong>
    </div>
  </div>

  <div class="firmas">
    ${firmasHtml}
  </div>

  <div class="footer">
    Universidad Popular del Cesar — ABIS-UPC &copy; ${new Date().getFullYear()}<br>
    Documento generado electronicamente. Este documento es oficial y tiene validez institucional.
  </div>
</body>
</html>`;
}
