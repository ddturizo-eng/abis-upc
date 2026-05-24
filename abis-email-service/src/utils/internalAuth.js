import { timingSafeEqual } from 'node:crypto';
import { config } from '../config/config.js';

function safeCompare(left, right) {
  const leftBuffer = Buffer.from(left);
  const rightBuffer = Buffer.from(right);
  return leftBuffer.length === rightBuffer.length && timingSafeEqual(leftBuffer, rightBuffer);
}

export function requireInternalToken(req, res, next) {
  if (!config.internalToken) {
    return res.status(503).json({
      success: false,
      error: 'ABIS_EMAIL_SERVICE_TOKEN no configurado'
    });
  }

  const token = req.header('X-Internal-Token') || '';
  if (!safeCompare(token, config.internalToken)) {
    return res.status(401).json({
      success: false,
      error: 'Token interno invalido'
    });
  }

  return next();
}
