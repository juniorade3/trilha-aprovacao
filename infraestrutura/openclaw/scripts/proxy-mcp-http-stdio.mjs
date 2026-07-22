#!/usr/bin/env node

import { createInterface } from "node:readline";

const [destinoInformado] = process.argv.slice(2);
const limiteDaMensagem = 1024 * 1024;
const tempoLimiteEmMs = 35_000;

let destino;
try {
  destino = new URL(destinoInformado);
  if (
    destino.protocol !== "http:"
    || destino.hostname !== "broker-credenciais"
    || destino.port !== "18890"
    || !/^\/mcp\/[0-9a-f-]{36}$/.test(destino.pathname)
    || destino.username
    || destino.password
    || destino.search
    || destino.hash
  ) {
    throw new Error("destino invalido");
  }
} catch {
  process.stderr.write("Proxy MCP: destino interno invalido.\n");
  process.exit(2);
}

let identificadorDaSessao;

function escrever(mensagem) {
  process.stdout.write(`${JSON.stringify(mensagem)}\n`);
}

function erroDaRequisicao(requisicao, codigo, mensagem) {
  if (requisicao?.id === undefined || requisicao?.id === null) return;
  escrever({ jsonrpc: "2.0", id: requisicao.id, error: { code: codigo, message: mensagem } });
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

async function encaminhar(requisicao) {
  const controlador = new AbortController();
  const temporizador = setTimeout(() => controlador.abort(), tempoLimiteEmMs);
  temporizador.unref?.();
  try {
    const resposta = await fetch(destino, {
      method: "POST",
      headers: {
        Accept: "application/json, text/event-stream",
        "Content-Type": "application/json",
        ...(identificadorDaSessao ? { "Mcp-Session-Id": identificadorDaSessao } : {}),
      },
      body: JSON.stringify(requisicao),
      redirect: "error",
      signal: controlador.signal,
    });
    identificadorDaSessao = resposta.headers.get("mcp-session-id") ?? identificadorDaSessao;
    const corpo = await resposta.text();
    if (!resposta.ok) {
      erroDaRequisicao(requisicao, -32000, `MCP indisponivel (${resposta.status}).`);
      return;
    }
    if (!corpo.trim()) return;
    const tipo = resposta.headers.get("content-type") ?? "";
    const mensagens = tipo.includes("text/event-stream")
      ? mensagensDoEvento(corpo)
      : [JSON.parse(corpo)];
    for (const mensagem of mensagens) escrever(mensagem);
  } catch {
    erroDaRequisicao(requisicao, -32000, "MCP indisponivel.");
  } finally {
    clearTimeout(temporizador);
  }
}

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
    escrever({ jsonrpc: "2.0", id: null, error: { code: -32700, message: "JSON invalido." } });
    continue;
  }
  await encaminhar(requisicao);
}
