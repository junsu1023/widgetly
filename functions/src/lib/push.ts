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
        // NO top-level `notification`: a combined notification+data message is
        // handed to the system tray by the OS while the app is backgrounded, and
        // FirebaseMessagingService.onMessageReceived is then not guaranteed to run
        // at all - which silently killed the whole Android widget-refresh path
        // (WidgetlyFirebaseMessagingService). Only a true data-only message always
        // reaches onMessageReceived. Spec 4.3's Android path is exactly that: data
        // message -> cache refresh -> Glance redraw, no visible notification.
        data,
        android: { collapseKey: connectionId },
        apns: {
          headers: { "apns-collapse-id": connectionId },
          payload: {
            aps: {
              // iOS needs the opposite of Android: a UNNotificationServiceExtension
              // only runs for a *displayable* notification carrying
              // `mutable-content: 1`. A data-only push is a silent push there and
              // NotificationService.didReceive would never fire. The apns block
              // overrides for iOS only, so Android stays data-only above.
              alert: { title: payload.title, body: payload.body },
              "mutable-content": 1,
            },
          },
        },
      });
    } catch (error) {
      if ((error as { code?: string }).code === "messaging/registration-token-not-registered") {
        await removeToken(firestore, partnerUid, token);
      }
      // Any other send failure: skip this token, keep trying the rest.
    }
  }
}
