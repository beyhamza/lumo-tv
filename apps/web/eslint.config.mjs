import { defineConfig, globalIgnores } from "eslint/config";
import nextVitals from "eslint-config-next/core-web-vitals";
import nextTs from "eslint-config-next/typescript";

const eslintConfig = defineConfig([
  ...nextVitals,
  ...nextTs,
  // Override default ignores of eslint-config-next.
  globalIgnores([
    // Default ignores of eslint-config-next:
    ".next/**",
    "out/**",
    "build/**",
    "next-env.d.ts",
    // The source test bench fixtures. Its HLS segments are MPEG-TS and carry
    // the .ts extension, which ESLint would otherwise try to parse — one
    // "Unexpected keyword or identifier" per segment. Same exclusion as in
    // tsconfig.json, for the same reason.
    "e2e/bench/www/**",
  ]),
]);

export default eslintConfig;
