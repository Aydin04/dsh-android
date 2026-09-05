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

  // Merge any hoisted sibling modules from global prefix into dsh/node_modules
  const globalNm = "/tmp/global_dsh/lib/node_modules";
  const targetNm = path.join(dshSourceDir, "node_modules");
  if (fs.existsSync(globalNm)) {
    if (!fs.existsSync(targetNm)) fs.mkdirSync(targetNm, { recursive: true });
    for (const item of fs.readdirSync(globalNm)) {
      if (item === "@deepseek-ai") {
        const deepseekDir = path.join(globalNm, item);
        for (const sub of fs.readdirSync(deepseekDir)) {
          if (sub !== "dsh") {
            const src = path.join(deepseekDir, sub);
            const dst = path.join(targetNm, "@deepseek-ai", sub);
            if (fs.existsSync(src)) {
              fs.mkdirSync(path.dirname(dst), { recursive: true });
              try { fs.cpSync(src, dst, { recursive: true, force: true }); } catch (_) {}
            }
          }
        }
      } else {
        const src = path.join(globalNm, item);
        const dst = path.join(targetNm, item);
        if (fs.existsSync(src)) {
          fs.mkdirSync(path.dirname(dst), { recursive: true });
          try { fs.cpSync(src, dst, { recursive: true, force: true }); } catch (_) {}
        }
      }
    }
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
