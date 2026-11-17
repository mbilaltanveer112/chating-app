const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

const pastTimestamp = () => new Date(Date.now() - 2000);

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_uid_1";
const BOB_UID = "bob_uid_2";
const CHARLIE_UID = "charlie_uid_3";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

// --- 1. Unauthenticated Rejections ---

test("Unauthenticated user: cannot read private users, public_profiles, or chats", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("public_profiles").where("discoverable", "==", true).get());
  await assertFails(unauthDb.collection("chats").where("memberUids", "array-contains", ALICE_UID).get());
});

// --- 2. Private User Profile (PII Isolation) ---

test("Private user profile: owner can create/read/update, other user cannot read or write", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      email: "alice@example.com",
      displayName: "Alice",
      bio: "Hello",
      darkModeEnabled: true,
      notificationsEnabled: true,
      createdAt: pastTimestamp(),
      updatedAt: pastTimestamp(),
    })
  );

  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).update({ displayName: "Hacked" })
  );
  // Shadow update with ghost field must fail
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      displayName: "Alice 2",
      isAdmin: true,
      updatedAt: pastTimestamp(),
    })
  );
});

// --- 3. Public Profiles Directory ---

test("Public profiles: owner writes, signed-in users query with discoverable == true", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  await assertSucceeds(
    aliceDb.collection("public_profiles").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice",
      handle: "alice",
      bio: "Designer",
      isOnline: true,
      discoverable: true,
      lastSeen: pastTimestamp(),
      createdAt: pastTimestamp(),
      updatedAt: pastTimestamp(),
    })
  );

  // Unfiltered list must fail (Query Enforcer)
  await assertFails(bobDb.collection("public_profiles").get());
  // Filtered query succeeds
  await assertSucceeds(
    bobDb.collection("public_profiles").where("discoverable", "==", true).get()
  );
  // Cross-user write fails
  await assertFails(
    bobDb.collection("public_profiles").doc(ALICE_UID).update({
      displayName: "Spoofed",
      updatedAt: pastTimestamp(),
    })
  );
});

// --- 4. Chats & Messages Isolation ---

test("Chats & Messages: members can create/read/update/send, non-members denied", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const charlieDb = testEnv.authenticatedContext(CHARLIE_UID).firestore();

  const chatId = "chat_alice_bob";
  await assertSucceeds(
    aliceDb.collection("chats").doc(chatId).set({
      type: "direct",
      title: "Alice & Bob",
      memberUids: [ALICE_UID, BOB_UID],
      memberNames: { [ALICE_UID]: "Alice", [BOB_UID]: "Bob" },
      createdBy: ALICE_UID,
      lastMessageText: "",
      lastMessageType: "text",
      lastMessageSenderId: ALICE_UID,
      pinnedBy: [],
      archivedBy: [],
      typingUids: [],
      readByUids: [ALICE_UID],
      createdAt: pastTimestamp(),
      updatedAt: pastTimestamp(),
    })
  );

  // Unfiltered chats query fails
  await assertFails(aliceDb.collection("chats").get());
  // Member-filtered chats query succeeds
  await assertSucceeds(
    bobDb.collection("chats").where("memberUids", "array-contains", BOB_UID).get()
  );
  // Non-member cannot read chat
  await assertFails(charlieDb.collection("chats").doc(chatId).get());

  // Bob sends a message in the chat
  const msgId = "msg_1";
  await assertSucceeds(
    bobDb.collection("chats").doc(chatId).collection("messages").doc(msgId).set({
      chatId: chatId,
      senderId: BOB_UID,
      senderName: "Bob",
      memberUids: [ALICE_UID, BOB_UID],
      type: "text",
      text: "Hey Alice!",
      readByUids: [BOB_UID],
      createdAt: pastTimestamp(),
      updatedAt: pastTimestamp(),
    })
  );

  // Alice queries messages with memberUids filter
  await assertSucceeds(
    aliceDb
      .collection("chats")
      .doc(chatId)
      .collection("messages")
      .where("memberUids", "array-contains", ALICE_UID)
      .get()
  );

  // Charlie (non-member) cannot read Bob's message, run unfiltered query, or query Alice's messages
  await assertFails(
    charlieDb.collection("chats").doc(chatId).collection("messages").doc(msgId).get()
  );
  await assertFails(
    charlieDb.collection("chats").doc(chatId).collection("messages").get()
  );
  await assertFails(
    charlieDb
      .collection("chats")
      .doc(chatId)
      .collection("messages")
      .where("memberUids", "array-contains", ALICE_UID)
      .get()
  );
  await assertFails(
    charlieDb.collection("chats").doc(chatId).collection("messages").doc("msg_bad").set({
      chatId: chatId,
      senderId: CHARLIE_UID,
      senderName: "Charlie",
      memberUids: [ALICE_UID, BOB_UID, CHARLIE_UID],
      type: "text",
      text: "Intrusion",
      createdAt: pastTimestamp(),
    })
  );

  // Alice cannot delete Bob's message
  await assertFails(
    aliceDb.collection("chats").doc(chatId).collection("messages").doc(msgId).delete()
  );
  // Bob can mark his own message deleted
  await assertSucceeds(
    bobDb.collection("chats").doc(chatId).collection("messages").doc(msgId).update({
      text: "Message deleted",
      deleted: true,
      updatedAt: pastTimestamp(),
    })
  );
});

test("Typing indicator: members can toggle typingUids and typingTimestamps in direct and group chats, non-members denied", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const charlieDb = testEnv.authenticatedContext(CHARLIE_UID).firestore();

  const groupId = "group_typing_room";
  await assertSucceeds(
    aliceDb.collection("chats").doc(groupId).set({
      type: "group",
      title: "Design Squad",
      memberUids: [ALICE_UID, BOB_UID],
      memberNames: { [ALICE_UID]: "Alice", [BOB_UID]: "Bob" },
      createdBy: ALICE_UID,
      lastMessageText: "Welcome",
      lastMessageType: "text",
      lastMessageSenderId: ALICE_UID,
      pinnedBy: [],
      archivedBy: [],
      typingUids: [],
      typingTimestamps: {},
      readByUids: [ALICE_UID],
      createdAt: pastTimestamp(),
      updatedAt: pastTimestamp(),
    })
  );

  // Bob starts typing
  await assertSucceeds(
    bobDb.collection("chats").doc(groupId).update({
      typingUids: [BOB_UID],
      typingTimestamps: { [BOB_UID]: pastTimestamp() },
      updatedAt: pastTimestamp(),
    })
  );

  // Alice also starts typing concurrently in group chat
  await assertSucceeds(
    aliceDb.collection("chats").doc(groupId).update({
      typingUids: [BOB_UID, ALICE_UID],
      typingTimestamps: { [BOB_UID]: pastTimestamp(), [ALICE_UID]: pastTimestamp() },
      updatedAt: pastTimestamp(),
    })
  );

  // Bob stops typing
  await assertSucceeds(
    bobDb.collection("chats").doc(groupId).update({
      typingUids: [ALICE_UID],
      typingTimestamps: { [ALICE_UID]: pastTimestamp() },
      updatedAt: pastTimestamp(),
    })
  );

  // Non-member Charlie cannot toggle typing
  await assertFails(
    charlieDb.collection("chats").doc(groupId).update({
      typingUids: [CHARLIE_UID],
      typingTimestamps: { [CHARLIE_UID]: pastTimestamp() },
      updatedAt: pastTimestamp(),
    })
  );
});

test("Read receipts: recipient updates delivered and read statuses/timestamps in direct and group chats, non-member denied", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const charlieDb = testEnv.authenticatedContext(CHARLIE_UID).firestore();

  const chatId = "chat_read_receipts";
  await assertSucceeds(
    aliceDb.collection("chats").doc(chatId).set({
      type: "direct",
      title: "Bob",
      memberUids: [ALICE_UID, BOB_UID],
      memberNames: { [ALICE_UID]: "Alice", [BOB_UID]: "Bob" },
      createdBy: ALICE_UID,
      lastMessageText: "Are you free?",
      lastMessageType: "text",
      lastMessageSenderId: ALICE_UID,
      pinnedBy: [],
      archivedBy: [],
      typingUids: [],
      typingTimestamps: {},
      deliveredByUids: [ALICE_UID],
      readByUids: [ALICE_UID],
      createdAt: pastTimestamp(),
      updatedAt: pastTimestamp(),
    })
  );

  const msgId = "msg_receipt_1";
  await assertSucceeds(
    aliceDb.collection("chats").doc(chatId).collection("messages").doc(msgId).set({
      chatId: chatId,
      senderId: ALICE_UID,
      senderName: "Alice",
      memberUids: [ALICE_UID, BOB_UID],
      type: "text",
      text: "Are you free?",
      deliveredByUids: [ALICE_UID],
      deliveredTimestamps: { [ALICE_UID]: pastTimestamp() },
      readByUids: [ALICE_UID],
      createdAt: pastTimestamp(),
      updatedAt: pastTimestamp(),
    })
  );

  // Bob's device syncs the message -> marks deliveredByUids + deliveredTimestamps before opening chat
  await assertSucceeds(
    bobDb.collection("chats").doc(chatId).update({
      deliveredByUids: [ALICE_UID, BOB_UID],
      updatedAt: pastTimestamp(),
    })
  );
  await assertSucceeds(
    bobDb.collection("chats").doc(chatId).collection("messages").doc(msgId).update({
      deliveredByUids: [ALICE_UID, BOB_UID],
      deliveredTimestamps: { [ALICE_UID]: pastTimestamp(), [BOB_UID]: pastTimestamp() },
      updatedAt: pastTimestamp(),
    })
  );

  // Bob opens chat and marks chat + message read with server/past timestamp
  await assertSucceeds(
    bobDb.collection("chats").doc(chatId).update({
      deliveredByUids: [ALICE_UID, BOB_UID],
      readByUids: [ALICE_UID, BOB_UID],
      updatedAt: pastTimestamp(),
    })
  );
  await assertSucceeds(
    bobDb.collection("chats").doc(chatId).collection("messages").doc(msgId).update({
      deliveredByUids: [ALICE_UID, BOB_UID],
      readByUids: [ALICE_UID, BOB_UID],
      readTimestamps: { [BOB_UID]: pastTimestamp() },
      updatedAt: pastTimestamp(),
    })
  );

  // Group chat delivery and read receipts across multiple members
  const groupId = "group_read_receipts";
  await assertSucceeds(
    aliceDb.collection("chats").doc(groupId).set({
      type: "group",
      title: "Project Sync",
      memberUids: [ALICE_UID, BOB_UID, CHARLIE_UID],
      memberNames: { [ALICE_UID]: "Alice", [BOB_UID]: "Bob", [CHARLIE_UID]: "Charlie" },
      createdBy: ALICE_UID,
      lastMessageText: "Standup in 5m",
      lastMessageType: "text",
      lastMessageSenderId: ALICE_UID,
      pinnedBy: [],
      archivedBy: [],
      typingUids: [],
      typingTimestamps: {},
      deliveredByUids: [ALICE_UID],
      readByUids: [ALICE_UID],
      createdAt: pastTimestamp(),
      updatedAt: pastTimestamp(),
    })
  );

  const grpMsgId = "grp_msg_receipt_1";
  await assertSucceeds(
    aliceDb.collection("chats").doc(groupId).collection("messages").doc(grpMsgId).set({
      chatId: groupId,
      senderId: ALICE_UID,
      senderName: "Alice",
      memberUids: [ALICE_UID, BOB_UID, CHARLIE_UID],
      type: "text",
      text: "Standup in 5m",
      deliveredByUids: [ALICE_UID],
      deliveredTimestamps: { [ALICE_UID]: pastTimestamp() },
      readByUids: [ALICE_UID],
      createdAt: pastTimestamp(),
      updatedAt: pastTimestamp(),
    })
  );

  // Bob receives (delivered) and Charlie reads in group chat
  await assertSucceeds(
    bobDb.collection("chats").doc(groupId).collection("messages").doc(grpMsgId).update({
      deliveredByUids: [ALICE_UID, BOB_UID],
      deliveredTimestamps: { [ALICE_UID]: pastTimestamp(), [BOB_UID]: pastTimestamp() },
      updatedAt: pastTimestamp(),
    })
  );
  await assertSucceeds(
    charlieDb.collection("chats").doc(groupId).collection("messages").doc(grpMsgId).update({
      deliveredByUids: [ALICE_UID, BOB_UID, CHARLIE_UID],
      deliveredTimestamps: {
        [ALICE_UID]: pastTimestamp(),
        [BOB_UID]: pastTimestamp(),
        [CHARLIE_UID]: pastTimestamp(),
      },
      readByUids: [ALICE_UID, CHARLIE_UID],
      readTimestamps: { [CHARLIE_UID]: pastTimestamp() },
      updatedAt: pastTimestamp(),
    })
  );

  // Recipient cannot tamper with message text while marking delivered or read
  await assertFails(
    bobDb.collection("chats").doc(chatId).collection("messages").doc(msgId).update({
      text: "Tampered",
      deliveredByUids: [ALICE_UID, BOB_UID],
      readByUids: [ALICE_UID, BOB_UID],
      updatedAt: pastTimestamp(),
    })
  );

  // Non-member Charlie cannot mark direct message delivered or read
  await assertFails(
    charlieDb.collection("chats").doc(chatId).collection("messages").doc(msgId).update({
      deliveredByUids: [ALICE_UID, BOB_UID, CHARLIE_UID],
      readByUids: [ALICE_UID, BOB_UID, CHARLIE_UID],
      updatedAt: pastTimestamp(),
    })
  );
});

test("Phone auth profile: E.164 phoneNumber validated and isolated to owner private doc", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  // Valid E.164 phoneNumber (+92 Pakistan) succeeds on owner private doc
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      phoneNumber: "+923001234567",
      displayName: "Amina",
      bio: "Available on PulseChat",
      darkModeEnabled: true,
      notificationsEnabled: true,
      createdAt: pastTimestamp(),
      updatedAt: pastTimestamp(),
    })
  );

  // Other user cannot read Alice's phoneNumber
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());

  // Malformed phoneNumber fails validation
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      phoneNumber: "0300-abc",
      updatedAt: pastTimestamp(),
    })
  );

  // Exposing phoneNumber in public_profiles fails strict field allowlist
  await assertFails(
    aliceDb.collection("public_profiles").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Amina",
      handle: "amina",
      phoneNumber: "+923001234567",
      isOnline: true,
      discoverable: true,
      createdAt: pastTimestamp(),
      updatedAt: pastTimestamp(),
    })
  );
});

test("Chat themes: owner can customize per-chat accent/background in private doc, other user denied", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice",
      darkModeEnabled: true,
      notificationsEnabled: true,
      chatThemes: {},
      createdAt: pastTimestamp(),
      updatedAt: pastTimestamp(),
    })
  );

  // Alice sets work/personal themes for specific conversations
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      "chatThemes.dm_work_1": "accent=cobalt;bg=work_grid;tag=work",
      "chatThemes.dm_personal_2": "accent=rose;bg=personal_waves;tag=personal",
      updatedAt: pastTimestamp(),
    })
  );

  // Bob cannot modify or read Alice's per-chat themes
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).update({
      "chatThemes.dm_work_1": "accent=amber;bg=aurora_glow;tag=vip",
      updatedAt: pastTimestamp(),
    })
  );
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
});

test("Voice & Video calls: participants exchange SDP/ICE signals and log history, outsiders denied", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const charlieDb = testEnv.authenticatedContext(CHARLIE_UID).firestore();

  const chatId = "chat_call_dm";
  await assertSucceeds(
    aliceDb.collection("chats").doc(chatId).set({
      type: "direct",
      title: "Alice & Bob",
      memberUids: [ALICE_UID, BOB_UID],
      memberNames: { [ALICE_UID]: "Alice", [BOB_UID]: "Bob" },
      createdBy: ALICE_UID,
      lastMessageText: "",
      lastMessageType: "text",
      lastMessageSenderId: ALICE_UID,
      pinnedBy: [],
      archivedBy: [],
      typingUids: [],
      readByUids: [ALICE_UID],
      createdAt: pastTimestamp(),
      updatedAt: pastTimestamp(),
    })
  );

  const callId = "call_session_1";
  await assertSucceeds(
    aliceDb.collection("calls").doc(callId).set({
      chatId: chatId,
      callerId: ALICE_UID,
      callerName: "Alice",
      calleeId: BOB_UID,
      calleeName: "Bob",
      participantUids: [ALICE_UID, BOB_UID],
      callType: "video",
      status: "calling",
      offerSdp: "v=0\r\no=- 123 2 IN IP4 127.0.0.1\r\n",
      createdAt: pastTimestamp(),
      updatedAt: pastTimestamp(),
    })
  );

  // Outsider Charlie cannot read, query, or hijack the call
  await assertFails(charlieDb.collection("calls").doc(callId).get());
  await assertFails(
    charlieDb.collection("calls").where("participantUids", "array-contains", ALICE_UID).get()
  );
  await assertFails(
    charlieDb.collection("calls").doc(callId).update({ status: "ended", updatedAt: pastTimestamp() })
  );

  // Callee Bob queries his calls, transitions to ringing -> connecting with answer SDP
  await assertSucceeds(
    bobDb.collection("calls").where("participantUids", "array-contains", BOB_UID).get()
  );
  await assertSucceeds(
    bobDb.collection("calls").doc(callId).update({
      status: "connecting",
      answerSdp: "v=0\r\no=- 456 2 IN IP4 127.0.0.1\r\n",
      updatedAt: pastTimestamp(),
    })
  );

  // ICE candidates exchange works for participants and denies Charlie
  await assertSucceeds(
    aliceDb.collection("calls").doc(callId).collection("candidates").doc("cand_1").set({
      senderId: ALICE_UID,
      participantUids: [ALICE_UID, BOB_UID],
      sdpMid: "0",
      sdpMLineIndex: 0,
      sdp: "candidate:1 1 UDP 2122252543 192.168.1.2 54321 typ host",
      createdAt: pastTimestamp(),
    })
  );
  await assertSucceeds(
    bobDb
      .collection("calls")
      .doc(callId)
      .collection("candidates")
      .where("participantUids", "array-contains", BOB_UID)
      .get()
  );
  await assertFails(
    charlieDb.collection("calls").doc(callId).collection("candidates").doc("cand_bad").set({
      senderId: CHARLIE_UID,
      participantUids: [ALICE_UID, BOB_UID],
      sdpMid: "0",
      sdpMLineIndex: 0,
      sdp: "candidate:evil",
      createdAt: pastTimestamp(),
    })
  );

  // Participants cannot tamper with immutable callerId/callType
  await assertFails(
    bobDb.collection("calls").doc(callId).update({
      callType: "voice",
      updatedAt: pastTimestamp(),
    })
  );

  // Ending call updates duration and status
  await assertSucceeds(
    aliceDb.collection("calls").doc(callId).update({
      status: "ended",
      endedBy: ALICE_UID,
      endReason: "hangup",
      durationSeconds: 42,
      endedAt: pastTimestamp(),
      updatedAt: pastTimestamp(),
    })
  );
});

// --- 5. Privacy & Security Rules Tests ---

test("Key bundles: owner publishes and rotates public keys, signed-in peers can read, others cannot overwrite", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  const bundlePayload = {
    userId: ALICE_UID,
    identityPublicKey: "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEAliceIdentityKey1234567890",
    signedPreKeyPublic: "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEAliceSignedPreKey1234567890",
    signedPreKeySignature: "MEUCIQAliceSignedPreKeySignatureBase64String1234567890",
    oneTimePreKeyPublic: "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEAliceOneTimePreKey123456789",
    keyFingerprint: "12345-67890-ABCDE",
    protocolVersion: 1,
    keyVersion: 1,
    createdAt: pastTimestamp(),
    updatedAt: pastTimestamp(),
  };

  await assertSucceeds(aliceDb.collection("key_bundles").doc(ALICE_UID).set(bundlePayload));
  await assertSucceeds(bobDb.collection("key_bundles").doc(ALICE_UID).get());
  await assertFails(
    bobDb.collection("key_bundles").doc(ALICE_UID).update({
      keyVersion: 2,
      updatedAt: pastTimestamp(),
    })
  );
});

test("Blocking & Block History: blocked users cannot send 1:1 messages or initiate calls; unblock restores access", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  const chatId = "chat_block_guard";
  await assertSucceeds(
    aliceDb.collection("chats").doc(chatId).set({
      type: "direct",
      title: "Alice & Bob",
      memberUids: [ALICE_UID, BOB_UID],
      memberNames: { [ALICE_UID]: "Alice", [BOB_UID]: "Bob" },
      createdBy: ALICE_UID,
      lastMessageText: "",
      lastMessageType: "text",
      lastMessageSenderId: ALICE_UID,
      pinnedBy: [],
      archivedBy: [],
      typingUids: [],
      readByUids: [ALICE_UID],
      createdAt: pastTimestamp(),
      updatedAt: pastTimestamp(),
    })
  );

  // Alice blocks Bob and writes immutable audit event
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("blocked_users").doc(BOB_UID).set({
      blockerUid: ALICE_UID,
      blockedUid: BOB_UID,
      blockedDisplayName: "Bob",
      blockedHandle: "bob",
      reason: "User blocked",
      createdAt: pastTimestamp(),
      updatedAt: pastTimestamp(),
    })
  );
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("block_history").doc("evt_1").set({
      blockerUid: ALICE_UID,
      targetUid: BOB_UID,
      targetDisplayName: "Bob",
      action: "blocked",
      reason: "User blocked",
      createdAt: pastTimestamp(),
    })
  );

  // Block history cannot be tampered with (update denied), and Bob cannot read Alice's block list
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("block_history").doc("evt_1").update({
      action: "unblocked",
    })
  );
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("blocked_users").doc(BOB_UID).get());

  // Bob is denied sending a direct message or starting a call while blocked
  await assertFails(
    bobDb.collection("chats").doc(chatId).collection("messages").doc("msg_blocked").set({
      chatId: chatId,
      senderId: BOB_UID,
      senderName: "Bob",
      memberUids: [ALICE_UID, BOB_UID],
      type: "text",
      text: "Blocked message",
      createdAt: pastTimestamp(),
    })
  );
  await assertFails(
    bobDb.collection("calls").doc("call_blocked").set({
      chatId: chatId,
      callerId: BOB_UID,
      callerName: "Bob",
      calleeId: ALICE_UID,
      calleeName: "Alice",
      participantUids: [BOB_UID, ALICE_UID],
      callType: "voice",
      status: "calling",
      createdAt: pastTimestamp(),
    })
  );

  // Alice unblocks Bob -> messaging works again
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("blocked_users").doc(BOB_UID).delete()
  );
  await assertSucceeds(
    bobDb.collection("chats").doc(chatId).collection("messages").doc("msg_unblocked").set({
      chatId: chatId,
      senderId: BOB_UID,
      senderName: "Bob",
      memberUids: [ALICE_UID, BOB_UID],
      type: "text",
      text: "Back again",
      createdAt: pastTimestamp(),
    })
  );
});

test("Disappearing messages: chat member can purge expired message but cannot purge unexpired message", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  const chatId = "chat_disappear";
  await assertSucceeds(
    aliceDb.collection("chats").doc(chatId).set({
      type: "direct",
      title: "Alice & Bob",
      memberUids: [ALICE_UID, BOB_UID],
      memberNames: { [ALICE_UID]: "Alice", [BOB_UID]: "Bob" },
      createdBy: ALICE_UID,
      disappearingDurationSeconds: 86400,
      disappearingUpdatedBy: ALICE_UID,
      disappearingUpdatedAt: pastTimestamp(),
      createdAt: pastTimestamp(),
      updatedAt: pastTimestamp(),
    })
  );

  // Expired message (epoch 1000ms)
  await assertSucceeds(
    aliceDb.collection("chats").doc(chatId).collection("messages").doc("msg_expired").set({
      chatId: chatId,
      senderId: ALICE_UID,
      senderName: "Alice",
      memberUids: [ALICE_UID, BOB_UID],
      type: "text",
      text: "Secret",
      expiresAtEpochMs: 1000,
      createdAt: pastTimestamp(),
    })
  );

  // Future-expiring message
  const futureEpoch = Date.now() + 86400000;
  await assertSucceeds(
    aliceDb.collection("chats").doc(chatId).collection("messages").doc("msg_live").set({
      chatId: chatId,
      senderId: ALICE_UID,
      senderName: "Alice",
      memberUids: [ALICE_UID, BOB_UID],
      type: "text",
      text: "Still valid",
      expiresAtEpochMs: futureEpoch,
      createdAt: pastTimestamp(),
    })
  );

  // Bob (recipient member) CANNOT delete unexpired message, CAN purge expired message
  await assertFails(
    bobDb.collection("chats").doc(chatId).collection("messages").doc("msg_live").delete()
  );
  await assertSucceeds(
    bobDb.collection("chats").doc(chatId).collection("messages").doc("msg_expired").delete()
  );
});

test("Security docs & Reports: owner-only 2FA/chat_lock/privacy_settings; admin-only report review", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const charlieDb = testEnv.authenticatedContext(CHARLIE_UID).firestore();

  // Security doc strictly isolated to owner
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("security").doc("privacy_settings").set({
      docType: "privacy_settings",
      lastSeenVisibility: "contacts",
      onlineVisibility: "everyone",
      screenshotMode: "block",
      defaultDisappearingSeconds: 604800,
      spamProtectionLevel: "strict",
      e2eeEnabled: true,
      createdAt: pastTimestamp(),
      updatedAt: pastTimestamp(),
    })
  );
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("security").doc("privacy_settings").get()
  );
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("security").doc("unknown_doc").set({
      docType: "unknown_doc",
      createdAt: pastTimestamp(),
    })
  );

  // Provision Charlie as admin via disabled-rules admin context
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("admins").doc(CHARLIE_UID).set({
      role: "moderator",
      createdAt: pastTimestamp(),
    });
  });

  // Users cannot self-promote to admin
  await assertFails(
    bobDb.collection("admins").doc(BOB_UID).set({ role: "moderator", createdAt: pastTimestamp() })
  );

  // Alice submits a spam report
  const reportId = "rep_1";
  await assertSucceeds(
    aliceDb.collection("reports").doc(reportId).set({
      reporterId: ALICE_UID,
      reporterName: "Alice",
      reportedUserId: BOB_UID,
      reportedUserName: "Bob",
      category: "spam",
      description: "Repeated unsolicited links",
      status: "pending",
      createdAt: pastTimestamp(),
      updatedAt: pastTimestamp(),
    })
  );

  // Alice can read her own report, Bob (reported user) cannot read or modify it
  await assertSucceeds(aliceDb.collection("reports").doc(reportId).get());
  await assertFails(bobDb.collection("reports").doc(reportId).get());
  await assertFails(
    aliceDb.collection("reports").doc(reportId).update({
      status: "resolved",
      updatedAt: pastTimestamp(),
    })
  );

  // Admin Charlie can list & update report review status
  await assertSucceeds(charlieDb.collection("reports").get());
  await assertSucceeds(
    charlieDb.collection("reports").doc(reportId).update({
      status: "resolved",
      reviewNotes: "Confirmed spam links; warning issued.",
      reviewedBy: CHARLIE_UID,
      updatedAt: pastTimestamp(),
    })
  );
});

test("Presence sessions: owner updates multi-device activeSessions, lastHeartbeatAt, and same_as_last_seen privacy; peers cannot spoof", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  await assertSucceeds(
    aliceDb.collection("public_profiles").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice",
      handle: "alice",
      isOnline: true,
      discoverable: true,
      lastSeenVisibility: "contacts",
      onlineVisibility: "same_as_last_seen",
      lastSeen: pastTimestamp(),
      lastHeartbeatAt: pastTimestamp(),
      activeSessions: {
        device_1: pastTimestamp(),
      },
      createdAt: pastTimestamp(),
      updatedAt: pastTimestamp(),
    })
  );

  // Add second device session and save matching privacy_settings doc
  await assertSucceeds(
    aliceDb.collection("public_profiles").doc(ALICE_UID).update({
      "activeSessions.device_2": pastTimestamp(),
      lastHeartbeatAt: pastTimestamp(),
      updatedAt: pastTimestamp(),
    })
  );
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("security").doc("privacy_settings").set({
      docType: "privacy_settings",
      lastSeenVisibility: "contacts",
      onlineVisibility: "same_as_last_seen",
      screenshotMode: "allow",
      defaultDisappearingSeconds: 0,
      spamProtectionLevel: "standard",
      e2eeEnabled: true,
      createdAt: pastTimestamp(),
      updatedAt: pastTimestamp(),
    })
  );

  // Device 1 goes offline while device 2 stays active
  await assertSucceeds(
    aliceDb.collection("public_profiles").doc(ALICE_UID).update({
      activeSessions: { device_2: pastTimestamp() },
      isOnline: true,
      updatedAt: pastTimestamp(),
    })
  );

  // Invalid onlineVisibility fails
  await assertFails(
    aliceDb.collection("public_profiles").doc(ALICE_UID).update({
      onlineVisibility: "invalid_mode",
      updatedAt: pastTimestamp(),
    })
  );

  // Oversized activeSessions (> 20) fails
  const tooManySessions = {};
  for (let i = 0; i < 21; i++) {
    tooManySessions[`dev_${i}`] = pastTimestamp();
  }
  await assertFails(
    aliceDb.collection("public_profiles").doc(ALICE_UID).update({
      activeSessions: tooManySessions,
      updatedAt: pastTimestamp(),
    })
  );

  // Bob cannot spoof Alice's presence or activeSessions
  await assertFails(
    bobDb.collection("public_profiles").doc(ALICE_UID).update({
      isOnline: false,
      activeSessions: {},
      updatedAt: pastTimestamp(),
    })
  );
});

test("Spark username index & locked auth collections: single-doc get without list, owner-only reservation, zero passwords", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const charlieDb = testEnv.authenticatedContext(CHARLIE_UID).firestore();

  await testEnv.withSecurityRulesDisabled(async (context) => {
    const adminFs = context.firestore();
    await adminFs.collection("admins").doc(CHARLIE_UID).set({
      role: "moderator",
      createdAt: pastTimestamp(),
    });
    await adminFs.collection("private_auth").doc(ALICE_UID).set({
      uid: ALICE_UID,
      username: "alice_sec",
      passwordHash: "reserved",
      createdAt: pastTimestamp(),
    });
    await adminFs.collection("auth_rate_limits").doc("bucket_1").set({
      count: 3,
      windowStartEpochMs: Date.now(),
      lockedUntilEpochMs: 0,
    });
  });

  // Legacy private_auth and auth_rate_limits stay completely denied
  for (const db of [unauthDb, aliceDb, charlieDb]) {
    await assertFails(db.collection("private_auth").doc(ALICE_UID).get());
    await assertFails(db.collection("private_auth").get());
    await assertFails(
      db.collection("private_auth").doc(ALICE_UID).set({ uid: ALICE_UID, username: "hack" })
    );
    await assertFails(db.collection("auth_rate_limits").doc("bucket_1").get());
    await assertFails(db.collection("auth_rate_limits").doc("bucket_1").delete());
  }

  // Unauthenticated user cannot claim or list usernames
  await assertFails(unauthDb.collection("usernames").get());
  await assertFails(
    unauthDb.collection("usernames").doc("alice_sec").set({
      username: "alice_sec",
      uid: ALICE_UID,
      authEmail: "alice@example.com",
      createdAt: pastTimestamp(),
    })
  );

  // Signed-in Alice can reserve her own lowercase username once
  await assertSucceeds(
    aliceDb.collection("usernames").doc("alice_sec").set({
      username: "alice_sec",
      uid: ALICE_UID,
      authEmail: "alice@example.com",
      emailVerified: false,
      createdAt: pastTimestamp(),
      updatedAt: pastTimestamp(),
    })
  );

  // Passwords/hashes must never be accepted in usernames docs
  await assertFails(
    bobDb.collection("usernames").doc("bob_sec").set({
      username: "bob_sec",
      uid: BOB_UID,
      authEmail: "bob@example.com",
      password: "plaintext-should-fail",
      createdAt: pastTimestamp(),
    })
  );

  // Listing usernames (directory scraping) is always denied; exact single-doc get works
  await assertFails(aliceDb.collection("usernames").get());
  await assertSucceeds(unauthDb.collection("usernames").doc("alice_sec").get());

  // Bob cannot overwrite or delete Alice's username
  await assertFails(
    bobDb.collection("usernames").doc("alice_sec").set({
      username: "alice_sec",
      uid: BOB_UID,
      authEmail: "bob@example.com",
      createdAt: pastTimestamp(),
    })
  );
  await assertFails(bobDb.collection("usernames").doc("alice_sec").delete());

  // Owner can set users/{uid} with empty/initial username once, then cannot spoof/change it later
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice Sec",
      darkModeEnabled: true,
      notificationsEnabled: true,
      createdAt: pastTimestamp(),
      updatedAt: pastTimestamp(),
    })
  );
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      username: "alice_sec",
      updatedAt: pastTimestamp(),
    })
  );
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      username: "spoofed_username",
      updatedAt: pastTimestamp(),
    })
  );
});

test("Bidirectional block markers (blocked_by): blocker sets own marker under target, target reads, third parties denied", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const charlieDb = testEnv.authenticatedContext(CHARLIE_UID).firestore();

  // Alice writes users/{bob}/blocked_by/{alice} when blocking Bob
  await assertSucceeds(
    aliceDb.collection("users").doc(BOB_UID).collection("blocked_by").doc(ALICE_UID).set({
      blockerUid: ALICE_UID,
      blockedUid: BOB_UID,
      createdAt: pastTimestamp(),
    })
  );

  // Bob can read his own blocked_by collection so he hides Alice's online/last seen
  await assertSucceeds(bobDb.collection("users").doc(BOB_UID).collection("blocked_by").get());
  await assertSucceeds(
    bobDb.collection("users").doc(BOB_UID).collection("blocked_by").doc(ALICE_UID).get()
  );

  // Alice and Charlie cannot read Bob's blocked_by list, and Charlie cannot forge Alice's marker
  await assertFails(aliceDb.collection("users").doc(BOB_UID).collection("blocked_by").get());
  await assertFails(charlieDb.collection("users").doc(BOB_UID).collection("blocked_by").get());
  await assertFails(
    charlieDb.collection("users").doc(BOB_UID).collection("blocked_by").doc(ALICE_UID).set({
      blockerUid: ALICE_UID,
      blockedUid: BOB_UID,
      createdAt: pastTimestamp(),
    })
  );
  await assertFails(
    charlieDb.collection("users").doc(BOB_UID).collection("blocked_by").doc(ALICE_UID).delete()
  );

  // Alice removes her marker when unblocking Bob
  await assertSucceeds(
    aliceDb.collection("users").doc(BOB_UID).collection("blocked_by").doc(ALICE_UID).delete()
  );
});



