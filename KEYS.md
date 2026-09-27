# Key system setup

Everything below is free. You need a Cloudflare account, which you have. Budget about ten
minutes the first time.

The key system has three moving parts. The site is already done, so there are two left.

```
  admin panel  ──POST /issue──▶  Cloudflare Worker  ──▶  KV (your keys)
       ▲                                │
       └────── GET /list ───────────────┘
                                      ▲
  the mod  ──GET /validate?key=...──────┘
```

---

## Step 1 — Create the Worker (3 min)

1. Go to **https://dash.cloudflare.com** → **Workers & Pages**
2. **Create** → **Start with** → **Hello World** → **Get started**
3. Name it `utilityclient-keys` → **Deploy**

You now have an address. It looks like this:

```
https://utilityclient-keys.utilityclient.workers.dev
```

**Write it down.** You need it in three places.

## Step 2 — Add the admin token (2 min)

This token is the thing that actually protects your keys. Not the panel password.

1. Still in the Worker → **Settings** → **Variables and Secrets**
2. **Add** → Variable name `ADMIN_TOKEN` → type **Secret**
3. Paste a long random string. Something like 40+ characters of random letters and digits.
4. **Save the value somewhere safe.** If you lose it you cannot issue keys any more, and
   you would have to create a new Worker and migrate every key.
5. **Deploy** when it prompts you.

A quick way to generate one: open a new tab to
`https://www.cloudflare.com/landing/workers-ga/turnstile/` — it has a random string
generator. Or just type a long random string yourself.

## Step 3 — Bind the key storage (2 min)

1. **Settings** → **Bindings** → **Add** → **KV Namespace**
2. Variable name: `KEYS`

   This must be spelled exactly `KEYS` in capitals. The code looks it up by that name and
   will not work with anything else.
3. Create the namespace, then **Deploy**.

## Step 4 — Upload the code (1 min)

1. In the Worker → **Edit code** (or **Quick edit**)
2. Select everything in the editor and delete it
3. Open `worker/index.js` from this project, copy the whole file
4. Paste it in, then **Deploy**

## Step 5 — Check it works (1 min)

Open this in your browser, using your address:

```
https://utilityclient-keys.YOUR-NAME.workers.dev/health
```

You want to see:

```json
{"ok":true,"service":"utilityclient-keys","time":1234567890}
```

Then check a key that does not exist:

```
https://utilityclient-keys.YOUR-NAME.workers.dev/validate?key=UC-AAAAA-BBBBB-CCCCC-DDDDD
```

You want to see it **rejected**, not an error:

```json
{"valid":false,"tier":"NONE","expiresAt":0,"message":"Key not recognised"}
```

If either of those is wrong, stop here and send me what you got. Something is not bound
correctly.

---

## Step 6 — Issue your first key (2 min)

1. Go to **https://utilityclient.github.io/utility-client/#admin**
2. Enter the panel password
3. Paste your **Worker URL** into "Key server URL"
4. Paste your **`ADMIN_TOKEN`** into "Admin token"
5. **Save and connect** — the status line should turn green
6. Pick a plan, set a quantity, add a note like `for Sam`, press **Generate**
7. **Copy all** and send the key to your buyer

The token is kept in the tab only and disappears when you close the browser, so you paste
it once per session. The URL is remembered.

## Step 7 — Point the mod at the key server (1 min)

The jar ships with a placeholder address, so until you set this, nobody's key will validate
and the client stays locked.

**Easiest way, no rebuild:** add a JVM argument to your Minecraft launcher.

```
-Dutilityclient.api=https://utilityclient-keys.YOUR-NAME.workers.dev
```

**Or rebuild it in:** open `src/main/java/dev/utilityclient/license/LicenseManager.java`
and change this line:

```java
public static final String DEFAULT_ENDPOINT = "https://utilityclient-keys.YOUR-NAME.workers.dev";
```

Then:

```powershell
.\gradlew.bat build
```

**Check it worked:** launch the game, press `Insert`, then go to **Settings** in the
sidebar. The "Key server" row shows your address in green when the client can reach it, red
when it cannot.

---

## Common problems

| Symptom | Cause |
| --- | --- |
| Key not recognised, right after generating it | KV is eventually consistent. Wait up to a minute. |
| `Storage key not found` or a 500 error | The KV namespace is not bound, or the variable is not called `KEYS` |
| Panel says "Bad admin token" | The `ADMIN_TOKEN` secret does not match what you typed into the panel |
| Panel says "Could not reach the key server" | Wrong URL. It needs `https://` and must have no trailing slash |
| Settings row is red in game | The mod still has the placeholder URL, or you are offline |
| Generating does nothing | You have not entered the admin token in this browser session |

---

## Free tier limits

Worth knowing before you have a lot of customers.

**KV writes: 1,000 per day.** Reading a key is free. The "last seen" timestamp is written
back at most once every 6 hours per key, which works out to roughly **250 active keys per
day**. If you go past that, open `worker/index.js` and change:

```js
const LAST_SEEN_INTERVAL_MS = 6 * 60 * 60 * 1000;
```

to `24 * 60 * 60 * 1000` for once a day. Validation is unaffected either way.

**KV reads: 100,000 per day.** The mod caches for 6 hours, so one player costs about four
reads a day. You would need 25,000 daily players to hit this.

**Worker requests: 100,000 per day.** Same ballpark.

**Site content: 200 KB.** Mostly the logo if you upload one as a data URL. Keep uploads
under 400 KB and you will not notice the limit.

---

## How the site editor fits in

The same Worker also stores your site content, which is how the **Publish to site** button
in the admin panel works. It saves to the same KV namespace under a different key
(`site:content`), so there is nothing extra to set up.

The four endpoints it uses:

| Route | Who can call it | What it does |
| --- | --- | --- |
| `GET /content` | anyone | The saved site content, read on page load |
| `POST /content` | admin token | Saves the content, this is what Publish calls |
| `GET /validate` | anyone | Checks whether a key is valid |
| `POST /issue` | admin token | Creates keys |

The site degrades gracefully. If `/content` is unreachable, the page renders from the static
HTML that shipped in the repo, so your site is never down because the Worker is.
