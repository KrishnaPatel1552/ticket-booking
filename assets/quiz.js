(function () {
  function gradeRecall(root) {
    var expected = (root.getAttribute("data-expect") || "")
      .split(",")
      .map(function (word) { return word.trim().toLowerCase(); })
      .filter(Boolean);
    var field = root.querySelector("textarea, input");
    var out = root.querySelector("[data-mark]");
    var said = (field.value || "").toLowerCase();
    var missing = expected.filter(function (word) { return said.indexOf(word) === -1; });
    if (missing.length === 0) {
      out.className = "mark right";
      out.textContent = "Those words are there. Cover the page and say the sentence again.";
    } else {
      out.className = "mark wrong";
      out.textContent = "Still missing: " + missing.join(", ") + ". Close the reference and try again.";
    }
  }

  document.querySelectorAll("[data-recall]").forEach(function (root) {
    var button = root.querySelector("button");
    button.addEventListener("click", function () { gradeRecall(root); });
  });

  document.querySelectorAll("[data-quiz]").forEach(function (quiz) {
    var buttons = quiz.querySelectorAll("button");
    var why = quiz.querySelector("[data-why]");
    buttons.forEach(function (button) {
      button.addEventListener("click", function () {
        var correct = button.getAttribute("data-correct") === "true";
        buttons.forEach(function (other) { other.disabled = true; });
        button.classList.add(correct ? "chosen-right" : "chosen-wrong");
        if (!correct) {
          buttons.forEach(function (other) {
            if (other.getAttribute("data-correct") === "true") {
              other.classList.add("chosen-right");
            }
          });
        }
        why.textContent = button.getAttribute("data-explain");
      });
    });
  });
})();
