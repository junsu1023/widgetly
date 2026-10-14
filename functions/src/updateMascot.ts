import { onCall, HttpsError } from "firebase-functions/v2/https";
import { Firestore } from "firebase-admin/firestore";
import { db } from "./index";
import type { ConnectionDoc } from "./lib/types";
import { MASCOT_IDS, MascotId } from "./lib/mascots";

type UpdateMascotInput = { connectionId: string; mascotId: string };

export async function updateMascotLogic(
  uid: string,
  input: UpdateMascotInput,
  firestore: Firestore
): Promise<void> {
  const { connectionId, mascotId } = input;
  if (!connectionId || typeof connectionId !== "string") {
    throw new HttpsError("invalid-argument", "connectionId is required");
  }
  if (!MASCOT_IDS.includes(mascotId as MascotId)) {
    throw new HttpsError("invalid-argument", "mascotId is not a recognized mascot");
  }
  // Same explicit guard as getConnection.ts: connectionId is interpolated into a
  // doc path, so a "/" in it escapes the connections collection entirely
  // ("a/signals/x" addresses a signal document, whose shape this function then
  // misreads). No real connection id contains a slash.
  if (connectionId.includes("/")) {
    throw new HttpsError("invalid-argument", "Invalid connectionId");
  }

  await firestore.runTransaction(async (tx) => {
    // The guard above rejects the one malformed shape we know of, but the SDK
    // can still throw a plain Error (not our HttpsError) on an id it considers
    // invalid for other reasons; fold that into the same client-facing error
    // instead of letting it leak out raw.
    let connectionSnap;
    try {
      connectionSnap = await tx.get(firestore.doc(`connections/${connectionId}`));
    } catch {
      throw new HttpsError("failed-precondition", "Invalid connection");
    }

    if (!connectionSnap.exists) {
      throw new HttpsError("failed-precondition", "Connection does not exist");
    }

    // The Admin SDK bypasses firestore.rules entirely, so the membership
    // check the client rule enforces has to be repeated here by hand.
    const connection = connectionSnap.data() as ConnectionDoc;
    if (connection.status !== "active" || !connection.memberUids.includes(uid)) {
      throw new HttpsError("failed-precondition", "Connection is not active for this user");
    }

    // Re-picking the character that's already stored is a no-op: skip the write
    // rather than spend a Firestore write (and a listener wakeup on the
    // partner's device) on nothing. The transaction still ran - a transaction
    // with no writes is valid.
    if (connection.mascotByUid?.[uid] === mascotId) {
      return;
    }

    // Dot-path key in update() merges into the existing mascotByUid map
    // without touching other members' entries.
    tx.update(connectionSnap.ref, { [`mascotByUid.${uid}`]: mascotId });
  });
}

export const updateMascot = onCall<UpdateMascotInput>(async (request) => {
  if (!request.auth) {
    throw new HttpsError("unauthenticated", "Must be authenticated to update a mascot");
  }

  const { connectionId, mascotId } = request.data;
  if (!connectionId || typeof connectionId !== "string") {
    throw new HttpsError("invalid-argument", "connectionId is required");
  }
  if (!mascotId || typeof mascotId !== "string") {
    throw new HttpsError("invalid-argument", "mascotId is required");
  }

  await updateMascotLogic(request.auth.uid, { connectionId, mascotId }, db);
});
