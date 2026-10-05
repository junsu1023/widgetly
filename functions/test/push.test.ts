import { beforeEach, describe, it, expect, vi } from "vitest";
import { Firestore } from "firebase-admin/firestore";
import type { Messaging } from "firebase-admin/messaging";
import { sendPushToPartner } from "../src/lib/push";
import { upsertToken, listTokens } from "../src/lib/tokens";
import { db as indexDb } from "../src/index";

let firestore: Firestore;

const partnerUid = "user-partner";
const connectionId = "conn-1";

beforeEach(async () => {
  firestore = indexDb;
  // upsertToken only ever creates the subcollection doc, never a `users/{uid}`
  // parent doc, so listing/deleting via the "users" collection finds nothing
  // to clean up. Clear the known token subcollection directly instead.
  await firestore.recursiveDelete(firestore.collection(`users/${partnerUid}/tokens`));
});

describe("sendPushToPartner", () => {
  it("sets the same collapse key on android and apns payloads", async () => {
    const send = vi.fn().mockResolvedValue("ok");
    const fakeMessaging = { send } as unknown as Messaging;
    await upsertToken(firestore, partnerUid, "tok-1", "android");

    await sendPushToPartner(fakeMessaging, firestore, partnerUid, connectionId, {
      title: "t",
      body: "b",
    });

    const sentMessage = send.mock.calls[0][0];
    expect(sentMessage.android.collapseKey).toBe(connectionId);
    expect(sentMessage.apns.headers["apns-collapse-id"]).toBe(connectionId);
  });

  it("removes a token that fails with a not-registered error and still tries the remaining tokens", async () => {
    const send = vi
      .fn()
      .mockRejectedValueOnce({ code: "messaging/registration-token-not-registered" })
      .mockResolvedValueOnce("ok");
    const fakeMessaging = { send } as unknown as Messaging;
    await upsertToken(firestore, partnerUid, "bad-token", "android");
    await upsertToken(firestore, partnerUid, "good-token", "android");

    await sendPushToPartner(fakeMessaging, firestore, partnerUid, connectionId, {
      title: "t",
      body: "b",
    });

    expect(await listTokens(firestore, partnerUid)).toEqual(["good-token"]);
    expect(send).toHaveBeenCalledTimes(2);
  });

  it("keeps a token that fails with a different, non-registration error", async () => {
    const send = vi.fn().mockRejectedValueOnce({ code: "messaging/internal-error" });
    const fakeMessaging = { send } as unknown as Messaging;
    await upsertToken(firestore, partnerUid, "flaky-token", "android");

    await sendPushToPartner(fakeMessaging, firestore, partnerUid, connectionId, {
      title: "t",
      body: "b",
    });

    expect(await listTokens(firestore, partnerUid)).toEqual(["flaky-token"]);
  });

  it("does nothing when the partner has no tokens", async () => {
    const send = vi.fn();
    const fakeMessaging = { send } as unknown as Messaging;

    await sendPushToPartner(fakeMessaging, firestore, partnerUid, connectionId, {
      title: "t",
      body: "b",
    });

    expect(send).not.toHaveBeenCalled();
  });
});
