# GitHub Pages

The site is a plain static folder. GitHub Pages serves `site/` from the `main` branch using
the workflow at `.github/workflows/pages.yml`, so the site and the mod stay in one repo.

**Your address will be `https://YOUR-USERNAME.github.io`** — it is not `utilityclient.github.io`
unless you name the repository `utilityclient.github.io`. Repository names must be unique
across all of GitHub, so pick something like `utility-client`.

## One-time setup

1. Create the repository on GitHub. **Do not** tick "Add a README", "Add .gitignore", or
   "Choose a license" — an empty repo is what you want, so the first push is not a conflict.
2. Set the Pages source. Repo → **Settings** → **Pages** → **Source: GitHub Actions**.
   Without this the workflow runs but the deploy step fails.
3. Push. The workflow publishes automatically.

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
