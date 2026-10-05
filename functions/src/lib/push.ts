import { Firestore } from "firebase-admin/firestore";
import type { Messaging } from "firebase-admin/messaging";
import { listTokens, removeToken } from "./tokens";

export async function sendPushToPartner(
  messaging: Messaging,
  firestore: Firestore,
  partnerUid: string,
  connectionId: string,
  payload: { title: string; body: string },
  // FCM data payloads must be string-valued. Android needs this to arrive as
  // a data message so onMessageReceived fires even when the app is
  // backgrounded, and it tells the client which connection changed.
  data: Record<string, string>
): Promise<void> {
  const tokens = await listTokens(firestore, partnerUid);

  for (const token of tokens) {
    try {
      await messaging.send({
        token,
        notification: { title: payload.title, body: payload.body },
        data,
        android: { collapseKey: connectionId },
        apns: { headers: { "apns-collapse-id": connectionId } },
      });
    } catch (error) {
      if ((error as { code?: string }).code === "messaging/registration-token-not-registered") {
        await removeToken(firestore, partnerUid, token);
      }
      // Any other send failure: skip this token, keep trying the rest.
    }
  }
}
