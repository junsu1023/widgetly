import { Firestore, Timestamp } from "firebase-admin/firestore";
import type { TokenDoc } from "./types";

export async function upsertToken(
  firestore: Firestore,
  uid: string,
  token: string,
  platform: "android" | "ios"
): Promise<void> {
  const tokenDoc: TokenDoc = {
    platform,
    updatedAt: Timestamp.now(),
  };
  await firestore.doc(`users/${uid}/tokens/${token}`).set(tokenDoc);
}

export async function removeToken(
  firestore: Firestore,
  uid: string,
  token: string
): Promise<void> {
  await firestore.doc(`users/${uid}/tokens/${token}`).delete();
}

export async function listTokens(
  firestore: Firestore,
  uid: string
): Promise<string[]> {
  const docs = await firestore.collection(`users/${uid}/tokens`).get();
  return docs.docs.map((doc) => doc.id);
}
