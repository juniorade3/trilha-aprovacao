#!/usr/bin/env node

import { createHash, createHmac, timingSafeEqual } from "node:crypto";
import { writeFileSync } from "node:fs";
import http from "node:http";

const [arquivoDaPorta, segredo, identificadorDaChave, identificadorDoVinculo] =
  process.argv.slice(2);
if (!arquivoDaPorta || !segredo || !identificadorDaChave || !identificadorDoVinculo) {
  process.exit(2);
}

const servidor = http.createServer((pedido, resposta) => {
  const partes = [];
  pedido.on("data", (parte) => partes.push(parte));
  pedido.on("end", () => {
    const corpo = Buffer.concat(partes);
    const hashDoCorpo = createHash("sha256").update(corpo).digest("hex");
    const instante = String(pedido.headers["x-trilha-instante"] ?? "");
    const nonce = String(pedido.headers["x-trilha-nonce"] ?? "");
    const idempotencia = String(pedido.headers["x-chave-de-idempotencia"] ?? "");
    const chave = String(pedido.headers["x-trilha-chave"] ?? "");
    const assinatura = String(pedido.headers["x-trilha-assinatura"] ?? "");
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
    const esperada = createHmac("sha256", segredo).update(canonico).digest("hex");
    const assinaturaValida =
      assinatura.length === esperada.length &&
      timingSafeEqual(Buffer.from(assinatura), Buffer.from(esperada));

    let dados;
    try {
      dados = JSON.parse(corpo.toString("utf8"));
    } catch {
      dados = {};
    }
    const valido =
      chave === identificadorDaChave &&
      assinaturaValida &&
      pedido.method === "POST" &&
      pedido.url ===
        `/api/v1/integracoes-confiaveis/telegram/vinculos/${identificadorDoVinculo}/provisionamento` &&
      dados.identificadorDoAgente &&
      dados.identificadorDaSessao;
    resposta.statusCode = valido ? 200 : 401;
    resposta.setHeader("Content-Type", "application/json");
    resposta.end(
      JSON.stringify(
        valido
          ? {
              identificador: identificadorDoVinculo,
              provisionado: true,
              identificadorDoAgente: dados.identificadorDoAgente,
            }
          : { codigo: "ASSINATURA_INVALIDA" },
      ),
    );
    servidor.close();
  });
});

servidor.listen(0, "127.0.0.1", () => {
  const endereco = servidor.address();
  writeFileSync(arquivoDaPorta, String(endereco.port), { mode: 0o600 });
});
setTimeout(() => servidor.close(), 15_000).unref();
