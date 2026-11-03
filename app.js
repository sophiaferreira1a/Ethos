/* ===== Configuração ===== */
const API_URL = "https://ethos-api-rpea.onrender.com"; // produção: URL do backend no Render
const USAR_MOCK_SE_FALHAR = false;       // backend fora do ar NA ABERTURA da página: usa dados de exemplo (desligar no deploy)
let modoPrevia = false;                  // decidido uma vez, no início; não muda no meio do uso

// v = valor enviado à API (combine os nomes dos enums com o Mateus); l = texto exibido
const TIPOS = [
  {v:"DESRESPEITO", l:"Desrespeito ao professor ou funcionário"},
  {v:"INDISCIPLINA", l:"Indisciplina / bagunça em sala"},
  {v:"CONFLITO", l:"Briga ou agressão entre alunos"},
  {v:"CELULAR", l:"Uso indevido de celular ou eletrônicos"},
  {v:"ATIVIDADE", l:"Recusa em fazer atividade / sem material"},
  {v:"SAIDA_SEM_AUTORIZACAO", l:"Saída de sala sem autorização"},
  {v:"PATRIMONIO", l:"Dano ao patrimônio da escola"},
  {v:"OUTRO", l:"Outro comportamento inadequado"}
];
const MEDIDAS = ["Conversa com o aluno", "Advertência verbal", "Advertência escrita (responsáveis assinam ciência)", "Suspensão", "Encaminhado à coordenação", "Responsável contatado", "Troca de lugar", "Atividade de reflexão"];
const TURMAS_MOCK = ["6A", "6B", "7A", "7B", "8A", "8B", "9A", "9B", "1EM", "2EM", "3EM"];
// Alunos FICTÍCIOS, só para desenvolvimento (LGPD: não usar dados reais sem autorização da escola)
const NOMES_MOCK = {
  "6A": ["Ana Clara Souza", "Bruno Henrique Lima", "Camila Rocha", "Davi Nogueira"],
  "6B": ["Carla Mendes", "Enzo Gabriel Costa", "Helena Barros", "Igor Teixeira"],
  "7A": ["Diego Alves", "Eduarda Pires", "Julia Fernandes", "Lucas Martins"],
  "7B": ["Mariana Duarte", "Nicolas Ribeiro", "Olivia Cardoso", "Pedro Lucas Gomes"],
  "8A": ["Felipe Ramos", "Larissa Freitas", "Matheus Araújo", "Natália Campos"],
  "8B": ["Rafael Monteiro", "Sofia Carvalho", "Thiago Moreira", "Valentina Neves"],
  "9A": ["Gabriela Torres", "Henrique Dias", "Isabela Moraes", "João Vitor Pinto"],
  "9B": ["Karen Batista", "Leonardo Siqueira", "Manuela Andrade", "Otávio Lacerda"],
  "1EM": ["Paula Vieira", "Renato Cunha", "Sara Medeiros", "Tiago Brandão"],
  "2EM": ["Úrsula Pacheco", "Vinícius Falcão", "Yasmin Teles", "Arthur Bastos"],
  "3EM": ["Beatriz Lopes", "Caio Veloso", "Daniela Rezende", "Eric Salgado"]
};
let _id = 0;
const ALUNOS_MOCK = Object.entries(NOMES_MOCK).flatMap(([turma, nomes]) =>
  nomes.map(nome => ({id: ++_id, nome, turma, matricula: String(2026000 + _id)})));

/* ===== Utilidades ===== */
const $ = (s, r = document) => r.querySelector(s);
const $$ = (s, r = document) => [...r.querySelectorAll(s)];
const mem = {};
// O login fica no cookie de sessão do backend; aqui só o nome, para exibir.
const sessao = {
  get nome() { try { return sessionStorage.getItem("nome"); } catch { return mem.nome || null; } },
  salvar(n) { mem.nome = n; try { sessionStorage.setItem("nome", n); } catch {} },
  limpar() { delete mem.nome; try { sessionStorage.clear(); } catch {} }
};
let turmas = [];
const esc = s => String(s).replace(/[&<>"']/g, c => ({"&":"&amp;","<":"&lt;",">":"&gt;",'"':"&quot;","'":"&#39;"}[c]));

async function api(caminho, opcoes = {}) {
  const res = await fetch(API_URL + caminho, {
    ...opcoes,
    credentials: "include", // envia o cookie de sessão (JSESSIONID)
    headers: {"Content-Type": "application/json", ...opcoes.headers}
  });
  const dados = await res.json().catch(() => ({}));
  if (!res.ok) throw Object.assign(new Error(dados.erro || "Erro " + res.status), {status: res.status, campos: dados.campos});
  return dados;
}
const semRede = e => !e.status; // falha de conexão (fetch lançou TypeError)

function mostrarMsg(el, texto, tipo = "erro") {
  el.textContent = texto; el.hidden = !texto;
  el.className = "msg " + (tipo === "ok" ? "ok-msg" : "erro-msg");
}
function erroCampo(nome, texto, escopo = document) {
  const el = $(`[data-erro="${nome}"]`, escopo);
  if (el) el.textContent = texto || "";
}

/* ===== Navegação entre telas ===== */
function telaLogin() { $("#tela-login").hidden = false; $("#tela-app").hidden = true; }
function telaApp() {
  $("#tela-login").hidden = true; $("#tela-app").hidden = false;
  const primeiro = (sessao.nome || "professor").split(" ")[0];
  $("#nome-prof").textContent = sessao.nome || "";
  $("#saudacao").textContent = primeiro;
  secao("painel");
  carregarTurmas();
}
function sessaoExpirada() { sessao.limpar(); telaLogin(); mostrarMsg($("#msg-login"), "Sua sessão expirou. Entre novamente."); }

async function carregarTurmas() {
  try {
    turmas = modoPrevia ? TURMAS_MOCK : (await api("/turmas")).map(t => t.nome);
  } catch (err) {
    if (err.status === 401) return sessaoExpirada();
    turmas = [];
  }
  preencherTurmas();
}
function preencherTurmas() {
  const sel = $("#turma"), atual = sel.value;
  sel.innerHTML = `<option value="">Todas as turmas</option>` + turmas.map(t => `<option>${esc(t)}</option>`).join("");
  sel.value = atual;
}
function secao(id) { ["painel", "wizard", "sucesso"].forEach(s => $("#" + s).hidden = s !== id); window.scrollTo(0, 0); }

/* ===== Login ===== */
$("#form-login").addEventListener("submit", async e => {
  e.preventDefault();
  const email = $("#email").value.trim(), senha = $("#senha").value;
  let ok = true;
  erroCampo("email", ""); erroCampo("senha", ""); mostrarMsg($("#msg-login"), "");
  if (!/^\S+@\S+\.\S+$/.test(email)) { erroCampo("email", "Informe um e-mail válido."); ok = false; }
  if (senha.length < 4) { erroCampo("senha", "A senha deve ter ao menos 4 caracteres."); ok = false; }
  if (!ok) return;

  const btn = e.submitter; btn.disabled = true; btn.textContent = "Entrando…";
  try {
    if (modoPrevia) { sessao.salvar("Professor(a) Demo"); telaApp(); return; }
    const d = await api("/login", {method: "POST", body: JSON.stringify({email, senha})});
    if (d.role === "COORDENACAO") { location.href = "/coordenacao"; return; }
    sessao.salvar(d.nome);
    telaApp();
  } catch (err) {
    if (semRede(err)) mostrarMsg($("#msg-login"), "Sem conexão com o servidor. Verifique a internet e tente novamente.");
    else mostrarMsg($("#msg-login"), err.status === 401 ? "E-mail ou senha incorretos." : err.message || "Não foi possível entrar. Tente novamente.");
  } finally { btn.disabled = false; btn.textContent = "Entrar"; }
});
$("#btn-sair").onclick = async () => {
  if (!modoPrevia) { try { await api("/logout", {method: "POST"}); } catch {} }
  sessao.limpar(); $("#form-login").reset(); telaLogin();
};

/* ===== Formulário em passos ===== */
const TOTAL = 5;
let passo = 1;
const estado = {aluno: null, tipo: "", descricao: "", medidas: []};

function montarOpcoes() {
  $("#tipos").innerHTML = TIPOS.map(t => `<label class="op"><input type="radio" name="tipo" value="${t.v}">${esc(t.l)}</label>`).join("");
  $("#medidas").innerHTML = MEDIDAS.map(m => `<label class="op"><input type="checkbox" name="medida" value="${esc(m)}">${esc(m)}</label>`).join("");
  preencherTurmas();
}

function novaOcorrencia() {
  Object.assign(estado, {aluno: null, tipo: "", descricao: "", medidas: []});
  $("#form-oc").reset(); montarOpcoes();
  const hoje = new Date().toLocaleDateString("sv-SE");
  $("#data").value = hoje; $("#data").max = hoje;
  $("#lista-alunos").innerHTML = ""; $("#aluno-sel").hidden = true; $("#cont").textContent = 0;
  mostrarMsg($("#msg-oc"), ""); $$(".erro", $("#form-oc")).forEach(e => e.textContent = "");
  secao("wizard"); irPara(1);
}

function irPara(n) {
  passo = n;
  $$(".passo").forEach(p => p.hidden = +p.dataset.passo !== n);
  $("#barra").style.width = (n / TOTAL * 100) + "%";
  $("#passo-info").textContent = `Passo ${n} de ${TOTAL}`;
  $("#btn-prox").textContent = n === TOTAL ? "Enviar ocorrência" : "Continuar";
  $("#btn-voltar").textContent = n === 1 ? "Cancelar" : "Voltar";
  mostrarMsg($("#msg-oc"), "");
  if (n === 5) montarResumo();
  if (n === 1) $("#busca").focus({preventScroll: true});
  if (n === 3) $("#descricao").focus({preventScroll: true});
}

/* Passo 1: busca de aluno */
let timer;
$("#busca").addEventListener("input", () => { clearTimeout(timer); timer = setTimeout(buscarAlunos, 250); });
$("#turma").addEventListener("change", buscarAlunos);

let pedidoBusca = 0; // descarta respostas de buscas antigas que cheguem atrasadas
async function buscarAlunos() {
  const q = $("#busca").value.trim(), turma = $("#turma").value, ul = $("#lista-alunos");
  const pedido = ++pedidoBusca;
  if (!q && !turma) { ul.innerHTML = ""; return; }
  let alunos;
  if (modoPrevia) {
    const n = s => s.toLowerCase().normalize("NFD").replace(/[\u0300-\u036f]/g, "");
    alunos = ALUNOS_MOCK.filter(a => (!turma || a.turma === turma) && (!q || n(a.nome).includes(n(q))));
  } else {
    try {
      alunos = await api("/alunos?" + new URLSearchParams({busca: q, turma}));
    } catch (err) {
      if (pedido !== pedidoBusca) return;
      if (err.status === 401) return sessaoExpirada();
      ul.innerHTML = `<li class="vazio">Não foi possível buscar alunos.</li>`; return;
    }
  }
  if (pedido !== pedidoBusca) return;
  ul.innerHTML = alunos.length
    ? alunos.map(a => `<li class="item" role="option" tabindex="0" data-id="${a.id}"><span>${esc(a.nome)}</span><small>${esc(a.turma)}</small></li>`).join("")
    : `<li class="vazio">Nenhum aluno encontrado.</li>`;
  ul._dados = alunos;
}

function selecionarAluno(li) {
  const a = $("#lista-alunos")._dados.find(x => String(x.id) === li.dataset.id);
  if (!a) return;
  estado.aluno = a; erroCampo("aluno", "");
  $("#lista-alunos").innerHTML = ""; $("#busca").value = "";
  const chip = $("#aluno-sel");
  chip.innerHTML = `<span>${esc(a.nome)} · ${esc(a.turma)}</span><button type="button">Trocar</button>`;
  chip.hidden = false;
}
$("#lista-alunos").addEventListener("click", e => { const li = e.target.closest(".item"); if (li) selecionarAluno(li); });
$("#lista-alunos").addEventListener("keydown", e => { if (e.key === "Enter" && e.target.classList.contains("item")) selecionarAluno(e.target); });
$("#aluno-sel").addEventListener("click", e => { if (e.target.tagName === "BUTTON") { estado.aluno = null; $("#aluno-sel").hidden = true; $("#busca").focus(); } });

$("#descricao").addEventListener("input", e => $("#cont").textContent = e.target.value.length);

/* Validação por passo */
function validar(n) {
  erroCampo("aluno", ""); erroCampo("tipo", ""); erroCampo("data", ""); erroCampo("descricao", ""); erroCampo("medidas", "");
  $("#descricao").classList.remove("invalido");
  if (n === 1 && !estado.aluno) { erroCampo("aluno", "Selecione um aluno da lista."); return false; }
  if (n === 2) {
    const marcado = $("input[name=tipo]:checked");
    estado.tipo = marcado?.value || "";
    estado.tipoLabel = marcado ? marcado.parentElement.textContent.trim() : "";
    if (!estado.tipo) { erroCampo("tipo", "Escolha o tipo da ocorrência."); return false; }
  }
  if (n === 3) {
    estado.descricao = $("#descricao").value.trim();
    if (estado.descricao.length < 10) { erroCampo("descricao", "Descreva o ocorrido com pelo menos 10 caracteres."); $("#descricao").classList.add("invalido"); return false; }
    estado.data = $("#data").value;
    if (!estado.data || estado.data > $("#data").max) { erroCampo("data", "Informe uma data válida (hoje ou anterior)."); return false; }
  }
  if (n === 4) {
    estado.medidas = $$("input[name=medida]:checked").map(i => i.value);
    const outra = $("#medida-outra").value.trim();
    if (outra) estado.medidas.push(outra);
    if (!estado.medidas.length) { erroCampo("medidas", "Selecione ou informe ao menos uma medida tomada."); return false; }
  }
  return true;
}

function montarResumo() {
  const l = [["Aluno", `${estado.aluno.nome} (${estado.aluno.turma})`], ["Motivo", estado.tipoLabel], ["Data", estado.data.split("-").reverse().join("/")], ["Descrição", estado.descricao], ["Medidas tomadas", estado.medidas.join("\n")]];
  $("#resumo").innerHTML = l.map(([k, v]) => `<div><dt>${k}</dt><dd>${esc(v)}</dd></div>`).join("");
}

$("#form-oc").addEventListener("submit", async e => {
  e.preventDefault();
  if (!validar(passo)) return;
  if (passo < TOTAL) return irPara(passo + 1);
  await enviar(e.submitter);
});
$("#btn-voltar").onclick = () => passo === 1 ? secao("painel") : irPara(passo - 1);

/* Envio: POST /ocorrencias */
async function enviar(btn) {
  const corpo = {
    // Professor e statusLancamento (NAO_LANCADA) devem ser definidos pelo backend, a partir da sessão autenticada
    aluno: {id: estado.aluno.id, nome: estado.aluno.nome}, turma: estado.aluno.turma, tipo: estado.tipo,
    descricao: estado.descricao, medidasTomadas: estado.medidas.join("; "), data: estado.data
  };
  btn.disabled = true; btn.textContent = "Enviando…";
  try {
    if (modoPrevia) console.info("Prévia — payload:", corpo);
    else await api("/ocorrencias", {method: "POST", body: JSON.stringify(corpo)});
    sucesso();
  } catch (err) {
    if (err.status === 401) return sessaoExpirada();
    mostrarMsg($("#msg-oc"), err.status ? `Não foi possível salvar: ${err.message}` : "Sem conexão com o servidor. Verifique a internet e tente novamente.");
  } finally { btn.disabled = false; btn.textContent = passo === TOTAL ? "Enviar ocorrência" : "Continuar"; }
}
function sucesso() {
  $("#sucesso-txt").textContent = `${estado.aluno.nome} (${estado.aluno.turma}) · ${estado.tipoLabel}`;
  secao("sucesso");
}

$("#btn-nova").onclick = novaOcorrencia;
$("#btn-outra").onclick = novaOcorrencia;
$("#btn-inicio").onclick = () => secao("painel");

/* Trocar de área: encerra a sessão atual antes, senão o /me redireciona de volta */
$("#ir-coordenacao").addEventListener("click", async e => {
  e.preventDefault();
  if (!modoPrevia) { try { await api("/logout", {method: "POST"}); } catch {} }
  sessao.limpar();
  location.href = "/coordenacao";
});

/* ===== Início ===== */
(async function iniciar() {
  try {
    const u = await api("/me");
    if (u.role === "COORDENACAO") { location.href = "/coordenacao"; return; }
    sessao.salvar(u.nome);
    telaApp();
  } catch (err) {
    if (semRede(err) && USAR_MOCK_SE_FALHAR) {
      modoPrevia = true;
      $("#dica").hidden = false;
      sessao.nome ? telaApp() : telaLogin();
    } else {
      sessao.limpar();
      telaLogin();
    }
  }
})();

