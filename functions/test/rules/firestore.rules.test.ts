import { assertFails, assertSucceeds } from "@firebase/rules-unit-testing";
import { afterAll, beforeEach, describe, it } from "vitest";
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
