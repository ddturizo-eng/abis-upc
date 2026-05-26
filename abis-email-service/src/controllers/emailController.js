import { enviarNotificacion } from '../services/emailService.js';

export async function notificarRecuperacion(req, res, next) {
  try {
    const { to, subject, body } = req.body;
    if (!to || !subject || !body) {
      return res.status(400).json({ success: false, error: 'to, subject y body son requeridos' });
    }
    const result = await enviarNotificacion({ to, subject, body });
    res.status(200).json({ success: true, messageId: result.id });
  } catch (error) {
    next(error);
  }
}
