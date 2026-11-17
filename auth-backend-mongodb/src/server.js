import 'dotenv/config';
import express from 'express';
import cors from 'cors';
import helmet from 'helmet';
import rateLimit from 'express-rate-limit';
import admin from 'firebase-admin';
import { initFirebaseAdmin } from './firebaseAdmin.js';
import { closeMongo, connectMongo } from './db.js';
import { isSmtpConfigured, sendPasswordResetEmail } from './mailer.js';
import { createAuthRouter } from './routes/auth.js';

async function start() {
  initFirebaseAdmin();
  const { db } = await connectMongo();

  const app = express();
  app.set('trust proxy', 1);
  app.use(helmet());
  app.use(express.json({ limit: '64kb' }));

  const allowedOrigins = (process.env.ALLOWED_ORIGINS || '')
    .split(',')
    .map((s) => s.trim())
    .filter(Boolean);
  app.use(
    cors({
      origin: allowedOrigins.length > 0 ? allowedOrigins : true
    })
  );

  const authLimiter = rateLimit({
    windowMs: 15 * 60 * 1000,
    max: 60,
    standardHeaders: true,
    legacyHeaders: false,
    message: { error: 'Too many authentication requests. Please try again later.' }
  });

  app.get('/health', (_req, res) => {
    res.json({
      ok: true,
      service: 'pulsechat-mongodb-auth',
      mongoConnected: true,
      smtpConfigured: isSmtpConfigured()
    });
  });

  app.use(
    '/auth',
    authLimiter,
    createAuthRouter({
      usersCol: db.collection('users'),
      resetsCol: db.collection('password_resets'),
      firebaseAuth: admin.auth(),
      sendResetEmail: sendPasswordResetEmail,
      isSmtpConfigured,
      allowDevResetPreview: process.env.ALLOW_DEV_RESET_CODE_PREVIEW === 'true'
    })
  );

  const port = Number(process.env.PORT || 8080);
  const server = app.listen(port, '0.0.0.0', () => {
    console.log(`PulseChat MongoDB Auth server listening on port ${port}`);
  });

  const shutdown = async () => {
    server.close();
    await closeMongo();
    process.exit(0);
  };
  process.on('SIGINT', shutdown);
  process.on('SIGTERM', shutdown);
}

start().catch((err) => {
  console.error('Failed to start PulseChat MongoDB Auth server:', err);
  process.exit(1);
});
