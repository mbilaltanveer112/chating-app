import test from 'node:test';
import assert from 'node:assert/strict';
import http from 'node:http';
import crypto from 'node:crypto';
import express from 'express';
import { createAuthRouter } from '../src/routes/auth.js';
import { validateUsername, validatePasswordStrength } from '../src/validators.js';

function createMemoryCollection(uniqueFields = []) {
  const docs = [];
  let seq = 1;

  function matches(doc, query) {
    if (!query || Object.keys(query).length === 0) return true;
    for (const [k, v] of Object.entries(query)) {
      if (k === '$or') {
        if (!v.some((sub) => matches(doc, sub))) return false;
      } else if (v && typeof v === 'object' && '$ne' in v) {
        if (doc[k] === v.$ne) return false;
      } else if (doc[k] !== v) {
        return false;
      }
    }
    return true;
  }

  return {
    docs,
    async findOne(query) {
      return docs.find((d) => matches(d, query)) || null;
    },
    async insertOne(doc) {
      for (const field of uniqueFields) {
        if (doc[field] && docs.some((d) => d[field] === doc[field])) {
          const err = new Error('Duplicate key');
          err.code = 11000;
          throw err;
        }
      }
      const copy = { _id: `id_${seq++}`, ...doc };
      docs.push(copy);
      return { insertedId: copy._id };
    },
    async updateOne(query, update, options = {}) {
      const idx = docs.findIndex((d) => matches(d, query));
      if (idx === -1) {
        if (!options.upsert) return { matchedCount: 0, modifiedCount: 0 };
        const inserted = {
          _id: `id_${seq++}`,
          ...query,
          ...(update.$setOnInsert || {}),
          ...(update.$set || {})
        };
        docs.push(inserted);
        return { matchedCount: 0, modifiedCount: 0, upsertedId: inserted._id };
      }
      const next = { ...docs[idx] };
      if (update.$set) Object.assign(next, update.$set);
      if (update.$inc) {
        for (const [f, delta] of Object.entries(update.$inc)) {
          next[f] = (next[f] || 0) + delta;
        }
      }
      docs[idx] = next;
      return { matchedCount: 1, modifiedCount: 1 };
    },
    async deleteOne(query) {
      const idx = docs.findIndex((d) => matches(d, query));
      if (idx === -1) return { deletedCount: 0 };
      docs.splice(idx, 1);
      return { deletedCount: 1 };
    }
  };
}

function createFakeFirebaseAuth() {
  const users = new Map();
  return {
    users,
    async createUser({ uid, displayName }) {
      users.set(uid, { uid, displayName });
      return { uid, displayName };
    },
    async getUser(uid) {
      if (!users.has(uid)) throw new Error('Not found');
      return users.get(uid);
    },
    async deleteUser(uid) {
      users.delete(uid);
    },
    async createCustomToken(uid) {
      return `custom_token_for_${uid}`;
    },
    async verifyIdToken(token) {
      if (!token.startsWith('idtoken:')) throw new Error('Invalid token');
      const [, uid, email = ''] = token.split(':');
      return { uid, email, email_verified: Boolean(email) };
    }
  };
}

async function startTestApp({ smtpEnabled = false, allowDevResetPreview = true } = {}) {
  const usersCol = createMemoryCollection(['username', 'uid', 'recoveryEmail']);
  const resetsCol = createMemoryCollection(['username']);
  const firebaseAuth = createFakeFirebaseAuth();
  const sentEmails = [];

  const app = express();
  app.use(express.json());
  app.use(
    '/auth',
    createAuthRouter({
      usersCol,
      resetsCol,
      firebaseAuth,
      sendResetEmail: async (payload) => {
        sentEmails.push(payload);
      },
      hashPassword: async (pw) => `hashed:${crypto.createHash('sha256').update(pw).digest('hex')}`,
      verifyPassword: async (hash, pw) =>
        hash === `hashed:${crypto.createHash('sha256').update(pw).digest('hex')}`,
      isSmtpConfigured: () => smtpEnabled,
      allowDevResetPreview
    })
  );

  const server = http.createServer(app);
  await new Promise((resolve) => server.listen(0, '127.0.0.1', resolve));
  const addr = server.address();
  const baseUrl = `http://127.0.0.1:${addr.port}`;
  return { server, baseUrl, usersCol, resetsCol, firebaseAuth, sentEmails };
}

test('validators normalize usernames and reject weak passwords', () => {
  assert.equal(validateUsername('  Bilal_Ahmed.1 ').normalized, 'bilal_ahmed.1');
  assert.equal(validateUsername('ab').valid, false);
  assert.equal(validateUsername('admin').valid, false);
  assert.equal(validatePasswordStrength('weak123').valid, false);
  assert.equal(validatePasswordStrength('Strong#Pass2026').valid, true);
});

test('register, duplicate rejection, login custom token, link username, change & reset password', async () => {
  const ctx = await startTestApp({ smtpEnabled: true, allowDevResetPreview: false });
  try {
    // 1. Check availability
    const availRes = await fetch(`${ctx.baseUrl}/auth/username-available?username=Bilal.99`);
    const availJson = await availRes.json();
    assert.equal(availJson.available, true);
    assert.equal(availJson.normalizedUsername, 'bilal.99');

    // 2. Register
    const regRes = await fetch(`${ctx.baseUrl}/auth/register`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        username: 'Bilal.99',
        password: 'Strong#Pass2026',
        displayName: 'Bilal Ahmed',
        recoveryEmail: 'bilal@example.com'
      })
    });
    assert.equal(regRes.status, 201);
    const regJson = await regRes.json();
    assert.equal(regJson.username, 'bilal.99');
    assert.ok(regJson.customToken.startsWith('custom_token_for_pc_'));

    // Ensure no plaintext password is stored
    assert.equal(ctx.usersCol.docs.length, 1);
    assert.equal(ctx.usersCol.docs[0].password, undefined);
    assert.ok(ctx.usersCol.docs[0].passwordHash.startsWith('hashed:'));

    // 3. Duplicate username rejected
    const dupRes = await fetch(`${ctx.baseUrl}/auth/register`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        username: 'BILAL.99',
        password: 'Strong#Pass2026',
        displayName: 'Other User',
        recoveryEmail: 'other@example.com'
      })
    });
    assert.equal(dupRes.status, 409);

    // 4. Wrong password rejected
    const badLogin = await fetch(`${ctx.baseUrl}/auth/login`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ username: 'bilal.99', password: 'Wrong#Pass2026' })
    });
    assert.equal(badLogin.status, 401);

    // 5. Valid login returns customToken
    const goodLogin = await fetch(`${ctx.baseUrl}/auth/login`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ username: 'BILAL.99', password: 'Strong#Pass2026' })
    });
    assert.equal(goodLogin.status, 200);
    const goodJson = await goodLogin.json();
    assert.equal(goodJson.uid, regJson.uid);

    // 6. Link username on existing Google/Phone UID without creating new UID
    const linkRes = await fetch(`${ctx.baseUrl}/auth/link-username`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: 'Bearer idtoken:google_uid_123:googleuser@example.com'
      },
      body: JSON.stringify({
        username: 'google_linked_user',
        password: 'Strong#Pass2026'
      })
    });
    assert.equal(linkRes.status, 200);
    const linkJson = await linkRes.json();
    assert.equal(linkJson.uid, 'google_uid_123');
    assert.equal(linkJson.username, 'google_linked_user');

    // 7. Forgot + Reset password flow
    const forgotRes = await fetch(`${ctx.baseUrl}/auth/forgot-password`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ username: 'bilal.99', recoveryEmail: 'bilal@example.com' })
    });
    assert.equal(forgotRes.status, 200);
    assert.equal(ctx.sentEmails.length, 1);
    const code = ctx.sentEmails[0].resetCode;

    const resetRes = await fetch(`${ctx.baseUrl}/auth/reset-password`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        username: 'bilal.99',
        recoveryEmail: 'bilal@example.com',
        resetCode: code,
        newPassword: 'Updated#Pass2026'
      })
    });
    assert.equal(resetRes.status, 200);

    // Login with new password works
    const relogin = await fetch(`${ctx.baseUrl}/auth/login`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ username: 'bilal.99', password: 'Updated#Pass2026' })
    });
    assert.equal(relogin.status, 200);
  } finally {
    ctx.server.close();
  }
});
