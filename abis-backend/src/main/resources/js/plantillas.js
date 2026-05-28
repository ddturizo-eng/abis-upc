var EMAIL_SERVICE = window._pltService || 'http://localhost:8010';
var INTERNAL_TOKEN = window._pltToken || '0b819503b19749e9b7a3355d56357854';
window._pltService = EMAIL_SERVICE;
window._pltToken = INTERNAL_TOKEN;

var PlantillasAdmin = window.PlantillasAdmin || {
  tipoActual: null,
  configOriginal: null,
  templateHtml: '',
  saved: false,

  info: {
    certificado_voto: { titulo: 'Certificado de voto', icono: 'verified_user', badge: 'En produccion', prod: true,
      variables: ['nombre_votante','identificacion','fecha','hora','codigo_verificacion','nombre_eleccion','puesto_votacion','sede','ciudad','firma1','cargo1','firma2','cargo2'],
      dummy: { nombre_votante:'DANIEL TURIZO', identificacion:'1234567890', fecha:'26 de mayo de 2026', hora:'10:35 AM', codigo_verificacion:'ABIS-A3F9B2C1D4', nombre_eleccion:'Eleccion Consejo Estudiantil 2026', puesto_votacion:'Bloque A Salon 201', sede:'SEDE CENTRAL', ciudad:'Valledupar', firma1:'Amilkar Sierra', cargo1:'Docente', firma2:'Alfredo Bautista', cargo2:'Asesor', watermarks:'', qr_code_img:'<div style="text-align:center;margin:8px 0;color:#888;font-size:11px">[QR de verificacion]</div>' } },
    carta_designacion: { titulo: 'Carta de designacion', icono: 'assignment_ind', badge: 'Pendiente', prod: false,
      variables: ['nombre_jurado','identificacion','cargo_jurado','nombre_mesa','nombre_eleccion','fecha','sede','ciudad','firma1','cargo1','firma2','cargo2'],
      dummy: { nombre_jurado:'DANIEL TURIZO', identificacion:'1234567890', cargo_jurado:'Presidente', nombre_mesa:'Mesa 01', nombre_eleccion:'Eleccion Consejo Estudiantil 2026', fecha:'26 de mayo de 2026', sede:'SEDE CENTRAL', ciudad:'Valledupar', firma1:'Amilkar Sierra', cargo1:'Docente', firma2:'Alfredo Bautista', cargo2:'Asesor', anio:'2026' } },
    acta_ganadores: { titulo: 'Acta de ganadores', icono: 'trophy', badge: 'En produccion', prod: true,
      variables: ['nombre_eleccion','fecha','total_votos','total_candidatos','anio'],
      dummy: { nombre_eleccion:'Eleccion Consejo Estudiantil 2026', fecha:'26 de mayo de 2026', total_votos:'330.0', total_candidatos:'3', anio:'2026', sede:'SEDE CENTRAL', ciudad:'Valledupar', porcentaje_participacion:'67', votos_blanco:'80.0', fecha_eleccion:'26 de mayo de 2026',
        tabla_resultados:'<table style="width:100%;border-collapse:collapse;font-size:10pt"><thead><tr><th style="background:#1a5c38;color:#fff;padding:6px 8px;text-align:left">Candidato</th><th style="background:#1a5c38;color:#fff;padding:6px 8px;text-align:left">Cargo</th><th style="background:#1a5c38;color:#fff;padding:6px 8px;text-align:right">Votos</th><th style="background:#1a5c38;color:#fff;padding:6px 8px;text-align:right">%</th></tr></thead><tbody><tr class="winner"><td>&#9733; Candidato 1</td><td>Rector</td><td style="text-align:right">150.0</td><td style="text-align:right">45.5%</td></tr><tr><td>Candidato 2</td><td>Rector</td><td style="text-align:right">100.0</td><td style="text-align:right">30.3%</td></tr><tr><td>Voto en blanco</td><td>VOTO EN BLANCO</td><td style="text-align:right">80.0</td><td style="text-align:right">24.2%</td></tr></tbody></table>',
        firmas_html:'<div class="firmas" style="display:flex;justify-content:space-around;margin-top:36px;gap:40px"><div class="firma" style="text-align:center;width:200px"><div class="firma-linea" style="border-top:1px solid #000;margin:0 auto 8px"></div><p style="font-size:10pt;margin:2px 0;text-align:center;font-weight:bold">Amilkar Sierra</p><p style="font-size:8.5pt;color:#555;margin:2px 0;text-align:center">Docente</p></div><div class="firma" style="text-align:center;width:200px"><div class="firma-linea" style="border-top:1px solid #000;margin:0 auto 8px"></div><p style="font-size:10pt;margin:2px 0;text-align:center;font-weight:bold">Alfredo Bautista</p><p style="font-size:8.5pt;color:#555;margin:2px 0;text-align:center">Asesor</p></div></div>',
        fecha_emision:'26 de mayo de 2026', codigo_verificacion:'ABIS-Eleccion'} }
  },

  init: function() {
    var self = this;
    this.cargar().then(function() {
      var tipos = document.querySelectorAll('.plt-tcard');
      if (tipos.length > 0) {
        var last = localStorage.getItem('plt_activa') || 'certificado_voto';
        var found = document.getElementById('card-' + last);
        if (found) found.click();
        else if (tipos.length > 0) tipos[0].click();
      }
    }).catch(function(e) {
      console.error('Plantillas: error init', e);
      document.getElementById('zonasEditor').innerHTML = '<p style="color:#dc2626;padding:20px">Error al iniciar plantillas</p>';
    });
    document.addEventListener('keydown', function(e) {
      if (e.ctrlKey && e.key === 's' && PlantillasAdmin.tipoActual) { e.preventDefault(); PlantillasAdmin.guardar(); }
    });
  },

  imagenABase64: async function(url) {
    try {
      var res = await fetch(url);
      if (!res.ok) return '';
      var blob = await res.blob();
      return new Promise(function(resolve) {
        var reader = new FileReader();
        reader.onloadend = function() { resolve(reader.result); };
        reader.readAsDataURL(blob);
      });
    } catch (_) { return ''; }
  },

  cargarLogos: async function() {
    var base = window.location.origin;
    this._logoUpc = await this.imagenABase64(base + '/assets/SIMBOLO-UNICESAR-2024.png');
    this._logoAbis = await this.imagenABase64(base + '/assets/img/ABIS-UPC-LOGO.png');
  },

  cargar: async function() {
    var grid = document.getElementById('plantillasGrid');
    try {
      var res = await fetch(EMAIL_SERVICE + '/api/plantillas/tipos', { headers: { 'X-Internal-Token': INTERNAL_TOKEN } });
      var data = await res.json();
      var tipos = data.tipos || [];
      var self = this;
      grid.innerHTML = tipos.map(function(tipo) {
        var info = self.info[tipo] || { titulo: tipo, icono: 'description', badge: '', prod: false };
        return '<div class="plt-tcard" onclick="PlantillasAdmin.seleccionar(\'' + tipo + '\')" id="card-' + tipo + '">'
          + '<span class="material-symbols-outlined">' + info.icono + '</span><h3>' + info.titulo + '</h3>'
          + (info.badge ? '<span class="plt-badge ' + (info.prod ? 'plt-badge-prod' : 'plt-badge-pend') + '">' + info.badge + '</span>' : '')
          + '</div>';
      }).join('');
    } catch (e) {
      grid.innerHTML = '<span style="color:#6b7280;font-size:12px">Error al cargar plantillas</span>';
    }
  },

  seleccionar: async function(tipo) {
    this.tipoActual = tipo;
    this.saved = false;
    localStorage.setItem('plt_activa', tipo);

    document.querySelectorAll('.plt-tcard').forEach(function(c) { c.classList.remove('selected'); });
    var card = document.getElementById('card-' + tipo);
    if (card) card.classList.add('selected');

    var info = this.info[tipo] || { titulo: tipo, variables: [], dummy: {} };
    var editor = document.getElementById('pltEditor');
    editor.style.display = 'block';
    this.renderVarChips(info.variables);

    var status = document.getElementById('plantillaStatus');
    var statusText = status.querySelector('span:last-child');
    if (statusText) statusText.textContent = 'Cargando...';
    status.className = 'plt-bottombar-status';

    var zonasEl = document.getElementById('zonasEditor');
    var previewEl = document.getElementById('previewContainer');

    if (!this._logoUpc) await this.cargarLogos();

    try {
      var htmlRes = await fetch(EMAIL_SERVICE + '/api/plantillas/' + tipo, { headers: { 'X-Internal-Token': INTERNAL_TOKEN } });
      if (!htmlRes.ok) throw new Error('Error al cargar HTML: ' + htmlRes.status);
      var htmlText = await htmlRes.text();
      try { var htmlData = JSON.parse(htmlText); this.templateHtml = htmlData.html || htmlText; }
      catch (_) { this.templateHtml = htmlText; }

      var cfgRes = await fetch(EMAIL_SERVICE + '/api/plantillas/' + tipo + '/config', { headers: { 'X-Internal-Token': INTERNAL_TOKEN } });
      if (!cfgRes.ok) throw new Error('Error al cargar config: ' + cfgRes.status);
      var cfgData = await cfgRes.json();
      var config = (cfgData && cfgData.config) ? cfgData.config : { zonas: {} };
      if (config.zonas && Object.keys(config.zonas).length === 0) {
        config = { zonas: { cuerpo: { texto: info.titulo + ' — sin zonas configuradas' } } };
      }
      this.configOriginal = config;

      try { this.renderZonas(this.configOriginal); }
      catch (zErr) { zonasEl.innerHTML = '<p style="color:#6b7280;padding:12px">Error en zonas: ' + (zErr.message || '') + '</p>'; }

      try { this.actualizarPreview(); }
      catch (pErr) { previewEl.innerHTML = '<p style="color:#6b7280;padding:40px;text-align:center">Error en vista previa: ' + (pErr.message || '') + '</p>'; }

      var stSuccess = status.querySelector('span:last-child');
      if (stSuccess) stSuccess.textContent = 'Cambios sin guardar';
      status.className = 'plt-bottombar-status';
    } catch (e) {
      zonasEl.innerHTML = '<p style="color:#dc2626;padding:16px">Error al cargar la plantilla.<br>' + (e.message || 'Intenta de nuevo.') + '</p>';
      previewEl.innerHTML = '<p style="color:#6b7280;padding:40px;text-align:center">No fue posible cargar la vista previa</p>';
      status.textContent = 'Error de carga';
      status.className = 'plt-bottombar-status';
      var stText2 = status.querySelector('span:last-child');
      if (stText2) stText2.textContent = 'Error de carga';
      this.templateHtml = '';
      this.configOriginal = null;
    }
  },

  cancelar: function() {
    document.getElementById('pltEditor').style.display = 'none';
    document.querySelectorAll('.plt-tcard').forEach(function(c) { c.classList.remove('selected'); });
    this.tipoActual = null; this.configOriginal = null; this.templateHtml = '';
  },

  renderVarChips: function(variables) {
    document.getElementById('pltVarsRow').innerHTML = variables.map(function(v) {
      return '<span class="plt-var-chip" onclick="PlantillasAdmin.copiarVariable(\'' + v + '\',this)">{{' + v + '}}</span>';
    }).join('');
  },

  copiarVariable: function(v, el) {
    navigator.clipboard.writeText('{{' + v + '}}').then(function() {
      var orig = el.textContent; el.textContent = 'Copiado!'; el.style.background = '#d4eedf';
      setTimeout(function() { el.textContent = orig; el.style.background = ''; }, 1000);
    });
  },

  renderZonas: function(config) {
    var zonas = (config && config.zonas) ? config.zonas : {};
    var keys = Object.keys(zonas);
    if (!keys.length) { document.getElementById('zonasEditor').innerHTML = '<p style="color:#9ca3af;padding:16px;font-size:12px">Sin zonas config</p>'; return; }
    var self = this;
    document.getElementById('zonasEditor').innerHTML = keys.map(function(nombre) {
      var z = zonas[nombre] || {};
      var texto = self.escAttr(z.texto || '');
      var colorFondo = z.color_fondo || '#ffffff', colorTexto = z.color_texto || '#333333', colorBorde = z.color_borde || '#1a6b3c';
      var tamanio = z.tamanio_fuente ? parseInt(z.tamanio_fuente) : 14, alin = z.alineacion || 'left';

      var fdo = z.color_fondo != null ? '<label title="Color de fondo">Fdo<input type="color" value="' + colorFondo + '" onchange="PlantillasAdmin.cambiar(\'' + nombre + '\',\'color_fondo\',this.value)"></label>' : '';
      var txt = z.color_texto != null ? '<label title="Color de texto">Txt<input type="color" value="' + colorTexto + '" onchange="PlantillasAdmin.cambiar(\'' + nombre + '\',\'color_texto\',this.value)"></label>' : '';
      var bor = z.color_borde != null ? '<label title="Color de borde">Bor<input type="color" value="' + colorBorde + '" onchange="PlantillasAdmin.cambiar(\'' + nombre + '\',\'color_borde\',this.value)"></label>' : '';

      return '<div class="plt-zone">'
        + '<div class="plt-zone-header">'
        + '<div class="plt-zone-header-left">'
        + '<div class="plt-zone-accent"></div>'
        + '<strong>' + nombre + '</strong>'
        + '</div>'
        + '<span class="plt-zone-badge">Activo</span>'
        + '</div>'
        + '<textarea id="zona-' + nombre + '" oninput="PlantillasAdmin.cambiar(\'' + nombre + '\',\'texto\',this.value)">' + texto + '</textarea>'
        + '<div class="plt-zone-controls">'
        + fdo + txt + bor
        + '<label title="Tamano de fuente">Tam<input type="number" value="' + tamanio + '" min="8" max="72" onchange="PlantillasAdmin.cambiar(\'' + nombre + '\',\'tamanio_fuente\',this.value+\'px\')"></label>'
        + '<div class="plt-toggle-group">'
        + '<button class="plt-toggle-btn' + (alin==='left'?' active':'') + '" onclick="PlantillasAdmin.setAlin(\'' + nombre + '\',\'left\',this)">Izq</button>'
        + '<button class="plt-toggle-btn' + (alin==='center'?' active':'') + '" onclick="PlantillasAdmin.setAlin(\'' + nombre + '\',\'center\',this)">Ctr</button>'
        + '<button class="plt-toggle-btn' + (alin==='right'?' active':'') + '" onclick="PlantillasAdmin.setAlin(\'' + nombre + '\',\'right\',this)">Der</button>'
        + '</div>'
        + (z.mostrar_badge != null ? '<label class="plt-switch"><input type="checkbox" ' + (z.mostrar_badge?'checked':'') + ' onchange="PlantillasAdmin.cambiar(\'' + nombre + '\',\'mostrar_badge\',this.checked)">Badg</label><input value="' + self.escAttr(z.texto_badge||'') + '" placeholder="Texto badge" style="width:90px;font-size:10px;border:1px solid #e5e7eb;border-radius:6px;padding:2px 6px" onchange="PlantillasAdmin.cambiar(\'' + nombre + '\',\'texto_badge\',this.value)">' : '')
        + '</div></div>';
    }).join('');
  },

  setAlin: function(nombre, valor, btn) {
    this.cambiar(nombre, 'alineacion', valor);
    btn.parentElement.querySelectorAll('.plt-toggle-btn').forEach(function(b) { b.classList.remove('active'); });
    btn.classList.add('active');
  },

  cambiar: function(nombre, prop, valor) {
    if (!this.configOriginal || !this.configOriginal.zonas) return;
    var z = this.configOriginal.zonas[nombre] || {};
    z[prop] = (prop === 'mostrar_badge') ? !!valor : valor;
    this.configOriginal.zonas[nombre] = z;
    this.marcarNoGuardado();
    this.actualizarPreview();
  },

  marcarNoGuardado: function() {
    this.saved = false;
    var s = document.getElementById('plantillaStatus');
    var span = s.querySelector('span:last-child');
    if (span) span.textContent = 'Cambios sin guardar';
    s.className = 'plt-bottombar-status';
  },

  actualizarPreview: function() {
    var previewEl = document.getElementById('previewContainer');
    if (!this.templateHtml || !this.configOriginal) {
      previewEl.innerHTML = '<p style="color:#9ca3af;padding:40px;text-align:center">Selecciona una plantilla para ver la vista previa</p>';
      return;
    }
    var html = this.templateHtml;
    var info = this.info[this.tipoActual] || {}, zonas = this.configOriginal.zonas || {};
    var dummy = {};
    if (info.dummy) {
      Object.keys(info.dummy).forEach(function(k) { dummy[k] = info.dummy[k]; });
    }
    dummy.logo_upc_url = this._logoUpc || '';
    dummy.logo_abis_url = this._logoAbis || '';
    dummy.watermark_url = this._logoUpc || '';

    Object.keys(zonas).forEach(function(nombre) {
      var z = zonas[nombre] || {}, estilo = [];
      if (z.color_fondo) estilo.push('background-color:' + z.color_fondo);
      if (z.color_texto) estilo.push('color:' + z.color_texto);
      if (z.alineacion) estilo.push('text-align:' + z.alineacion);
      if (z.tamanio_fuente) estilo.push('font-size:' + z.tamanio_fuente);
      if (z.mayusculas) estilo.push('text-transform:uppercase');

      html = html.split('{{texto_' + nombre + '}}').join(z.texto || '');
      html = html.split('{{estilo_' + nombre + '}}').join(estilo.join(';'));
      html = html.split('{{estilo_color_borde_' + nombre + '}}').join(z.color_borde || '#1a6b3c');
      html = html.split('{{badge_' + nombre + '}}').join(
        z.mostrar_badge && z.texto_badge
          ? '<span style="background:#145332;color:#fff;border-radius:6px;padding:5px 16px;font-size:0.7rem;font-weight:700;letter-spacing:1px;text-transform:uppercase;display:inline-block">' + (z.texto_badge || '') + '</span>'
          : ''
      );
    });

    Object.keys(dummy).forEach(function(k) {
      html = html.split('{{' + k + '}}').join(dummy[k] != null ? String(dummy[k]) : '');
    });

    html = html.replace(/\{\{[^}]+\}\}/g, '');

    var temp = previewEl.innerHTML;
    previewEl.innerHTML = html || '<p style="color:#9ca3af;padding:40px;text-align:center">Sin contenido</p>';
  },

  guardar: async function() {
    var tipo = this.tipoActual;
    if (!tipo || !this.configOriginal) return;
    var btn = document.getElementById('btnGuardarPlantilla');
    btn.disabled = true; btn.innerHTML = '<span class="material-symbols-outlined">hourglass_top</span> Guardando...';
    try {
      var res = await fetch(EMAIL_SERVICE + '/api/plantillas/' + tipo + '/config', {
        method: 'PUT', headers: { 'Content-Type': 'application/json', 'X-Internal-Token': INTERNAL_TOKEN },
        body: JSON.stringify({ config: this.configOriginal }) });
      var data = await res.json();
      if (data.ok) {
        this.saved = true;
        var s = document.getElementById('plantillaStatus');
        var span = s.querySelector('span:last-child');
        if (span) span.textContent = 'Guardado ' + new Date().toLocaleTimeString();
        s.className = 'plt-bottombar-status saved';
        if (window.showToast) window.showToast('Plantilla guardada', 'success');
      } else { throw new Error(data.error || 'Error'); }
    } catch (e) { if (window.showToast) window.showToast(e.message, 'error'); }
    finally { btn.disabled = false; btn.innerHTML = '<span class="material-symbols-outlined">save</span> Guardar cambios'; }
  },

  escAttr: function(v) { return String(v||'').replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/"/g,'&quot;'); }
};
PlantillasAdmin.init();
