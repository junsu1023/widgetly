import { beforeEach, describe, it, expect, afterAll } from "vitest";
import { initializeApp, App, getApp, deleteApp } from "firebase-admin/app";
import { getFirestore, Firestore } from "firebase-admin/firestore";
import { upsertToken, removeToken, listTokens } from "../src/lib/tokens";
import { registerFcmToken, db as indexDb } from "../src/index";
import type { CallableRequest } from "firebase-functions/v2/https";
import { HttpsError } from "firebase-functions/v2/https";

let app: App;
let firestore: Firestore;
let db: Firestore;

const uidA = "user-a";
const uidB = "user-b";

// Initialize app once for all tests
beforeEach(async () => {
  // Use the db instance from index.ts (which is already initialized in the module)
  db = indexDb;
  firestore = indexDb;

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

describe("tokens", () => {

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

describe("registerFcmToken (callable)", () => {
  it("with valid auth context and data writes the token doc under the authenticated caller's uid", async () => {
    const request: CallableRequest<{ token: string; platform: "android" | "ios" }> = {
      data: { token: "tok-1", platform: "android" },
      auth: {
        uid: uidA,
        token: {} as any,
        rawToken: "fake-token",
      },
    };

    await registerFcmToken.run(request);

    const doc = await firestore.doc(`users/${uidA}/tokens/tok-1`).get();
    expect(doc.exists).toBe(true);
    expect(doc.get("platform")).toBe("android");
  });

  it("without auth context throws HttpsError with unauthenticated code", async () => {
    const request: CallableRequest<{ token: string; platform: "android" | "ios" }> = {
      data: { token: "tok-1", platform: "android" },
    };

    try {
      await registerFcmToken.run(request);
      expect.fail("Should have thrown");
    } catch (error) {
      expect(error).toBeInstanceOf(HttpsError);
      expect((error as HttpsError).code).toBe("unauthenticated");
    }
  });

  it("with invalid platform throws HttpsError with invalid-argument code", async () => {
    const request: CallableRequest<{ token: string; platform: "android" | "ios" }> = {
      data: { token: "tok-1", platform: "foo" as any },
      auth: {
        uid: uidA,
        token: {} as any,
        rawToken: "fake-token",
      },
    };

    try {
      await registerFcmToken.run(request);
      expect.fail("Should have thrown");
    } catch (error) {
      expect(error).toBeInstanceOf(HttpsError);
      expect((error as HttpsError).code).toBe("invalid-argument");
    }
  });

  it("with missing token throws HttpsError with invalid-argument code", async () => {
    const request: CallableRequest<{ token: string; platform: "android" | "ios" }> = {
      data: { token: "", platform: "android" },
      auth: {
        uid: uidA,
        token: {} as any,
        rawToken: "fake-token",
      },
    };

    try {
      await registerFcmToken.run(request);
      expect.fail("Should have thrown");
    } catch (error) {
      expect(error).toBeInstanceOf(HttpsError);
      expect((error as HttpsError).code).toBe("invalid-argument");
    }
  });

  it("with a token containing '/' throws HttpsError with invalid-argument code (not a raw Firestore path error)", async () => {
    const request: CallableRequest<{ token: string; platform: "android" | "ios" }> = {
      data: { token: "abc/def", platform: "android" },
      auth: {
        uid: uidA,
        token: {} as any,
        rawToken: "fake-token",
      },
    };

    try {
      await registerFcmToken.run(request);
      expect.fail("Should have thrown");
    } catch (error) {
      expect(error).toBeInstanceOf(HttpsError);
      expect((error as HttpsError).code).toBe("invalid-argument");
    }
  });

  it("with an oversized token throws HttpsError with invalid-argument code", async () => {
    const request: CallableRequest<{ token: string; platform: "android" | "ios" }> = {
      data: { token: "a".repeat(600), platform: "android" },
      auth: {
        uid: uidA,
        token: {} as any,
        rawToken: "fake-token",
      },
    };

    try {
      await registerFcmToken.run(request);
      expect.fail("Should have thrown");
    } catch (error) {
      expect(error).toBeInstanceOf(HttpsError);
      expect((error as HttpsError).code).toBe("invalid-argument");
    }
  });

  it("does not accept client-supplied uid, uses auth.uid instead", async () => {
    const request: CallableRequest<{ token: string; platform: "android" | "ios" }> = {
      data: { token: "tok-1", platform: "android" },
      auth: {
        uid: uidA,
        token: {} as any,
        rawToken: "fake-token",
      },
    };

    await registerFcmToken.run(request);

    // Token should exist under uidA, not under any client-supplied uid
    const doc = await firestore.doc(`users/${uidA}/tokens/tok-1`).get();
    expect(doc.exists).toBe(true);

    // Verify uidB doesn't have the token
    const uidBTokens = await listTokens(firestore, uidB);
    expect(uidBTokens).toEqual([]);
  });
});
