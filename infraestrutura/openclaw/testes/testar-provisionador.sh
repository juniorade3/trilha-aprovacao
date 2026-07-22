#!/usr/bin/env bash
set -Eeuo pipefail

diretorio_do_teste="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
diretorio_do_modulo="$(cd -- "${diretorio_do_teste}/.." && pwd)"
temporario="$(mktemp -d)"
processos=()
limpar_teste() {
  for processo in "${processos[@]}"; do kill "${processo}" 2>/dev/null || true; done
  rm -rf -- "${temporario}"
}
trap limpar_teste EXIT

estado="${temporario}/estado"
credenciais="${temporario}/credenciais-mcp"
token_um="${temporario}/token-um"
token_dois="${temporario}/token-dois"
token_inseguro="${temporario}/token-inseguro"
token_simbolico="${temporario}/token-simbolico"
valor_token_um='mcp_AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA'
valor_token_dois='mcp_BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB'
printf '%s\n' "${valor_token_um}" > "${token_um}"
printf '%s\n' "${valor_token_dois}" > "${token_dois}"
cp "${token_um}" "${token_inseguro}"
chmod 600 -- "${token_um}" "${token_dois}"
chmod 644 -- "${token_inseguro}"
ln -s "${token_um}" "${token_simbolico}"

vinculo_um="123e4567-e89b-42d3-a456-426614174000"
vinculo_dois="223e4567-e89b-42d3-a456-426614174001"
vinculo_tres="323e4567-e89b-42d3-a456-426614174002"
agente_um="trilha_a123"
agente_dois="trilha_b456"

"${diretorio_do_modulo}/scripts/inicializar-estado.sh" \
  --diretorio-estado "${estado}" \
  --diretorio-credenciais-mcp "${credenciais}" >/dev/null
[[ "$(stat -c '%a' "${estado}")" == "700" ]]
[[ "$(stat -c '%a' "${credenciais}")" == "700" ]]

if "${diretorio_do_modulo}/scripts/provisionar-vinculo.sh" \
  --diretorio-estado "${estado}" --diretorio-credenciais-mcp "${credenciais}" \
  --identificador-vinculo "${vinculo_tres}" --identificador-bot 700000001 \
  --identificador-telegram 800000003 --identificador-chat 800000003 \
  --identificador-agente trilha_inseguro --identificador-sessao "sessao:${vinculo_tres}" \
  --token-mcp-arquivo "${token_inseguro}" --url-mcp http://127.0.0.1:8080/mcp \
  >/dev/null 2>&1; then
  printf 'Falha: token com permissao diferente de 0600 foi aceito.\n' >&2
  exit 1
fi
if "${diretorio_do_modulo}/scripts/provisionar-vinculo.sh" \
  --diretorio-estado "${estado}" --diretorio-credenciais-mcp "${credenciais}" \
  --identificador-vinculo "${vinculo_tres}" --identificador-bot 700000001 \
  --identificador-telegram 800000003 --identificador-chat 800000003 \
  --identificador-agente trilha_simbolico --identificador-sessao "sessao:${vinculo_tres}" \
  --token-mcp-arquivo "${token_simbolico}" --url-mcp http://127.0.0.1:8080/mcp \
  >/dev/null 2>&1; then
  printf 'Falha: token por link simbolico foi aceito.\n' >&2
  exit 1
fi

porta_mcp="${temporario}/porta-mcp"
resultado_mcp="${temporario}/resultado-mcp"
node "${diretorio_do_modulo}/testes/servidor-mcp-falso.mjs" \
  "${porta_mcp}" "${resultado_mcp}" "${valor_token_um}" "${agente_um}" "sessao:${vinculo_um}" &
processo_mcp=$!
processos+=("${processo_mcp}")
for _ in {1..50}; do [[ -s "${porta_mcp}" ]] && break; sleep 0.05; done
[[ -s "${porta_mcp}" ]]
url_mcp="http://127.0.0.1:$(<"${porta_mcp}")/mcp"

argumentos=(
  --diretorio-estado "${estado}"
  --diretorio-credenciais-mcp "${credenciais}"
  --identificador-vinculo "${vinculo_um}"
  --identificador-bot 700000001
  --identificador-telegram 800000001
  --identificador-chat 800000001
  --identificador-agente "${agente_um}"
  --identificador-sessao "sessao:${vinculo_um}"
  --token-mcp-arquivo "${token_um}"
  --url-mcp "${url_mcp}"
)
"${diretorio_do_modulo}/scripts/provisionar-vinculo.sh" "${argumentos[@]}" >/dev/null
"${diretorio_do_modulo}/scripts/provisionar-vinculo.sh" "${argumentos[@]}" >/dev/null

configuracao="${estado}/openclaw.json"
plugin_um="trilha-mcp-${vinculo_um//-/}"
arquivo_mcp_um="${estado}/workspaces/${agente_um}/.openclaw/extensions/${plugin_um}/.mcp.json"
arquivo_credencial_um="${credenciais}/${vinculo_um}.json"
jq -e --arg agente "${agente_um}" '.agents.list | length == 1 and .[0].id == $agente' \
  "${configuracao}" >/dev/null
jq -e '(.bindings | length == 1) and .channels.telegram.allowFrom == ["800000001"]' \
  "${configuracao}" >/dev/null
jq -e \
  '.plugins.allow == ["trilha-aprovacao"] and
   .plugins.entries["trilha-aprovacao"].enabled == true and .mcp.servers == {}' \
  "${configuracao}" >/dev/null
jq -e --arg vinculo "${vinculo_um}" \
  '.mcpServers.trilha.url == ("http://broker-credenciais:18890/mcp/" + $vinculo) and
   (.mcpServers.trilha | has("headers") | not) and
   (.mcpServers.trilha.toolFilter.include | length == 24)' "${arquivo_mcp_um}" >/dev/null
jq -e --arg token "${valor_token_um}" --arg agente "${agente_um}" \
  --arg sessao "sessao:${vinculo_um}" \
  '.tokenMcp == $token and .identificadorDoAgente == $agente and
   .identificadorDaSessao == $sessao' "${arquivo_credencial_um}" >/dev/null
[[ -f "${arquivo_credencial_um}" && ! -L "${arquivo_credencial_um}" ]]
[[ "$(stat -c '%a' "${arquivo_credencial_um}")" == "600" ]]
! grep -R -q -- "${valor_token_um}" "${estado}"

porta_broker="${temporario}/porta-broker"
node "${diretorio_do_modulo}/scripts/broker-de-credenciais-mcp.mjs" \
  "${credenciais}" 0 "${porta_broker}" &
processo_broker=$!
processos+=("${processo_broker}")
for _ in {1..50}; do [[ -s "${porta_broker}" ]] && break; sleep 0.05; done
[[ -s "${porta_broker}" ]]
chmod 644 "${arquivo_credencial_um}"
[[ "$(curl -sS -o /dev/null -w '%{http_code}' -X POST \
  "http://127.0.0.1:$(<"${porta_broker}")/mcp/${vinculo_um}" \
  -H 'Content-Type: application/json' --data '{"jsonrpc":"2.0","id":1,"method":"tools/list"}')" == "503" ]]
chmod 600 "${arquivo_credencial_um}"
[[ "$(curl -sS -o "${temporario}/resposta-mcp" -w '%{http_code}' -X POST \
  "http://127.0.0.1:$(<"${porta_broker}")/mcp/${vinculo_um}" \
  -H 'Content-Type: application/json' --data '{"jsonrpc":"2.0","id":1,"method":"tools/list"}')" == "200" ]]
wait "${processo_mcp}"
processos=("${processo_broker}")
[[ "$(<"${resultado_mcp}")" == "AUTENTICADO" ]]

if "${diretorio_do_modulo}/scripts/provisionar-vinculo.sh" \
  --diretorio-estado "${estado}" --diretorio-credenciais-mcp "${credenciais}" \
  --identificador-vinculo "${vinculo_dois}" --identificador-bot 700000001 \
  --identificador-telegram 800000001 --identificador-chat 800000001 \
  --identificador-agente "${agente_dois}" --identificador-sessao "sessao:${vinculo_dois}" \
  --token-mcp-arquivo "${token_dois}" --url-mcp "${url_mcp}" >/dev/null 2>&1; then
  printf 'Falha: Telegram duplicado foi aceito fora de uma rotacao.\n' >&2
  exit 1
fi

segredo_do_gateway="segredo-de-teste-do-gateway-com-mais-de-32-caracteres"
arquivo_do_segredo="${temporario}/segredo-gateway"
arquivo_do_segredo_inseguro="${temporario}/segredo-gateway-inseguro"
arquivo_do_segredo_simbolico="${temporario}/segredo-gateway-simbolico"
printf '%s\n' "${segredo_do_gateway}" > "${arquivo_do_segredo}"
cp "${arquivo_do_segredo}" "${arquivo_do_segredo_inseguro}"
chmod 600 "${arquivo_do_segredo}"
chmod 644 "${arquivo_do_segredo_inseguro}"
ln -s "${arquivo_do_segredo}" "${arquivo_do_segredo_simbolico}"
for segredo_invalido in "${arquivo_do_segredo_inseguro}" "${arquivo_do_segredo_simbolico}"; do
  if "${diretorio_do_modulo}/scripts/registrar-provisionamento.sh" \
    --diretorio-estado "${estado}" --identificador-vinculo "${vinculo_um}" \
    --url-backend http://127.0.0.1:1 --identificador-chave gateway-teste \
    --segredo-gateway-arquivo "${segredo_invalido}" >/dev/null 2>&1; then
    printf 'Falha: segredo HMAC inseguro foi aceito.\n' >&2
    exit 1
  fi
done

arquivo_da_porta="${temporario}/porta-gateway"
node "${diretorio_do_modulo}/testes/servidor-confiavel-falso.mjs" \
  "${arquivo_da_porta}" "${segredo_do_gateway}" gateway-teste "${vinculo_um}" &
processo_gateway=$!
processos+=("${processo_gateway}")
for _ in {1..50}; do [[ -s "${arquivo_da_porta}" ]] && break; sleep 0.05; done
[[ -s "${arquivo_da_porta}" ]]
"${diretorio_do_modulo}/scripts/registrar-provisionamento.sh" \
  --diretorio-estado "${estado}" --identificador-vinculo "${vinculo_um}" \
  --url-backend "http://127.0.0.1:$(<"${arquivo_da_porta}")" \
  --identificador-chave gateway-teste --segredo-gateway-arquivo "${arquivo_do_segredo}" >/dev/null
wait "${processo_gateway}"
processos=("${processo_broker}")
jq -e '.registradoNoBackendEm != null' "${estado}/provisionamentos/${vinculo_um}.json" >/dev/null

"${diretorio_do_modulo}/scripts/rotacionar-token-mcp.sh" \
  --diretorio-estado "${estado}" --diretorio-credenciais-mcp "${credenciais}" \
  --identificador-vinculo-anterior "${vinculo_um}" --identificador-vinculo-novo "${vinculo_dois}" \
  --identificador-bot 700000001 --identificador-telegram 800000001 --identificador-chat 800000001 \
  --identificador-agente "${agente_dois}" --identificador-sessao "sessao:${vinculo_dois}" \
  --token-mcp-arquivo "${token_dois}" --url-mcp "${url_mcp}" >/dev/null
"${diretorio_do_modulo}/scripts/rotacionar-token-mcp.sh" \
  --diretorio-estado "${estado}" --diretorio-credenciais-mcp "${credenciais}" \
  --identificador-vinculo-anterior "${vinculo_um}" --identificador-vinculo-novo "${vinculo_dois}" \
  --identificador-bot 700000001 --identificador-telegram 800000001 --identificador-chat 800000001 \
  --identificador-agente "${agente_dois}" --identificador-sessao "sessao:${vinculo_dois}" \
  --token-mcp-arquivo "${token_dois}" --url-mcp "${url_mcp}" >/dev/null

plugin_dois="trilha-mcp-${vinculo_dois//-/}"
arquivo_mcp_dois="${estado}/workspaces/${agente_dois}/.openclaw/extensions/${plugin_dois}/.mcp.json"
arquivo_credencial_dois="${credenciais}/${vinculo_dois}.json"
jq -e --arg agente "${agente_dois}" '.agents.list | length == 1 and .[0].id == $agente' \
  "${configuracao}" >/dev/null
jq -e '(.bindings | length == 1) and .channels.telegram.allowFrom == ["800000001"]' \
  "${configuracao}" >/dev/null
jq -e --arg novo "${vinculo_dois}" \
  '.estado == "REVOGADO" and .substituidoPor == $novo and (has("hashDoToken") | not)' \
  "${estado}/provisionamentos/${vinculo_um}.json" >/dev/null
jq -e '.estado == "ATIVO"' "${estado}/provisionamentos/${vinculo_dois}.json" >/dev/null
[[ ! -e "${arquivo_credencial_um}" ]]
[[ -f "${arquivo_credencial_dois}" && ! -L "${arquivo_credencial_dois}" ]]
[[ "$(stat -c '%a' "${arquivo_credencial_dois}")" == "600" ]]
jq -e --arg token "${valor_token_dois}" '.tokenMcp == $token' "${arquivo_credencial_dois}" >/dev/null
[[ -f "${arquivo_mcp_dois}" ]]
[[ -d "${estado}/revogados/${vinculo_um}/workspace" ]]
! grep -R -q -- "${valor_token_um}" "${estado}" "${credenciais}"
! grep -R -q -- "${valor_token_dois}" "${estado}"

"${diretorio_do_modulo}/scripts/revogar-vinculo.sh" \
  --diretorio-estado "${estado}" --diretorio-credenciais-mcp "${credenciais}" \
  --identificador-vinculo "${vinculo_dois}" >/dev/null
"${diretorio_do_modulo}/scripts/revogar-vinculo.sh" \
  --diretorio-estado "${estado}" --diretorio-credenciais-mcp "${credenciais}" \
  --identificador-vinculo "${vinculo_dois}" >/dev/null
jq -e '.agents.list == [] and .bindings == [] and .channels.telegram.allowFrom == [] and
  .plugins.allow == ["trilha-aprovacao"] and .plugins.entries["trilha-aprovacao"].enabled == true' \
  "${configuracao}" >/dev/null
jq -e '.estado == "REVOGADO" and (has("hashDoToken") | not)' \
  "${estado}/provisionamentos/${vinculo_dois}.json" >/dev/null
[[ ! -e "${arquivo_credencial_dois}" ]]
! grep -R -q -- "${valor_token_dois}" "${estado}" "${credenciais}"

kill "${processo_broker}" 2>/dev/null || true
wait "${processo_broker}" 2>/dev/null || true
processos=()
printf 'Testes do provisionador e do broker de credenciais concluidos.\n'
