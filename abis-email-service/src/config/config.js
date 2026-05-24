import dotenv from 'dotenv';

dotenv.config();

export const config = {
  port: Number(process.env.PORT || 8010),
  internalToken: process.env.ABIS_EMAIL_SERVICE_TOKEN || '',
  resendApiKey: process.env.ABIS_RESEND_API_KEY || '',
  resendFromEmail: process.env.ABIS_RESEND_FROM_EMAIL || 'ABIS UPC <abisupc@hcefectos.com>'
};
