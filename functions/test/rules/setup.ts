import * as fs from "node:fs";
import * as path from "node:path";
import {
  initializeTestEnvironment,
  type RulesTestEnvironment,
} from "@firebase/rules-unit-testing";

// Repo root firestore.rules — same file real users/widgetly database is
// guarded by. See CLAUDE.md / task-2-brief.md: @firebase/rules-unit-testing
// can only target the (default) database namespace in the emulator, but
// rules aren't scoped per-database-id, so this still exercises the real rules.
const RULES_PATH = path.resolve(__dirname, "../../../firestore.rules");

let testEnv: RulesTestEnvironment | undefined;

export async function getTestEnv(): Promise<RulesTestEnvironment> {
  if (!testEnv) {
    testEnv = await initializeTestEnvironment({
      projectId: "widgetly-rules-test",
      firestore: {
        rules: fs.readFileSync(RULES_PATH, "utf8"),
      },
    });
  }
  return testEnv;
}

export async function teardownTestEnv(): Promise<void> {
  await testEnv?.cleanup();
  testEnv = undefined;
}

/** Firestore client acting as the given authenticated uid, subject to security rules. */
export async function authedDb(uid: string) {
  const env = await getTestEnv();
  return env.authenticatedContext(uid).firestore();
}

/** Seeds a connection doc directly (bypassing rules) for tests to read/write against. */
export async function seedConnection(
  connectionId: string,
  memberUids: [string, string]
): Promise<void> {
  const env = await getTestEnv();
  await env.withSecurityRulesDisabled(async (ctx) => {
    await ctx
      .firestore()
      .doc(`connections/${connectionId}`)
      .set({
        memberUids,
        type: "couple",
        status: "active",
        createdAt: new Date(),
        anniversaryDate: "2026-01-01",
        anniversaryUpdatedBy: memberUids[0],
        streak: 0,
        streakUpdatedDate: "2026-01-01",
        lastSignalByUid: {},
      });
  });
}
