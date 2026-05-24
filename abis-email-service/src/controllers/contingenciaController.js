import { contingenciaService } from '../services/contingenciaService.js';

export async function enviarQrContingencia(req, res, next) {
  try {
    const result = await contingenciaService.enviarQr(req.body);
    res.status(200).json(result);
  } catch (error) {
    next(error);
  }
}
