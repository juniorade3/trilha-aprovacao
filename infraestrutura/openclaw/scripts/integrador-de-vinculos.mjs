#!/usr/bin/env node

import { createHash, createHmac, randomBytes, randomUUID } from "node:crypto";
import {
  chmodSync,
  lstatSync,
  mkdirSync,
  mkdtempSync,
  readFileSync,
  readdirSync,
  renameSync,
  rmSync,
  statSync,
  writeFileSync,
} from "node:fs";
import { createServer } from "node:http";
import { tmpdir } from "node:os";
import path from "node:path";
import { fileURLToPath, pathToFileURL } from "node:url";
import { spawn } from "node:child_process";

const FORMATO_DO_CODIGO = /^[23456789A-HJ-NP-Z]{10}$/;
const FORMATO_DO_UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
const FORMATO_DO_TOKEN = /^mcp_[A-Za-z0-9_-]{43}$/;
const FORMATO_DO_IDENTIFICADOR_NUMERICO = /^[1-9][0-9]{0,18}$/;
const FORMATO_DA_CONTA = /^[A-Za-z0-9._:-]{1,100}$/;
const FORMATO_DA_CHAVE = /^[A-Za-z0-9._-]{1,80}$/;
const CHAVES_DA_REQUISICAO = [
  "canal",
  "codigoDeVinculo",
  "identificadorDaContaDoBot",
  "identificadorDoChat",
  "identificadorDoTelegram",
  "versaoDoContrato",
].sort();
const LIMITE_DO_CORPO = 4_096;
const LIMITE_DA_RESPOSTA_DO_BACKEND = 65_536;
const CAMINHO_DA_TROCA = "/api/v1/integracoes-confiaveis/telegram/vinculos";
const CAMINHO_DA_CONFIRMACAO =
  "/api/v1/integracoes-confiaveis/telegram/operacoes/confirmacao";
const CAMINHO_LOCAL_DA_CONFIRMACAO = "/v1/operacoes/telegram/confirmacao";
const FORMATO_DO_CODIGO_DE_CONFIRMACAO = /^[23456789A-HJ-NP-Z]{8}$/;

class ErroDoIntegrador extends Error {
  constructor(codigo, estadoHttp) {
    super(codigo);
    this.codigo = codigo;
    this.estadoHttp = estadoHttp;
  }
}

function falharConfiguracao(mensagem) {
  throw new Error(`CONFIGURACAO_DO_INTEGRADOR_INVALIDA: ${mensagem}`);
}

function inteiroDoAmbiente(valor, padrao, minimo, maximo, nome) {
  const texto = valor === undefined || valor === "" ? String(padrao) : valor;
  if (!/^[0-9]+$/.test(texto)) falharConfiguracao(`${nome} invalido.`);
  const numero = Number(texto);
  if (!Number.isSafeInteger(numero) || numero < minimo || numero > maximo) {
    falharConfiguracao(`${nome} deve ficar entre ${minimo} e ${maximo}.`);
  }
  return numero;
}

function arquivoSecreto(caminho, nome, validar) {
  if (!caminho) falharConfiguracao(`${nome} nao informado.`);
  const estado = lstatSync(caminho);
  if (!estado.isFile() || estado.isSymbolicLink()
      || (estado.mode & 0o777) !== 0o600) {
    falharConfiguracao(`${nome} deve ser arquivo regular 0600.`);
  }
  const valor = readFileSync(caminho, "utf8").replace(/[\r\n]+$/u, "");
  if (!validar(valor)) falharConfiguracao(`${nome} possui formato invalido.`);
  return valor;
}

function diretorioSeguro(caminho, nome) {
  if (!caminho) falharConfiguracao(`${nome} nao informado.`);
  const estado = lstatSync(caminho);
  if (!estado.isDirectory() || estado.isSymbolicLink()
      || (estado.mode & 0o777) !== 0o700) {
    falharConfiguracao(`${nome} deve ser diretorio regular 0700.`);
  }
  return caminho;
}

function origemHttp(valor, nome) {
  let url;
  try {
    url = new URL(valor);
  } catch {
    falharConfiguracao(`${nome} invalida.`);
  }
  if (!["http:", "https:"].includes(url.protocol) || url.username
      || url.password || url.search || url.hash
      || (url.pathname !== "/" && url.pathname !== "")) {
    falharConfiguracao(`${nome} deve conter somente esquema, host e porta.`);
  }
  return url.origin;
}

function urlMcp(valor) {
  let url;
  try {
    url = new URL(valor);
  } catch {
    falharConfiguracao("URL_MCP_DA_TRILHA invalida.");
  }
  if (!["http:", "https:"].includes(url.protocol) || url.username
      || url.password || url.search || url.hash
      || !/\/mcp\/?$/.test(url.pathname)) {
    falharConfiguracao("URL_MCP_DA_TRILHA deve terminar em /mcp.");
  }
  return url.toString();
}

export function carregarConfiguracaoDoAmbiente(ambiente = process.env) {
  const diretorioDeEstado = diretorioSeguro(
    ambiente.OPENCLAW_DIRETORIO_ESTADO, "OPENCLAW_DIRETORIO_ESTADO");
  const diretorioDeCredenciais = diretorioSeguro(
    ambiente.OPENCLAW_DIRETORIO_CREDENCIAIS_MCP,
    "OPENCLAW_DIRETORIO_CREDENCIAIS_MCP");
  const identificadorDoBot = arquivoSecreto(
    ambiente.OPENCLAW_ARQUIVO_IDENTIFICADOR_BOT,
    "OPENCLAW_ARQUIVO_IDENTIFICADOR_BOT",
    (valor) => FORMATO_DO_IDENTIFICADOR_NUMERICO.test(valor)
      && Number.isSafeInteger(Number(valor)));
  const segredoDoGateway = arquivoSecreto(
    ambiente.OPENCLAW_ARQUIVO_SEGREDO_GATEWAY,
    "OPENCLAW_ARQUIVO_SEGREDO_GATEWAY",
    (valor) => Buffer.byteLength(valor, "utf8") >= 32
      && Buffer.byteLength(valor, "utf8") <= 4_096);
  const arquivoDoSegredoGateway = path.resolve(
    ambiente.OPENCLAW_ARQUIVO_SEGREDO_GATEWAY);
  const identificadorDaChave =
    ambiente.IDENTIFICADOR_DA_CHAVE_DO_GATEWAY_OPENCLAW ?? "gateway-openclaw";
  if (!FORMATO_DA_CHAVE.test(identificadorDaChave)) {
    falharConfiguracao("IDENTIFICADOR_DA_CHAVE_DO_GATEWAY_OPENCLAW invalido.");
  }
  const identificadorDaContaDoBot =
    ambiente.IDENTIFICADOR_DA_CONTA_DO_BOT_OPENCLAW ?? "default";
  if (!FORMATO_DA_CONTA.test(identificadorDaContaDoBot)) {
    falharConfiguracao("IDENTIFICADOR_DA_CONTA_DO_BOT_OPENCLAW invalido.");
  }
  const modelo = ambiente.MODELO_OPENAI_DO_ASSISTENTE ?? "openai/gpt-5.6";
  if (!/^openai\/[A-Za-z0-9._-]+$/.test(modelo)) {
    falharConfiguracao("MODELO_OPENAI_DO_ASSISTENTE invalido.");
  }
  const diretorioDosScripts = ambiente.OPENCLAW_DIRETORIO_SCRIPTS
    ?? path.dirname(fileURLToPath(import.meta.url));
  const arquivoDeProvisionamento = path.join(
    diretorioDosScripts, "provisionar-vinculo.sh");
  const arquivoDeRegistro = path.join(
    diretorioDosScripts, "registrar-provisionamento.sh");
  for (const arquivo of [arquivoDeProvisionamento, arquivoDeRegistro]) {
    const estado = statSync(arquivo);
    if (!estado.isFile()) falharConfiguracao("script confiavel ausente.");
  }
  const diretorioDosRecibos = path.join(diretorioDeEstado, "integrador");
  mkdirSync(diretorioDosRecibos, { recursive: true, mode: 0o700 });
  chmodSync(diretorioDosRecibos, 0o700);
  diretorioSeguro(diretorioDosRecibos, "diretorio de recibos");

  return Object.freeze({
    diretorioDeEstado,
    diretorioDeCredenciais,
    diretorioDosRecibos,
    identificadorDoBot,
    identificadorDaContaDoBot,
    segredoDoGateway,
    arquivoDoSegredoGateway,
    identificadorDaChave,
    urlDoBackend: origemHttp(
      ambiente.URL_DO_BACKEND_DA_TRILHA ?? "http://host.docker.internal:8080",
      "URL_DO_BACKEND_DA_TRILHA"),
    urlMcp: urlMcp(
      ambiente.URL_MCP_DA_TRILHA ?? "http://host.docker.internal:8080/mcp"),
    modelo,
    arquivoDeProvisionamento,
    arquivoDeRegistro,
    limitePorTelegram: inteiroDoAmbiente(
      ambiente.LIMITE_DE_VINCULOS_POR_TELEGRAM, 5, 1, 100,
      "LIMITE_DE_VINCULOS_POR_TELEGRAM"),
    janelaPorTelegramEmMs: inteiroDoAmbiente(
      ambiente.JANELA_DE_VINCULOS_POR_TELEGRAM_EM_MS, 600_000,
      10_000, 3_600_000, "JANELA_DE_VINCULOS_POR_TELEGRAM_EM_MS"),
    limiteGlobalPorMinuto: inteiroDoAmbiente(
      ambiente.LIMITE_GLOBAL_DE_VINCULOS_POR_MINUTO, 100, 1, 10_000,
      "LIMITE_GLOBAL_DE_VINCULOS_POR_MINUTO"),
    tempoLimiteDoBackendEmMs: inteiroDoAmbiente(
      ambiente.TEMPO_LIMITE_DO_BACKEND_EM_MS, 20_000, 1_000, 60_000,
      "TEMPO_LIMITE_DO_BACKEND_EM_MS"),
    tempoLimiteDosScriptsEmMs: inteiroDoAmbiente(
      ambiente.TEMPO_LIMITE_DOS_SCRIPTS_EM_MS, 45_000, 5_000, 120_000,
      "TEMPO_LIMITE_DOS_SCRIPTS_EM_MS"),
  });
}

async function lerCorpoLimitado(fonte, limite) {
  const partes = [];
  let tamanho = 0;
  for await (const parte of fonte) {
    const bytes = Buffer.from(parte);
    tamanho += bytes.length;
    if (tamanho > limite) throw new ErroDoIntegrador("CORPO_MUITO_GRANDE", 413);
    partes.push(bytes);
  }
  return Buffer.concat(partes);
}

function validarRequisicao(valor, configuracao) {
  if (!valor || typeof valor !== "object" || Array.isArray(valor)
      || JSON.stringify(Object.keys(valor).sort()) !== JSON.stringify(CHAVES_DA_REQUISICAO)) {
    throw new ErroDoIntegrador("REQUISICAO_INVALIDA", 400);
  }
  const codigo = typeof valor.codigoDeVinculo === "string"
    ? valor.codigoDeVinculo.trim().toUpperCase()
    : "";
  for (const identificador of [valor.identificadorDoTelegram, valor.identificadorDoChat]) {
    if (typeof identificador !== "string"
        || !FORMATO_DO_IDENTIFICADOR_NUMERICO.test(identificador)
        || !Number.isSafeInteger(Number(identificador))) {
      throw new ErroDoIntegrador("REQUISICAO_INVALIDA", 400);
    }
  }
  if (valor.versaoDoContrato !== "1" || valor.canal !== "TELEGRAM"
      || !FORMATO_DO_CODIGO.test(codigo)
      || valor.identificadorDoTelegram !== valor.identificadorDoChat
      || valor.identificadorDaContaDoBot !== configuracao.identificadorDaContaDoBot) {
    throw new ErroDoIntegrador("REQUISICAO_INVALIDA", 400);
  }
  return Object.freeze({
    codigo,
    identificadorDoTelegram: valor.identificadorDoTelegram,
    identificadorDoChat: valor.identificadorDoChat,
  });
}

function validarConfirmacao(valor, configuracao) {
  const chaves = ["canal", "codigo", "identificadorDaContaDoBot",
    "identificadorDoChat", "identificadorDoTelegram", "identificadorDoUpdate",
    "metodo", "versaoDoContrato"].sort();
  if (!valor || typeof valor !== "object" || Array.isArray(valor)
      || JSON.stringify(Object.keys(valor).sort()) !== JSON.stringify(chaves)
      || valor.versaoDoContrato !== "1" || valor.canal !== "TELEGRAM"
      || valor.metodo !== "TEXTO"
      || valor.identificadorDaContaDoBot !== configuracao.identificadorDaContaDoBot
      || typeof valor.codigo !== "string"
      || !FORMATO_DO_CODIGO_DE_CONFIRMACAO.test(valor.codigo)
      || typeof valor.identificadorDoUpdate !== "string"
      || valor.identificadorDoUpdate.length < 1
      || valor.identificadorDoUpdate.length > 160) {
    throw new ErroDoIntegrador("REQUISICAO_INVALIDA", 400);
  }
  for (const identificador of [valor.identificadorDoTelegram,
    valor.identificadorDoChat]) {
    if (typeof identificador !== "string"
        || !FORMATO_DO_IDENTIFICADOR_NUMERICO.test(identificador)
        || !Number.isSafeInteger(Number(identificador))) {
      throw new ErroDoIntegrador("REQUISICAO_INVALIDA", 400);
    }
  }
  if (valor.identificadorDoTelegram !== valor.identificadorDoChat) {
    throw new ErroDoIntegrador("REQUISICAO_INVALIDA", 400);
  }
  return Object.freeze({ ...valor });
}

function localizarProvisionamento(requisicao, configuracao) {
  const diretorio = path.join(configuracao.diretorioDeEstado, "provisionamentos");
  for (const nome of readdirSync(diretorio)) {
    if (!nome.endsWith(".json")) continue;
    const arquivo = path.join(diretorio, nome);
    const estado = lstatSync(arquivo);
    if (!estado.isFile() || estado.isSymbolicLink()
        || (estado.mode & 0o777) !== 0o600) continue;
    const item = JSON.parse(readFileSync(arquivo, "utf8"));
    if (item.estado === "ATIVO"
        && item.identificadorDoBot === configuracao.identificadorDoBot
        && item.identificadorDoTelegram === requisicao.identificadorDoTelegram
        && item.identificadorDoChat === requisicao.identificadorDoChat
        && typeof item.identificadorDaSessao === "string") return item;
  }
  throw new ErroDoIntegrador("VINCULO_NAO_ENCONTRADO", 404);
}

async function confirmarOperacao(requisicao, configuracao, buscar) {
  const provisionamento = localizarProvisionamento(requisicao, configuracao);
  const corpo = JSON.stringify({
    codigo: requisicao.codigo,
    metodo: requisicao.metodo,
    identificadorDoBot: Number(configuracao.identificadorDoBot),
    identificadorDoTelegram: Number(requisicao.identificadorDoTelegram),
    identificadorDoChat: Number(requisicao.identificadorDoChat),
    identificadorDaSessao: provisionamento.identificadorDaSessao,
    identificadorDoUpdate: requisicao.identificadorDoUpdate,
  });
  const idempotencia = `confirmacao-${createHash("sha256")
    .update(`${requisicao.identificadorDoTelegram}:${requisicao.identificadorDoUpdate}`)
    .digest("hex")}`;
  let retorno;
  try {
    retorno = await chamarBackend({ caminho: CAMINHO_DA_CONFIRMACAO, corpo,
      idempotencia, configuracao, buscar });
  } catch {
    throw new ErroDoIntegrador("INTEGRACAO_INDISPONIVEL", 503);
  }
  if (retorno.status >= 200 && retorno.status < 300) {
    try {
      const dados = JSON.parse(retorno.bytes.toString("utf8"));
      if (dados?.exigeNovaConfirmacao === true
          && typeof dados.proximaFrase === "string"
          && /^CONFIRMAR [23456789A-HJ-NP-Z]{8}$/.test(dados.proximaFrase)) {
        return { estado: 200, codigo: "NOVA_CONFIRMACAO_EXIGIDA",
          proximaFrase: dados.proximaFrase };
      }
    } catch {
      throw new ErroDoIntegrador("RESPOSTA_DO_BACKEND_INVALIDA", 503);
    }
    return { estado: 200, codigo: "OPERACAO_APLICADA" };
  }
  if ([404, 409, 410, 422].includes(retorno.status)) {
    throw new ErroDoIntegrador("CONFIRMACAO_RECUSADA", 409);
  }
  throw new ErroDoIntegrador("INTEGRACAO_INDISPONIVEL", 503);
}

function hashDaOperacao(requisicao, configuracao) {
  const canonico = JSON.stringify({
    bot: configuracao.identificadorDoBot,
    telegram: requisicao.identificadorDoTelegram,
    chat: requisicao.identificadorDoChat,
    codigo: requisicao.codigo,
  });
  return createHmac("sha256", configuracao.segredoDoGateway)
    .update(canonico).digest("hex");
}

function caminhoDoRecibo(hash, configuracao) {
  return path.join(configuracao.diretorioDosRecibos, `${hash}.json`);
}

function lerRecibo(hash, configuracao) {
  const arquivo = caminhoDoRecibo(hash, configuracao);
  try {
    const estado = lstatSync(arquivo);
    if (!estado.isFile() || estado.isSymbolicLink()
        || (estado.mode & 0o777) !== 0o600) {
      throw new ErroDoIntegrador("RECIBO_INSEGURO", 503);
    }
    const recibo = JSON.parse(readFileSync(arquivo, "utf8"));
    if (recibo?.versao !== 1 || recibo?.hashDaOperacao !== hash
        || !["TROCADO", "PROVISIONADO_LOCALMENTE", "APLICADO"].includes(recibo.estado)
        || !FORMATO_DO_UUID.test(recibo.identificadorDoVinculo)) {
      throw new ErroDoIntegrador("RECIBO_INVALIDO", 503);
    }
    return recibo;
  } catch (erro) {
    if (erro?.code === "ENOENT") return null;
    if (erro instanceof ErroDoIntegrador) throw erro;
    throw new ErroDoIntegrador("RECIBO_INVALIDO", 503);
  }
}

function gravarRecibo(hash, estado, identificadorDoVinculo, configuracao) {
  const destino = caminhoDoRecibo(hash, configuracao);
  const temporario = path.join(configuracao.diretorioDosRecibos,
    `.recibo-${randomUUID()}`);
  const recibo = {
    versao: 1,
    hashDaOperacao: hash,
    estado,
    identificadorDoVinculo,
    atualizadoEm: new Date().toISOString(),
  };
  writeFileSync(temporario, `${JSON.stringify(recibo)}\n`, {
    encoding: "utf8", mode: 0o600, flag: "wx",
  });
  chmodSync(temporario, 0o600);
  renameSync(temporario, destino);
}

function cabecalhosHmac({ corpo, caminho, idempotencia, configuracao }) {
  const instante = String(Math.floor(Date.now() / 1_000));
  const nonce = randomBytes(32).toString("base64url");
  const hashDoCorpo = createHash("sha256").update(corpo).digest("hex");
  const canonico = [
    "TRILHA-HMAC-V1",
    configuracao.identificadorDaChave,
    instante,
    nonce,
    "POST",
    caminho,
    hashDoCorpo,
    idempotencia,
  ].join("\n");
  const assinatura = createHmac("sha256", configuracao.segredoDoGateway)
    .update(canonico).digest("hex");
  return {
    "Content-Type": "application/json",
    Accept: "application/json",
    "X-Trilha-Chave": configuracao.identificadorDaChave,
    "X-Trilha-Instante": instante,
    "X-Trilha-Nonce": nonce,
    "X-Trilha-Assinatura": assinatura,
    "X-Chave-De-Idempotencia": idempotencia,
  };
}

async function chamarBackend({ caminho, corpo, idempotencia, configuracao, buscar }) {
  const controlador = new AbortController();
  const temporizador = setTimeout(
    () => controlador.abort(), configuracao.tempoLimiteDoBackendEmMs);
  temporizador.unref?.();
  try {
    const resposta = await buscar(`${configuracao.urlDoBackend}${caminho}`, {
      method: "POST",
      headers: cabecalhosHmac({ corpo, caminho, idempotencia, configuracao }),
      body: corpo,
      redirect: "error",
      signal: controlador.signal,
    });
    const bytes = resposta.body
      ? await lerCorpoLimitado(resposta.body, LIMITE_DA_RESPOSTA_DO_BACKEND)
      : Buffer.alloc(0);
    return { status: resposta.status, bytes };
  } finally {
    clearTimeout(temporizador);
  }
}

function validarTroca(bytes, requisicao, configuracao) {
  let resposta;
  try {
    resposta = JSON.parse(bytes.toString("utf8"));
  } catch {
    throw new ErroDoIntegrador("RESPOSTA_DO_BACKEND_INVALIDA", 503);
  }
  const vinculo = resposta?.vinculo;
  if (!vinculo || !FORMATO_DO_UUID.test(vinculo.identificador)
      || vinculo.estado !== "ATIVO" || vinculo.canal !== "TELEGRAM"
      || String(vinculo.identificadorDoBot) !== configuracao.identificadorDoBot
      || String(vinculo.identificadorExterno) !== requisicao.identificadorDoTelegram
      || String(vinculo.identificadorDoChat) !== requisicao.identificadorDoChat
      || typeof resposta.token !== "string" || !FORMATO_DO_TOKEN.test(resposta.token)) {
    throw new ErroDoIntegrador("RESPOSTA_DO_BACKEND_INVALIDA", 503);
  }
  return { identificadorDoVinculo: vinculo.identificador, token: resposta.token };
}

async function trocarCodigo(requisicao, hash, configuracao, buscar) {
  const corpo = JSON.stringify({
    codigo: requisicao.codigo,
    identificadorDoBot: Number(configuracao.identificadorDoBot),
    identificadorDoTelegram: Number(requisicao.identificadorDoTelegram),
    identificadorDoChat: Number(requisicao.identificadorDoChat),
  });
  let resposta;
  try {
    resposta = await chamarBackend({
      caminho: CAMINHO_DA_TROCA,
      corpo,
      idempotencia: `vinculo-telegram-${hash}`,
      configuracao,
      buscar,
    });
  } catch {
    throw new ErroDoIntegrador("INTEGRACAO_INDISPONIVEL", 503);
  }
  if (resposta.status >= 200 && resposta.status < 300) {
    return validarTroca(resposta.bytes, requisicao, configuracao);
  }
  if ([400, 404, 410, 422].includes(resposta.status)) {
    throw new ErroDoIntegrador("CODIGO_INVALIDO_OU_EXPIRADO", 422);
  }
  if (resposta.status === 409) {
    throw new ErroDoIntegrador("VINCULO_EM_CONFLITO", 409);
  }
  if (resposta.status === 429) {
    throw new ErroDoIntegrador("LIMITE_DE_TENTATIVAS_ATINGIDO", 429);
  }
  throw new ErroDoIntegrador("INTEGRACAO_INDISPONIVEL", 503);
}

function executarScript(arquivo, argumentos, tempoLimiteEmMs) {
  return new Promise((resolver, rejeitar) => {
    const processo = spawn(arquivo, argumentos, {
      stdio: ["ignore", "pipe", "pipe"],
      env: process.env,
    });
    let tamanho = 0;
    const consumir = (parte) => {
      tamanho += parte.length;
      if (tamanho > 65_536) processo.kill("SIGKILL");
    };
    processo.stdout.on("data", consumir);
    processo.stderr.on("data", consumir);
    const temporizador = setTimeout(() => processo.kill("SIGKILL"), tempoLimiteEmMs);
    temporizador.unref?.();
    processo.once("error", () => {
      clearTimeout(temporizador);
      rejeitar(new ErroDoIntegrador("SCRIPT_CONFIAVEL_INDISPONIVEL", 503));
    });
    processo.once("close", (codigo) => {
      clearTimeout(temporizador);
      if (codigo === 0 && tamanho <= 65_536) resolver();
      else rejeitar(new ErroDoIntegrador("SCRIPT_CONFIAVEL_FALHOU", 503));
    });
  });
}

async function provisionar({ troca, requisicao, configuracao, executar }) {
  const identificadorCompacto = troca.identificadorDoVinculo.replaceAll("-", "");
  const identificadorDoAgente = `trilha_${identificadorCompacto}`;
  const identificadorDaSessao = `sessao:${troca.identificadorDoVinculo}`;
  const diretorioTemporario = mkdtempSync(
    path.join(tmpdir(), "trilha-integrador-"));
  chmodSync(diretorioTemporario, 0o700);
  const arquivoDoToken = path.join(diretorioTemporario, "token-mcp");
  writeFileSync(arquivoDoToken, `${troca.token}\n`, {
    encoding: "utf8", mode: 0o600, flag: "wx",
  });
  try {
    await executar(configuracao.arquivoDeProvisionamento, [
      "--diretorio-estado", configuracao.diretorioDeEstado,
      "--diretorio-credenciais-mcp", configuracao.diretorioDeCredenciais,
      "--identificador-vinculo", troca.identificadorDoVinculo,
      "--identificador-bot", configuracao.identificadorDoBot,
      "--identificador-telegram", requisicao.identificadorDoTelegram,
      "--identificador-chat", requisicao.identificadorDoChat,
      "--identificador-agente", identificadorDoAgente,
      "--identificador-sessao", identificadorDaSessao,
      "--token-mcp-arquivo", arquivoDoToken,
      "--url-mcp", configuracao.urlMcp,
      "--modelo", configuracao.modelo,
    ], configuracao.tempoLimiteDosScriptsEmMs);
  } finally {
    rmSync(diretorioTemporario, { recursive: true, force: true });
  }
  return { identificadorDoAgente, identificadorDaSessao };
}

async function registrarProvisionamento(troca, configuracao, executar) {
  await executar(configuracao.arquivoDeRegistro, [
    "--diretorio-estado", configuracao.diretorioDeEstado,
    "--identificador-vinculo", troca.identificadorDoVinculo,
    "--url-backend", configuracao.urlDoBackend,
    "--identificador-chave", configuracao.identificadorDaChave,
    "--segredo-gateway-arquivo", configuracao.arquivoDoSegredoGateway,
  ], configuracao.tempoLimiteDosScriptsEmMs);
}

function criarLimitador(configuracao) {
  const porTelegram = new Map();
  let globais = [];
  return (telegram) => {
    const agora = Date.now();
    globais = globais.filter((instante) => instante > agora - 60_000);
    const anteriores = (porTelegram.get(telegram) ?? [])
      .filter((instante) => instante > agora - configuracao.janelaPorTelegramEmMs);
    if (globais.length >= configuracao.limiteGlobalPorMinuto
        || anteriores.length >= configuracao.limitePorTelegram) {
      throw new ErroDoIntegrador("LIMITE_DE_TENTATIVAS_ATINGIDO", 429);
    }
    globais.push(agora);
    anteriores.push(agora);
    porTelegram.set(telegram, anteriores);
  };
}

function responder(resposta, estado, codigo, proximaFrase = undefined) {
  resposta.writeHead(estado, {
    "Content-Type": "application/json; charset=utf-8",
    "Cache-Control": "no-store",
    "X-Content-Type-Options": "nosniff",
  });
  resposta.end(JSON.stringify({ codigo, ...(proximaFrase
    ? { proximaFrase } : {}) }));
}

export function criarServidorDoIntegrador({
  configuracao,
  buscar = globalThis.fetch,
  executar = executarScript,
} = {}) {
  if (!configuracao || typeof buscar !== "function" || typeof executar !== "function") {
    throw new Error("Dependencias do integrador invalidas.");
  }
  const limitar = criarLimitador(configuracao);
  const emAndamento = new Map();

  const processar = async (requisicao) => {
    const hash = hashDaOperacao(requisicao, configuracao);
    const recibo = lerRecibo(hash, configuracao);
    if (recibo?.estado === "APLICADO") {
      return { estado: 200, codigo: "VINCULO_CONCLUIDO" };
    }
    const existente = emAndamento.get(hash);
    if (existente) return existente;
    limitar(requisicao.identificadorDoTelegram);
    const operacao = (async () => {
      if (recibo?.estado === "PROVISIONADO_LOCALMENTE") {
        await registrarProvisionamento({
          identificadorDoVinculo: recibo.identificadorDoVinculo,
        }, configuracao, executar);
        gravarRecibo(hash, "APLICADO", recibo.identificadorDoVinculo,
          configuracao);
        return { estado: 200, codigo: "VINCULO_CONCLUIDO" };
      }
      const troca = await trocarCodigo(requisicao, hash, configuracao, buscar);
      gravarRecibo(hash, "TROCADO", troca.identificadorDoVinculo, configuracao);
      await provisionar({ troca, requisicao, configuracao, executar });
      gravarRecibo(hash, "PROVISIONADO_LOCALMENTE",
        troca.identificadorDoVinculo, configuracao);
      await registrarProvisionamento(troca, configuracao, executar);
      gravarRecibo(hash, "APLICADO", troca.identificadorDoVinculo, configuracao);
      return { estado: 200, codigo: "VINCULO_CONCLUIDO" };
    })();
    emAndamento.set(hash, operacao);
    try {
      return await operacao;
    } finally {
      emAndamento.delete(hash);
    }
  };

  const servidor = createServer(async (pedido, resposta) => {
    if (pedido.method === "GET" && pedido.url === "/healthz") {
      responder(resposta, 200, "ATIVO");
      return;
    }
    const rotaDeVinculo = pedido.url === "/v1/vinculos/telegram";
    const rotaDeConfirmacao = pedido.url === CAMINHO_LOCAL_DA_CONFIRMACAO;
    if (pedido.method !== "POST" || (!rotaDeVinculo && !rotaDeConfirmacao)) {
      responder(resposta, 404, "ROTA_NAO_ENCONTRADA");
      return;
    }
    if (!/^application\/json(?:\s*;|$)/i.test(
      String(pedido.headers["content-type"] ?? ""))) {
      responder(resposta, 415, "TIPO_DE_CONTEUDO_INVALIDO");
      return;
    }
    try {
      const corpo = await lerCorpoLimitado(pedido, LIMITE_DO_CORPO);
      let valor;
      try {
        valor = JSON.parse(corpo.toString("utf8"));
      } catch {
        throw new ErroDoIntegrador("REQUISICAO_INVALIDA", 400);
      }
      const requisicao = rotaDeVinculo
        ? validarRequisicao(valor, configuracao)
        : validarConfirmacao(valor, configuracao);
      const resultado = rotaDeVinculo
        ? await processar(requisicao)
        : await confirmarOperacao(requisicao, configuracao, buscar);
      responder(resposta, resultado.estado, resultado.codigo,
        resultado.proximaFrase);
    } catch (erro) {
      const conhecido = erro instanceof ErroDoIntegrador
        ? erro : new ErroDoIntegrador("INTEGRACAO_INDISPONIVEL", 503);
      responder(resposta, conhecido.estadoHttp, conhecido.codigo);
    }
  });
  servidor.requestTimeout = 30_000;
  servidor.headersTimeout = 10_000;
  servidor.maxHeadersCount = 32;
  return servidor;
}

function iniciar() {
  const configuracao = carregarConfiguracaoDoAmbiente();
  const porta = inteiroDoAmbiente(process.env.PORTA_DO_INTEGRADOR,
    8_090, 1, 65_535, "PORTA_DO_INTEGRADOR");
  const servidor = criarServidorDoIntegrador({ configuracao });
  servidor.listen(porta, "0.0.0.0");
  const encerrar = () => {
    servidor.close(() => process.exit(0));
    setTimeout(() => process.exit(1), 5_000).unref();
  };
  process.on("SIGTERM", encerrar);
  process.on("SIGINT", encerrar);
}

if (process.argv[1]
    && import.meta.url === pathToFileURL(path.resolve(process.argv[1])).href) {
  try {
    iniciar();
  } catch {
    process.stderr.write("Erro: nao foi possivel iniciar o integrador confiavel.\n");
    process.exit(1);
  }
}
