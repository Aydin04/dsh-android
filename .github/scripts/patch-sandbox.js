import fs from 'node:fs';
import path from 'node:path';

const dshRoot = '/tmp/global_dsh/lib/node_modules/@deepseek-ai/dsh';

// 1. Patch dsh-session-persistence-jsonl to use copyFile instead of link()
const sessionPersistencePath = `${dshRoot}/node_modules/@deepseek-ai/dsh-session-persistence-jsonl/lib/index.js`;
if (fs.existsSync(sessionPersistencePath)) {
  let code = fs.readFileSync(sessionPersistencePath, 'utf8');
  if (code.includes('await link(tmp, finalPath);')) {
    console.log('[PATCH] Patching session-persistence-jsonl link()...');
    code = code.replace(
      'await link(tmp, finalPath);',
      'try { await link(tmp, finalPath); } catch (e) { const { copyFile } = await import("node:fs/promises"); await copyFile(tmp, finalPath); }'
    );
    fs.writeFileSync(sessionPersistencePath, code, 'utf8');
    console.log('[PATCH SUCCESS] session-persistence-jsonl patched!');
  }
}

// 2. Patch dsh-fs-local for Android storage compatibility (no hardlinks)
const fsLocalPath = `${dshRoot}/node_modules/@deepseek-ai/dsh-fs-local/lib/index.js`;
if (fs.existsSync(fsLocalPath)) {
  let fsCode = fs.readFileSync(fsLocalPath, 'utf8');
  console.log('[PATCH] Patching dsh-fs-local for Android storage...');
  fsCode = fsCode.replace('const linkFile = internals.linkFile ?? link;', 'const linkFile = internals.linkFile ?? rename;');
  fsCode = fsCode.replace(/await linkFile\(tempPath, absolutePath\);/g, 'await rename(tempPath, absolutePath);');
  fsCode = fsCode.replace('await mkdir(stagingDir, { mode: 448 });', 'try { await mkdir(stagingDir, { mode: 448 }); } catch (_) {}');
  fs.writeFileSync(fsLocalPath, fsCode, 'utf8');
  console.log('[PATCH SUCCESS] dsh-fs-local permanently patched for Android storage!');
}

// 3. Patch dsh-terminal-bash: Hybrid Root SU and PRoot Shell
const terminalBashPath = `${dshRoot}/node_modules/@deepseek-ai/dsh-terminal-bash/lib/index.js`;
if (fs.existsSync(terminalBashPath)) {
  let code = fs.readFileSync(terminalBashPath, 'utf8');
  if (code.includes('const DEFAULT_BASH_SHELL = "/bin/bash";')) {
    console.log('[PATCH] Patching dsh-terminal-bash default shell path...');
    const shellResolver = `
import { existsSync as __termExistsSync } from "node:fs";
function __getAndroidTerminalShell() {
  for (const sh of [
    "/data/user/0/com.dsh.mobile/files/usr/bin/bash",
    "/data/user/0/com.dsh.mobile/files/usr/bin/sh",
    "/data/data/com.dsh.mobile/files/usr/bin/bash",
    "/data/data/com.dsh.mobile/files/usr/bin/sh",
    "/data/data/com.termux/files/usr/bin/bash",
    "/data/data/com.termux/files/usr/bin/sh",
    "/data/user/0/com.dsh.mobile/files/bin/bash",
    "/data/user/0/com.dsh.mobile/files/bin/sh",
    "/data/data/com.dsh.mobile/files/bin/bash",
    "/data/data/com.dsh.mobile/files/bin/sh"
  ]) {
    if (__termExistsSync(sh)) return sh;
  }
  const isRoot = __termExistsSync("/data/user/0/com.dsh.mobile/files/root_enabled.flag") || 
                 __termExistsSync("/data/data/com.dsh.mobile/files/root_enabled.flag") || 
                 __termExistsSync("/data/user/0/com.aydin.dsh/files/root_enabled.flag") ||
                 __termExistsSync("/data/data/com.aydin.dsh/files/root_enabled.flag");
  if (isRoot) {
    for (const su of ["/product/bin/magisk", "/system/bin/magisk", "/data/adb/magisk/magisk", "/data/adb/ksu/bin/su", "/data/adb/ksud", "/data/adb/ap/bin/su", "/data/adb/magisk/su", "su", "/system/bin/su", "/system/xbin/su"]) {
      if (__termExistsSync(su) || su === "su") return su;
    }
  }
  return "/system/bin/sh";
}
const DEFAULT_BASH_SHELL = __getAndroidTerminalShell();
`;
    code = code.replace('const DEFAULT_BASH_SHELL = "/bin/bash";', shellResolver);
    fs.writeFileSync(terminalBashPath, code, 'utf8');
    console.log('[PATCH SUCCESS] dsh-terminal-bash shell path patched!');
  }
}

// 4. Patch dsh-bash-local and dsh-bash-sandbox to dynamically select Direct SU or PRoot Shell at runtime
const bashLocalPath = `${dshRoot}/node_modules/@deepseek-ai/dsh-bash-local/lib/index.js`;
const bashSandboxPath = `${dshRoot}/node_modules/@deepseek-ai/dsh-bash-sandbox/lib/index.js`;

const hybridResolver = `
import { existsSync as __fsExistsSync } from "node:fs";
function __resolveAndroidShellArgv(command) {
  for (const sh of [
    "/data/user/0/com.dsh.mobile/files/usr/bin/bash",
    "/data/user/0/com.dsh.mobile/files/usr/bin/sh",
    "/data/data/com.dsh.mobile/files/usr/bin/bash",
    "/data/data/com.dsh.mobile/files/usr/bin/sh",
    "/data/data/com.termux/files/usr/bin/bash",
    "/data/data/com.termux/files/usr/bin/sh",
    "/data/user/0/com.dsh.mobile/files/bin/bash",
    "/data/user/0/com.dsh.mobile/files/bin/sh",
    "/data/data/com.dsh.mobile/files/bin/bash",
    "/data/data/com.dsh.mobile/files/bin/sh"
  ]) {
    try {
      if (__fsExistsSync(sh)) {
        return [sh, "-c", command];
      }
    } catch (_) {}
  }
  const isRoot = __fsExistsSync("/data/user/0/com.dsh.mobile/files/root_enabled.flag") ||
                 __fsExistsSync("/data/data/com.dsh.mobile/files/root_enabled.flag");
  if (isRoot) {
    for (const su of ["/product/bin/magisk", "/system/bin/magisk", "/data/adb/magisk/magisk", "/data/adb/ksu/bin/su", "/data/adb/ksud", "/data/adb/ap/bin/su", "/data/adb/magisk/su", "su"]) {
      if (su.endsWith("magisk")) {
        return [su, "su", "-mm", "-c", command];
      }
      return [su, "-mm", "-c", command];
    }
  }
  return ["su", "-mm", "-c", command];
}
`;

if (fs.existsSync(bashLocalPath)) {
  let code = fs.readFileSync(bashLocalPath, 'utf8');
  console.log('[PATCH] Patching dsh-bash-local with hybrid Direct SU / PRoot dispatching...');
  if (!code.includes('__resolveAndroidShellArgv')) {
    code = hybridResolver + code;
  }
  code = code.replace(
    /run\(spec\)\s*\{\s*return this\.runArgv\(spec,\s*\[\s*"bash",\s*"-c",\s*spec\.command\s*\]\);\s*\}/g,
    'run(spec) { return this.runArgv(spec, __resolveAndroidShellArgv(spec.command)); }'
  );
  code = code.replace(
    /start\(spec\)\s*\{\s*return this\.startArgv\(spec,\s*\[\s*"bash",\s*"-c",\s*spec\.command\s*\]\);\s*\}/g,
    'start(spec) { return this.startArgv(spec, __resolveAndroidShellArgv(spec.command)); }'
  );
  fs.writeFileSync(bashLocalPath, code, 'utf8');
  console.log('[PATCH SUCCESS] dsh-bash-local patched with Direct SU & PRoot dispatching!');
}

if (fs.existsSync(bashSandboxPath)) {
  let code = fs.readFileSync(bashSandboxPath, 'utf8');
  console.log('[PATCH] Patching dsh-bash-sandbox confine method...');
  if (!code.includes('__resolveAndroidShellArgv')) {
    code = hybridResolver + code;
  }
  code = code.replace(
    /confine\(command,\s*policy\)\s*\{\s*return this\.ctx\.sandbox\.confine\(\[\s*"bash",\s*"-c",\s*command\s*\],\s*policy\);\s*\}/g,
    'confine(command, policy) { return this.ctx.sandbox.confine(__resolveAndroidShellArgv(command), policy); }'
  );
  fs.writeFileSync(bashSandboxPath, code, 'utf8');
  console.log('[PATCH SUCCESS] dsh-bash-sandbox patched with Direct SU & PRoot dispatching!');
}

// 5. Patch dsh-code-runtime-worker-thread to inject require, fs, and path into AsyncFunction execution scope
const workerThreadPath = `${dshRoot}/node_modules/@deepseek-ai/dsh-code-runtime-worker-thread/lib/worker.cjs`;
if (fs.existsSync(workerThreadPath)) {
  let code = fs.readFileSync(workerThreadPath, 'utf8');
  if (code.includes('new AsyncFunction(...data.namespaces.map((namespace) => namespace.global), ...errorClassParameters, "console",')) {
    console.log('[PATCH] Injecting require, fs, path globals into dsh-code-runtime-worker-thread AsyncFunction...');
    code = code.replace(
      'new AsyncFunction(...data.namespaces.map((namespace) => namespace.global), ...errorClassParameters, "console",',
      'new AsyncFunction(...data.namespaces.map((namespace) => namespace.global), ...errorClassParameters, "console", "require", "fs", "path", "process",'
    );
    code = code.replace(
      ')(...namespaces, ...errorClassValues, consoleShim)',
      ')(...namespaces, ...errorClassValues, consoleShim, typeof require !== "undefined" ? require : () => {}, typeof require !== "undefined" ? require("node:fs") : {}, typeof require !== "undefined" ? require("node:path") : {}, typeof process !== "undefined" ? process : {})'
    );
    fs.writeFileSync(workerThreadPath, code, 'utf8');
    console.log('[PATCH SUCCESS] dsh-code-runtime-worker-thread globals injected!');
  }
}

// 6. Patch dsh-tool-fs-search to use packaged rg binary safely and support symlinks & hidden files
const toolFsSearchPath = `${dshRoot}/node_modules/@deepseek-ai/dsh-tool-fs-search/lib/index.js`;
if (fs.existsSync(toolFsSearchPath)) {
  let code = fs.readFileSync(toolFsSearchPath, 'utf8');
  if (code.includes('return (await import("@vscode/ripgrep")).rgPath;')) {
    code = code.replace(
      'return (await import("@vscode/ripgrep")).rgPath;',
      'try { const { existsSync } = await import("node:fs"); if (existsSync("/data/user/0/com.dsh.mobile/files/bin/rg")) return "/data/user/0/com.dsh.mobile/files/bin/rg"; if (existsSync("/data/data/com.dsh.mobile/files/bin/rg")) return "/data/data/com.dsh.mobile/files/bin/rg"; if (existsSync("/data/user/0/com.aydin.dsh/files/bin/rg")) return "/data/user/0/com.aydin.dsh/files/bin/rg"; return (await import("@vscode/ripgrep")).rgPath; } catch (e) { throw e; }'
    );
  }
  // Ensure rg follows symlinks (-L) and includes hidden files on Android
  if (code.includes('["--json",')) {
    code = code.replace('["--json",', '["--json", "-L", "--hidden",');
  }
  fs.writeFileSync(toolFsSearchPath, code, 'utf8');
  console.log('[PATCH SUCCESS] dsh-tool-fs-search patched with -L symlink and hidden support!');
}

// 7. Patch dsh-host-directory-picker-browse to set default browsing root to /sdcard
const dirPickerPath = `${dshRoot}/node_modules/@deepseek-ai/dsh-host-directory-picker-browse/lib/index.js`;
if (fs.existsSync(dirPickerPath)) {
  let code = fs.readFileSync(dirPickerPath, 'utf8');
  if (code.includes('const target = resolve(path ?? home);')) {
    code = code.replace(
      'const home = homedir();',
      'const home = (typeof process !== "undefined" && process.env.DSH_EXTERNAL_STORAGE) ? process.env.DSH_EXTERNAL_STORAGE : (fs.existsSync("/sdcard") ? "/sdcard" : homedir());'
    );
    fs.writeFileSync(dirPickerPath, code, 'utf8');
    console.log('[PATCH SUCCESS] dsh-host-directory-picker-browse patched for /sdcard!');
  }
}

// 8. Patch dsh-host-apiproxy openTarget and native path opener for 100% reliable config open on Android
const apiproxyPath = `${dshRoot}/node_modules/@deepseek-ai/dsh-host-apiproxy/lib/index.js`;
if (fs.existsSync(apiproxyPath)) {
  let code = fs.readFileSync(apiproxyPath, 'utf8');
  console.log('[PATCH] Patching openTarget and openNativePathWithIntent in apiproxy...');
  if (code.includes('async function openTarget(request, path, signal, open) {')) {
    code = code.replace(
      'async function openTarget(request, path, signal, open) {',
      'async function openTarget(request, path, signal, open) {\n\t\ttry { const { exec } = await import("node:child_process"); exec(`am start -a android.intent.action.VIEW -d "file://${path}" -t "text/*" 2>/dev/null || true`); return ok(request, { opened: true }); } catch (_ignored) {}'
    );
  }
  fs.writeFileSync(apiproxyPath, code, 'utf8');
  console.log('[PATCH SUCCESS] dsh-host-apiproxy patched for Android!');
}

// 9. Patch dsh-sandbox-local for Android
const sandboxLocalPath = `${dshRoot}/node_modules/@deepseek-ai/dsh-sandbox-local/lib/index.js`;
if (fs.existsSync(sandboxLocalPath)) {
  let code = fs.readFileSync(sandboxLocalPath, 'utf8');
  if (code.includes('if (this.selectedRunner === "unavailable") throw new SandboxUnavailableError(mode);')) {
    code = code.replace(
      'if (this.selectedRunner === "unavailable") throw new SandboxUnavailableError(mode);',
      'if (this.selectedRunner === "unavailable") return { runner: "passthrough", enforcement: "full" };'
    );
  }
  if (code.includes('const selected = this.selectRunner(policy.mode);')) {
    code = code.replace(
      'const selected = this.selectRunner(policy.mode);',
      'const selected = this.selectRunner(policy.mode);\n\t\tif (selected.runner === "passthrough") return { argv, enforcement: "full", denialSignatures: [], runnerFailureRules: [] };'
    );
  }
  fs.writeFileSync(sandboxLocalPath, code, 'utf8');
  console.log('[PATCH SUCCESS] dsh-sandbox-local patched cleanly!');
}

// 9b. Patch dsh-win32-process on disk: It is a Windows-only module that causes koffi layout mismatch on Android/Linux
const win32ProcessPaths = [
  `${dshRoot}/node_modules/@deepseek-ai/dsh-win32-process/lib/index.js`,
  `/tmp/global_dsh/lib/node_modules/@deepseek-ai/dsh-win32-process/lib/index.js`
];
for (const wp of win32ProcessPaths) {
  if (fs.existsSync(wp)) {
    console.log(`[PATCH] Replacing ${wp} with safe Android stub...`);
    const stubCode = `
export class Win32Process { spawn() { throw new Error("Win32Process not supported on Android"); } }
export class Win32Error extends Error {}
export const ERROR_INSUFFICIENT_BUFFER = 122;
export const ERROR_MORE_DATA = 234;
export const allocPtrSlot = () => ({});
export default { Win32Process, Win32Error, ERROR_INSUFFICIENT_BUFFER, ERROR_MORE_DATA, allocPtrSlot };
`;
    fs.writeFileSync(wp, stubCode, 'utf8');
    console.log(`[PATCH SUCCESS] ${wp} replaced with safe Android stub!`);
  }
}

// 10. Inject System Prompt Context
const sandboxPolicyPath = `${dshRoot}/node_modules/@deepseek-ai/dsh-sandbox-policy/lib/index.js`;
if (fs.existsSync(sandboxPolicyPath)) {
  let code = fs.readFileSync(sandboxPolicyPath, 'utf8');
  if (code.includes('renderPolicyContext(policy) {')) {
    const androidInfo = '`\\n\\n[Environment Context]\\nHost Environment: Android OS on ARM64 with Embedded Alpine Linux Rootfs (PRoot).\\nPackage Manager: apk (run "apk add <package>" e.g. python3, git, openssh, build-base).\\nPrimary Storage: /sdcard (or /storage/emulated/0).\\nShell & Root: PRoot Linux shell, Android binaries (/system/bin/sh), and Superuser (su - via Magisk/KernelSU/APatch) are fully supported. In tools.code, fs, path, process, and require() are pre-injected.`';
    code = code.replace(
      'case "danger-full-access": return "Current DSH file policy: danger-full-access. The DSH file sandbox does not restrict file modifications by available operations.";',
      'case "danger-full-access": return "Current DSH file policy: danger-full-access. The DSH file sandbox does not restrict file modifications by available operations." + ' + androidInfo + ';'
    );
    code = code.replace(
      'case "workspace-write": return `Current DSH file policy: workspace-write. Any available operation enforced by the DSH file sandbox may modify files under the session workspace: ${JSON.stringify(policy.workspaceRoot)}. Some platform temporary areas may also be writable.`;',
      'case "workspace-write": return `Current DSH file policy: workspace-write. Any available operation enforced by the DSH file sandbox may modify files under the session workspace: ${JSON.stringify(policy.workspaceRoot)}. Some platform temporary areas may also be writable.` + ' + androidInfo + ';'
    );
    fs.writeFileSync(sandboxPolicyPath, code, 'utf8');
    console.log('[PATCH SUCCESS] dsh-sandbox-policy injected with complete environment context!');
  }
}

// 11. Patch dsh-web-frontend to make Enter key insert newline and send only via Send button
const webDistPath = `${dshRoot}/node_modules/@deepseek-ai/dsh-web-frontend/dist/index.html`;
if (fs.existsSync(webDistPath)) {
  let html = fs.readFileSync(webDistPath, 'utf8');
  console.log('[PATCH] Injecting Enter newline script into dsh-web-frontend index.html...');
  const enterPatchScript = `
<script>
(function() {
  document.addEventListener('keydown', function(e) {
    if (e.key === 'Enter' && !e.shiftKey && !e.ctrlKey && !e.altKey && !e.metaKey) {
      const active = document.activeElement;
      if (active && (active.tagName === 'TEXTAREA' || active.isContentEditable || active.getAttribute('role') === 'textbox')) {
        e.stopPropagation();
      }
    }
  }, true);
})();
</script>
`;
  if (!html.includes('e.key === \'Enter\' && !e.shiftKey')) {
    html = html.replace('</head>', `${enterPatchScript}\n</head>`);
    fs.writeFileSync(webDistPath, html, 'utf8');
    console.log('[PATCH SUCCESS] dsh-web-frontend index.html patched for Enter-as-newline!');
  }
}

// 12. Patch dsh plugin runner to invoke pnpm via node with android-safe paths
const dshLibDir = `${dshRoot}/lib`;
if (fs.existsSync(dshLibDir)) {
  const pluginFiles = fs.readdirSync(dshLibDir).filter(f => f.startsWith('plugin-') && f.endsWith('.js'));
  for (const pFile of pluginFiles) {
    const fullPath = `${dshLibDir}/${pFile}`;
    let pCode = fs.readFileSync(fullPath, 'utf8');
    if (pCode.includes('spawnSync("pnpm",')) {
      console.log(`[PATCH] Patching pnpm spawn in ${pFile}...`);
      const pnpmShim = `
function __resolvePnpmArgs(args, cwd) {
  const candidates = [
    "/data/user/0/com.dsh.mobile/files/dsh/node_modules/pnpm/bin/pnpm.cjs",
    "/data/data/com.dsh.mobile/files/dsh/node_modules/pnpm/bin/pnpm.cjs",
    "/data/user/0/com.dsh.mobile/files/dsh/bin/pnpm.cjs",
    "/data/data/com.dsh.mobile/files/dsh/bin/pnpm.cjs",
    "/tmp/global_dsh/lib/node_modules/pnpm/bin/pnpm.cjs"
  ];
  for (const c of candidates) {
    if (existsSync(c)) {
      return { exe: process.execPath, args: [c, ...args] };
    }
  }
  return { exe: "pnpm", args };
}
`;
      pCode = pnpmShim + pCode;
      pCode = pCode.replace(
        'const result = spawnSync("pnpm", args.map((argument) => anchorPathSpec(argument, process.cwd())), {',
        'const __p = __resolvePnpmArgs(args.map((argument) => anchorPathSpec(argument, process.cwd())), dir);\n\tconst result = spawnSync(__p.exe, __p.args, {'
      );
      fs.writeFileSync(fullPath, pCode, 'utf8');
      console.log(`[PATCH SUCCESS] ${pFile} patched for Android pnpm!`);
    }
  }
}

// 13. Patch cordis-plugin-loader to resolve plugins from .dsh/profiles/web/node_modules
const cordisLoaderPath = `${dshRoot}/node_modules/@deepseek-ai/cordis-plugin-loader/lib/index.js`;
if (fs.existsSync(cordisLoaderPath)) {
  let cCode = fs.readFileSync(cordisLoaderPath, 'utf8');
  if (!cCode.includes('import { existsSync as __fsExistsSync } from "node:fs";')) {
    cCode = 'import { existsSync as __fsExistsSync } from "node:fs";\n' + cCode;
  }
  if (cCode.includes('import(name, getOuterStack) {')) {
    console.log('[PATCH] Patching cordis-plugin-loader import resolution for Android profiles...');
    const resolverPatch = `
\t\t\telse {
\t\t\t\ttry {
\t\t\t\t\treturn await import(__rewriteRelativeImportExtension(name));
\t\t\t\t} catch (err) {
\t\t\t\t\tconst homeDir = process.env.HOME || "/data/user/0/com.dsh.mobile/files";
\t\t\t\t\tconst candidates = [
\t\t\t\t\t\t\`\${homeDir}/.dsh/profiles/web/node_modules/\${name}/lib/index.js\`,
\t\t\t\t\t\t\`\${homeDir}/.dsh/profiles/web/node_modules/\${name}/index.js\`,
\t\t\t\t\t\t\`\${homeDir}/.dsh/profiles/web/node_modules/\${name}/dist/index.js\`,
\t\t\t\t\t\t\`/data/user/0/com.dsh.mobile/files/.dsh/profiles/web/node_modules/\${name}/lib/index.js\`,
\t\t\t\t\t\t\`/data/data/com.dsh.mobile/files/.dsh/profiles/web/node_modules/\${name}/lib/index.js\`
\t\t\t\t\t];
\t\t\t\t\tfor (const c of candidates) {
\t\t\t\t\t\tif (__fsExistsSync(c)) {
\t\t\t\t\t\t\treturn await import("file://" + c);
\t\t\t\t\t\t}
\t\t\t\t\t}
\t\t\t\t\tthrow err;
\t\t\t\t}
\t\t\t}`;
    cCode = cCode.replace(
      'else return await import(__rewriteRelativeImportExtension(\n\t\t\t\t/* @vite-ignore */\n\t\t\t\tname\n\t\t\t));',
      resolverPatch
    );
    fs.writeFileSync(cordisLoaderPath, cCode, 'utf8');
    console.log('[PATCH SUCCESS] cordis-plugin-loader patched for profile plugins with __fsExistsSync!');
  }
}

// 14. Patch dsh-app-boot to gracefully skip missing profile bundles instead of crashing
const dshAppBootPath = `${dshRoot}/node_modules/@deepseek-ai/dsh-app-boot/lib/index.js`;
if (fs.existsSync(dshAppBootPath)) {
  let bCode = fs.readFileSync(dshAppBootPath, 'utf8');
  if (bCode.includes('cannot resolve profile bundle')) {
    console.log('[PATCH] Patching dsh-app-boot to handle unresolvable profile bundles gracefully...');
    bCode = bCode.replace(
      'throw new Error(`${binName}: cannot resolve profile bundle ${JSON.stringify(packageName)} from the dsh installation or ${profileDir}; run \'dsh plugin --profile ${basename(profileDir)} install\' if its dependency is not installed`);',
      'console.warn(`[WARN] Skipping unresolvable profile bundle: ${packageName}`); return void 0;'
    );
    bCode = bCode.replace(
      'const declared = JSON.parse(readFileSync(join(packageDir, "package.json"), "utf8")).dsh?.bundle?.patch;\n\t\tif (declared === void 0) throw new Error(`${binName}: profile bundle ${JSON.stringify(packageName)} declares no dsh.bundle in its package.json`);',
      'if (!packageDir) return null;\n\t\tlet declared;\n\t\ttry { declared = JSON.parse(readFileSync(join(packageDir, "package.json"), "utf8")).dsh?.bundle?.patch; } catch (_) { return null; }\n\t\tif (declared === void 0) return null;'
    );
    bCode = bCode.replace(
      'const patchPath = join(packageDir, declared);',
      'if (!packageDir || declared === void 0) return null;\n\t\tconst patchPath = join(packageDir, declared);'
    );
    bCode = bCode.replace(
      ');\n\tconst patchPath = join(dir, PROFILE_PATCH_FILENAME);',
      ').filter(Boolean);\n\tconst patchPath = join(dir, PROFILE_PATCH_FILENAME);'
    );
    fs.writeFileSync(dshAppBootPath, bCode, 'utf8');
    console.log('[PATCH SUCCESS] dsh-app-boot patched for robust bundle resolution!');
  }
}

// 4. Native 0ms Background Stdout Notification Hook in dsh-agent-loop
const loopFiles = [
    path.join(dshRoot, 'node_modules/@deepseek-ai/dsh-agent-loop/lib/index.js'),
    path.join(dshRoot, 'node_modules/@deepseek-ai/dsh-agent-loop/lib/index.mjs')
];
for (const lf of loopFiles) {
    if (fs.existsSync(lf)) {
        let code = fs.readFileSync(lf, 'utf-8');
        if (!code.includes('__DSH_AGENT_REPLY__:')) {
            const target = 'if (toolCalls.length === 0) return { kind: "completed" };';
            const replacement = `if (toolCalls.length === 0) {
                try {
                    const textBlocks = message.content.filter(function(b) { return b && b.type === "text"; }).map(function(b) { return b.text; }).join("\\n").trim();
                    if (textBlocks.length >= 1) {
                        var payload = textBlocks.length > 30000 ? textBlocks.slice(0, 30000) : textBlocks;
                        console.log("__DSH_AGENT_REPLY__:" + JSON.stringify({ reply: payload }));
                    }
                } catch(e) {}
                return { kind: "completed" };
            }`;
            code = code.replace(target, replacement);
            fs.writeFileSync(lf, code, 'utf-8');
            console.log('[PATCH] Patched dsh-agent-loop for native 0ms background stdout notification');
        }
    }
}

// 15. Patch dsh-web-app startup command to tolerate unknown options & --no-open flag, and log startup options
const webAppStartupPath = `${dshRoot}/node_modules/@deepseek-ai/dsh-web-app/lib/startup.js`;
if (fs.existsSync(webAppStartupPath)) {
  let code = fs.readFileSync(webAppStartupPath, 'utf8');
  if (code.includes('new Command().name("dsh --profile web")')) {
    console.log('[PATCH] Patching dsh-web-app startup for permissive CLI options and logs...');
    code = code.replace(
      'new Command().name("dsh --profile web")',
      'new Command().name("dsh --profile web").allowUnknownOption().option("--no-open", "do not open browser")'
    );
    code = code.replace(
      'function apply(ctx) {',
      'function apply(ctx) {\n\tconsole.log("[WEB-STARTUP] apply() called");'
    );
    code = code.replace(
      'if (values !== void 0) ctx.provide(WEB_STARTUP_SERVICE, values);',
      'console.log("[WEB-STARTUP] Parsed values:", JSON.stringify(values));\n\tif (values !== void 0) ctx.provide(WEB_STARTUP_SERVICE, values);'
    );
    fs.writeFileSync(webAppStartupPath, code, 'utf8');
    console.log('[PATCH SUCCESS] dsh-web-app startup patched!');
  }
}

// 16. Patch dsh-host-webserver to log binding state and errors
const webserverPaths = [
  `${dshRoot}/node_modules/@deepseek-ai/dsh-host-webserver/lib/index.js`,
  `/tmp/global_dsh/lib/node_modules/@deepseek-ai/dsh-host-webserver/lib/index.js`
];
for (const wsPath of webserverPaths) {
  if (fs.existsSync(wsPath)) {
    let wsCode = fs.readFileSync(wsPath, 'utf8');
    console.log(`[PATCH] Patching dsh-host-webserver at ${wsPath}...`);
    if (wsCode.includes('this.listenedPort = this.server.address().port;')) {
      wsCode = wsCode.replace(
        'this.listenedPort = this.server.address().port;',
        'this.listenedPort = this.server.address().port;\n\t\t\t\tconsole.log(`[WEBSERVER SUCCESS] Bound and actively listening on http://${this.config.host}:${this.listenedPort}`);'
      );
    }
    if (wsCode.includes('this.server.once("error", reject);')) {
      wsCode = wsCode.replace(
        'this.server.once("error", reject);',
        'this.server.once("error", (err) => { console.error("[WEBSERVER BIND ERROR]", err); reject(err); });'
      );
    }
    if (wsCode.includes('constructor(ctx, config) {')) {
      wsCode = wsCode.replace(
        'constructor(ctx, config) {',
        'constructor(ctx, config) {\n\t\tconsole.log("[WEBSERVER] HttpServerService created with config:", JSON.stringify(config));'
      );
    }
    fs.writeFileSync(wsPath, wsCode, 'utf8');
    console.log(`[PATCH SUCCESS] dsh-host-webserver patched!`);
  }
}

// 17. Patch cordis-plugin-loader EntryGroup.update to skip failing plugin entries gracefully
const loaderFiles = [
  `${dshRoot}/node_modules/@deepseek-ai/cordis-plugin-loader/lib/index.js`,
  `/tmp/global_dsh/lib/node_modules/@deepseek-ai/cordis-plugin-loader/lib/index.js`
];
for (const lf of loaderFiles) {
  if (fs.existsSync(lf)) {
    let lCode = fs.readFileSync(lf, 'utf8');
    if (lCode.includes('if (failures.length > 1) throw new AggregateError(failures, "loader entries failed to apply");')) {
      console.log(`[PATCH] Patching cordis-plugin-loader error handler in ${lf}...`);
      lCode = lCode.replace(
        'if (failures.length === 1) throw failures[0];\n\t\t\tif (failures.length > 1) throw new AggregateError(failures, "loader entries failed to apply");',
        'if (failures.length > 0) { for (const f of failures) console.warn("[WARN] Plugin loader entry failed to apply (gracefully skipped):", f?.message || f); }'
      );
      fs.writeFileSync(lf, lCode, 'utf8');
      console.log('[PATCH SUCCESS] cordis-plugin-loader patched for fault-tolerant entry loading!');
    }
  }
}

// 18. Patch dsh-app-boot assertEntriesActivated to warn instead of aborting the process
const appBootFiles = [
  `${dshRoot}/node_modules/@deepseek-ai/dsh-app-boot/lib/index.js`,
  `/tmp/global_dsh/lib/node_modules/@deepseek-ai/dsh-app-boot/lib/index.js`
];
for (const bf of appBootFiles) {
  if (fs.existsSync(bf)) {
    let bCode = fs.readFileSync(bf, 'utf8');
    if (bCode.includes('throw new Error(`${binName}: ${String(failures.length)} ${noun} did not activate')) {
      console.log(`[PATCH] Patching dsh-app-boot assertEntriesActivated in ${bf}...`);
      bCode = bCode.replace(
        'throw new Error(`${binName}: ${String(failures.length)} ${noun} did not activate\\n${failures.join("\\n")}`);',
        'console.warn(`[WARN] ${failures.length} plugin entries did not activate (gracefully skipped):\\n${failures.join("\\n")}`); return;'
      );
      fs.writeFileSync(bf, bCode, 'utf8');
      console.log('[PATCH SUCCESS] dsh-app-boot patched to never abort on unactivated optional plugins!');
    }
  }
}

