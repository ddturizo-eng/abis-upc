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
  setConsentimiento(data) {
    sessionStorage.setItem('abis_consentimiento', JSON.stringify(data));
  },
  getConsentimiento() {
    return JSON.parse(sessionStorage.getItem('abis_consentimiento') || 'null');
  },
  clear() {
    ['abis_registro', 'abis_identificacion', 'abis_consentimiento'].forEach((key) => sessionStorage.removeItem(key));
  }
};

window.VotanteSession = VotanteSession;
