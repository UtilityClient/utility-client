// Utility Client — admin panel
//
// Reachable at <site>/#admin. Two separate gates, on purpose:
//   1. the password below, which only decides whether this panel is drawn
//   2. the ADMIN_TOKEN on the key server, which is the thing that actually protects keys
// The token is held in sessionStorage so it does not sit on disk, and disappears when the
// tab closes.

(function () {
  "use strict";

  var ADMIN_PASSWORD = "6241746457";
  var URL_STORE = "utilityclient.apiUrl";
  var TOKEN_STORE = "utilityclient.adminToken";
  var GATE_STORE = "utilityclient.gatePassed";

  var app = document.getElementById("adminApp");
  if (!app) return;

  var gate = document.getElementById("adminGate");
  var dash = document.getElementById("adminDash");
  var passInput = document.getElementById("adminPass");
  var msg = document.getElementById("adminMsg");
  var statusLine = document.getElementById("adminStatus");
  var apiUrl = document.getElementById("apiUrl");
  var apiToken = document.getElementById("apiToken");
  var genOut = document.getElementById("genOut");
  var genKeys = document.getElementById("genKeys");
  var genCountLabel = document.getElementById("genCountLabel");
  var allRows = document.getElementById("allRows");
  var allEmpty = document.getElementById("allEmpty");
  var allCount = document.getElementById("allCount");

  var selectedTier = "DAY";
  var lastIssued = [];
  var knownKeys = [];

  /* ---------------------------------------------------------------- routing */

  function isAdminRoute() {
    return window.location.hash.replace(/^#/, "") === "admin";
  }

  function applyRoute() {
    var admin = isAdminRoute();
    app.hidden = !admin;
    var main = document.querySelector("main");
    var footer = document.querySelector("footer");
    var nav = document.querySelector(".nav");
    if (main) main.hidden = admin;
    if (footer) footer.hidden = admin;
    if (nav) nav.hidden = admin;
    if (!admin) return;

    if (sessionStorage.getItem(GATE_STORE) === "1") {
      showDash();
    } else {
      showGate();
    }
  }

  function showGate() {
    gate.hidden = false;
    dash.hidden = true;
    passInput.value = "";
    passInput.focus();
  }

  // The deployed key server, so the URL does not have to be pasted every session. The
  // field stays editable in case the Worker ever moves.
  var DEFAULT_API_URL = "https://utilityclient-keys.utilityclient.workers.dev";

  function showDash() {
    gate.hidden = true;
    dash.hidden = false;
    apiUrl.value = localStorage.getItem(URL_STORE) || DEFAULT_API_URL;
    apiToken.value = sessionStorage.getItem(TOKEN_STORE) || "";
    if (apiUrl.value && apiToken.value) {
      refresh();
    }
  }

  window.addEventListener("hashchange", applyRoute);
  applyRoute();

  /* ---------------------------------------------------------------- gate */

  function tryUnlock() {
    if (passInput.value.trim() === ADMIN_PASSWORD) {
      sessionStorage.setItem(GATE_STORE, "1");
      showDash();
    } else {
      msg.textContent = "Wrong password.";
      msg.className = "admin-msg is-bad";
      passInput.value = "";
      passInput.focus();
    }
  }

  document.getElementById("adminUnlock").addEventListener("click", tryUnlock);
  passInput.addEventListener("keydown", function (event) {
    if (event.key === "Enter") {
      event.preventDefault();
      tryUnlock();
    }
  });

  document.getElementById("adminLock").addEventListener("click", function () {
    sessionStorage.removeItem(GATE_STORE);
    sessionStorage.removeItem(TOKEN_STORE);
    apiToken.value = "";
    showGate();
  });

  /* ---------------------------------------------------------------- api */

  function base() {
    return (apiUrl.value || "").trim().replace(/\/+$/, "");
  }

  function haveCredentials() {
    if (!base()) {
      setStatus("Add your key server URL first.", true);
      return false;
    }
    if (!apiToken.value.trim()) {
      setStatus("Add your admin token first.", true);
      return false;
    }
    return true;
  }

  function setStatus(text, bad) {
    statusLine.textContent = text;
    statusLine.className = bad ? "fine is-bad" : "fine";
  }

  function call(path, options) {
    var settings = Object.assign({ headers: {} }, options || {});
    settings.headers["x-admin-token"] = apiToken.value.trim();
    return fetch(base() + path, settings).then(function (response) {
      return response.json().then(function (data) {
        if (!response.ok) {
          throw new Error(data && data.error ? data.error : "HTTP " + response.status);
        }
        return data;
      });
    });
  }

  document.getElementById("apiSave").addEventListener("click", function () {
    localStorage.setItem(URL_STORE, base());
    sessionStorage.setItem(TOKEN_STORE, apiToken.value.trim());
    apiToken.value = "";
    setStatus("Saved. Checking the key server...");
    call("/health")
      .then(function () {
        setStatus("Connected to " + base());
        refresh();
      })
      .catch(function (error) {
        setStatus("Could not reach the key server: " + error.message, true);
      });
  });

  /* ---------------------------------------------------------------- generate */

  Array.prototype.forEach.call(document.querySelectorAll(".tier"), function (button) {
    button.addEventListener("click", function () {
      Array.prototype.forEach.call(document.querySelectorAll(".tier"), function (other) {
        other.classList.remove("is-active");
      });
      button.classList.add("is-active");
      selectedTier = button.getAttribute("data-tier");
    });
  });
  // Preselect the 1 Month tier as the most common default.
  var monthButton = document.querySelector('.tier[data-tier="MONTH"]');
  if (monthButton) {
    monthButton.classList.add("is-active");
    selectedTier = "MONTH";
  }

  document.getElementById("genGo").addEventListener("click", function () {
    if (!haveCredentials()) return;
    var count = Math.min(50, Math.max(1, parseInt(document.getElementById("genCount").value, 10) || 1));
    var note = document.getElementById("genNote").value.trim();
    setStatus("Generating...");

    call("/issue", {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify({ tier: selectedTier, count: count, note: note }),
    })
      .then(function (data) {
        lastIssued = data.issued || [];
        genOut.hidden = false;
        genCountLabel.textContent =
          lastIssued.length + (lastIssued.length === 1 ? " key" : " keys") + " - " + tierName(selectedTier);
        renderIssued();
        setStatus("Generated " + lastIssued.length + " key(s).");
        document.getElementById("genNote").value = "";
        refresh();
      })
      .catch(function (error) {
        setStatus("Could not generate: " + error.message, true);
      });
  });

  function renderIssued() {
    genKeys.innerHTML = "";
    lastIssued.forEach(function (record) {
      var row = document.createElement("div");
      row.className = "key-row";
      var code = document.createElement("code");
      code.textContent = record.key;
      var copy = document.createElement("button");
      copy.type = "button";
      copy.className = "btn btn-sm btn-ghost";
      copy.textContent = "Copy";
      copy.addEventListener("click", function () {
        copyText(record.key, copy);
      });
      row.appendChild(code);
      row.appendChild(copy);
      genKeys.appendChild(row);
    });
  }

  document.getElementById("genCopyAll").addEventListener("click", function () {
    copyText(lastIssued.map(function (r) { return r.key; }).join("\n"), this);
  });

  /* ---------------------------------------------------------------- list */

  function refresh() {
    if (!haveCredentials()) return;
    call("/list")
      .then(function (data) {
        knownKeys = data.keys || [];
        renderTable();
        setStatus("Connected to " + base() + " - " + knownKeys.length + " key(s).");
      })
      .catch(function (error) {
        knownKeys = [];
        renderTable();
        setStatus("Could not list keys: " + error.message, true);
      });
  }

  document.getElementById("adminRefresh").addEventListener("click", refresh);

  document.getElementById("adminExport").addEventListener("click", function () {
    if (!knownKeys.length) {
      setStatus("Nothing to export yet.", true);
      return;
    }
    var blob = new Blob([JSON.stringify(knownKeys, null, 2)], { type: "application/json" });
    var link = document.createElement("a");
    link.href = URL.createObjectURL(blob);
    link.download = "utility-client-keys-" + new Date().toISOString().slice(0, 10) + ".json";
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    URL.revokeObjectURL(link.href);
  });

  function renderTable() {
    allRows.innerHTML = "";
    allCount.textContent = String(knownKeys.length);
    allEmpty.hidden = knownKeys.length > 0;

    knownKeys.forEach(function (record) {
      var tr = document.createElement("tr");

      var keyCell = document.createElement("td");
      var code = document.createElement("code");
      code.textContent = record.key;
      var copy = document.createElement("button");
      copy.type = "button";
      copy.className = "link-btn";
      copy.textContent = "copy";
      copy.addEventListener("click", function () {
        copyText(record.key, copy);
      });
      keyCell.appendChild(code);
      keyCell.appendChild(copy);

      tr.appendChild(keyCell);
      tr.appendChild(cell(tierName(record.tier)));

      var stateCell = document.createElement("td");
      var badge = document.createElement("span");
      badge.className = "state state-" + (record.state || "unknown");
      badge.textContent = record.state || "unknown";
      stateCell.appendChild(badge);
      tr.appendChild(stateCell);

      tr.appendChild(cell(record.note || "-"));
      tr.appendChild(cell(record.tier === "PERMANENT" ? "never" : formatDate(record.expiresAt)));
      tr.appendChild(cell(String(record.seen || 0)));

      var actionCell = document.createElement("td");
      var revoke = document.createElement("button");
      revoke.type = "button";
      revoke.className = "link-btn";
      revoke.textContent = record.revoked ? "restore" : "revoke";
      revoke.addEventListener("click", function () {
        call("/revoke", {
          method: "POST",
          headers: { "content-type": "application/json" },
          body: JSON.stringify({ key: record.key, restore: !!record.revoked }),
        })
          .then(refresh)
          .catch(function (error) {
            setStatus("Could not change that key: " + error.message, true);
          });
      });
      actionCell.appendChild(revoke);
      tr.appendChild(actionCell);

      allRows.appendChild(tr);
    });
  }

  function cell(text) {
    var td = document.createElement("td");
    td.textContent = text;
    return td;
  }

  /* ---------------------------------------------------------------- helpers */

  function tierName(tier) {
    switch (tier) {
      case "DAY": return "1 Day";
      case "WEEK": return "1 Week";
      case "MONTH": return "1 Month";
      case "PERMANENT": return "Permanent";
      default: return tier || "-";
    }
  }

  function formatDate(millis) {
    if (!millis) return "-";
    var date = new Date(millis);
    if (isNaN(date.getTime())) return "-";
    return date.toISOString().slice(0, 16).replace("T", " ");
  }

  function copyText(value, button) {
    var original = button.textContent;
    function done(label) {
      button.textContent = label;
      setTimeout(function () {
        button.textContent = original;
      }, 1400);
    }
    if (navigator.clipboard && navigator.clipboard.writeText) {
      navigator.clipboard.writeText(value).then(function () {
        done("Copied");
      }).catch(function () {
        done("Ctrl+C");
      });
      return;
    }
    done("Copy failed");
  }
})();
