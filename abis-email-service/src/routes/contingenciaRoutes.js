import { Router } from 'express';
import { enviarQrContingencia } from '../controllers/contingenciaController.js';
import { requireInternalToken } from '../utils/internalAuth.js';

const router = Router();

router.post('/enviar-qr', requireInternalToken, enviarQrContingencia);

export default router;
