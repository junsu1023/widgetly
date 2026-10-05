import { onCall, HttpsError } from "firebase-functions/v2/https";
import { Firestore, Timestamp } from "firebase-admin/firestore";
import { getMessaging } from "firebase-admin/messaging";
import type { Messaging } from "firebase-admin/messaging";
import { db } from "./index";
import type { ConnectionDoc, SignalDoc } from "./lib/types";
import { sendPushToPartner } from "./lib/push";
import { toSeoulDateString } from "./lib/dates";

export { toSeoulDateString };

const RATE_LIMIT_SECONDS = 5;
const SIGNAL_EXPIRY_DAYS = 30;
const MAX_PRESET_LENGTH = 32;

type SendSignalInput = { connectionId: string; preset: string; signalId: string };

export async function sendSignalLogic(
  uid: string,
  input: SendSignalInput,
  firestore: Firestore,
  now: Timestamp,
  messaging: Messaging = getMessaging()
): Promise<{ rateLimited: boolean }> {
  const { connectionId, preset, signalId } = input;
  if (!connectionId || typeof connectionId !== "string") {
    throw new HttpsError("invalid-argument", "connectionId is required");
  }
  if (!preset || typeof preset !== "string") {
    throw new HttpsError("invalid-argument", "preset is required");
  }
  if (preset.length > MAX_PRESET_LENGTH) {
    throw new HttpsError("invalid-argument", `preset must be at most ${MAX_PRESET_LENGTH} characters`);
  }
  if (!signalId || typeof signalId !== "string") {
    throw new HttpsError("invalid-argument", "signalId is required");
  }

  let partnerUidForPush: string | null = null;

  const { rateLimited, shouldPush } = await firestore.runTransaction(async (tx) => {
    // connectionId/signalId are client-supplied and get interpolated into a
    // Firestore doc path below. A malformed id (e.g. containing "/") makes
    // the SDK throw a plain Error, not our HttpsError; fold that into the
    // same client-facing error instead of letting it leak out raw.
    let connectionSnap;
    try {
      connectionSnap = await tx.get(firestore.doc(`connections/${connectionId}`));
    } catch {
      throw new HttpsError("failed-precondition", "Invalid connection");
    }

    if (!connectionSnap.exists) {
      throw new HttpsError("failed-precondition", "Connection does not exist");
    }

    const connection = connectionSnap.data() as ConnectionDoc;
    if (connection.status !== "active" || !connection.memberUids.includes(uid)) {
      throw new HttpsError("failed-precondition", "Connection is not active for this user");
    }

    let signalSnap;
    try {
      signalSnap = await tx.get(
        firestore.doc(`connections/${connectionId}/signals/${signalId}`)
      );
    } catch {
      throw new HttpsError("invalid-argument", "Invalid signalId");
    }

    if (signalSnap.exists) {
      // Idempotent retry of an already-delivered signal: nothing more to do,
      // and no push (it was already pushed the first time).
      return { rateLimited: false, shouldPush: false };
    }

    const lastSignalByUid = connection.lastSignalByUid ?? {};
    const myLast = lastSignalByUid[uid];
    if (myLast && now.toMillis() - myLast.sentAt.toMillis() < RATE_LIMIT_SECONDS * 1000) {
      return { rateLimited: true, shouldPush: false };
    }

    const signalDoc: SignalDoc = {
      senderUid: uid,
      presetType: preset,
      sentAt: now,
      expireAt: Timestamp.fromMillis(now.toMillis() + SIGNAL_EXPIRY_DAYS * 24 * 60 * 60 * 1000),
    };
    tx.set(signalSnap.ref, signalDoc);

    const partnerUid = connection.memberUids.find((member) => member !== uid) as string;
    const updatedLastSignalByUid = {
      ...lastSignalByUid,
      [uid]: { presetType: preset, sentAt: now },
    };

    const today = toSeoulDateString(now);
    const myEntry = updatedLastSignalByUid[uid];
    const partnerEntry = updatedLastSignalByUid[partnerUid];
    let streak = connection.streak;
    let streakUpdatedDate = connection.streakUpdatedDate;
    const bothSignaledToday =
      toSeoulDateString(myEntry.sentAt) === today &&
      !!partnerEntry &&
      toSeoulDateString(partnerEntry.sentAt) === today;
    if (bothSignaledToday && streakUpdatedDate !== today) {
      streak += 1;
      streakUpdatedDate = today;
    }

    tx.update(connectionSnap.ref, {
      lastSignalByUid: updatedLastSignalByUid,
      streak,
      streakUpdatedDate,
    });

    partnerUidForPush = partnerUid;
    return { rateLimited: false, shouldPush: true };
  });

  if (shouldPush && partnerUidForPush) {
    await sendPushToPartner(
      messaging,
      firestore,
      partnerUidForPush,
      connectionId,
      {
        title: "새로운 신호",
        body: "상대방이 신호를 보냈어요",
      },
      { connectionId, presetType: preset, senderUid: uid }
    );
  }

  return { rateLimited };
}

export const sendSignal = onCall<SendSignalInput>(async (request) => {
  if (!request.auth) {
    throw new HttpsError("unauthenticated", "Must be authenticated to send a signal");
  }

  const { connectionId, preset, signalId } = request.data;
  if (!connectionId || typeof connectionId !== "string") {
    throw new HttpsError("invalid-argument", "connectionId is required");
  }
  if (!preset || typeof preset !== "string") {
    throw new HttpsError("invalid-argument", "preset is required");
  }
  if (!signalId || typeof signalId !== "string") {
    throw new HttpsError("invalid-argument", "signalId is required");
  }

  return sendSignalLogic(request.auth.uid, { connectionId, preset, signalId }, db, Timestamp.now());
});
