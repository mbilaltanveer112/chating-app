# PulseChat MongoDB Auth Backend (Option A)

100% free-tier compatible **Node.js + MongoDB Atlas + Argon2id + Firebase Custom Token** authentication backend for PulseChat.
- **Login, Sign Up, Forgot Password, Change Password, and Link Username** run against **MongoDB Atlas**.
- **Chats, Messages, WebRTC Calls, E2EE Key Bundles, and Online/Last Seen Presence** stay on **Cloud Firestore** via Firebase Custom Tokens (`signInWithCustomToken`) — no Firebase Email/Password provider toggle required.

---

## 1. Create Free MongoDB Atlas Database (No Credit Card)
1. Go to [MongoDB Atlas](https://www.mongodb.com/cloud/atlas/register) and sign in.
2. Create an **M0 Free** cluster (`512 MB` storage, `$0/month`).
3. Under **Database Access**, create a database user (save username & password).
4. Under **Network Access**, allow access from anywhere (`0.0.0.0/0`) so your hosted Node.js server can connect.
5. Click **Connect -> Drivers -> Node.js** and copy your connection string:
   ```
   mongodb+srv://<user>:<password>@<cluster>.mongodb.net/?retryWrites=true&w=majority
   ```

## 2. Get Firebase Service Account Key
1. Open Firebase Console for project **`gen-lang-client-0547049184`**.
2. Go to **Project Settings -> Service accounts -> Generate new private key** (downloads a `.json` file).
3. Convert it to a single-line JSON string (or Base64) for `FIREBASE_SERVICE_ACCOUNT_JSON`.

## 3. Deploy Free (Render / Koyeb / Local)
- Push `auth-backend-mongodb/` (or connect repo) to a free Node.js host such as **Render** (Free Web Service — no credit card required).
  - **Root Directory**: `auth-backend-mongodb`
  - **Build Command**: `npm install`
  - **Start Command**: `npm start`
- Set Environment Variables on the host:
  - `MONGODB_URI` = your MongoDB Atlas URI
  - `MONGODB_DB_NAME` = `pulsechat_auth`
  - `FIREBASE_SERVICE_ACCOUNT_JSON` = your service account JSON
  - Optional (for password reset emails): `SMTP_HOST`, `SMTP_PORT`, `SMTP_USER`, `SMTP_PASS`, `SMTP_FROM`
- Copy your deployed HTTPS URL (e.g., `https://pulsechat-auth.onrender.com`).

## 4. Connect the Android App
1. Open the **Secrets** panel in Google AI Studio.
2. Add **`AUTH_BACKEND_BASE_URL`** with your deployed HTTPS URL (e.g., `https://pulsechat-auth.onrender.com`).
3. Rebuild/run PulseChat — Username Login, Sign Up, Set Username & Password, Change Password, and Forgot Password will automatically authenticate against MongoDB!
