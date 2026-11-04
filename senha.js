(function () {
  const input = document.getElementById("senha");
  const botao = document.getElementById("btn-senha");
  if (!input || !botao) return;

  function definir(mostrar) {
    input.type = mostrar ? "text" : "password";
    botao.textContent = mostrar ? "Ocultar senha" : "Mostrar senha";
  }

  botao.addEventListener("click", () => definir(input.type === "password"));

  // form.reset() (app.js:125 e coordenacao.js:102) dispara este evento
  if (input.form) input.form.addEventListener("reset", () => definir(false));
})();
