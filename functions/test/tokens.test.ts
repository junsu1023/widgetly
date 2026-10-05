import { beforeEach, describe, it, expect } from "vitest";
import { initializeApp, App } from "firebase-admin/app";
import { getFirestore, Firestore } from "firebase-admin/firestore";
import { upsertToken, removeToken, listTokens } from "../src/lib/tokens";

let app: App;
let firestore: Firestore;

const uidA = "user-a";
const uidB = "user-b";

describe("tokens", () => {
  beforeEach(async () => {
    // Initialize a fresh app/firestore for each test
    app = initializeApp({ projectId: "widgetly-test" });
    firestore = getFirestore(app, "widgetly");

    // Clear the firestore database: delete all docs in users collection
    const userDocs = await firestore.collection("users").get();
    for (const doc of userDocs.docs) {
      const tokenDocs = await firestore
        .collection(`users/${doc.id}/tokens`)
        .get();
      for (const tokenDoc of tokenDocs.docs) {
        await tokenDoc.ref.delete();
      }
      await doc.ref.delete();
    }
  });

  it("upsertToken creates a token doc keyed by the token string", async () => {
    await upsertToken(firestore, uidA, "tok-1", "android");
    const doc = await firestore.doc(`users/${uidA}/tokens/tok-1`).get();
    expect(doc.get("platform")).toBe("android");
  });

  it("upsertToken called twice with the same token does not create a duplicate", async () => {
    await upsertToken(firestore, uidA, "tok-1", "android");
    await upsertToken(firestore, uidA, "tok-1", "android");
    const docs = await firestore.collection(`users/${uidA}/tokens`).get();
    expect(docs.size).toBe(1);
  });

  it("removeToken deletes only the given token", async () => {
    await upsertToken(firestore, uidA, "tok-1", "android");
    await upsertToken(firestore, uidA, "tok-2", "android");
    await removeToken(firestore, uidA, "tok-1");
    expect(await listTokens(firestore, uidA)).toEqual(["tok-2"]);
  });
});
