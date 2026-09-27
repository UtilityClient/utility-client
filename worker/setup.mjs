// One-shot setup for the Utility Client key API.
//
// Does everything that can be done from a machine, so the only human step is signing in to
// Cloudflare once. Run with:  node setup.mjs
//
//   1. checks you are signed in to wrangler
//   2. generates an ADMIN_TOKEN and saves it next to this file
//   3. creates the KV namespace and writes its id into wrangler.toml
//   4. uploads ADMIN_TOKEN as a Worker secret
//   5. deploys
//   6. runs the two smoke tests
//
// Safe to run twice. Existing resources are reused rather than duplicated.

import { execFileSync, spawnSync } from "node:child_process";
import { randomBytes } from "node:crypto";
import { readFileSync, writeFileSync, existsSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const HERE = dirname(fileURLToPath(import.meta.url));
const TOML = join(HERE, "wrangler.toml");
const TOKEN_FILE = join(HERE, "admin-token.txt");
const WORKER_NAME = "utilityclient-keys";

const log = (msg) => console.log(msg);
const step = (msg) => console.log(`\n=== ${msg} ===`);
const ok = (msg) => console.log(`  [ok] ${msg}`);
const warn = (msg) => console.log(`  [!] ${msg}`);

function wrangler(args, opts = {}) {
  return spawnSync("wrangler.cmd", args, {
    cwd: HERE,
    encoding: "utf8",
    shell: true,
    ...opts,
  });
}

function readToken() {
  if (existsSync(TOKEN_FILE)) {
    const value = readFileSync(TOKEN_FILE, "utf8").trim();
    if (value) return value;
  }
  // 48 random characters, URL safe. Printed once at the end so it can be saved properly.
  const value = randomBytes(36).toString("base64url");
  writeFileSync(TOKEN_FILE, value + "\n", "utf8");
  return value;
}

function namespaceId() {
  const toml = readFileSync(TOML, "utf8");
  const match = toml.match(/^id\s*=\s*"([^"]+)"/m);
  return match ? match[1].trim() : "";
}

// Wrangler rejects the whole config file if a binding has an empty id, and it validates
// that before running any command at all, including "whoami". So the binding is only ever
// written once a real id exists.
function writeNamespaceId(id) {
  let toml = readFileSync(TOML, "utf8");
  if (namespaceId()) {
    toml = toml.replace(/^id\s*=\s*"[^"]*"/m, `id = "${id}"`);
  } else {
    toml = toml.replace(
      /# The KV binding is added by setup\.mjs[\s\S]*$/m,
      `[[kv_namespaces]]\nbinding = "KEYS"\nid = "${id}"\n`
    );
  }
  writeFileSync(TOML, toml, "utf8");
}

/* ---------------------------------------------------------------- 1. auth */

step("Checking Cloudflare sign-in");

const who = wrangler(["whoami"]);
if (who.status !== 0) {
  console.log(`
  Wrangler is not signed in yet, so this has to stop here.

  Run this in PowerShell and press Enter:

      npx.cmd wrangler login

  Use the .cmd. Plain "npx" is a PowerShell script this machine blocks.

  A browser tab opens. Choose Cloudflare, sign in, and click "Allow".
  Then come back and run:

      node setup.mjs

  That is the only step that genuinely needs a human, because it needs your
  Cloudflare password.
`);
  process.exit(1);
}
ok(who.stdout.trim().split("\n").filter(Boolean).slice(0, 3).join(" | "));

/* ---------------------------------------------------------------- 2. token */

step("Admin token");

const token = readToken();
ok(`ADMIN_TOKEN ready, ${token.length} characters`);
warn(`saved to worker${"\\"}admin-token.txt - move this somewhere safe and do not commit it`);

/* ---------------------------------------------------------------- 3. kv */

step("KV namespace for keys");

let id = namespaceId();
if (id) {
  ok(`namespace already bound, id ${id}`);
} else {
  const created = wrangler(["kv", "namespace", "create", "KEYS"]);
  const combined = `${created.stdout || ""}\n${created.stderr || ""}`;
  const match = combined.match(/([0-9a-f]{32})/i);
  if (!match) {
    console.error("Could not read the new namespace id out of wrangler's output:");
    console.error(combined.trim());
    process.exit(1);
  }
  id = match[1];
  writeNamespaceId(id);
  ok(`namespace created, id ${id}`);
  ok("written into wrangler.toml");
}

/* ---------------------------------------------------------------- 4. secret */

step("Uploading ADMIN_TOKEN");

const put = wrangler(["secret", "put", "ADMIN_TOKEN"], { input: token + "\n" });
if (put.status !== 0) {
  console.error("Failed to set the secret:");
  console.error((put.stderr || put.stdout || "").trim());
  process.exit(1);
}
ok("secret stored (Cloudflare will not show it again)");

/* ---------------------------------------------------------------- 5. deploy */

step("Deploying");

const deploy = wrangler(["deploy"]);
const deployOut = `${deploy.stdout || ""}\n${deploy.stderr || ""}`;
console.log(deployOut.trim());

const urlMatch = deployOut.match(/https:\/\/[a-z0-9-]+\.[a-z0-9-]+\.workers\.dev/i);
if (deploy.status !== 0 || !urlMatch) {
  console.error("\nDeploy did not finish cleanly. Fix the error above and re-run.");
  process.exit(1);
}

const URL = urlMatch[0].replace(/\/$/, "");
ok(`live at ${URL}`);

/* ---------------------------------------------------------------- 6. smoke */

step("Smoke tests");

function get(path) {
  const result = spawnSync("curl", ["-s", "-w", "\n%{http_code}", URL + path], {
    encoding: "utf8",
  });
  const out = result.stdout || "";
  const cut = out.lastIndexOf("\n");
  return { body: out.slice(0, cut), status: out.slice(cut + 1).trim() };
}

const health = get("/health");
if (health.status === "200") {
  ok(`GET /health -> ${health.body.trim()}`);
} else {
  warn(`GET /health -> HTTP ${health.status} ${health.body.trim()}`);
}

const validate = get("/validate?key=UC-AAAAA-BBBBB-CCCCC-DDDDD");
if (validate.status === "200") {
  ok("GET /validate on a fake key -> rejected cleanly, storage is wired up");
  log(`       ${validate.body.trim()}`);
} else {
  warn(`GET /validate -> HTTP ${validate.status} ${validate.body.trim()}`);
}

const noAuth = spawnSync(
  "curl",
  ["-s", "-o", "NUL", "-w", "%{http_code}", "-X", "POST", URL + "/issue",
   "-H", "content-type: application/json", "-d", "{\"tier\":\"DAY\",\"count\":1}"],
  { encoding: "utf8" }
);
if ((noAuth.stdout || "").trim() === "401") {
  ok("POST /issue without a token -> 401, the lock works");
} else {
  warn(`POST /issue without a token -> HTTP ${(noAuth.stdout || "").trim()}, expected 401`);
}

/* ---------------------------------------------------------------- done */

console.log(`
============================================================
  Key server is live.

  URL           ${URL}

  ADMIN_TOKEN   ${token}

  Do these two things now:

  1. Put the token above in a password manager. This file
     cannot show it to you again, and without it you cannot
     issue keys.

  2. Add this JVM argument to your Minecraft launcher, or edit
     DEFAULT_ENDPOINT in LicenseManager.java and rebuild:

       -Dutilityclient.api=${URL}

  Then open the admin panel and paste both in:

     https://utilityclient.github.io/utility-client/#admin
============================================================
`);
