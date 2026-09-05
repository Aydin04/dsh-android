import { register } from "node:module";
import { fileURLToPath } from "node:url";
import path from "node:path";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
register(path.join(__dirname, "asar-esm-worker.mjs"), import.meta.url);
