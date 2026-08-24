// Regression tests for firestore.rules' invitations rules, run against the Firestore emulator only
// (never the live project) - see package.json's "test" script, which wraps this in
// `firebase emulators:exec`.
//
// Real bug this covers: an invited user who is NOT YET a group member got PERMISSION_DENIED
// reading their own PENDING invitation through FirestoreGroupCloudRepository.observeIncomingInvitations
// (a collectionGroup("invitations") query filtered by inviteeEmail + status). The rules' nested
// match under groups/{groupId}/invitations/{invitationId} correctly authorizes get() and a
// collection() query scoped to one group, but Firestore does NOT extend that nested rule to a
// collectionGroup() query across every group - that requires a separate rule declared with the
// recursive wildcard {path=**}. Verified empirically: with only the nested rule, get() on the
// exact invitation succeeded while the identical document read through the collectionGroup query
// was denied with "No matching allow statements". The fix adds a {path=**}/invitations/{id} rule
// (see firestore.rules) authorizing `list` under the same invitee-email-match condition.
const {
  initializeTestEnvironment,
  assertSucceeds,
  assertFails,
} = require("@firebase/rules-unit-testing");
const {
  collectionGroup,
  query,
  where,
  getDocs,
  doc,
  getDoc,
} = require("firebase/firestore");
const fs = require("fs");
const path = require("path");

const GROUP_ID = "group1";
const INVITATION_ID = "inv1";
const INVITEE_EMAIL = "bob@example.com";

let testEnv;
let failures = 0;

function check(name, condition) {
  if (condition) {
    console.log(`PASS: ${name}`);
  } else {
    failures++;
    console.error(`FAIL: ${name}`);
  }
}

async function seedInvitation() {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    const db = context.firestore();
    await db.doc(`groups/${GROUP_ID}`).set({ name: "Trip", ownerUid: "uidA", createdAt: new Date() });
    await db.doc(`groups/${GROUP_ID}/members/uidA`).set({
      role: "OWNER", displayName: "Alice", email: "alice@example.com", joinedAt: new Date(),
    });
    await db.doc(`groups/${GROUP_ID}/invitations/${INVITATION_ID}`).set({
      groupName: "Trip",
      inviterUid: "uidA",
      inviterEmail: "alice@example.com",
      inviteeEmail: INVITEE_EMAIL,
      status: "PENDING",
      createdAt: new Date(),
      respondedAt: null,
    });
  });
}

// The exact query FirestoreGroupCloudRepository.observeIncomingInvitations issues, reproduced
// against the emulator instead of a fake, so it exercises the REAL rules engine.
function incomingInvitationsQuery(db, email) {
  return query(
    collectionGroup(db, "invitations"),
    where("inviteeEmail", "==", email),
    where("status", "==", "PENDING")
  );
}

async function main() {
  testEnv = await initializeTestEnvironment({
    projectId: "spendwise-rules-test",
    firestore: { rules: fs.readFileSync(path.resolve(__dirname, "../firestore.rules"), "utf8") },
  });

  await seedInvitation();

  // 1. The regression scenario: B is invited but not yet a group member, and must be able to read
  // their own PENDING invitation through the same collectionGroup query the app uses.
  {
    const db = testEnv.authenticatedContext("uidB", { email: INVITEE_EMAIL }).firestore();
    const snap = await assertSucceeds(getDocs(incomingInvitationsQuery(db, INVITEE_EMAIL)));
    check("invited non-member can read their own PENDING invitation via collectionGroup query", snap.size === 1);
    await assertSucceeds(getDoc(doc(db, `groups/${GROUP_ID}/invitations/${INVITATION_ID}`)));
  }

  // 2. Case-insensitive email matching still works (the invite typed with different case/whitespace
  // than the account's own token email claim) - regression coverage for the earlier .lower() fix,
  // now exercised through the collectionGroup query path too.
  {
    const db = testEnv.authenticatedContext("uidB", { email: "BOB@EXAMPLE.COM" }).firestore();
    const snap = await assertSucceeds(getDocs(incomingInvitationsQuery(db, INVITEE_EMAIL)));
    check("case-different email claim still matches via the collectionGroup query", snap.size === 1);
  }

  // 3. Security regression guard: a signed-in user who is NOT the invitee must never see this
  // invitation, proving the {path=**} fix didn't widen access beyond the intended invitee.
  {
    const db = testEnv.authenticatedContext("uidC", { email: "someone-else@example.com" }).firestore();
    await assertFails(getDocs(incomingInvitationsQuery(db, INVITEE_EMAIL)));
    check("a non-invitee is denied the same collectionGroup query", true);
  }

  // 4. An unauthenticated read must be denied outright.
  {
    const db = testEnv.unauthenticatedContext().firestore();
    await assertFails(getDocs(incomingInvitationsQuery(db, INVITEE_EMAIL)));
    check("an unauthenticated read is denied", true);
  }

  await testEnv.cleanup();

  if (failures > 0) {
    console.error(`\n${failures} check(s) failed.`);
    process.exit(1);
  }
  console.log("\nAll checks passed.");
}

main().catch((e) => {
  console.error("FATAL:", e);
  process.exit(1);
});
