import { Router } from 'express';
import { enviarCertificado } from '../controllers/certificadoController.js';
import { requireInternalToken } from '../utils/internalAuth.js';

const router = Router();

router.post('/enviar', requireInternalToken, enviarCertificado);

export default router;
