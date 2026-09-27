# Deploying this

Two pieces go online: the **site** (free static hosting) and the **key server** (a free
Cloudflare Worker). Everything here is on a free tier. Nothing costs money, but a `.com`
domain does — see [About the address](#about-the-address).

---

## Part 1 — The key server (do this first)

The client refuses to run modules without a valid key, and the key list cannot live inside
the jar, because anyone can unzip a jar and delete the branch that reads it. So the keys live
on a server.

### 1. Create the Worker

1. Sign in at <https://dash.cloudflare.com>
2. **Workers & Pages** → **Create** → **Start with** → **Hello World** → **Get started**
3. Name it something like `utilityclient-keys` → **Deploy**
4. You now have an address like `https://utilityclient-keys.YOUR-NAME.workers.dev`
   — **copy it somewhere safe**, you need it in three places.

### 2. Add the admin token

This token is the real protection for your keys. The password typed into the website is only
a convenience gate.

1. In the Worker → **Settings** → **Variables and Secrets**
2. **Add** → type `ADMIN_TOKEN` → **Secret** (not a plain variable)
3. Paste a long random string. Generate one at
   <https://www.cloudflare.com/landing/workers-ga/turnstile/> or just type 40+ random
   characters. **Save the secret value somewhere you can find it** — if you lose it you cannot
   issue keys any more.
4. Deploy again when prompted.

### 3. Bind the key storage

1. **Settings** → **Bindings** → **Add** → **KV Namespace**
2. Variable name: `KEYS` (exactly this, the code looks it up by that name)
3. Create the namespace, then **Deploy**.

### 4. Check it works

Open this in a browser, replacing the address with yours:

```
https://utilityclient-keys.YOUR-NAME.workers.dev/health
```

You should see `{"ok":true,"service":"utilityclient-keys","time":...}`.

And a key that does not exist should be rejected, not crash:

```
https://utilityclient-keys.YOUR-NAME.workers.dev/validate?key=UC-AAAAA-BBBBB-CCCCC-DDDDD
```

Expected: `{"valid":false,"tier":"NONE","expiresAt":0,"message":"Key not recognised"}`

### Note on KV consistency

Cloudflare KV is eventually consistent. A key can take **up to about a minute** to start
validating right after you issue it. Everything else — revoking, listing, expiry — is
immediate. If you issue a key and it does not work straight away, wait a minute before
assuming you did something wrong.

### Note on the free tier limits

The free KV plan allows **1,000 writes per day**. Every `/validate` call reads for free, but
the "last seen" timestamp is written back at most once every 6 hours per key. That works out
to roughly **250 active keys per day** before you would start hitting the limit.

If you get more customers than that, open `worker/index.js` and raise
`LAST_SEEN_INTERVAL_MS` to `24 * 60 * 60 * 1000` (once a day per key). The read path is
unaffected, so validation still works — you just see less precise activity data.

Permanent keys are stored with **no** TTL, so they never expire out of storage. Timed keys
are kept for 90 days past their expiry so the admin list can still show them as expired.


---

## Part 2 — The site

### Option A — Cloudflare Pages (recommended, same account)

1. Cloudflare dashboard → **Workers & Pages** → **Create** → **Pages** → **Connect to Git**
2. Connect a GitHub repository, or choose **Direct Upload**
3. If connecting Git:
   - Project name: `utilityclient`
   - Production branch: `main`
   - **Build command**: leave empty
   - **Build output directory**: `site`
4. Deploy. You get `https://utilityclient.pages.dev`

### Option B — GitHub Pages (free, no Cloudflare needed for the site)

1. Push this repo to GitHub
2. **Settings** → **Pages** → **Source: Deploy from a branch** → `main` / `root`
3. But the site is in the `site/` folder, so either move it to the repo root, or add a
   `.github/workflows/pages.yml`:

```yaml
name: Deploy site
on:
  push:
    branches: [main]
    paths: ['site/**']
jobs:
  deploy:
    runs-on: ubuntu-latest
    permissions:
      pages: write
      id-token: write
    environment:
      name: github-pages
      url: ${{ steps.deployment.outputs.page_url }}
    steps:
      - uses: actions/checkout@v4
      - uses: actions/configure-pages@v5
      - uses: actions/upload-pages-artifact@v3
        with:
          path: site
      - id: deployment
        uses: actions/deploy-pages@v4
```

You get `https://YOUR-USERNAME.github.io`

### Option C — Just open it locally

Double-click `site/index.html`. Everything works except the admin panel's key generation,
which needs a real `http(s)` origin to call the Worker.

---

## Part 3 — Issuing your first key

1. Visit `https://utilityclient.pages.dev/#admin`
2. Enter the panel password
3. Paste your **key server URL** and your **ADMIN_TOKEN**, then **Save and connect**
4. Pick a plan — **1 Day**, **1 Week**, **1 Month** or **Permanent**
5. Set a quantity, add a note like `for Steve`, press **Generate**
6. **Copy all** and send the key to your buyer

The token is kept in `sessionStorage`, so it disappears when you close the tab. The URL is
remembered. You only paste the token once per browsing session.

---

## Part 4 — Pointing the client at the key server

The mod ships with a placeholder. You have two ways to set the real one.

**Without rebuilding** — add a JVM argument to your launcher:

```
-Dutilityclient.api=https://utilityclient-keys.YOUR-NAME.workers.dev
```

**With rebuilding** — edit `LicenseManager.java`:

```java
public static final String DEFAULT_ENDPOINT = "https://utilityclient-keys.YOUR-NAME.workers.dev";
```

Then `.\gradlew.bat build` and ship the new jar.

Check it took: open the menu → **Settings** → the "Key server" row shows the address in
green when the client can reach it.

---

## About the address

A `.com` is not free. Cheapest registrars are roughly $10–15 for the first year and then
renew annually, and you would need to buy it yourself — I cannot register a domain on your
behalf.

What you can get for nothing:

| Option | Address | Notes |
| --- | --- | --- |
| Cloudflare Pages | `utilityclient.pages.dev` | Free forever, same account as the Worker |
| GitHub Pages | `username.github.io` | Free, needs a GitHub repo |
| Netlify / Vercel | `utilityclient.netlify.app` | Free tier, drag and drop |
| Cloudflare custom domain | `yourname.com` | Only if you already own a domain |

`utilityclient.pages.dev` is the closest thing to what you asked for that is genuinely free,
and it can be pointed at a real domain later without changing any code.

---

## What the key system does and does not do

**Does:**
- Stops casual sharing. The overwhelming majority of piracy is someone forwarding a jar, and
  a key stops that.
- Lets you revoke a key without shipping a new build.
- Expires keys on a schedule, enforced server side, so the client cannot be tricked into
  thinking a key is still good.
- Never locks you out over a network problem. There is a 72 hour grace window after the last
  successful check, and results are cached for 6 hours.

**Does not:**
- Make the mod uncrackable. It runs on the buyer's machine. Someone who decompiles the jar can
  remove the check. No client-side scheme has ever prevented this, and none should be sold as
  though it does.
- Protect the panel password. The `#admin` password lives in a JavaScript file that anyone can
  read. The `ADMIN_TOKEN` on the Worker is what protects the keys.

If you want stronger enforcement than this, the honest answer is to move the valuable logic to
a server the client has to call per use. That is a much larger project and it needs paying
customers to be worth it.
