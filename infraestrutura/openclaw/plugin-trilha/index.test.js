import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";
import {
  criarPluginDaTrilha,
  interpretarTextoDeConfirmacao,
  MENSAGENS,
} from "./index.js";

const IDENTIFICADOR_DA_OPERACAO = "123e4567-e89b-12d3-a456-426614174000";
const APLICADA_EM = "2026-07-26T18:00:00Z";

function registrar(buscar, pluginConfig = {}, loggerInformado) {
  const comandos = [];
  const hooks = [];
  const logs = [];
  const logger = loggerInformado ?? {
    info: (mensagem) => logs.push(mensagem),
    warn: (mensagem) => logs.push(mensagem),
  };
  criarPluginDaTrilha({ buscar }).register({
    pluginConfig,
    logger,
    registerCommand(registrado) {
      comandos.push(registrado);
    },
    on(nome, manipulador, opcoes) {
      hooks.push({ nome, manipulador, opcoes });
    },
  });
  return {
    comandos,
    hooks,
    logs,
    comando(nome) {
      const comando = comandos.find((item) => item.name === nome);
      assert.ok(comando, `comando ${nome} nao registrado`);
      return comando;
    },
    hook(nome) {
      const hook = hooks.find((item) => item.nome === nome);
      assert.ok(hook, `hook ${nome} nao registrado`);
      return hook.manipulador;
    },
  };
}

function contextoPrivado(sobrescritas = {}) {
  return {
    channel: "telegram",
    senderId: "123456789",
    from: "telegram:987654321",
    to: "telegram:987654321",
    accountId: "default",
    args: "23456789AB",
    commandBody: "/conectar 23456789AB",
    isAuthorizedSender: true,
    messageId: "update-10",
    config: {},
    ...sobrescritas,
  };
}

function eventoPrivado(sobrescritas = {}) {
  return {
    content: "2345678A",
    channel: "telegram",
    accountId: "default",
    conversationId: "987654321",
    senderId: "123456789",
    messageId: "update-10",
    isGroup: false,
    commandAuthorized: true,
    ...sobrescritas,
  };
}

function contextoDoHook(sobrescritas = {}) {
  return {
    channelId: "telegram",
    accountId: "default",
    conversationId: "987654321",
    senderId: "123456789",
    messageId: "update-10",
    ...sobrescritas,
  };
}

function respostaAplicada(sobrescritasDoRecibo = {}, sobrescritas = {}) {
  return new Response(JSON.stringify({
    codigo: "OPERACAO_APLICADA",
    recibo: {
      identificadorDaOperacao: IDENTIFICADOR_DA_OPERACAO,
      tipo: "REGISTRAR_ESTUDO",
      estado: "APLICADA",
      aplicadaEm: APLICADA_EM,
      resultado: {
        tipo: "REGISTRAR_ESTUDO",
        dados: { dadoSensivel: "nao deve chegar ao Telegram" },
      },
      ...sobrescritasDoRecibo,
    },
    ...sobrescritas,
  }), { status: 200, headers: { "content-type": "application/json" } });
}

function respostaReforcada(sobrescritas = {}) {
  return new Response(JSON.stringify({
    codigo: "NOVA_CONFIRMACAO_EXIGIDA",
    proximoCodigo: "BCDEFGHJ",
    proximaFrase: "/confirmar BCDEFGHJ",
    ...sobrescritas,
  }), { status: 200, headers: { "content-type": "application/json" } });
}

test("registra comandos e hooks terminais antes do modelo", () => {
  const registro = registrar(async () => respostaAplicada());
  const conectar = registro.comando("conectar");
  const confirmar = registro.comando("confirmar");

  assert.equal(conectar.requireAuth, false);
  assert.equal(confirmar.requireAuth, true);
  assert.deepEqual(conectar.channels, ["telegram"]);
  assert.deepEqual(confirmar.channels, ["telegram"]);
  assert.deepEqual(registro.hooks.map((item) => item.nome), [
    "inbound_claim", "before_dispatch",
  ]);
  for (const hook of registro.hooks) {
    assert.equal(hook.opcoes.priority, 100);
    assert.equal(hook.opcoes.timeoutMs, 6000);
  }
});

test("interpreta tres formas de confirmacao e preserva texto arbitrario", () => {
  for (const texto of [
    "/confirmar 2345678a",
    "CONFIRMAR 2345678A",
    "  confirmar\t2345678a  ",
    "2345678a",
  ]) {
    assert.deepEqual(interpretarTextoDeConfirmacao(texto), {
      tipo: "CONFIRMAR", codigo: "2345678A",
    });
  }
  for (const texto of [
    "/confirmar",
    "confirmar codigo-ruim",
    "/confirmar 2345678A sobra",
  ]) {
    assert.deepEqual(interpretarTextoDeConfirmacao(texto), { tipo: "INVALIDA" });
  }
  for (const texto of ["quero revisar hoje", "nao confirmar isso", "", null]) {
    assert.deepEqual(interpretarTextoDeConfirmacao(texto), { tipo: "IGNORAR" });
  }
});

test("vinculo aceita chat privado diferente do remetente e envia conta configurada", async () => {
  let chamada;
  const registro = registrar(async (url, opcoes) => {
    chamada = { url, opcoes };
    return new Response(JSON.stringify({ codigo: "VINCULO_CONCLUIDO" }), {
      status: 201,
      headers: { "content-type": "application/json" },
    });
  }, {
    urlDoIntegrador: "http://integrador-teste:8091",
    tempoLimiteEmMs: 1200,
    identificadorDaContaDoBot: "principal",
  });

  const resultado = await registro.comando("conectar").handler(
    contextoPrivado({ accountId: "principal", args: "23456789ab" }));

  assert.deepEqual(resultado, { text: MENSAGENS.sucesso });
  assert.equal(chamada.url, "http://integrador-teste:8091/v1/vinculos/telegram");
  assert.equal(chamada.opcoes.method, "POST");
  assert.equal(chamada.opcoes.redirect, "error");
  assert.deepEqual(JSON.parse(chamada.opcoes.body), {
    versaoDoContrato: "1",
    canal: "TELEGRAM",
    codigoDeVinculo: "23456789AB",
    identificadorDoTelegram: "123456789",
    identificadorDoChat: "987654321",
    identificadorDaContaDoBot: "principal",
  });
  assert.ok(chamada.opcoes.signal instanceof AbortSignal);
});

test("vinculo rejeita canal, conversa, conta e codigo invalidos", async () => {
  let chamadas = 0;
  const registro = registrar(async () => {
    chamadas += 1;
    return { status: 204 };
  });
  const comando = registro.comando("conectar");

  assert.deepEqual(await comando.handler(contextoPrivado({ channel: "discord" })),
    { text: MENSAGENS.canal });
  assert.deepEqual(await comando.handler(contextoPrivado({
    from: "telegram:-100123", to: "telegram:-100123",
  })), { text: MENSAGENS.privado });
  assert.deepEqual(await comando.handler(contextoPrivado({ senderId: "usuario" })),
    { text: MENSAGENS.privado });
  assert.deepEqual(await comando.handler(contextoPrivado({ accountId: "" })),
    { text: MENSAGENS.conta });
  assert.deepEqual(await comando.handler(contextoPrivado({ args: "" })),
    { text: MENSAGENS.uso });
  assert.deepEqual(await comando.handler(contextoPrivado({ args: "23456789IO" })),
    { text: MENSAGENS.codigo });
  assert.equal(chamadas, 0);
});

test("comando nativo confirma com recibo factual sem expor resultado", async () => {
  let chamada;
  const registro = registrar(async (url, opcoes) => {
    chamada = { url, opcoes };
    return respostaAplicada();
  });
  const resposta = await registro.comando("confirmar").handler(
    contextoPrivado({ args: "2345678a" }));

  assert.deepEqual(resposta, {
    text: MENSAGENS.confirmacaoAplicada + IDENTIFICADOR_DA_OPERACAO,
  });
  assert.doesNotMatch(resposta.text, /dadoSensivel|REGISTRAR_ESTUDO/);
  assert.equal(chamada.url,
    "http://integrador:8090/v1/operacoes/telegram/confirmacao");
  assert.deepEqual(JSON.parse(chamada.opcoes.body), {
    versaoDoContrato: "1",
    canal: "TELEGRAM",
    codigo: "2345678A",
    metodo: "TEXTO",
    identificadorDoTelegram: "123456789",
    identificadorDoChat: "987654321",
    identificadorDaContaDoBot: "default",
    identificadorDoUpdate: "update-10",
  });
});

test("inbound_claim intercepta comando, frase e codigo sem chegar ao modelo", async () => {
  const textos = [
    "/confirmar 2345678A",
    "CONFIRMAR 2345678A",
    "2345678A",
  ];
  let chamadas = 0;
  const registro = registrar(async () => {
    chamadas += 1;
    return respostaAplicada();
  });
  const hook = registro.hook("inbound_claim");

  for (const content of textos) {
    assert.deepEqual(await hook(eventoPrivado({ content }), contextoDoHook()), {
      handled: true,
      reply: {
        text: MENSAGENS.confirmacaoAplicada + IDENTIFICADOR_DA_OPERACAO,
      },
    });
  }
  assert.equal(await hook(
    eventoPrivado({ content: "qual meu plano hoje?" }), contextoDoHook()),
  undefined);
  assert.equal(chamadas, 3);
});

test("before_dispatch cobre binding configurado no OpenClaw 2026.7.1", async () => {
  let chamadas = 0;
  const registro = registrar(async () => {
    chamadas += 1;
    return respostaAplicada();
  });
  const evento = eventoPrivado({
    content: "CONFIRMAR 2345678A",
    commandAuthorized: undefined,
  });

  assert.deepEqual(await registro.hook("before_dispatch")(
    evento, contextoDoHook()), {
    handled: true,
    text: MENSAGENS.confirmacaoAplicada + IDENTIFICADOR_DA_OPERACAO,
  });
  assert.equal(chamadas, 1);
});

test("texto de confirmacao malformado recebe erro seguro sem chamar integrador", async () => {
  let chamadas = 0;
  const registro = registrar(async () => {
    chamadas += 1;
    return respostaAplicada();
  });

  assert.deepEqual(await registro.hook("inbound_claim")(
    eventoPrivado({ content: "CONFIRMAR codigo-invalido" }),
    contextoDoHook()), {
    handled: true,
    reply: { text: MENSAGENS.confirmacaoInvalida },
  });
  assert.equal(await registro.hook("inbound_claim")(
    eventoPrivado({
      content: "CONFIRMAR codigo-invalido",
      channel: "discord",
    }), contextoDoHook()), undefined);
  assert.deepEqual(await registro.hook("inbound_claim")(
    eventoPrivado({
      content: "CONFIRMAR codigo-invalido",
      isGroup: true,
    }), contextoDoHook()), {
    handled: true,
    reply: { text: MENSAGENS.contextoDaConfirmacaoInvalido },
  });
  assert.equal(chamadas, 0);
});

test("confirmacao falha fechada para grupo, autorizacao, identidade e conta", async () => {
  let chamadas = 0;
  const registro = registrar(async () => {
    chamadas += 1;
    return respostaAplicada();
  });
  const hook = registro.hook("inbound_claim");
  for (const evento of [
    eventoPrivado({ isGroup: true }),
    eventoPrivado({ commandAuthorized: false }),
    eventoPrivado({ senderId: "usuario" }),
    eventoPrivado({ conversationId: "-100123" }),
    eventoPrivado({ accountId: "" }),
    eventoPrivado({ accountId: "outra" }),
  ]) {
    assert.deepEqual(await hook(evento, contextoDoHook()), {
      handled: true,
      reply: { text: MENSAGENS.contextoDaConfirmacaoInvalido },
    });
  }
  assert.equal(await hook(
    eventoPrivado({ channel: "discord" }), contextoDoHook()), undefined);
  assert.equal(chamadas, 0);
});

test("conta nomeada exige correspondencia explicita", async () => {
  let chamadas = 0;
  const registro = registrar(async () => {
    chamadas += 1;
    return respostaAplicada();
  }, { identificadorDaContaDoBot: "principal" });
  const hook = registro.hook("inbound_claim");

  assert.match((await hook(eventoPrivado({
    accountId: "principal",
  }), contextoDoHook({ accountId: "principal" }))).reply.text,
  new RegExp(IDENTIFICADOR_DA_OPERACAO));
  for (const accountId of [undefined, "", "default", "secundaria"]) {
    const evento = eventoPrivado({ accountId });
    delete evento.accountId;
    if (accountId !== undefined) evento.accountId = accountId;
    const contexto = contextoDoHook({ accountId });
    if (accountId === undefined) delete contexto.accountId;
    assert.deepEqual(await hook(evento, contexto), {
      handled: true,
      reply: { text: MENSAGENS.contextoDaConfirmacaoInvalido },
    });
  }
  assert.equal(chamadas, 1);
  assert.throws(() => registrar(async () => respostaAplicada(), {
    identificadorDaContaDoBot: "conta com espaco",
  }), /identificadorDaContaDoBot invalida/);
});

test("usa messageId; fallback SHA-256 e estavel sem expor codigo", async () => {
  const corpos = [];
  const registro = registrar(async (_url, opcoes) => {
    corpos.push(JSON.parse(opcoes.body));
    return respostaAplicada();
  });
  const hook = registro.hook("inbound_claim");

  await hook(eventoPrivado(), contextoDoHook());
  for (const content of [
    "/confirmar 2345678A",
    "CONFIRMAR 2345678A",
    "2345678A",
  ]) {
    const evento = eventoPrivado({ content, messageId: undefined });
    const contexto = contextoDoHook({ messageId: undefined });
    await hook(evento, contexto);
  }

  assert.equal(corpos[0].identificadorDoUpdate, "update-10");
  const hashes = corpos.slice(1).map((corpo) => corpo.identificadorDoUpdate);
  assert.equal(new Set(hashes).size, 1);
  assert.match(hashes[0], /^confirmacao-[0-9a-f]{64}$/);
  assert.doesNotMatch(hashes[0], /2345678A/);
});

test("resposta reforcada exige contrato fechado", async () => {
  const registro = registrar(async () => respostaReforcada());
  assert.deepEqual(await registro.comando("confirmar").handler(
    contextoPrivado({ args: "2345678A" })), {
    text: MENSAGENS.confirmacaoReforcada + "/confirmar BCDEFGHJ",
  });

  for (const sobrescritas of [
    { codigo: "OUTRO" },
    { proximoCodigo: "codigo-ruim" },
    { proximaFrase: "CONFIRMAR BCDEFGHJ" },
    { campoExtra: true },
  ]) {
    const invalido = registrar(async () => respostaReforcada(sobrescritas));
    assert.deepEqual(await invalido.comando("confirmar").handler(
      contextoPrivado({ args: "2345678A" })), {
      text: MENSAGENS.confirmacaoIndisponivel,
    });
  }
});

test("2xx malformado ou desconhecido nunca afirma aplicacao", async (t) => {
  const respostas = [
    ["JSON invalido", () => new Response("{", { status: 200 })],
    ["sem recibo", () => new Response(JSON.stringify({
      codigo: "OPERACAO_APLICADA",
    }), { status: 200 })],
    ["estado incorreto", () => respostaAplicada({ estado: "AGUARDANDO" })],
    ["UUID incorreto", () => respostaAplicada({
      identificadorDaOperacao: "operacao-1",
    })],
    ["tipo incorreto", () => respostaAplicada({ tipo: "tipo-invalido" })],
    ["resultado nulo", () => respostaAplicada({ resultado: null })],
    ["chave extra no recibo", () => respostaAplicada({ token: "segredo" })],
    ["chave extra na raiz", () => respostaAplicada({}, { token: "segredo" })],
    ["codigo desconhecido", () => new Response(JSON.stringify({
      codigo: "DESCONHECIDO",
    }), { status: 200 })],
    ["sem corpo", () => new Response(null, { status: 204 })],
  ];

  for (const [nome, criarResposta] of respostas) {
    await t.test(nome, async () => {
      const registro = registrar(async () => criarResposta());
      assert.deepEqual(await registro.comando("confirmar").handler(
        contextoPrivado({ args: "2345678A" })), {
        text: MENSAGENS.confirmacaoIndisponivel,
      });
    });
  }
});

test("informa que o bloco precisa estar em execucao quando o integrador confirma esse motivo", async () => {
  const registro = registrar(async () => new Response(JSON.stringify({
    codigo: "EXECUCAO_DO_BLOCO_NAO_ENCONTRADA",
  }), {
    status: 409,
    headers: { "content-type": "application/json" },
  }));

  assert.deepEqual(await registro.comando("confirmar").handler(
    contextoPrivado({ args: "2345678A" })), {
    text: MENSAGENS.execucaoDoBlocoNaoEncontrada,
  });
});

test("mapeia recusas desconhecidas de confirmacao sem expor o corpo", async () => {
  for (const [status, mensagem] of [
    [400, MENSAGENS.contextoDaConfirmacaoInvalido],
    [404, MENSAGENS.vinculoDaConfirmacaoNaoEncontrado],
    [409, MENSAGENS.confirmacaoRecusada],
    [410, MENSAGENS.confirmacaoRecusada],
    [422, MENSAGENS.confirmacaoRecusada],
    [429, MENSAGENS.confirmacaoLimitada],
    [503, MENSAGENS.confirmacaoIndisponivel],
  ]) {
    let corpoLido = false;
    const registro = registrar(async () => ({
      status,
      json() {
        corpoLido = true;
        return { codigo: "CODIGO_NAO_PERMITIDO" };
      },
      body: { async cancel() {} },
    }));
    assert.deepEqual(await registro.comando("confirmar").handler(
      contextoPrivado({ args: "2345678A" })), { text: mensagem });
    assert.equal(corpoLido, [409, 410, 422].includes(status));
  }
});

test("recusa com corpo malformado continua generica e nao vaza o motivo", async () => {
  const motivo = "detalhe-interno-nao-exposto";
  const logs = [];
  const registro = registrar(async () => new Response(
    JSON.stringify({ codigo: "EXECUCAO_DO_BLOCO_NAO_ENCONTRADA", motivo }),
    { status: 409, headers: { "content-type": "application/json" } },
  ), {}, {
    warn: (mensagem) => logs.push(mensagem),
  });

  const resposta = await registro.comando("confirmar").handler(
    contextoPrivado({ args: "2345678A" }));
  const textoAuditado = `${resposta.text}\n${logs.join("\n")}`;
  assert.deepEqual(resposta, { text: MENSAGENS.confirmacaoRecusada });
  assert.doesNotMatch(textoAuditado, new RegExp(motivo));
});

test("timeout aborta chamada e retorna estado indeterminado", async () => {
  let sinal;
  const registro = registrar(async (_url, opcoes) => {
    sinal = opcoes.signal;
    return new Promise((_resolver, rejeitar) => {
      sinal.addEventListener("abort", () => rejeitar(
        new DOMException("aborted", "AbortError")), { once: true });
    });
  }, { tempoLimiteEmMs: 1000 });

  assert.deepEqual(await registro.comando("confirmar").handler(
    contextoPrivado({ args: "2345678A" })), {
    text: MENSAGENS.confirmacaoTempoEsgotado,
  });
  assert.equal(sinal.aborted, true);
});

test("logs e respostas nunca vazam codigo, token, HMAC ou corpo", async () => {
  const codigo = "2345678A";
  const segredo = "mcp_token_super_secreto";
  const logs = [];
  const registro = registrar(async () => {
    throw new Error(`${segredo} ${codigo} hmac=abc`);
  }, {}, {
    info: (mensagem) => logs.push(mensagem),
    warn: (mensagem) => logs.push(mensagem),
  });

  const resposta = await registro.comando("confirmar").handler(
    contextoPrivado({ args: codigo }));
  const textoAuditado = `${resposta.text}\n${logs.join("\n")}`;
  assert.deepEqual(resposta, { text: MENSAGENS.confirmacaoIndisponivel });
  assert.doesNotMatch(textoAuditado, /2345678A|mcp_token|hmac=abc/i);
});

test("manifesto e configuracao sao fechados e compativeis com 2026.7.1", async () => {
  const manifesto = JSON.parse(await readFile(
    new URL("./openclaw.plugin.json", import.meta.url), "utf8"));
  const pacote = JSON.parse(await readFile(
    new URL("./package.json", import.meta.url), "utf8"));
  const configuracao = JSON.parse(await readFile(
    new URL("../modelos/openclaw.json", import.meta.url), "utf8"));

  assert.equal(manifesto.id, "trilha-aprovacao");
  assert.equal(manifesto.version, "2026.7.1");
  assert.equal(manifesto.configSchema.type, "object");
  assert.equal(manifesto.configSchema.additionalProperties, false);
  assert.equal(
    manifesto.configSchema.properties.identificadorDaContaDoBot.default,
    "default");
  assert.equal(pacote.openclaw.compat.pluginApi, ">=2026.7.1");
  assert.deepEqual(pacote.openclaw.extensions, ["./index.js"]);
  assert.equal(
    configuracao.plugins.entries["trilha-aprovacao"].config
      .identificadorDaContaDoBot,
    "default");
});

test("modelo mantem superficie minima e atalhos nao privilegiados", async () => {
  const configuracao = JSON.parse(await readFile(
    new URL("../modelos/openclaw.json", import.meta.url), "utf8"));
  const nomes = configuracao.channels.telegram.customCommands
    .map((comando) => comando.command);

  assert.deepEqual(nomes, [
    "hoje", "revisoes", "prioridades", "progresso", "operacoes",
    "desconectar", "privacidade",
  ]);
  assert.equal(new Set(nomes).size, nomes.length);
  assert.equal(configuracao.commands.text, true);
  assert.equal(configuracao.tools.deny.includes("group:messaging"), true);
  assert.equal(configuracao.tools.profile, "minimal");
  assert.equal(configuracao.tools.deny.includes("group:runtime"), true);
  assert.equal(configuracao.tools.deny.includes("group:fs"), true);
  assert.equal(configuracao.models.providers.openai.apiKey, undefined);
  assert.equal(configuracao.models.providers.openai.agentRuntime.id, "codex");
  assert.equal(configuracao.agents.defaults.model.primary, "openai/gpt-5.5");
  assert.equal(configuracao.plugins.allow.includes("codex"), true);
  assert.equal(configuracao.plugins.entries.codex.enabled, true);
  assert.equal(
    configuracao.plugins.entries.codex.config.appServer.homeScope, "agent");
});

test("prompt vincula cada conversa suportada a dados atuais do MCP", async () => {
  const prompt = await readFile(
    new URL("../modelos/workspace/AGENTS.md", import.meta.url), "utf8");
  for (const ferramenta of [
    "trilha__obter_agenda_de_estudos_de_hoje",
    "trilha__obter_revisoes_devidas",
    "trilha__obter_prioridades_atuais",
    "trilha__obter_progresso_do_concurso",
    "trilha__obter_historico_recente",
    "trilha__explicar_bloco_de_estudo",
    "trilha__consultar_operacao_assistida",
  ]) {
    assert.match(prompt, new RegExp(ferramenta));
  }
  assert.match(prompt, /Sempre consulte a ferramenta indicada/);
  assert.match(prompt, /Nunca estime esses valores/);
});
