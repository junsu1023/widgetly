import { onCall, HttpsError } from "firebase-functions/v2/https";
import { db } from "./index";
import { upsertToken } from "./lib/tokens";

// Token gets interpolated straight into a Firestore doc path
// (`users/{uid}/tokens/{token}`), so it must be constrained to characters
// that can't break that path (no "/") and to a sane length (Firestore's
// doc-path-segment limit is 1500 bytes; real FCM tokens are well under 512).
const FCM_TOKEN_PATTERN = /^[A-Za-z0-9_:.~%-]{1,512}$/;

export const registerFcmToken = onCall<{ token: string; platform: "android" | "ios" }>(
  async (request) => {
    if (!request.auth) {
      throw new HttpsError("unauthenticated", "Must be authenticated to register FCM token");
    }

    const uid = request.auth.uid;
    const { token, platform } = request.data;

    if (!token || typeof token !== "string" || !FCM_TOKEN_PATTERN.test(token)) {
      throw new HttpsError("invalid-argument", "Token is required and must be a valid FCM token");
    }

    if (platform !== "android" && platform !== "ios") {
      throw new HttpsError("invalid-argument", "Platform must be 'android' or 'ios'");
    }

    await upsertToken(db, uid, token, platform);
  }
);
