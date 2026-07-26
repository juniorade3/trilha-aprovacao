#!/usr/bin/env bash
set -Eeuo pipefail

source "$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)/biblioteca.sh"

diretorio_de_estado=""
identificador_da_conta_do_bot=""

while [[ $# -gt 0 ]]; do
  case "$1" in
    --diretorio-estado) diretorio_de_estado="${2:-}"; shift 2 ;;
    --identificador-conta-bot)
      identificador_da_conta_do_bot="${2:-}"
      shift 2
      ;;
    *) falhar "argumento desconhecido: $1" ;;
  esac
done

for comando in jq flock realpath install mktemp sha256sum stat; do
  exigir_comando "${comando}"
done
exigir_argumento --diretorio-estado "${diretorio_de_estado}"
exigir_argumento --identificador-conta-bot "${identificador_da_conta_do_bot}"
validar_identificador_da_conta_do_bot "${identificador_da_conta_do_bot}"

diretorio_de_estado="$(normalizar_diretorio_de_estado "${diretorio_de_estado}")"
inicializar_estado "${diretorio_de_estado}"
validar_permissoes_do_estado "${diretorio_de_estado}"
adquirir_bloqueio "${diretorio_de_estado}"
validar_modelo_do_workspace

arquivo_da_configuracao="${diretorio_de_estado}/openclaw.json"
diretorio_de_temporarios="${diretorio_de_estado}/temporarios"
modelo_do_workspace="$(modelo_do_workspace_em_json)"
configuracao_final=""
adaptador_temporario=""
limpar_temporario_da_sincronizacao() {
  if [[ -n "${configuracao_final}" ]]; then
    rm -f -- "${configuracao_final}"
  fi
  if [[ -n "${adaptador_temporario}" ]]; then
    rm -rf -- "${adaptador_temporario}"
  fi
}
trap limpar_temporario_da_sincronizacao EXIT

shopt -s nullglob
provisionamentos_ativos=()
for arquivo_do_provisionamento in \
  "${diretorio_de_estado}"/provisionamentos/*.json; do
  nome="${arquivo_do_provisionamento##*/}"
  identificador_do_vinculo="${nome%.json}"
  [[ "${identificador_do_vinculo}" =~ ^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-8][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$ ]] ||
    continue
  [[ -f "${arquivo_do_provisionamento}" &&
    ! -L "${arquivo_do_provisionamento}" &&
    "$(stat -c '%a' -- "${arquivo_do_provisionamento}")" == "600" ]] ||
    falhar "metadados de provisionamento inseguros: ${identificador_do_vinculo}."
  jq empty "${arquivo_do_provisionamento}" ||
    falhar "metadados de provisionamento invalidos: ${identificador_do_vinculo}."
  [[ "$(jq -r '.estado' "${arquivo_do_provisionamento}")" == "ATIVO" ]] ||
    continue

  jq -e \
    --arg vinculo "${identificador_do_vinculo}" \
    --arg conta "${identificador_da_conta_do_bot}" \
    '(.versao == 2 or .versao == 3) and
     .identificadorDoVinculo == $vinculo and
     (.identificadorDaContaDoBot // $conta) == $conta and
     (.identificadorDoBot | type == "string") and
     (.identificadorDoTelegram | type == "string") and
     (.identificadorDoChat | type == "string") and
     (.identificadorDoAgente | type == "string") and
     (.identificadorDaSessao | type == "string")' \
    "${arquivo_do_provisionamento}" >/dev/null ||
    falhar "metadados ativos incompativeis: ${identificador_do_vinculo}."

  identificador_do_agente="$(
    jq -r '.identificadorDoAgente' "${arquivo_do_provisionamento}")"
  identificador_do_bot="$(
    jq -r '.identificadorDoBot' "${arquivo_do_provisionamento}")"
  identificador_do_telegram="$(
    jq -r '.identificadorDoTelegram' "${arquivo_do_provisionamento}")"
  identificador_do_chat="$(
    jq -r '.identificadorDoChat' "${arquivo_do_provisionamento}")"
  identificador_da_sessao="$(
    jq -r '.identificadorDaSessao' "${arquivo_do_provisionamento}")"
  nome_do_plugin="trilha-mcp-${identificador_do_vinculo//-/}"
  validar_identificador_do_agente "${identificador_do_agente}"
  validar_inteiro_positivo "${identificador_do_bot}" identificador-do-bot
  validar_inteiro_positivo "${identificador_do_telegram}" identificador-do-telegram
  validar_inteiro_positivo "${identificador_do_chat}" identificador-do-chat
  validar_identificador_da_sessao "${identificador_da_sessao}"
  jq -e --arg plugin "${nome_do_plugin}" \
    '.nomeDoPlugin == $plugin' "${arquivo_do_provisionamento}" >/dev/null ||
    falhar "plugin MCP divergente: ${identificador_do_vinculo}."

  caminho_do_workspace_no_container="/home/node/.openclaw/workspaces/${identificador_do_agente}"
  jq -e --arg agente "${identificador_do_agente}" \
    --arg workspace "${caminho_do_workspace_no_container}" \
    '([.agents.list[]? | select(.id == $agente)] | length) == 1 and
     (.agents.list[] | select(.id == $agente) | .workspace) == $workspace' \
    "${arquivo_da_configuracao}" >/dev/null ||
    falhar "agente ativo ausente ou duplicado: ${identificador_do_agente}."
  jq -e --arg agente "${identificador_do_agente}" \
    '([.bindings[]? | select(.agentId == $agente)] | length) == 1 and
     (.bindings[] | select(.agentId == $agente) | .type) == "route" and
     (.bindings[] | select(.agentId == $agente) | .match.channel)
       == "telegram" and
     (.bindings[] | select(.agentId == $agente) | .match.peer.kind)
       == "direct"' "${arquivo_da_configuracao}" >/dev/null ||
    falhar "binding ativo ausente ou duplicado: ${identificador_do_agente}."

  workspace="${diretorio_de_estado}/workspaces/${identificador_do_agente}"
  validar_workspace_gerenciado "${workspace}"
  provisionamentos_ativos+=("${arquivo_do_provisionamento}")
done

arquivo_do_bot="${diretorio_de_estado}/provisionamentos/bot.json"
if [[ -e "${arquivo_do_bot}" || -L "${arquivo_do_bot}" ]]; then
  [[ -f "${arquivo_do_bot}" && ! -L "${arquivo_do_bot}" &&
    "$(stat -c '%a' -- "${arquivo_do_bot}")" == "600" ]] ||
    falhar "metadados do bot devem ser arquivo regular 0600."
  jq -e --arg conta "${identificador_da_conta_do_bot}" \
    '(.identificadorDaContaDoBot // $conta) == $conta' \
    "${arquivo_do_bot}" >/dev/null ||
    falhar "estado pertence a outra conta do bot."
fi

configuracao_final="$(
  mktemp "${diretorio_de_temporarios}/configuracao-final.XXXXXX")"
configurar_conta_do_telegram "${arquivo_da_configuracao}" \
  "${configuracao_final}" "${identificador_da_conta_do_bot}"
chmod 600 -- "${configuracao_final}"

for arquivo_do_provisionamento in "${provisionamentos_ativos[@]}"; do
  identificador_do_vinculo="$(
    jq -r '.identificadorDoVinculo' "${arquivo_do_provisionamento}")"
  identificador_do_agente="$(
    jq -r '.identificadorDoAgente' "${arquivo_do_provisionamento}")"
  identificador_do_chat="$(
    jq -r '.identificadorDoChat' "${arquivo_do_provisionamento}")"
  nome_do_plugin="trilha-mcp-${identificador_do_vinculo//-/}"
  workspace="${diretorio_de_estado}/workspaces/${identificador_do_agente}"
  instalar_modelo_do_workspace "${workspace}" "${diretorio_de_temporarios}"
  diretorio_do_plugin="${workspace}/.openclaw/extensions/${nome_do_plugin}"
  diretorio_do_manifesto="${diretorio_do_plugin}/.codex-plugin"
  for diretorio in \
    "${workspace}/.openclaw" \
    "${workspace}/.openclaw/extensions" \
    "${diretorio_do_plugin}"; do
    [[ -d "${diretorio}" && ! -L "${diretorio}" ]] ||
      falhar "diretorio do adaptador MCP inseguro: ${identificador_do_vinculo}."
  done
  if [[ -e "${diretorio_do_manifesto}" ||
    -L "${diretorio_do_manifesto}" ]]; then
    [[ -d "${diretorio_do_manifesto}" &&
      ! -L "${diretorio_do_manifesto}" ]] ||
      falhar "diretorio do manifesto MCP inseguro: ${identificador_do_vinculo}."
  else
    install -d -m 700 -- "${diretorio_do_manifesto}"
  fi
  for alvo in \
    "${diretorio_do_plugin}/proxy-mcp-http-stdio.mjs" \
    "${diretorio_do_manifesto}/plugin.json" \
    "${diretorio_do_plugin}/.mcp.json"; do
    if [[ -e "${alvo}" || -L "${alvo}" ]]; then
      [[ -f "${alvo}" && ! -L "${alvo}" ]] ||
        falhar "arquivo gerenciado do adaptador MCP inseguro: ${identificador_do_vinculo}."
    fi
  done
  adaptador_temporario="$(
    mktemp -d "${diretorio_de_temporarios}/adaptador-mcp.XXXXXX")"
  chmod 700 -- "${adaptador_temporario}"
  install -m 500 -- \
    "${diretorio_do_modulo}/scripts/proxy-mcp-http-stdio.mjs" \
    "${adaptador_temporario}/proxy-mcp-http-stdio.mjs"
  jq -n --arg nome "${nome_do_plugin}" '{
    name: $nome, version: "1.0.0",
    description: "Adaptador MCP isolado da Trilha da Aprovacao",
    mcpServers: [".mcp.json"]
  }' > "${adaptador_temporario}/plugin.json"
  ferramentas="$(ferramentas_mcp_em_json)"
  jq -n --arg vinculo "${identificador_do_vinculo}" \
    --argjson ferramentas "${ferramentas}" \
    '{mcpServers: {trilha: {
      command: "node",
      args: ["./proxy-mcp-http-stdio.mjs", ("http://broker-credenciais:18890/mcp/" + $vinculo)],
      toolFilter: {include: $ferramentas}
    }}}' > "${adaptador_temporario}/mcp.json"
  chmod 600 -- "${adaptador_temporario}/plugin.json" \
    "${adaptador_temporario}/mcp.json"
  mv -f -- "${adaptador_temporario}/proxy-mcp-http-stdio.mjs" \
    "${diretorio_do_plugin}/proxy-mcp-http-stdio.mjs"
  mv -f -- "${adaptador_temporario}/plugin.json" \
    "${diretorio_do_manifesto}/plugin.json"
  mv -f -- "${adaptador_temporario}/mcp.json" \
    "${diretorio_do_plugin}/.mcp.json"
  rmdir -- "${adaptador_temporario}"
  adaptador_temporario=""
  temporario_da_configuracao="$(
    mktemp "${diretorio_de_temporarios}/configuracao-workspace.XXXXXX")"
  jq \
    --arg agente "${identificador_do_agente}" \
    --arg conta "${identificador_da_conta_do_bot}" \
    --arg chat "${identificador_do_chat}" \
    --arg plugin "${nome_do_plugin}" \
    '(.bindings[] | select(.agentId == $agente) | .match.accountId) = $conta
     | (.bindings[] | select(.agentId == $agente) | .match.peer.id) = $chat
     | .plugins.allow = (((.plugins.allow // []) + [$plugin]) | unique)' \
    "${configuracao_final}" > "${temporario_da_configuracao}"
  chmod 600 -- "${temporario_da_configuracao}"
  mv -- "${temporario_da_configuracao}" "${configuracao_final}"

  metadados_temporarios="$(
    mktemp "${diretorio_de_temporarios}/metadados-workspace.XXXXXX")"
  jq \
    --arg conta "${identificador_da_conta_do_bot}" \
    --argjson modelo "${modelo_do_workspace}" \
    '.versao = 3
     | .identificadorDaContaDoBot = $conta
     | .modeloDoWorkspace = $modelo' \
    "${arquivo_do_provisionamento}" > "${metadados_temporarios}"
  chmod 600 -- "${metadados_temporarios}"
  mv -- "${metadados_temporarios}" "${arquivo_do_provisionamento}"
done

if [[ -e "${arquivo_do_bot}" || -L "${arquivo_do_bot}" ]]; then
  metadados_do_bot_temporarios="$(
    mktemp "${diretorio_de_temporarios}/metadados-bot.XXXXXX")"
  jq --arg conta "${identificador_da_conta_do_bot}" \
    '.identificadorDaContaDoBot = $conta' "${arquivo_do_bot}" \
    > "${metadados_do_bot_temporarios}"
  chmod 600 -- "${metadados_do_bot_temporarios}"
  mv -- "${metadados_do_bot_temporarios}" "${arquivo_do_bot}"
fi

jq empty "${configuracao_final}"
mv -- "${configuracao_final}" "${arquivo_da_configuracao}"
configuracao_final=""
trap - EXIT

printf 'Workspaces ativos sincronizados com modelo gerenciado versao %s.\n' \
  "$(jq -r '.versao' \
    "${diretorio_do_modulo}/modelos/workspace/manifesto.json")"
