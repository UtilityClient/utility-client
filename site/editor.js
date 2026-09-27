// Utility Client — admin site editor
//
// A form for every element the page marks with data-edit, plus the theme colours, section
// visibility and the logo. Inputs are generated from the DOM, so making a new part of the
// page editable only means adding data-edit="some.path" to the HTML. No wiring needed.

(function () {
  "use strict";

  var content = window.SiteContent;

  function SiteEditor() {
    this.content = content;
    this.history = [];
    this.filter = "";
  }

  /* ------------------------------------------------------------------ boot */

  SiteEditor.prototype.init = function () {
    if (!this.content) return;
    this.buildText();
    this.buildTheme();
    this.buildSections();
    this.bindToolbar();
    this.bindTabs();
    this.loadLogoInputs();
    this.refreshJson();
    this.updateStatus();
  };

  /* ------------------------------------------------------------------ text tab */

  SiteEditor.prototype.buildText = function () {
    var host = document.getElementById("edFields");
    if (!host) return;
    var self = this;

    var search = document.createElement("input");
    search.type = "search";
    search.placeholder = "Search the fields you can edit...";
    search.className = "ed-search";
    search.addEventListener("input", function () {
      self.filter = search.value.trim().toLowerCase();
      self.filterFields();
    });
    host.appendChild(search);

    // Group by the section each field lives in, keeping page order.
    var groups = [];
    var index = {};
    this.content.fields().forEach(function (el) {
      var section = self.content.sectionOf(el);
      if (index[section.id] === undefined) {
        index[section.id] = groups.length;
        groups.push({ id: section.id, label: section.label, items: [] });
      }
      groups[index[section.id]].items.push({ path: el.getAttribute("data-edit"), el: el });
    });

    var openByDefault = { hero: 1, features: 1, modules: 1 };
    groups.forEach(function (group) {
      var wrap = document.createElement("details");
      wrap.className = "ed-group";
      wrap.open = !!openByDefault[group.id];

      var summary = document.createElement("summary");
      summary.textContent = group.label + " (" + group.items.length + ")";
      wrap.appendChild(summary);

      group.items.forEach(function (item) {
        wrap.appendChild(self.makeInput(item));
      });
      host.appendChild(wrap);
    });
  };

  SiteEditor.prototype.makeInput = function (item) {
    var self = this;
    var el = item.el;
    var label = el.getAttribute("data-label") || item.path;
    var override = this.content.state.overrides[item.path];
    var shown = override !== undefined ? override : el.textContent.trim();

    // Paragraphs and anything long get a textarea, the rest a single line box.
    var isLong = el.tagName === "P" || shown.length > 70;

    var wrap = document.createElement("label");
    wrap.className = "field ed-field";
    wrap.setAttribute("data-path", item.path);
    wrap.setAttribute("data-search", (label + " " + shown).toLowerCase());

    var caption = document.createElement("span");
    caption.textContent = label;
    if (override !== undefined) {
      var flag = document.createElement("em");
      flag.className = "ed-changed";
      flag.textContent = "edited";
      caption.appendChild(flag);
    }
    wrap.appendChild(caption);

    var input;
    if (isLong) {
      input = document.createElement("textarea");
      input.rows = 3;
    } else {
      input = document.createElement("input");
      input.type = "text";
    }
    input.value = shown;
    input.addEventListener("input", function () {
      self.onEdit(item.path, input.value);
    });
    wrap.appendChild(input);

    var revert = document.createElement("button");
    revert.type = "button";
    revert.className = "link-btn";
    revert.textContent = "revert";
    revert.addEventListener("click", function () {
      self.onEdit(item.path, null);
      input.value = el.textContent.trim();
      var flag = wrap.querySelector(".ed-changed");
      if (flag) flag.remove();
    });
    wrap.appendChild(revert);

    return wrap;
  };

  SiteEditor.prototype.filterFields = function () {
    var host = document.getElementById("edFields");
    if (!host) return;
    var filter = this.filter;
    Array.prototype.forEach.call(host.querySelectorAll(".ed-group"), function (group) {
      var shown = 0;
      Array.prototype.forEach.call(group.querySelectorAll(".ed-field"), function (field) {
        var hay = field.getAttribute("data-search") || "";
        var match = filter === "" || hay.indexOf(filter) !== -1;
        field.hidden = !match;
        if (match) shown++;
      });
      group.hidden = shown === 0;
    });
  };

  SiteEditor.prototype.onEdit = function (path, value) {
    // One undo entry per field, so undo steps back through the fields you touched.
    var top = this.history[this.history.length - 1];
    if (!top || top.path !== path) {
      this.history.push({ path: path, previous: this.content.state.overrides[path] });
      if (this.history.length > 200) this.history.shift();
    }
    this.content.setOverride(path, value);
    this.content.apply();
    this.refreshJson();
    this.updateStatus();
  };

  /* ------------------------------------------------------------------ theme tab */

  var THEME_VARS = [
    { name: "--purple", label: "Accent colour", fallback: "#d000ff" },
    { name: "--bg", label: "Page background", fallback: "#0b0b11" },
    { name: "--bg-alt", label: "Alternate band", fallback: "#0f0f17" },
    { name: "--panel", label: "Card background", fallback: "#16161f" },
    { name: "--text", label: "Body text", fallback: "#f7f3ff" },
    { name: "--muted", label: "Muted text", fallback: "#9a96a8" },
    { name: "--line", label: "Borders", fallback: "#2b2b36" },
    { name: "--green", label: "Success green", fallback: "#42e88a" },
    { name: "--red", label: "Warning red", fallback: "#ff5c72" }
  ];

  SiteEditor.prototype.buildTheme = function () {
    var host = document.getElementById("edTheme");
    if (!host) return;
    var self = this;

    THEME_VARS.forEach(function (item) {
      var current = self.content.state.theme[item.name] || item.fallback;

      var wrap = document.createElement("label");
      wrap.className = "field ed-field";

      var caption = document.createElement("span");
      caption.textContent = item.label;
      wrap.appendChild(caption);

      var row = document.createElement("div");
      row.className = "swatch-row";

      var picker = document.createElement("input");
      picker.type = "color";
      picker.value = toHex(current);

      var text = document.createElement("input");
      text.type = "text";
      text.className = "hex";
      text.value = current;

      picker.addEventListener("input", function () {
        text.value = picker.value;
        self.content.setTheme(item.name, picker.value);
        self.content.apply();
        self.updateStatus();
      });

      text.addEventListener("change", function () {
        var value = text.value.trim();
        if (value.charAt(0) !== "#") value = "#" + value;
        if (!/^#[0-9a-fA-F]{6}$/.test(value) && !/^#[0-9a-fA-F]{3}$/.test(value)) {
          setStatus("Colours look like #d000ff", true);
          text.value = toHex(self.content.state.theme[item.name] || item.fallback);
          return;
        }
        picker.value = toHex(value);
        self.content.setTheme(item.name, toHex(value));
        self.content.apply();
        self.updateStatus();
      });

      var reset = document.createElement("button");
      reset.type = "button";
      reset.className = "link-btn";
      reset.textContent = "reset";
      reset.addEventListener("click", function () {
        self.content.setTheme(item.name, null);
        picker.value = item.fallback;
        text.value = item.fallback;
        self.content.apply();
        self.updateStatus();
      });

      row.appendChild(picker);
      row.appendChild(text);
      row.appendChild(reset);
      wrap.appendChild(row);
      host.appendChild(wrap);
    });
  };

  function toHex(value) {
    var v = String(value == null ? "" : value).trim();
    if (/^#[0-9a-fA-F]{6}$/.test(v)) return v.toLowerCase();
    if (/^#[0-9a-fA-F]{3}$/.test(v)) {
      return "#" + v[1] + v[1] + v[2] + v[2] + v[3] + v[3];
    }
    return "#000000";
  }

  /* ------------------------------------------------------------------ sections tab */

  SiteEditor.prototype.buildSections = function () {
    var host = document.getElementById("edSections");
    if (!host) return;
    var self = this;

    var nodes = document.querySelectorAll("main > section[id], footer");
    Array.prototype.forEach.call(nodes, function (node) {
      var id = node.id || "footer";
      var heading = node.querySelector("h2");
      var label = heading ? heading.textContent.trim() : "Footer";

      var wrap = document.createElement("label");
      wrap.className = "toggle-row";

      var box = document.createElement("input");
      box.type = "checkbox";
      box.checked = !self.content.state.hidden[id];
      box.addEventListener("change", function () {
        self.content.setHidden(id, !box.checked);
        self.content.apply();
        self.updateStatus();
      });

      var text = document.createElement("span");
      text.textContent = label;

      wrap.appendChild(box);
      wrap.appendChild(text);
      host.appendChild(wrap);
    });
  };

  /* ------------------------------------------------------------------ toolbar */

  SiteEditor.prototype.bindToolbar = function () {
    var self = this;
    var C = this.content;

    on("edSaveLocal", "click", function () {
      setStatus(C.saveLocal()
        ? "Saved in this browser only. Visitors still see the old version."
        : "Could not save. Browser storage is full or blocked.", !C.saveLocal());
    });

    on("edPublish", "click", function () {
      setStatus("Publishing...");
      C.saveLocal();
      C.pushRemote()
        .then(function () {
          setStatus("Published. Every visitor sees this within about a minute.");
        })
        .catch(function (error) {
          setStatus("Publish failed: " + error.message, true);
        });
    });

    on("edUndo", "click", function () {
      var last = self.history.pop();
      if (!last) return;
      C.setOverride(last.path, last.previous);
      C.apply();
      self.syncTextInputs();
      self.refreshJson();
      self.updateStatus();
    });

    on("edReset", "click", function () {
      if (!window.confirm("Discard every site edit and restore the original wording and colours?")) return;
      C.resetAll();
    });

    on("edExport", "click", function () {
      download("site-content.json", JSON.stringify(C.serialise(), null, 2));
      setStatus("Downloaded site-content.json. Keep it as a backup.");
    });

    on("edImport", "click", function () {
      var picker = document.createElement("input");
      picker.type = "file";
      picker.accept = "application/json,.json";
      picker.addEventListener("change", function () {
        var file = picker.files && picker.files[0];
        if (!file) return;
        var reader = new FileReader();
        reader.onload = function () {
          try {
            C.merge(JSON.parse(String(reader.result)));
            C.apply();
            C.saveLocal();
            self.rebuildAll();
            setStatus("Imported " + file.name + ". Press Publish to share it.");
          } catch (error) {
            setStatus("That file is not valid content JSON: " + error.message, true);
          }
        };
        reader.readAsText(file);
      });
      picker.click();
    });

    on("edJsonApply", "click", function () {
      var box = document.getElementById("edJson");
      if (!box) return;
      try {
        var parsed = JSON.parse(box.value);
        C.state.overrides = parsed.overrides || {};
        C.state.theme = parsed.theme || {};
        C.state.hidden = parsed.hidden || {};
        C.state.order = parsed.order || {};
        C.state.logo = parsed.logo || null;
        C.apply();
        C.saveLocal();
        self.rebuildAll();
        setStatus("Applied. Press Publish to share it.");
      } catch (error) {
        setStatus("That JSON is not valid: " + error.message, true);
      }
    });

    on("edJsonCopy", "click", function () {
      var box = document.getElementById("edJson");
      if (box) copy(box.value, this);
    });

    on("edThemeReset", "click", function () {
      THEME_VARS.forEach(function (item) {
        C.setTheme(item.name, null);
      });
      C.apply();
      self.buildTheme();
      self.updateStatus();
      setStatus("All colours back to the originals.");
    });

    /* ---- logo ---- */

    on("edLogoText", "input", function () {
      C.setLogo({ text: this.value.trim().toUpperCase() || "U" });
      C.apply();
      self.updateStatus();
    });

    on("edLogoUrl", "change", function () {
      var value = this.value.trim();
      C.setLogo(value ? { data: value, text: "U" } : { text: "U" });
      C.apply();
      self.updateStatus();
    });

    on("edLogoFile", "change", function () {
      var file = this.files && this.files[0];
      if (!file) return;
      if (file.size > 400 * 1024) {
        setStatus("That image is over 400 KB. Shrink it first, or use an image URL.", true);
        return;
      }
      var reader = new FileReader();
      reader.onload = function () {
        C.setLogo({ data: String(reader.result), text: "U" });
        C.apply();
        self.updateStatus();
      };
      reader.readAsDataURL(file);
    });

    on("edLogoClear", "click", function () {
      C.setLogo(null);
      var url = document.getElementById("edLogoUrl");
      if (url) url.value = "";
      C.apply();
      self.updateStatus();
    });

    // Previewing is just leaving the admin view: the edits are already applied to the
    // page, so the hash change reveals them without a reload.
    on("edPreview", "click", function () {
      C.saveLocal();
      window.location.hash = "top";
    });
  };

  SiteEditor.prototype.loadLogoInputs = function () {
    var logo = this.content.state.logo;
    var text = document.getElementById("edLogoText");
    var url = document.getElementById("edLogoUrl");
    if (text) text.value = logo && logo.text ? logo.text : "U";
    // A data URL is far too long to show in a text box, so only fill in real URLs.
    if (url) url.value = logo && logo.data && logo.data.indexOf("data:") !== 0 ? logo.data : "";
  };

  /* ------------------------------------------------------------------ helpers */

  SiteEditor.prototype.rebuildAll = function () {
    var host = document.getElementById("edFields");
    if (host) host.innerHTML = "";
    var theme = document.getElementById("edTheme");
    if (theme) theme.innerHTML = "";
    var sections = document.getElementById("edSections");
    if (sections) sections.innerHTML = "";
    this.history = [];
    this.buildText();
    this.buildTheme();
    this.buildSections();
    this.loadLogoInputs();
    this.refreshJson();
    this.updateStatus();
  };

  // Refresh only the boxes, so typing in one does not lose focus.
  SiteEditor.prototype.syncTextInputs = function () {
    var self = this;
    Array.prototype.forEach.call(document.querySelectorAll("#edFields .ed-field"), function (wrap) {
      var path = wrap.getAttribute("data-path");
      var el = self.content.fieldByPath(path);
      if (!el) return;
      var input = wrap.querySelector("input, textarea");
      if (!input) return;
      var override = self.content.state.overrides[path];
      input.value = override !== undefined ? override : el.textContent.trim();
      var flag = wrap.querySelector(".ed-changed");
      if (override !== undefined && !flag) {
        var added = document.createElement("em");
        added.className = "ed-changed";
        added.textContent = "edited";
        wrap.querySelector("span").appendChild(added);
      } else if (override === undefined && flag) {
        flag.remove();
      }
    });
  };

  SiteEditor.prototype.bindTabs = function () {
    var tabs = document.getElementById("editorTabs");
    if (!tabs) return;
    tabs.addEventListener("click", function (event) {
      var button = event.target.closest(".chip");
      if (!button) return;
      Array.prototype.forEach.call(tabs.querySelectorAll(".chip"), function (other) {
        other.classList.toggle("is-active", other === button);
      });
      var wanted = button.getAttribute("data-tab");
      Array.prototype.forEach.call(document.querySelectorAll("[data-pane]"), function (pane) {
        pane.hidden = pane.getAttribute("data-pane") !== wanted;
      });
      if (wanted === "json") this.refreshJson();
    }.bind(this));
  };

  SiteEditor.prototype.refreshJson = function () {
    var box = document.getElementById("edJson");
    if (box) box.value = JSON.stringify(this.content.serialise(), null, 2);
  };

  SiteEditor.prototype.updateStatus = function () {
    var count = Object.keys(this.content.state.overrides).length;
    var colours = Object.keys(this.content.state.theme).length;
    var undo = document.getElementById("edUndo");
    if (undo) undo.disabled = this.history.length === 0;

    if (count === 0 && colours === 0) {
      setStatus("Nothing changed yet. Edits preview live on this page.");
    } else {
      setStatus(count + " text edit(s) and " + colours + " colour change(s) staged. "
        + "Save here affects only you, Publish affects every visitor.");
    }
  };

  /* ------------------------------------------------------------------ shared bits */

  function setStatus(text, bad) {
    var node = document.getElementById("edStatus");
    if (!node) return;
    node.textContent = text;
    node.className = bad ? "fine is-bad" : "fine";
  }

  function on(id, event, handler) {
    var node = document.getElementById(id);
    if (node) node.addEventListener(event, handler);
  }

  function copy(value, button) {
    var original = button.textContent;
    function done(label) {
      button.textContent = label;
      setTimeout(function () { button.textContent = original; }, 1400);
    }
    if (navigator.clipboard && navigator.clipboard.writeText) {
      navigator.clipboard.writeText(value)
        .then(function () { done("Copied"); })
        .catch(function () { done("Ctrl+C"); });
      return;
    }
    done("Copy failed");
  }

  function download(name, text) {
    var blob = new Blob([text], { type: "application/json" });
    var link = document.createElement("a");
    link.href = URL.createObjectURL(blob);
    link.download = name;
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    URL.revokeObjectURL(link.href);
  }

  window.SiteEditor = SiteEditor;

  document.addEventListener("DOMContentLoaded", function () {
    var editor = new SiteEditor();
    editor.init();
    window.siteEditor = editor;
  });
})();
