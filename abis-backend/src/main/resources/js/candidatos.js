(function initCandidatosPage() {
  const select = document.getElementById('candidatos-election-select');
  const list = document.getElementById('candidatos-list');
  const error = document.getElementById('candidatos-error');

  function showError(message) {
    error.textContent = message;
    error.classList.remove('hidden');
  }

  async function cargarElecciones() {
    try {
      const res = await ApiElecciones.listar();
      const elecciones = res.data || res || [];
      select.innerHTML = '<option value="">Seleccione una eleccion...</option>' + elecciones.map((e) =>
        `<option value="${e.id}">${e.nombre} - ${e.estado || 'SIN ESTADO'}</option>`
      ).join('');
      const activa = elecciones.find((e) => e.estado === 'EN_CURSO') || elecciones[0];
      if (activa) {
        select.value = activa.id;
        await cargarCandidatos(activa.id);
      }
    } catch (err) {
      select.innerHTML = '<option value="">No fue posible cargar elecciones</option>';
      showError(err.message);
    }
  }

  async function cargarCandidatos(idEleccion) {
    error.classList.add('hidden');
    list.innerHTML = '';
    if (!idEleccion) return;
    try {
      const res = await ApiElecciones.candidatos(idEleccion);
      const candidatos = res.data || [];
      if (!candidatos.length) {
        list.innerHTML = '<div class="rounded-lg border border-slate-200 bg-white p-6 text-sm text-slate-500">No hay candidatos registrados para esta eleccion.</div>';
        return;
      }
      const grupos = candidatos.reduce((acc, c) => {
        const cargo = c.cargo || 'Sin cargo';
        acc[cargo] = acc[cargo] || [];
        acc[cargo].push(c);
        return acc;
      }, {});
      list.innerHTML = Object.entries(grupos).map(([cargo, items]) => `
        <section class="bg-white rounded-xl shadow-[0px_10px_30px_rgba(0,77,51,0.08)] overflow-hidden">
          <div class="px-6 py-4 bg-slate-50 border-b">
            <h2 class="text-[11px] font-bold uppercase tracking-widest text-[#004D33]">${cargo}</h2>
          </div>
          <div class="grid grid-cols-2 gap-4 p-6">
            ${items.map((c) => `
              <div class="rounded-lg border border-slate-200 p-4">
                <div class="flex items-center gap-3">
                  <span class="flex h-10 w-10 items-center justify-center rounded-full bg-[#004D33] text-sm font-bold text-white">${c.numeroCampania || c.numero_campania || '--'}</span>
                  <div>
                    <p class="font-bold text-slate-700">${[c.primerNombre || c.primer_nombre, c.segundoNombre || c.segundo_nombre, c.primerApellido || c.primer_apellido, c.segundoApellido || c.segundo_apellido].filter(Boolean).join(' ') || '--'}</p>
                    <p class="text-xs text-slate-400">ID ${c.idCandidato || c.id_candidato || '--'}</p>
                  </div>
                </div>
              </div>
            `).join('')}
          </div>
        </section>
      `).join('');
    } catch (err) {
      showError(err.message);
    }
  }

  select.addEventListener('change', () => cargarCandidatos(select.value));
  cargarElecciones();
})();

