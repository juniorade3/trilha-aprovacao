import assert from "node:assert/strict";
import { createHash, createHmac } from "node:crypto";
import { execFileSync } from "node:child_process";
import {
  chmodSync,
  mkdtempSync,
  mkdirSync,
  readdirSync,
  readFileSync,
  rmSync,
  statSync,
  writeFileSync,
} from "node:fs";
import { createServer } from "node:http";
import { tmpdir } from "node:os";
import path from "node:path";
import { fileURLToPath } from "node:url";
import test from "node:test";
import {
  carregarConfiguracaoDoAmbiente,
  criarServidorDoIntegrador,
} from "../scripts/integrador-de-vinculos.mjs";

const DIRETORIO_DO_MODULO = path.resolve(
  path.dirname(fileURLToPath(import.meta.url)), "..");
const SEGREDO = "segredo-falso-do-gateway-com-mais-de-trinta-e-dois-bytes";
const IDENTIFICADOR_DA_CHAVE = "gateway-teste";
const IDENTIFICADOR_DO_BOT = "700000001";
const IDENTIFICADOR_DO_TELEGRAM = "800000001";
const IDENTIFICADOR_DO_VINCULO = "123e4567-e89b-42d3-a456-426614174000";
const IDENTIFICADOR_DA_OPERACAO = "223e4567-e89b-42d3-a456-426614174001";
const APLICADA_EM = "2026-07-26T18:00:00.123456Z";
const TOKEN_MCP = `mcp_${"A".repeat(43)}`;
const CAMINHO_DA_TROCA =
  "/api/v1/integracoes-confiaveis/telegram/vinculos";
const CAMINHO_DA_CONFIRMACAO =
  "/api/v1/integracoes-confiaveis/telegram/operacoes/confirmacao";

function escutar(servidor) {
  return new Promise((resolver, rejeitar) => {
    servidor.once("error", rejeitar);
    servidor.listen(0, "127.0.0.1", () => {
      servidor.off("error", rejeitar);
      resolver(servidor.address().port);
    });
  });
}

function fechar(servidor) {
  return new Promise((resolver) => servidor.close(() => resolver()));
}

async function lerCorpo(pedido) {
  const partes = [];
  for await (const parte of pedido) partes.push(Buffer.from(parte));
  return Buffer.concat(partes);
}

function assinaturaValida(pedido, corpo) {
  const chave = String(pedido.headers["x-trilha-chave"] ?? "");
  const instante = String(pedido.headers["x-trilha-instante"] ?? "");
  const nonce = String(pedido.headers["x-trilha-nonce"] ?? "");
  const idempotencia = String(
    pedido.headers["x-chave-de-idempotencia"] ?? "");
  const assinatura = String(pedido.headers["x-trilha-assinatura"] ?? "");
  const hashDoCorpo = createHash("sha256").update(corpo).digest("hex");
  const canonico = [
    "TRILHA-HMAC-V1",
    chave,
    instante,
    nonce,
    pedido.method,
    pedido.url,
    hashDoCorpo,
    idempotencia,
  ].join("\n");
  const esperada = createHmac("sha256", SEGREDO)
    .update(canonico).digest("hex");
  return chave === IDENTIFICADOR_DA_CHAVE
    && /^[0-9]{10}$/.test(instante)
    && nonce.length >= 32
    && idempotencia.length > 0
    && assinatura === esperada;
}

function responderJson(resposta, estado, corpo) {
  resposta.writeHead(estado, { "Content-Type": "application/json" });
  resposta.end(JSON.stringify(corpo));
}

function criarBackendFalso({
  estadoDaTroca = 200,
  estadosDoProvisionamento = [200],
  estadoDaConfirmacao = 200,
  codigoDaConfirmacao = "CONFIRMACAO_EXPIRADA_OU_INVALIDA",
  corpoBrutoDaConfirmacao = null,
  respostaDaConfirmacao = {
    operacao: {
      identificador: IDENTIFICADOR_DA_OPERACAO,
      tipo: "REGISTRO_DE_ESTUDO",
      estado: "APLICADA",
      aplicadaEm: APLICADA_EM,
      resultado: {
        tipo: "REGISTRO_DE_ESTUDO",
        dados: { identificador: "estudo-1" },
      },
    },
    exigeNovaConfirmacao: false,
    proximoCodigo: null,
    proximaFrase: null,
  },
} = {}) {
  const chamadas = { troca: 0, provisionamento: 0, confirmacao: 0,
    assinaturasInvalidas: 0, idempotenciasDaConfirmacao: [],
    corposDaConfirmacao: [] };
  const servidor = createServer(async (pedido, resposta) => {
    const corpo = await lerCorpo(pedido);
    if (!assinaturaValida(pedido, corpo)) {
      chamadas.assinaturasInvalidas += 1;
      responderJson(resposta, 401, { codigo: "ASSINATURA_INVALIDA" });
      return;
    }
    if (pedido.method === "POST" && pedido.url === CAMINHO_DA_TROCA) {
      chamadas.troca += 1;
      if (estadoDaTroca !== 200) {
        responderJson(resposta, estadoDaTroca, { codigo: "CONFLITO" });
        return;
      }
      const requisicao = JSON.parse(corpo.toString("utf8"));
      assert.deepEqual(requisicao, {
        codigo: requisicao.codigo,
        identificadorDoBot: Number(IDENTIFICADOR_DO_BOT),
        identificadorDoTelegram: Number(IDENTIFICADOR_DO_TELEGRAM),
        identificadorDoChat: requisicao.identificadorDoChat,
      });
      responderJson(resposta, 200, {
        vinculo: {
          identificador: IDENTIFICADOR_DO_VINCULO,
          canal: "TELEGRAM",
          estado: "ATIVO",
          identificadorDoBot: Number(IDENTIFICADOR_DO_BOT),
          identificadorExterno: Number(IDENTIFICADOR_DO_TELEGRAM),
          identificadorDoChat: requisicao.identificadorDoChat,
        },
        token: TOKEN_MCP,
      });
      return;
    }
    if (pedido.method === "POST" && pedido.url === CAMINHO_DA_CONFIRMACAO) {
      chamadas.confirmacao += 1;
      const requisicao = JSON.parse(corpo.toString("utf8"));
      chamadas.idempotenciasDaConfirmacao.push(
        String(pedido.headers["x-chave-de-idempotencia"]));
      chamadas.corposDaConfirmacao.push(requisicao);
      assert.equal(requisicao.codigo, "2345678A");
      if (estadoDaConfirmacao !== 200) {
        responderJson(resposta, estadoDaConfirmacao,
          { codigo: codigoDaConfirmacao });
        return;
      }
      if (corpoBrutoDaConfirmacao !== null) {
        resposta.writeHead(200, { "Content-Type": "application/json" });
        resposta.end(corpoBrutoDaConfirmacao);
        return;
      }
      responderJson(resposta, 200,
        typeof respostaDaConfirmacao === "function"
          ? respostaDaConfirmacao(requisicao)
          : respostaDaConfirmacao);
      return;
    }
    const caminhoDeProvisionamento =
      `/api/v1/integracoes-confiaveis/telegram/vinculos/${IDENTIFICADOR_DO_VINCULO}/provisionamento`;
    if (pedido.method === "POST" && pedido.url === caminhoDeProvisionamento) {
      chamadas.provisionamento += 1;
      const indice = Math.min(chamadas.provisionamento - 1,
        estadosDoProvisionamento.length - 1);
      const estado = estadosDoProvisionamento[indice];
      if (estado !== 200) {
        responderJson(resposta, estado, { codigo: "INDISPONIVEL" });
        return;
      }
      const requisicao = JSON.parse(corpo.toString("utf8"));
      responderJson(resposta, 200, {
        identificador: IDENTIFICADOR_DO_VINCULO,
        identificadorDoAgente: requisicao.identificadorDoAgente,
        provisionado: true,
      });
      return;
    }
    responderJson(resposta, 404, { codigo: "NAO_ENCONTRADO" });
  });
  return { servidor, chamadas };
}

function criarArquivoSecreto(diretorio, nome, valor) {
  const arquivo = path.join(diretorio, nome);
  writeFileSync(arquivo, `${valor}\n`, { encoding: "utf8", mode: 0o600 });
  chmodSync(arquivo, 0o600);
  return arquivo;
}

async function criarCenario(opcoesDoBackend = {}, opcoesDoAmbiente = {}) {
  const diretorio = mkdtempSync(path.join(tmpdir(), "trilha-integrador-teste-"));
  chmodSync(diretorio, 0o700);
  const estado = path.join(diretorio, "estado");
  const credenciais = path.join(diretorio, "credenciais-mcp");
  execFileSync(path.join(DIRETORIO_DO_MODULO, "scripts/inicializar-estado.sh"), [
    "--diretorio-estado", estado,
    "--diretorio-credenciais-mcp", credenciais,
  ], { stdio: "ignore" });
  const arquivoDoBot = criarArquivoSecreto(
    diretorio, "identificador-bot", IDENTIFICADOR_DO_BOT);
  const arquivoDoSegredo = criarArquivoSecreto(
    diretorio, "segredo-gateway", SEGREDO);
  const backend = criarBackendFalso(opcoesDoBackend);
  const portaDoBackend = await escutar(backend.servidor);
  const configuracao = carregarConfiguracaoDoAmbiente({
    OPENCLAW_DIRETORIO_ESTADO: estado,
    OPENCLAW_DIRETORIO_CREDENCIAIS_MCP: credenciais,
    OPENCLAW_ARQUIVO_IDENTIFICADOR_BOT: arquivoDoBot,
    OPENCLAW_ARQUIVO_SEGREDO_GATEWAY: arquivoDoSegredo,
    OPENCLAW_DIRETORIO_SCRIPTS: path.join(DIRETORIO_DO_MODULO, "scripts"),
    URL_DO_BACKEND_DA_TRILHA: `http://127.0.0.1:${portaDoBackend}`,
    URL_MCP_DA_TRILHA: "http://127.0.0.1:8080/mcp",
    IDENTIFICADOR_DA_CHAVE_DO_GATEWAY_OPENCLAW: IDENTIFICADOR_DA_CHAVE,
    IDENTIFICADOR_DA_CONTA_DO_BOT_OPENCLAW: "default",
    MODELO_OPENAI_DO_ASSISTENTE: "openai/gpt-5.5",
    TEMPO_LIMITE_DO_BACKEND_EM_MS: "5000",
    TEMPO_LIMITE_DOS_SCRIPTS_EM_MS: "10000",
    ...opcoesDoAmbiente,
  });
  const logs = [];
  const integrador = criarServidorDoIntegrador({
    configuracao,
    registrar: (evento) => logs.push(evento),
  });
  const portaDoIntegrador = await escutar(integrador);
  return {
    estado,
    credenciais,
    backend,
    configuracao,
    logs,
    url: `http://127.0.0.1:${portaDoIntegrador}`,
    async encerrar() {
      await fechar(integrador);
      await fechar(backend.servidor);
      rmSync(diretorio, { recursive: true, force: true });
    },
  };
}

function corpoDoPlugin(codigo = "23456789AB", sobrescritas = {}) {
  return {
    versaoDoContrato: "1",
    canal: "TELEGRAM",
    codigoDeVinculo: codigo,
    identificadorDoTelegram: IDENTIFICADOR_DO_TELEGRAM,
    identificadorDoChat: IDENTIFICADOR_DO_TELEGRAM,
    identificadorDaContaDoBot: "default",
    ...sobrescritas,
  };
}

async function vincular(cenario, corpo) {
  const resposta = await fetch(`${cenario.url}/v1/vinculos/telegram`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(corpo),
  });
  return { status: resposta.status, texto: await resposta.text() };
}

async function confirmar(cenario, sobrescritas = {}) {
  const resposta = await fetch(
    `${cenario.url}/v1/operacoes/telegram/confirmacao`, {
      method: "POST", headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ versaoDoContrato: "1", canal: "TELEGRAM",
        codigo: "2345678A", metodo: "TEXTO",
        identificadorDoTelegram: IDENTIFICADOR_DO_TELEGRAM,
        identificadorDoChat: IDENTIFICADOR_DO_TELEGRAM,
        identificadorDaContaDoBot: "default",
        identificadorDoUpdate: "update-100",
        ...sobrescritas }),
    });
  return { status: resposta.status, texto: await resposta.text() };
}

function lerArquivosRecursivamente(diretorio) {
  const conteudos = [];
  for (const entrada of readdirSync(diretorio, { withFileTypes: true })) {
    const arquivo = path.join(diretorio, entrada.name);
    if (entrada.isDirectory()) conteudos.push(lerArquivosRecursivamente(arquivo));
    else if (entrada.isFile() && statSync(arquivo).size <= 1_000_000) {
      conteudos.push(readFileSync(arquivo, "utf8"));
    }
  }
  return conteudos.join("\n");
}

function arquivosDeCredenciais(diretorio) {
  return readdirSync(diretorio).filter((nome) =>
    /^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}\.json$/i
      .test(nome));
}

test("vincula uma vez, repete por recibo e nao devolve segredos", async () => {
  const cenario = await criarCenario();
  try {
    const [primeira, concorrente] = await Promise.all([
      vincular(cenario, corpoDoPlugin()),
      vincular(cenario, corpoDoPlugin()),
    ]);
    const repetida = await vincular(cenario, corpoDoPlugin());
    assert.equal(primeira.status, 200);
    assert.equal(concorrente.status, 200);
    assert.equal(repetida.status, 200);
    assert.deepEqual(JSON.parse(primeira.texto), { codigo: "VINCULO_CONCLUIDO" });
    assert.equal(cenario.backend.chamadas.troca, 1);
    assert.equal(cenario.backend.chamadas.provisionamento, 1);
    assert.equal(cenario.backend.chamadas.assinaturasInvalidas, 0);

    const configuracao = JSON.parse(readFileSync(
      path.join(cenario.estado, "openclaw.json"), "utf8"));
    assert.equal(configuracao.agents.list.length, 1);
    assert.equal(configuracao.bindings.length, 1);
    const credencial = path.join(
      cenario.credenciais, `${IDENTIFICADOR_DO_VINCULO}.json`);
    assert.equal(statSync(credencial).mode & 0o777, 0o600);
    assert.equal(JSON.parse(readFileSync(credencial, "utf8")).tokenMcp, TOKEN_MCP);

    const estadoCompleto = lerArquivosRecursivamente(cenario.estado);
    for (const segredo of ["23456789AB", SEGREDO, TOKEN_MCP]) {
      assert.equal(estadoCompleto.includes(segredo), false);
      assert.equal(primeira.texto.includes(segredo), false);
    }

    const logsDaNovaInstancia = [];
    const novaInstancia = criarServidorDoIntegrador({
      configuracao: cenario.configuracao,
      registrar: (evento) => logsDaNovaInstancia.push(evento),
    });
    const portaDaNovaInstancia = await escutar(novaInstancia);
    try {
      const respostaAposReinicio = await vincular({
        url: `http://127.0.0.1:${portaDaNovaInstancia}`,
      }, corpoDoPlugin());
      assert.equal(respostaAposReinicio.status, 200);
      assert.equal(cenario.backend.chamadas.troca, 1);
      assert.equal(logsDaNovaInstancia[0].identificadorDoVinculo,
        IDENTIFICADOR_DO_VINCULO);
    } finally {
      await fechar(novaInstancia);
    }
  } finally {
    await cenario.encerrar();
  }
});

test("retoma o registro no backend sem trocar novamente o codigo", async () => {
  const cenario = await criarCenario({ estadosDoProvisionamento: [503, 200] });
  try {
    const primeira = await vincular(cenario, corpoDoPlugin("CDEFGHJKLM"));
    assert.equal(primeira.status, 503);
    const segunda = await vincular(cenario, corpoDoPlugin("CDEFGHJKLM"));
    assert.equal(segunda.status, 200);
    assert.equal(cenario.backend.chamadas.troca, 1);
    assert.equal(cenario.backend.chamadas.provisionamento, 2);
    const configuracao = JSON.parse(readFileSync(
      path.join(cenario.estado, "openclaw.json"), "utf8"));
    assert.equal(configuracao.agents.list.length, 1);
    assert.equal(arquivosDeCredenciais(cenario.credenciais).length, 1);
  } finally {
    await cenario.encerrar();
  }
});

test("confirma pelo vinculo provisionado sem expor o segredo do gateway", async () => {
  const cenario = await criarCenario();
  try {
    assert.equal((await vincular(cenario, corpoDoPlugin())).status, 200);
    const resposta = await confirmar(cenario);
    assert.equal(resposta.status, 200);
    assert.deepEqual(JSON.parse(resposta.texto), {
      codigo: "OPERACAO_APLICADA",
      recibo: {
        identificadorDaOperacao: IDENTIFICADOR_DA_OPERACAO,
        tipo: "REGISTRO_DE_ESTUDO",
        estado: "APLICADA",
        aplicadaEm: APLICADA_EM,
        resultado: {
          tipo: "REGISTRO_DE_ESTUDO",
          dados: { identificador: "estudo-1" },
        },
      },
    });
    assert.equal(cenario.backend.chamadas.confirmacao, 1);
    assert.equal(cenario.backend.chamadas.assinaturasInvalidas, 0);
    assert.equal(cenario.backend.chamadas.corposDaConfirmacao[0]
      .identificadorDaSessao, `sessao:${IDENTIFICADOR_DO_VINCULO}`);
  } finally {
    await cenario.encerrar();
  }
});

test("rejeita 2xx sem recibo aplicado estruturalmente valido", async () => {
  const aplicada = {
    operacao: {
      identificador: IDENTIFICADOR_DA_OPERACAO,
      tipo: "REGISTRO_DE_ESTUDO",
      estado: "APLICADA",
      aplicadaEm: APLICADA_EM,
      resultado: {
        tipo: "REGISTRO_DE_ESTUDO",
        dados: { identificador: "estudo-1" },
      },
    },
    exigeNovaConfirmacao: false,
    proximoCodigo: null,
    proximaFrase: null,
  };
  const respostasInvalidas = [
    {
      operacao: {
        identificador: IDENTIFICADOR_DA_OPERACAO,
        tipo: "REGISTRO_DE_ESTUDO",
        estado: "AGUARDANDO_CONFIRMACAO",
        aplicadaEm: null,
        resultado: null,
      },
      exigeNovaConfirmacao: false,
      proximoCodigo: null,
      proximaFrase: null,
    },
    { ...aplicada, operacao: { ...aplicada.operacao, aplicadaEm: null } },
    { ...aplicada, operacao: {
      ...aplicada.operacao, aplicadaEm: "2026-07-26 sem fuso",
    } },
    { ...aplicada, operacao: { ...aplicada.operacao, resultado: {
      tipo: "OUTRA_OPERACAO", dados: {},
    } } },
    { ...aplicada, operacao: { ...aplicada.operacao, resultado: {
      tipo: "REGISTRO_DE_ESTUDO",
    } } },
  ];
  let indice = 0;
  const cenario = await criarCenario({
    respostaDaConfirmacao: () => respostasInvalidas[indice++],
  });
  try {
    assert.equal((await vincular(cenario, corpoDoPlugin())).status, 200);
    for (let atual = 0; atual < respostasInvalidas.length; atual += 1) {
      const resposta = await confirmar(cenario, {
        identificadorDoUpdate: `update-invalido-${atual}`,
      });
      assert.equal(resposta.status, 503);
      assert.deepEqual(JSON.parse(resposta.texto),
        { codigo: "RESPOSTA_DO_BACKEND_INVALIDA" });
    }
    assert.equal(cenario.logs.some((item) =>
      item.etapa === "VALIDACAO_DA_RESPOSTA_DO_BACKEND"), true);
  } finally {
    await cenario.encerrar();
  }

  const jsonInvalido = await criarCenario({
    corpoBrutoDaConfirmacao: "{\"operacao\":",
  });
  try {
    assert.equal((await vincular(jsonInvalido, corpoDoPlugin())).status, 200);
    const resposta = await confirmar(jsonInvalido);
    assert.equal(resposta.status, 503);
    assert.deepEqual(JSON.parse(resposta.texto),
      { codigo: "RESPOSTA_DO_BACKEND_INVALIDA" });
  } finally {
    await jsonInvalido.encerrar();
  }
});

test("aceita somente segunda confirmacao coerente", async () => {
  const respostaReforcada = {
    operacao: {
      identificador: IDENTIFICADOR_DA_OPERACAO,
      tipo: "ATIVACAO_DO_CONCURSO",
      estado: "AGUARDANDO_CONFIRMACAO",
      resultado: null,
    },
    exigeNovaConfirmacao: true,
    proximoCodigo: "BCDEFGHJ",
    proximaFrase: "/confirmar BCDEFGHJ",
  };
  const cenario = await criarCenario({
    respostaDaConfirmacao: respostaReforcada,
  });
  try {
    assert.equal((await vincular(cenario, corpoDoPlugin())).status, 200);
    const resposta = await confirmar(cenario);
    assert.equal(resposta.status, 200);
    assert.deepEqual(JSON.parse(resposta.texto), {
      codigo: "NOVA_CONFIRMACAO_EXIGIDA",
      proximoCodigo: "BCDEFGHJ",
      proximaFrase: "/confirmar BCDEFGHJ",
    });
  } finally {
    await cenario.encerrar();
  }

  const incoerente = await criarCenario({
    respostaDaConfirmacao: {
      ...respostaReforcada,
      proximaFrase: "/confirmar CDEFGHJK",
    },
  });
  try {
    assert.equal((await vincular(incoerente, corpoDoPlugin())).status, 200);
    assert.equal((await confirmar(incoerente)).status, 503);
  } finally {
    await incoerente.encerrar();
  }
});

test("aceita conta nomeada e chat privado diferente do Telegram", async () => {
  const chat = "900000001";
  const cenario = await criarCenario({}, {
    IDENTIFICADOR_DA_CONTA_DO_BOT_OPENCLAW: "principal",
  });
  try {
    const contaErrada = await vincular(cenario, corpoDoPlugin("23456789AB", {
      identificadorDoChat: chat,
    }));
    assert.equal(contaErrada.status, 400);

    const vinculada = await vincular(cenario, corpoDoPlugin("23456789AB", {
      identificadorDoChat: chat,
      identificadorDaContaDoBot: "principal",
    }));
    assert.equal(vinculada.status, 200);

    const metadados = JSON.parse(readFileSync(path.join(cenario.estado,
      "provisionamentos", `${IDENTIFICADOR_DO_VINCULO}.json`), "utf8"));
    assert.equal(metadados.versao, 3);
    assert.equal(metadados.identificadorDaContaDoBot, "principal");
    assert.equal(metadados.identificadorDoTelegram, IDENTIFICADOR_DO_TELEGRAM);
    assert.equal(metadados.identificadorDoChat, chat);
    assert.equal(typeof metadados.registradoNoBackendEm, "string");

    const configuracao = JSON.parse(readFileSync(
      path.join(cenario.estado, "openclaw.json"), "utf8"));
    assert.equal(configuracao.bindings[0].match.accountId, "principal");
    assert.equal(configuracao.bindings[0].match.peer.id, chat);
    assert.deepEqual(configuracao.channels.telegram.allowFrom,
      [IDENTIFICADOR_DO_TELEGRAM]);
    assert.equal(configuracao.channels.telegram.defaultAccount, "principal");
    assert.deepEqual(Object.keys(configuracao.channels.telegram.accounts),
      ["principal"]);
    assert.deepEqual(
      configuracao.channels.telegram.accounts.principal.botToken,
      {
        source: "file",
        provider: "arquivo",
        id: "/telegram/tokenDoBot",
      });
    assert.equal(configuracao.channels.telegram.botToken, undefined);
    assert.equal(configuracao.plugins.entries["trilha-aprovacao"].config
      .identificadorDaContaDoBot, "principal");

    const confirmada = await confirmar(cenario, {
      identificadorDoChat: chat,
      identificadorDaContaDoBot: "principal",
    });
    assert.equal(confirmada.status, 200);
    assert.equal(cenario.backend.chamadas.corposDaConfirmacao[0]
      .identificadorDoChat, Number(chat));
  } finally {
    await cenario.encerrar();
  }
});

test("exige um unico provisionamento ativo valido e registrado", async () => {
  const inexistente = await criarCenario();
  try {
    const resposta = await confirmar(inexistente);
    assert.equal(resposta.status, 404);
    assert.deepEqual(JSON.parse(resposta.texto),
      { codigo: "VINCULO_NAO_ENCONTRADO" });
    assert.equal(inexistente.backend.chamadas.confirmacao, 0);
    assert.equal(inexistente.logs.at(-1).etapa, "VINCULO_NAO_LOCALIZADO");
  } finally {
    await inexistente.encerrar();
  }

  const cenario = await criarCenario();
  try {
    assert.equal((await vincular(cenario, corpoDoPlugin())).status, 200);
    const arquivo = path.join(cenario.estado, "provisionamentos",
      `${IDENTIFICADOR_DO_VINCULO}.json`);
    const original = JSON.parse(readFileSync(arquivo, "utf8"));
    const divergencias = [
      ["BOT_DIVERGENTE", {
        ...original, identificadorDoBot: "700000002",
      }],
      ["CONTA_DIVERGENTE", {
        ...original, identificadorDaContaDoBot: "secundaria",
      }],
      ["TELEGRAM_DIVERGENTE", {
        ...original, identificadorDoTelegram: "800000002",
      }],
      ["CHAT_DIVERGENTE", {
        ...original, identificadorDoChat: "900000002",
      }],
    ];
    for (const [etapa, metadados] of divergencias) {
      writeFileSync(arquivo, `${JSON.stringify(metadados)}\n`, { mode: 0o600 });
      chmodSync(arquivo, 0o600);
      const divergente = await confirmar(cenario, {
        identificadorDoUpdate: `update-${etapa.toLowerCase()}`,
      });
      assert.equal(divergente.status, 404);
      assert.deepEqual(JSON.parse(divergente.texto),
        { codigo: "VINCULO_NAO_ENCONTRADO" });
      assert.equal(cenario.logs.at(-1).etapa, etapa);
    }
    writeFileSync(arquivo, `${JSON.stringify(original)}\n`, { mode: 0o600 });
    chmodSync(arquivo, 0o600);
    const logsDeDivergencia = JSON.stringify(cenario.logs);
    assert.equal(logsDeDivergencia.includes(IDENTIFICADOR_DO_TELEGRAM), false);
    assert.equal(logsDeDivergencia.includes("900000002"), false);

    const outroVinculo = "323e4567-e89b-42d3-a456-426614174002";
    const duplicado = {
      ...original,
      identificadorDoVinculo: outroVinculo,
      identificadorDoAgente: "trilha_duplicado",
      identificadorDaSessao: `sessao:${outroVinculo}`,
    };
    const arquivoDuplicado = path.join(cenario.estado, "provisionamentos",
      `${outroVinculo}.json`);
    writeFileSync(arquivoDuplicado, `${JSON.stringify(duplicado)}\n`, {
      mode: 0o600,
    });
    chmodSync(arquivoDuplicado, 0o600);

    const resposta = await confirmar(cenario);
    assert.equal(resposta.status, 503);
    assert.deepEqual(JSON.parse(resposta.texto),
      { codigo: "ESTADO_LOCAL_INCONSISTENTE" });
    assert.equal(cenario.backend.chamadas.confirmacao, 0);
    assert.equal(cenario.logs.some((item) =>
      item.etapa === "PROVISIONAMENTOS_ATIVOS_DUPLICADOS"), true);
  } finally {
    await cenario.encerrar();
  }

  const naoRegistrado = await criarCenario();
  try {
    assert.equal((await vincular(naoRegistrado, corpoDoPlugin())).status, 200);
    const arquivo = path.join(naoRegistrado.estado, "provisionamentos",
      `${IDENTIFICADOR_DO_VINCULO}.json`);
    const metadados = JSON.parse(readFileSync(arquivo, "utf8"));
    delete metadados.registradoNoBackendEm;
    writeFileSync(arquivo, `${JSON.stringify(metadados)}\n`, { mode: 0o600 });
    chmodSync(arquivo, 0o600);
    assert.equal((await confirmar(naoRegistrado)).status, 503);
    assert.equal(naoRegistrado.backend.chamadas.confirmacao, 0);
    assert.equal(naoRegistrado.logs.at(-1).etapa, "VINCULO_NAO_LOCALIZADO");
  } finally {
    await naoRegistrado.encerrar();
  }
});

test("rejeita credencial local divergente antes de confirmar", async () => {
  const cenario = await criarCenario();
  try {
    assert.equal((await vincular(cenario, corpoDoPlugin())).status, 200);
    const arquivo = path.join(
      cenario.credenciais, `${IDENTIFICADOR_DO_VINCULO}.json`);
    const credencial = JSON.parse(readFileSync(arquivo, "utf8"));
    credencial.identificadorDaSessao = "sessao:divergente";
    writeFileSync(arquivo, `${JSON.stringify(credencial)}\n`, { mode: 0o600 });
    chmodSync(arquivo, 0o600);

    const resposta = await confirmar(cenario);
    assert.equal(resposta.status, 503);
    assert.deepEqual(JSON.parse(resposta.texto),
      { codigo: "ESTADO_LOCAL_INCONSISTENTE" });
    assert.equal(cenario.backend.chamadas.confirmacao, 0);
    assert.equal(cenario.logs.at(-1).etapa, "SESSAO_DIVERGENTE");

    credencial.identificadorDaSessao = `sessao:${IDENTIFICADOR_DO_VINCULO}`;
    credencial.tokenMcp = `mcp_${"Z".repeat(43)}`;
    writeFileSync(arquivo, `${JSON.stringify(credencial)}\n`, { mode: 0o600 });
    chmodSync(arquivo, 0o600);
    assert.equal((await confirmar(cenario)).status, 503);
    assert.equal(cenario.backend.chamadas.confirmacao, 0);
    assert.equal(cenario.logs.at(-1).etapa, "CREDENCIAL_DIVERGENTE");

    credencial.tokenMcp = TOKEN_MCP;
    writeFileSync(arquivo, `${JSON.stringify(credencial)}\n`, { mode: 0o600 });
    chmodSync(arquivo, 0o600);
    const arquivoDaConfiguracao = path.join(cenario.estado, "openclaw.json");
    const configuracao = JSON.parse(readFileSync(
      arquivoDaConfiguracao, "utf8"));
    configuracao.bindings[0].comment = "binding:divergente";
    writeFileSync(arquivoDaConfiguracao, `${JSON.stringify(configuracao)}\n`,
      { mode: 0o600 });
    chmodSync(arquivoDaConfiguracao, 0o600);
    assert.equal((await confirmar(cenario)).status, 503);
    assert.equal(cenario.backend.chamadas.confirmacao, 0);
    assert.equal(cenario.logs.at(-1).etapa, "BINDING_DIVERGENTE");

    const logs = JSON.stringify(cenario.logs);
    assert.equal(logs.includes(IDENTIFICADOR_DO_TELEGRAM), false);
    assert.equal(logs.includes(TOKEN_MCP), false);
  } finally {
    await cenario.encerrar();
  }
});

test("mantem idempotencia por bot, conta, conversa e update e logs sanitizados", async () => {
  const cenario = await criarCenario();
  try {
    assert.equal((await vincular(cenario, corpoDoPlugin())).status, 200);
    assert.equal((await confirmar(cenario)).status, 200);
    assert.equal((await confirmar(cenario)).status, 200);
    assert.equal((await confirmar(cenario, {
      identificadorDoUpdate: "update-101",
    })).status, 200);

    const canonico = JSON.stringify({
      canal: "TELEGRAM",
      bot: IDENTIFICADOR_DO_BOT,
      conta: "default",
      telegram: IDENTIFICADOR_DO_TELEGRAM,
      chat: IDENTIFICADOR_DO_TELEGRAM,
      update: "update-100",
    });
    const esperada = `confirmacao-${createHash("sha256")
      .update(canonico).digest("hex")}`;
    assert.equal(cenario.backend.chamadas.idempotenciasDaConfirmacao[0],
      esperada);
    assert.equal(cenario.backend.chamadas.idempotenciasDaConfirmacao[1],
      esperada);
    assert.notEqual(cenario.backend.chamadas.idempotenciasDaConfirmacao[2],
      esperada);

    const logs = JSON.stringify(cenario.logs);
    for (const segredo of ["23456789AB", "2345678A", TOKEN_MCP, SEGREDO]) {
      assert.equal(logs.includes(segredo), false);
    }
    assert.equal(cenario.logs.some((item) =>
      item.etapa === "CONFIRMACAO_CONCLUIDA"
      && /^[0-9a-f]{64}$/.test(item.identidadeHash)
      && item.identificadorDoVinculo === IDENTIFICADOR_DO_VINCULO
      && item.identificadorDaOperacao === IDENTIFICADOR_DA_OPERACAO
      && item.tipoDaOperacao === "REGISTRO_DE_ESTUDO"
      && item.estadoDaOperacao === "APLICADA"), true);
  } finally {
    await cenario.encerrar();
  }
});

test("preserva somente o codigo seguro da recusa de confirmacao", async () => {
  const cenario = await criarCenario({
    estadoDaConfirmacao: 409,
    codigoDaConfirmacao: "PREVIA_DE_AUTOMACAO_DESATUALIZADA",
  });
  try {
    assert.equal((await vincular(cenario, corpoDoPlugin())).status, 200);
    const resposta = await confirmar(cenario);
    assert.equal(resposta.status, 409);
    assert.deepEqual(JSON.parse(resposta.texto),
      { codigo: "PREVIA_DE_AUTOMACAO_DESATUALIZADA" });
  } finally {
    await cenario.encerrar();
  }
});

test("propaga conflito sem criar estado ou credencial", async () => {
  const cenario = await criarCenario({ estadoDaTroca: 409 });
  try {
    const resposta = await vincular(cenario, corpoDoPlugin("NPQRSTUVWX"));
    assert.equal(resposta.status, 409);
    assert.deepEqual(JSON.parse(resposta.texto), { codigo: "VINCULO_EM_CONFLITO" });
    assert.equal(cenario.backend.chamadas.troca, 1);
    assert.equal(cenario.backend.chamadas.provisionamento, 0);
    assert.equal(arquivosDeCredenciais(cenario.credenciais).length, 0);
    assert.equal(readdirSync(path.join(cenario.estado, "provisionamentos")).length, 0);
  } finally {
    await cenario.encerrar();
  }
});

test("rejeita schema aberto e limita tentativas", async () => {
  const cenario = await criarCenario(
    { estadoDaTroca: 409 },
    { LIMITE_DE_VINCULOS_POR_TELEGRAM: "2" });
  try {
    const aberto = await vincular(cenario, corpoDoPlugin("YZ23456789", {
      campoNaoPermitido: true,
    }));
    assert.equal(aberto.status, 400);
    assert.equal(cenario.backend.chamadas.troca, 0);

    assert.equal((await vincular(cenario, corpoDoPlugin("YZ23456789"))).status, 409);
    assert.equal((await vincular(cenario, corpoDoPlugin("23456789AC"))).status, 409);
    const limitada = await vincular(cenario, corpoDoPlugin("23456789AD"));
    assert.equal(limitada.status, 429);
    assert.equal(cenario.backend.chamadas.troca, 2);
  } finally {
    await cenario.encerrar();
  }
});
