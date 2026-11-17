import nodemailer from 'nodemailer';

export function isSmtpConfigured() {
  return Boolean(
    (process.env.SMTP_HOST || '').trim() &&
      (process.env.SMTP_USER || '').trim() &&
      (process.env.SMTP_PASS || '').trim()
  );
}

export async function sendPasswordResetEmail({ to, username, resetCode }) {
  if (!isSmtpConfigured()) {
    throw new Error(
      'Password reset email service (SMTP) is not configured on the backend server.'
    );
  }

  const port = Number(process.env.SMTP_PORT || 465);
  const secure = process.env.SMTP_SECURE ? process.env.SMTP_SECURE === 'true' : port === 465;

  const transporter = nodemailer.createTransport({
    host: process.env.SMTP_HOST.trim(),
    port,
    secure,
    auth: {
      user: process.env.SMTP_USER.trim(),
      pass: process.env.SMTP_PASS.trim()
    }
  });

  const from = (process.env.SMTP_FROM || process.env.SMTP_USER).trim();
  await transporter.sendMail({
    from,
    to,
    subject: 'PulseChat Password Reset Code',
    text: `Hello @${username},\n\nYour PulseChat password reset verification code is: ${resetCode}\n\nThis code expires in 15 minutes. If you did not request a reset, ignore this email.`
  });
}
