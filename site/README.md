# GitHub Pages

The site is a plain static folder. GitHub Pages serves `site/` from the `main` branch using
the workflow at `.github/workflows/pages.yml`, so the site and the mod stay in one repo.

**Your address will be `https://YOUR-USERNAME.github.io`** — it is not `utilityclient.github.io`
unless you name the repository `utilityclient.github.io`. Repository names must be unique
across all of GitHub, so pick something like `utility-client`.

## One-time setup

### 1. Create the repository

On GitHub → **New repository**. Name it `utility-client` (or anything you like, it does not
have to match the site name).

**Leave all four initialisation boxes unticked.** An empty repository is what you want,
otherwise the first push conflicts with GitHub's own README and licence files.

### 2. Set the Pages source

In your new repo → **Settings** → **Pages** → **Build and deployment** →
**Source: GitHub Actions**.

This step is easy to miss. Without it the workflow runs green but the deploy step fails.

### 3. Push

The repo is already initialised and committed locally, so from this folder:

```powershell
git remote add origin https://github.com/YOUR-USERNAME/utility-client.git
git push -u origin main
```

GitHub will ask you to sign in. If it opens a browser and asks for a password, use your
account password — but if you have 2FA on, it needs a **Personal Access Token** instead:
GitHub → **Settings** → **Developer settings** → **Personal access tokens** → **Tokens
(classic)** → **Generate new token** with the `repo` scope.

Within about a minute the site is live at `https://YOUR-USERNAME.github.io`, and the admin
panel at `https://YOUR-USERNAME.github.io/#admin`.

## Everyday use

Push a change to anything in `site/` and the site updates in about a minute:

```powershell
git add site/
git commit -m "update copy"
git push
```

You can also trigger it by hand from the **Actions** tab → **Deploy site** → **Run workflow**,
which is handy for testing a change before pushing it.

## Custom domain

If you ever buy a real name, you do not need to touch any code:

1. Repo → **Settings** → **Pages** → **Custom domain** → type it → Save
2. Cloudflare or your registrar → add the `CNAME` records GitHub shows you
3. Tick **Enforce HTTPS** once the certificate has issued

The site keeps working during the changeover, and you can point the domain at Cloudflare
Pages later instead if you would rather host there.
