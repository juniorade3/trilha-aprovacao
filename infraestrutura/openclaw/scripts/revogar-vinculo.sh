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
[[ -f "${arquivo_do_provisionamento}" ]] || falhar "vinculo nao provisionado."
arquivo_da_credencial="$(caminho_da_credencial_mcp "${diretorio_de_credenciais}" "${identificador_do_vinculo}")"
if [[ "$(jq -r '.estado' "${arquivo_do_provisionamento}")" == "REVOGADO" ]]; then
  rm -f -- "${arquivo_da_credencial}"
  printf 'Vinculo %s ja revogado.\n' "${identificador_do_vinculo}"
  exit 0
fi

agente="$(jq -r '.identificadorDoAgente' "${arquivo_do_provisionamento}")"
telegram="$(jq -r '.identificadorDoTelegram' "${arquivo_do_provisionamento}")"
plugin="$(jq -r '.nomeDoPlugin' "${arquivo_do_provisionamento}")"
arquivo_da_configuracao="${diretorio_de_estado}/openclaw.json"
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
destino_revogado="${diretorio_de_estado}/revogados/${identificador_do_vinculo}"
install -d -m 700 -- "${destino_revogado}"
if [[ -d "${workspace}" ]]; then mv -- "${workspace}" "${destino_revogado}/workspace"; fi
if [[ -d "${diretorio_de_estado}/agentes/${agente}" ]]; then
  mv -- "${diretorio_de_estado}/agentes/${agente}" "${destino_revogado}/agente"
fi
trap - EXIT
printf 'Vinculo %s revogado e credencial local removida. Reinicie o Gateway.\n' \
  "${identificador_do_vinculo}"
