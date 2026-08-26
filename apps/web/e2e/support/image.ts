import { createHash } from "node:crypto";
import { readFileSync, readdirSync, statSync } from "node:fs";
import path from "node:path";

/**
 * Decides whether the lumo-api image has to be rebuilt.
 *
 * Docker already caches layers, and the Dockerfile mounts a BuildKit cache on
 * `/root/.gradle`, so an unchanged tree recompiles nothing. What it does not
 * avoid is the invocation itself: packing the build context, walking the
 * layers, answering. Half a minute, on every run, for nothing — and half a
 * minute is exactly the amount of friction that stops someone running the suite
 * while they work on the front end.
 *
 * So the inputs that can change the image are fingerprinted, and `--build` is
 * passed only when that fingerprint moves or the image is missing. The
 * fingerprint is the file list plus contents, not timestamps: a `git checkout`
 * that restores a file to what it already was must not trigger a rebuild.
 *
 * Deliberately conservative. If a path listed here is wrong or missing, the
 * fingerprint changes and we rebuild — the failure mode is a wasted build, not
 * a suite testing a server that no longer exists in the tree.
 */

/** Everything the image is built from. Relative to the repository root. */
const IMAGE_INPUTS = [
  "apps/api/Dockerfile",
  "apps/api/build.gradle.kts",
  "apps/api/settings.gradle.kts",
  "apps/api/gradle.properties",
  "apps/api/gradle",
  "apps/api/src",
  // The server interfaces are generated from it at image build time (ADR 0001),
  // so a contract change is an API change even when no Java file moved.
  "packages/contracts/openapi.yaml",
];

export function fingerprintApiImage(repoRoot: string): string {
  const hash = createHash("sha256");

  for (const input of IMAGE_INPUTS) {
    hashPath(hash, repoRoot, input);
  }

  return hash.digest("hex");
}

function hashPath(hash: ReturnType<typeof createHash>, repoRoot: string, relative: string) {
  const absolute = path.join(repoRoot, relative);

  let stats;
  try {
    stats = statSync(absolute);
  } catch {
    // Absent is a state like any other, and one that must change the
    // fingerprint: deleting a file changes the image.
    hash.update(`missing:${relative}\n`);
    return;
  }

  if (stats.isDirectory()) {
    // Sorted, so the fingerprint does not depend on the order the filesystem
    // happens to return entries in.
    for (const entry of readdirSync(absolute).sort()) {
      // Gradle's own output. It is not an input, and it changes on every build.
      if (entry === "build" || entry === ".gradle") continue;
      hashPath(hash, repoRoot, path.join(relative, entry));
    }
    return;
  }

  // The path is part of the hash: renaming a file changes the image even when
  // its bytes do not.
  hash.update(`${relative.replace(/\\/g, "/")}\n`);
  hash.update(readFileSync(absolute));
}
