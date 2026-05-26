import { Router } from 'express';
import { notificarRecuperacion } from '../controllers/emailController.js';
import { requireInternalToken } from '../utils/internalAuth.js';

const router = Router();

router.post('/notificar-recuperacion', requireInternalToken, notificarRecuperacion);

export default router;
