#!/usr/bin/env node

import { constants } from "node:fs";
import { open } from "node:fs/promises";
import path from "node:path";
import { createInterface } from "node:readline";
import { fileURLToPath, pathToFileURL } from "node:url";

const limiteDaMensagem = 1024 * 1024;
const tempoLimiteEmMs = 35_000;
const caminhoDaPoliticaPadrao = fileURLToPath(new URL("./.mcp.json", import.meta.url));
const formatoDoUuid =
  /^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/;

export function validarDestino(destinoInformado) {
  const destino = new URL(destinoInformado);
  if (
    destino.protocol !== "http:"
    || destino.hostname !== "broker-credenciais"
    || destino.port !== "18890"
    || !formatoDoUuid.test(destino.pathname.slice("/mcp/".length))
    || !destino.pathname.startsWith("/mcp/")
    || destino.username
    || destino.password
    || destino.search
    || destino.hash
  ) {
    throw new Error("destino invalido");
  }
  return destino;
}

export async function carregarFerramentasPermitidas(
  caminhoDaPolitica = caminhoDaPoliticaPadrao,
) {
  const arquivo = await open(
    caminhoDaPolitica,
    constants.O_RDONLY | constants.O_NOFOLLOW,
  );
  try {
    const estado = await arquivo.stat();
    if (
      !estado.isFile()
      || (estado.mode & 0o7777) !== 0o600
      || estado.size > 64 * 1024
    ) {
      throw new Error("politica de ferramentas insegura");
    }
    const politica = JSON.parse(await arquivo.readFile("utf8"));
    const ferramentas = politica?.mcpServers?.trilha?.toolFilter?.include;
    if (
      !Array.isArray(ferramentas)
      || ferramentas.length === 0
      || ferramentas.length > 128
      || ferramentas.some(
        (ferramenta) => typeof ferramenta !== "string"
          || !/^[a-z0-9][a-z0-9_.-]{0,127}$/i.test(ferramenta),
      )
      || new Set(ferramentas).size !== ferramentas.length
    ) {
      throw new Error("politica de ferramentas invalida");
    }
    return new Set(ferramentas);
  } finally {
    await arquivo.close();
  }
}

function escrever(mensagem) {
  process.stdout.write(`${JSON.stringify(mensagem)}\n`);
}

function criarErroDaRequisicao(requisicao, codigo, mensagem) {
  if (requisicao?.id === undefined || requisicao?.id === null) return null;
  return {
    jsonrpc: "2.0",
    id: requisicao.id,
    error: { code: codigo, message: mensagem },
  };
}

function erroDaRequisicao(requisicao, codigo, mensagem) {
  const erro = criarErroDaRequisicao(requisicao, codigo, mensagem);
  if (erro) escrever(erro);
}

export function autorizarRequisicao(requisicao, ferramentasPermitidas) {
  if (
    !requisicao
    || typeof requisicao !== "object"
    || Array.isArray(requisicao)
    || requisicao.jsonrpc !== "2.0"
    || typeof requisicao.method !== "string"
  ) {
    return {
      autorizada: false,
      resposta: {
        jsonrpc: "2.0",
        id: requisicao?.id ?? null,
        error: { code: -32600, message: "Requisicao MCP invalida." },
      },
    };
  }
  if (requisicao.method !== "tools/call") return { autorizada: true };
  const nome = requisicao.params?.name;
  if (typeof nome !== "string") {
    return {
      autorizada: false,
      resposta: criarErroDaRequisicao(
        requisicao,
        -32602,
        "Parametros da ferramenta MCP invalidos.",
      ),
    };
  }
  if (!ferramentasPermitidas.has(nome)) {
    return {
      autorizada: false,
      resposta: criarErroDaRequisicao(
        requisicao,
        -32601,
        "Ferramenta MCP nao permitida.",
      ),
    };
  }
  return { autorizada: true };
}

export function filtrarResposta(
  requisicao,
  mensagem,
  ferramentasPermitidas,
) {
  if (
    requisicao.method !== "tools/list"
    || !mensagem
    || typeof mensagem !== "object"
    || Array.isArray(mensagem)
    || mensagem.id !== requisicao.id
    || !Array.isArray(mensagem.result?.tools)
  ) {
    return mensagem;
  }
  return {
    ...mensagem,
    result: {
      ...mensagem.result,
      tools: mensagem.result.tools.filter(
        (ferramenta) => typeof ferramenta?.name === "string"
          && ferramentasPermitidas.has(ferramenta.name),
      ),
    },
  };
}

function mensagensDoEvento(corpo) {
  return corpo
    .split(/\r?\n\r?\n/)
    .flatMap((evento) => evento.split(/\r?\n/))
    .filter((linha) => linha.startsWith("data:"))
    .map((linha) => linha.slice(5).trim())
    .filter((dados) => dados && dados !== "[DONE]")
    .map((dados) => JSON.parse(dados));
}

async function lerCorpoLimitado(resposta) {
  const tamanhoDeclarado = Number(resposta.headers.get("content-length"));
  if (Number.isFinite(tamanhoDeclarado) && tamanhoDeclarado > limiteDaMensagem) {
    throw new Error("resposta MCP excede o limite");
  }
  if (!resposta.body) return "";
  const leitor = resposta.body.getReader();
  const partes = [];
  let tamanho = 0;
  try {
    while (true) {
      const { done, value } = await leitor.read();
      if (done) break;
      tamanho += value.byteLength;
      if (tamanho > limiteDaMensagem) {
        throw new Error("resposta MCP excede o limite");
      }
      partes.push(Buffer.from(value));
    }
  } catch (erro) {
    await leitor.cancel().catch(() => {});
    throw erro;
  }
  return Buffer.concat(partes, tamanho).toString("utf8");
}

async function encaminhar(
  requisicao,
  destino,
  ferramentasPermitidas,
  sessao,
) {
  const controlador = new AbortController();
  const temporizador = setTimeout(() => controlador.abort(), tempoLimiteEmMs);
  temporizador.unref?.();
  try {
    const resposta = await fetch(destino, {
      method: "POST",
      headers: {
        Accept: "application/json, text/event-stream",
        "Content-Type": "application/json",
        ...(sessao.identificador
          ? { "Mcp-Session-Id": sessao.identificador }
          : {}),
      },
      body: JSON.stringify(requisicao),
      redirect: "error",
      signal: controlador.signal,
    });
    sessao.identificador =
      resposta.headers.get("mcp-session-id") ?? sessao.identificador;
    const corpo = await lerCorpoLimitado(resposta);
    if (!resposta.ok) {
      erroDaRequisicao(requisicao, -32000, `MCP indisponivel (${resposta.status}).`);
      return;
    }
    if (!corpo.trim()) return;
    const tipo = resposta.headers.get("content-type") ?? "";
    const mensagens = tipo.includes("text/event-stream")
      ? mensagensDoEvento(corpo)
      : [JSON.parse(corpo)];
    for (const mensagem of mensagens) {
      escrever(filtrarResposta(requisicao, mensagem, ferramentasPermitidas));
    }
  } catch {
    erroDaRequisicao(requisicao, -32000, "MCP indisponivel.");
  } finally {
    clearTimeout(temporizador);
  }
}

export async function executarProxy(argumentos = process.argv.slice(2)) {
  const [destinoInformado, ...argumentosExtras] = argumentos;
  let destino;
  let ferramentasPermitidas;
  try {
    if (argumentosExtras.length !== 0) throw new Error("argumentos invalidos");
    destino = validarDestino(destinoInformado);
    ferramentasPermitidas = await carregarFerramentasPermitidas();
  } catch {
    process.stderr.write("Proxy MCP: configuracao local invalida.\n");
    process.exitCode = 2;
    return;
  }

  const sessao = {};
  const linhas = createInterface({ input: process.stdin, crlfDelay: Infinity });
  for await (const linha of linhas) {
    if (!linha.trim()) continue;
    if (Buffer.byteLength(linha) > limiteDaMensagem) {
      process.stderr.write("Proxy MCP: mensagem excede o limite.\n");
      process.exitCode = 1;
      break;
    }
    let requisicao;
    try {
      requisicao = JSON.parse(linha);
    } catch {
      escrever({
        jsonrpc: "2.0",
        id: null,
        error: { code: -32700, message: "JSON invalido." },
      });
      continue;
    }
    const autorizacao = autorizarRequisicao(
      requisicao,
      ferramentasPermitidas,
    );
    if (!autorizacao.autorizada) {
      if (autorizacao.resposta) escrever(autorizacao.resposta);
      continue;
    }
    await encaminhar(requisicao, destino, ferramentasPermitidas, sessao);
  }
}

const executadoDiretamente = process.argv[1]
  && pathToFileURL(path.resolve(process.argv[1])).href === import.meta.url;
if (executadoDiretamente) await executarProxy();
