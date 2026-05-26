import { generarActaPdf } from '../services/pdfService.js';

export async function generarActa(req, res, next) {
  try {
    const pdfBuffer = await generarActaPdf(req.body);
    res.setHeader('Content-Type', 'application/pdf');
    res.setHeader('Content-Disposition', 'inline; filename="acta-ganadores.pdf"');
    res.status(200).send(Buffer.from(pdfBuffer));
  } catch (error) {
    next(error);
  }
}
