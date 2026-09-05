import fs from "node:fs";
import path from "node:path";
import * as asar from "@electron/asar";

async function main() {
  const dshSourceDir = "/tmp/global_dsh/lib/node_modules/@deepseek-ai/dsh";
  const outputAsar = process.argv[2] || "/tmp/dsh.asar";

  console.log(`[ASAR-BUILD] Packaging ${dshSourceDir} into ${outputAsar}...`);
  if (!fs.existsSync(dshSourceDir)) {
    throw new Error(`DSH source dir does not exist: ${dshSourceDir}`);
  }

  // Ensure output dir exists
  const outDir = path.dirname(outputAsar);
  if (!fs.existsSync(outDir)) {
    fs.mkdirSync(outDir, { recursive: true });
  }

  // Create asar archive
  await asar.createPackage(dshSourceDir, outputAsar);
  const stat = fs.statSync(outputAsar);
  console.log(`[ASAR-BUILD SUCCESS] Created ${outputAsar} (${(stat.size / (1024 * 1024)).toFixed(2)} MB)`);
}

main().catch((err) => {
  console.error("[ASAR-BUILD ERROR]", err);
  process.exit(1);
});
