import assert from "node:assert/strict";
import { mkdtemp, mkdir, readFile, rm, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import path from "node:path";
import test from "node:test";
import { pathToFileURL } from "node:url";
import { criarPluginDaTrilha, MENSAGENS } from "./index.js";

const diretorioDaDistribuicao = process.env.OPENCLAW_DIST_DIR;
const somenteNaImagemOficial = { skip: !diretorioDaDistribuicao };
const IDENTIFICADOR_DA_OPERACAO = "123e4567-e89b-12d3-a456-426614174000";

function modulo(caminho) {
  return pathToFileURL(`${diretorioDaDistribuicao}/${caminho}`).href;
}

function contextoCanonico() {
  return {
    Body: "2345678A",
    BodyForAgent: "2345678A",
    BodyForCommands: "2345678A",
    RawBody: "2345678A",
    From: "telegram:987654321",
    To: "telegram:987654321",
    OriginatingTo: "987654321",
    Provider: "telegram",
    Surface: "telegram",
    OriginatingChannel: "telegram",
    AccountId: "default",
    SenderId: "123456789",
    MessageSid: "update-10",
    CommandAuthorized: true,
    SessionKey: "agent:main:telegram:direct:987654321",
  };
}

test("mapper oficial constroi DM e identidades esperadas", somenteNaImagemOficial,
  async () => {
    const { deriveInboundMessageHookContext } = await import(
      modulo("plugin-sdk/hook-runtime.js"));
    const contexto = deriveInboundMessageHookContext(contextoCanonico());

    assert.equal(contexto.channelId, "telegram");
    assert.equal(contexto.accountId, "default");
    assert.equal(contexto.conversationId, "987654321");
    assert.equal(contexto.senderId, "123456789");
    assert.equal(contexto.messageId, "update-10");
    assert.equal(contexto.isGroup, false);
  });

test("dispatcher oficial encerra before_dispatch sem chamar modelo",
  somenteNaImagemOficial, async () => {
    const {
      initializeGlobalHookRunner,
      resetGlobalHookRunner,
    } = await import(modulo("plugin-sdk/hook-runtime.js"));
    const { r: dispatchInboundMessageWithDispatcher } = await import(
      modulo("dispatch-V82RCNJs.js"));
    const typedHooks = [];
    const plugin = criarPluginDaTrilha({
      buscar: async () => new Response(JSON.stringify({
        codigo: "OPERACAO_APLICADA",
        recibo: {
          identificadorDaOperacao: IDENTIFICADOR_DA_OPERACAO,
          tipo: "REGISTRAR_ESTUDO",
          estado: "APLICADA",
          aplicadaEm: "2026-07-26T18:00:00Z",
          resultado: {
            tipo: "REGISTRAR_ESTUDO",
            dados: {},
          },
        },
      }), { status: 200 }),
    });
    plugin.register({
      pluginConfig: {
        identificadorDaContaDoBot: "default",
      },
      logger: { info() {}, warn() {} },
      registerCommand() {},
      on(hookName, handler, options) {
        typedHooks.push({
          pluginId: "trilha-aprovacao",
          hookName,
          handler,
          priority: options?.priority,
          timeoutMs: options?.timeoutMs,
          source: "teste-oci",
        });
      },
    });
    initializeGlobalHookRunner({
      hooks: [],
      typedHooks,
      plugins: [{ id: "trilha-aprovacao", status: "loaded" }],
      trustedToolPolicies: [],
    });

    let chamadasDoModelo = 0;
    const entregas = [];
    try {
      const resultado = await dispatchInboundMessageWithDispatcher({
        ctx: contextoCanonico(),
        cfg: {
          plugins: { enabled: false },
          agents: {
            defaults: { workspace: "/tmp/trilha-plugin-runtime-test" },
          },
        },
        dispatcherOptions: {
          async deliver(payload) {
            entregas.push(payload);
          },
        },
        async replyResolver() {
          chamadasDoModelo += 1;
          return { text: "resposta indevida do modelo" };
        },
        replyOptions: {},
      });

      assert.equal(chamadasDoModelo, 0);
      assert.deepEqual(entregas, [{
        text: MENSAGENS.confirmacaoAplicada + IDENTIFICADOR_DA_OPERACAO,
      }]);
      assert.equal(resultado.queuedFinal, true);
      assert.equal(resultado.counts.final, 1);
    } finally {
      resetGlobalHookRunner();
    }
  });

test("runtime Codex carrega proxy MCP do bundle allowlisted no workspace",
  somenteNaImagemOficial, async () => {
    const { n: carregarConfiguracaoMcp } = await import(
      modulo("codex-mcp-config-C1g0iV-w.js"));
    const workspace = await mkdtemp(path.join(tmpdir(), "trilha-mcp-runtime-"));
    const nomeDoPlugin =
      "trilha-mcp-323e4567e89b42d3a456426614174002";
    const diretorioDoPlugin = path.join(
      workspace, ".openclaw", "extensions", nomeDoPlugin);
    try {
      await mkdir(path.join(diretorioDoPlugin, ".codex-plugin"), {
        recursive: true,
      });
      await writeFile(path.join(
        diretorioDoPlugin, ".codex-plugin", "plugin.json"), JSON.stringify({
        name: nomeDoPlugin,
        version: "1.0.0",
        mcpServers: [".mcp.json"],
      }));
      await writeFile(path.join(diretorioDoPlugin, ".mcp.json"),
        JSON.stringify({
          mcpServers: {
            trilha: {
              command: "node",
              args: [
                "./proxy-mcp-http-stdio.mjs",
                "http://broker-credenciais:18890/mcp/"
                  + "323e4567-e89b-42d3-a456-426614174002",
              ],
              toolFilter: {
                include: [
                  "preparar_registro_de_estudo",
                  "consultar_operacao_assistida",
                ],
              },
            },
          },
        }));

      const carregada = carregarConfiguracaoMcp({
        workspaceDir: workspace,
        cfg: {
          plugins: {
            enabled: true,
            allow: [nomeDoPlugin],
          },
        },
        toolsEnabled: true,
      });
      assert.deepEqual(Object.keys(
        carregada.configPatch?.mcp_servers ?? {}), ["trilha"]);
      assert.deepEqual(
        carregada.configPatch.mcp_servers.trilha,
        {
          command: "node",
          args: [
            path.join(diretorioDoPlugin, "proxy-mcp-http-stdio.mjs"),
            "http://broker-credenciais:18890/mcp/"
              + "323e4567-e89b-42d3-a456-426614174002",
          ],
          cwd: diretorioDoPlugin,
        },
      );
      assert.deepEqual(
        JSON.parse(await readFile(
          path.join(diretorioDoPlugin, ".mcp.json"),
          "utf8",
        )).mcpServers.trilha.toolFilter.include,
        [
          "preparar_registro_de_estudo",
          "consultar_operacao_assistida",
        ]);
      assert.equal(carregada.diagnostics.length, 0);
    } finally {
      await rm(workspace, { recursive: true });
    }
  });
