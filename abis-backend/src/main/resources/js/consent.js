const ConsentStep = {
  init() {
    const scrollBox = document.getElementById('consent-scroll');
    const checkbox = document.getElementById('consent-checkbox');
    const button = document.getElementById('btn-consent-accept');
    const hint = document.getElementById('consent-hint');

    const update = () => {
      const hasRead = scrollBox.scrollTop + scrollBox.clientHeight >= scrollBox.scrollHeight - 6;
      checkbox.disabled = !hasRead;
      if (hasRead) hint.textContent = 'Marque la casilla para continuar.';
      const enabled = hasRead && checkbox.checked;
      button.disabled = !enabled;
      button.classList.toggle('opacity-50', !enabled);
      button.classList.toggle('cursor-not-allowed', !enabled);
    };

    scrollBox.addEventListener('scroll', update);
    checkbox.addEventListener('change', update);
    button.addEventListener('click', async () => {
      VotanteSession.setConsentimiento({
        accepted: true,
        acceptedAt: new Date().toISOString(),
        legalBase: 'Ley 1581 de 2012; Decreto 1377 de 2013'
      });
      await Router.irA(1);
    });
    update();
  }
};

window.ConsentStep = ConsentStep;
