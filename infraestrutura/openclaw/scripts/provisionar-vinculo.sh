#!/usr/bin/env bash
set -Eeuo pipefail

source "$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)/biblioteca.sh"

diretorio_de_estado=""
diretorio_de_credenciais=""
identificador_do_vinculo=""
identificador_do_vinculo_substituido=""
identificador_do_bot=""
identificador_do_telegram=""
identificador_do_chat=""
identificador_do_agente=""
identificador_da_sessao=""
arquivo_do_token=""
url_mcp=""
modelo="openai/gpt-5.5"

uso() {
  printf '%s\n' \
    'Uso: provisionar-vinculo.sh \' \
    '  --diretorio-estado CAMINHO --diretorio-credenciais-mcp CAMINHO \' \
    '  --identificador-vinculo UUID --identificador-bot NUMERO \' \
    '  --identificador-telegram NUMERO --identificador-chat NUMERO \' \
    '  --identificador-agente ID --identificador-sessao ID \' \
    '  --token-mcp-arquivo CAMINHO --url-mcp URL \' \
    '  [--substituir-vinculo UUID] [--modelo openai/MODELO]'
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --diretorio-estado) diretorio_de_estado="${2:-}"; shift 2 ;;
    --diretorio-credenciais-mcp) diretorio_de_credenciais="${2:-}"; shift 2 ;;
    --identificador-vinculo) identificador_do_vinculo="${2:-}"; shift 2 ;;
    --substituir-vinculo) identificador_do_vinculo_substituido="${2:-}"; shift 2 ;;
    --identificador-bot) identificador_do_bot="${2:-}"; shift 2 ;;
    --identificador-telegram) identificador_do_telegram="${2:-}"; shift 2 ;;
    --identificador-chat) identificador_do_chat="${2:-}"; shift 2 ;;
    --identificador-agente) identificador_do_agente="${2:-}"; shift 2 ;;
    --identificador-sessao) identificador_da_sessao="${2:-}"; shift 2 ;;
    --token-mcp-arquivo) arquivo_do_token="${2:-}"; shift 2 ;;
    --url-mcp) url_mcp="${2:-}"; shift 2 ;;
    --modelo) modelo="${2:-}"; shift 2 ;;
    --ajuda|-h) uso; exit 0 ;;
    *) uso >&2; falhar "argumento desconhecido: $1" ;;
  esac
done

for comando in jq flock realpath install mktemp sha256sum stat; do exigir_comando "${comando}"; done
exigir_argumento --diretorio-estado "${diretorio_de_estado}"
exigir_argumento --diretorio-credenciais-mcp "${diretorio_de_credenciais}"
exigir_argumento --identificador-vinculo "${identificador_do_vinculo}"
exigir_argumento --identificador-bot "${identificador_do_bot}"
exigir_argumento --identificador-telegram "${identificador_do_telegram}"
exigir_argumento --identificador-chat "${identificador_do_chat}"
exigir_argumento --identificador-agente "${identificador_do_agente}"
exigir_argumento --identificador-sessao "${identificador_da_sessao}"
exigir_argumento --token-mcp-arquivo "${arquivo_do_token}"
exigir_argumento --url-mcp "${url_mcp}"

validar_uuid "${identificador_do_vinculo}" identificador-vinculo
if [[ -n "${identificador_do_vinculo_substituido}" ]]; then
  validar_uuid "${identificador_do_vinculo_substituido}" substituir-vinculo
  [[ "${identificador_do_vinculo_substituido}" != "${identificador_do_vinculo}" ]] ||
    falhar "o novo vinculo deve ser diferente do vinculo substituido."
fi
validar_inteiro_positivo "${identificador_do_bot}" identificador-bot
validar_inteiro_positivo "${identificador_do_telegram}" identificador-telegram
validar_inteiro_positivo "${identificador_do_chat}" identificador-chat
[[ "${identificador_do_telegram}" == "${identificador_do_chat}" ]] ||
  falhar "somente conversa privada e aceita; identificador do chat deve ser o Telegram."
validar_identificador_do_agente "${identificador_do_agente}"
validar_identificador_da_sessao "${identificador_da_sessao}"
validar_url_mcp "${url_mcp}"
[[ "${modelo}" =~ ^openai/[A-Za-z0-9._-]+$ ]] || falhar "somente modelo do provedor OpenAI e aceito."
validar_arquivo_do_token "${arquivo_do_token}"

diretorio_de_estado="$(normalizar_diretorio_de_estado "${diretorio_de_estado}")"
diretorio_de_credenciais="$(normalizar_diretorio_de_credenciais "${diretorio_de_credenciais}")"
validar_separacao_dos_diretorios "${diretorio_de_estado}" "${diretorio_de_credenciais}"
inicializar_estado "${diretorio_de_estado}"
inicializar_diretorio_de_credenciais "${diretorio_de_credenciais}"
validar_permissoes_do_estado "${diretorio_de_estado}"
adquirir_bloqueio "${diretorio_de_estado}"

arquivo_da_configuracao="${diretorio_de_estado}/openclaw.json"
arquivo_do_provisionamento="${diretorio_de_estado}/provisionamentos/${identificador_do_vinculo}.json"
arquivo_do_bot="${diretorio_de_estado}/provisionamentos/bot.json"
arquivo_da_credencial="$(caminho_da_credencial_mcp "${diretorio_de_credenciais}" "${identificador_do_vinculo}")"
identificador_compacto_do_vinculo="${identificador_do_vinculo//-/}"
nome_do_plugin="trilha-mcp-${identificador_compacto_do_vinculo}"
caminho_do_workspace_no_container="/home/node/.openclaw/workspaces/${identificador_do_agente}"
caminho_do_agente_no_container="/home/node/.openclaw/agentes/${identificador_do_agente}"
hash_token="$(hash_do_token "${arquivo_do_token}")"

if [[ -f "${arquivo_do_provisionamento}" ]]; then
  if jq -e \
    --arg vinculo "${identificador_do_vinculo}" --arg bot "${identificador_do_bot}" \
    --arg telegram "${identificador_do_telegram}" --arg chat "${identificador_do_chat}" \
    --arg agente "${identificador_do_agente}" --arg sessao "${identificador_da_sessao}" \
    --arg url "${url_mcp}" --arg hash "${hash_token}" \
    '.identificadorDoVinculo == $vinculo and .identificadorDoBot == $bot and
     .identificadorDoTelegram == $telegram and .identificadorDoChat == $chat and
     .identificadorDoAgente == $agente and .identificadorDaSessao == $sessao and
     .urlMcp == $url and .hashDoToken == $hash and .estado == "ATIVO"' \
    "${arquivo_do_provisionamento}" >/dev/null; then
    validar_arquivo_secreto "${arquivo_da_credencial}" credencial-mcp
    [[ "$(jq -jr '.tokenMcp' "${arquivo_da_credencial}" | sha256sum | cut -d' ' -f1)" == "${hash_token}" ]] ||
      falhar "a credencial externa diverge dos metadados do vinculo."
    if [[ -n "${identificador_do_vinculo_substituido}" ]]; then
      arquivo_anterior="${diretorio_de_estado}/provisionamentos/${identificador_do_vinculo_substituido}.json"
      jq -e --arg novo "${identificador_do_vinculo}" \
        '.estado == "REVOGADO" and .substituidoPor == $novo' "${arquivo_anterior}" >/dev/null ||
        falhar "o novo vinculo existe, mas o anterior nao foi substituido integralmente."
    fi
    printf 'Vinculo %s ja provisionado com os mesmos dados.\n' "${identificador_do_vinculo}"
    exit 0
  fi
  falhar "a chave do vinculo ja foi usada com dados diferentes."
fi

if [[ -f "${arquivo_do_bot}" ]]; then
  [[ "$(jq -r '.identificadorDoBot' "${arquivo_do_bot}")" == "${identificador_do_bot}" ]] ||
    falhar "o estado pertence a outro bot do Telegram."
fi

agente_substituido=""
telegram_substituido=""
plugin_substituido=""
arquivo_do_provisionamento_substituido=""
arquivo_da_credencial_substituida=""
if [[ -n "${identificador_do_vinculo_substituido}" ]]; then
  arquivo_do_provisionamento_substituido="${diretorio_de_estado}/provisionamentos/${identificador_do_vinculo_substituido}.json"
  [[ -f "${arquivo_do_provisionamento_substituido}" ]] || falhar "vinculo a substituir nao foi provisionado."
  jq -e --arg bot "${identificador_do_bot}" --arg telegram "${identificador_do_telegram}" \
    --arg chat "${identificador_do_chat}" \
    '.estado == "ATIVO" and .identificadorDoBot == $bot and
     .identificadorDoTelegram == $telegram and .identificadorDoChat == $chat' \
    "${arquivo_do_provisionamento_substituido}" >/dev/null ||
    falhar "o vinculo anterior nao esta ativo ou pertence a outra conversa."
  agente_substituido="$(jq -r '.identificadorDoAgente' "${arquivo_do_provisionamento_substituido}")"
  telegram_substituido="$(jq -r '.identificadorDoTelegram' "${arquivo_do_provisionamento_substituido}")"
  plugin_substituido="$(jq -r '.nomeDoPlugin' "${arquivo_do_provisionamento_substituido}")"
  [[ "${agente_substituido}" != "${identificador_do_agente}" ]] ||
    falhar "a rotacao exige um novo identificador de agente."
  arquivo_da_credencial_substituida="$(caminho_da_credencial_mcp "${diretorio_de_credenciais}" "${identificador_do_vinculo_substituido}")"
fi

jq -e --arg telegram "${identificador_do_telegram}" --arg antigo "${agente_substituido}" \
  'all(.bindings[]?; .match.channel != "telegram" or .match.peer.id != $telegram or .agentId == $antigo)' \
  "${arquivo_da_configuracao}" >/dev/null || falhar "o Telegram ja esta vinculado a outro agente."
jq -e --arg agente "${identificador_do_agente}" \
  'all(.agents.list[]?; .id != $agente)' "${arquivo_da_configuracao}" >/dev/null ||
  falhar "o identificador do agente ja esta em uso."

diretorio_temporario="$(mktemp -d "${diretorio_de_estado}/temporarios/provisionamento.XXXXXX")"
credencial_temporaria="$(mktemp "${diretorio_de_credenciais}/.credencial.XXXXXX")"
trap 'rm -rf -- "${diretorio_temporario}"; rm -f -- "${credencial_temporaria}"' EXIT
workspace_temporario="${diretorio_temporario}/workspace"
agente_temporario="${diretorio_temporario}/agente"
plugin_temporario="${workspace_temporario}/.openclaw/extensions/${nome_do_plugin}"
install -d -m 700 -- "${workspace_temporario}" "${agente_temporario}" "${plugin_temporario}/.codex-plugin"
install -m 500 -- "${diretorio_do_modulo}/scripts/proxy-mcp-http-stdio.mjs" \
  "${plugin_temporario}/proxy-mcp-http-stdio.mjs"

for arquivo in AGENTS.md SOUL.md IDENTITY.md TOOLS.md USER.md; do
  install -m 600 -- "${diretorio_do_modulo}/modelos/workspace/${arquivo}" "${workspace_temporario}/${arquivo}"
done

jq -n --arg nome "${nome_do_plugin}" '{
  name: $nome, version: "1.0.0",
  description: "Adaptador MCP isolado da Trilha da Aprovacao",
  mcpServers: [".mcp.json"]
}' > "${plugin_temporario}/.codex-plugin/plugin.json"

ferramentas="$(ferramentas_mcp_em_json)"
jq -n --arg vinculo "${identificador_do_vinculo}" --argjson ferramentas "${ferramentas}" \
  '{mcpServers: {trilha: {
    command: "node",
    args: ["./proxy-mcp-http-stdio.mjs", ("http://broker-credenciais:18890/mcp/" + $vinculo)],
    toolFilter: {include: $ferramentas}
  }}}' > "${plugin_temporario}/.mcp.json"
chmod 600 -- "${plugin_temporario}/.codex-plugin/plugin.json" "${plugin_temporario}/.mcp.json"

jq -n --rawfile token "${arquivo_do_token}" --arg vinculo "${identificador_do_vinculo}" \
  --arg agente "${identificador_do_agente}" --arg sessao "${identificador_da_sessao}" --arg url "${url_mcp}" \
  '{versao: 1, identificadorDoVinculo: $vinculo, identificadorDoAgente: $agente,
    identificadorDaSessao: $sessao, urlMcp: $url,
    tokenMcp: ($token | gsub("[\\r\\n]+$"; ""))}' > "${credencial_temporaria}"
chmod 600 -- "${credencial_temporaria}"

configuracao_temporaria="${diretorio_temporario}/openclaw.json"
jq --arg agente "${identificador_do_agente}" --arg telegram "${identificador_do_telegram}" \
  --arg vinculo "${identificador_do_vinculo}" --arg sessao "${identificador_da_sessao}" \
  --arg plugin "${nome_do_plugin}" --arg pluginAntigo "${plugin_substituido}" \
  --arg workspace "${caminho_do_workspace_no_container}" --arg diretorioAgente "${caminho_do_agente_no_container}" \
  --arg modelo "${modelo}" \
  --arg agenteAntigo "${agente_substituido}" --arg telegramAntigo "${telegram_substituido}" \
  '.plugins.allow = (([.plugins.allow[]? | select(. != $pluginAntigo)] + [$plugin]) | unique)
   | .agents.list = [.agents.list[]? | select(.id != $agenteAntigo)]
   | .bindings = [.bindings[]? | select(.agentId != $agenteAntigo)]
   | .channels.telegram.allowFrom = [.channels.telegram.allowFrom[]? | select(tostring != $telegramAntigo)]
   | .agents.list += [{
      id: $agente, name: "Assistente da Trilha",
      description: ("Agente isolado do vinculo " + $vinculo),
      workspace: $workspace, agentDir: $diretorioAgente,
      model: {primary: $modelo}, skills: [], memorySearch: {enabled: false},
      contextInjection: "always", subagents: {allowAgents: [], requireAgentId: true},
      tools: {profile: "minimal", alsoAllow: ["trilha__*"],
        deny: ["session_status", "group:runtime", "group:fs", "group:web",
          "group:sessions", "group:memory", "group:ui", "group:automation",
          "group:messaging", "group:nodes", "group:agents", "group:media"],
        elevated: {enabled: false}}, runtime: {type: "embedded"}
    }]
   | .bindings += [{type: "route", agentId: $agente,
      comment: ("vinculo=" + $vinculo + ";sessao=" + $sessao),
      match: {channel: "telegram", peer: {kind: "direct", id: $telegram}},
      session: {dmScope: "per-channel-peer"}}]
   | .channels.telegram.allowFrom = ((.channels.telegram.allowFrom + [$telegram]) | unique)' \
  "${arquivo_da_configuracao}" > "${configuracao_temporaria}"
jq empty "${configuracao_temporaria}"
chmod 600 -- "${configuracao_temporaria}"

provisionamento_temporario="${diretorio_temporario}/provisionamento.json"
jq -n --arg vinculo "${identificador_do_vinculo}" --arg bot "${identificador_do_bot}" \
  --arg telegram "${identificador_do_telegram}" --arg chat "${identificador_do_chat}" \
  --arg agente "${identificador_do_agente}" --arg sessao "${identificador_da_sessao}" \
  --arg plugin "${nome_do_plugin}" --arg url "${url_mcp}" --arg hash "${hash_token}" \
  --arg criadoEm "$(date -u +'%Y-%m-%dT%H:%M:%SZ')" \
  '{versao: 2, estado: "ATIVO", identificadorDoVinculo: $vinculo,
    identificadorDoBot: $bot, identificadorDoTelegram: $telegram,
    identificadorDoChat: $chat, identificadorDoAgente: $agente,
    identificadorDaSessao: $sessao, nomeDoPlugin: $plugin, urlMcp: $url,
    hashDoToken: $hash, criadoEm: $criadoEm}' > "${provisionamento_temporario}"
chmod 600 -- "${provisionamento_temporario}"

destino_do_workspace="${diretorio_de_estado}/workspaces/${identificador_do_agente}"
destino_do_agente="${diretorio_de_estado}/agentes/${identificador_do_agente}"
[[ ! -e "${destino_do_workspace}" && ! -e "${destino_do_agente}" && ! -e "${arquivo_da_credencial}" ]] ||
  falhar "diretorio do novo agente, workspace ou credencial ja existe."

destino_revogado=""
metadados_anteriores_temporarios=""
if [[ -n "${identificador_do_vinculo_substituido}" ]]; then
  destino_revogado="${diretorio_de_estado}/revogados/${identificador_do_vinculo_substituido}"
  [[ ! -e "${destino_revogado}" ]] || falhar "o estado revogado do vinculo anterior ja existe."
  install -d -m 700 -- "${destino_revogado}"
  metadados_anteriores_temporarios="${diretorio_temporario}/provisionamento-anterior.json"
  jq --arg novo "${identificador_do_vinculo}" --arg revogadoEm "$(date -u +'%Y-%m-%dT%H:%M:%SZ')" \
    '.estado = "REVOGADO" | .revogadoEm = $revogadoEm | .substituidoPor = $novo | del(.hashDoToken)' \
    "${arquivo_do_provisionamento_substituido}" > "${metadados_anteriores_temporarios}"
  chmod 600 -- "${metadados_anteriores_temporarios}"
fi

mv -- "${workspace_temporario}" "${destino_do_workspace}"
mv -- "${agente_temporario}" "${destino_do_agente}"
mv -- "${credencial_temporaria}" "${arquivo_da_credencial}"
mv -- "${configuracao_temporaria}" "${arquivo_da_configuracao}"
mv -- "${provisionamento_temporario}" "${arquivo_do_provisionamento}"
if [[ -n "${identificador_do_vinculo_substituido}" ]]; then
  rm -f -- "${arquivo_da_credencial_substituida}"
  workspace_anterior="${diretorio_de_estado}/workspaces/${agente_substituido}"
  agente_anterior="${diretorio_de_estado}/agentes/${agente_substituido}"
  rm -f -- "${workspace_anterior}/.openclaw/extensions/${plugin_substituido}/.mcp.json"
  [[ ! -d "${workspace_anterior}" ]] || mv -- "${workspace_anterior}" "${destino_revogado}/workspace"
  [[ ! -d "${agente_anterior}" ]] || mv -- "${agente_anterior}" "${destino_revogado}/agente"
  mv -- "${metadados_anteriores_temporarios}" "${arquivo_do_provisionamento_substituido}"
fi
if [[ ! -f "${arquivo_do_bot}" ]]; then
  jq -n --arg bot "${identificador_do_bot}" '{identificadorDoBot: $bot}' > "${arquivo_do_bot}"
  chmod 600 -- "${arquivo_do_bot}"
fi

trap - EXIT
rm -rf -- "${diretorio_temporario}"
if [[ -n "${identificador_do_vinculo_substituido}" ]]; then
  printf 'Vinculo %s substituido integralmente por %s no agente %s. Confirme o novo provisionamento no backend.\n' \
    "${identificador_do_vinculo_substituido}" "${identificador_do_vinculo}" "${identificador_do_agente}"
else
  printf 'Vinculo %s provisionado localmente no agente %s. Confirme-o no backend antes de reiniciar o Gateway.\n' \
    "${identificador_do_vinculo}" "${identificador_do_agente}"
fi
