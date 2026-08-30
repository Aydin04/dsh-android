import fs from 'node:fs';
import path from 'node:path';

// Patch dsh-session-persistence-jsonl to use copyFile instead of link() on Android SELinux
const sessionPersistencePath = '/tmp/global_dsh/lib/node_modules/@deepseek-ai/dsh/node_modules/@deepseek-ai/dsh-session-persistence-jsonl/lib/index.js';

if (fs.existsSync(sessionPersistencePath)) {
  let code = fs.readFileSync(sessionPersistencePath, 'utf8');
  if (code.includes('await link(tmp, finalPath);')) {
    console.log('[PATCH] Patching await link(tmp, finalPath) with safe atomic copy fallback...');
    code = code.replace(
      'await link(tmp, finalPath);',
      'try { await link(tmp, finalPath); } catch (e) { const { copyFile } = await import("node:fs/promises"); await copyFile(tmp, finalPath); }'
    );
    fs.writeFileSync(sessionPersistencePath, code, 'utf8');
    console.log('[PATCH SUCCESS] session-persistence-jsonl link() patched!');
  } else {
    console.log('[WARN] await link(tmp, finalPath) not found in', sessionPersistencePath);
  }
} else {
  console.log('[WARN] Target file does not exist:', sessionPersistencePath);
}
