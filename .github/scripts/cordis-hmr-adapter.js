import { Service } from "@deepseek-ai/cordis";
import { fileURLToPath } from "node:url";

export class Hmr extends Service {
	constructor(ctx, config) {
		super(ctx, "hmr");
		this.config = config || {};
		this.internal = ctx.loader?.internal || null;
		try {
			this.baseDir = fileURLToPath(new URL(this.config.base || ".", ctx.baseUrl));
		} catch {
			this.baseDir = process.cwd();
		}
		this.configs = new Map();
		this.watcher = null;
		this.externals = new Set();
		this.accepted = new Set();
		this.declined = new Set();
		this.stashed = new Set();
	}

	async registerConfig(filename, refresh) {
		return () => {};
	}

	async registerResource(filename, refresh) {
		return () => {};
	}
}

export default Hmr;
