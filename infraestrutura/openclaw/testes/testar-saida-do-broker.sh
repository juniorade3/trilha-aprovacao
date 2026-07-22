#!/usr/bin/env bash
set -Eeuo pipefail

diretorio_do_teste="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
diretorio_do_modulo="$(cd -- "${diretorio_do_teste}/.." && pwd)"
temporario="$(mktemp -d)"
projeto="trilha-openclaw-validacao-$RANDOM-$$"
processo_do_backend=""

compor() {
  OPENCLAW_DIRETORIO_ESTADO="${temporario}/estado" \
  OPENCLAW_DIRETORIO_CREDENCIAIS_MCP="${temporario}/credenciais-mcp" \
  OPENCLAW_ARQUIVO_SEGREDOS="${temporario}/segredos.json" \
  OPENCLAW_ARQUIVO_IDENTIFICADOR_BOT="${temporario}/identificador-bot" \
  OPENCLAW_ARQUIVO_SEGREDO_GATEWAY="${temporario}/segredo-gateway" \
    docker compose -p "${projeto}" -f "${diretorio_do_modulo}/compose.yaml" "$@"
}

limpar() {
  compor down --remove-orphans --volumes >/dev/null 2>&1 || true
  if [[ -n "${processo_do_backend}" ]]; then
    kill "${processo_do_backend}" >/dev/null 2>&1 || true
    wait "${processo_do_backend}" >/dev/null 2>&1 || true
  fi
  rm -rf -- "${temporario}"
}
trap limpar EXIT

mkdir -m 700 "${temporario}/estado" "${temporario}/credenciais-mcp"
mkdir -m 700 "${temporario}/estado/extensions" \
  "${temporario}/estado/extensions/trilha-aprovacao"
printf '{}\n' > "${temporario}/segredos.json"
printf '700000001\n' > "${temporario}/identificador-bot"
printf 'segredo-de-teste-do-gateway-com-mais-de-trinta-e-dois-bytes\n' \
  > "${temporario}/segredo-gateway"
chmod 600 "${temporario}/segredos.json" "${temporario}/identificador-bot" \
  "${temporario}/segredo-gateway"

vinculo="423e4567-e89b-42d3-a456-426614174003"
agente="trilha_teste_saida"
sessao="sessao:teste-saida"
token="mcp_DDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDD"
arquivo_da_porta="${temporario}/porta-backend"
arquivo_do_resultado="${temporario}/resultado-backend"
node "${diretorio_do_modulo}/testes/servidor-mcp-falso.mjs" \
  "${arquivo_da_porta}" "${arquivo_do_resultado}" "${token}" \
  "${agente}" "${sessao}" 0.0.0.0 &
processo_do_backend=$!
for _ in {1..100}; do
  [[ -s "${arquivo_da_porta}" ]] && break
  sleep 0.05
done
[[ -s "${arquivo_da_porta}" ]]

jq -n --arg vinculo "${vinculo}" --arg agente "${agente}" \
  --arg sessao "${sessao}" --arg token "${token}" \
  --arg url "http://host.docker.internal:$(<"${arquivo_da_porta}")/mcp" \
  '{versao: 1, identificadorDoVinculo: $vinculo,
    identificadorDoAgente: $agente, identificadorDaSessao: $sessao,
    tokenMcp: $token, urlMcp: $url}' \
  > "${temporario}/credenciais-mcp/${vinculo}.json"
chmod 600 "${temporario}/credenciais-mcp/${vinculo}.json"

compor up --detach --no-deps broker-credenciais >/dev/null
compor run --rm --no-deps gateway node -e '
  const esperar = (ms) => new Promise((resolver) => setTimeout(resolver, ms));
  let ativo = false;
  for (let tentativa = 0; tentativa < 100; tentativa += 1) {
    try {
      const resposta = await fetch("http://broker-credenciais:18890/healthz");
      if (resposta.ok) { ativo = true; break; }
    } catch {}
    await esperar(50);
  }
  if (!ativo) process.exit(2);
  const resposta = await fetch(
    "http://broker-credenciais:18890/mcp/423e4567-e89b-42d3-a456-426614174003",
    {method: "POST", headers: {"Content-Type": "application/json"},
      body: JSON.stringify({jsonrpc: "2.0", id: 1, method: "tools/list"})});
  if (!resposta.ok) process.exit(3);
' >/dev/null

wait "${processo_do_backend}"
processo_do_backend=""
[[ "$(<"${arquivo_do_resultado}")" == "AUTENTICADO" ]]
printf 'Saida real do broker para backend no host validada em container.\n'
