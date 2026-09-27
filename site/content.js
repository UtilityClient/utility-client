// Utility Client — content engine
//
// Every editable element in the page carries data-edit="<path>" and a data-label. This file
// reads those, applies saved overrides, and persists changes in two places:
//
//   localStorage  - instant, this browser only, survives a refresh
//   key server    - shared, so every visitor sees the change
//
// Nothing here is required for the site to work. With JavaScript off, or with no saved
// overrides, the page is exactly the static HTML that shipped in the repo.

(function () {
  "use strict";

  var LOCAL_KEY = "utilityclient.content";
  var REMOTE_CACHE_KEY = "utilityclient.content.remote";
  var CACHE_TTL_MS = 5 * 60 * 1000;

  var state = {
    overrides: {},   // path -> string
    theme: {},       // css variable name -> value
    hidden: {},      // section id -> true
    order: {},       // repeat region name -> array of paths, in display order
    savedAt: 0
  };

  /* ------------------------------------------------------------------ helpers */

  function allFields() {
    return Array.prototype.slice.call(document.querySelectorAll("[data-edit]"));
  }

  function fieldByPath(path) {
    return document.querySelector('[data-edit="' + cssEscape(path) + '"]');
  }

  // Slugs contain spaces, which are valid inside quoted attribute selectors but we escape
  // anyway so odd labels can never break a query.
  function cssEscape(value) {
    if (window.CSS && CSS.escape) return CSS.escape(value);
    return String(value).replace(/["\\]/g, "\\$&");
  }

  function sectionOf(el) {
    var node = el.closest("section, footer, header");
    if (!node) return { id: "page", label: "Page" };
    var heading = node.querySelector("h2");
    return {
      id: node.id || node.className.split(" ")[0] || "page",
      label: heading ? heading.textContent.trim() : (node.id || "Page")
    };
  }

  function normaliseText(value) {
    return String(value == null ? "" : value)
      .replace(/\s+/g, " ")
      .trim();
  }

  /* ------------------------------------------------------------------ apply */

  function apply() {
    allFields().forEach(function (el) {
      var path = el.getAttribute("data-edit");
      if (!path) return;
      if (Object.prototype.hasOwnProperty.call(state.overrides, path)) {
        // Only replace the text if the element has no element children we would destroy.
        if (el.children.length === 0) {
          el.textContent = state.overrides[path];
        }
      }
    });

    applyTheme();
    applyHidden();
  }

  function applyTheme() {
    var root = document.documentElement;
    Object.keys(state.theme).forEach(function (name) {
      var value = state.theme[name];
      if (value) root.style.setProperty(name, value);
    });
    updateFavicon();
  }

  function applyHidden() {
    Object.keys(state.hidden).forEach(function (id) {
      var node = document.getElementById(id);
      if (node) node.hidden = !!state.hidden[id];
    });
  }

  function updateFavicon() {
    var mark = document.querySelector("[data-logo]");
    var accent = state.theme["--purple"];
    if (!mark) return;

    if (state.logo && state.logo.data) {
      mark.style.backgroundImage = 'url("' + state.logo.data + '")';
      mark.style.backgroundSize = "cover";
      mark.style.backgroundPosition = "center";
      mark.textContent = "";
      return;
    }
    if (state.logo && state.logo.text) {
      mark.textContent = state.logo.text;
    }
    if (accent) {
      // Only recolour the plain mark, never an uploaded image.
      if (!state.logo || !state.logo.data) {
        mark.style.background = accent;
      }
      mark.style.color = "#120018";
    }
  }

  /* ------------------------------------------------------------------ defaults */

  // Defaults are read from the DOM itself, so there is no second copy of the copy to keep
  // in sync with the HTML.
  function snapshotDefaults() {
    var map = {};
    allFields().forEach(function (el) {
      map[el.getAttribute("data-edit")] = normaliseText(el.textContent);
    });
    return map;
  }

  /* ------------------------------------------------------------------ storage */

  function saveLocal() {
    state.savedAt = Date.now();
    try {
      localStorage.setItem(LOCAL_KEY, JSON.stringify(state));
      return true;
    } catch (error) {
      return false;
    }
  }

  function loadLocal() {
    try {
      var raw = localStorage.getItem(LOCAL_KEY);
      if (!raw) return false;
      merge(JSON.parse(raw));
      return true;
    } catch (error) {
      return false;
    }
  }

  function merge(incoming) {
    if (!incoming || typeof incoming !== "object") return;
    state.overrides = Object.assign(state.overrides, incoming.overrides || {});
    state.theme = Object.assign(state.theme, incoming.theme || {});
    state.hidden = Object.assign(state.hidden, incoming.hidden || {});
    state.order = Object.assign(state.order, incoming.order || {});
    if (incoming.logo) state.logo = incoming.logo;
    if (incoming.savedAt) state.savedAt = incoming.savedAt;
  }

  function resetAll() {
    state.overrides = {};
    state.theme = {};
    state.hidden = {};
    state.order = {};
    state.logo = null;
    state.savedAt = 0;
    try { localStorage.removeItem(LOCAL_KEY); } catch (error) { /* nothing to do */ }
    document.documentElement.removeAttribute("style");
    location.reload();
  }

  /* ------------------------------------------------------------------ remote */

  function apiBase() {
    return localStorage.getItem("utilityclient.apiUrl") || "";
  }

  function adminToken() {
    return sessionStorage.getItem("utilityclient.adminToken") || "";
  }

  /**
   * Pulls shared content from the key server. A short cache keeps a page load from making
   * an extra request every time, and a failure is never fatal: the page still renders from
   * whatever is in localStorage.
   */
  function pullRemote() {
    var base = apiBase();
    if (!base) return Promise.resolve(null);

    var cachedAt = parseInt(localStorage.getItem(REMOTE_CACHE_KEY) || "0", 10);
    if (Date.now() - cachedAt < CACHE_TTL_MS) {
      var cached = localStorage.getItem(LOCAL_KEY);
      if (cached) { merge(JSON.parse(cached)); apply(); return Promise.resolve(null); }
    }

    return fetch(base + "/content", { headers: { Accept: "application/json" } })
      .then(function (response) {
        if (!response.ok) throw new Error("HTTP " + response.status);
        return response.json();
      })
      .then(function (data) {
        if (!data || !data.content) return null;
        state.overrides = data.content.overrides || {};
        state.theme = data.content.theme || {};
        state.hidden = data.content.hidden || {};
        state.order = data.content.order || {};
        state.logo = data.content.logo || null;
        state.savedAt = data.content.savedAt || Date.now();
        saveLocal();
        localStorage.setItem(REMOTE_CACHE_KEY, String(Date.now()));
        apply();
        return data.content;
      })
      .catch(function () {
        return null;
      });
  }

  function pushRemote() {
    var base = apiBase();
    if (!base) return Promise.reject(new Error("No key server URL set"));
    var token = adminToken();
    if (!token) return Promise.reject(new Error("No admin token in this tab"));

    return fetch(base + "/content", {
      method: "POST",
      headers: {
        "content-type": "application/json",
        "x-admin-token": token
      },
      body: JSON.stringify({ content: serialise() })
    })
      .then(function (response) {
        return response.json().then(function (data) {
          if (!response.ok) throw new Error(data && data.error ? data.error : "HTTP " + response.status);
          return data;
        });
      })
      .then(function (data) {
        state.savedAt = (data && data.content && data.content.savedAt) || Date.now();
        saveLocal();
        localStorage.setItem(REMOTE_CACHE_KEY, String(Date.now()));
        return data;
      });
  }

  function serialise() {
    return {
      overrides: state.overrides,
      theme: state.theme,
      hidden: state.hidden,
      order: state.order,
      logo: state.logo,
      savedAt: Date.now()
    };
  }

  /* ------------------------------------------------------------------ public api */

  window.SiteContent = {
    state: state,
    fields: allFields,
    fieldByPath: fieldByPath,
    sectionOf: sectionOf,
    defaults: snapshotDefaults,
    apply: apply,
    saveLocal: saveLocal,
    loadLocal: loadLocal,
    resetAll: resetAll,
    pullRemote: pullRemote,
    pushRemote: pushRemote,
    serialise: serialise,
    merge: merge,
    apiBase: apiBase,
    adminToken: adminToken,
    setOverride: function (path, value) {
      if (value === null || value === undefined) delete state.overrides[path];
      else state.overrides[path] = String(value);
    },
    setTheme: function (name, value) {
      if (!value) delete state.theme[name];
      else state.theme[name] = value;
    },
    setHidden: function (id, hidden) {
      state.hidden[id] = !!hidden;
    },
    setLogo: function (logo) {
      state.logo = logo;
    }
  };

  /* ------------------------------------------------------------------ boot */

  loadLocal();
  apply();
  // Shared content is layered on top of the local copy when a key server is configured.
  pullRemote();
})();
