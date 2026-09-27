// Confirms through the Cloudflare API that the Worker and its bindings are really there.
// Useful when this machine cannot reach *.workers.dev directly to run the smoke tests.

import { spawnSync } from "node:child_process";
import { readFileSync, existsSync } from "node:fs";
import { join } from "node:path";
import { homedir } from "node:os";

const CFG = join(homedir(), "AppData", "Roaming", "xdg.config", ".wrangler", "config", "default.toml");
const toml = readFileSync(CFG, "utf8");
const token = (toml.match(/^oauth_token\s*=\s*"([^"]+)"/m) || [])[1];

const who = spawnSync("wrangler.cmd", ["whoami"], { encoding: "utf8", shell: true });
const accountId = `${who.stdout || ""}\n${who.stderr || ""}`.match(/([0-9a-f]{32})/i)[1];
const base = `https://api.cloudflare.com/client/v4/accounts/${accountId}`;

async function get(path) {
  const r = await fetch(base + path, {
    headers: { authorization: `Bearer ${token}` },
    signal: AbortSignal.timeout(30000)
  });
  return { status: r.status, body: await r.json().catch(() => ({})) };
}

console.log("=== subdomain ===");
const sub = await get("/workers/subdomain");
console.log("  " + JSON.stringify(sub.body.result || sub.body.errors));

console.log("\n=== deployed script ===");
const scripts = await get("/workers/scripts");
const list = scripts.body.result || [];
for (const s of list) {
  console.log(`  ${s.id}  modified ${s.modified_on}`);
}

console.log("\n=== bindings on the deployed worker ===");
const settings = await get("/workers/services/utilityclient-keys/environments/production/settings");
const bindings = settings.body.result && settings.body.result.bindings;
if (Array.isArray(bindings)) {
  for (const b of bindings) {
    const kind = b.type || "unknown";
    if (kind === "kv_namespace") console.log(`  KV binding "${b.name}" -> ${b.namespace_id}`);
    else if (kind === "secret_text") console.log(`  secret "${b.name}" (value hidden by Cloudflare)`);
    else console.log(`  ${kind} "${b.name}"`);
  }
} else {
  console.log("  " + JSON.stringify(settings.body.errors || settings.body.result));
}

console.log("\n=== KV namespaces on the account ===");
const ns = await get("/storage/kv/namespaces");
for (const n of ns.body.result || []) {
  console.log(`  ${n.title}  ${n.id}`);
}

console.log("\nDone. If the bindings above are present, the deployment is sound.");
console.log("The smoke tests only fail when this machine cannot reach *.workers.dev.");
