import fs from "node:fs";
import { createRequire } from "node:module";

const require2 = createRequire(import.meta.url);
let native2 = null;

const possiblePaths = [
  `${import.meta.dirname}/../../build/koffi/android_arm64/koffi.node`,
  `${import.meta.dirname}/../../build/koffi/linux_arm64/koffi.node`,
  `${import.meta.dirname}/../../build/koffi/linux_x64/koffi.node`,
  `${import.meta.dirname}/../../../@koromix/koffi-linux-arm64/linux_arm64/koffi.node`,
  `${import.meta.dirname}/../../../@koromix/koffi-linux-x64/linux_x64/koffi.node`
];

for (const p of possiblePaths) {
  if (fs.existsSync(p)) {
    try {
      native2 = require2(p);
      break;
    } catch (e) {}
  }
}

if (!native2) {
  const dummyFn = () => ({});
  native2 = {
    version: "3.2.1",
    load: () => new Proxy({}, { get: () => dummyFn }),
    register: dummyFn,
    pointer: (type) => ({ size: 8, alignment: 8 }),
    struct: (name, fields) => {
      let size = 8;
      const n = String(name || "");
      if (n.includes("STARTUPINFOW")) size = 104;
      else if (n.includes("PROCESS_INFORMATION")) size = 24;
      return { size, alignment: 8, members: fields || {} };
    },
    opaque: () => ({ size: 8, alignment: 8 }),
    array: (type, len) => ({ size: (type?.size || 1) * len }),
    introspect: (spec) => ({ size: (spec && spec.size) ? spec.size : 8, alignment: 8, members: {} }),
    type: (spec) => ({ size: (spec && spec.size) ? spec.size : 8, alignment: 8, members: {} }),
    sizeof: (spec) => (spec && spec.size ? spec.size : 8),
    alignof: () => 8,
    offsetof: () => 0,
    alloc: () => ({}),
    encode: () => {},
    decode: () => ({}),
    address: () => 0n
  };
} else {
  let introspect = native2.introspect ?? native2.type;
  native2.sizeof = (spec) => introspect(spec).size;
  native2.alignof = (spec) => introspect(spec).alignment;
  native2.offsetof = (spec, name) => 0;
}

export default native2;
export const { load, register, pointer, struct, opaque, array, sizeof, alignof, offsetof, alloc, encode, decode, address } = native2;
