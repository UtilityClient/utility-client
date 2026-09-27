/**
 * Utility Client — key API
 *
 * A single Cloudflare Worker that issues and validates licence keys. Keys live in a KV
 * namespace, so nothing is hard coded in the jar and you can revoke a key without
 * shipping a new build.
 *
 * Setup
 * -----
 * 1. Cloudflare dashboard -> Workers & Pages -> Create -> Worker.
 * 2. Paste this file in, then Settings -> Variables and Secrets:
 *      ADMIN_TOKEN  = a long random string you keep to yourself
 *    (this is the real gate; the password in the website is only a convenience)
 * 3. Bind a KV namespace:
 *      Settings -> Bindings -> Add -> KV Namespace -> variable name KEYS
 * 4. Deploy. Copy the resulting https://...workers.dev address.
 *
 * Client wiring
 * -------------
 * Pass it to the mod with a JVM argument, no rebuild needed:
 *    -Dutilityclient.api=https://your-worker.workers.dev
 * or edit DEFAULT_ENDPOINT in LicenseManager.java and rebuild.
 *
 * Free tier note: KV is eventually consistent, so a key can take up to about a minute to
 * become visible right after you issue it. Everything else is immediate.
 */

const TIER_DAYS = { DAY: 1, WEEK: 7, MONTH: 30, PERMANENT: 0 };
const KEY_PREFIX = 'key:';
const ALPHABET = '0123456789ABCDEFGHJKMNPQRSTVWXYZ'; // Crockford base32, no I L O U

/**
 * How often a validate may write "last seen" back to KV. The free tier allows 1,000 writes
 * per day, so a six hour interval supports roughly 250 keys checking in daily before you
 * would start hitting the limit. Raise it if you have more customers than that.
 */
const LAST_SEEN_INTERVAL_MS = 6 * 60 * 60 * 1000;

/**
 * How long a KV record is kept.
 *
 * Permanent keys get no expiry at all, otherwise the key would silently disappear from
 * storage after the TTL and stop validating. Timed keys are kept for 90 days past their
 * expiry so the admin list can still show them as expired rather than losing the history.
 */
function ttlFor(record) {
  if (record.tier === 'PERMANENT' || !record.expiresAt) return undefined;
  const seconds = Math.ceil((record.expiresAt - Date.now()) / 1000) + 90 * 24 * 60 * 60;
  return Math.max(60, seconds);
}

/** Put options, omitting the TTL entirely for permanent keys. */
function putOptions(record) {
  const ttl = ttlFor(record);
  return ttl === undefined ? {} : { expirationTtl: ttl };
}

/* ---------------------------------------------------------------- plumbing */

function json(body, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: {
      'content-type': 'application/json; charset=utf-8',
      'access-control-allow-origin': '*',
      'access-control-allow-headers': 'content-type, x-admin-token',
      'access-control-allow-methods': 'GET, POST, OPTIONS',
      'cache-control': 'no-store',
    },
  });
}

function fail(message, status = 400) {
  return json({ valid: false, error: message }, status);
}

function newKey() {
  const groups = [];
  for (let g = 0; g < 4; g += 1) {
    let group = '';
    for (let i = 0; i < 5; i += 1) {
      group += ALPHABET[crypto.getRandomValues(new Uint32Array(1))[0] % ALPHABET.length];
    }
    groups.push(group);
  }
  return `UC-${groups.join('-')}`;
}

function expiresFor(tier, from = Date.now()) {
  const days = TIER_DAYS[tier];
  if (!days) return 0;
  return from + days * 24 * 60 * 60 * 1000;
}

function authorised(request, env) {
  const expected = (env && env.ADMIN_TOKEN) || '';
  if (!expected) return false;
  const given = request.headers.get('x-admin-token') || '';
  if (given.length !== expected.length) return false;
  // Constant time compare so the token cannot be guessed one character at a time.
  let diff = 0;
  for (let i = 0; i < expected.length; i += 1) {
    diff |= given.charCodeAt(i) ^ expected.charCodeAt(i);
  }
  return diff === 0;
}

/**
 * Cheap per-isolate flood guard. Not a substitute for a real WAF, but it stops a single
 * client from hammering /validate in a tight loop on the free tier.
 */
const hits = new Map();
function rateLimited(request) {
  const ip = request.headers.get('cf-connecting-ip') || 'unknown';
  const now = Date.now();
  const window = 60_000;
  const limit = 60;
  const list = (hits.get(ip) || []).filter((t) => now - t < window);
  list.push(now);
  hits.set(ip, list);
  if (hits.size > 5000) hits.clear();
  return list.length > limit;
}

/* ---------------------------------------------------------------- site content */

const CONTENT_KEY = 'site:content';
const MAX_CONTENT_BYTES = 200 * 1024;

/**
 * Public read, used by the site on every page load. The site treats a failure here as
 * harmless, so this must never throw.
 */
async function handleGetContent(request, env) {
  const stored = await env.KEYS.get(CONTENT_KEY, 'json');
  if (!stored) {
    return json({ ok: true, content: null, updatedAt: 0 });
  }
  return json({ ok: true, content: stored.content, updatedAt: stored.updatedAt || 0 });
}

/** Admin write. This is what the Publish button in the admin panel calls. */
async function handlePutContent(request, env) {
  if (!authorised(request, env)) {
    return fail('Bad admin token', 401);
  }

  let body;
  try {
    body = await request.json();
  } catch {
    return fail('Body must be JSON');
  }

  const content = body && body.content;
  if (!content || typeof content !== 'object') {
    return fail('Missing content object');
  }

  if (JSON.stringify(content).length > MAX_CONTENT_BYTES) {
    return fail('Content is too large. The limit is about 200 KB, most of which is the logo.', 413);
  }

  // Only the keys the site understands are kept, so a hand crafted request cannot stuff
  // arbitrary data into KV.
  const clean = {
    overrides: sanitiseStrings(content.overrides),
    theme: sanitiseStrings(content.theme),
    hidden: sanitiseBools(content.hidden),
    order: content.order && typeof content.order === 'object' ? content.order : {},
    logo: sanitiseLogo(content.logo),
    savedAt: Date.now(),
  };

  const updatedAt = Date.now();
  await env.KEYS.put(CONTENT_KEY, JSON.stringify({ content: clean, updatedAt }));

  return json({ ok: true, content: clean, updatedAt });
}

function sanitiseStrings(input) {
  const out = {};
  if (!input || typeof input !== 'object') return out;
  let count = 0;
  for (const key of Object.keys(input)) {
    if (count >= 2000) break;
    if (key.length > 120) continue;
    const value = input[key];
    if (typeof value !== 'string') continue;
    out[key] = value.slice(0, 4000);
    count += 1;
  }
  return out;
}

function sanitiseBools(input) {
  const out = {};
  if (!input || typeof input !== 'object') return out;
  for (const key of Object.keys(input)) {
    if (key.length > 120) continue;
    out[key] = !!input[key];
  }
  return out;
}

function sanitiseLogo(logo) {
  if (!logo || typeof logo !== 'object') return null;
  const out = {};
  if (typeof logo.text === 'string') out.text = logo.text.slice(0, 3);
  if (typeof logo.data === 'string') {
    const d = logo.data;
    // Images only. Nothing that a browser could execute.
    if (d.startsWith('data:image/') || d.startsWith('https://') || d.startsWith('http://')) {
      out.data = d.slice(0, 150 * 1024);
    }
  }
  return Object.keys(out).length ? out : null;
}

/* ---------------------------------------------------------------- routes */

async function handleValidate(request, env) {
  if (rateLimited(request)) {
    return fail('Too many checks, slow down', 429);
  }

  const key = (new URL(request.url).searchParams.get('key') || '').trim().toUpperCase();
  if (!key) {
    return json({ valid: false, tier: 'NONE', expiresAt: 0, message: 'No key supplied' });
  }

  const raw = await env.KEYS.get(KEY_PREFIX + key, 'json');
  if (!raw) {
    return json({ valid: false, tier: 'NONE', expiresAt: 0, message: 'Key not recognised' });
  }
  if (raw.revoked) {
    return json({
      valid: false, tier: raw.tier, expiresAt: raw.expiresAt || 0,
      message: 'This key was revoked',
    });
  }

  const now = Date.now();
  if (raw.tier !== 'PERMANENT' && raw.expiresAt && now > raw.expiresAt) {
    return json({
      valid: false, tier: raw.tier, expiresAt: raw.expiresAt,
      message: 'This key has expired',
    });
  }

  // Record activity, but at most once an hour, to stay well inside the free write quota.
  if (!raw.lastSeen || now - raw.lastSeen > LAST_SEEN_INTERVAL_MS) {
    raw.lastSeen = now;
    raw.seen = (raw.seen || 0) + 1;
    env.KEYS.put(KEY_PREFIX + key, JSON.stringify(raw), putOptions(raw));
  }

  return json({
    valid: true,
    tier: raw.tier,
    expiresAt: raw.expiresAt || 0,
    message: raw.tier === 'PERMANENT' ? 'Licensed for life' : 'Licensed',
  });
}

async function handleIssue(request, env) {
  if (!authorised(request, env)) {
    return fail('Bad admin token', 401);
  }

  let body;
  try {
    body = await request.json();
  } catch {
    return fail('Body must be JSON');
  }

  const tier = String(body.tier || '').toUpperCase();
  if (!TIER_DAYS[tier] && tier !== 'PERMANENT') {
    return fail('Tier must be DAY, WEEK, MONTH or PERMANENT');
  }

  const count = Math.min(50, Math.max(1, parseInt(body.count, 10) || 1));
  const note = String(body.note || '').slice(0, 120);
  const now = Date.now();
  const expiresAt = expiresFor(tier, now);

  const issued = [];
  for (let i = 0; i < count; i += 1) {
    let key = newKey();
    // Astronomically unlikely to collide, but check anyway so we never overwrite a live key.
    let attempts = 0;
    while (await env.KEYS.get(KEY_PREFIX + key) && attempts < 5) {
      key = newKey();
      attempts += 1;
    }
    const record = {
      key,
      tier,
      expiresAt,
      note,
      createdAt: now,
      createdAtIso: new Date(now).toISOString(),
      revoked: false,
      seen: 0,
    };
    await env.KEYS.put(KEY_PREFIX + key, JSON.stringify(record), putOptions(record));
    issued.push(record);
  }

  return json({ ok: true, tier, issued });
}

async function handleList(request, env) {
  if (!authorised(request, env)) {
    return fail('Bad admin token', 401);
  }

  const now = Date.now();
  const all = [];
  let cursor;
  do {
    const page = await env.KEYS.list({ prefix: KEY_PREFIX, cursor, limit: 1000 });
    for (const item of page.keys) {
      const value = await env.KEYS.get(item.name, 'json');
      if (value) all.push(value);
    }
    cursor = page.list_complete ? undefined : page.cursor;
  } while (cursor);

  all.sort((a, b) => (b.createdAt || 0) - (a.createdAt || 0));

  return json({
    ok: true,
    keys: all.map((k) => ({
      ...k,
      state: k.revoked
        ? 'revoked'
        : (k.tier !== 'PERMANENT' && k.expiresAt && now > k.expiresAt ? 'expired' : 'active'),
    })),
  });
}

async function handleRevoke(request, env) {
  if (!authorised(request, env)) {
    return fail('Bad admin token', 401);
  }

  let body;
  try {
    body = await request.json();
  } catch {
    return fail('Body must be JSON');
  }

  const key = String(body.key || '').trim().toUpperCase();
  if (!key) return fail('No key supplied');

  const raw = await env.KEYS.get(KEY_PREFIX + key, 'json');
  if (!raw) return fail('Key not found', 404);

  raw.revoked = !body.restore;
  await env.KEYS.put(KEY_PREFIX + key, JSON.stringify(raw), putOptions(raw));

  return json({ ok: true, key, revoked: raw.revoked });
}

async function handleDelete(request, env) {
  if (!authorised(request, env)) {
    return fail('Bad admin token', 401);
  }
  let body;
  try {
    body = await request.json();
  } catch {
    return fail('Body must be JSON');
  }
  const key = String(body.key || '').trim().toUpperCase();
  if (!key) return fail('No key supplied');
  await env.KEYS.delete(KEY_PREFIX + key);
  return json({ ok: true, key, deleted: true });
}

export default {
  async fetch(request, env) {
    if (request.method === 'OPTIONS') {
      return new Response(null, {
        status: 204,
        headers: {
          'access-control-allow-origin': '*',
          'access-control-allow-headers': 'content-type, x-admin-token',
          'access-control-allow-methods': 'GET, POST, OPTIONS',
          'access-control-max-age': '86400',
        },
      });
    }

    const url = new URL(request.url);
    const route = url.pathname.replace(/\/+$/, '') || '/';

    try {
      if (route === '/content' && request.method === 'GET') {
        return await handleGetContent(request, env);
      }
      if (route === '/content' && request.method === 'POST') {
        return await handlePutContent(request, env);
      }
      if (route === '/health') {
        return json({ ok: true, service: 'utilityclient-keys', time: Date.now() });
      }
      if (route === '/validate' && request.method === 'GET') {
        return await handleValidate(request, env);
      }
      if (route === '/issue' && request.method === 'POST') {
        return await handleIssue(request, env);
      }
      if (route === '/list' && request.method === 'GET') {
        return await handleList(request, env);
      }
      if (route === '/revoke' && request.method === 'POST') {
        return await handleRevoke(request, env);
      }
      if (route === '/delete' && request.method === 'POST') {
        return await handleDelete(request, env);
      }
      return fail('Not found', 404);
    } catch (error) {
      return fail('Server error: ' + (error && error.message ? error.message : 'unknown'), 500);
    }
  },
};
