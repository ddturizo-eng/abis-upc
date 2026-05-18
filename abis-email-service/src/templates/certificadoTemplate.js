function escapeHtml(value) {
  return String(value ?? '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#039;');
}

function parsedDate(value) {
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? null : date;
}

function formatDate(value) {
  const date = parsedDate(value);
  if (!date) {
    return escapeHtml(value);
  }
  return new Intl.DateTimeFormat('es-CO', {
    day: 'numeric',
    month: 'long',
    year: 'numeric',
    timeZone: 'America/Bogota'
  }).format(date);
}

function formatTime(value) {
  const date = parsedDate(value);
  if (!date) {
    return '--';
  }
  return new Intl.DateTimeFormat('es-CO', {
    hour: '2-digit',
    minute: '2-digit',
    hour12: true,
    timeZone: 'America/Bogota'
  }).format(date);
}

export function renderCertificadoHtml(payload) {
  const nombre = escapeHtml(payload.nombre).toUpperCase();
  const identificacion = escapeHtml(payload.identificacion);
  const eleccion = escapeHtml(payload.nombreEleccion);
  const fecha = formatDate(payload.fechaVoto);
  const hora = formatTime(payload.fechaVoto);
  const codigo = escapeHtml(payload.codigoCertificado);
  const puesto = escapeHtml(payload.nombrePuesto);
  const sede = escapeHtml(payload.sede);
  const ciudad = escapeHtml(payload.ciudad);

  return `<!doctype html>
<html lang="es">
<head>
  <meta charset="utf-8">
  <title>Certificado de Participacion Electoral ABIS-UPC</title>
  <style>
    @page {
      size: letter landscape;
      margin: 18px;
    }

    * {
      box-sizing: border-box;
    }

    body {
      margin: 0;
      color: #1f2925;
      font-family: Georgia, 'Times New Roman', serif;
      background: #f8faf7;
    }

    .certificate {
      min-height: 576px;
      border: 4px double #284c4d;
      padding: 44px 58px 34px;
      background:
        radial-gradient(circle at 0 42%, rgba(0, 77, 51, 0.05) 0 26%, transparent 27%),
        radial-gradient(circle at 90% 62%, rgba(0, 77, 51, 0.045) 0 28%, transparent 29%),
        linear-gradient(180deg, #ffffff 0%, #fbfdfb 100%);
      position: relative;
      overflow: hidden;
    }

    .certificate::before,
    .certificate::after {
      content: "";
      position: absolute;
      left: 30px;
      right: 30px;
      height: 18px;
      border-top: 2px solid #284c4d;
      border-bottom: 1px solid #284c4d;
      opacity: 0.75;
    }

    .certificate::before {
      top: 18px;
    }

    .certificate::after {
      bottom: 18px;
    }

    .ornament {
      width: 380px;
      height: 1px;
      margin: 0 auto 14px;
      background: linear-gradient(90deg, transparent, #075521 22%, #075521 78%, transparent);
      position: relative;
    }

    .ornament::after {
      content: "ABIS UPC";
      position: absolute;
      left: 50%;
      top: -10px;
      transform: translateX(-50%);
      padding: 0 16px;
      background: #ffffff;
      color: #075521;
      font-family: Arial, Helvetica, sans-serif;
      font-size: 11px;
      font-weight: 700;
      letter-spacing: 0;
    }

    .title {
      margin: 8px auto 0;
      max-width: 820px;
      color: #075521;
      text-align: center;
      font-size: 47px;
      line-height: 1.08;
      font-weight: 700;
      letter-spacing: 0;
      text-transform: uppercase;
      text-shadow: 0 1px 0 rgba(0, 0, 0, 0.08);
    }

    .intro {
      margin: 28px 0 0;
      text-align: center;
      font-size: 18px;
      letter-spacing: 0;
    }

    .name {
      margin: 18px auto 6px;
      width: fit-content;
      max-width: 900px;
      padding: 0 24px 8px;
      border-bottom: 2px solid #7b8a66;
      color: #075521;
      text-align: center;
      font-size: 36px;
      line-height: 1.15;
      font-weight: 700;
      text-transform: uppercase;
    }

    .id {
      margin: 0;
      color: #075521;
      text-align: center;
      font-size: 18px;
      font-family: Arial, Helvetica, sans-serif;
    }

    .description {
      margin: 16px auto 0;
      max-width: 760px;
      text-align: center;
      color: #303833;
      font-size: 17px;
      line-height: 1.45;
    }

    .badge {
      width: fit-content;
      margin: 26px auto 0;
      padding: 9px 24px;
      border-radius: 5px;
      color: #ffffff;
      background: linear-gradient(180deg, #14733b, #064f22);
      box-shadow: 0 3px 0 rgba(0, 0, 0, 0.14);
      font-family: Arial, Helvetica, sans-serif;
      font-size: 14px;
      font-weight: 700;
      letter-spacing: 1px;
      text-transform: uppercase;
    }

    .facts {
      display: grid;
      grid-template-columns: 1.35fr 1fr 1fr 1.35fr;
      gap: 0;
      margin-top: 30px;
      border-top: 1px solid rgba(7, 85, 33, 0.2);
      border-bottom: 1px dashed rgba(7, 85, 33, 0.45);
      padding: 17px 0;
      font-family: Arial, Helvetica, sans-serif;
    }

    .fact {
      min-height: 58px;
      padding: 0 20px;
      border-right: 1px solid rgba(7, 85, 33, 0.35);
    }

    .fact:last-child {
      border-right: 0;
    }

    .fact-label {
      margin: 0 0 6px;
      color: #1b2420;
      font-size: 12px;
      text-transform: uppercase;
    }

    .fact-value {
      margin: 0;
      color: #075521;
      font-size: 15px;
      line-height: 1.28;
      font-weight: 700;
    }

    .footer {
      display: grid;
      grid-template-columns: 1.25fr 1fr 1.25fr;
      align-items: end;
      gap: 26px;
      margin-top: 24px;
      font-family: Arial, Helvetica, sans-serif;
    }

    .verify {
      color: #28312d;
      font-size: 12px;
      line-height: 1.45;
    }

    .verify strong {
      display: block;
      margin-top: 6px;
      color: #075521;
      font-size: 13px;
    }

    .brand {
      text-align: center;
      color: #075521;
      font-weight: 700;
      font-size: 18px;
      line-height: 1.1;
    }

    .brand span {
      display: block;
      color: #2d3a35;
      font-weight: 400;
      font-size: 12px;
      margin-top: 5px;
    }

    .advisor {
      text-align: center;
      color: #1b2420;
      font-size: 12px;
    }

    .advisor-line {
      width: 230px;
      margin: 0 auto 8px;
      border-top: 1px solid #075521;
    }

    .advisor strong {
      display: block;
      color: #075521;
      font-size: 14px;
    }

    .bottom-ribbon {
      position: absolute;
      left: 50%;
      bottom: 6px;
      transform: translateX(-50%);
      width: 310px;
      padding: 6px 0 5px;
      border: 1px solid #0d6b34;
      background: #ffffff;
      color: #075521;
      text-align: center;
      font-family: Arial, Helvetica, sans-serif;
      font-size: 15px;
      font-weight: 700;
      letter-spacing: 0;
    }

    .bottom-ribbon span {
      display: block;
      margin-top: 2px;
      color: #28312d;
      font-size: 10px;
      font-weight: 400;
    }
  </style>
</head>
<body>
  <main class="certificate">
    <div class="ornament"></div>
    <h1 class="title">Certificado de<br>Participacion Electoral</h1>
    <p class="intro">La Universidad Popular del Cesar certifica que:</p>

    <p class="name">${nombre}</p>
    <p class="id">Identificacion: ${identificacion}</p>

    <p class="description">
      Participo de manera activa y democratica en la jornada electoral para
      <strong>${eleccion}</strong>, ejerciendo su derecho al voto.
    </p>

    <div class="badge">Participacion certificada</div>

    <section class="facts">
      <article class="fact">
        <p class="fact-label">Puesto de votacion</p>
        <p class="fact-value">${puesto}<br>${sede}<br>${ciudad}</p>
      </article>
      <article class="fact">
        <p class="fact-label">Fecha de votacion</p>
        <p class="fact-value">${fecha}</p>
      </article>
      <article class="fact">
        <p class="fact-label">Hora de votacion</p>
        <p class="fact-value">${hora}</p>
      </article>
      <article class="fact">
        <p class="fact-label">Codigo de certificado</p>
        <p class="fact-value">${codigo}</p>
      </article>
    </section>

    <footer class="footer">
      <section class="verify">
        Este certificado acredita participacion, no contiene informacion del candidato seleccionado
        y respeta el principio de secreto del sufragio.
        <strong>Validacion publica pendiente de implementacion</strong>
      </section>
      <section class="brand">
        UNIVERSIDAD<br>Popular del Cesar
        <span>Sistema de Votacion Inteligente y Seguro</span>
      </section>
      <section class="advisor">
        <div class="advisor-line"></div>
        <strong>Alfredo Bautista</strong>
        Asesor y supervisor de desarrollo
      </section>
    </footer>

    <div class="bottom-ribbon">ABIS UPC<span>Sistema de Votacion Inteligente y Seguro</span></div>
  </main>
</body>
</html>`;
}
