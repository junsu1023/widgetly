import { defineConfig } from "vitest/config";

export default defineConfig({
  test: {
    environment: "node",
    // Emulator round-trips are slower than unit tests.
    testTimeout: 20000,
    hookTimeout: 20000,
    // All test files share one live Firestore emulator instance (no per-file
    // namespacing). Running files in parallel lets one file's beforeEach
    // (which clears a whole collection, e.g. "connections") wipe out data
    // another file is mid-test with. Serialize file execution to match the
    // shared, stateful backend.
    fileParallelism: false,
  },
});
