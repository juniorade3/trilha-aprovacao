#!/usr/bin/env node

import { createServer } from "node:http";
import { lstatSync, readFileSync, realpathSync, statSync, writeFileSync } from "node:fs";
import { Readable } from "node:stream";
import path from "node:path";

const [diretorioInformado, portaInformada = "18890", arquivoDaPorta] = process.argv.slice(2);
const limiteDoCorpo = 1024 * 1024;
const formatoDoVinculo = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
const formatoDoToken = /^mcp_[A-Za-z0-9_-]{43}$/;

function falhar(mensagem) {
  process.stderr.write(`Erro: ${mensagem}\n`);
  process.exit(1);
}

if (!diretorioInformado || !/^\d{1,5}$/.test(portaInformada)) {
  falhar("uso: broker-de-credenciais-mcp.mjs DIRETORIO [PORTA] [ARQUIVO_DA_PORTA].");
}

const porta = Number(portaInformada);
if (porta < 0 || porta > 65535) {
  falhar("porta invalida.");
}

let diretorioDeCredenciais;
try {
  diretorioDeCredenciais = realpathSync(diretorioInformado);
  const estado = statSync(diretorioDeCredenciais);
  if (!estado.isDirectory() || (estado.mode & 0o777) !== 0o700) {
    falhar("o diretorio de credenciais MCP deve ser regular e ter permissao 0700.");
  }
} catch (erro) {
  falhar(`nao foi possivel validar o diretorio de credenciais: ${erro.message}`);
}

function carregarCredencial(identificadorDoVinculo) {
  const arquivo = path.join(diretorioDeCredenciais, `${identificadorDoVinculo}.json`);
  const estado = lstatSync(arquivo);
  if (!estado.isFile() || estado.isSymbolicLink() || (estado.mode & 0o777) !== 0o600) {
    throw new Error("ARQUIVO_DE_CREDENCIAL_INSEGURO");
  }
  const dados = JSON.parse(readFileSync(arquivo, "utf8"));
  const chaves = Object.keys(dados).sort();
  const esperadas = [
    "identificadorDaSessao",
    "identificadorDoAgente",
    "identificadorDoVinculo",
    "tokenMcp",
    "urlMcp",
    "versao",
  ].sort();
  if (JSON.stringify(chaves) !== JSON.stringify(esperadas)) {
    throw new Error("FORMATO_DE_CREDENCIAL_INVALIDO");
  }
  if (
    dados.versao !== 1 ||
    dados.identificadorDoVinculo !== identificadorDoVinculo ||
    typeof dados.identificadorDoAgente !== "string" ||
    !/^[a-z0-9][a-z0-9_-]{0,63}$/.test(dados.identificadorDoAgente) ||
    typeof dados.identificadorDaSessao !== "string" ||
    !/^[A-Za-z0-9][A-Za-z0-9._:-]{0,159}$/.test(dados.identificadorDaSessao) ||
    typeof dados.tokenMcp !== "string" ||
    !formatoDoToken.test(dados.tokenMcp)
  ) {
    throw new Error("FORMATO_DE_CREDENCIAL_INVALIDO");
  }
  const destino = new URL(dados.urlMcp);
  if (
    !["http:", "https:"].includes(destino.protocol) ||
    destino.username ||
    destino.password ||
    destino.search ||
    destino.hash ||
    !/\/mcp\/?$/.test(destino.pathname)
  ) {
    throw new Error("URL_MCP_INVALIDA");
  }
  return { ...dados, urlMcp: destino.toString() };
}

async function lerCorpo(pedido) {
  const partes = [];
  let tamanho = 0;
  for await (const parte of pedido) {
    tamanho += parte.length;
    if (tamanho > limiteDoCorpo) {
      throw new Error("CORPO_MUITO_GRANDE");
    }
    partes.push(parte);
  }
  return Buffer.concat(partes);
}

function responder(resposta, estado, codigo) {
  resposta.writeHead(estado, { "Content-Type": "application/json", "Cache-Control": "no-store" });
  resposta.end(JSON.stringify({ codigo }));
}

const servidor = createServer(async (pedido, resposta) => {
  if (pedido.method === "GET" && pedido.url === "/healthz") {
    responder(resposta, 200, "ATIVO");
    return;
  }

  const encontrado = /^\/mcp\/([0-9a-f-]+)$/.exec(pedido.url ?? "");
  if (pedido.method !== "POST" || !encontrado || !formatoDoVinculo.test(encontrado[1])) {
    responder(resposta, 404, "ROTA_NAO_ENCONTRADA");
    return;
  }

  try {
    const credencial = carregarCredencial(encontrado[1]);
    const corpo = await lerCorpo(pedido);
    const controlador = new AbortController();
    const temporizador = setTimeout(() => controlador.abort(), 35_000);
    temporizador.unref?.();
    let retorno;
    try {
      retorno = await fetch(credencial.urlMcp, {
        method: "POST",
        headers: {
          Authorization: `Bearer ${credencial.tokenMcp}`,
          "X-Identificador-Do-Agente": credencial.identificadorDoAgente,
          "X-Identificador-Da-Sessao": credencial.identificadorDaSessao,
          Accept: pedido.headers.accept ?? "application/json, text/event-stream",
          "Content-Type": pedido.headers["content-type"] ?? "application/json",
          ...(pedido.headers["mcp-protocol-version"]
            ? { "MCP-Protocol-Version": pedido.headers["mcp-protocol-version"] }
            : {}),
        },
        body: corpo,
        redirect: "error",
        signal: controlador.signal,
      });
    } finally {
      clearTimeout(temporizador);
    }

    const cabecalhos = { "Cache-Control": "no-store" };
    for (const nome of ["content-type", "mcp-session-id", "retry-after"]) {
      const valor = retorno.headers.get(nome);
      if (valor) cabecalhos[nome] = valor;
    }
    resposta.writeHead(retorno.status, cabecalhos);
    if (retorno.body) {
      Readable.fromWeb(retorno.body).pipe(resposta);
    } else {
      resposta.end();
    }
  } catch (erro) {
    const esperado = new Set([
      "ARQUIVO_DE_CREDENCIAL_INSEGURO",
      "FORMATO_DE_CREDENCIAL_INVALIDO",
      "URL_MCP_INVALIDA",
      "CORPO_MUITO_GRANDE",
    ]);
    const codigo = esperado.has(erro.message) ? erro.message : "CREDENCIAL_INDISPONIVEL";
    responder(resposta, codigo === "CORPO_MUITO_GRANDE" ? 413 : 503, codigo);
  }
});

servidor.listen(porta, "0.0.0.0", () => {
  const endereco = servidor.address();
  if (arquivoDaPorta) {
    writeFileSync(arquivoDaPorta, String(endereco.port), { mode: 0o600 });
  }
});

function encerrar() {
  servidor.close(() => process.exit(0));
  setTimeout(() => process.exit(1), 5_000).unref();
}
process.on("SIGTERM", encerrar);
process.on("SIGINT", encerrar);
