import { onCall, HttpsError } from "firebase-functions/v2/https";
import { db } from "./index";
import { upsertToken } from "./lib/tokens";

export const registerFcmToken = onCall<{ token: string; platform: "android" | "ios" }>(
  async (request) => {
    if (!request.auth) {
      throw new HttpsError("unauthenticated", "Must be authenticated to register FCM token");
    }

    const uid = request.auth.uid;
    const { token, platform } = request.data;

    if (!token) {
      throw new HttpsError("invalid-argument", "Token is required");
    }

    if (platform !== "android" && platform !== "ios") {
      throw new HttpsError("invalid-argument", "Platform must be 'android' or 'ios'");
    }

    await upsertToken(db, uid, token, platform);
  }
);
