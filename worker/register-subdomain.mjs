// Registers a workers.dev subdomain for the account, which is a one-time step that the
// dashboard otherwise handles. Uses the OAuth token wrangler already stored from login.
//
//   node register-subdomain.mjs

import { spawnSync } from "node:child_process";
import { readFileSync, existsSync } from "node:fs";
import { join } from "node:path";
import { homedir } from "node:os";

const CFG = join(homedir(), "AppData", "Roaming", "xdg.config", ".wrangler", "config", "default.toml");

if (!existsSync(CFG)) {
  console.error("Wrangler config not found at " + CFG);
  process.exit(1);
}

const toml = readFileSync(CFG, "utf8");
const token = (toml.match(/^oauth_token\s*=\s*"([^"]+)"/m) || [])[1];
if (!token) {
  console.error("No oauth_token in the wrangler config. Run `npx.cmd wrangler login` first.");
  process.exit(1);
}

// The account id comes from wrangler itself rather than being hard coded.
const who = spawnSync("wrangler.cmd", ["whoami"], { encoding: "utf8", shell: true });
const whoText = `${who.stdout || ""}\n${who.stderr || ""}`;
const accountMatch = whoText.match(/([0-9a-f]{32})/i);
const accountId = accountMatch && accountMatch[1];
if (!accountId) {
  console.error("Could not read the account id out of `wrangler whoami`:");
  console.log(whoText.trim());
  process.exit(1);
}
console.log("account " + accountId + "\n");

// workers.dev subdomains are global, so try a few and take the first that is free.
const candidates = [
  "utilityclient",
  "utilityclientkeys",
  "ucclientkeys",
  "utility-client-keys",
  "utilityclients",
  "somone290keys",
  "ucmodkeys"
];

for (const name of candidates) {
  const res = await fetch(
    `https://api.cloudflare.com/client/v4/accounts/${accountId}/workers/subdomain`,
    {
      method: "PUT",
      headers: {
        authorization: `Bearer ${token}`,
        "content-type": "application/json"
      },
      body: JSON.stringify({ subdomain: name })
    }
  );
  const body = await res.json().catch(() => ({}));

  if (res.ok && body.success) {
    console.log(`  [ok] subdomain registered: ${name}.workers.dev`);
    console.log(`       your worker will be at https://utilityclient-keys.${name}.workers.dev`);
    process.exit(0);
  }

  const message = (body.errors && body.errors[0] && body.errors[0].message) || `HTTP ${res.status}`;
  console.log(`  [--] ${name}: ${message}`);
}

console.error("\n  Every candidate name was taken. Pick your own and retry:");
console.error("  https://dash.cloudflare.com -> Workers & Pages -> create a subdomain there,");
console.error("  then run this script again with your name in the list.");
process.exit(1);
