import { defineConfig } from "vitest/config";

export default defineConfig({
  test: {
    environment: "node",
    // Emulator round-trips are slower than unit tests.
    testTimeout: 20000,
    hookTimeout: 20000,
  },
});
