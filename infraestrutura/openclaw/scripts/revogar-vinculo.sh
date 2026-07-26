#!/usr/bin/env bash
set -Eeuo pipefail

source "$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)/biblioteca.sh"

diretorio_de_estado=""
diretorio_de_credenciais=""
identificador_do_vinculo=""

while [[ $# -gt 0 ]]; do
  case "$1" in
    --diretorio-estado) diretorio_de_estado="${2:-}"; shift 2 ;;
    --diretorio-credenciais-mcp) diretorio_de_credenciais="${2:-}"; shift 2 ;;
    --identificador-vinculo) identificador_do_vinculo="${2:-}"; shift 2 ;;
    *) falhar "argumento desconhecido: $1" ;;
  esac
done

for comando in jq flock realpath install mktemp stat; do exigir_comando "${comando}"; done
exigir_argumento --diretorio-estado "${diretorio_de_estado}"
exigir_argumento --diretorio-credenciais-mcp "${diretorio_de_credenciais}"
exigir_argumento --identificador-vinculo "${identificador_do_vinculo}"
validar_uuid "${identificador_do_vinculo}" identificador-vinculo

diretorio_de_estado="$(normalizar_diretorio_de_estado "${diretorio_de_estado}")"
diretorio_de_credenciais="$(normalizar_diretorio_de_credenciais "${diretorio_de_credenciais}")"
validar_separacao_dos_diretorios "${diretorio_de_estado}" "${diretorio_de_credenciais}"
inicializar_estado "${diretorio_de_estado}"
inicializar_diretorio_de_credenciais "${diretorio_de_credenciais}"
adquirir_bloqueio "${diretorio_de_estado}"
arquivo_do_provisionamento="${diretorio_de_estado}/provisionamentos/${identificador_do_vinculo}.json"
[[ -f "${arquivo_do_provisionamento}" &&
  ! -L "${arquivo_do_provisionamento}" &&
  "$(stat -c '%a' -- "${arquivo_do_provisionamento}")" == "600" ]] ||
  falhar "metadados do vinculo devem ser arquivo regular 0600."
jq empty "${arquivo_do_provisionamento}" ||
  falhar "metadados do vinculo possuem JSON invalido."
arquivo_da_credencial="$(caminho_da_credencial_mcp "${diretorio_de_credenciais}" "${identificador_do_vinculo}")"
if [[ "$(jq -r '.estado' "${arquivo_do_provisionamento}")" == "REVOGADO" ]]; then
  if [[ -e "${arquivo_da_credencial}" || -L "${arquivo_da_credencial}" ]]; then
    validar_arquivo_secreto "${arquivo_da_credencial}" credencial-mcp
  fi
  rm -f -- "${arquivo_da_credencial}"
  printf 'Vinculo %s ja revogado.\n' "${identificador_do_vinculo}"
  exit 0
fi

jq -e --arg vinculo "${identificador_do_vinculo}" '
  .identificadorDoVinculo == $vinculo and
  .estado == "ATIVO" and
  (.identificadorDoAgente | type == "string") and
  (.identificadorDoTelegram | type == "string") and
  (.nomeDoPlugin | type == "string")
' "${arquivo_do_provisionamento}" >/dev/null ||
  falhar "metadados ativos do vinculo sao incompativeis."
agente="$(jq -r '.identificadorDoAgente' "${arquivo_do_provisionamento}")"
telegram="$(jq -r '.identificadorDoTelegram' "${arquivo_do_provisionamento}")"
plugin="$(jq -r '.nomeDoPlugin' "${arquivo_do_provisionamento}")"
validar_identificador_do_agente "${agente}"
validar_inteiro_positivo "${telegram}" identificador-telegram
validar_nome_do_plugin_do_vinculo "${identificador_do_vinculo}" "${plugin}"
validar_estrutura_local_do_vinculo "${diretorio_de_estado}" \
  "${agente}" "${plugin}"
validar_arquivo_secreto "${arquivo_da_credencial}" credencial-mcp
arquivo_da_configuracao="${diretorio_de_estado}/openclaw.json"
destino_revogado="${diretorio_de_estado}/revogados/${identificador_do_vinculo}"
[[ ! -e "${destino_revogado}" && ! -L "${destino_revogado}" ]] ||
  falhar "destino do vinculo revogado ja existe ou e inseguro."
install -d -m 700 -- "${destino_revogado}"
temporario="$(mktemp "${diretorio_de_estado}/temporarios/revogacao.XXXXXX")"
metadados_temporarios="$(mktemp "${diretorio_de_estado}/temporarios/metadados.XXXXXX")"
trap 'rm -f -- "${temporario}" "${metadados_temporarios}"' EXIT

jq --arg agente "${agente}" --arg telegram "${telegram}" --arg plugin "${plugin}" \
  '.plugins.allow = [.plugins.allow[]? | select(. != $plugin)]
   | .agents.list = [.agents.list[]? | select(.id != $agente)]
   | .bindings = [.bindings[]? | select(.agentId != $agente)]
   | .channels.telegram.allowFrom = [.channels.telegram.allowFrom[]? | select(tostring != $telegram)]' \
  "${arquivo_da_configuracao}" > "${temporario}"
jq --arg revogadoEm "$(date -u +'%Y-%m-%dT%H:%M:%SZ')" \
  '.estado = "REVOGADO" | .revogadoEm = $revogadoEm | del(.hashDoToken)' \
  "${arquivo_do_provisionamento}" > "${metadados_temporarios}"
chmod 600 -- "${temporario}" "${metadados_temporarios}"

workspace="${diretorio_de_estado}/workspaces/${agente}"
diretorio_do_plugin="${workspace}/.openclaw/extensions/${plugin}"
mv -- "${temporario}" "${arquivo_da_configuracao}"
mv -- "${metadados_temporarios}" "${arquivo_do_provisionamento}"
rm -f -- "${diretorio_do_plugin}/.mcp.json"
rm -f -- "${arquivo_da_credencial}"
mv -- "${workspace}" "${destino_revogado}/workspace"
mv -- "${diretorio_de_estado}/agentes/${agente}" "${destino_revogado}/agente"
trap - EXIT
printf 'Vinculo %s revogado e credencial local removida. Reinicie o Gateway.\n' \
  "${identificador_do_vinculo}"
