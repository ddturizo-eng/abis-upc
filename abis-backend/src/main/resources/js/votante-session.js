const VotanteSession = {
  setDatosRegistro(data) {
    sessionStorage.setItem('abis_registro', JSON.stringify(data));
  },
  getDatosRegistro() {
    return JSON.parse(sessionStorage.getItem('abis_registro') || 'null');
  },
  setIdentificacion(id) {
    sessionStorage.setItem('abis_identificacion', id);
  },
  getIdentificacion() {
    return sessionStorage.getItem('abis_identificacion');
  },
  clear() {
    ['abis_registro', 'abis_identificacion'].forEach((key) => sessionStorage.removeItem(key));
  }
};

window.VotanteSession = VotanteSession;
