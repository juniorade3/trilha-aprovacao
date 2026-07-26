#!/usr/bin/env bash
set -Eeuo pipefail

source "$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)/biblioteca.sh"

diretorio_de_estado=""
identificador_do_vinculo=""
url_do_backend=""
identificador_da_chave=""
identificador_da_correlacao=""
arquivo_do_segredo=""

while [[ $# -gt 0 ]]; do
  case "$1" in
    --diretorio-estado) diretorio_de_estado="${2:-}"; shift 2 ;;
    --identificador-vinculo) identificador_do_vinculo="${2:-}"; shift 2 ;;
    --url-backend) url_do_backend="${2:-}"; shift 2 ;;
    --identificador-chave) identificador_da_chave="${2:-}"; shift 2 ;;
    --identificador-correlacao)
      identificador_da_correlacao="${2:-}"
      shift 2
      ;;
    --segredo-gateway-arquivo) arquivo_do_segredo="${2:-}"; shift 2 ;;
    *) falhar "argumento desconhecido: $1" ;;
  esac
done

for comando in jq flock realpath install mktemp sha256sum stat curl node; do exigir_comando "${comando}"; done
exigir_argumento --diretorio-estado "${diretorio_de_estado}"
exigir_argumento --identificador-vinculo "${identificador_do_vinculo}"
exigir_argumento --url-backend "${url_do_backend}"
exigir_argumento --identificador-chave "${identificador_da_chave}"
exigir_argumento --segredo-gateway-arquivo "${arquivo_do_segredo}"
validar_uuid "${identificador_do_vinculo}" identificador-vinculo
[[ "${url_do_backend}" =~ ^https?://[^/@[:space:]]+/?$ ]] ||
  falhar "url-backend deve conter somente esquema, host e porta, sem credenciais."
[[ "${identificador_da_chave}" =~ ^[A-Za-z0-9._:-]{1,160}$ ]] ||
  falhar "identificador-chave possui formato invalido."
if [[ -z "${identificador_da_correlacao}" ]]; then
  identificador_da_correlacao="$(
    node -e 'process.stdout.write(require("node:crypto").randomUUID())')"
fi
validar_uuid "${identificador_da_correlacao}" identificador-correlacao
validar_arquivo_secreto "${arquivo_do_segredo}" segredo-gateway-arquivo
quantidade_do_segredo="$(tr -d '\r\n' < "${arquivo_do_segredo}" | wc -c)"
[[ "${quantidade_do_segredo}" -ge 32 && "${quantidade_do_segredo}" -le 4096 ]] ||
  falhar "o segredo do Gateway deve ter entre 32 e 4096 caracteres."

diretorio_de_estado="$(normalizar_diretorio_de_estado "${diretorio_de_estado}")"
inicializar_estado "${diretorio_de_estado}"
adquirir_bloqueio "${diretorio_de_estado}"
arquivo_do_provisionamento="${diretorio_de_estado}/provisionamentos/${identificador_do_vinculo}.json"
[[ -f "${arquivo_do_provisionamento}" ]] || falhar "vinculo nao foi provisionado localmente."
[[ "$(jq -r '.estado' "${arquivo_do_provisionamento}")" == "ATIVO" ]] ||
  falhar "vinculo local nao esta ativo."

identificador_do_bot="$(jq -r '.identificadorDoBot' "${arquivo_do_provisionamento}")"
identificador_do_telegram="$(jq -r '.identificadorDoTelegram' "${arquivo_do_provisionamento}")"
identificador_do_chat="$(jq -r '.identificadorDoChat' "${arquivo_do_provisionamento}")"
identificador_do_agente="$(jq -r '.identificadorDoAgente' "${arquivo_do_provisionamento}")"
identificador_da_sessao="$(jq -r '.identificadorDaSessao' "${arquivo_do_provisionamento}")"
caminho="/api/v1/integracoes-confiaveis/telegram/vinculos/${identificador_do_vinculo}/provisionamento"
url_do_backend="${url_do_backend%/}"

diretorio_temporario="$(mktemp -d "${diretorio_de_estado}/temporarios/registro.XXXXXX")"
trap 'rm -rf -- "${diretorio_temporario}"' EXIT
arquivo_do_corpo="${diretorio_temporario}/corpo.json"
arquivo_canonico="${diretorio_temporario}/canonico"
arquivo_da_resposta="${diretorio_temporario}/resposta.json"
jq -n \
  --arg bot "${identificador_do_bot}" \
  --arg telegram "${identificador_do_telegram}" \
  --arg chat "${identificador_do_chat}" \
  --arg agente "${identificador_do_agente}" \
  --arg sessao "${identificador_da_sessao}" \
  '{identificadorDoBot: ($bot | tonumber),
    identificadorDoTelegram: ($telegram | tonumber),
    identificadorDoChat: ($chat | tonumber),
    identificadorDoAgente: $agente,
    identificadorDaSessao: $sessao}' > "${arquivo_do_corpo}"
chmod 600 -- "${arquivo_do_corpo}"

instante="$(date -u +%s)"
nonce="$(node -e 'process.stdout.write(require("node:crypto").randomBytes(32).toString("base64url"))')"
idempotencia="provisionamento-${identificador_do_vinculo}"
hash_do_corpo="$(sha256sum "${arquivo_do_corpo}" | cut -d' ' -f1)"
printf 'TRILHA-HMAC-V1\n%s\n%s\n%s\nPOST\n%s\n%s\n%s' \
  "${identificador_da_chave}" "${instante}" "${nonce}" "${caminho}" \
  "${hash_do_corpo}" "${idempotencia}" > "${arquivo_canonico}"
chmod 600 -- "${arquivo_canonico}"
assinatura="$(node "${diretorio_dos_scripts}/assinar-gateway.mjs" \
  "${arquivo_do_segredo}" "${arquivo_canonico}")"

codigo_http="$(curl --silent --show-error \
  --connect-timeout 5 --max-time 20 \
  --request POST "${url_do_backend}${caminho}" \
  --header 'Content-Type: application/json' \
  --header "X-Trilha-Chave: ${identificador_da_chave}" \
  --header "X-Trilha-Instante: ${instante}" \
  --header "X-Trilha-Nonce: ${nonce}" \
  --header "X-Trilha-Assinatura: ${assinatura}" \
  --header "X-Chave-De-Idempotencia: ${idempotencia}" \
  --header "X-Identificador-De-Correlacao: ${identificador_da_correlacao}" \
  --data-binary "@${arquivo_do_corpo}" \
  --output "${arquivo_da_resposta}" --write-out '%{http_code}')"
[[ "${codigo_http}" =~ ^2[0-9][0-9]$ ]] || {
  printf 'Backend recusou registro de provisionamento com HTTP %s.\n' "${codigo_http}" >&2
  exit 1
}
jq -e \
  --arg vinculo "${identificador_do_vinculo}" \
  --arg agente "${identificador_do_agente}" \
  '.identificador == $vinculo and .identificadorDoAgente == $agente and .provisionado == true' \
  "${arquivo_da_resposta}" >/dev/null || falhar "resposta do backend nao confirmou o provisionamento esperado."

metadados_temporarios="${diretorio_temporario}/provisionamento.json"
jq --arg registradoEm "$(date -u +'%Y-%m-%dT%H:%M:%SZ')" \
  '.registradoNoBackendEm = $registradoEm' "${arquivo_do_provisionamento}" > "${metadados_temporarios}"
chmod 600 -- "${metadados_temporarios}"
mv -- "${metadados_temporarios}" "${arquivo_do_provisionamento}"
trap - EXIT
rm -rf -- "${diretorio_temporario}"
printf 'Provisionamento do vinculo %s confirmado pelo backend.\n' "${identificador_do_vinculo}"
