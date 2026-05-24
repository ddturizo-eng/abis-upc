import { certificadoService } from '../services/certificadoService.js';

export async function enviarCertificado(req, res, next) {
  try {
    const result = await certificadoService.enviar(req.body);
    res.status(200).json(result);
  } catch (error) {
    next(error);
  }
}
