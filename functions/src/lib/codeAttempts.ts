import { Firestore, Timestamp } from "firebase-admin/firestore";
import type { CodeAttemptsDoc } from "./types";

// Brute-force lockout: MAX_CODE_ATTEMPTS failures within LOCKOUT_WINDOW_MINUTES locks further attempts.
export const LOCKOUT_WINDOW_MINUTES = 15;
export const MAX_CODE_ATTEMPTS = 5;

/**
 * Records a failed pairing-code attempt for `uid` and reports whether they are
 * now locked out. If the existing window has expired, the counter resets to 1
 * with a fresh window; otherwise it increments within the current window.
 * Must only be called on a failed redemption attempt — successful redemptions
 * don't touch this counter.
 */
export async function checkAndRecordFailure(
  firestore: Firestore,
  uid: string,
  now: Timestamp
): Promise<{ locked: boolean }> {
  const ref = firestore.doc(`codeAttempts/${uid}`);
  const windowCutoffMs = now.toMillis() - LOCKOUT_WINDOW_MINUTES * 60 * 1000;

  return firestore.runTransaction(async (tx) => {
    const snap = await tx.get(ref);
    const existing = snap.exists ? (snap.data() as CodeAttemptsDoc) : undefined;

    let failCount: number;
    let windowStart: Timestamp;
    if (!existing || existing.windowStart.toMillis() < windowCutoffMs) {
      failCount = 1;
      windowStart = now;
    } else {
      failCount = existing.failCount + 1;
      windowStart = existing.windowStart;
    }

    const doc: CodeAttemptsDoc = { failCount, windowStart };
    tx.set(ref, doc);

    return { locked: failCount >= MAX_CODE_ATTEMPTS };
  });
}

/** Clears a uid's failed-attempt history, e.g. after a successful redemption. */
export async function resetAttempts(firestore: Firestore, uid: string): Promise<void> {
  await firestore.doc(`codeAttempts/${uid}`).delete();
}
