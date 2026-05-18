import express from 'express';
import { config } from './src/config/config.js';
import certificadoRoutes from './src/routes/certificadoRoutes.js';

const app = express();

app.disable('x-powered-by');
app.use(express.json({ limit: '1mb' }));

app.get('/health', (_req, res) => {
  res.json({
    service: 'abis-email-service',
    status: 'ok'
  });
});

app.use('/api/certificados', certificadoRoutes);

app.use((req, res) => {
  res.status(404).json({
    success: false,
    error: `Ruta no encontrada: ${req.method} ${req.path}`
  });
});

app.use((err, _req, res, _next) => {
  console.error('[EmailService]', err.message);
  res.status(err.statusCode || 500).json({
    success: false,
    error: err.publicMessage || 'Error interno del servicio de certificados'
  });
});

app.listen(config.port, () => {
  console.log(`ABIS Email Service en http://localhost:${config.port}`);
});
