import type { Timestamp } from "firebase-admin/firestore";

/**
 * Formats a Firestore Timestamp as a Seoul-local "YYYY-MM-DD" date string.
 * en-CA formats as YYYY-MM-DD; Asia/Seoul is a fixed UTC+9 offset (no DST),
 * but Intl handles the conversion correctly without hand-rolled ms math.
 */
export function toSeoulDateString(ts: Timestamp): string {
  return new Intl.DateTimeFormat("en-CA", {
    timeZone: "Asia/Seoul",
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  }).format(ts.toDate());
}
