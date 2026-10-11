import { initializeApp } from "firebase-admin/app";
import { getFirestore } from "firebase-admin/firestore";

const app = initializeApp();

// Firestore database is named "widgetly", not "(default)" — must be targeted
// explicitly everywhere the Admin SDK touches Firestore.
export const db = getFirestore(app, "widgetly");

// Task 2-5 register their callable functions here, one export line each, e.g.:
export { registerFcmToken } from "./registerFcmToken";
export { createPairingCode } from "./createPairingCode";
export { redeemPairingCode } from "./redeemPairingCode";
export { sendSignal } from "./sendSignal";
// Read-only counterpart to the four write callables: the only way the iOS
// widget's "공통 폴백" (spec 4.3) can re-read a connection without carrying the
// Firebase SDK inside the extension.
export { getConnection } from "./getConnection";
