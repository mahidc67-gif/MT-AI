const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";
const ADMIN_UID = "admin_001";
const ADMIN_EMAIL = "mahidc67@gmail.com";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function validUserPayload(uid, overrides = {}) {
  const now = new Date(Date.now() - 2000);
  return {
    userId: uid,
    displayName: "Alice Tester",
    email: "alice@example.com",
    role: "USER",
    plan: "FREE",
    status: "ACTIVE",
    preferredLanguage: "English",
    themePreference: "DARK",
    memoryEnabled: true,
    preferredModel: "gemini-3.8-flash",
    dailyRequestsUsed: 0,
    lastRequestDate: "2026-11-22",
    totalRequests: 0,
    createdAt: now,
    updatedAt: now,
    ...overrides,
  };
}

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

test("Unauthenticated users cannot read or write any collection", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).set(validUserPayload(ALICE_UID)));
  await assertFails(unauthDb.collection("system_config").doc("settings").get());
  await assertFails(unauthDb.collection("announcements").where("active", "==", true).get());
});

test("User profile creation prevents role/plan/status escalation and shadow fields", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID, { email: "alice@example.com", email_verified: true }).firestore();

  // Valid FREE/USER/ACTIVE profile succeeds
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).set(validUserPayload(ALICE_UID)));

  // Reject self-promotion to ADMIN or PRO
  await assertFails(
    aliceDb.collection("users").doc("alice_escalate").set(validUserPayload("alice_escalate", { role: "ADMIN" }))
  );
  await assertFails(
    aliceDb.collection("users").doc("alice_pro").set(validUserPayload("alice_pro", { plan: "PRO" }))
  );

  // Reject Ghost / @DocumentId id field
  await assertFails(
    aliceDb.collection("users").doc("alice_ghost").set(validUserPayload("alice_ghost", { id: "alice_ghost" }))
  );
});

test("User profile updates restrict owners to preferences/usage and block PII leaks", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("users").doc(ALICE_UID).set(validUserPayload(ALICE_UID));
    await ctx.firestore().collection("users").doc(BOB_UID).set(validUserPayload(BOB_UID, { email: "bob@example.com" }));
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID, { email: "alice@example.com", email_verified: true }).firestore();

  // Owner preference update succeeds
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      preferredLanguage: "Bengali",
      themePreference: "LIGHT",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Owner usage increment succeeds
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      dailyRequestsUsed: 1,
      totalRequests: 1,
      lastRequestDate: "2026-11-22",
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Owner cannot elevate own plan or role
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      plan: "PRO",
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Cross-user PII read or list fails
  await assertFails(aliceDb.collection("users").doc(BOB_UID).get());
  await assertFails(aliceDb.collection("users").get());
});

test("Conversations, messages, and memories isolate strictly per owner", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const now = new Date(Date.now() - 2000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("conversations").doc("conv_1").set({
      userId: ALICE_UID,
      title: "Kotlin Help",
      modelId: "gemini-3.8-flash",
      lastMessagePreview: "How do coroutines work?",
      messageCount: 1,
      pinned: false,
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("conversations").doc("conv_1")
      .collection("messages").doc("msg_1").set({
        userId: ALICE_UID,
        role: "user",
        content: "Explain Kotlin Flow",
        modelId: "gemini-3.8-flash",
        edited: false,
        citations: [],
        createdAt: now,
        updatedAt: now,
      })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("memories").doc("mem_1").set({
      userId: ALICE_UID,
      fact: "Prefers Kotlin Jetpack Compose examples",
      category: "Coding",
      active: true,
      createdAt: now,
      updatedAt: now,
    })
  );

  // List queries succeed for owner, fail for Bob
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("conversations").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("conversations").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("memories").get());
});

test("Admin email spoofing (unverified) is rejected; verified admin manages users, config, and announcements", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("users").doc(ALICE_UID).set(validUserPayload(ALICE_UID));
  });

  // Spoofed admin with email_verified: false MUST fail
  const spoofedDb = testEnv.authenticatedContext("spoof_999", {
    email: ADMIN_EMAIL,
    email_verified: false,
  }).firestore();
  await assertFails(spoofedDb.collection("users").get());
  await assertFails(spoofedDb.collection("system_config").doc("settings").set({
    defaultModel: "gemini-3.8-flash",
    enabledModels: ["gemini-3.8-flash"],
    freeDailyLimit: 25,
    proDailyLimit: 500,
    fallbackEnabled: true,
    maintenanceMode: false,
    updatedBy: "spoof_999",
    updatedAt: new Date(Date.now() - 1000),
  }));

  // Verified admin succeeds
  const adminDb = testEnv.authenticatedContext(ADMIN_UID, {
    email: ADMIN_EMAIL,
    email_verified: true,
  }).firestore();

  await assertSucceeds(adminDb.collection("users").get());
  await assertSucceeds(
    adminDb.collection("users").doc(ALICE_UID).update({
      plan: "PRO",
      status: "SUSPENDED",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  await assertSucceeds(
    adminDb.collection("system_config").doc("settings").set({
      defaultModel: "gemini-3.8-flash",
      enabledModels: ["gemini-3.8-flash", "gemini-3.5-flash-lite", "gemini-4-argon"],
      freeDailyLimit: 30,
      proDailyLimit: 500,
      fallbackEnabled: true,
      maintenanceMode: false,
      updatedBy: ADMIN_UID,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  await assertSucceeds(
    adminDb.collection("announcements").doc("ann_1").set({
      title: "MT AI 2.0 Live",
      body: "Multimodal vision & coding lab enabled.",
      severity: "UPDATE",
      active: true,
      createdBy: ADMIN_UID,
      createdAt: new Date(Date.now() - 1000),
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Regular user can query active announcements (filtered), but cannot query unfiltered or read error_logs
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(aliceDb.collection("announcements").where("active", "==", true).get());
  await assertFails(aliceDb.collection("announcements").get());
  await assertFails(aliceDb.collection("error_logs").get());

  // Regular user can log their own error
  await assertSucceeds(
    aliceDb.collection("error_logs").doc("err_1").set({
      userId: ALICE_UID,
      userEmail: "alice@example.com",
      source: "ChatAI",
      modelId: "gemini-4-argon",
      message: "Quota exceeded",
      resolved: false,
      createdAt: new Date(Date.now() - 1000),
    })
  );
  await assertSucceeds(adminDb.collection("error_logs").get());
});
