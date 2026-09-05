import fs from "node:fs";
import path from "node:path";
import asar from "@electron/asar";

async function main() {
  const atomicSourceDir = "/tmp/atomic_pack/atomic-router";
  const outputAsar = process.argv[2] || "/tmp/atomic-router.asar";

  console.log(`[ATOMIC-ASAR-BUILD] Packaging ${atomicSourceDir} into ${outputAsar}...`);
  if (!fs.existsSync(atomicSourceDir)) {
    console.warn(`[ATOMIC-ASAR-BUILD] Source dir not found: ${atomicSourceDir}`);
    return;
  }

  const outDir = path.dirname(outputAsar);
  if (!fs.existsSync(outDir)) {
    fs.mkdirSync(outDir, { recursive: true });
  }

  await asar.createPackage(atomicSourceDir, outputAsar);
  const stat = fs.statSync(outputAsar);
  console.log(`[ATOMIC-ASAR-BUILD SUCCESS] Created ${outputAsar} (${(stat.size / (1024 * 1024)).toFixed(2)} MB)`);
}

main().catch((err) => {
  console.error("[ATOMIC-ASAR-BUILD ERROR]", err);
  process.exit(1);
});
