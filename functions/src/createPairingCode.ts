import { onCall, HttpsError } from "firebase-functions/v2/https";
import { Firestore, Timestamp } from "firebase-admin/firestore";
import { db } from "./index";
import type { PairingCodeDoc } from "./lib/types";

const CODE_EXPIRY_MINUTES = 10;
const MAX_CODE_GENERATION_ATTEMPTS = 5;

function randomSixDigitCode(): string {
  return Math.floor(Math.random() * 1_000_000)
    .toString()
    .padStart(6, "0");
}

export async function createPairingCodeLogic(
  uid: string,
  input: { type: "couple" | "friend" },
  firestore: Firestore,
  now: Timestamp
): Promise<{ code: string }> {
  if (input.type !== "couple" && input.type !== "friend") {
    throw new HttpsError("invalid-argument", "type must be 'couple' or 'friend'");
  }

  const expiresAt = Timestamp.fromMillis(now.toMillis() + CODE_EXPIRY_MINUTES * 60 * 1000);

  for (let attempt = 0; attempt < MAX_CODE_GENERATION_ATTEMPTS; attempt++) {
    const code = randomSixDigitCode();
    const codeRef = firestore.doc(`pairingCodes/${code}`);

    // Transactional existence check + write so two concurrent calls that roll
    // the same 6-digit code can't both "win" and silently overwrite each other.
    const created = await firestore.runTransaction(async (tx) => {
      const snap = await tx.get(codeRef);
      if (snap.exists) {
        return false;
      }
      const doc: PairingCodeDoc = {
        creatorUid: uid,
        type: input.type,
        createdAt: now,
        expiresAt,
      };
      tx.set(codeRef, doc);
      return true;
    });

    if (created) {
      return { code };
    }
  }

  throw new HttpsError("internal", "Could not generate a unique pairing code, please try again");
}

export const createPairingCode = onCall<{ type: "couple" | "friend" }>(async (request) => {
  if (!request.auth) {
    throw new HttpsError("unauthenticated", "Must be authenticated to create a pairing code");
  }

  const { type } = request.data;
  if (type !== "couple" && type !== "friend") {
    throw new HttpsError("invalid-argument", "type must be 'couple' or 'friend'");
  }

  return createPairingCodeLogic(request.auth.uid, { type }, db, Timestamp.now());
});
