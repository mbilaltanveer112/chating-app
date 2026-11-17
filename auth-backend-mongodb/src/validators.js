const USERNAME_REGEX = /^[a-z0-9._]{3,30}$/;
const EMAIL_REGEX = /^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/;

const RESERVED_USERNAMES = new Set([
  'admin',
  'administrator',
  'auth',
  'help',
  'moderator',
  'null',
  'pulsechat',
  'root',
  'security',
  'support',
  'system',
  'undefined'
]);

export function normalizeUsername(raw = '') {
  return String(raw).trim().toLowerCase();
}

export function validateUsername(raw = '') {
  const normalized = normalizeUsername(raw);
  if (normalized.length < 3 || normalized.length > 30) {
    return { valid: false, error: 'Username must be between 3 and 30 characters.', normalized };
  }
  if (!USERNAME_REGEX.test(normalized)) {
    return { valid: false, error: 'Use only lowercase letters, numbers, dots, or underscores.', normalized };
  }
  if (
    normalized.startsWith('.') ||
    normalized.startsWith('_') ||
    normalized.endsWith('.') ||
    normalized.endsWith('_') ||
    normalized.includes('..') ||
    normalized.includes('__')
  ) {
    return {
      valid: false,
      error: 'Username cannot start/end with or repeat dots or underscores.',
      normalized
    };
  }
  if (RESERVED_USERNAMES.has(normalized)) {
    return { valid: false, error: 'This username is reserved.', normalized };
  }
  return { valid: true, normalized };
}

export function validateDisplayName(raw = '') {
  const clean = String(raw).trim();
  if (!clean) {
    return { valid: false, error: 'Full name is required.', normalized: '' };
  }
  if (clean.length > 50) {
    return { valid: false, error: 'Full name must be 50 characters or fewer.', normalized: clean };
  }
  return { valid: true, normalized: clean };
}

export function validateEmail(raw = '') {
  const normalized = String(raw).trim().toLowerCase();
  if (!normalized) {
    return { valid: false, error: 'Recovery email is required.', normalized: '' };
  }
  if (normalized.length > 200 || !EMAIL_REGEX.test(normalized)) {
    return { valid: false, error: 'Enter a valid email address.', normalized };
  }
  return { valid: true, normalized };
}

export function validatePasswordStrength(password = '') {
  const str = String(password);
  if (!str) return { valid: false, error: 'Password is required.' };
  if (str.length < 8) return { valid: false, error: 'Password must be at least 8 characters.' };
  if (str.length > 128) return { valid: false, error: 'Password must be 128 characters or fewer.' };
  if (str !== str.trim()) return { valid: false, error: 'Password cannot start or end with spaces.' };
  if (!/[a-z]/.test(str)) return { valid: false, error: 'Include at least one lowercase letter.' };
  if (!/[A-Z]/.test(str)) return { valid: false, error: 'Include at least one uppercase letter.' };
  if (!/[0-9]/.test(str)) return { valid: false, error: 'Include at least one number.' };
  if (!/[^a-zA-Z0-9\s]/.test(str)) return { valid: false, error: 'Include at least one special character.' };
  return { valid: true };
}
