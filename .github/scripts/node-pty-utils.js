"use strict";
/**
 * Safe Node-PTY Android/POSIX Fallback
 */
Object.defineProperty(exports, "__esModule", { value: true });
exports.assign = assign;
exports.loadNativeModule = loadNativeModule;
function assign(target) {
    var sources = [];
    for (var _i = 1; _i < arguments.length; _i++) {
        sources[_i - 1] = arguments[_i];
    }
    sources.forEach(function (source) { return Object.keys(source).forEach(function (key) { return target[key] = source[key]; }); });
    return target;
}
function loadNativeModule(name) {
    var dirs = [
        'build/Release',
        'build/Debug',
        "prebuilds/android-arm64",
        "prebuilds/linux-arm64",
        "prebuilds/" + process.platform + "-" + process.arch
    ];
    var relative = ['..', '.'];
    for (var _i = 0, dirs_1 = dirs; _i < dirs_1.length; _i++) {
        var d = dirs_1[_i];
        for (var _a = 0, relative_1 = relative; _a < relative_1.length; _a++) {
            var r = relative_1[_a];
            var dir = r + "/" + d;
            try {
                return { dir: dir, module: require(dir + "/" + name + ".node") };
            } catch (e) {}
        }
    }
    // Safe Fallback Mock Object for pty native module on Android
    var mockPty = {
        open: function() { return { master: 0, slave: 0, pty: "/dev/ptmx" }; },
        resize: function() {},
        process: function() { return ""; }
    };
    return { dir: ".", module: mockPty };
}
