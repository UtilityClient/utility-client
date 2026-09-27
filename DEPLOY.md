# Deploying this

Two pieces go online. **The site is already done** — it is live at
<https://somone290.github.io/utility-client/> on GitHub Pages. What is left is the **key
server**, a free Cloudflare Worker.

| Piece | Status |
| --- | --- |
| Site | Live |
| Admin panel | <https://somone290.github.io/utility-client/#admin> |
| Key server | **Not deployed yet** |
| Client pointing at the key server | **Not configured yet** |

Until the key server exists, no key can be validated and the client stays locked.

---

## Part 1 — The key server (the remaining work)

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

## Part 2 — The site (already done)

The site is **live** at <https://somone290.github.io/utility-client/> on GitHub Pages, wired up
by `.github/workflows/pages.yml`. Every push that touches `site/` republishes it. Admin panel:

<https://somone290.github.io/utility-client/#admin>

There is nothing left to do here. The options below are only relevant if you ever want to move
it, for example onto Cloudflare so the site and the Worker share an account.

### Option A — Cloudflare Pages (same account as the Worker)

1. Cloudflare dashboard → **Workers & Pages** → **Create** → **Pages** → **Connect to Git**
2. Connect a GitHub repository, or choose **Direct Upload**
3. If connecting Git:
   - Project name: `utilityclient`
   - Production branch: `main`
   - **Build command**: leave empty
   - **Build output directory**: `site`
4. Deploy. You get `https://utilityclient.pages.dev`

> **Note:** the site is already live at <https://somone290.github.io/utility-client/> on
> GitHub Pages, so this whole step is optional. It is only worth doing if you would rather
> host the site on the same Cloudflare account as the Worker.

### Option B — Just open it locally

Double-click `site/index.html`. Everything works except the admin panel's key generation,
which needs a real `http(s)` origin to call the Worker.

---

## Part 3 — Issuing your first key

1. Visit <https://somone290.github.io/utility-client/#admin>
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
| **GitHub Pages** | `somone290.github.io/utility-client` | **Live now.** Free, no extra setup |
| Cloudflare Pages | `utilityclient.pages.dev` | Free forever, same account as the Worker |
| Netlify / Vercel | `utilityclient.netlify.app` | Free tier, drag and drop |
| Cloudflare custom domain | `yourname.com` | Only if you already own a domain |

The site is running on GitHub Pages at <https://somone290.github.io/utility-client/>, which is
free and needs nothing further. If you buy a domain later you can point it at GitHub Pages or
move the site to Cloudflare Pages without touching any code.

One thing to know: a GitHub **project** page lives under `/utility-client/`, not at the bare
domain root. That is normal and does not affect anything. If you ever want the shorter
`somone290.github.io` address, the repository would have to be renamed to
`somone290.github.io`, which GitHub allows once per account.

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
