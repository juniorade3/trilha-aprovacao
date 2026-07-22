#!/usr/bin/env node

import { writeFileSync } from "node:fs";
import http from "node:http";

const [arquivoDaPorta, arquivoDoResultado, token, agente, sessao,
  endereco = "127.0.0.1"] = process.argv.slice(2);
if (!arquivoDaPorta || !arquivoDoResultado || !token || !agente || !sessao) process.exit(2);
if (!["127.0.0.1", "0.0.0.0"].includes(endereco)) process.exit(2);

const servidor = http.createServer((pedido, resposta) => {
  const partes = [];
  pedido.on("data", (parte) => partes.push(parte));
  pedido.on("end", () => {
    const valido =
      pedido.method === "POST" &&
      pedido.url === "/mcp" &&
      pedido.headers.authorization === `Bearer ${token}` &&
      pedido.headers["x-identificador-do-agente"] === agente &&
      pedido.headers["x-identificador-da-sessao"] === sessao;
    writeFileSync(arquivoDoResultado, valido ? "AUTENTICADO" : "INVALIDO", { mode: 0o600 });
    resposta.statusCode = valido ? 200 : 401;
    resposta.setHeader("Content-Type", "application/json");
    resposta.end(
      JSON.stringify({ jsonrpc: "2.0", id: 1, result: valido ? { tools: [] } : undefined }),
    );
    servidor.close();
  });
});

servidor.listen(0, endereco, () => {
  writeFileSync(arquivoDaPorta, String(servidor.address().port), { mode: 0o600 });
});
setTimeout(() => servidor.close(), 15_000).unref();
