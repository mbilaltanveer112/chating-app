import { MongoClient } from 'mongodb';

let client = null;
let database = null;

export async function connectMongo() {
  if (database) {
    return { client, db: database };
  }

  const uri = (process.env.MONGODB_URI || '').trim();
  if (!uri) {
    throw new Error('MONGODB_URI is required. Add your MongoDB Atlas connection string to .env.');
  }

  const dbName = (process.env.MONGODB_DB_NAME || 'pulsechat_auth').trim() || 'pulsechat_auth';
  client = new MongoClient(uri, {
    maxPoolSize: 10,
    serverSelectionTimeoutMS: 8000
  });

  await client.connect();
  database = client.db(dbName);

  const usersCol = database.collection('users');
  await usersCol.createIndex({ username: 1 }, { unique: true, name: 'uniq_username' });
  await usersCol.createIndex({ uid: 1 }, { unique: true, name: 'uniq_uid' });
  await usersCol.createIndex(
    { recoveryEmail: 1 },
    { unique: true, sparse: true, name: 'uniq_recovery_email' }
  );

  const resetsCol = database.collection('password_resets');
  await resetsCol.createIndex({ expiresAt: 1 }, { expireAfterSeconds: 0, name: 'ttl_password_resets' });
  await resetsCol.createIndex({ username: 1 }, { unique: true, name: 'uniq_reset_username' });

  return { client, db: database };
}

export async function closeMongo() {
  if (client) {
    await client.close();
    client = null;
    database = null;
  }
}
