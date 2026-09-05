import fs from "node:fs";
import { fileURLToPath, pathToFileURL } from "node:url";
import path from "node:path";
import { createRequire } from "node:module";

const require = createRequire(import.meta.url);
const __dirname = path.dirname(fileURLToPath(import.meta.url));

// Register asar fs interception hooks
const asarNode = require(path.join(__dirname, "asar-node"));
asarNode.register();

/**
 * Resolve an export target from package.json `exports` field given a subpath.
 */
function resolveExportTarget(exportsField, subpath) {
  if (!exportsField) return null;

  if (typeof exportsField === "string") {
    if (subpath === "." || subpath === "") return exportsField;
    return null;
  }

  if (typeof exportsField !== "object") return null;

  let candidate = exportsField[subpath];
  if (!candidate && subpath === ".") {
    candidate = exportsField["."];
  }
  if (!candidate && subpath === ".") {
    candidate = exportsField;
  }

  function extractString(val) {
    if (!val) return null;
    if (typeof val === "string") return val;
    if (typeof val === "object") {
      return extractString(val.node) || extractString(val.import) || extractString(val.module) || extractString(val.default) || extractString(val.require);
    }
    return null;
  }

  return extractString(candidate);
}

/**
 * Searches for a package in the asar archive or parent directories
 */
function findPackageInAsar(startDir, baseAsarPath, packageName) {
  let curr = startDir;
  while (curr.startsWith(baseAsarPath)) {
    const candidate = path.join(curr, "node_modules", packageName);
    if (fs.existsSync(candidate)) return candidate;
    const parent = path.dirname(curr);
    if (parent === curr) break;
    curr = parent;
  }
  // Root node_modules in asar
  const rootNm = path.join(baseAsarPath, "node_modules", packageName);
  if (fs.existsSync(rootNm)) return rootNm;
  return null;
}

/**
 * Resolves a bare specifier (including subpaths e.g. '@pkg/foo' or 'pkg/subpath')
 */
function resolveFromAsar(baseAsarPath, specifier, parentDir) {
  let pkgName = "";
  let subpath = ".";

  if (specifier.startsWith("@")) {
    const parts = specifier.split("/");
    if (parts.length >= 2) {
      pkgName = parts[0] + "/" + parts[1];
      if (parts.length > 2) {
        subpath = "./" + parts.slice(2).join("/");
      }
    } else {
      pkgName = specifier;
    }
  } else {
    const parts = specifier.split("/");
    pkgName = parts[0];
    if (parts.length > 1) {
      subpath = "./" + parts.slice(1).join("/");
    }
  }

  const nmDir = findPackageInAsar(parentDir || baseAsarPath, baseAsarPath, pkgName);
  if (!nmDir) return null;

  const pkgJsonPath = path.join(nmDir, "package.json");
  let entryPoint = null;

  if (fs.existsSync(pkgJsonPath)) {
    try {
      const pkg = JSON.parse(fs.readFileSync(pkgJsonPath, "utf8"));
      if (pkg.exports) {
        entryPoint = resolveExportTarget(pkg.exports, subpath);
      }
      if (!entryPoint && subpath === ".") {
        if (typeof pkg.module === "string") entryPoint = pkg.module;
        else if (typeof pkg.main === "string") entryPoint = pkg.main;
      }
    } catch (_) {}
  }

  if (!entryPoint) {
    if (subpath === ".") {
      entryPoint = "index.js";
    } else {
      entryPoint = subpath.startsWith("./") ? subpath.substring(2) : subpath;
    }
  }

  if (typeof entryPoint !== "string") {
    entryPoint = "index.js";
  }

  let fullTarget = path.resolve(nmDir, entryPoint);

  if (!fs.existsSync(fullTarget)) {
    if (fs.existsSync(fullTarget + ".js")) fullTarget += ".js";
    else if (fs.existsSync(fullTarget + ".mjs")) fullTarget += ".mjs";
    else if (fs.existsSync(fullTarget + ".cjs")) fullTarget += ".cjs";
    else if (fs.existsSync(fullTarget + ".json")) fullTarget += ".json";
    else if (fs.existsSync(path.join(fullTarget, "index.js"))) fullTarget = path.join(fullTarget, "index.js");
    else if (fs.existsSync(path.join(fullTarget, "index.mjs"))) fullTarget = path.join(fullTarget, "index.mjs");
    else if (fs.existsSync(path.join(fullTarget, "index.cjs"))) fullTarget = path.join(fullTarget, "index.cjs");
  }

  return fs.existsSync(fullTarget) ? fullTarget : null;
}

/**
 * Determine module format (commonjs vs module vs json)
 */
function getFormatForFile(filePath) {
  if (filePath.endsWith(".json")) return "json";
  if (filePath.endsWith(".cjs")) return "commonjs";
  if (filePath.endsWith(".mjs")) return "module";

  // Check nearest package.json for "type": "module"
  let curr = path.dirname(filePath);
  while (curr.length > 1) {
    const pj = path.join(curr, "package.json");
    if (fs.existsSync(pj)) {
      try {
        const pkg = JSON.parse(fs.readFileSync(pj, "utf8"));
        return pkg.type === "module" ? "module" : "commonjs";
      } catch (_) {}
      break;
    }
    const parent = path.dirname(curr);
    if (parent === curr) break;
    curr = parent;
  }
  return "commonjs";
}

export async function resolve(specifier, context, nextResolve) {
  // Safe fallback for packages not bundled or native on Android
  if (specifier === "sharp" || specifier.startsWith("sharp/")) {
    return {
      shortCircuit: true,
      url: "data:text/javascript,export default function sharp(){ return { resize: () => ({ toBuffer: () => Promise.resolve(Buffer.alloc(0)) }) }; }; export const fit = {};",
      format: "module"
    };
  }
  if (specifier === "@earendil-works/pi-ai" || specifier.startsWith("@earendil-works/pi-ai")) {
    return {
      shortCircuit: true,
      url: "data:text/javascript,export default {}; export const getModel = () => null;",
      format: "module"
    };
  }

  // Case 1: Bare package specifier (e.g. '@deepseek-ai/dsh-app-boot', 'eventsource-parser', 'ipaddr.js')
  if (!specifier.startsWith(".") && !specifier.startsWith("/") && !specifier.startsWith("file://") && !specifier.startsWith("node:")) {
    const parent = context.parentURL ? fileURLToPath(context.parentURL) : "";
    if (parent.includes(".asar")) {
      const asarIdx = parent.indexOf(".asar");
      const baseAsarPath = parent.substring(0, asarIdx + 5);
      const parentDir = path.dirname(parent);

      const resolvedTarget = resolveFromAsar(baseAsarPath, specifier, parentDir);
      if (resolvedTarget) {
        const format = getFormatForFile(resolvedTarget);
        return {
          shortCircuit: true,
          url: pathToFileURL(resolvedTarget).href,
          format: format
        };
      }
    }
  }

  // Case 2: Relative or direct path inside .asar
  if (specifier.includes(".asar") || (context.parentURL && context.parentURL.includes(".asar"))) {
    let target = specifier;
    if (target.startsWith(".")) {
      const parentDir = path.dirname(fileURLToPath(context.parentURL));
      target = path.resolve(parentDir, specifier);
    } else if (target.startsWith("file://")) {
      target = fileURLToPath(target);
    }

    if (target.includes(".asar")) {
      if (!fs.existsSync(target)) {
        if (fs.existsSync(target + ".js")) target += ".js";
        else if (fs.existsSync(target + ".mjs")) target += ".mjs";
        else if (fs.existsSync(target + ".cjs")) target += ".cjs";
        else if (fs.existsSync(target + ".json")) target += ".json";
        else if (fs.existsSync(path.join(target, "index.js"))) target = path.join(target, "index.js");
        else if (fs.existsSync(path.join(target, "index.mjs"))) target = path.join(target, "index.mjs");
        else if (fs.existsSync(path.join(target, "index.cjs"))) target = path.join(target, "index.cjs");
      }

      if (fs.existsSync(target)) {
        const format = getFormatForFile(target);
        return {
          shortCircuit: true,
          url: pathToFileURL(target).href,
          format: format
        };
      }
    }
  }

  return nextResolve(specifier, context);
}

export async function load(url, context, nextLoad) {
  if (url.includes(".asar")) {
    const filePath = fileURLToPath(url);

    // Stub out Windows-specific modules that crash on Android
    if (filePath.includes("@deepseek-ai/dsh-win32-process") || filePath.includes("win32-process")) {
      return {
        shortCircuit: true,
        format: "module",
        source: `
export class Win32Process { spawn() { throw new Error('Not supported on Android'); } }
export const ERROR_INSUFFICIENT_BUFFER = 122;
export const ERROR_MORE_DATA = 234;
export default { Win32Process, ERROR_INSUFFICIENT_BUFFER, ERROR_MORE_DATA };
`
      };
    }

    // Polyfill opentelemetry named exports for ESM
    if (filePath.includes("@opentelemetry/sdk-logs") || filePath.includes("sdk-logs")) {
      return {
        shortCircuit: true,
        format: "module",
        source: `
export class BatchLogRecordProcessor { onEmit() {} shutdown() { return Promise.resolve(); } forceFlush() { return Promise.resolve(); } }
export class SimpleLogRecordProcessor { onEmit() {} shutdown() { return Promise.resolve(); } forceFlush() { return Promise.resolve(); } }
export class LoggerProvider { getLogger() { return { emit() {} }; } shutdown() { return Promise.resolve(); } }
export default { BatchLogRecordProcessor, SimpleLogRecordProcessor, LoggerProvider };
`
      };
    }

    const format = context.format || getFormatForFile(filePath);
    let source = fs.readFileSync(filePath, "utf8");

    // Polyfill CommonJS exports/module if executed in module scope
    if (format === "module") {
      if ((source.includes("exports.") || source.includes("module.exports")) && !source.includes("export default") && !source.includes("export {")) {
        source = `const module = { exports: {} };\nconst exports = module.exports;\n${source}\nexport default (module.exports.default || module.exports);\n`;
      }
    }

    return {
      shortCircuit: true,
      format: format,
      source: source
    };
  }

  return nextLoad(url, context);
}

