import { beforeEach, describe, it, expect, vi } from "vitest";
import { Firestore, Timestamp } from "firebase-admin/firestore";
import type { Messaging } from "firebase-admin/messaging";
import { createPairingCodeLogic, createPairingCode } from "../src/createPairingCode";
import { redeemPairingCodeLogic, redeemPairingCode } from "../src/redeemPairingCode";
import { db as indexDb } from "../src/index";
import { upsertToken } from "../src/lib/tokens";
import type { CallableRequest } from "firebase-functions/v2/https";
import { HttpsError } from "firebase-functions/v2/https";
import type { ConnectionDoc } from "../src/lib/types";

let firestore: Firestore;

const uidA = "user-a";
const uidB = "user-b";

async function clearCollection(name: string): Promise<void> {
  const docs = await firestore.collection(name).get();
  for (const doc of docs.docs) {
    await doc.ref.delete();
  }
}

async function seedActiveCoupleConnection(fs: Firestore, uid: string): Promise<void> {
  const connectionDoc: ConnectionDoc = {
    memberUids: [uid, "some-other-uid"],
    type: "couple",
    status: "active",
    createdAt: Timestamp.now(),
    anniversaryDate: "2026-01-01",
    anniversaryUpdatedBy: uid,
    streak: 0,
    streakUpdatedDate: "",
    lastSignalByUid: {},
  };
  await fs.collection("connections").add(connectionDoc);
}

beforeEach(async () => {
  firestore = indexDb;
  await clearCollection("pairingCodes");
  await clearCollection("codeAttempts");
  await clearCollection("connections");
  await firestore.recursiveDelete(firestore.collection(`users/${uidA}/tokens`));
});

describe("createPairingCodeLogic", () => {
  const now = Timestamp.now();

  it("creates a 6-digit code document that expires in 10 minutes", async () => {
    const { code } = await createPairingCodeLogic(uidA, { type: "friend" }, firestore, now);
    expect(code).toMatch(/^\d{6}$/);
    const doc = await firestore.doc(`pairingCodes/${code}`).get();
    expect(doc.get("creatorUid")).toBe(uidA);
    expect(doc.get("type")).toBe("friend");
    expect(doc.get("expiresAt").toMillis()).toBe(now.toMillis() + 10 * 60 * 1000);
  });

  it("rejects an invalid type", async () => {
    await expect(
      createPairingCodeLogic(uidA, { type: "bogus" as never }, firestore, now)
    ).rejects.toThrow(HttpsError);
  });
});

describe("redeemPairingCodeLogic", () => {
  const now = Timestamp.now();

  it("creates a connection with both uids and deletes the code", async () => {
    const { code } = await createPairingCodeLogic(uidA, { type: "friend" }, firestore, now);
    const { connectionId } = await redeemPairingCodeLogic(uidB, { code }, firestore, now);
    const conn = await firestore.doc(`connections/${connectionId}`).get();
    expect(conn.get("memberUids")).toEqual(expect.arrayContaining([uidA, uidB]));
    expect((await firestore.doc(`pairingCodes/${code}`).get()).exists).toBe(false);
  });

  it("rejects an expired code and records a failed attempt", async () => {
    const { code } = await createPairingCodeLogic(uidA, { type: "friend" }, firestore, now);
    const later = Timestamp.fromMillis(now.toMillis() + 11 * 60 * 1000);
    await expect(redeemPairingCodeLogic(uidB, { code }, firestore, later)).rejects.toThrow();
    const attempts = await firestore.doc(`codeAttempts/${uidB}`).get();
    expect(attempts.get("failCount")).toBe(1);
  });

  it("locks out after 5 failed attempts within the window", async () => {
    for (let i = 0; i < 5; i++) {
      await expect(
        redeemPairingCodeLogic(uidB, { code: "wrong0" + i }, firestore, now)
      ).rejects.toThrow();
    }
    await expect(
      redeemPairingCodeLogic(uidB, { code: "wrong06" }, firestore, now)
    ).rejects.toThrow(/locked/i);
  });

  it("blocks a VALID code redemption outright once the caller is locked out (lockout must be enforced, not just recorded)", async () => {
    for (let i = 0; i < 5; i++) {
      await expect(
        redeemPairingCodeLogic(uidB, { code: "wrong0" + i }, firestore, now)
      ).rejects.toThrow();
    }
    const { code } = await createPairingCodeLogic(uidA, { type: "friend" }, firestore, now);

    try {
      await redeemPairingCodeLogic(uidB, { code }, firestore, now);
      expect.fail("Should have thrown");
    } catch (error) {
      expect(error).toBeInstanceOf(HttpsError);
      expect((error as HttpsError).code).toBe("resource-exhausted");
    }

    // No connection should have been created, and the valid code must still
    // exist untouched (it was never consumed).
    const connections = await firestore.collection("connections").get();
    expect(connections.empty).toBe(true);
    const codeDoc = await firestore.doc(`pairingCodes/${code}`).get();
    expect(codeDoc.exists).toBe(true);
  });

  it("treats a Firestore-path-breaking code (containing '/') as a normal failed guess, not a raw Error", async () => {
    await expect(
      redeemPairingCodeLogic(uidB, { code: "a/b" }, firestore, now)
    ).rejects.toThrow(HttpsError);
    const attempts = await firestore.doc(`codeAttempts/${uidB}`).get();
    expect(attempts.get("failCount")).toBe(1);
  });

  it("rejects a couple code when the redeemer already has an active couple connection", async () => {
    await seedActiveCoupleConnection(firestore, uidB);
    const { code } = await createPairingCodeLogic(uidA, { type: "couple" }, firestore, now);
    await expect(redeemPairingCodeLogic(uidB, { code }, firestore, now)).rejects.toThrow(
      /already-exists/
    );
  });

  it("rejects a couple code when the CREATOR already has an active couple connection, even though the redeemer doesn't", async () => {
    await seedActiveCoupleConnection(firestore, uidA);
    const { code } = await createPairingCodeLogic(uidA, { type: "couple" }, firestore, now);
    await expect(redeemPairingCodeLogic(uidB, { code }, firestore, now)).rejects.toThrow(
      /already-exists/
    );
    // Only the originally seeded connection should exist — no second couple
    // connection was created for the creator.
    const connections = await firestore.collection("connections").get();
    expect(connections.size).toBe(1);
  });

  it("computes anniversaryDate using the Seoul-local date, not UTC", async () => {
    const createdAt = Timestamp.fromDate(new Date("2026-01-01T15:25:00Z"));
    const { code } = await createPairingCodeLogic(uidA, { type: "friend" }, firestore, createdAt);
    // 2026-01-01 15:30 UTC == 2026-01-02 00:30 KST (UTC+9) — same boundary
    // timestamp used in sendSignal.test.ts's toSeoulDateString tests.
    const redeemTime = Timestamp.fromDate(new Date("2026-01-01T15:30:00Z"));
    const { connectionId } = await redeemPairingCodeLogic(uidB, { code }, firestore, redeemTime);
    const conn = await firestore.doc(`connections/${connectionId}`).get();
    expect(conn.get("anniversaryDate")).toBe("2026-01-02");
  });

  it("notifies the code creator with a push containing the new connectionId on successful redemption", async () => {
    const send = vi.fn().mockResolvedValue("ok");
    const fakeMessaging = { send } as unknown as Messaging;
    await upsertToken(firestore, uidA, "creator-token", "android");

    const { code } = await createPairingCodeLogic(uidA, { type: "friend" }, firestore, now);
    const { connectionId } = await redeemPairingCodeLogic(
      uidB,
      { code },
      firestore,
      now,
      fakeMessaging
    );

    expect(send).toHaveBeenCalledTimes(1);
    const sentMessage = send.mock.calls[0][0];
    expect(sentMessage.token).toBe("creator-token");
    expect(sentMessage.data).toEqual({ connectionId });
  });

  it("does not count already-exists rejections toward the lockout counter", async () => {
    await seedActiveCoupleConnection(firestore, uidB);
    const { code } = await createPairingCodeLogic(uidA, { type: "couple" }, firestore, now);
    await expect(redeemPairingCodeLogic(uidB, { code }, firestore, now)).rejects.toThrow();
    const attempts = await firestore.doc(`codeAttempts/${uidB}`).get();
    expect(attempts.exists).toBe(false);
  });

  it("rejects redeeming your own code without treating it as a guess failure", async () => {
    const { code } = await createPairingCodeLogic(uidA, { type: "friend" }, firestore, now);
    await expect(redeemPairingCodeLogic(uidA, { code }, firestore, now)).rejects.toThrow();
    const attempts = await firestore.doc(`codeAttempts/${uidA}`).get();
    expect(attempts.exists).toBe(false);
  });

  it("clears prior failed attempts after a successful redemption", async () => {
    await expect(
      redeemPairingCodeLogic(uidB, { code: "wrong00" }, firestore, now)
    ).rejects.toThrow();
    const { code } = await createPairingCodeLogic(uidA, { type: "friend" }, firestore, now);
    await redeemPairingCodeLogic(uidB, { code }, firestore, now);
    const attempts = await firestore.doc(`codeAttempts/${uidB}`).get();
    expect(attempts.exists).toBe(false);
  });

  it("does not redeem the same code twice concurrently (uniqueness holds under a race)", async () => {
    const { code } = await createPairingCodeLogic(uidA, { type: "friend" }, firestore, now);
    const results = await Promise.allSettled([
      redeemPairingCodeLogic(uidB, { code }, firestore, now),
      redeemPairingCodeLogic("user-c", { code }, firestore, now),
    ]);
    const fulfilled = results.filter((r) => r.status === "fulfilled");
    const rejected = results.filter((r) => r.status === "rejected");
    expect(fulfilled).toHaveLength(1);
    expect(rejected).toHaveLength(1);
  });
});

describe("createPairingCode (callable)", () => {
  it("without auth context throws HttpsError with unauthenticated code", async () => {
    const request: CallableRequest<{ type: "couple" | "friend" }> = {
      data: { type: "friend" },
    };
    try {
      await createPairingCode.run(request);
      expect.fail("Should have thrown");
    } catch (error) {
      expect(error).toBeInstanceOf(HttpsError);
      expect((error as HttpsError).code).toBe("unauthenticated");
    }
  });

  it("with invalid type throws HttpsError with invalid-argument code", async () => {
    const request: CallableRequest<{ type: "couple" | "friend" }> = {
      data: { type: "bogus" as never },
      auth: { uid: uidA, token: {} as never, rawToken: "fake-token" },
    };
    try {
      await createPairingCode.run(request);
      expect.fail("Should have thrown");
    } catch (error) {
      expect(error).toBeInstanceOf(HttpsError);
      expect((error as HttpsError).code).toBe("invalid-argument");
    }
  });

  it("with valid auth and type creates a code document under the caller's uid", async () => {
    const request: CallableRequest<{ type: "couple" | "friend" }> = {
      data: { type: "friend" },
      auth: { uid: uidA, token: {} as never, rawToken: "fake-token" },
    };
    const { code } = await createPairingCode.run(request);
    const doc = await firestore.doc(`pairingCodes/${code}`).get();
    expect(doc.get("creatorUid")).toBe(uidA);
  });
});

describe("redeemPairingCode (callable)", () => {
  it("without auth context throws HttpsError with unauthenticated code", async () => {
    const request: CallableRequest<{ code: string }> = {
      data: { code: "123456" },
    };
    try {
      await redeemPairingCode.run(request);
      expect.fail("Should have thrown");
    } catch (error) {
      expect(error).toBeInstanceOf(HttpsError);
      expect((error as HttpsError).code).toBe("unauthenticated");
    }
  });

  it("with missing code throws HttpsError with invalid-argument code", async () => {
    const request: CallableRequest<{ code: string }> = {
      data: { code: "" },
      auth: { uid: uidA, token: {} as never, rawToken: "fake-token" },
    };
    try {
      await redeemPairingCode.run(request);
      expect.fail("Should have thrown");
    } catch (error) {
      expect(error).toBeInstanceOf(HttpsError);
      expect((error as HttpsError).code).toBe("invalid-argument");
    }
  });

  it("with valid auth and a real code creates a connection for the caller's uid", async () => {
    const now = Timestamp.now();
    const { code } = await createPairingCodeLogic(uidA, { type: "friend" }, firestore, now);
    const request: CallableRequest<{ code: string }> = {
      data: { code },
      auth: { uid: uidB, token: {} as never, rawToken: "fake-token" },
    };
    const { connectionId } = await redeemPairingCode.run(request);
    const conn = await firestore.doc(`connections/${connectionId}`).get();
    expect(conn.get("memberUids")).toEqual(expect.arrayContaining([uidA, uidB]));
  });
});
