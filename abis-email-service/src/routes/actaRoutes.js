import { Router } from 'express';
import { generarActa } from '../controllers/actaController.js';
import { requireInternalToken } from '../utils/internalAuth.js';

const router = Router();

router.post('/generar', requireInternalToken, generarActa);

export default router;
