import fs from "node:fs";
import { fileURLToPath } from "node:url";
import path from "node:path";
import { createRequire } from "node:module";

const require = createRequire(import.meta.url);
const __dirname = path.dirname(fileURLToPath(import.meta.url));

// Register asar fs interception hooks
const asarNode = require(path.join(__dirname, "asar-node"));
asarNode.register();

function resolveFromAsarNodeModules(baseAsarPath, packageName) {
  const nmDir = path.join(baseAsarPath, "node_modules", packageName);
  if (!fs.existsSync(nmDir)) return null;

  const pkgJsonPath = path.join(nmDir, "package.json");
  let entryPoint = null;
  if (fs.existsSync(pkgJsonPath)) {
    try {
      const pkg = JSON.parse(fs.readFileSync(pkgJsonPath, "utf8"));
      if (pkg.exports) {
        if (typeof pkg.exports === "string") {
          entryPoint = pkg.exports;
        } else if (pkg.exports["."]) {
          const dot = pkg.exports["."];
          if (typeof dot === "string") entryPoint = dot;
          else if (typeof dot === "object") {
            entryPoint = dot.import || dot.default || dot.require || dot.node;
          }
        }
      }
      if (!entryPoint && pkg.module) entryPoint = pkg.module;
      if (!entryPoint && pkg.main) entryPoint = pkg.main;
    } catch (_) {}
  }

  if (!entryPoint) entryPoint = "index.js";
  let fullTarget = path.resolve(nmDir, entryPoint);

  if (!fs.existsSync(fullTarget)) {
    if (fs.existsSync(fullTarget + ".js")) fullTarget += ".js";
    else if (fs.existsSync(fullTarget + ".mjs")) fullTarget += ".mjs";
    else if (fs.existsSync(path.join(fullTarget, "index.js"))) fullTarget = path.join(fullTarget, "index.js");
  }

  return fullTarget;
}

export async function resolve(specifier, context, nextResolve) {
  // Case 1: Bare package specifier imported from inside an .asar file (e.g. '@deepseek-ai/dsh-app-boot')
  if (!specifier.startsWith(".") && !specifier.startsWith("/") && !specifier.startsWith("file://") && !specifier.startsWith("node:")) {
    const parent = context.parentURL ? fileURLToPath(context.parentURL) : "";
    if (parent.includes(".asar")) {
      const asarIdx = parent.indexOf(".asar");
      const baseAsarPath = parent.substring(0, asarIdx + 5);
      const resolvedTarget = resolveFromAsarNodeModules(baseAsarPath, specifier);
      if (resolvedTarget && fs.existsSync(resolvedTarget)) {
        return {
          shortCircuit: true,
          url: "file://" + resolvedTarget,
          format: resolvedTarget.endsWith(".json") ? "json" : "module"
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
        else if (fs.existsSync(target + ".json")) target += ".json";
        else if (fs.existsSync(path.join(target, "index.js"))) target = path.join(target, "index.js");
        else if (fs.existsSync(path.join(target, "index.mjs"))) target = path.join(target, "index.mjs");
      }

      if (fs.existsSync(target)) {
        const isJson = target.endsWith(".json");
        return {
          shortCircuit: true,
          url: "file://" + target,
          format: isJson ? "json" : "module"
        };
      }
    }
  }

  return nextResolve(specifier, context);
}

export async function load(url, context, nextLoad) {
  if (url.includes(".asar")) {
    const filePath = fileURLToPath(url);
    const source = fs.readFileSync(filePath, "utf8");
    const isJson = filePath.endsWith(".json");
    return {
      shortCircuit: true,
      format: isJson ? "json" : (context.format || "module"),
      source: source
    };
  }

  return nextLoad(url, context);
}
