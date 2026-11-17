import fs from 'node:fs';
import admin from 'firebase-admin';

export function initFirebaseAdmin() {
  if (admin.apps.length > 0) {
    return admin.app();
  }

  const rawEnv = (process.env.FIREBASE_SERVICE_ACCOUNT_JSON || '').trim();
  if (rawEnv) {
    let jsonText = rawEnv;
    if (!rawEnv.startsWith('{')) {
      jsonText = Buffer.from(rawEnv, 'base64').toString('utf8');
    }
    const serviceAccount = JSON.parse(jsonText);
    return admin.initializeApp({
      credential: admin.credential.cert(serviceAccount)
    });
  }

  const credPath = (process.env.GOOGLE_APPLICATION_CREDENTIALS || '').trim();
  if (credPath && fs.existsSync(credPath)) {
    const serviceAccount = JSON.parse(fs.readFileSync(credPath, 'utf8'));
    return admin.initializeApp({
      credential: admin.credential.cert(serviceAccount)
    });
  }

  throw new Error(
    'Firebase Admin credentials missing. Set FIREBASE_SERVICE_ACCOUNT_JSON or GOOGLE_APPLICATION_CREDENTIALS.'
  );
}
