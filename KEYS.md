# Key system setup

There is now **one command** that does almost all of it. The only step that needs a human is
signing in to Cloudflare, because that needs your password.

```
.\setup-keys.ps1
```

That single command will:

1. Check you are signed in to Cloudflare
2. Generate your `ADMIN_TOKEN` and save it to `worker/admin-token.txt`
3. Create the key storage and bind it to the Worker
4. Upload the token as a secret
5. Deploy the Worker
6. Run three tests to prove it works
7. Print you the address and the token

Expect about two minutes, most of it waiting on the browser.

---

## The one manual step

The first time, the script stops and tells you to run:

```
npx wrangler login
```

A browser tab opens. Sign in to Cloudflare, click **Allow**, come back. After that the
script never asks again.

If PowerShell complains that scripts are blocked, run it like this instead:

```
powershell -ExecutionPolicy Bypass -File .\setup-keys.ps1
```

---

## After it finishes

You get two things printed. **The token is only ever shown this once**, so this matters:

```
  URL           https://utilityclient-keys.YOUR-NAME.workers.dev
  ADMIN_TOKEN   aVeryLongRandomString...
```

### 1. Save the token

Put it in a password manager. It is also in `worker/admin-token.txt` in this project, which
is gitignored so it will not be published.

If you lose it you cannot issue keys ever again without rebuilding the Worker by hand and
re-entering every existing key. Do not skip this.

### 2. Point the mod at the key server

The jar ships with a placeholder, so until you do this nobody's key will validate.

**Easiest, no rebuild.** Add a JVM argument to your Minecraft launcher:

```
-Dutilityclient.api=https://utilityclient-keys.YOUR-NAME.workers.dev
```

**Or rebuild it in.** Edit `src/main/java/dev/utilityclient/license/LicenseManager.java`:

```java
public static final String DEFAULT_ENDPOINT = "https://utilityclient-keys.YOUR-NAME.workers.dev";
```

Then `.\gradlew.bat build`.

**Check it worked:** launch the game, press `Insert`, click **Settings** in the sidebar. The
"Key server" row is green when the client can reach it.

### 3. Issue your first key

Go to **https://utilityclient.github.io/utility-client/#admin**

- Panel password: `6241746457`
- Key server URL: paste the `workers.dev` address
- Admin token: paste your `ADMIN_TOKEN`
- **Save and connect**, then pick a plan and press **Generate**

The token is kept in that browser tab only, so you paste it once per session. The URL is
remembered.

---

## What the script actually does, and why

You do not need any of this to run it, but it helps if something goes wrong.

**Wrangler** is Cloudflare's command line tool. It creates, configures and deploys Workers
without the dashboard. The dashboard steps and the CLI steps produce identical results; the
CLI just means you are not clicking through six screens.

**A Worker** is a small program Cloudflare runs for you. You paste the code, they host it.
No server to rent or maintain.

**A KV Namespace** is a small key-value store, also provided by Cloudflare, free on the same
plan. It is where your keys live. The script runs `wrangler kv namespace create KEYS` and
writes the resulting id into `worker/wrangler.toml`. The name `KEYS` is a convention
between the Worker code and the config, so it has to match exactly.

**A secret** is a value stored encrypted that Cloudflare never displays again. `ADMIN_TOKEN`
is one. It is the actual lock on your keys, which is why it is a secret and not a plain
variable.

### Why the token exists at all

Your site has a panel password, `6241746457`. That password lives in a JavaScript file
anyone can read by viewing the page source. It exists only to stop friends clicking around
in your admin panel.

`ADMIN_TOKEN` is the real protection. Every request that creates or lists keys must carry
it, and the Worker rejects anything without it. Someone who learns the panel password can
open the panel but cannot generate keys. Someone who learns the token can generate keys from
anywhere, and the panel password becomes irrelevant.

### Why keys cannot live inside the mod

A `.jar` is a renamed zip file. Anyone can unzip it and read the code as plain text. If your
list of valid keys were inside the mod, you would be publishing your own customer list, and
the check could be deleted in under a minute.

So the mod asks the Worker instead, and the answer comes from storage you control. That is
the only version of this that means anything.

What it still does not do: stop someone who patches the check out of their own copy. A key
stops casual sharing, which is the overwhelming majority of piracy. It is not unbreakable
DRM and should not be sold as though it is.

---

## The three tests the script runs

It finishes by checking the Worker actually works, so you find out immediately rather than
when a customer complains.

| Test | Expect | Proves |
| --- | --- | --- |
| `GET /health` | `{"ok":true,...}` | The Worker is live |
| `GET /validate?key=UC-AAAAA-...` | `"Key not recognised"` | The code runs **and** storage is bound |
| `POST /issue` with no token | `401` | The lock on your keys works |

The middle one is the most useful. A clean rejection means the KV binding is correct. If you
get a 500 error instead, the namespace is not bound.

---

## When something goes wrong

| What you see | What it means |
| --- | --- |
| `Storage key not found`, or a 500 | The KV namespace is not bound. Delete the id line in `worker/wrangler.toml`, run the script again |
| `Key not recognised` for a key you just made | Not a bug. KV is eventually consistent, wait up to a minute |
| Admin panel says `Bad admin token` | The token you pasted does not match the one you deployed |
| Admin panel says `Could not reach the key server` | Wrong URL. Needs `https://` and no trailing slash |
| `Settings` row is red in game | The mod still has the placeholder URL, or you are offline |
| `not authenticated` | Run `npx wrangler login` again |

The script is safe to run more than once. It reuses the existing namespace and token rather
than making new ones, so a failed run never leaves you with duplicates.

---

## Free tier limits

Per day, on the free plan:

| Limit | How many that allows |
| --- | --- |
| 100,000 Worker requests | ~25,000 daily players |
| 100,000 KV reads | same order |
| **1,000 KV writes** | **~250 active keys** |

Writes are the tight one. Every validation writes back a "last seen" timestamp, capped at
once per 6 hours per key, so 4 writes per key per day.

If you reach it, edit `worker/index.js`:

```js
const LAST_SEEN_INTERVAL_MS = 6 * 60 * 60 * 1000;   // change to 24 * 60 * 60 * 1000
```

Once a day instead of four times supports 1,000 keys. Validation speed is unchanged, you
just see coarser activity data.

---

## Everyday use

Redeploy after changing the Worker code:

```
cd worker
npx wrangler deploy
```

Run it locally first to catch mistakes:

```
cd worker
npx wrangler dev
```

Keys and site content both live in the same KV namespace under different prefixes
(`key:` and `site:content`), so there is nothing else to manage.
