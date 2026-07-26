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

normalizar_diretorio_dedicado() {
  local caminho="$1"
  local nome="$2"
  local caminho_sem_barra="${caminho%/}"
  [[ -n "${caminho_sem_barra}" ]] ||
    falhar "${nome} nao pode apontar para a raiz."
  [[ ! -L "${caminho_sem_barra}" ]] ||
    falhar "${nome} nao pode ser um link simbolico."
  local absoluto
  absoluto="$(realpath -m -- "${caminho_sem_barra}")"
  case "${absoluto}" in
    "${diretorio_do_repositorio}"|"${diretorio_do_repositorio}"/*)
      falhar "${nome} deve ficar fora do repositorio."
      ;;
    /|/bin|/boot|/dev|/etc|/home|/lib|/lib32|/lib64|/media|/mnt|/opt|/proc|/root|/run|/sbin|/srv|/sys|/tmp|/usr|/var)
      falhar "${nome} deve apontar para um diretorio dedicado, nunca um diretorio amplo do sistema."
      ;;
  esac
  [[ ! "${absoluto}" =~ ^/home/[^/]+$ &&
    ! "${absoluto}" =~ ^/Users/[^/]+$ ]] ||
    falhar "${nome} nao pode apontar para um diretorio pessoal inteiro."
  if [[ -n "${HOME:-}" ]]; then
    local diretorio_pessoal
    diretorio_pessoal="$(realpath -m -- "${HOME}")"
    [[ "${absoluto}" != "${diretorio_pessoal}" ]] ||
      falhar "${nome} nao pode apontar para a pasta pessoal."
    if [[ "$(dirname -- "${absoluto}")" == "${diretorio_pessoal}" ]]; then
      [[ "${nome}" == "OPENCLAW_DIRETORIO_ESTADO" &&
        "$(basename -- "${absoluto}")" == ".openclaw" ]] ||
        falhar "${nome} deve usar um subdiretorio dedicado."
    fi
  fi
  [[ "$(dirname -- "${absoluto}")" != "/" ]] ||
    falhar "${nome} deve usar um diretorio dedicado abaixo de um caminho intermediario."
  printf '%s\n' "${absoluto}"
}

normalizar_diretorio_de_estado() {
  normalizar_diretorio_dedicado "$1" OPENCLAW_DIRETORIO_ESTADO
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

validar_nome_do_plugin_do_vinculo() {
  local vinculo="$1"
  local nome="$2"
  local esperado="trilha-mcp-${vinculo//-/}"
  [[ "${nome}" == "${esperado}" ]] ||
    falhar "nome do plugin MCP diverge do vinculo."
}

validar_estrutura_local_do_vinculo() {
  local diretorio_de_estado="$1"
  local agente="$2"
  local plugin="$3"
  validar_identificador_do_agente "${agente}"
  local workspace="${diretorio_de_estado}/workspaces/${agente}"
  local diretorio_do_agente="${diretorio_de_estado}/agentes/${agente}"
  local caminho
  for caminho in \
    "${workspace}" \
    "${diretorio_do_agente}" \
    "${workspace}/.openclaw" \
    "${workspace}/.openclaw/extensions" \
    "${workspace}/.openclaw/extensions/${plugin}"; do
    [[ -d "${caminho}" && ! -L "${caminho}" ]] ||
      falhar "estrutura local do vinculo possui diretorio inseguro."
  done
  local arquivo_mcp="${workspace}/.openclaw/extensions/${plugin}/.mcp.json"
  if [[ -e "${arquivo_mcp}" || -L "${arquivo_mcp}" ]]; then
    [[ -f "${arquivo_mcp}" && ! -L "${arquivo_mcp}" &&
      "$(stat -c '%a' -- "${arquivo_mcp}")" == "600" ]] ||
      falhar "arquivo MCP do vinculo deve ser regular 0600."
  fi
}

validar_identificador_da_sessao() {
  [[ "$1" =~ ^[A-Za-z0-9][A-Za-z0-9._:-]{0,159}$ ]] ||
    falhar "identificador-da-sessao possui formato invalido."
}

validar_identificador_da_conta_do_bot() {
  [[ "$1" =~ ^[a-z0-9][a-z0-9_-]{0,63}$ ]] ||
    falhar "identificador-conta-bot deve usar de 1 a 64 caracteres [a-z0-9_-]."
}

configurar_conta_do_telegram() {
  local origem="$1"
  local destino="$2"
  local conta="$3"
  validar_identificador_da_conta_do_bot "${conta}"
  jq -e --arg conta "${conta}" '
    (.channels.telegram | type == "object") and
    ((.channels.telegram.accounts // {}) | type == "object") and
    ((.channels.telegram.accounts // {}) | keys |
      all(. == "default" or . == $conta)) and
    (.plugins.entries["trilha-aprovacao"].config | type == "object") and
    ((.channels.telegram.accounts[$conta].botToken //
      .channels.telegram.accounts.default.botToken //
      .channels.telegram.botToken) |
      type == "object" and
      .source == "file" and
      .provider == "arquivo" and
      .id == "/telegram/tokenDoBot" and
      (keys | sort) == ["id", "provider", "source"])
  ' "${origem}" >/dev/null ||
    falhar "configuracao da conta do Telegram ou SecretRef invalida."
  jq --arg conta "${conta}" '
    (.channels.telegram.accounts[$conta].botToken //
      .channels.telegram.accounts.default.botToken //
      .channels.telegram.botToken) as $token |
    .channels.telegram.accounts = {
      ($conta): {
        enabled: true,
        botToken: $token
      }
    } |
    .channels.telegram.defaultAccount = $conta |
    del(.channels.telegram.botToken, .channels.telegram.tokenFile) |
    .plugins.entries["trilha-aprovacao"].config
      .identificadorDaContaDoBot = $conta
  ' "${origem}" > "${destino}"
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
  normalizar_diretorio_dedicado "$1" OPENCLAW_DIRETORIO_CREDENCIAIS_MCP
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

validar_diretorio_controlado() {
  local diretorio="$1"
  [[ -d "${diretorio}" && ! -L "${diretorio}" ]] ||
    falhar "$2 deve ser um diretorio regular, nao simbolico."
  [[ "$(stat -c '%u' -- "${diretorio}")" == "$(id -u)" ]] ||
    falhar "$2 deve pertencer ao usuario atual."
  local modo
  modo="$(stat -c '%a' -- "${diretorio}")"
  [[ "${modo}" =~ ^[0-7]{3,4}$ ]] ||
    falhar "$2 possui permissoes invalidas."
  (( (8#${modo: -3} & 0022) == 0 )) ||
    falhar "$2 nao pode permitir escrita por grupo ou outros usuarios."
}

validar_ou_criar_marcador_dedicado() {
  local diretorio="$1"
  local tipo="$2"
  local marcador="${diretorio}/.trilha-aprovacao-diretorio"
  local conteudo="trilha-aprovacao:${tipo}:v1"
  if [[ -e "${marcador}" || -L "${marcador}" ]]; then
    [[ -f "${marcador}" && ! -L "${marcador}" &&
      "$(stat -c '%a' -- "${marcador}")" == "600" &&
      "$(tr -d '\r\n' < "${marcador}")" == "${conteudo}" ]] ||
      falhar "marcador do diretorio dedicado e invalido."
    return
  fi

  local possui_entrada=""
  possui_entrada="$(find "${diretorio}" -mindepth 1 -maxdepth 1 -print -quit)"
  if [[ -n "${possui_entrada}" && "${tipo}" == "estado" ]]; then
    [[ -f "${diretorio}/openclaw.json" &&
      ! -L "${diretorio}/openclaw.json" &&
      -d "${diretorio}/agentes" && ! -L "${diretorio}/agentes" &&
      -d "${diretorio}/workspaces" && ! -L "${diretorio}/workspaces" &&
      -d "${diretorio}/provisionamentos" &&
      ! -L "${diretorio}/provisionamentos" ]] ||
      falhar "diretorio de estado sem marcador nao e vazio nem estado legado reconhecido."
  elif [[ -n "${possui_entrada}" && "${tipo}" == "credenciais-mcp" ]]; then
    local entrada nome quantidade=0
    while IFS= read -r -d '' entrada; do
      nome="${entrada##*/}"
      [[ "${nome}" =~ ^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-8][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}\.json$ &&
        -f "${entrada}" && ! -L "${entrada}" &&
        "$(stat -c '%a' -- "${entrada}")" == "600" ]] ||
        falhar "diretorio de credenciais sem marcador possui conteudo desconhecido."
      quantidade=$((quantidade + 1))
    done < <(find "${diretorio}" -mindepth 1 -maxdepth 1 -print0)
    (( quantidade > 0 )) ||
      falhar "diretorio de credenciais sem marcador possui conteudo desconhecido."
  fi

  local temporario
  temporario="$(mktemp "${diretorio}/.marcador.XXXXXX")"
  chmod 600 -- "${temporario}"
  printf '%s\n' "${conteudo}" > "${temporario}"
  mv -T -- "${temporario}" "${marcador}"
}

inicializar_diretorio_dedicado() {
  local diretorio="$1"
  local tipo="$2"
  local nome="$3"
  if [[ -e "${diretorio}" || -L "${diretorio}" ]]; then
    validar_diretorio_controlado "${diretorio}" "${nome}"
  else
    (umask 077; mkdir -p -- "${diretorio}")
    validar_diretorio_controlado "${diretorio}" "${nome}"
  fi
  validar_ou_criar_marcador_dedicado "${diretorio}" "${tipo}"
  chmod 700 -- "${diretorio}"
}

inicializar_diretorio_de_credenciais() {
  inicializar_diretorio_dedicado "$1" credenciais-mcp \
    "o diretorio de credenciais MCP"
}

caminho_da_credencial_mcp() {
  local diretorio="$1"
  local vinculo="$2"
  printf '%s/%s.json\n' "${diretorio}" "${vinculo}"
}

inicializar_estado() {
  local diretorio_de_estado="$1"
  inicializar_diretorio_dedicado "${diretorio_de_estado}" estado \
    "o diretorio de estado"

  local subdiretorio
  for subdiretorio in \
    agentes \
    workspaces \
    extensions \
    extensions/trilha-aprovacao \
    provisionamentos \
    revogados \
    temporarios; do
    local caminho="${diretorio_de_estado}/${subdiretorio}"
    if [[ -e "${caminho}" || -L "${caminho}" ]]; then
      validar_diretorio_controlado "${caminho}" \
        "o subdiretorio de estado ${subdiretorio}"
    else
      (umask 077; mkdir -- "${caminho}")
      validar_diretorio_controlado "${caminho}" \
        "o subdiretorio de estado ${subdiretorio}"
    fi
    chmod 700 -- "${caminho}"
  done
  if [[ ! -e "${diretorio_de_estado}/openclaw.json" &&
    ! -L "${diretorio_de_estado}/openclaw.json" ]]; then
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
  [[ -d "${diretorio_de_estado}" && ! -L "${diretorio_de_estado}" &&
    "$(stat -c '%a' -- "${diretorio_de_estado}")" == "700" ]] ||
    falhar "o diretorio de estado deve ser regular 0700 para bloqueio."
  local bloqueio_legado="${diretorio_de_estado}/.provisionamento.lock"
  if [[ -e "${bloqueio_legado}" || -L "${bloqueio_legado}" ]]; then
    [[ -f "${bloqueio_legado}" && ! -L "${bloqueio_legado}" &&
      "$(stat -c '%a' -- "${bloqueio_legado}")" == "600" ]] ||
      falhar "arquivo de bloqueio legado inseguro."
  fi
  exec 9<"${diretorio_de_estado}"
  flock -x 9
}

hash_do_token() {
  local arquivo="$1"
  tr -d '\r\n' < "${arquivo}" | sha256sum | cut -d' ' -f1
}

validar_modelo_do_workspace() {
  local diretorio="${diretorio_do_modulo}/modelos/workspace"
  local manifesto="${diretorio}/manifesto.json"
  [[ -f "${manifesto}" && ! -L "${manifesto}" ]] ||
    falhar "manifesto do modelo de workspace deve ser arquivo regular."
  jq -e '
    .versao == 1 and
    .arquivosGerenciados == [
      "AGENTS.md", "SOUL.md", "IDENTITY.md", "TOOLS.md", "USER.md"
    ]
  ' "${manifesto}" >/dev/null ||
    falhar "manifesto do modelo de workspace invalido."
  local arquivo
  while IFS= read -r arquivo; do
    [[ -f "${diretorio}/${arquivo}" && ! -L "${diretorio}/${arquivo}" ]] ||
      falhar "arquivo gerenciado do workspace invalido: ${arquivo}."
  done < <(jq -r '.arquivosGerenciados[]' "${manifesto}")
}

modelo_do_workspace_em_json() {
  validar_modelo_do_workspace
  local diretorio="${diretorio_do_modulo}/modelos/workspace"
  local manifesto="${diretorio}/manifesto.json"
  local versao
  versao="$(jq -r '.versao' "${manifesto}")"
  jq -cn \
    --argjson versao "${versao}" \
    --arg agents "$(sha256sum "${diretorio}/AGENTS.md" | cut -d' ' -f1)" \
    --arg soul "$(sha256sum "${diretorio}/SOUL.md" | cut -d' ' -f1)" \
    --arg identity "$(sha256sum "${diretorio}/IDENTITY.md" | cut -d' ' -f1)" \
    --arg tools "$(sha256sum "${diretorio}/TOOLS.md" | cut -d' ' -f1)" \
    --arg user "$(sha256sum "${diretorio}/USER.md" | cut -d' ' -f1)" \
    '{
      versao: $versao,
      hashes: {
        "AGENTS.md": $agents,
        "SOUL.md": $soul,
        "IDENTITY.md": $identity,
        "TOOLS.md": $tools,
        "USER.md": $user
      }
    }'
}

validar_workspace_gerenciado() {
  local workspace="$1"
  validar_modelo_do_workspace
  [[ -d "${workspace}" && ! -L "${workspace}" ]] ||
    falhar "workspace gerenciado deve ser diretorio regular."
  local arquivo
  while IFS= read -r arquivo; do
    if [[ -e "${workspace}/${arquivo}" || -L "${workspace}/${arquivo}" ]]; then
      [[ -f "${workspace}/${arquivo}" && ! -L "${workspace}/${arquivo}" ]] ||
        falhar "arquivo gerenciado do workspace nao pode ser simbolico: ${arquivo}."
    fi
  done < <(jq -r '.arquivosGerenciados[]' \
    "${diretorio_do_modulo}/modelos/workspace/manifesto.json")
}

instalar_modelo_do_workspace() {
  local workspace="$1"
  local diretorio_de_temporarios="$2"
  validar_workspace_gerenciado "${workspace}"
  local temporario
  temporario="$(mktemp -d "${diretorio_de_temporarios}/modelo-workspace.XXXXXX")"
  chmod 700 -- "${temporario}"
  while IFS= read -r arquivo; do
    install -m 600 -- "${diretorio_do_modulo}/modelos/workspace/${arquivo}" \
      "${temporario}/${arquivo}"
  done < <(jq -r '.arquivosGerenciados[]' \
    "${diretorio_do_modulo}/modelos/workspace/manifesto.json")
  while IFS= read -r arquivo; do
    mv -- "${temporario}/${arquivo}" "${workspace}/${arquivo}"
  done < <(jq -r '.arquivosGerenciados[]' \
    "${diretorio_do_modulo}/modelos/workspace/manifesto.json")
  rmdir -- "${temporario}"
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
    "consultar_operacao_assistida",
    "preparar_registro_de_estudo",
    "preparar_conclusao_do_bloco",
    "preparar_interrupcao_do_bloco",
    "preparar_correcao_do_estudo",
    "preparar_geracao_do_plano",
    "preparar_replanejamento",
    "preparar_alteracao_de_disponibilidade",
    "preparar_alteracao_de_prioridades",
    "preparar_cadastro_do_concurso",
    "preparar_catalogo_de_conteudos",
    "preparar_conteudo_programatico",
    "preparar_mapeamentos_do_edital",
    "validar_contexto_do_concurso",
    "preparar_ativacao_do_concurso",
    "preparar_arquivamento_do_concurso",
    "preparar_cancelamento_do_concurso"
  ]'
}

validar_permissoes_do_estado() {
  local diretorio_de_estado="$1"
  local modo
  modo="$(stat -c '%a' "${diretorio_de_estado}")"
  [[ "${modo}" == "700" ]] || falhar "o diretorio de estado deve ter permissao 700."
}
