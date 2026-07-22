import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";
import { criarPluginDaTrilha, MENSAGENS } from "./index.js";

function registrar(buscar, pluginConfig = {}) {
  let comando;
  const plugin = criarPluginDaTrilha({ buscar });
  plugin.register({
    pluginConfig,
    registerCommand(registrado) {
      comando = registrado;
    },
  });
  assert.ok(comando);
  return comando;
}

function contextoPrivado(sobrescritas = {}) {
  return {
    channel: "telegram",
    senderId: "123456789",
    from: "telegram:123456789",
    to: "telegram:123456789",
    accountId: "principal",
    args: "23456789AB",
    commandBody: "/conectar 23456789AB",
    isAuthorizedSender: false,
    config: {},
    ...sobrescritas,
  };
}

test("registra /conectar como comando Telegram sem autenticacao previa", () => {
  const comando = registrar(async () => ({ status: 204 }));
  assert.equal(comando.name, "conectar");
  assert.equal(comando.requireAuth, false);
  assert.equal(comando.acceptsArgs, true);
  assert.deepEqual(comando.channels, ["telegram"]);
});

test("rejeita outro canal, grupo e identificador nao numerico sem chamar integrador", async () => {
  let chamadas = 0;
  const comando = registrar(async () => {
    chamadas += 1;
    return { status: 204 };
  });

  assert.deepEqual(await comando.handler(contextoPrivado({ channel: "discord" })),
    { text: MENSAGENS.canal });
  assert.deepEqual(await comando.handler(contextoPrivado({
    from: "telegram:group:-100123", to: "telegram:-100123",
  })), { text: MENSAGENS.privado });
  assert.deepEqual(await comando.handler(contextoPrivado({ senderId: "usuario" })),
    { text: MENSAGENS.privado });
  assert.equal(chamadas, 0);
});

test("rejeita codigo ausente ou fora do alfabeto e tamanho esperados", async () => {
  let chamadas = 0;
  const comando = registrar(async () => {
    chamadas += 1;
    return { status: 204 };
  });

  assert.deepEqual(await comando.handler(contextoPrivado({ args: "" })),
    { text: MENSAGENS.uso });
  assert.deepEqual(await comando.handler(contextoPrivado({ args: "ABC123" })),
    { text: MENSAGENS.codigo });
  assert.deepEqual(await comando.handler(contextoPrivado({ args: "23456789IO" })),
    { text: MENSAGENS.codigo });
  assert.equal(chamadas, 0);
});

test("encaminha somente o contrato necessario ao integrador configurado", async () => {
  let chamada;
  const comando = registrar(async (url, opcoes) => {
    chamada = { url, opcoes };
    return { status: 201 };
  }, { urlDoIntegrador: "http://integrador-teste:8091", tempoLimiteEmMs: 1200 });

  const resultado = await comando.handler(contextoPrivado({ args: "23456789ab" }));

  assert.deepEqual(resultado, { text: MENSAGENS.sucesso });
  assert.equal(chamada.url, "http://integrador-teste:8091/v1/vinculos/telegram");
  assert.equal(chamada.opcoes.method, "POST");
  assert.equal(chamada.opcoes.redirect, "error");
  assert.deepEqual(chamada.opcoes.headers, {
    accept: "application/json",
    "content-type": "application/json",
  });
  assert.deepEqual(JSON.parse(chamada.opcoes.body), {
    versaoDoContrato: "1",
    canal: "TELEGRAM",
    codigoDeVinculo: "23456789AB",
    identificadorDoTelegram: "123456789",
    identificadorDoChat: "123456789",
    identificadorDaContaDoBot: "principal",
  });
  assert.ok(chamada.opcoes.signal instanceof AbortSignal);
});

test("nunca repassa corpo, segredo ou detalhe de falha do integrador", async () => {
  const segredo = "mcp_token_super_secreto";
  const comandoComConflito = registrar(async () => new Response(
    JSON.stringify({ token: segredo, codigo: "DETALHE_INTERNO" }),
    { status: 409, headers: { "content-type": "application/json" } }));
  const conflito = await comandoComConflito.handler(contextoPrivado());
  assert.deepEqual(conflito, { text: MENSAGENS.conflito });
  assert.doesNotMatch(conflito.text, /mcp_|token|DETALHE_INTERNO/i);

  const comandoComFalha = registrar(async () => {
    throw new Error(`falha com ${segredo}`);
  });
  const falha = await comandoComFalha.handler(contextoPrivado());
  assert.deepEqual(falha, { text: MENSAGENS.indisponivel });
  assert.doesNotMatch(falha.text, /mcp_|token|segredo/i);
});

test("manifesto e configuracao sao fechados e compativeis com 2026.7.1", async () => {
  const manifesto = JSON.parse(await readFile(
    new URL("./openclaw.plugin.json", import.meta.url), "utf8"));
  const pacote = JSON.parse(await readFile(
    new URL("./package.json", import.meta.url), "utf8"));

  assert.equal(manifesto.id, "trilha-aprovacao");
  assert.equal(manifesto.version, "2026.7.1");
  assert.equal(manifesto.configSchema.type, "object");
  assert.equal(manifesto.configSchema.additionalProperties, false);
  assert.equal(pacote.openclaw.compat.pluginApi, ">=2026.7.1");
  assert.deepEqual(pacote.openclaw.extensions, ["./index.js"]);
});
