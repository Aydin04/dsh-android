import fs from "node:fs";
import { fileURLToPath } from "node:url";
import path from "node:path";
import { createRequire } from "node:module";

const require = createRequire(import.meta.url);
const __dirname = path.dirname(fileURLToPath(import.meta.url));

// Register asar fs interception hooks
const asarNode = require(path.join(__dirname, "asar-node"));
asarNode.register();

export async function resolve(specifier, context, nextResolve) {
  if (specifier.includes(".asar")) {
    let target = specifier;
    if (target.startsWith("file://")) {
      target = fileURLToPath(target);
    }
    
    // Resolve relative or exact extensions inside asar
    if (!fs.existsSync(target)) {
      if (fs.existsSync(target + ".js")) target += ".js";
      else if (fs.existsSync(target + ".mjs")) target += ".mjs";
      else if (fs.existsSync(target + ".json")) target += ".json";
      else if (fs.existsSync(path.join(target, "index.js"))) target = path.join(target, "index.js");
      else if (fs.existsSync(path.join(target, "index.mjs"))) target = path.join(target, "index.mjs");
    }

    const isJson = target.endsWith(".json");
    return {
      shortCircuit: true,
      url: target.startsWith("file://") ? target : ("file://" + target),
      format: isJson ? "json" : "module"
    };
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
