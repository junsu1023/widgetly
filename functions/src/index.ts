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
