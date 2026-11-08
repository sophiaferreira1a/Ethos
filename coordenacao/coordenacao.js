const $ = (s, r = document) => r.querySelector(s);
const $$ = (s, r = document) => [...r.querySelectorAll(s)];
const esc = s => String(s ?? "").replace(/[&<>"']/g, c => ({"&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;"}[c]));

// Sem new Date(): "2026-10-07" é uma data, não um instante, e o fuso mudaria o dia.
const fmtData = d => d ? d.slice(0, 10).split("-").reverse().join("/") : "—";
const fmtDataHora = iso => {
  if (!iso) return "—";
  const d = new Date(iso);
  if (isNaN(d)) return iso;
  return d.toLocaleString("pt-BR", {dateStyle: "short", timeStyle: "short", timeZone: "America/Sao_Paulo"});
};
const plural = (n, um, varios) => `${n} ${n === 1 ? um : varios}`;

function mostrarMsg(el, texto, tipo = "erro") {
  el.textContent = texto || "";
  el.hidden = !texto;
  el.className = "msg " + (tipo === "ok" ? "ok-msg" : "erro-msg");
}
function erroCampo(nome, texto, escopo = document) {
  const el = $(`[data-erro="${nome}"]`, escopo);
  if (el) el.textContent = texto || "";
}

let avisoTimer;
function avisar(texto) {
  const el = $("#aviso");
  el.textContent = texto;
  el.hidden = false;
  clearTimeout(avisoTimer);
  avisoTimer = setTimeout(() => { el.hidden = true; }, 3500);
}

function confirmar({titulo, texto, botao, perigo = false, cancelar = "Cancelar", cancelarPrimario = false}) {
  const dlg = $("#dlg-confirmar");
  $("#conf-titulo").textContent = titulo;
  $("#conf-texto").textContent = texto;
  const sim = $("#conf-sim");
  sim.textContent = botao;
  sim.classList.toggle("perigo", perigo);
  const nao = $("#conf-nao");
  nao.textContent = cancelar;
  nao.className = "btn " + (cancelarPrimario ? "azul" : "secundario");
  dlg.returnValue = "";
  dlg.showModal();
  return new Promise(res => dlg.addEventListener("close", () => res(dlg.returnValue === "sim"), {once: true}));
}

const FILTROS_INICIAIS = {turma: "", tipo: "", status: "NAO_LANCADA", aluno: "", dataInicio: "", dataFim: ""};
const ui = {
  usuario: null,
  filtros: {...FILTROS_INICIAIS},
  ordem: "data,desc",
  pagina: 0,
  totalPaginas: 1,
  itens: [],
  atual: null,
  pedidoLista: 0, // descarta respostas antigas quando o filtro muda rápido
  aba: "ocorrencias",
  subaba: "turmas",
  turmas: [],
  alunos: [],
  moverSelecionados: new Set(),
  turmasCarregadas: false
};

function tratarErroGlobal(e) {
  if (e.status === 401) {
    ui.usuario = null;
    $("#dlg-detalhe").open && $("#dlg-detalhe").close();
    telaLogin("Sua sessão expirou. Entre novamente.");
    return true;
  }
  return false;
}

function mostrarTela(id) {
  ["tela-carregando", "tela-login", "tela-app"].forEach(t => { $("#" + t).hidden = t !== id; });
}

function telaLogin(mensagem = "") {
  mostrarTela("tela-login");
  mostrarMsg($("#msg-login"), mensagem);
  $("#email").focus();
}

async function telaApp() {
  mostrarTela("tela-app");
  $("#nome-usuario").textContent = ui.usuario.nome;
  $("#filtros").open = window.matchMedia("(min-width: 900px)").matches;
  await prepararFiltros();
  sincronizarControles();
  await recarregar();
  irParaAba(ui.aba);
}

$("#form-login").addEventListener("submit", async e => {
  e.preventDefault();
  const email = $("#email").value.trim(), senha = $("#senha").value;
  erroCampo("email", ""); erroCampo("senha", ""); mostrarMsg($("#msg-login"), "");
  let ok = true;
  if (!/^\S+@\S+\.\S+$/.test(email)) { erroCampo("email", "Informe um e-mail válido."); ok = false; }
  if (!senha) { erroCampo("senha", "Informe a senha."); ok = false; }
  if (!ok) return;

  const btn = e.submitter;
  btn.disabled = true; btn.textContent = "Entrando…";
  try {
    const u = await Api.login(email, senha);
    if (u.role !== "COORDENACAO") { location.href = "/"; return; }
    ui.usuario = u;
    $("#form-login").reset();
    telaApp();
  } catch (err) {
    if (err.status === 400 && err.campos) {
      erroCampo("email", err.campos.email); erroCampo("senha", err.campos.senha);
    } else {
      mostrarMsg($("#msg-login"), err.status === 401 ? "E-mail ou senha incorretos." : err.message);
    }
  } finally {
    btn.disabled = false; btn.textContent = "Entrar";
  }
});

$("#btn-sair").addEventListener("click", async () => {
  await Api.logout();
  ui.usuario = null;
  ui.filtros = {...FILTROS_INICIAIS};
  telaLogin();
});

$("#ir-professor").addEventListener("click", async e => {
  e.preventDefault();
  try { await Api.logout(); } catch {}
  location.href = "/";
});

/* ===== Exportar planilha (CSV aberto direto no Excel) ===== */
const exportData = d => d ? String(d).slice(0, 10).split("-").reverse().join("/") : "";
const exportDataHora = d => {
  if (!d) return "";
  const x = new Date(d);
  return isNaN(x) ? String(d) : x.toLocaleString("pt-BR", {timeZone: "America/Sao_Paulo"});
};
// Aspas duplicadas e proteção contra fórmulas (=, +, -, @) ao abrir no Excel
const celulaCsv = v => {
  let t = String(v ?? "").replace(/\r?\n/g, " ").trim();
  if (/^[=+\-@]/.test(t)) t = "'" + t;
  return '"' + t.replace(/"/g, '""') + '"';
};

$("#btn-exportar").addEventListener("click", async e => {
  const btn = e.currentTarget, textoOriginal = btn.textContent;
  btn.disabled = true; btn.textContent = "Gerando…";
  mostrarMsg($("#msg-lista"), "");
  try {
    // Busca TODAS as páginas do filtro atual (não só a que está na tela)
    const todos = [];
    let pagina = 0, totalPaginas = 1;
    while (pagina < totalPaginas) {
      const r = await Api.listar(ui.filtros, pagina, ui.ordem, 200);
      todos.push(...r.itens);
      totalPaginas = r.totalPaginas;
      pagina++;
    }
    if (!todos.length) { mostrarMsg($("#msg-lista"), "Não há ocorrências para exportar com os filtros atuais."); return; }

    const cabecalho = ["Data", "Aluno", "Turma", "Tipo", "Professor", "Descrição", "Medidas tomadas",
      "Sugestões à coordenação", "Situação", "Data do lançamento", "Responsável pelo lançamento", "Registrada em"];
    const linhas = todos.map(o => [
      exportData(o.data), o.aluno.nome, o.turma, rotuloTipo(o.tipo), o.professor, o.descricao, o.medidasTomadas,
      o.medidasSugeridas,
      o.status === "LANCADA" ? "Lançada" : "Não lançada",
      exportDataHora(o.dataLancamento), o.responsavelLancamento, exportDataHora(o.criadoEm)
    ]);
    // ";" como separador e BOM UTF-8: o Excel em português abre em colunas e com acentos corretos
    const csv = "\uFEFF" + [cabecalho, ...linhas].map(l => l.map(celulaCsv).join(";")).join("\r\n");
    const url = URL.createObjectURL(new Blob([csv], {type: "text/csv;charset=utf-8"}));
    const a = document.createElement("a");
    a.href = url;
    a.download = `ocorrencias-${new Date().toLocaleDateString("sv-SE")}.csv`;
    document.body.appendChild(a); a.click(); a.remove();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
  } catch (err) {
    if (!tratarErroGlobal(err)) mostrarMsg($("#msg-lista"), "Não foi possível exportar: " + err.message);
  } finally {
    btn.disabled = false; btn.textContent = textoOriginal;
  }
});

let filtrosProntos = false;
async function prepararFiltros() {
  if (filtrosProntos) return;
  $("#f-tipo").insertAdjacentHTML("beforeend", TIPOS.map(t => `<option value="${t.v}">${esc(t.l)}</option>`).join(""));
  await carregarTurmas();
  filtrosProntos = true;
}

async function carregarTurmas() {
  try {
    ui.turmas = await Api.listarTurmas();
  } catch (e) {
    if (tratarErroGlobal(e)) return;
    ui.turmas = [];
  }
  preencherOpcoesTurmas();
}

function sincronizarControles() {
  const f = ui.filtros;
  $("#f-aluno").value = f.aluno;
  $("#f-turma").value = f.turma;
  $("#f-tipo").value = f.tipo;
  $("#f-status").value = f.status;
  $("#f-inicio").value = f.dataInicio;
  $("#f-fim").value = f.dataFim;
  $("#f-ordem").value = ui.ordem;
  $$(".fila-item").forEach(b => b.setAttribute("aria-pressed", String(b.dataset.status === f.status)));
  const ativos = ["turma", "tipo", "dataInicio", "dataFim"].filter(k => f[k]).length;
  $("#filtros-ativos").textContent = ativos;
  $("#filtros-ativos").hidden = !ativos;
}

function aplicarFiltros() {
  const f = ui.filtros;
  erroCampo("periodo", "");
  if (f.dataInicio && f.dataFim && f.dataInicio > f.dataFim) {
    erroCampo("periodo", "A data inicial precisa ser anterior à data final.");
    return;
  }
  ui.pagina = 0;
  sincronizarControles();
  carregarLista();
}

$$(".fila-item").forEach(b => b.addEventListener("click", () => {
  ui.filtros.status = b.dataset.status;
  aplicarFiltros();
}));

let buscaTimer;
$("#f-aluno").addEventListener("input", e => {
  clearTimeout(buscaTimer);
  buscaTimer = setTimeout(() => { ui.filtros.aluno = e.target.value.trim(); aplicarFiltros(); }, 300);
});
[["#f-turma", "turma"], ["#f-tipo", "tipo"], ["#f-status", "status"], ["#f-inicio", "dataInicio"], ["#f-fim", "dataFim"]]
  .forEach(([sel, chave]) => $(sel).addEventListener("change", e => { ui.filtros[chave] = e.target.value; aplicarFiltros(); }));
$("#f-ordem").addEventListener("change", e => { ui.ordem = e.target.value; aplicarFiltros(); });

function limparFiltros() {
  ui.filtros = {...FILTROS_INICIAIS, status: ""};
  aplicarFiltros();
}
$("#btn-limpar").addEventListener("click", limparFiltros);
$("#vazio-limpar").addEventListener("click", limparFiltros);

/* ===== Abas e cadastros ===== */
function irParaAba(nome) {
  ui.aba = nome;
  $$(".aba").forEach(b => {
    const ativo = b.dataset.aba === nome;
    b.setAttribute("aria-selected", String(ativo));
    b.tabIndex = ativo ? 0 : -1;
  });
  $("#aba-ocorrencias").hidden = nome !== "ocorrencias";
  $("#aba-cadastros").hidden = nome !== "cadastros";
  if (nome === "cadastros") {
    carregarCadastros().catch(e => {
      if (!tratarErroGlobal(e)) avisar(e.message);
    });
  } else if (ui.turmasCarregadas) {
    // Cadastros podem ter mudado nomes/turmas: atualiza a lista ao voltar.
    recarregar().catch(e => {
      if (!tratarErroGlobal(e)) avisar(e.message);
    });
  }
}
$$(".aba").forEach(b => b.addEventListener("click", () => irParaAba(b.dataset.aba)));

function irParaSubaba(nome) {
  ui.subaba = nome;
  $$("[data-ir-subaba]").forEach(b => b.setAttribute("aria-selected", String(b.dataset.irSubaba === nome)));
  $$("[data-painel-subaba]").forEach(p => { p.hidden = p.dataset.painelSubaba !== nome; });
  if (nome === "montar") renderMover();
}
$$("[data-ir-subaba]").forEach(b => b.addEventListener("click", () => irParaSubaba(b.dataset.irSubaba)));

function preencherOpcoesTurmas() {
  if (ui.filtros.turma && !ui.turmas.some(t => t.nome === ui.filtros.turma)) ui.filtros.turma = "";
  const opcoes = (sel, rotulo, usarId = false) => {
    const atual = sel.value;
    sel.innerHTML = `<option value="">${esc(rotulo)}</option>` + ui.turmas.map(t =>
      `<option value="${esc(usarId ? t.id : t.nome)}">${esc(t.nome)}</option>`).join("");
    sel.value = [...sel.options].some(o => o.value === atual) ? atual : "";
  };
  opcoes($("#f-turma"), "Todas");
  opcoes($("#cad-filtro-turma"), "Todas as turmas", true);
  opcoes($("#mover-origem"), "Todas as turmas", true);
  opcoes($("#mover-destino"), "Selecione a turma", true);
}

async function carregarCadastros(forcar = false) {
  if (ui.turmasCarregadas && !forcar) {
    renderTurmas(); renderAlunosCadastro(); renderMover();
    return;
  }
  try {
    const [turmas, alunos] = await Promise.all([Api.listarTurmas(), Api.listarAlunos()]);
    ui.turmas = turmas;
    ui.alunos = alunos;
    ui.moverSelecionados.clear();
    ui.turmasCarregadas = true;
    preencherOpcoesTurmas();
    mostrarMsg($("#msg-turmas"), "");
    mostrarMsg($("#msg-alunos"), "");
    mostrarMsg($("#msg-mover"), "");
    renderTurmas(); renderAlunosCadastro(); renderMover();
  } catch (e) {
    if (tratarErroGlobal(e)) return;
    mostrarMsg($("#msg-turmas"), e.message);
    mostrarMsg($("#msg-alunos"), e.message);
  }
}

function renderTurmas() {
  $("#linhas-turmas").innerHTML = ui.turmas.map(turma => {
    const quantidade = ui.alunos.filter(a => a.turmaId === turma.id).length;
    return `<tr data-id="${esc(turma.id)}">
      <td data-rotulo="Turma"><div class="cel"><strong>${esc(turma.nome)}</strong></div></td>
      <td data-rotulo="Alunos ativos"><div class="cel">${quantidade}</div></td>
      <td class="col-acoes">
        <button type="button" class="btn secundario pequeno" data-acao="editar-turma"
          aria-label="Renomear turma ${esc(turma.nome)}">Renomear</button>
        <button type="button" class="btn texto pequeno perigo-txt" data-acao="excluir-turma"
          aria-label="Excluir turma ${esc(turma.nome)}">Excluir</button>
      </td>
    </tr>`;
  }).join("");
  $("#vazio-turmas").hidden = ui.turmas.length > 0;
  $("#tabela-turmas").closest(".tabela-wrap").hidden = !ui.turmas.length;
}

function renderAlunosCadastro() {
  const busca = semAcento($("#cad-busca-aluno").value.trim());
  const turmaId = $("#cad-filtro-turma").value;
  const alunos = ui.alunos
    .filter(a => (!turmaId || a.turmaId === turmaId) && (!busca || semAcento(a.nome).includes(busca)))
    .sort((a, b) => a.nome.localeCompare(b.nome, "pt-BR"));
  $("#linhas-alunos").innerHTML = alunos.map(aluno => `<tr data-id="${esc(aluno.id)}">
      <td data-rotulo="Aluno"><div class="cel">${esc(aluno.nome)}</div></td>
      <td data-rotulo="Turma"><div class="cel">${esc(aluno.turma)}</div></td>
      <td class="col-acoes">
        <button type="button" class="btn secundario pequeno" data-acao="editar-aluno"
          aria-label="Editar ${esc(aluno.nome)}">Editar</button>
        <button type="button" class="btn texto pequeno perigo-txt" data-acao="excluir-aluno"
          aria-label="Excluir ${esc(aluno.nome)}">Excluir</button>
      </td>
    </tr>`).join("");
  $("#vazio-alunos").hidden = alunos.length > 0;
  $("#tabela-alunos").closest(".tabela-wrap").hidden = !alunos.length;
}

function alunosParaMover() {
  const origem = $("#mover-origem").value;
  return ui.alunos
    .filter(a => !origem || a.turmaId === origem)
    .sort((a, b) => a.nome.localeCompare(b.nome, "pt-BR"));
}

function atualizarContagemMover() {
  const total = ui.moverSelecionados.size;
  $("#mover-cont").textContent = total === 0 ? "Nenhum aluno selecionado"
    : total === 1 ? "1 aluno selecionado" : `${total} alunos selecionados`;
  $("#btn-mover").disabled = total === 0 || !$("#mover-destino").value;
}

function renderMover() {
  const alunos = alunosParaMover();
  const selecionados = ui.moverSelecionados;
  $("#linhas-mover").innerHTML = alunos.map(aluno => `<tr data-id="${esc(aluno.id)}">
      <td class="col-check"><input type="checkbox" class="mover-check" data-aluno-id="${esc(aluno.id)}"
        ${selecionados.has(aluno.id) ? "checked" : ""} aria-label="Selecionar ${esc(aluno.nome)}"></td>
      <td data-rotulo="Aluno"><div class="cel">${esc(aluno.nome)}</div></td>
      <td data-rotulo="Turma atual"><div class="cel">${esc(aluno.turma)}</div></td>
    </tr>`).join("");
  $("#vazio-mover").hidden = alunos.length > 0;
  $("#tabela-mover").closest(".tabela-wrap").hidden = !alunos.length;
  const ids = alunos.map(a => a.id);
  const todos = ids.length > 0 && ids.every(id => selecionados.has(id));
  $("#mover-todos").checked = todos;
  $("#mover-todos").indeterminate = !todos && ids.some(id => selecionados.has(id));
  atualizarContagemMover();
}

$("#cad-busca-aluno").addEventListener("input", renderAlunosCadastro);
$("#cad-filtro-turma").addEventListener("change", renderAlunosCadastro);
$("#mover-origem").addEventListener("change", () => {
  ui.moverSelecionados.clear();
  mostrarMsg($("#msg-mover"), "");
  renderMover();
});
$("#mover-destino").addEventListener("change", atualizarContagemMover);
$("#linhas-mover").addEventListener("change", e => {
  const cb = e.target.closest(".mover-check");
  if (!cb) return;
  if (cb.checked) ui.moverSelecionados.add(cb.dataset.alunoId);
  else ui.moverSelecionados.delete(cb.dataset.alunoId);
  renderMover();
});
$("#mover-todos").addEventListener("change", e => {
  const ids = alunosParaMover().map(a => a.id);
  if (e.target.checked) ids.forEach(id => ui.moverSelecionados.add(id));
  else ids.forEach(id => ui.moverSelecionados.delete(id));
  renderMover();
});

$("#btn-nova-turma").addEventListener("click", () => abrirCadastro("turma"));
$("#btn-novo-aluno").addEventListener("click", () => abrirCadastro("aluno"));

$("#linhas-turmas").addEventListener("click", e => {
  const btn = e.target.closest("[data-acao]");
  if (!btn) return;
  const turma = ui.turmas.find(t => t.id === btn.closest("tr").dataset.id);
  if (!turma) return;
  if (btn.dataset.acao === "editar-turma") abrirCadastro("turma", turma);
  if (btn.dataset.acao === "excluir-turma") excluirTurma(turma);
});

$("#linhas-alunos").addEventListener("click", e => {
  const btn = e.target.closest("[data-acao]");
  if (!btn) return;
  const aluno = ui.alunos.find(a => a.id === btn.closest("tr").dataset.id);
  if (!aluno) return;
  if (btn.dataset.acao === "editar-aluno") abrirCadastro("aluno", aluno);
  if (btn.dataset.acao === "excluir-aluno") excluirAluno(aluno);
});

function abrirCadastro(tipo, item = null) {
  ui.cadastro = {tipo, item};
  const editando = Boolean(item);
  $("#cad-titulo").textContent = `${editando ? "Editar" : "Novo"} ${tipo === "turma" ? "turma" : "aluno"}`;
  $("#cad-sub").textContent = tipo === "turma"
    ? "Turmas são reutilizadas todo ano. Use um nome curto, como 6A ou 1EM."
    : "O aluno sempre pertence a uma turma.";
  const tamanho = tipo === "turma" ? 20 : 150;
  let html = `<label for="cad-nome">${tipo === "turma" ? "Nome da turma" : "Nome do aluno"}</label>
    <input id="cad-nome" maxlength="${tamanho}" autocomplete="off" value="${esc(item?.nome || "")}">
    <small class="erro" data-erro="nome"></small>`;
  if (tipo === "aluno") {
    html += `<label for="cad-turma">Turma</label>
      <select id="cad-turma"><option value="">Selecione a turma</option>
        ${ui.turmas.map(t => `<option value="${esc(t.id)}">${esc(t.nome)}</option>`).join("")}
      </select><small class="erro" data-erro="turmaId"></small>`;
  }
  $("#cad-campos").innerHTML = html;
  if (tipo === "aluno") $("#cad-turma").value = item?.turmaId || "";
  mostrarMsg($("#msg-cadastro"), "");
  $("#dlg-cadastro").showModal();
  $("#cad-nome").focus();
}

function fecharCadastro() {
  $("#dlg-cadastro").close();
  ui.cadastro = null;
}

$("#cad-cancelar").addEventListener("click", fecharCadastro);
$("#dlg-cadastro").addEventListener("click", e => {
  if (e.target === $("#dlg-cadastro") || e.target.closest("[data-fechar-cadastro]")) fecharCadastro();
});
$("#dlg-cadastro").addEventListener("close", () => { ui.cadastro = null; });

$("#form-cadastro").addEventListener("submit", async e => {
  e.preventDefault();
  const cadastro = ui.cadastro;
  if (!cadastro) return;
  const nome = $("#cad-nome").value.trim();
  const turmaId = cadastro.tipo === "aluno" ? $("#cad-turma").value : null;
  $$(".erro", $("#form-cadastro")).forEach(el => { el.textContent = ""; });
  let ok = true;
  if (!nome) { erroCampo("nome", "Informe o nome.", $("#form-cadastro")); ok = false; }
  if (cadastro.tipo === "aluno" && !turmaId) {
    erroCampo("turmaId", "Escolha a turma.", $("#form-cadastro")); ok = false;
  }
  if (!ok) return;

  const btn = $("#cad-salvar"), texto = btn.textContent;
  btn.disabled = true; btn.textContent = "Salvando…";
  try {
    if (cadastro.tipo === "turma") {
      if (cadastro.item) await Api.atualizarTurma(cadastro.item.id, nome);
      else await Api.criarTurma(nome);
    } else if (cadastro.item) {
      await Api.atualizarAluno(cadastro.item.id, nome, turmaId);
    } else {
      await Api.criarAluno(nome, turmaId);
    }
    fecharCadastro();
    avisar(cadastro.item ? "Cadastro atualizado." : "Cadastro criado.");
    await carregarCadastros(true);
    sincronizarControles();
  } catch (err) {
    if (tratarErroGlobal(err)) return;
    if (err.status === 400 && err.campos) {
      Object.entries(err.campos).forEach(([campo, msg]) => erroCampo(campo, msg, $("#form-cadastro")));
    }
    mostrarMsg($("#msg-cadastro"), err.message);
  } finally {
    btn.disabled = false; btn.textContent = texto;
  }
});

async function excluirTurma(turma) {
  const quantidade = ui.alunos.filter(a => a.turmaId === turma.id).length;
  const ok = await confirmar({
    titulo: "Excluir turma?",
    texto: `A turma ${turma.nome} será removida do cadastro. A exclusão é bloqueada se houver alunos ou ocorrências vinculadas.${quantidade ? ` No momento, há ${plural(quantidade, "aluno ativo", "alunos ativos")} nesta turma.` : ""}`,
    botao: "Excluir",
    perigo: true
  });
  if (!ok) return;
  try {
    await Api.excluirTurma(turma.id);
    avisar("Turma excluída.");
    await carregarCadastros(true);
    sincronizarControles();
  } catch (e) {
    if (tratarErroGlobal(e)) return;
    avisar(e.message);
  }
}

async function excluirAluno(aluno) {
  const ok = await confirmar({
    titulo: "Excluir aluno?",
    texto: `${aluno.nome} deixará de aparecer nas listas e não poderá ser usado em novas ocorrências. As ocorrências e o histórico já registrados serão preservados.`,
    botao: "Excluir",
    perigo: true,
    cancelar: "Voltar",
    cancelarPrimario: true
  });
  if (!ok) return;
  try {
    await Api.excluirAluno(aluno.id);
    ui.moverSelecionados.delete(aluno.id);
    avisar("Aluno excluído. Ocorrências e histórico foram preservados.");
    await carregarCadastros(true);
  } catch (e) {
    if (tratarErroGlobal(e)) return;
    avisar(e.message);
  }
}

$("#btn-mover").addEventListener("click", async () => {
  const destino = $("#mover-destino").value;
  const turma = ui.turmas.find(t => t.id === destino);
  const total = ui.moverSelecionados.size;
  if (!destino || !total) return;
  const ok = await confirmar({
    titulo: "Mover alunos?",
    texto: `Mover ${plural(total, "aluno selecionado", "alunos selecionados")} para a turma ${turma?.nome || "de destino"}? Ocorrências antigas mantêm a turma registrada na época.`,
    botao: "Mover alunos"
  });
  if (!ok) return;
  const btn = $("#btn-mover"), texto = btn.textContent;
  btn.disabled = true; btn.textContent = "Movendo…";
  try {
    await Api.moverAlunos([...ui.moverSelecionados], destino);
    ui.moverSelecionados.clear();
    avisar(`${plural(total, "aluno movido", "alunos movidos")} para ${turma?.nome || "a turma de destino"}.`);
    await carregarCadastros(true);
    mostrarMsg($("#msg-mover"), "");
    renderMover();
  } catch (e) {
    if (tratarErroGlobal(e)) return;
    mostrarMsg($("#msg-mover"), e.message);
  } finally {
    btn.disabled = false; btn.textContent = texto;
    atualizarContagemMover();
  }
});

async function carregarIndicadores() {
  try {
    const d = await Api.indicadores();
    $("#ind-nao").textContent = d.naoLancadas;
    $("#ind-lancadas").textContent = d.lancadas;
    $("#ind-total").textContent = d.total;
    $(".fila-item.pendente").classList.toggle("zerada", d.naoLancadas === 0);
  } catch (e) {
    if (!tratarErroGlobal(e)) ["#ind-nao", "#ind-lancadas", "#ind-total"].forEach(s => { $(s).textContent = "–"; });
  }
}

function recarregar() {
  return Promise.all([carregarIndicadores(), carregarLista()]);
}

async function carregarLista() {
  const pedido = ++ui.pedidoLista;
  $("#tabela").setAttribute("aria-busy", "true");
  mostrarMsg($("#msg-lista"), "");
  try {
    const r = await Api.listar(ui.filtros, ui.pagina, ui.ordem);
    if (pedido !== ui.pedidoLista) return;
    // Página ficou vazia (último item lançado ou excluído): volta uma.
    if (!r.itens.length && ui.pagina > 0) { ui.pagina--; return carregarLista(); }
    Object.assign(ui, {itens: r.itens, totalPaginas: r.totalPaginas});
    renderLista(r.total);
  } catch (e) {
    if (pedido !== ui.pedidoLista || tratarErroGlobal(e)) return;
    ui.itens = [];
    $("#linhas").innerHTML = "";
    $("#resultado").textContent = "";
    $("#vazio").hidden = true;
    $("#paginacao").hidden = true;
    mostrarMsg($("#msg-lista"), e.message);
  } finally {
    if (pedido === ui.pedidoLista) $("#tabela").removeAttribute("aria-busy");
  }
}

function descreverResultado(total) {
  const s = ui.filtros.status;
  const nome = s === "NAO_LANCADA" ? ["ocorrência não lançada", "ocorrências não lançadas"]
    : s === "LANCADA" ? ["ocorrência lançada", "ocorrências lançadas"]
    : ["ocorrência", "ocorrências"];
  return plural(total, ...nome);
}

function temFiltroAlemDoStatus() {
  const f = ui.filtros;
  return Boolean(f.turma || f.tipo || f.aluno || f.dataInicio || f.dataFim);
}

function renderLista(total) {
  $("#resultado").textContent = descreverResultado(total);
  $("#linhas").innerHTML = ui.itens.map(linhaHTML).join("");

  const vazio = !ui.itens.length;
  $("#vazio").hidden = !vazio;
  $(".tabela-wrap").hidden = vazio;
  if (vazio) {
    const fila = ui.filtros.status === "NAO_LANCADA" && !temFiltroAlemDoStatus();
    $("#vazio-txt").textContent = fila
      ? "Nenhuma ocorrência pendente. Tudo o que foi registrado já está no sistema oficial."
      : "Nenhuma ocorrência encontrada com esses filtros.";
    $("#vazio-limpar").hidden = fila;
  }

  $("#paginacao").hidden = ui.totalPaginas <= 1;
  $("#pag-info").textContent = `Página ${ui.pagina + 1} de ${ui.totalPaginas}`;
  $("#pag-ant").disabled = ui.pagina === 0;
  $("#pag-prox").disabled = ui.pagina >= ui.totalPaginas - 1;
}

function situacaoHTML(o) {
  return o.status === "LANCADA"
    ? `<span class="situacao lancada">Lançada</span>`
    : `<span class="situacao pendente">Não lançada</span>`;
}

function linhaHTML(o) {
  const pendente = o.status !== "LANCADA";
  return `<tr class="${pendente ? "pendente" : ""}" data-id="${esc(o.id)}">
    <td data-rotulo="Data"><div class="cel">${fmtData(o.data)}</div></td>
    <td data-rotulo="Aluno"><div class="cel">
      <button type="button" class="link-aluno" data-acao="abrir">${esc(o.aluno.nome)}</button>
      <small>${esc(o.turma)}</small>
    </div></td>
    <td data-rotulo="Tipo"><div class="cel">${esc(rotuloTipo(o.tipo))}</div></td>
    <td data-rotulo="Professor"><div class="cel">${esc(o.professor)}</div></td>
    <td data-rotulo="Situação"><div class="cel">${situacaoHTML(o)}${o.status === "LANCADA" && o.dataLancamento ? `<small class="quando">${fmtDataHora(o.dataLancamento)}</small>` : ""}</div></td>
    <td class="col-acoes">
      ${pendente ? `<button type="button" class="btn primario pequeno" data-acao="lancar">Marcar como lançada</button>` : ""}
      <button type="button" class="btn texto pequeno" data-acao="abrir" aria-label="Ver detalhes da ocorrência de ${esc(o.aluno.nome)}">Detalhes</button>
    </td>
  </tr>`;
}

$("#linhas").addEventListener("click", e => {
  const btn = e.target.closest("[data-acao]");
  if (!btn) return;
  const id = btn.closest("tr").dataset.id;
  const o = ui.itens.find(x => x.id === id);
  if (btn.dataset.acao === "abrir") abrirDetalhes(id);
  if (btn.dataset.acao === "lancar" && o) lancar(o, btn);
});

$("#pag-ant").addEventListener("click", () => { ui.pagina--; carregarLista(); $(".painel-coord").scrollIntoView(); });
$("#pag-prox").addEventListener("click", () => { ui.pagina++; carregarLista(); $(".painel-coord").scrollIntoView(); });

async function lancar(o, btn) {
  const ok = await confirmar({
    titulo: "Marcar como lançada?",
    texto: `Confirme que a ocorrência de ${o.aluno.nome} (${o.turma}, ${fmtData(o.data)}) já foi registrada no sistema oficial do colégio. Seu nome e o horário ficam gravados e a ação não pode ser desfeita aqui.`,
    botao: "Confirmar lançamento"
  });
  if (!ok) return;
  if (btn) btn.disabled = true;
  try {
    const atualizada = await Api.marcarLancada(o.id);
    avisar(`Ocorrência de ${o.aluno.nome} marcada como lançada.`);
    if (ui.atual?.id === o.id) { ui.atual = atualizada; renderDetalhes(); }
    await recarregar();
  } catch (e) {
    if (tratarErroGlobal(e)) return;
    avisar(e.message);
    if (e.status === 409 || e.status === 404) recarregar();
    if (btn) btn.disabled = false;
  }
}

async function salvarEdicao(form) {
  const dados = {
    tipo: form.tipo.value,
    data: form.data.value,
    descricao: form.descricao.value.trim(),
    medidasTomadas: form.medidas.value.trim(),
    medidasSugeridas: form.medidasSugeridas.value.trim()
  };
  $$(".erro", form).forEach(el => { el.textContent = ""; });
  const hoje = new Date().toLocaleDateString("sv-SE");
  let ok = true;
  if (!dados.tipo) { erroCampo("tipo", "Escolha o tipo.", form); ok = false; }
  if (!dados.data || dados.data > hoje) { erroCampo("data", "Informe uma data válida (hoje ou anterior).", form); ok = false; }
  if (dados.descricao.length < 10) { erroCampo("descricao", "Descreva o ocorrido com pelo menos 10 caracteres.", form); ok = false; }
  if (!dados.medidasTomadas) { erroCampo("medidasTomadas", "Informe ao menos uma medida tomada.", form); ok = false; }
  if (!ok) return;

  const btn = $("[type=submit]", form);
  btn.disabled = true; btn.textContent = "Salvando…";
  try {
    ui.atual = await Api.atualizar(ui.atual, dados);
    avisar("Alterações salvas.");
    renderDetalhes();
    carregarLista();
  } catch (e) {
    if (tratarErroGlobal(e)) return;
    if (e.status === 400 && e.campos) {
      Object.entries(e.campos).forEach(([campo, msg]) => erroCampo(campo === "medidas" ? "medidasTomadas" : campo, msg, form));
    }
    mostrarMsg($("#msg-edicao"), e.message);
    btn.disabled = false; btn.textContent = "Salvar alterações";
  }
}

async function excluir(o) {
  const ok = await confirmar({
    titulo: "Excluir ocorrência?",
    texto: `A ocorrência de ${o.aluno.nome} (${o.turma}, ${fmtData(o.data)}) sai da lista, mas o histórico fica guardado. Use só para registros feitos por engano.`,
    botao: "Excluir ocorrência",
    perigo: true
  });
  if (!ok) return;
  try {
    await Api.excluir(o.id);
    $("#dlg-detalhe").close();
    avisar("Ocorrência excluída.");
    recarregar();
  } catch (e) {
    if (tratarErroGlobal(e)) return;
    if (e.status === 409) {
      avisar("Esta ocorrência não pode mais ser excluída. A lista foi atualizada.");
      recarregar();
    } else {
      avisar(e.message);
    }
  }
}

const dlg = $("#dlg-detalhe");
dlg.addEventListener("click", e => {
  if (e.target === dlg || e.target.closest("[data-fechar]")) dlg.close();
});
dlg.addEventListener("close", () => { ui.atual = null; });

async function abrirDetalhes(id) {
  const resumo = ui.itens.find(x => x.id === id);
  ui.atual = resumo || null;
  $("#det-titulo").textContent = resumo ? resumo.aluno.nome : "Ocorrência";
  $("#det-sub").textContent = resumo ? `${resumo.turma} · ${fmtData(resumo.data)}` : "";
  $("#det-corpo").innerHTML = `<p class="sub">Carregando detalhes…</p>`;
  if (!dlg.open) dlg.showModal();
  try {
    ui.atual = await Api.obter(id);
    renderDetalhes();
  } catch (e) {
    if (tratarErroGlobal(e)) return;
    $("#det-corpo").innerHTML = `<div class="msg erro-msg">${esc(e.message)}</div>`;
    if (e.status === 404) recarregar();
  }
}

function podeAlterar(o) {
  return o.status !== "LANCADA" || CONFIG.PODE_ALTERAR_LANCADA;
}

function renderDetalhes() {
  const o = ui.atual;
  if (!o) return;
  $("#det-titulo").textContent = o.aluno.nome;
  $("#det-sub").textContent = `${o.turma} · ${fmtData(o.data)}`;

  const blocoSituacao = o.status === "LANCADA"
    ? `<div class="bloco-situacao lancada">
         ${situacaoHTML(o)}
         <p>Lançada no sistema oficial em <strong>${fmtDataHora(o.dataLancamento)}</strong>${o.responsavelLancamento ? ` por <strong>${esc(o.responsavelLancamento)}</strong>` : ""}.</p>
       </div>`
    : `<div class="bloco-situacao pendente">
         ${situacaoHTML(o)}
         <p>Ainda não foi registrada no sistema oficial do colégio.</p>
         <button type="button" class="btn primario" id="det-lancar">Marcar como lançada</button>
       </div>`;

  const campos = [
    ["Tipo", rotuloTipo(o.tipo)],
    ["Professor", o.professor],
    ["Data da ocorrência", fmtData(o.data)],
    ["Descrição", o.descricao],
    ["Medidas tomadas", o.medidasTomadas.split(/;\s*/).filter(Boolean).join("\n") || "—"],
    ["Sugestões à coordenação", (o.medidasSugeridas || "").split(/;\s*/).filter(Boolean).join("\n") || "—"],
    ["Registrada em", fmtDataHora(o.criadoEm)]
  ];

  const alteravel = podeAlterar(o);
  $("#det-corpo").innerHTML = `
    ${blocoSituacao}
    <dl class="resumo">${campos.map(([k, v]) => `<div><dt>${k}</dt><dd>${esc(v)}</dd></div>`).join("")}</dl>

    <section class="historico" aria-labelledby="hist-titulo">
      <h3 id="hist-titulo">Histórico</h3>
      <ol id="hist-lista" class="hist-lista"><li class="sub">Carregando…</li></ol>
    </section>

    <div class="det-acoes">
      ${alteravel
        ? `<button type="button" class="btn secundario" id="det-editar">Corrigir dados</button>
           <button type="button" class="btn texto perigo-txt" id="det-excluir">Excluir ocorrência</button>`
        : `<p class="sub">Ocorrências já lançadas não podem ser corrigidas nem excluídas aqui. Corrija primeiro no sistema oficial e fale com o suporte da plataforma.</p>`}
    </div>`;

  $("#det-lancar")?.addEventListener("click", e => lancar(o, e.currentTarget));
  $("#det-editar")?.addEventListener("click", renderEdicao);
  $("#det-excluir")?.addEventListener("click", () => excluir(o));
  carregarHistorico(o.id);
}

async function carregarHistorico(id) {
  const ol = $("#hist-lista");
  try {
    const eventos = await Api.historico(id);
    if (ui.atual?.id !== id || !ol.isConnected) return;
    if (eventos === null) { ol.innerHTML = `<li class="sub">O histórico ainda não está disponível na API.</li>`; return; }
    if (!eventos.length) { ol.innerHTML = `<li class="sub">Nenhum evento registrado.</li>`; return; }
    ol.innerHTML = eventos
      .sort((a, b) => String(a.dataHora).localeCompare(String(b.dataHora)))
      .map(h => `<li class="hist-item ${h.acao === "OCORRENCIA_LANCADA" ? "marco" : ""}">
          <strong>${esc(h.rotulo)}</strong>
          <span class="sub">${esc(h.usuario)} · ${fmtDataHora(h.dataHora)}</span>
          ${h.detalhes ? `<p>${esc(h.detalhes)}</p>` : ""}
        </li>`).join("");
  } catch (e) {
    if (tratarErroGlobal(e)) return;
    if (ol.isConnected) ol.innerHTML = `<li class="sub">Não foi possível carregar o histórico.</li>`;
  }
}

function renderEdicao() {
  const o = ui.atual;
  const hoje = new Date().toLocaleDateString("sv-SE");
  $("#det-corpo").innerHTML = `
    <form id="form-edicao" class="form-edicao" novalidate>
      <p class="sub">Aluno e turma não podem ser trocados. Se o aluno estiver errado, exclua e peça ao professor para registrar novamente.</p>

      <label for="ed-tipo">Tipo</label>
      <select id="ed-tipo" name="tipo">
        ${TIPOS.map(t => `<option value="${t.v}" ${t.v === o.tipo ? "selected" : ""}>${esc(t.l)}</option>`).join("")}
        ${TIPOS.some(t => t.v === o.tipo) ? "" : `<option value="${esc(o.tipo)}" selected>${esc(rotuloTipo(o.tipo))}</option>`}
      </select>
      <small class="erro" data-erro="tipo"></small>

      <label for="ed-data">Data da ocorrência</label>
      <input id="ed-data" name="data" type="date" max="${hoje}" value="${esc(o.data)}">
      <small class="erro" data-erro="data"></small>

      <label for="ed-descricao">Descrição</label>
      <textarea id="ed-descricao" name="descricao" rows="6" maxlength="1000">${esc(o.descricao)}</textarea>
      <small class="erro" data-erro="descricao"></small>

      <label for="ed-medidas">Medidas tomadas</label>
      <textarea id="ed-medidas" name="medidas" rows="3" maxlength="500">${esc(o.medidasTomadas)}</textarea>
      <small class="erro" data-erro="medidasTomadas"></small>

      <label for="ed-sugestoes">Sugestões à coordenação</label>
      <textarea id="ed-sugestoes" name="medidasSugeridas" rows="3" maxlength="2000">${esc(o.medidasSugeridas || "")}</textarea>
      <small class="erro" data-erro="medidasSugeridas"></small>

      <div id="msg-edicao" class="msg" role="alert" hidden></div>
      <div class="det-acoes">
        <button type="button" class="btn secundario" id="ed-cancelar">Cancelar</button>
        <button type="submit" class="btn primario">Salvar alterações</button>
      </div>
    </form>`;
  const form = $("#form-edicao");
  form.addEventListener("submit", e => { e.preventDefault(); salvarEdicao(form); });
  $("#ed-cancelar").addEventListener("click", renderDetalhes);
  $("#ed-tipo").focus();
}

(async function iniciar() {
  try {
    const u = await Api.me();
    if (u.role !== "COORDENACAO") { location.href = "/"; return; }
    ui.usuario = u;
    telaApp();
  } catch (e) {
    telaLogin(e.status === 401 ? "" : e.message);
  }
})();
