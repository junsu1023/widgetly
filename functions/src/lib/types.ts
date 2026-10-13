import type { Timestamp } from "firebase-admin/firestore";

export interface UserDoc {
  nickname: string;
}

export interface TokenDoc {
  platform: "android" | "ios";
  updatedAt: Timestamp;
}

export interface ConnectionDoc {
  memberUids: [string, string];
  type: "couple" | "friend";
  status: "active" | "disconnected";
  createdAt: Timestamp;
  anniversaryDate: string; // "YYYY-MM-DD"
  anniversaryUpdatedBy: string;
  streak: number;
  streakUpdatedDate: string; // "YYYY-MM-DD", Asia/Seoul 기준
  lastSignalByUid: Record<string, { presetType: string; sentAt: Timestamp }>;
  mascotByUid?: Record<string, string>;
}

export interface SignalDoc {
  senderUid: string;
  presetType: string;
  sentAt: Timestamp;
  expireAt: Timestamp;
}

export interface PairingCodeDoc {
  creatorUid: string;
  type: "couple" | "friend";
  createdAt: Timestamp;
  expiresAt: Timestamp;
}

export interface CodeAttemptsDoc {
  failCount: number;
  windowStart: Timestamp;
}
