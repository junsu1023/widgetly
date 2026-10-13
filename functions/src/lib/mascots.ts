export const MASCOT_IDS = ["quokka", "cat", "bear", "rabbit", "hamster"] as const;

export type MascotId = typeof MASCOT_IDS[number];
