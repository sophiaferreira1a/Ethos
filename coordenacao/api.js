const MODO_LOCAL = !location.hostname || ["localhost", "127.0.0.1"].includes(location.hostname);

const CONFIG = {
  // Em dev, abra o front em "localhost" (não 127.0.0.1), senão o cookie de sessão não é enviado.
  // Em produção, a Vercel faz proxy de /api para o Render (ver vercel.json).
  API_URL: MODO_LOCAL ? "http://localhost:8080" : "/api",

  // Sem backend na abertura da página, usa dados fictícios em memória. Só em desenvolvimento.
  USAR_MOCK_SE_FALHAR: MODO_LOCAL,

  TAMANHO_PAGINA: 20,

  // Turma e aluno não vão para o backend (lá o filtro é por UUID); são filtrados no navegador.
  PARAMS: {
    tipo: "tipo", status: "status", dataInicio: "dataInicio", dataFim: "dataFim",
    pagina: "page", tamanho: "size", ordem: "sort"
  },

  PODE_ALTERAR_LANCADA: false
};

// Manter igual à lista do app.js (professor) e ao enum TipoOcorrencia.
const TIPOS = [
  {v: "DESRESPEITO", l: "Desrespeito ao professor ou funcionário"},
  {v: "INDISCIPLINA", l: "Indisciplina / bagunça em sala"},
  {v: "CONFLITO", l: "Briga ou agressão entre alunos"},
  {v: "CELULAR", l: "Uso indevido de celular ou eletrônicos"},
  {v: "ATIVIDADE", l: "Recusa em fazer atividade / sem material"},
  {v: "SAIDA_SEM_AUTORIZACAO", l: "Saída de sala sem autorização"},
  {v: "PATRIMONIO", l: "Dano ao patrimônio da escola"},
  {v: "OUTRO", l: "Outro comportamento inadequado"}
];
const TURMAS_PADRAO = ["6A", "6B", "7A", "7B", "8A", "8B", "9A", "9B", "1EM", "2EM", "3EM"];

const ACOES_HISTORICO = {
  OCORRENCIA_CRIADA: "Registrada",
  OCORRENCIA_ATUALIZADA: "Corrigida",
  OCORRENCIA_LANCADA: "Marcada como lançada",
  OCORRENCIA_EXCLUIDA: "Excluída"
};

function rotuloTipo(v) {
  const t = TIPOS.find(x => x.v === v);
  if (t) return t.l;
  return v ? v.charAt(0) + v.slice(1).toLowerCase().replace(/_/g, " ") : "—";
}

class ApiErro extends Error {
  constructor(status, mensagem, campos) {
    super(mensagem);
    this.status = status; // 0 = sem conexão
    this.campos = campos || null;
  }
}
const MENSAGENS = {
  0: "Sem conexão com o servidor. Verifique a internet e tente novamente.",
  400: "Os dados enviados não foram aceitos. Revise os campos.",
  401: "Sua sessão expirou. Entre novamente.",
  403: "Seu perfil não tem permissão para esta ação.",
  404: "Ocorrência não encontrada. Ela pode ter sido excluída.",
  409: "Esta ocorrência foi alterada por outra pessoa. A lista foi atualizada.",
  500: "Erro no servidor. Tente novamente em instantes."
};
const DETALHES_PADRAO = new Set(["criada pelo professor", "atualizada", "excluida", "lancada pela coordenacao"]);
const mensagemPadrao = s => MENSAGENS[s] || `Erro ${s} ao falar com o servidor.`;

async function requisitar(metodo, caminho, corpo) {
  let res;
  try {
    res = await fetch(CONFIG.API_URL + caminho, {
      method: metodo,
      credentials: "include", // envia o cookie de sessão (JSESSIONID)
      headers: corpo !== undefined ? {"Content-Type": "application/json"} : {},
      body: corpo !== undefined ? JSON.stringify(corpo) : undefined
    });
  } catch {
    throw new ApiErro(0, MENSAGENS[0]);
  }
  if (res.status === 204) return null;
  const texto = await res.text();
  let dados = null;
  try { dados = texto ? JSON.parse(texto) : null; } catch {}
  if (!res.ok) {
    // Para 401/403/404 a mensagem da interface é mais clara que a do backend.
    const msg = [401, 403, 404].includes(res.status) ? mensagemPadrao(res.status) : (dados?.erro || mensagemPadrao(res.status));
    throw new ApiErro(res.status, msg, dados?.campos);
  }
  return dados;
}

const nomeDe = v => v == null ? "" : typeof v === "string" ? v : (v.nome ?? "");

function normalizarOcorrencia(o) {
  return {
    id: String(o.id),
    aluno: {id: o.aluno?.id ?? o.alunoId ?? null, nome: nomeDe(o.aluno) || o.alunoNome || "—"},
    turma: nomeDe(o.turma) || o.turmaNome || "—",
    professor: nomeDe(o.professor) || o.professorNome || "—",
    tipo: o.tipo || "",
    descricao: o.descricao || "",
    medidasTomadas: o.medidasTomadas || "",
    medidasSugeridas: o.medidasSugeridas || "",
    data: o.data || "",
    status: o.statusLancamento || o.status || "NAO_LANCADA",
    dataLancamento: o.dataLancamento || null,
    responsavelLancamento: nomeDe(o.responsavelLancamento) || o.responsavelLancamentoNome || "",
    criadoEm: o.criadoEm || null
  };
}

const semAcento = t => String(t).toLowerCase().normalize("NFD").replace(/[\u0300-\u036f]/g, "");

function normalizarTurma(t) {
  return {id: String(t.id), nome: nomeDe(t)};
}

function normalizarAluno(a) {
  return {
    id: String(a.id),
    nome: a.nome || "—",
    turmaId: a.turmaId ? String(a.turmaId) : null,
    turma: nomeDe(a.turma) || a.turmaNome || "—"
  };
}


function filtrarNoNavegador(itens, filtros, ordem) {
  const turma = filtros.turma ? semAcento(filtros.turma) : "";
  const aluno = filtros.aluno ? semAcento(filtros.aluno) : "";
  const r = itens.filter(o =>
    (!turma || semAcento(o.turma) === turma) && (!aluno || semAcento(o.aluno.nome).includes(aluno)));
  const sinal = ordem.endsWith("asc") ? 1 : -1;
  return r.sort((a, b) =>
    (a.data === b.data ? String(a.criadoEm).localeCompare(String(b.criadoEm)) : a.data.localeCompare(b.data)) * sinal);
}

function normalizarPagina(d, filtros, pagina, tamanho, ordem) {
  if (Array.isArray(d)) {
    const todos = filtrarNoNavegador(d.map(normalizarOcorrencia), filtros, ordem);
    return {
      itens: todos.slice(pagina * tamanho, (pagina + 1) * tamanho),
      total: todos.length, pagina,
      totalPaginas: Math.max(1, Math.ceil(todos.length / tamanho))
    };
  }
  // Page do Spring: metadados na raiz ou em "page" (PagedModel)
  const meta = d?.page || d || {};
  return {
    itens: (d?.content || []).map(normalizarOcorrencia),
    total: meta.totalElements ?? 0,
    pagina: meta.number ?? pagina,
    totalPaginas: Math.max(1, meta.totalPages ?? 1)
  };
}

function normalizarHistorico(h) {
  return {
    acao: h.acao,
    rotulo: ACOES_HISTORICO[h.acao] || h.acao,
    usuario: nomeDe(h.usuario) || h.usuarioNome || "—",
    dataHora: h.dataHora || h.data || null,
    detalhes: DETALHES_PADRAO.has(h.detalhes) ? "" : (h.detalhes || "")
  };
}

function corpoAtualizacao(o, mudancas) {
  return {
    aluno: {id: o.aluno.id, nome: o.aluno.nome},
    turma: o.turma,
    tipo: mudancas.tipo,
    descricao: mudancas.descricao,
    medidasTomadas: mudancas.medidasTomadas,
    medidasSugeridas: mudancas.medidasSugeridas || "",
    data: mudancas.data
  };
}

const Api = {
  local: false,

  async me() {
    if (this.local) return Local.me();
    try {
      return await requisitar("GET", "/me");
    } catch (e) {
      if (e.status === 0 && CONFIG.USAR_MOCK_SE_FALHAR) {
        this.local = true;
        return Local.me();
      }
      throw e;
    }
  },

  async login(email, senha) {
    if (this.local) return Local.login(email, senha);
    return requisitar("POST", "/login", {email, senha});
  },

  async logout() {
    if (this.local) return Local.logout();
    try { await requisitar("POST", "/logout"); } catch {}
  },

  async listar(filtros, pagina = 0, ordem = "data,desc", tamanho = CONFIG.TAMANHO_PAGINA) {
    if (this.local) return Local.listar(filtros, pagina, ordem, tamanho);
    const p = new URLSearchParams();
    for (const [k, v] of Object.entries(filtros)) if (v && CONFIG.PARAMS[k]) p.set(CONFIG.PARAMS[k], v);
    p.set(CONFIG.PARAMS.pagina, pagina);
    p.set(CONFIG.PARAMS.tamanho, tamanho);
    p.set(CONFIG.PARAMS.ordem, ordem);
    return normalizarPagina(await requisitar("GET", "/ocorrencias?" + p), filtros, pagina, tamanho, ordem);
  },

  async obter(id) {
    if (this.local) return Local.obter(id);
    return normalizarOcorrencia(await requisitar("GET", `/ocorrencias/${encodeURIComponent(id)}`));
  },

  async atualizar(ocorrencia, mudancas) {
    if (this.local) return Local.atualizar(ocorrencia.id, corpoAtualizacao(ocorrencia, mudancas));
    const d = await requisitar("PUT", `/ocorrencias/${encodeURIComponent(ocorrencia.id)}`, corpoAtualizacao(ocorrencia, mudancas));
    return d ? normalizarOcorrencia(d) : this.obter(ocorrencia.id);
  },

  async excluir(id) {
    if (this.local) return Local.excluir(id);
    await requisitar("DELETE", `/ocorrencias/${encodeURIComponent(id)}`);
  },

  // Sem corpo: data e responsável são definidos pelo backend a partir da sessão.
  async marcarLancada(id) {
    if (this.local) return Local.marcarLancada(id);
    const d = await requisitar("PATCH", `/ocorrencias/${encodeURIComponent(id)}/lancada`);
    return d ? normalizarOcorrencia(d) : this.obter(id);
  },

  async historico(id) {
    if (this.local) return Local.historico(id);
    try {
      const d = await requisitar("GET", `/ocorrencias/${encodeURIComponent(id)}/historico`);
      return (d || []).map(normalizarHistorico);
    } catch (e) {
      if (e.status === 404) return null;
      throw e;
    }
  },

  async indicadores() {
    if (this.local) return Local.indicadores();
    try {
      const d = await requisitar("GET", "/indicadores");
      return {
        total: d.total ?? d.totalOcorrencias ?? 0,
        naoLancadas: d.naoLancadas ?? d.naoLancada ?? 0,
        lancadas: d.lancadas ?? d.lancada ?? 0
      };
    } catch (e) {
      if (e.status !== 404) throw e;
      const contar = async status => (await this.listar({status}, 0, "data,desc", 1)).total;
      const [naoLancadas, lancadas] = await Promise.all([contar("NAO_LANCADA"), contar("LANCADA")]);
      return {total: naoLancadas + lancadas, naoLancadas, lancadas};
    }
  },

  async turmas() {
    if (this.local) return Local.turmas();
    try {
      const lista = await this.listarTurmas();
      const nomes = lista.map(t => t.nome).filter(Boolean);
      return nomes.length ? nomes : TURMAS_PADRAO;
    } catch {
      return TURMAS_PADRAO;
    }
  },

  async listarTurmas() {
    if (this.local) return Local.listarTurmas();
    const d = await requisitar("GET", "/turmas");
    return (d || []).map(normalizarTurma);
  },

  async criarTurma(nome) {
    if (this.local) return Local.criarTurma(nome);
    return normalizarTurma(await requisitar("POST", "/turmas", {nome}));
  },

  async atualizarTurma(id, nome) {
    if (this.local) return Local.atualizarTurma(id, nome);
    return normalizarTurma(await requisitar("PUT", `/turmas/${encodeURIComponent(id)}`, {nome}));
  },

  async excluirTurma(id) {
    if (this.local) return Local.excluirTurma(id);
    await requisitar("DELETE", `/turmas/${encodeURIComponent(id)}`);
  },

  async listarAlunos(filtros = {}) {
    if (this.local) return Local.listarAlunos(filtros);
    const p = new URLSearchParams();
    if (filtros.busca) p.set("busca", filtros.busca);
    if (filtros.turma) p.set("turma", filtros.turma);
    const d = await requisitar("GET", "/alunos" + (p.toString() ? "?" + p : ""));
    return (d || []).map(normalizarAluno);
  },

  async criarAluno(nome, turmaId) {
    if (this.local) return Local.criarAluno(nome, turmaId);
    return normalizarAluno(await requisitar("POST", "/alunos", {nome, turmaId}));
  },

  async atualizarAluno(id, nome, turmaId) {
    if (this.local) return Local.atualizarAluno(id, nome, turmaId);
    return normalizarAluno(await requisitar("PUT", `/alunos/${encodeURIComponent(id)}`, {nome, turmaId}));
  },

  async excluirAluno(id) {
    if (this.local) return Local.excluirAluno(id);
    await requisitar("DELETE", `/alunos/${encodeURIComponent(id)}`);
  },

  async moverAlunos(alunoIds, turmaId) {
    if (this.local) return Local.moverAlunos(alunoIds, turmaId);
    const d = await requisitar("PATCH", "/alunos/turma", {alunoIds, turmaId});
    return (d || []).map(normalizarAluno);
  }
};

// Nomes fictícios (LGPD). Nada é salvo: recarregar a página zera tudo.
const Local = (() => {
  const chave = "ethos-coord-local";
  const guardar = v => { try { v ? sessionStorage.setItem(chave, v) : sessionStorage.removeItem(chave); } catch {} };
  const lido = () => { try { return sessionStorage.getItem(chave); } catch { return null; } };
  let usuario = lido() ? {nome: lido(), email: "coordenacao@escola.com", role: "COORDENACAO"} : null;

  // Semente fixa: os mesmos dados a cada recarga.
  let s = 20261007;
  const rnd = () => { s |= 0; s = s + 0x6D2B79F5 | 0; let t = Math.imul(s ^ s >>> 15, 1 | s); t = t + Math.imul(t ^ t >>> 7, 61 | t) ^ t; return ((t ^ t >>> 14) >>> 0) / 4294967296; };
  const um = lista => lista[Math.floor(rnd() * lista.length)];

  const ALUNOS = {
    "6A": ["Ana Clara Souza", "Bruno Henrique Lima"], "6B": ["Enzo Gabriel Costa", "Helena Barros"],
    "7A": ["Diego Alves", "Julia Fernandes"], "7B": ["Mariana Duarte", "Nicolas Ribeiro"],
    "8A": ["Felipe Ramos", "Larissa Freitas"], "8B": ["Rafael Monteiro", "Sofia Carvalho"],
    "9A": ["Gabriela Torres", "Henrique Dias"], "9B": ["Karen Batista", "Otávio Lacerda"],
    "1EM": ["Paula Vieira", "Renato Cunha"], "2EM": ["Vinícius Falcão", "Yasmin Teles"], "3EM": ["Beatriz Lopes", "Caio Veloso"]
  };
  const turmas = Object.keys(ALUNOS).map(nome => ({id: `turma-${nome}`, nome}));
  const alunos = Object.entries(ALUNOS).flatMap(([turma, nomes]) =>
    nomes.map((nome, indice) => ({id: `aluno-${turma}-${indice}`, nome, turmaId: `turma-${turma}`})));
  let seqTurma = 0, seqAluno = 0;
  const PROFESSORES = ["Maria Silva", "Joao Pereira"];
  const DESCRICOES = {
    DESRESPEITO: "Respondeu de forma grosseira após ser chamado atenção durante a explicação.",
    INDISCIPLINA: "Conversou alto e atrapalhou a turma durante a atividade em grupo.",
    CONFLITO: "Discussão com colega no intervalo que terminou em empurrões.",
    CELULAR: "Usou o celular durante a avaliação mesmo após aviso.",
    ATIVIDADE: "Terceira aula seguida sem o material e sem realizar a atividade proposta.",
    SAIDA_SEM_AUTORIZACAO: "Saiu da sala sem autorização e só voltou ao fim da aula.",
    PATRIMONIO: "Riscou a carteira com caneta durante a aula.",
    OUTRO: "Comportamento inadequado durante a apresentação de trabalhos."
  };
  const MEDIDAS = ["Conversa com o aluno", "Advertência verbal", "Encaminhado à coordenação", "Troca de lugar", "Atividade de reflexão"];
  const SUGESTOES = ["Advertência escrita (responsáveis assinam ciência)", "Suspensão", "Chamar os pais ou responsáveis", "Reunião com os responsáveis"];

  const hoje = new Date();
  const diaISO = d => d.toLocaleDateString("sv-SE");
  const ocorrencias = [];
  const historicos = {};
  let seq = 0;
  for (let i = 0; i < 42; i++) {
    const turma = um(Object.keys(ALUNOS));
    const tipo = um(TIPOS).v;
    const dia = new Date(hoje); dia.setDate(hoje.getDate() - Math.floor(rnd() * 45));
    const id = `local-${String(++seq).padStart(3, "0")}`;
    const professor = um(PROFESSORES);
    const lancada = rnd() < 0.62 && (hoje - dia) > 864e5;
    const o = {
      id, aluno: {id: `aluno-${turma}-${i % 2}`, nome: ALUNOS[turma][i % 2]}, turma, professor, tipo,
      descricao: DESCRICOES[tipo], medidasTomadas: [um(MEDIDAS), um(MEDIDAS)].filter((m, k, a) => a.indexOf(m) === k).join("; "),
      medidasSugeridas: [um(SUGESTOES), um(SUGESTOES)].filter((m, k, a) => a.indexOf(m) === k).join("; "),
      data: diaISO(dia), status: "NAO_LANCADA", dataLancamento: null, responsavelLancamento: "", criadoEm: dia.toISOString()
    };
    historicos[id] = [{acao: "OCORRENCIA_CRIADA", usuario: professor, dataHora: dia.toISOString(), detalhes: ""}];
    if (lancada) {
      const quando = new Date(dia.getTime() + (1 + Math.floor(rnd() * 3)) * 864e5);
      if (quando > hoje) quando.setTime(hoje.getTime());
      Object.assign(o, {status: "LANCADA", dataLancamento: quando.toISOString(), responsavelLancamento: "Ana Coordenacao"});
      historicos[id].push({acao: "OCORRENCIA_LANCADA", usuario: "Ana Coordenacao", dataHora: quando.toISOString(), detalhes: ""});
    }
    ocorrencias.push(o);
  }

  const espera = (v, ms = 220) => new Promise(r => setTimeout(() => r(structuredClone(v)), ms));
  const achar = id => {
    const o = ocorrencias.find(x => x.id === id);
    if (!o) throw new ApiErro(404, mensagemPadrao(404));
    return o;
  };
  const registrar = (id, acao, detalhes = "") =>
    historicos[id].push({acao, usuario: usuario?.nome || "—", dataHora: new Date().toISOString(), detalhes});
  const exigirSessao = () => { if (!usuario) throw new ApiErro(401, mensagemPadrao(401)); };
  const acharTurma = id => {
    const t = turmas.find(x => x.id === id);
    if (!t) throw new ApiErro(404, "Turma não encontrada.");
    return t;
  };
  const acharAluno = id => {
    const a = alunos.find(x => x.id === id);
    if (!a) throw new ApiErro(404, "Aluno não encontrado ou excluído.");
    return a;
  };
  const nomeTurma = id => acharTurma(id).nome;
  const alunoNormalizado = a => ({id: a.id, nome: a.nome, turmaId: a.turmaId, turma: nomeTurma(a.turmaId)});

  return {
    async me() { exigirSessao(); return espera(usuario, 60); },
    async login(email, senha) {
      if (!/^\S+@\S+\.\S+$/.test(email) || senha.length < 4) throw new ApiErro(401, "E-mail ou senha inválidos.");
      usuario = {nome: "Coordenação", email, role: "COORDENACAO"};
      guardar(usuario.nome);
      return espera(usuario);
    },
    async logout() { usuario = null; guardar(null); },

    async turmas() {
      exigirSessao();
      return espera(turmas.map(t => t.nome));
    },
    async listarTurmas() {
      exigirSessao();
      return espera(turmas.map(t => ({id: t.id, nome: t.nome})));
    },
    async criarTurma(nome) {
      exigirSessao();
      const limpo = String(nome || "").trim();
      if (!limpo) throw new ApiErro(400, "Informe o nome da turma.");
      if (turmas.some(t => semAcento(t.nome) === semAcento(limpo))) {
        throw new ApiErro(409, "Já existe uma turma com esse nome.");
      }
      const nova = {id: `turma-local-${++seqTurma}`, nome: limpo};
      turmas.push(nova);
      return espera(nova);
    },
    async atualizarTurma(id, nome) {
      exigirSessao();
      const turma = acharTurma(id);
      const limpo = String(nome || "").trim();
      if (!limpo) throw new ApiErro(400, "Informe o nome da turma.");
      if (turmas.some(t => t.id !== id && semAcento(t.nome) === semAcento(limpo))) {
        throw new ApiErro(409, "Já existe uma turma com esse nome.");
      }
      const anterior = turma.nome;
      turma.nome = limpo;
      // No mock a ocorrência guarda o nome da turma, então o rename precisa refletir no histórico.
      ocorrencias.forEach(o => { if (o.turma === anterior) o.turma = limpo; });
      return espera(turma);
    },
    async excluirTurma(id) {
      exigirSessao();
      const turma = acharTurma(id);
      if (alunos.some(a => a.turmaId === id) || ocorrencias.some(o => o.turma === turma.nome)) {
        throw new ApiErro(409, "Turma possui alunos ou ocorrências vinculadas e não pode ser excluída.");
      }
      turmas.splice(turmas.indexOf(turma), 1);
      await espera(null);
    },
    async listarAlunos(f = {}) {
      exigirSessao();
      const busca = semAcento(f.busca || "");
      const lista = alunos
        .filter(a => (!f.turma || nomeTurma(a.turmaId) === f.turma) &&
          (!busca || semAcento(a.nome).includes(busca)))
        .sort((a, b) => a.nome.localeCompare(b.nome, "pt-BR"))
        .map(alunoNormalizado);
      return espera(lista);
    },
    async criarAluno(nome, turmaId) {
      exigirSessao();
      const limpo = String(nome || "").trim();
      if (!limpo) throw new ApiErro(400, "Informe o nome do aluno.");
      acharTurma(turmaId);
      const novo = {id: `aluno-local-${++seqAluno}`, nome: limpo, turmaId};
      alunos.push(novo);
      return espera(alunoNormalizado(novo));
    },
    async atualizarAluno(id, nome, turmaId) {
      exigirSessao();
      const aluno = acharAluno(id);
      const limpo = String(nome || "").trim();
      if (!limpo) throw new ApiErro(400, "Informe o nome do aluno.");
      acharTurma(turmaId);
      Object.assign(aluno, {nome: limpo, turmaId});
      return espera(alunoNormalizado(aluno));
    },
    async excluirAluno(id) {
      exigirSessao();
      const aluno = acharAluno(id);
      alunos.splice(alunos.indexOf(aluno), 1);
      await espera(null);
    },
    async moverAlunos(alunoIds, turmaId) {
      exigirSessao();
      acharTurma(turmaId);
      const selecionados = alunoIds.map(acharAluno);
      selecionados.forEach(a => { a.turmaId = turmaId; });
      return espera(selecionados.map(alunoNormalizado));
    },

    async listar(f, pagina, ordem, tamanho) {
      exigirSessao();
      const r = filtrarNoNavegador(ocorrencias.filter(o =>
        (!f.tipo || o.tipo === f.tipo) && (!f.status || o.status === f.status) &&
        (!f.dataInicio || o.data >= f.dataInicio) && (!f.dataFim || o.data <= f.dataFim)), f, ordem);
      return espera({
        itens: r.slice(pagina * tamanho, (pagina + 1) * tamanho), total: r.length, pagina,
        totalPaginas: Math.max(1, Math.ceil(r.length / tamanho))
      });
    },
    async obter(id) { exigirSessao(); return espera(achar(id)); },
    async atualizar(id, corpo) {
      exigirSessao();
      const o = achar(id);
      if (o.status === "LANCADA" && !CONFIG.PODE_ALTERAR_LANCADA) throw new ApiErro(409, "Ocorrência já lançada não pode ser alterada.");
      Object.assign(o, {tipo: corpo.tipo, descricao: corpo.descricao, medidasTomadas: corpo.medidasTomadas,
        medidasSugeridas: corpo.medidasSugeridas, data: corpo.data});
      registrar(id, "OCORRENCIA_ATUALIZADA");
      return espera(o);
    },
    async excluir(id) {
      exigirSessao();
      const o = achar(id);
      if (o.status === "LANCADA" && !CONFIG.PODE_ALTERAR_LANCADA) throw new ApiErro(409, "Ocorrência já lançada não pode ser excluída.");
      ocorrencias.splice(ocorrencias.indexOf(o), 1);
      await espera(null);
    },
    async marcarLancada(id) {
      exigirSessao();
      const o = achar(id);
      if (o.status === "LANCADA") throw new ApiErro(409, mensagemPadrao(409));
      Object.assign(o, {status: "LANCADA", dataLancamento: new Date().toISOString(), responsavelLancamento: usuario.nome});
      registrar(id, "OCORRENCIA_LANCADA");
      return espera(o);
    },
    async historico(id) { exigirSessao(); return espera((historicos[id] || []).map(normalizarHistorico), 120); },
    async indicadores() {
      exigirSessao();
      const naoLancadas = ocorrencias.filter(o => o.status === "NAO_LANCADA").length;
      return espera({total: ocorrencias.length, naoLancadas, lancadas: ocorrencias.length - naoLancadas}, 120);
    }
  };
})();
