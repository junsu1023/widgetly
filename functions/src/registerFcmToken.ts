import { onCall } from "firebase-functions/v2/https";
import { db } from "./index";
import { upsertToken } from "./lib/tokens";

export const registerFcmToken = onCall<{ token: string; platform: "android" | "ios" }>(
  async (request) => {
    if (!request.auth) {
      throw new Error("Unauthenticated");
    }

    const uid = request.auth.uid;
    const { token, platform } = request.data;

    if (!token || !platform) {
      throw new Error("Missing token or platform");
    }

    await upsertToken(db, uid, token, platform);
  }
);
