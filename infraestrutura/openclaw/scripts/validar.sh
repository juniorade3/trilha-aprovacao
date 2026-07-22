#!/usr/bin/env bash
set -Eeuo pipefail

diretorio_dos_scripts="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
diretorio_do_modulo="$(cd -- "${diretorio_dos_scripts}/.." && pwd)"

for comando in bash curl docker grep jq node npm; do
  command -v "${comando}" >/dev/null 2>&1 || {
    printf "Erro: o comando '%s' e obrigatorio.\n" "${comando}" >&2
    exit 1
  }
done

for script in "${diretorio_do_modulo}"/scripts/*.sh "${diretorio_do_modulo}"/testes/*.sh; do
  bash -n "${script}"
done
for script in "${diretorio_do_modulo}"/scripts/*.mjs "${diretorio_do_modulo}"/testes/*.mjs; do
  node --check "${script}"
done
jq empty "${diretorio_do_modulo}/modelos/openclaw.json"
node --test "${diretorio_do_modulo}/testes/integrador-de-vinculos.test.mjs"
npm --prefix "${diretorio_do_modulo}/plugin-trilha" run check

temporario="$(mktemp -d)"
trap 'rm -rf -- "${temporario}"' EXIT
mkdir -p "${temporario}/estado"
mkdir -p "${temporario}/credenciais-mcp"
chmod 700 "${temporario}/estado" "${temporario}/credenciais-mcp"
printf '{}\n' > "${temporario}/segredos.json"
printf '{"auth_mode":"chatgpt","tokens":{}}\n' > "${temporario}/autenticacao-codex.json"
printf '700000001\n' > "${temporario}/identificador-bot"
printf 'segredo-de-teste-do-gateway-com-mais-de-trinta-e-dois-bytes\n' \
  > "${temporario}/segredo-gateway"
chmod 600 "${temporario}/segredos.json" "${temporario}/autenticacao-codex.json" \
  "${temporario}/identificador-bot" \
  "${temporario}/segredo-gateway"
OPENCLAW_DIRETORIO_ESTADO="${temporario}/estado" \
OPENCLAW_DIRETORIO_CREDENCIAIS_MCP="${temporario}/credenciais-mcp" \
OPENCLAW_ARQUIVO_SEGREDOS="${temporario}/segredos.json" \
OPENCLAW_ARQUIVO_AUTENTICACAO_CODEX="${temporario}/autenticacao-codex.json" \
OPENCLAW_ARQUIVO_IDENTIFICADOR_BOT="${temporario}/identificador-bot" \
OPENCLAW_ARQUIVO_SEGREDO_GATEWAY="${temporario}/segredo-gateway" \
  docker compose -f "${diretorio_do_modulo}/compose.yaml" config > "${temporario}/compose-resolvido.yaml"
OPENCLAW_DIRETORIO_ESTADO="${temporario}/estado" \
OPENCLAW_DIRETORIO_CREDENCIAIS_MCP="${temporario}/credenciais-mcp" \
OPENCLAW_ARQUIVO_SEGREDOS="${temporario}/segredos.json" \
OPENCLAW_ARQUIVO_AUTENTICACAO_CODEX="${temporario}/autenticacao-codex.json" \
OPENCLAW_ARQUIVO_IDENTIFICADOR_BOT="${temporario}/identificador-bot" \
OPENCLAW_ARQUIVO_SEGREDO_GATEWAY="${temporario}/segredo-gateway" \
  docker compose -f "${diretorio_do_modulo}/compose.yaml" config --format json \
  > "${temporario}/compose-resolvido.json"

! grep -Eq '^[[:space:]]+ports:' "${temporario}/compose-resolvido.yaml"
! grep -q '/var/run/docker.sock' "${temporario}/compose-resolvido.yaml"
! grep -Eq '^[[:space:]]+privileged:[[:space:]]+true' "${temporario}/compose-resolvido.yaml"
grep -q 'read_only: true' "${temporario}/compose-resolvido.yaml"
grep -q 'no-new-privileges:true' "${temporario}/compose-resolvido.yaml"
grep -q 'target: /run/secrets/credenciais-mcp' "${temporario}/compose-resolvido.yaml"
grep -q 'target: /opt/trilha/broker-de-credenciais-mcp.mjs' "${temporario}/compose-resolvido.yaml"
grep -q 'target: /home/node/.openclaw/extensions/trilha-aprovacao' \
  "${temporario}/compose-resolvido.yaml"
grep -q 'target: /run/secrets/codex-cli/auth.json' \
  "${temporario}/compose-resolvido.yaml"
grep -q 'target: /opt/trilha/scripts' "${temporario}/compose-resolvido.yaml"
grep -q 'sha256:6a31d44b2944e7adcd2b582bf6fb463111264ebca97a0201795b799135bd102c' \
  "${temporario}/compose-resolvido.yaml"
jq -e '
  (.services.gateway.volumes | all(.target != "/run/secrets/credenciais-mcp")) and
  (.services.gateway.volumes | all(.target != "/run/secrets/segredo-gateway" and
    .target != "/run/secrets/identificador-bot")) and
  (.services.gateway.volumes | any(.target == "/run/secrets/codex-cli/auth.json" and
    .read_only == true)) and
  (.services.gateway.volumes | any(.target == "/home/node/.openclaw/extensions/trilha-aprovacao" and
    .read_only == true)) and
  (.services["broker-credenciais"].volumes | any(.target == "/run/secrets/credenciais-mcp" and .read_only == true)) and
  (.services["broker-credenciais"].volumes | all(.target != "/home/node/.openclaw")) and
  (.services["broker-credenciais"].volumes | all(.target != "/run/secrets/codex-cli/auth.json")) and
  (.services["broker-credenciais"].networks | has("credenciais") and has("saida")) and
  (.services.integrador.volumes | any(.target == "/home/node/.openclaw" and
    (.read_only // false) == false)) and
  (.services.integrador.volumes | any(.target == "/run/secrets/credenciais-mcp" and
    (.read_only // false) == false)) and
  (.services.integrador.volumes | any(.target == "/run/secrets/segredo-gateway" and .read_only == true)) and
  (.services.integrador.volumes | any(.target == "/run/secrets/identificador-bot" and .read_only == true)) and
  (.services.integrador.volumes | all(.target != "/run/secrets/codex-cli/auth.json")) and
  (.services.integrador.networks | has("integracoes") and has("saida")) and
  (.services.gateway.networks | has("credenciais") and has("integracoes") and has("saida")) and
  (.services.gateway.ports == null) and (.services["broker-credenciais"].ports == null) and
  (.services.integrador.ports == null) and
  (.networks.credenciais.internal == true) and
  (.networks.integracoes.internal == true)' "${temporario}/compose-resolvido.json" >/dev/null

jq -e '
  (.models.providers.openai | has("apiKey") | not) and
  .models.providers.openai.agentRuntime.id == "codex" and
  .agents.defaults.model.primary == "openai/gpt-5.5" and
  .plugins.entries.codex.enabled == true and
  .plugins.entries.codex.config.appServer.homeScope == "agent" and
  (.tools.deny | index("group:runtime")) != null and
  (.tools.deny | index("group:fs")) != null' "${diretorio_do_modulo}/modelos/openclaw.json" >/dev/null

"${diretorio_do_modulo}/testes/testar-provisionador.sh"
"${diretorio_do_modulo}/testes/testar-saida-do-broker.sh"

if [[ "${VALIDAR_COM_IMAGEM_OPENCLAW:-0}" == "1" ]]; then
  estado_da_imagem="${temporario}/estado-imagem"
  credenciais_da_imagem="${temporario}/credenciais-imagem"
  "${diretorio_do_modulo}/scripts/inicializar-estado.sh" \
    --diretorio-estado "${estado_da_imagem}" \
    --diretorio-credenciais-mcp "${credenciais_da_imagem}" >/dev/null
  printf 'mcp_CCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCC\n' \
    > "${temporario}/token-imagem"
  chmod 600 "${temporario}/token-imagem"
  "${diretorio_do_modulo}/scripts/provisionar-vinculo.sh" \
    --diretorio-estado "${estado_da_imagem}" \
    --diretorio-credenciais-mcp "${credenciais_da_imagem}" \
    --identificador-vinculo 323e4567-e89b-42d3-a456-426614174002 \
    --identificador-bot 700000001 \
    --identificador-telegram 800000001 \
    --identificador-chat 800000001 \
    --identificador-agente trilha_validacao \
    --identificador-sessao sessao:validacao \
    --token-mcp-arquivo "${temporario}/token-imagem" \
    --url-mcp http://host.docker.internal:8080/mcp >/dev/null
  install -d -m 700 -- \
    "${estado_da_imagem}/extensions/trilha-aprovacao"
  jq -n \
    --arg bot '700000001:token-de-teste-nao-real' \
    --arg gateway 'token-de-gateway-nao-real-com-mais-de-trinta-e-dois-caracteres' \
    '{telegram: {tokenDoBot: $bot}, gateway: {token: $gateway}}' \
    > "${temporario}/segredos-imagem.json"
  chmod 600 "${temporario}/segredos-imagem.json"
  docker run --rm --user 1000:1000 \
    -e HOME=/home/node \
    -e OPENCLAW_HOME=/home/node \
    -e OPENCLAW_STATE_DIR=/home/node/.openclaw \
    -e OPENCLAW_CONFIG_PATH=/home/node/.openclaw/openclaw.json \
    -e CODEX_HOME=/run/secrets/codex-cli \
    -v "${estado_da_imagem}:/home/node/.openclaw" \
    -v "${temporario}/segredos-imagem.json:/run/secrets/segredos-openclaw.json:ro" \
    -v "${temporario}/autenticacao-codex.json:/run/secrets/codex-cli/auth.json:ro" \
    -v "${diretorio_do_modulo}/plugin-trilha:/home/node/.openclaw/extensions/trilha-aprovacao:ro" \
    ghcr.io/openclaw/openclaw:2026.7.1@sha256:6a31d44b2944e7adcd2b582bf6fb463111264ebca97a0201795b799135bd102c \
    node dist/index.js config validate --json
  docker run --rm --user 1000:1000 \
    -e HOME=/home/node \
    -e OPENCLAW_HOME=/home/node \
    -e OPENCLAW_STATE_DIR=/home/node/.openclaw \
    -e OPENCLAW_CONFIG_PATH=/home/node/.openclaw/openclaw.json \
    -e CODEX_HOME=/run/secrets/codex-cli \
    -v "${estado_da_imagem}:/home/node/.openclaw" \
    -v "${temporario}/segredos-imagem.json:/run/secrets/segredos-openclaw.json:ro" \
    -v "${temporario}/autenticacao-codex.json:/run/secrets/codex-cli/auth.json:ro" \
    -v "${diretorio_do_modulo}/plugin-trilha:/home/node/.openclaw/extensions/trilha-aprovacao:ro" \
    ghcr.io/openclaw/openclaw:2026.7.1@sha256:6a31d44b2944e7adcd2b582bf6fb463111264ebca97a0201795b799135bd102c \
    node dist/index.js security audit --deep
  docker run --rm --user 1000:1000 \
    -e HOME=/home/node \
    -e OPENCLAW_HOME=/home/node \
    -e OPENCLAW_STATE_DIR=/home/node/.openclaw \
    -e OPENCLAW_CONFIG_PATH=/home/node/.openclaw/openclaw.json \
    -e CODEX_HOME=/run/secrets/codex-cli \
    -v "${estado_da_imagem}:/home/node/.openclaw" \
    -v "${temporario}/segredos-imagem.json:/run/secrets/segredos-openclaw.json:ro" \
    -v "${temporario}/autenticacao-codex.json:/run/secrets/codex-cli/auth.json:ro" \
    -v "${diretorio_do_modulo}/plugin-trilha:/home/node/.openclaw/extensions/trilha-aprovacao:ro" \
    ghcr.io/openclaw/openclaw:2026.7.1@sha256:6a31d44b2944e7adcd2b582bf6fb463111264ebca97a0201795b799135bd102c \
    node dist/index.js secrets audit --check
fi

printf 'Validacoes da infraestrutura OpenClaw concluidas.\n'
