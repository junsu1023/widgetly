import { assertFails, assertSucceeds } from "@firebase/rules-unit-testing";
import { afterAll, beforeEach, describe, expect, it } from "vitest";
import { authedDb, getTestEnv, seedConnection, teardownTestEnv } from "./setup";

const userAUid = "user-a";
const userBUid = "user-b";
const strangerUid = "stranger";
const connectionId = "connection-1";

describe("firestore.rules", () => {
  beforeEach(async () => {
    const env = await getTestEnv();
    await env.clearFirestore();
  });

  afterAll(async () => {
    await teardownTestEnv();
  });

  it("lets a user update only their own nickname", async () => {
    const db = await authedDb(userAUid);
    await assertSucceeds(db.doc(`users/${userAUid}`).set({ nickname: "A" }));
    await assertFails(
      db.doc(`users/${userAUid}`).set({ nickname: "A", ownedThemes: ["x"] })
    );
  });

  it("denies writing another user's doc", async () => {
    const db = await authedDb(userAUid);
    await assertFails(db.doc(`users/${userBUid}`).set({ nickname: "hacked" }));
  });

  it("lets a connection member read the connection but not write it", async () => {
    await seedConnection(connectionId, [userAUid, userBUid]);
    const db = await authedDb(userAUid);
    await assertSucceeds(db.doc(`connections/${connectionId}`).get());
    await assertFails(db.doc(`connections/${connectionId}`).update({ streak: 999 }));
  });

  it("lets any signed-in user get a connection doc that does not exist", async () => {
    // A typo'd/stale connectionId must come back as a non-existent snapshot, not
    // PERMISSION_DENIED - the widget needs to render "연결 끊김" rather than throw.
    const db = await authedDb(strangerUid);
    const snap = await assertSucceeds(db.doc("connections/does-not-exist").get());
    expect(snap.exists).toBe(false);
  });

  it("denies a non-member from reading the connection", async () => {
    await seedConnection(connectionId, [userAUid, userBUid]);
    const db = await authedDb(strangerUid);
    await assertFails(db.doc(`connections/${connectionId}`).get());
  });

  it("denies any client write to signals, even by a connection member", async () => {
    await seedConnection(connectionId, [userAUid, userBUid]);
    const db = await authedDb(userAUid);
    await assertFails(
      db
        .collection(`connections/${connectionId}/signals`)
        .add({ senderUid: userAUid, presetType: "heart" })
    );
  });
});
