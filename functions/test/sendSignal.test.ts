import { beforeEach, describe, it, expect } from "vitest";
import { Firestore, Timestamp } from "firebase-admin/firestore";
import { sendSignalLogic, sendSignal, toSeoulDateString } from "../src/sendSignal";
import { db as indexDb } from "../src/index";
import type { CallableRequest } from "firebase-functions/v2/https";
import { HttpsError } from "firebase-functions/v2/https";
import type { ConnectionDoc } from "../src/lib/types";

let firestore: Firestore;

const uidA = "user-a";
const uidB = "user-b";
const connectionId = "conn-1";

async function clearCollection(name: string): Promise<void> {
  const docs = await firestore.collection(name).get();
  // recursiveDelete also removes subcollections (e.g. connections/{id}/signals),
  // which a plain doc.delete() leaves behind as orphaned leftover docs.
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

describe("toSeoulDateString", () => {
  it("returns the Seoul-local date for a timestamp that's still the previous day in UTC", () => {
    // 2026-01-01 15:30 UTC == 2026-01-02 00:30 KST (UTC+9)
    const ts = Timestamp.fromDate(new Date("2026-01-01T15:30:00Z"));
    expect(toSeoulDateString(ts)).toBe("2026-01-02");
  });

  it("returns the Seoul-local date for a timestamp just before the Seoul day boundary", () => {
    // 2026-01-01 14:59 UTC == 2026-01-01 23:59 KST
    const ts = Timestamp.fromDate(new Date("2026-01-01T14:59:00Z"));
    expect(toSeoulDateString(ts)).toBe("2026-01-01");
  });
});

describe("sendSignalLogic", () => {
  const now = Timestamp.now();

  it("creates a signal and updates lastSignalByUid for the sender", async () => {
    await seedConnection(firestore, connectionId, [uidA, uidB]);
    const result = await sendSignalLogic(
      uidA,
      { connectionId, preset: "heart", signalId: "sig-1" },
      firestore,
      now
    );
    expect(result.rateLimited).toBe(false);
    const conn = await firestore.doc(`connections/${connectionId}`).get();
    expect(conn.get("lastSignalByUid")[uidA].presetType).toBe("heart");
  });

  it("is idempotent for a repeated signalId", async () => {
    await seedConnection(firestore, connectionId, [uidA, uidB]);
    await sendSignalLogic(
      uidA,
      { connectionId, preset: "heart", signalId: "sig-1" },
      firestore,
      now
    );
    const result = await sendSignalLogic(
      uidA,
      { connectionId, preset: "heart", signalId: "sig-1" },
      firestore,
      now
    );
    expect(result.rateLimited).toBe(false);
    const signals = await firestore.collection(`connections/${connectionId}/signals`).get();
    expect(signals.size).toBe(1);
  });

  it("rate-limits a second signal from the same sender within 5 seconds", async () => {
    await seedConnection(firestore, connectionId, [uidA, uidB]);
    await sendSignalLogic(
      uidA,
      { connectionId, preset: "heart", signalId: "sig-1" },
      firestore,
      now
    );
    const soon = Timestamp.fromMillis(now.toMillis() + 2000);
    const result = await sendSignalLogic(
      uidA,
      { connectionId, preset: "heart", signalId: "sig-2" },
      firestore,
      soon
    );
    expect(result.rateLimited).toBe(true);
  });

  it("does not rate-limit the partner replying immediately after", async () => {
    await seedConnection(firestore, connectionId, [uidA, uidB]);
    await sendSignalLogic(
      uidA,
      { connectionId, preset: "heart", signalId: "sig-1" },
      firestore,
      now
    );
    const soon = Timestamp.fromMillis(now.toMillis() + 1000);
    const result = await sendSignalLogic(
      uidB,
      { connectionId, preset: "heart", signalId: "sig-2" },
      firestore,
      soon
    );
    expect(result.rateLimited).toBe(false);
  });

  it("increments streak once when both partners have signaled the same day, and not again on a third signal that day", async () => {
    await seedConnection(firestore, connectionId, [uidA, uidB]);
    await sendSignalLogic(
      uidA,
      { connectionId, preset: "heart", signalId: "sig-1" },
      firestore,
      now
    );
    const laterSameDay = Timestamp.fromMillis(now.toMillis() + 6000);
    await sendSignalLogic(
      uidB,
      { connectionId, preset: "heart", signalId: "sig-2" },
      firestore,
      laterSameDay
    );
    let conn = await firestore.doc(`connections/${connectionId}`).get();
    expect(conn.get("streak")).toBe(1);
    const evenLater = Timestamp.fromMillis(laterSameDay.toMillis() + 6000);
    await sendSignalLogic(
      uidA,
      { connectionId, preset: "heart", signalId: "sig-3" },
      firestore,
      evenLater
    );
    conn = await firestore.doc(`connections/${connectionId}`).get();
    expect(conn.get("streak")).toBe(1);
  });

  it("rejects when the connection does not exist", async () => {
    await expect(
      sendSignalLogic(
        uidA,
        { connectionId: "no-such-connection", preset: "heart", signalId: "sig-1" },
        firestore,
        now
      )
    ).rejects.toThrow(HttpsError);
  });

  it("rejects when the caller is not a member of the connection", async () => {
    await seedConnection(firestore, connectionId, [uidA, uidB]);
    await expect(
      sendSignalLogic(
        "user-c",
        { connectionId, preset: "heart", signalId: "sig-1" },
        firestore,
        now
      )
    ).rejects.toThrow(HttpsError);
  });

  it("rejects when the connection is not active", async () => {
    await seedConnection(firestore, connectionId, [uidA, uidB]);
    await firestore.doc(`connections/${connectionId}`).update({ status: "disconnected" });
    await expect(
      sendSignalLogic(
        uidA,
        { connectionId, preset: "heart", signalId: "sig-1" },
        firestore,
        now
      )
    ).rejects.toThrow(HttpsError);
  });

  it("rejects an oversized preset with invalid-argument, before touching the connection", async () => {
    await seedConnection(firestore, connectionId, [uidA, uidB]);
    try {
      await sendSignalLogic(
        uidA,
        { connectionId, preset: "x".repeat(33), signalId: "sig-oversized" },
        firestore,
        now
      );
      expect.fail("Should have thrown");
    } catch (error) {
      expect(error).toBeInstanceOf(HttpsError);
      expect((error as HttpsError).code).toBe("invalid-argument");
    }
    const signals = await firestore.collection(`connections/${connectionId}/signals`).get();
    expect(signals.empty).toBe(true);
  });

  it("treats a path-breaking connectionId (containing '/') as a normal not-found/failed-precondition HttpsError, not a raw Error", async () => {
    await expect(
      sendSignalLogic(
        uidA,
        { connectionId: "a/b", preset: "heart", signalId: "sig-1" },
        firestore,
        now
      )
    ).rejects.toThrow(HttpsError);
  });

  it("treats a path-breaking signalId (containing '/') as a HttpsError, not a raw Error", async () => {
    await seedConnection(firestore, connectionId, [uidA, uidB]);
    await expect(
      sendSignalLogic(
        uidA,
        { connectionId, preset: "heart", signalId: "a/b" },
        firestore,
        now
      )
    ).rejects.toThrow(HttpsError);
  });

  it("rate-limits concurrent signals from the same sender at the same instant (transaction, not a sequential-call artifact)", async () => {
    await seedConnection(firestore, connectionId, [uidA, uidB]);
    const results = await Promise.all([
      sendSignalLogic(uidA, { connectionId, preset: "heart", signalId: "sig-concurrent-1" }, firestore, now),
      sendSignalLogic(uidA, { connectionId, preset: "heart", signalId: "sig-concurrent-2" }, firestore, now),
    ]);
    const rateLimitedCount = results.filter((r) => r.rateLimited).length;
    const okCount = results.filter((r) => !r.rateLimited).length;
    expect(okCount).toBe(1);
    expect(rateLimitedCount).toBe(1);
    // Exactly one signal doc should have been created despite both racing.
    const signals = await firestore.collection(`connections/${connectionId}/signals`).get();
    expect(signals.size).toBe(1);
  });

  it("creates exactly one signal doc when the same signalId races concurrently (idempotency holds under a race)", async () => {
    await seedConnection(firestore, connectionId, [uidA, uidB]);
    const results = await Promise.all([
      sendSignalLogic(uidA, { connectionId, preset: "heart", signalId: "sig-race" }, firestore, now),
      sendSignalLogic(uidA, { connectionId, preset: "heart", signalId: "sig-race" }, firestore, now),
    ]);
    expect(results.every((r) => r.rateLimited === false)).toBe(true);
    const signals = await firestore.collection(`connections/${connectionId}/signals`).get();
    expect(signals.size).toBe(1);
  });

  it("does not rate-limit based on the partner's lastSignalByUid entry", async () => {
    // Seed the connection with the partner having JUST signaled; the caller
    // (uidA) has never signaled, so uidA must not be rate-limited.
    await seedConnection(firestore, connectionId, [uidA, uidB]);
    await firestore.doc(`connections/${connectionId}`).update({
      [`lastSignalByUid.${uidB}`]: { presetType: "heart", sentAt: now },
    });
    const result = await sendSignalLogic(
      uidA,
      { connectionId, preset: "heart", signalId: "sig-1" },
      firestore,
      now
    );
    expect(result.rateLimited).toBe(false);
  });
});

describe("sendSignal (callable)", () => {
  it("without auth context throws HttpsError with unauthenticated code", async () => {
    const request: CallableRequest<{ connectionId: string; preset: string; signalId: string }> = {
      data: { connectionId, preset: "heart", signalId: "sig-1" },
    };
    try {
      await sendSignal.run(request);
      expect.fail("Should have thrown");
    } catch (error) {
      expect(error).toBeInstanceOf(HttpsError);
      expect((error as HttpsError).code).toBe("unauthenticated");
    }
  });

  it("with missing signalId throws HttpsError with invalid-argument code", async () => {
    const request: CallableRequest<{ connectionId: string; preset: string; signalId: string }> = {
      data: { connectionId, preset: "heart", signalId: "" },
      auth: { uid: uidA, token: {} as never, rawToken: "fake-token" },
    };
    try {
      await sendSignal.run(request);
      expect.fail("Should have thrown");
    } catch (error) {
      expect(error).toBeInstanceOf(HttpsError);
      expect((error as HttpsError).code).toBe("invalid-argument");
    }
  });

  it("with valid auth and an active connection creates a signal", async () => {
    await seedConnection(firestore, connectionId, [uidA, uidB]);
    const request: CallableRequest<{ connectionId: string; preset: string; signalId: string }> = {
      data: { connectionId, preset: "heart", signalId: "sig-1" },
      auth: { uid: uidA, token: {} as never, rawToken: "fake-token" },
    };
    const result = await sendSignal.run(request);
    expect(result.rateLimited).toBe(false);
    const signal = await firestore.doc(`connections/${connectionId}/signals/sig-1`).get();
    expect(signal.get("senderUid")).toBe(uidA);
  });
});
