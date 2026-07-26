import assert from "node:assert/strict";
import { chmod, mkdtemp, rm, symlink, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import path from "node:path";
import test from "node:test";
import {
  autorizarRequisicao,
  carregarFerramentasPermitidas,
  filtrarResposta,
  validarDestino,
} from "../scripts/proxy-mcp-http-stdio.mjs";

const ferramentasPermitidas = new Set([
  "consultar_operacao_assistida",
  "preparar_registro_de_estudo",
]);

test("aceita somente destino interno do broker", () => {
  assert.equal(
    validarDestino(
      "http://broker-credenciais:18890/mcp/"
        + "123e4567-e89b-12d3-a456-426614174000",
    ).pathname,
    "/mcp/123e4567-e89b-12d3-a456-426614174000",
  );
  for (const destino of [
    "https://broker-credenciais:18890/mcp/123e4567-e89b-12d3-a456-426614174000",
    "http://externo:18890/mcp/123e4567-e89b-12d3-a456-426614174000",
    "http://broker-credenciais:18890/mcp/invalido",
    "http://broker-credenciais:18890/mcp/00000000-0000-0000-0000-000000000000",
    "http://usuario@broker-credenciais:18890/mcp/123e4567-e89b-12d3-a456-426614174000",
  ]) {
    assert.throws(() => validarDestino(destino));
  }
});

test("carrega allowlist regular 0600 e rejeita politica insegura", async () => {
  const diretorio = await mkdtemp(path.join(tmpdir(), "trilha-proxy-"));
  const politica = path.join(diretorio, ".mcp.json");
  const simbolico = path.join(diretorio, "politica-simbolica.json");
  try {
    await writeFile(politica, JSON.stringify({
      mcpServers: {
        trilha: {
          toolFilter: {
            include: [...ferramentasPermitidas],
          },
        },
      },
    }), { mode: 0o600 });
    assert.deepEqual(
      [...await carregarFerramentasPermitidas(politica)],
      [...ferramentasPermitidas],
    );

    await chmod(politica, 0o644);
    await assert.rejects(carregarFerramentasPermitidas(politica));
    await chmod(politica, 0o400);
    await assert.rejects(carregarFerramentasPermitidas(politica));
    await chmod(politica, 0o600);
    await symlink(politica, simbolico);
    await assert.rejects(carregarFerramentasPermitidas(simbolico));

    await writeFile(politica, JSON.stringify({
      mcpServers: {
        trilha: {
          toolFilter: {
            include: [
              "consultar_operacao_assistida",
              "consultar_operacao_assistida",
            ],
          },
        },
      },
    }), { mode: 0o600 });
    await assert.rejects(carregarFerramentasPermitidas(politica));
  } finally {
    await rm(diretorio, { recursive: true });
  }
});

test("nega chamada fora da allowlist antes do servidor", () => {
  assert.deepEqual(
    autorizarRequisicao({
      jsonrpc: "2.0",
      id: 1,
      method: "tools/call",
      params: { name: "preparar_registro_de_estudo", arguments: {} },
    }, ferramentasPermitidas),
    { autorizada: true },
  );
  assert.deepEqual(
    autorizarRequisicao({
      jsonrpc: "2.0",
      id: 2,
      method: "tools/call",
      params: { name: "executar_shell", arguments: {} },
    }, ferramentasPermitidas),
    {
      autorizada: false,
      resposta: {
        jsonrpc: "2.0",
        id: 2,
        error: { code: -32601, message: "Ferramenta MCP nao permitida." },
      },
    },
  );
  assert.equal(
    autorizarRequisicao({
      jsonrpc: "2.0",
      method: "tools/call",
      params: { name: "executar_shell", arguments: {} },
    }, ferramentasPermitidas).autorizada,
    false,
  );
});

test("filtra tools/list e preserva paginacao", () => {
  const requisicao = {
    jsonrpc: "2.0",
    id: "lista-1",
    method: "tools/list",
  };
  const resposta = {
    jsonrpc: "2.0",
    id: "lista-1",
    result: {
      nextCursor: "pagina-2",
      tools: [
        { name: "consultar_operacao_assistida" },
        { name: "executar_shell" },
        { name: "preparar_registro_de_estudo" },
      ],
    },
  };
  assert.deepEqual(
    filtrarResposta(requisicao, resposta, ferramentasPermitidas),
    {
      jsonrpc: "2.0",
      id: "lista-1",
      result: {
        nextCursor: "pagina-2",
        tools: [
          { name: "consultar_operacao_assistida" },
          { name: "preparar_registro_de_estudo" },
        ],
      },
    },
  );
  assert.equal(resposta.result.tools.length, 3);
});
