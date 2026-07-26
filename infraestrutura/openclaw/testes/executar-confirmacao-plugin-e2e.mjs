#!/usr/bin/env node

import { stdin, stdout } from "node:process";
import { criarPluginDaTrilha } from "../plugin-trilha/index.js";

async function lerEntrada() {
  const partes = [];
  for await (const parte of stdin) partes.push(Buffer.from(parte));
  try {
    return JSON.parse(Buffer.concat(partes).toString("utf8"));
  } catch {
    throw new Error("Entrada E2E invalida.");
  }
}

function validarEntrada(entrada) {
  if (!entrada || typeof entrada !== "object"
      || typeof entrada.urlDoIntegrador !== "string"
      || typeof entrada.identificadorDaContaDoBot !== "string"
      || typeof entrada.texto !== "string"
      || typeof entrada.identificadorDoTelegram !== "string"
      || typeof entrada.identificadorDoChat !== "string"
      || typeof entrada.identificadorDoUpdate !== "string") {
    throw new Error("Entrada E2E invalida.");
  }
}

async function executar() {
  const entrada = await lerEntrada();
  validarEntrada(entrada);
  const hooks = [];
  const plugin = criarPluginDaTrilha();
  plugin.register({
    pluginConfig: {
      urlDoIntegrador: entrada.urlDoIntegrador,
      identificadorDaContaDoBot: entrada.identificadorDaContaDoBot,
      tempoLimiteEmMs: 10_000,
    },
    logger: { info() {}, warn() {} },
    registerCommand() {},
    on(nome, manipulador, opcoes) {
      hooks.push({ nome, manipulador, opcoes });
    },
  });
  const encontrados = hooks.filter((hook) => hook.nome === "before_dispatch");
  if (encontrados.length !== 1) {
    throw new Error("Hook before_dispatch indisponivel.");
  }
  const resposta = await encontrados[0].manipulador({
    content: entrada.texto,
    channel: "telegram",
    accountId: entrada.identificadorDaContaDoBot,
    conversationId: entrada.identificadorDoChat,
    senderId: entrada.identificadorDoTelegram,
    messageId: entrada.identificadorDoUpdate,
    isGroup: false,
    commandAuthorized: true,
  }, {
    channelId: "telegram",
    accountId: entrada.identificadorDaContaDoBot,
    conversationId: entrada.identificadorDoChat,
    senderId: entrada.identificadorDoTelegram,
    messageId: entrada.identificadorDoUpdate,
    commandAuthorized: true,
  });
  if (resposta?.handled !== true || typeof resposta.text !== "string") {
    throw new Error("Resposta do hook before_dispatch invalida.");
  }
  stdout.write(JSON.stringify({
    handled: resposta.handled,
    text: resposta.text,
  }));
}

executar().catch(() => {
  process.stderr.write("Falha no harness E2E do plugin.\n");
  process.exitCode = 1;
});
