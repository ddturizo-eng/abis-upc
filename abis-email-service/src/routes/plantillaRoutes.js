import { Router } from 'express';
import * as plantillaController from '../controllers/plantillaController.js';
import { requireInternalToken } from '../utils/internalAuth.js';

const router = Router();

function authOrOptions(req, res, next) {
  if (req.method === 'OPTIONS') return next();
  requireInternalToken(req, res, next);
}

router.get('/tipos', authOrOptions, plantillaController.tipos);
router.get('/:tipo', authOrOptions, plantillaController.obtener);
router.put('/:tipo', authOrOptions, plantillaController.guardar);
router.get('/:tipo/config', authOrOptions, plantillaController.obtenerConfig);
router.put('/:tipo/config', authOrOptions, plantillaController.guardarConfig);
router.post('/:tipo/preview', authOrOptions, plantillaController.preview);

export default router;
