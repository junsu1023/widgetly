import { onCall, HttpsError } from "firebase-functions/v2/https";
import { Firestore, Timestamp } from "firebase-admin/firestore";
import { getMessaging } from "firebase-admin/messaging";
import type { Messaging } from "firebase-admin/messaging";
import { db } from "./index";
import type { ConnectionDoc, PairingCodeDoc } from "./lib/types";
import { checkAndRecordFailure, resetAttempts, isLockedOut } from "./lib/codeAttempts";
import { toSeoulDateString } from "./lib/dates";
import { sendPushToPartner } from "./lib/push";

export async function redeemPairingCodeLogic(
  uid: string,
  input: { code: string },
  firestore: Firestore,
  now: Timestamp,
  messaging: Messaging = getMessaging()
): Promise<{ connectionId: string }> {
  const code = input.code;
  if (!code || typeof code !== "string") {
    throw new HttpsError("invalid-argument", "code is required");
  }

  // Lockout must be checked BEFORE attempting a redemption, not only recorded
  // after a failure — otherwise an already-locked-out uid can still redeem a
  // VALID code, which defeats the brute-force protection entirely.
  if (await isLockedOut(firestore, uid, now)) {
    throw new HttpsError(
      "resource-exhausted",
      "Too many failed attempts, locked out. Try again later."
    );
  }

  let creatorUidForPush: string | null = null;

  try {
    const connectionId = await firestore.runTransaction(async (tx) => {
      // A malformed code (e.g. one containing "/") makes Firestore's doc-path
      // parsing throw a plain SDK Error, not our HttpsError — fold any such
      // failure into the same "code doesn't exist" case so it still surfaces
      // as HttpsError('not-found', ...) and still counts as a failed guess
      // below, instead of leaking a raw Error / skipping the lockout counter.
      let codeSnap;
      try {
        codeSnap = await tx.get(firestore.doc(`pairingCodes/${code}`));
      } catch {
        throw new HttpsError("not-found", "Invalid or expired pairing code");
      }

      if (!codeSnap.exists) {
        throw new HttpsError("not-found", "Invalid or expired pairing code");
      }

      const codeData = codeSnap.data() as PairingCodeDoc;

      if (codeData.expiresAt.toMillis() <= now.toMillis()) {
        throw new HttpsError("not-found", "Invalid or expired pairing code");
      }

      if (codeData.creatorUid === uid) {
        throw new HttpsError("invalid-argument", "Cannot redeem your own pairing code");
      }

      const hasActiveCoupleConnection = async (checkUid: string): Promise<boolean> => {
        const snap = await tx.get(
          firestore
            .collection("connections")
            .where("memberUids", "array-contains", checkUid)
            .where("type", "==", "couple")
            .where("status", "==", "active")
        );
        return !snap.empty;
      };

      if (codeData.type === "couple") {
        // Check both sides of the pairing: a redeemer OR a creator who's
        // already in an active couple connection must not end up in a
        // second one — the cap exists to prevent exactly that.
        if (await hasActiveCoupleConnection(uid)) {
          throw new HttpsError(
            "already-exists",
            "already-exists: caller already has an active couple connection"
          );
        }
        if (await hasActiveCoupleConnection(codeData.creatorUid)) {
          throw new HttpsError(
            "already-exists",
            "already-exists: creator already has an active couple connection"
          );
        }
      }

      const connectionRef = firestore.collection("connections").doc();
      const connectionDoc: ConnectionDoc = {
        memberUids: [codeData.creatorUid, uid],
        type: codeData.type,
        status: "active",
        createdAt: now,
        anniversaryDate: toSeoulDateString(now),
        anniversaryUpdatedBy: uid,
        streak: 0,
        streakUpdatedDate: "",
        lastSignalByUid: {},
      };
      tx.set(connectionRef, connectionDoc);
      tx.delete(codeSnap.ref);

      creatorUidForPush = codeData.creatorUid;
      return connectionRef.id;
    });

    // A successful redemption proves the caller wasn't guessing; clear their
    // failure history so it doesn't carry over to future legitimate attempts.
    await resetAttempts(firestore, uid);

    // Notify the code creator too — they have no other way to learn that
    // pairing happened or what connectionId resulted from it.
    if (creatorUidForPush) {
      await sendPushToPartner(
        messaging,
        firestore,
        creatorUidForPush,
        connectionId,
        {
          title: "연결 완료",
          body: "상대방과 연결되었어요",
        },
        { connectionId }
      );
    }

    return { connectionId };
  } catch (error) {
    // Only count failures caused by a nonexistent/expired code toward the
    // brute-force lockout. Other rejections (own code, already-exists) are
    // legitimate business outcomes against a code the caller actually had,
    // not a guessing attempt, so they don't consume lockout budget.
    if (error instanceof HttpsError && error.code === "not-found") {
      const { locked } = await checkAndRecordFailure(firestore, uid, now);
      if (locked) {
        throw new HttpsError(
          "resource-exhausted",
          "Too many failed attempts, locked out. Try again later."
        );
      }
    }
    throw error;
  }
}

export const redeemPairingCode = onCall<{ code: string }>(async (request) => {
  if (!request.auth) {
    throw new HttpsError("unauthenticated", "Must be authenticated to redeem a pairing code");
  }

  const { code } = request.data;
  if (!code || typeof code !== "string") {
    throw new HttpsError("invalid-argument", "code is required");
  }

  return redeemPairingCodeLogic(request.auth.uid, { code }, db, Timestamp.now());
});
