import { register, createRequire } from "node:module";
import { fileURLToPath } from "node:url";
import path from "node:path";

const require = createRequire(import.meta.url);
const __dirname = path.dirname(fileURLToPath(import.meta.url));

// Register asar fs interception hooks in the main process thread
const asarNode = require(path.join(__dirname, "asar-node"));
asarNode.register();

process.on("uncaughtException", (err) => {
  console.error("[PRELOAD UNCAUGHT EXCEPTION]", err && err.stack ? err.stack : err);
});

process.on("unhandledRejection", (reason) => {
  console.error("[PRELOAD UNHANDLED REJECTION]", reason && reason.stack ? reason.stack : reason);
});

// Register ESM worker hooks
register(path.join(__dirname, "asar-esm-worker.mjs"), import.meta.url);

