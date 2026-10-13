import { onCall, HttpsError } from "firebase-functions/v2/https";
import { Firestore } from "firebase-admin/firestore";
import { db } from "./index";
import type { ConnectionDoc } from "./lib/types";

type GetConnectionInput = { connectionId: string };

/**
 * What the clients' "공통 폴백" path (spec 4.3) reads. Deliberately a subset of
 * `ConnectionDoc`: only what a widget actually renders, with every Firestore
 * `Timestamp` flattened to an ISO-8601 string. iOS calls this over plain
 * `URLSession` (see DdayWidgetExtension/ConnectionWidget.swift - that target
 * carries no Firebase SDK), so it decodes the raw callable JSON envelope itself
 * and must not have to deal with the SDK's Timestamp wire encoding.
 */
export type GetConnectionResult = {
  status: string;
  streak: number;
  memberUids: string[];
  lastSignalByUid: Record<string, { presetType: string; sentAt: string }>;
  mascotByUid: Record<string, string>;
};

export async function getConnectionLogic(
  uid: string,
  input: GetConnectionInput,
  firestore: Firestore
): Promise<GetConnectionResult> {
  const { connectionId } = input;
  if (!connectionId || typeof connectionId !== "string") {
    throw new HttpsError("invalid-argument", "connectionId is required");
  }

  // connectionId is client-supplied and gets interpolated into a doc path, so a
  // "/" in it escapes the connections collection entirely: "a/signals/x" would
  // address a signal document, whose shape this function then misreads. No real
  // connection id contains a slash, so reject it outright rather than catching
  // the consequences downstream.
  if (connectionId.includes("/")) {
    throw new HttpsError("invalid-argument", "Invalid connectionId");
  }

  const snap = await firestore.doc(`connections/${connectionId}`).get();

  if (!snap.exists) {
    throw new HttpsError("not-found", "Connection does not exist");
  }

  const connection = snap.data() as ConnectionDoc;
  // The Admin SDK bypasses firestore.rules entirely, so the membership check the
  // client rule enforces has to be repeated here by hand - same policy as the
  // `connections/{connectionId}` get rule.
  if (!connection.memberUids.includes(uid)) {
    throw new HttpsError("permission-denied", "Not a member of this connection");
  }

  const lastSignalByUid: GetConnectionResult["lastSignalByUid"] = {};
  for (const [memberUid, signal] of Object.entries(connection.lastSignalByUid ?? {})) {
    lastSignalByUid[memberUid] = {
      presetType: signal.presetType,
      sentAt: signal.sentAt.toDate().toISOString(),
    };
  }

  return {
    status: connection.status,
    streak: connection.streak,
    memberUids: connection.memberUids,
    lastSignalByUid,
    mascotByUid: connection.mascotByUid ?? {},
  };
}

export const getConnection = onCall<GetConnectionInput>(async (request) => {
  if (!request.auth) {
    throw new HttpsError("unauthenticated", "Must be authenticated to read a connection");
  }

  const { connectionId } = request.data;
  if (!connectionId || typeof connectionId !== "string") {
    throw new HttpsError("invalid-argument", "connectionId is required");
  }

  return getConnectionLogic(request.auth.uid, { connectionId }, db);
});
