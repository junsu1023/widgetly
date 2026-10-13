import { beforeEach, describe, it, expect } from "vitest";
import { Firestore, Timestamp } from "firebase-admin/firestore";
import { updateMascotLogic } from "../src/updateMascot";
import { db as indexDb } from "../src/index";
import { HttpsError } from "firebase-functions/v2/https";
import type { ConnectionDoc } from "../src/lib/types";

let firestore: Firestore;

const uidA = "user-a";
const uidB = "user-b";
const connectionId = "conn-1";

async function clearCollection(name: string): Promise<void> {
  const docs = await firestore.collection(name).get();
  for (const doc of docs.docs) {
    await firestore.recursiveDelete(doc.ref);
  }
}

async function seedConnection(
  fs: Firestore,
  id: string,
  members: [string, string]
): Promise<void> {
  const connectionDoc: ConnectionDoc = {
    memberUids: members,
    type: "couple",
    status: "active",
    createdAt: Timestamp.now(),
    anniversaryDate: "2026-01-01",
    anniversaryUpdatedBy: members[0],
    streak: 0,
    streakUpdatedDate: "",
    lastSignalByUid: {},
  };
  await fs.doc(`connections/${id}`).set(connectionDoc);
}

beforeEach(async () => {
  firestore = indexDb;
  await clearCollection("connections");
});

describe("updateMascotLogic", () => {
  it("writes mascotByUid.{uid} on an active connection the caller belongs to", async () => {
    await seedConnection(firestore, connectionId, [uidA, uidB]);
    await updateMascotLogic(uidA, { connectionId, mascotId: "cat" }, firestore);
    const conn = await firestore.doc(`connections/${connectionId}`).get();
    expect(conn.get("mascotByUid")[uidA]).toBe("cat");
  });

  it("rejects an id outside the 5-value catalog with invalid-argument", async () => {
    await seedConnection(firestore, connectionId, [uidA, uidB]);
    try {
      await updateMascotLogic(uidA, { connectionId, mascotId: "dragon" }, firestore);
      expect.fail("Should have thrown");
    } catch (error) {
      expect(error).toBeInstanceOf(HttpsError);
      expect((error as HttpsError).code).toBe("invalid-argument");
    }
    const conn = await firestore.doc(`connections/${connectionId}`).get();
    expect(conn.get("mascotByUid")).toBeUndefined();
  });

  it("rejects a caller who is not a member of the connection", async () => {
    await seedConnection(firestore, connectionId, [uidA, uidB]);
    await expect(
      updateMascotLogic("user-c", { connectionId, mascotId: "cat" }, firestore)
    ).rejects.toThrow(HttpsError);
  });

  it("rejects when the connection does not exist", async () => {
    await expect(
      updateMascotLogic(uidA, { connectionId: "no-such-connection", mascotId: "cat" }, firestore)
    ).rejects.toThrow(HttpsError);
  });

  it("does not touch the partner's existing mascotByUid entry", async () => {
    await seedConnection(firestore, connectionId, [uidA, uidB]);
    await updateMascotLogic(uidB, { connectionId, mascotId: "bear" }, firestore);
    await updateMascotLogic(uidA, { connectionId, mascotId: "rabbit" }, firestore);
    const conn = await firestore.doc(`connections/${connectionId}`).get();
    const mascotByUid = conn.get("mascotByUid");
    expect(mascotByUid[uidA]).toBe("rabbit");
    expect(mascotByUid[uidB]).toBe("bear");
  });
});
