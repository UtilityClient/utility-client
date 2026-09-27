// Utility Client — site behaviour
// Sticky nav state, module filtering by category + text, copy-to-clipboard for the mods path.

(function () {
  "use strict";

  /* ---------- Sticky nav border ---------- */

  var nav = document.getElementById("nav");

  function onScroll() {
    if (nav) nav.classList.toggle("scrolled", window.scrollY > 8);
  }

  window.addEventListener("scroll", onScroll, { passive: true });
  onScroll();

  /* ---------- Module filtering ---------- */

  var grid = document.getElementById("moduleGrid");
  var chips = document.getElementById("chips");
  var search = document.getElementById("search");
  var empty = document.getElementById("empty");

  if (grid && chips) {
    var modules = Array.prototype.slice.call(grid.querySelectorAll(".module"));
    var category = "all";

    function apply() {
      var term = (search && search.value ? search.value : "").trim().toLowerCase();
      var shown = 0;

      modules.forEach(function (el) {
        var cat = el.getAttribute("data-cat") || "";
        // The name attribute carries extra keywords (e.g. "auto villager trader").
        var haystack = (el.getAttribute("data-name") || "").toLowerCase() + " " + el.textContent.toLowerCase();
        var matchCat = category === "all" || cat === category;
        var matchTerm = term === "" || haystack.indexOf(term) !== -1;
        var visible = matchCat && matchTerm;

        el.hidden = !visible;
        if (visible) shown++;
      });

      if (empty) empty.hidden = shown !== 0;
    }

    chips.addEventListener("click", function (event) {
      var button = event.target.closest(".chip");
      if (!button) return;
      category = button.getAttribute("data-filter") || "all";
      chips.querySelectorAll(".chip").forEach(function (chip) {
        chip.classList.toggle("is-active", chip === button);
      });
      apply();
    });

    if (search) search.addEventListener("input", apply);
  }

  /* ---------- Copy the mods path ---------- */

  var copyButton = document.getElementById("copyPath");
  var pathText = document.getElementById("pathText");

  if (copyButton && pathText) {
    var originalLabel = copyButton.textContent;
    var resetTimer = null;

    copyButton.addEventListener("click", function () {
      var value = pathText.textContent.trim();
      var done = function (label) {
        copyButton.textContent = label;
        clearTimeout(resetTimer);
        resetTimer = setTimeout(function () {
          copyButton.textContent = originalLabel;
        }, 1600);
      };

      if (navigator.clipboard && navigator.clipboard.writeText) {
        navigator.clipboard.writeText(value).then(function () {
          done("Copied");
        }).catch(function () {
          done("Press Ctrl+C");
        });
        return;
      }

      // Older browsers: select the path so the user can copy it manually.
      var range = document.createRange();
      range.selectNodeContents(pathText);
      var selection = window.getSelection();
      selection.removeAllRanges();
      selection.addRange(range);
      done("Selected");
    });
  }

  /* ---------- Anchor offset for the sticky nav ---------- */

  document.querySelectorAll('a[href^="#"]').forEach(function (link) {
    link.addEventListener("click", function (event) {
      var id = link.getAttribute("href");
      if (!id || id === "#") return;
      var target = document.querySelector(id);
      if (!target) return;

      event.preventDefault();
      var offset = nav ? nav.offsetHeight + 12 : 12;
      var top = target.getBoundingClientRect().top + window.scrollY - offset;
      window.scrollTo({ top: top, behavior: "smooth" });
      history.replaceState(null, "", id);
    });
  });
})();
