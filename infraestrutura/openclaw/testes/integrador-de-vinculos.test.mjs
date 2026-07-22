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
} = {}) {
  const chamadas = { troca: 0, provisionamento: 0, confirmacao: 0,
    assinaturasInvalidas: 0 };
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
        identificadorDoChat: Number(IDENTIFICADOR_DO_TELEGRAM),
      });
      responderJson(resposta, 200, {
        vinculo: {
          identificador: IDENTIFICADOR_DO_VINCULO,
          canal: "TELEGRAM",
          estado: "ATIVO",
          identificadorDoBot: Number(IDENTIFICADOR_DO_BOT),
          identificadorExterno: Number(IDENTIFICADOR_DO_TELEGRAM),
          identificadorDoChat: Number(IDENTIFICADOR_DO_TELEGRAM),
        },
        token: TOKEN_MCP,
      });
      return;
    }
    if (pedido.method === "POST" && pedido.url === CAMINHO_DA_CONFIRMACAO) {
      chamadas.confirmacao += 1;
      const requisicao = JSON.parse(corpo.toString("utf8"));
      assert.equal(requisicao.codigo, "2345678A");
      assert.equal(requisicao.identificadorDaSessao,
        `sessao:${IDENTIFICADOR_DO_VINCULO}`);
      responderJson(resposta, 200, { estado: "APLICADA" });
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
    MODELO_OPENAI_DO_ASSISTENTE: "openai/gpt-5.6",
    TEMPO_LIMITE_DO_BACKEND_EM_MS: "5000",
    TEMPO_LIMITE_DOS_SCRIPTS_EM_MS: "10000",
    ...opcoesDoAmbiente,
  });
  const integrador = criarServidorDoIntegrador({ configuracao });
  const portaDoIntegrador = await escutar(integrador);
  return {
    estado,
    credenciais,
    backend,
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

async function confirmar(cenario) {
  const resposta = await fetch(
    `${cenario.url}/v1/operacoes/telegram/confirmacao`, {
      method: "POST", headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ versaoDoContrato: "1", canal: "TELEGRAM",
        codigo: "2345678A", metodo: "TEXTO",
        identificadorDoTelegram: IDENTIFICADOR_DO_TELEGRAM,
        identificadorDoChat: IDENTIFICADOR_DO_TELEGRAM,
        identificadorDaContaDoBot: "default",
        identificadorDoUpdate: "update-100" }),
    });
  return { status: resposta.status, texto: await resposta.text() };
}

function lerArquivosRecursivamente(diretorio) {
  const conteudos = [];
  for (const entrada of readdirSync(diretorio, { withFileTypes: true })) {
    const arquivo = path.join(diretorio, entrada.name);
    if (entrada.isDirectory()) conteudos.push(...lerArquivosRecursivamente(arquivo));
    else if (entrada.isFile() && statSync(arquivo).size <= 1_000_000) {
      conteudos.push(readFileSync(arquivo, "utf8"));
    }
  }
  return conteudos.join("\n");
}

test("vincula uma vez, repete por recibo e nao devolve segredos", async () => {
  const cenario = await criarCenario();
  try {
    const primeira = await vincular(cenario, corpoDoPlugin());
    const repetida = await vincular(cenario, corpoDoPlugin());
    assert.equal(primeira.status, 200);
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
    assert.equal(readdirSync(cenario.credenciais).length, 1);
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
    assert.deepEqual(JSON.parse(resposta.texto), { codigo: "OPERACAO_APLICADA" });
    assert.equal(cenario.backend.chamadas.confirmacao, 1);
    assert.equal(cenario.backend.chamadas.assinaturasInvalidas, 0);
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
    assert.equal(readdirSync(cenario.credenciais).length, 0);
    assert.equal(readdirSync(path.join(cenario.estado, "provisionamentos")).length, 0);
  } finally {
    await cenario.encerrar();
  }
});

test("rejeita schema aberto, conversa nao privada e limita tentativas", async () => {
  const cenario = await criarCenario(
    { estadoDaTroca: 409 },
    { LIMITE_DE_VINCULOS_POR_TELEGRAM: "2" });
  try {
    const aberto = await vincular(cenario, corpoDoPlugin("YZ23456789", {
      campoNaoPermitido: true,
    }));
    const grupo = await vincular(cenario, corpoDoPlugin("YZ23456789", {
      identificadorDoChat: "900000001",
    }));
    assert.equal(aberto.status, 400);
    assert.equal(grupo.status, 400);
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
