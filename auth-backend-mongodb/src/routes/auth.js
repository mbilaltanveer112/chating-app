import crypto from 'node:crypto';
import express from 'express';
import argon2 from 'argon2';
import {
  normalizeUsername,
  validateDisplayName,
  validateEmail,
  validatePasswordStrength,
  validateUsername
} from '../validators.js';

const MAX_FAILED_ATTEMPTS = 5;
const LOCKOUT_MS = 15 * 60 * 1000;
const RESET_CODE_TTL_MS = 15 * 60 * 1000;
const MAX_RESET_ATTEMPTS = 5;

function sha256Hex(value) {
  return crypto.createHash('sha256').update(String(value)).digest('hex');
}

export async function defaultHashPassword(password) {
  return argon2.hash(password, {
    type: argon2.argon2id,
    memoryCost: 65536,
    timeCost: 3,
    parallelism: 1
  });
}

export async function defaultVerifyPassword(hash, password) {
  return argon2.verify(hash, password);
}

export function createAuthRouter({
  usersCol,
  resetsCol,
  firebaseAuth,
  sendResetEmail,
  hashPassword = defaultHashPassword,
  verifyPassword = defaultVerifyPassword,
  isSmtpConfigured = () => false,
  allowDevResetPreview = false
}) {
  const router = express.Router();

  async function requireFirebaseBearer(req, res) {
    const header = String(req.headers.authorization || '');
    if (!header.startsWith('Bearer ')) {
      res.status(401).json({ error: 'Missing Authorization Bearer token.' });
      return null;
    }
    const idToken = header.slice('Bearer '.length).trim();
    if (!idToken) {
      res.status(401).json({ error: 'Invalid Authorization Bearer token.' });
      return null;
    }
    try {
      return await firebaseAuth.verifyIdToken(idToken);
    } catch {
      res.status(401).json({ error: 'Authentication session expired. Please sign in again.' });
      return null;
    }
  }

  // GET /auth/username-available?username=...
  router.get('/username-available', async (req, res) => {
    try {
      const check = validateUsername(req.query.username || '');
      if (!check.valid) {
        return res.json({
          available: false,
          valid: false,
          normalizedUsername: check.normalized,
          reason: check.error
        });
      }
      const existing = await usersCol.findOne(
        { username: check.normalized },
        { projection: { _id: 1 } }
      );
      return res.json({
        available: !existing,
        valid: true,
        normalizedUsername: check.normalized,
        reason: existing ? 'This username is already taken.' : ''
      });
    } catch (err) {
      return res.status(500).json({ error: 'Unable to verify username availability.' });
    }
  });

  // POST /auth/register
  router.post('/register', async (req, res) => {
    try {
      const nameCheck = validateDisplayName(req.body?.displayName);
      if (!nameCheck.valid) return res.status(400).json({ error: nameCheck.error });

      const userCheck = validateUsername(req.body?.username);
      if (!userCheck.valid) return res.status(400).json({ error: userCheck.error });

      const emailCheck = validateEmail(req.body?.recoveryEmail);
      if (!emailCheck.valid) return res.status(400).json({ error: emailCheck.error });

      const pwCheck = validatePasswordStrength(req.body?.password);
      if (!pwCheck.valid) return res.status(400).json({ error: pwCheck.error });

      const normalizedUsername = userCheck.normalized;
      const normalizedEmail = emailCheck.normalized;
      const displayName = nameCheck.normalized;

      const existingUser = await usersCol.findOne(
        { $or: [{ username: normalizedUsername }, { recoveryEmail: normalizedEmail }] },
        { projection: { username: 1, recoveryEmail: 1 } }
      );
      if (existingUser) {
        if (existingUser.username === normalizedUsername) {
          return res.status(409).json({ error: 'This username is already taken.' });
        }
        return res.status(409).json({ error: 'This recovery email is already registered.' });
      }

      const uid = `pc_${crypto.randomUUID().replace(/-/g, '').slice(0, 20)}`;
      await firebaseAuth.createUser({ uid, displayName });

      try {
        const passwordHash = await hashPassword(req.body.password);
        const now = new Date();
        await usersCol.insertOne({
          uid,
          username: normalizedUsername,
          displayName,
          recoveryEmail: normalizedEmail,
          emailVerified: false,
          passwordHash,
          failedAttempts: 0,
          lockedUntil: null,
          createdAt: now,
          updatedAt: now
        });
      } catch (dbErr) {
        await firebaseAuth.deleteUser(uid).catch(() => {});
        if (dbErr?.code === 11000) {
          return res.status(409).json({ error: 'Username or recovery email is already registered.' });
        }
        throw dbErr;
      }

      const customToken = await firebaseAuth.createCustomToken(uid, {
        username: normalizedUsername
      });

      return res.status(201).json({
        customToken,
        uid,
        username: normalizedUsername,
        displayName,
        email: normalizedEmail,
        emailVerified: false,
        message: 'Account created in MongoDB.'
      });
    } catch (err) {
      return res.status(500).json({ error: err?.message || 'Unable to create account.' });
    }
  });

  // POST /auth/login
  router.post('/login', async (req, res) => {
    try {
      const userCheck = validateUsername(req.body?.username);
      if (!userCheck.valid) return res.status(400).json({ error: userCheck.error });

      const password = String(req.body?.password || '');
      if (!password) return res.status(400).json({ error: 'Enter your password.' });

      const normalizedUsername = userCheck.normalized;
      const userDoc = await usersCol.findOne({ username: normalizedUsername });
      if (!userDoc || !userDoc.passwordHash) {
        return res.status(401).json({ error: 'Invalid username or password.' });
      }

      if (userDoc.lockedUntil && new Date(userDoc.lockedUntil).getTime() > Date.now()) {
        return res.status(429).json({
          error: 'Too many failed login attempts. Please wait 15 minutes and try again.'
        });
      }

      const validPassword = await verifyPassword(userDoc.passwordHash, password).catch(() => false);
      if (!validPassword) {
        const nextFailed = (userDoc.failedAttempts || 0) + 1;
        const updateDoc = {
          failedAttempts: nextFailed,
          updatedAt: new Date()
        };
        if (nextFailed >= MAX_FAILED_ATTEMPTS) {
          updateDoc.lockedUntil = new Date(Date.now() + LOCKOUT_MS);
        }
        await usersCol.updateOne({ _id: userDoc._id }, { $set: updateDoc });
        return res.status(401).json({ error: 'Invalid username or password.' });
      }

      await usersCol.updateOne(
        { _id: userDoc._id },
        { $set: { failedAttempts: 0, lockedUntil: null, lastLoginAt: new Date(), updatedAt: new Date() } }
      );

      try {
        await firebaseAuth.getUser(userDoc.uid);
      } catch {
        await firebaseAuth.createUser({
          uid: userDoc.uid,
          displayName: userDoc.displayName || normalizedUsername
        });
      }

      const customToken = await firebaseAuth.createCustomToken(userDoc.uid, {
        username: normalizedUsername
      });

      return res.json({
        customToken,
        uid: userDoc.uid,
        username: normalizedUsername,
        displayName: userDoc.displayName || normalizedUsername,
        email: userDoc.recoveryEmail || '',
        emailVerified: Boolean(userDoc.emailVerified)
      });
    } catch (err) {
      return res.status(500).json({ error: 'Unable to sign in right now.' });
    }
  });

  // POST /auth/link-username (keeps existing Google/Phone UID intact)
  router.post('/link-username', async (req, res) => {
    try {
      const decoded = await requireFirebaseBearer(req, res);
      if (!decoded) return;

      const userCheck = validateUsername(req.body?.username);
      if (!userCheck.valid) return res.status(400).json({ error: userCheck.error });

      const pwCheck = validatePasswordStrength(req.body?.password);
      if (!pwCheck.valid) return res.status(400).json({ error: pwCheck.error });

      const rawEmail = req.body?.recoveryEmail || decoded.email || '';
      const emailCheck = validateEmail(rawEmail);
      if (!emailCheck.valid) {
        return res.status(400).json({
          error: 'Enter a valid recovery email to enable password sign-in and reset.'
        });
      }

      const normalizedUsername = userCheck.normalized;
      const normalizedEmail = emailCheck.normalized;
      const uid = decoded.uid;

      const conflictingUsername = await usersCol.findOne(
        { username: normalizedUsername, uid: { $ne: uid } },
        { projection: { _id: 1 } }
      );
      if (conflictingUsername) {
        return res.status(409).json({ error: 'This username is already taken.' });
      }

      const conflictingEmail = await usersCol.findOne(
        { recoveryEmail: normalizedEmail, uid: { $ne: uid } },
        { projection: { _id: 1 } }
      );
      if (conflictingEmail) {
        return res.status(409).json({ error: 'This recovery email is already linked to another account.' });
      }

      const passwordHash = await hashPassword(req.body.password);
      const displayName = String(req.body?.displayName || decoded.name || normalizedUsername).trim().slice(0, 50);
      const now = new Date();

      await usersCol.updateOne(
        { uid },
        {
          $set: {
            uid,
            username: normalizedUsername,
            displayName: displayName || normalizedUsername,
            recoveryEmail: normalizedEmail,
            emailVerified: Boolean(decoded.email_verified),
            passwordHash,
            failedAttempts: 0,
            lockedUntil: null,
            updatedAt: now
          },
          $setOnInsert: {
            createdAt: now
          }
        },
        { upsert: true }
      );

      return res.json({
        uid,
        username: normalizedUsername,
        displayName: displayName || normalizedUsername,
        email: normalizedEmail,
        emailVerified: Boolean(decoded.email_verified),
        message: `Username @${normalizedUsername} linked to your account.`
      });
    } catch (err) {
      if (err?.code === 11000) {
        return res.status(409).json({ error: 'This username or email is already taken.' });
      }
      return res.status(500).json({ error: 'Failed to link username credentials.' });
    }
  });

  // POST /auth/change-password
  router.post('/change-password', async (req, res) => {
    try {
      const decoded = await requireFirebaseBearer(req, res);
      if (!decoded) return;

      const currentPassword = String(req.body?.currentPassword || '');
      if (!currentPassword) {
        return res.status(400).json({ error: 'Enter your current password.' });
      }

      const pwCheck = validatePasswordStrength(req.body?.newPassword);
      if (!pwCheck.valid) return res.status(400).json({ error: pwCheck.error });

      const userDoc = await usersCol.findOne({ uid: decoded.uid });
      if (!userDoc || !userDoc.passwordHash) {
        return res.status(400).json({ error: 'Set a username & password first.' });
      }

      const validCurrent = await verifyPassword(userDoc.passwordHash, currentPassword).catch(() => false);
      if (!validCurrent) {
        return res.status(401).json({ error: 'Current password is incorrect.' });
      }

      const nextHash = await hashPassword(req.body.newPassword);
      await usersCol.updateOne(
        { _id: userDoc._id },
        { $set: { passwordHash: nextHash, updatedAt: new Date() } }
      );

      return res.json({ message: 'Password updated successfully.' });
    } catch (err) {
      return res.status(500).json({ error: 'Failed to update password.' });
    }
  });

  // POST /auth/forgot-password
  router.post('/forgot-password', async (req, res) => {
    try {
      const userCheck = validateUsername(req.body?.username);
      if (!userCheck.valid) return res.status(400).json({ error: userCheck.error });

      const emailCheck = validateEmail(req.body?.recoveryEmail);
      if (!emailCheck.valid) return res.status(400).json({ error: emailCheck.error });

      const normalizedUsername = userCheck.normalized;
      const normalizedEmail = emailCheck.normalized;

      const userDoc = await usersCol.findOne({ username: normalizedUsername });
      if (!userDoc || (userDoc.recoveryEmail || '').toLowerCase() !== normalizedEmail) {
        return res.status(400).json({
          error: 'Username and recovery email do not match our records.'
        });
      }

      const resetCode = String(crypto.randomInt(100000, 1000000));
      const now = new Date();
      const expiresAt = new Date(now.getTime() + RESET_CODE_TTL_MS);

      await resetsCol.updateOne(
        { username: normalizedUsername },
        {
          $set: {
            username: normalizedUsername,
            uid: userDoc.uid,
            recoveryEmail: normalizedEmail,
            codeHash: sha256Hex(resetCode),
            attempts: 0,
            expiresAt,
            createdAt: now
          }
        },
        { upsert: true }
      );

      if (isSmtpConfigured()) {
        await sendResetEmail({
          to: normalizedEmail,
          username: normalizedUsername,
          resetCode
        });
        return res.json({
          message: `Verification code sent to ${normalizedEmail}. Enter the 6-digit code and your new password below.`
        });
      }

      if (allowDevResetPreview) {
        return res.json({
          message: `Dev mode reset code for @${normalizedUsername}: ${resetCode} (configure SMTP_* in .env for real email delivery).`,
          devResetCode: resetCode
        });
      }

      return res.status(503).json({
        error: 'Password reset email service (SMTP) is not configured on the backend yet.'
      });
    } catch (err) {
      return res.status(500).json({ error: err?.message || 'Unable to send password reset code.' });
    }
  });

  // POST /auth/reset-password
  router.post('/reset-password', async (req, res) => {
    try {
      const userCheck = validateUsername(req.body?.username);
      if (!userCheck.valid) return res.status(400).json({ error: userCheck.error });

      const emailCheck = validateEmail(req.body?.recoveryEmail);
      if (!emailCheck.valid) return res.status(400).json({ error: emailCheck.error });

      const resetCode = String(req.body?.resetCode || '').trim();
      if (!/^\d{6}$/.test(resetCode)) {
        return res.status(400).json({ error: 'Enter the 6-digit verification code.' });
      }

      const pwCheck = validatePasswordStrength(req.body?.newPassword);
      if (!pwCheck.valid) return res.status(400).json({ error: pwCheck.error });

      const normalizedUsername = normalizeUsername(userCheck.normalized);
      const resetDoc = await resetsCol.findOne({
        username: normalizedUsername,
        recoveryEmail: emailCheck.normalized
      });

      if (!resetDoc || new Date(resetDoc.expiresAt).getTime() <= Date.now()) {
        return res.status(400).json({ error: 'Reset code expired or not requested. Request a new code.' });
      }

      if ((resetDoc.attempts || 0) >= MAX_RESET_ATTEMPTS) {
        await resetsCol.deleteOne({ _id: resetDoc._id });
        return res.status(429).json({ error: 'Too many invalid reset attempts. Request a new code.' });
      }

      if (resetDoc.codeHash !== sha256Hex(resetCode)) {
        await resetsCol.updateOne({ _id: resetDoc._id }, { $inc: { attempts: 1 } });
        return res.status(400).json({ error: 'Invalid verification code.' });
      }

      const nextHash = await hashPassword(req.body.newPassword);
      await usersCol.updateOne(
        { uid: resetDoc.uid },
        {
          $set: {
            passwordHash: nextHash,
            failedAttempts: 0,
            lockedUntil: null,
            updatedAt: new Date()
          }
        }
      );
      await resetsCol.deleteOne({ _id: resetDoc._id });

      return res.json({
        message: 'Password reset successful. Sign in with your new password.'
      });
    } catch (err) {
      return res.status(500).json({ error: 'Unable to reset password.' });
    }
  });

  return router;
}
