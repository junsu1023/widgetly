import { beforeEach, describe, it, expect } from "vitest";
import { Firestore, Timestamp } from "firebase-admin/firestore";
import type { CallableRequest } from "firebase-functions/v2/https";
import { HttpsError } from "firebase-functions/v2/https";
import { getConnection, getConnectionLogic } from "../src/getConnection";
import { db as indexDb } from "../src/index";
import type { ConnectionDoc } from "../src/lib/types";

let firestore: Firestore;

const uidA = "user-a";
const uidB = "user-b";
const strangerUid = "stranger";
const connectionId = "conn-1";

const sentAt = Timestamp.fromDate(new Date("2026-02-03T04:05:06.000Z"));

async function clearCollection(name: string): Promise<void> {
  const docs = await firestore.collection(name).get();
  for (const doc of docs.docs) {
    await firestore.recursiveDelete(doc.ref);
  }
}

async function seedConnection(
  fs: Firestore,
  id: string,
  members: [string, string],
  overrides: Partial<ConnectionDoc> = {}
): Promise<void> {
  const connectionDoc: ConnectionDoc = {
    memberUids: members,
    type: "couple",
    status: "active",
    createdAt: Timestamp.now(),
    anniversaryDate: "2026-01-01",
    anniversaryUpdatedBy: members[0],
    streak: 7,
    streakUpdatedDate: "2026-01-01",
    lastSignalByUid: { [members[1]]: { presetType: "heart", sentAt } },
    ...overrides,
  };
  await fs.doc(`connections/${id}`).set(connectionDoc);
}

beforeEach(async () => {
  firestore = indexDb;
  await clearCollection("connections");
});

describe("getConnectionLogic", () => {
  it("returns the renderable fields with timestamps as ISO strings", async () => {
    await seedConnection(firestore, connectionId, [uidA, uidB]);

    const result = await getConnectionLogic(uidA, { connectionId }, firestore);

    expect(result).toEqual({
      status: "active",
      streak: 7,
      memberUids: [uidA, uidB],
      lastSignalByUid: {
        [uidB]: { presetType: "heart", sentAt: "2026-02-03T04:05:06.000Z" },
      },
    });
  });

  it("returns an empty lastSignalByUid for a connection with no signals yet", async () => {
    await seedConnection(firestore, connectionId, [uidA, uidB], { lastSignalByUid: {} });

    const result = await getConnectionLogic(uidA, { connectionId }, firestore);

    expect(result.lastSignalByUid).toEqual({});
  });

  it("reports a disconnected connection rather than hiding it", async () => {
    await seedConnection(firestore, connectionId, [uidA, uidB], { status: "disconnected" });

    const result = await getConnectionLogic(uidA, { connectionId }, firestore);

    expect(result.status).toBe("disconnected");
  });

  it("throws not-found for a connection that does not exist", async () => {
    try {
      await getConnectionLogic(uidA, { connectionId: "nope" }, firestore);
      expect.fail("Should have thrown");
    } catch (error) {
      expect(error).toBeInstanceOf(HttpsError);
      expect((error as HttpsError).code).toBe("not-found");
    }
  });

  it("throws permission-denied for a non-member (the Admin SDK bypasses rules)", async () => {
    await seedConnection(firestore, connectionId, [uidA, uidB]);
    try {
      await getConnectionLogic(strangerUid, { connectionId }, firestore);
      expect.fail("Should have thrown");
    } catch (error) {
      expect(error).toBeInstanceOf(HttpsError);
      expect((error as HttpsError).code).toBe("permission-denied");
    }
  });

  it("throws invalid-argument for a connectionId that escapes the collection", async () => {
    // "connections/a/signals/x" is a perfectly valid document path, so without an
    // explicit guard this would read a signal doc and misread its shape.
    try {
      await getConnectionLogic(uidA, { connectionId: "a/signals/x" }, firestore);
      expect.fail("Should have thrown");
    } catch (error) {
      expect(error).toBeInstanceOf(HttpsError);
      expect((error as HttpsError).code).toBe("invalid-argument");
    }
  });
});

describe("getConnection callable", () => {
  it("without auth context throws HttpsError with unauthenticated code", async () => {
    const request: CallableRequest<{ connectionId: string }> = {
      data: { connectionId },
    };
    try {
      await getConnection.run(request);
      expect.fail("Should have thrown");
    } catch (error) {
      expect(error).toBeInstanceOf(HttpsError);
      expect((error as HttpsError).code).toBe("unauthenticated");
    }
  });

  it("with a missing connectionId throws HttpsError with invalid-argument code", async () => {
    const request: CallableRequest<{ connectionId: string }> = {
      data: { connectionId: "" },
      auth: { uid: uidA, token: {} as never, rawToken: "fake-token" },
    };
    try {
      await getConnection.run(request);
      expect.fail("Should have thrown");
    } catch (error) {
      expect(error).toBeInstanceOf(HttpsError);
      expect((error as HttpsError).code).toBe("invalid-argument");
    }
  });

  it("with valid auth returns the connection for a member", async () => {
    await seedConnection(firestore, connectionId, [uidA, uidB]);
    const request: CallableRequest<{ connectionId: string }> = {
      data: { connectionId },
      auth: { uid: uidA, token: {} as never, rawToken: "fake-token" },
    };

    const result = await getConnection.run(request);

    expect(result.streak).toBe(7);
    expect(result.memberUids).toEqual([uidA, uidB]);
  });
});
