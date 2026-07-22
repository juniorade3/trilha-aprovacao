#!/usr/bin/env bash

diretorio_dos_scripts="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
diretorio_do_modulo="$(cd -- "${diretorio_dos_scripts}/.." && pwd)"
diretorio_do_repositorio="$(cd -- "${diretorio_do_modulo}/../.." && pwd)"

falhar() {
  printf 'Erro: %s\n' "$*" >&2
  exit 1
}

exigir_comando() {
  command -v "$1" >/dev/null 2>&1 || falhar "o comando '$1' e obrigatorio."
}

exigir_argumento() {
  local nome="$1"
  local valor="$2"
  [[ -n "${valor}" ]] || falhar "o argumento ${nome} e obrigatorio."
}

normalizar_diretorio_de_estado() {
  local caminho="$1"
  local absoluto
  absoluto="$(realpath -m -- "${caminho}")"
  case "${absoluto}" in
    "${diretorio_do_repositorio}"|"${diretorio_do_repositorio}"/*)
      falhar "OPENCLAW_DIRETORIO_ESTADO deve ficar fora do repositorio."
      ;;
  esac
  printf '%s\n' "${absoluto}"
}

validar_uuid() {
  [[ "$1" =~ ^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-8][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$ ]] ||
    falhar "$2 deve ser um UUID valido."
}

validar_inteiro_positivo() {
  [[ "$1" =~ ^[1-9][0-9]*$ ]] || falhar "$2 deve ser um inteiro positivo."
}

validar_identificador_do_agente() {
  [[ "$1" =~ ^[a-z0-9][a-z0-9_-]{0,63}$ ]] ||
    falhar "identificador-do-agente deve usar de 1 a 64 caracteres [a-z0-9_-]."
}

validar_identificador_da_sessao() {
  [[ "$1" =~ ^[A-Za-z0-9][A-Za-z0-9._:-]{0,159}$ ]] ||
    falhar "identificador-da-sessao possui formato invalido."
}

validar_url_mcp() {
  local url="$1"
  [[ "${url}" =~ ^https?://[^/@[:space:]]+(/[^?#[:space:]]*)?/mcp/?$ ]] ||
    falhar "url-mcp deve ser uma URL HTTP(S), sem credenciais, consulta ou fragmento, terminada em /mcp."
}

validar_arquivo_do_token() {
  local arquivo="$1"
  validar_arquivo_secreto "${arquivo}" token-mcp-arquivo
  local token
  token="$(tr -d '\r\n' < "${arquivo}")"
  [[ "${token}" =~ ^mcp_[A-Za-z0-9_-]{43}$ ]] ||
    falhar "o token MCP nao possui o formato emitido pela Trilha."
  [[ "$(wc -l < "${arquivo}")" -le 1 ]] || falhar "o arquivo do token deve conter uma unica linha."
}

validar_arquivo_secreto() {
  local arquivo="$1"
  local nome="$2"
  [[ -f "${arquivo}" && ! -L "${arquivo}" ]] ||
    falhar "${nome} deve apontar para um arquivo regular, nao simbolico."
  [[ "$(stat -c '%a' -- "${arquivo}")" == "600" ]] ||
    falhar "${nome} deve ter permissao 600."
}

normalizar_diretorio_de_credenciais() {
  normalizar_diretorio_de_estado "$1"
}

validar_separacao_dos_diretorios() {
  local estado="$1"
  local credenciais="$2"
  case "${credenciais}" in
    "${estado}"|"${estado}"/*)
      falhar "o diretorio de credenciais MCP deve ficar fora do diretorio de estado."
      ;;
  esac
  case "${estado}" in
    "${credenciais}"|"${credenciais}"/*)
      falhar "o diretorio de estado deve ficar fora do diretorio de credenciais MCP."
      ;;
  esac
}

inicializar_diretorio_de_credenciais() {
  local diretorio="$1"
  install -d -m 700 -- "${diretorio}"
  [[ -d "${diretorio}" && ! -L "${diretorio}" ]] ||
    falhar "o diretorio de credenciais MCP deve ser regular, nao simbolico."
  chmod 700 -- "${diretorio}"
}

caminho_da_credencial_mcp() {
  local diretorio="$1"
  local vinculo="$2"
  printf '%s/%s.json\n' "${diretorio}" "${vinculo}"
}

inicializar_estado() {
  local diretorio_de_estado="$1"
  install -d -m 700 -- "${diretorio_de_estado}"
  install -d -m 700 -- \
    "${diretorio_de_estado}/agentes" \
    "${diretorio_de_estado}/workspaces" \
    "${diretorio_de_estado}/extensions" \
    "${diretorio_de_estado}/extensions/trilha-aprovacao" \
    "${diretorio_de_estado}/provisionamentos" \
    "${diretorio_de_estado}/revogados" \
    "${diretorio_de_estado}/temporarios"
  if [[ ! -e "${diretorio_de_estado}/openclaw.json" ]]; then
    install -m 600 -- "${diretorio_do_modulo}/modelos/openclaw.json" \
      "${diretorio_de_estado}/openclaw.json"
  fi
  [[ -f "${diretorio_de_estado}/openclaw.json" && ! -L "${diretorio_de_estado}/openclaw.json" ]] ||
    falhar "openclaw.json deve ser um arquivo regular, nao simbolico."
  chmod 600 -- "${diretorio_de_estado}/openclaw.json"
  jq empty "${diretorio_de_estado}/openclaw.json" || falhar "openclaw.json possui JSON invalido."
}

adquirir_bloqueio() {
  local diretorio_de_estado="$1"
  exec 9>"${diretorio_de_estado}/.provisionamento.lock"
  chmod 600 -- "${diretorio_de_estado}/.provisionamento.lock"
  flock -x 9
}

hash_do_token() {
  local arquivo="$1"
  tr -d '\r\n' < "${arquivo}" | sha256sum | cut -d' ' -f1
}

ferramentas_mcp_em_json() {
  jq -cn '[
    "obter_agenda_de_estudos_de_hoje",
    "obter_revisoes_devidas",
    "obter_prioridades_atuais",
    "obter_progresso_do_concurso",
    "obter_historico_recente",
    "obter_estrutura_do_concurso",
    "explicar_bloco_de_estudo",
    "consultar_operacao_assistida"
  ]'
}

validar_permissoes_do_estado() {
  local diretorio_de_estado="$1"
  local modo
  modo="$(stat -c '%a' "${diretorio_de_estado}")"
  [[ "${modo}" == "700" ]] || falhar "o diretorio de estado deve ter permissao 700."
}
