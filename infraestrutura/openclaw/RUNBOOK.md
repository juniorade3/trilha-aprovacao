# Runbook do OpenClaw

## 1. Preparar estado e segredos

Use um usuario operacional dedicado com UID/GID `1000`, ou ajuste de forma consciente o `user` do Compose e a propriedade dos arquivos. O exemplo local usa caminhos fora do repositorio:

```bash
export OPENCLAW_DIRETORIO_ESTADO="$HOME/.local/share/trilha-openclaw"
export OPENCLAW_DIRETORIO_CREDENCIAIS_MCP="$HOME/.config/trilha-openclaw/credenciais-mcp"
export OPENCLAW_ARQUIVO_SEGREDOS="$HOME/.config/trilha-openclaw/segredos.json"
export OPENCLAW_ARQUIVO_AUTENTICACAO_CODEX="$HOME/.codex/auth.json"
export OPENCLAW_ARQUIVO_IDENTIFICADOR_BOT="$HOME/.config/trilha-openclaw/identificador-bot"
export OPENCLAW_ARQUIVO_SEGREDO_GATEWAY="$HOME/.config/trilha-openclaw/segredo-do-gateway"

install -d -m 700 "$OPENCLAW_DIRETORIO_ESTADO"
install -d -m 700 "$(dirname "$OPENCLAW_ARQUIVO_SEGREDOS")"
infraestrutura/openclaw/scripts/inicializar-estado.sh \
  --diretorio-estado "$OPENCLAW_DIRETORIO_ESTADO" \
  --diretorio-credenciais-mcp "$OPENCLAW_DIRETORIO_CREDENCIAIS_MCP"
```

Crie os tres arquivos por um cofre ou processo que nao registre valores no
historico. `segredos.json` deve ter o seguinte formato:

```json
{
  "telegram": { "tokenDoBot": "VALOR_REAL" },
  "gateway": { "token": "VALOR_ALEATORIO_COM_PELO_MENOS_32_BYTES" }
}
```

Nao ha `OPENAI_API_KEY`. O Gateway usa `openai/gpt-5.5` pelo runtime Codex
nativo e fail-closed do OpenClaw. O binario gerenciado pelo OpenClaw reutiliza a
autenticacao do login ja realizado no CLI do computador; ele nao executa o
binario do host. Confirme esse login antes de subir os containers:

```bash
codex login status
```

O Compose monta `OPENCLAW_ARQUIVO_AUTENTICACAO_CODEX` em modo somente leitura
apenas no Gateway e define `CODEX_HOME=/run/secrets/codex-cli`. Cada app-server
mantem configuracao e sessoes no diretorio isolado do agente; apenas a conta do
CLI e repassada pelo runtime. O perfil `minimal`, sem `exec` ou `process`, e a
negacao dos grupos de runtime e filesystem desabilitam o modo de codigo nativo.
Se a autenticacao estiver ausente,
expirada ou invalida, a chamada do modelo falha; nao existe fallback por chave
de API.

`identificador-bot` contem somente o identificador numerico do bot, sem o token.
`segredo-do-gateway` contem exatamente o mesmo segredo HMAC configurado no
backend por `SEGREDO_DO_GATEWAY_OPENCLAW`, com no minimo 32 bytes. O Gateway nao
monta esses dois arquivos; somente o integrador confiavel os recebe.

Verifique antes de iniciar:

```bash
chmod 600 "$OPENCLAW_ARQUIVO_SEGREDOS"
chmod 600 "$OPENCLAW_ARQUIVO_AUTENTICACAO_CODEX"
chmod 600 "$OPENCLAW_ARQUIVO_IDENTIFICADOR_BOT" "$OPENCLAW_ARQUIVO_SEGREDO_GATEWAY"
test "$(stat -c '%a' "$OPENCLAW_DIRETORIO_ESTADO")" = 700
test "$(stat -c '%a' "$OPENCLAW_DIRETORIO_CREDENCIAIS_MCP")" = 700
test "$(stat -c '%a' "$OPENCLAW_ARQUIVO_SEGREDOS")" = 600
test "$(stat -c '%a' "$OPENCLAW_ARQUIVO_AUTENTICACAO_CODEX")" = 600
test "$(stat -c '%a' "$OPENCLAW_ARQUIVO_IDENTIFICADOR_BOT")" = 600
test "$(stat -c '%a' "$OPENCLAW_ARQUIVO_SEGREDO_GATEWAY")" = 600
test ! -L "$OPENCLAW_ARQUIVO_SEGREDOS"
test ! -L "$OPENCLAW_ARQUIVO_AUTENTICACAO_CODEX"
jq empty "$OPENCLAW_ARQUIVO_SEGREDOS"
jq empty "$OPENCLAW_ARQUIVO_AUTENTICACAO_CODEX"
```

Nao use symlinks, nao coloque esses caminhos dentro do checkout e nao versione
copias ou backups. Estado e credenciais MCP devem ser diretorios separados; o
container do Gateway monta o estado e somente o arquivo de autenticacao Codex,
e apenas o broker monta as credenciais MCP.

## 2. Validar e iniciar

Antes de iniciar, confirme que `codex login status` informa uma sessao valida e
que o JSON local contem somente os segredos do Telegram e do Gateway.

```bash
infraestrutura/openclaw/scripts/validar.sh

OPENCLAW_DIRETORIO_ESTADO="$OPENCLAW_DIRETORIO_ESTADO" \
OPENCLAW_DIRETORIO_CREDENCIAIS_MCP="$OPENCLAW_DIRETORIO_CREDENCIAIS_MCP" \
OPENCLAW_ARQUIVO_SEGREDOS="$OPENCLAW_ARQUIVO_SEGREDOS" \
OPENCLAW_ARQUIVO_AUTENTICACAO_CODEX="$OPENCLAW_ARQUIVO_AUTENTICACAO_CODEX" \
docker compose -f infraestrutura/openclaw/compose.yaml up -d
```

O Compose nao publica `18789`. A verificacao ocorre dentro do container:

```bash
OPENCLAW_DIRETORIO_ESTADO="$OPENCLAW_DIRETORIO_ESTADO" \
OPENCLAW_DIRETORIO_CREDENCIAIS_MCP="$OPENCLAW_DIRETORIO_CREDENCIAIS_MCP" \
OPENCLAW_ARQUIVO_SEGREDOS="$OPENCLAW_ARQUIVO_SEGREDOS" \
OPENCLAW_ARQUIVO_AUTENTICACAO_CODEX="$OPENCLAW_ARQUIVO_AUTENTICACAO_CODEX" \
docker compose -f infraestrutura/openclaw/compose.yaml ps

OPENCLAW_DIRETORIO_ESTADO="$OPENCLAW_DIRETORIO_ESTADO" \
OPENCLAW_DIRETORIO_CREDENCIAIS_MCP="$OPENCLAW_DIRETORIO_CREDENCIAIS_MCP" \
OPENCLAW_ARQUIVO_SEGREDOS="$OPENCLAW_ARQUIVO_SEGREDOS" \
OPENCLAW_ARQUIVO_AUTENTICACAO_CODEX="$OPENCLAW_ARQUIVO_AUTENTICACAO_CODEX" \
docker compose -f infraestrutura/openclaw/compose.yaml exec gateway \
  node dist/index.js config validate --json
```

## 3. Provisionar um vinculo

No fluxo normal, o usuario envia `/conectar CODIGO` em DM. O plugin encaminha
um DTO sem segredos ao integrador interno; ele troca o codigo com HMAC, executa
os dois scripts abaixo e responde ao plugin somente com um codigo de resultado.
Se o registro final no backend falhar depois do provisionamento local, repetir
o mesmo comando retoma esse registro sem nova troca. Nao copie o token MCP para
logs nem para a conversa.

O procedimento manual a seguir fica disponivel para recuperacao operacional.

O adaptador confiavel troca o codigo de vinculo no backend e recebe o token MCP apenas uma vez. Grave esse token em arquivo temporario `0600`, sem imprimi-lo. Os identificadores de bot, Telegram e chat vem do update validado pelo adaptador. Para DM, chat e Telegram devem ser iguais.

```bash
umask 077
ARQUIVO_TOKEN_MCP="$(mktemp)"
# O adaptador confiavel escreve somente o campo token no arquivo acima.

infraestrutura/openclaw/scripts/provisionar-vinculo.sh \
  --diretorio-estado "$OPENCLAW_DIRETORIO_ESTADO" \
  --diretorio-credenciais-mcp "$OPENCLAW_DIRETORIO_CREDENCIAIS_MCP" \
  --identificador-vinculo 00000000-0000-4000-8000-000000000000 \
  --identificador-bot 700000001 \
  --identificador-telegram 800000001 \
  --identificador-chat 800000001 \
  --identificador-agente trilha_000000000000 \
  --identificador-sessao sessao:00000000-0000-4000-8000-000000000000 \
  --token-mcp-arquivo "$ARQUIVO_TOKEN_MCP" \
  --url-mcp http://host.docker.internal:8080/mcp

rm -f "$ARQUIVO_TOKEN_MCP"
```

Em ambiente distribuido, prefira HTTPS e DNS interno em `--url-mcp`. `host.docker.internal` existe no Compose apenas para desenvolvimento com o backend no host.

O provisionador copia o token para um JSON externo regular `0600` em `OPENCLAW_DIRETORIO_CREDENCIAIS_MCP` e grava no workspace somente `http://broker-credenciais:18890/mcp/{vinculo}`. O Gateway OpenClaw nao monta o diretorio de credenciais. Em cada chamada, o broker le o arquivo pelo UUID, injeta o bearer e os identificadores imutaveis de agente e sessao e encaminha ao backend; o runtime embedded enxerga apenas o MCP autenticado resultante.

Depois do provisionamento:

1. o orquestrador confiavel chama o endpoint de registro com HMAC, usando exatamente o mesmo segredo configurado no backend por `SEGREDO_DO_GATEWAY_OPENCLAW`;
2. reinicie o Gateway para descartar qualquer cache de plugins e MCP;
3. envie uma DM permitida e confirme que a resposta usa somente dados da conta vinculada;
4. repita com usuarios A e B e confirme isolamento nos dois sentidos.

```bash
ARQUIVO_SEGREDO_GATEWAY="$HOME/.config/trilha-openclaw/segredo-do-gateway"
chmod 600 "$ARQUIVO_SEGREDO_GATEWAY"

infraestrutura/openclaw/scripts/registrar-provisionamento.sh \
  --diretorio-estado "$OPENCLAW_DIRETORIO_ESTADO" \
  --identificador-vinculo 00000000-0000-4000-8000-000000000000 \
  --url-backend http://127.0.0.1:8080 \
  --identificador-chave gateway-openclaw \
  --segredo-gateway-arquivo "$ARQUIVO_SEGREDO_GATEWAY"

OPENCLAW_DIRETORIO_ESTADO="$OPENCLAW_DIRETORIO_ESTADO" \
OPENCLAW_DIRETORIO_CREDENCIAIS_MCP="$OPENCLAW_DIRETORIO_CREDENCIAIS_MCP" \
OPENCLAW_ARQUIVO_SEGREDOS="$OPENCLAW_ARQUIVO_SEGREDOS" \
OPENCLAW_ARQUIVO_AUTENTICACAO_CODEX="$OPENCLAW_ARQUIVO_AUTENTICACAO_CODEX" \
docker compose -f infraestrutura/openclaw/compose.yaml restart gateway
```

O registrador monta o corpo canonico `TRILHA-HMAC-V1`, usa nonce novo e chave de idempotencia estavel, chama `POST /api/v1/integracoes-confiaveis/telegram/vinculos/{id}/provisionamento` e somente marca o metadado local depois que o backend responde com o mesmo vinculo e agente. Ele nao faz retry automatico; uma nova execucao gera outro nonce e reutiliza a idempotencia segura. Esse segredo nunca deve entrar no container do OpenClaw ou no contexto do modelo.

O script e idempotente para o mesmo vinculo, corpo e token. Mesmo vinculo com dados diferentes ou Telegram ja usado falha sem alterar a configuracao.

## 4. Rotacionar vinculo e credencial MCP

A rotacao do backend revoga o vinculo atual e cria outro vinculo pendente; ela nao emite um token novo para a mesma identidade. Troque o novo codigo no adaptador confiavel, guarde o token retornado em arquivo temporario regular `0600` e reprovisione a identidade completa:

```bash
infraestrutura/openclaw/scripts/rotacionar-token-mcp.sh \
  --diretorio-estado "$OPENCLAW_DIRETORIO_ESTADO" \
  --diretorio-credenciais-mcp "$OPENCLAW_DIRETORIO_CREDENCIAIS_MCP" \
  --identificador-vinculo-anterior 00000000-0000-4000-8000-000000000000 \
  --identificador-vinculo-novo 11111111-1111-4111-8111-111111111111 \
  --identificador-bot 700000001 \
  --identificador-telegram 800000001 \
  --identificador-chat 800000001 \
  --identificador-agente trilha_111111111111 \
  --identificador-sessao sessao:11111111-1111-4111-8111-111111111111 \
  --token-mcp-arquivo /caminho/temporario/token \
  --url-mcp http://host.docker.internal:8080/mcp
```

O script exige vinculo e agente novos, cria workspace, sessao, bundle e arquivo de credencial novos, retira a rota antiga, remove a credencial antiga e arquiva o estado anterior em `revogados/<vinculo-anterior>`. Em seguida, execute `registrar-provisionamento.sh` para o identificador novo, remova o arquivo temporario, reinicie o Gateway e confirme que o vinculo anterior permanece revogado no backend.

## 5. Revogar um vinculo

Revogue primeiro a credencial no backend. Depois retire a rota local:

```bash
infraestrutura/openclaw/scripts/revogar-vinculo.sh \
  --diretorio-estado "$OPENCLAW_DIRETORIO_ESTADO" \
  --diretorio-credenciais-mcp "$OPENCLAW_DIRETORIO_CREDENCIAIS_MCP" \
  --identificador-vinculo 00000000-0000-4000-8000-000000000000
```

O script remove o arquivo externo que continha o token, retira agente/binding/allowlist e move workspace e sessao para `revogados/<vinculo>`. O `.mcp.json` do workspace nunca contem o bearer. O script nao apaga o historico automaticamente. Reinicie o Gateway e confirme que novas mensagens daquele Telegram sao recusadas.

## 6. Auditoria operacional

Depois de cada alteracao de versao ou configuracao:

```bash
OPENCLAW_DIRETORIO_ESTADO="$OPENCLAW_DIRETORIO_ESTADO" \
OPENCLAW_DIRETORIO_CREDENCIAIS_MCP="$OPENCLAW_DIRETORIO_CREDENCIAIS_MCP" \
OPENCLAW_ARQUIVO_SEGREDOS="$OPENCLAW_ARQUIVO_SEGREDOS" \
OPENCLAW_ARQUIVO_AUTENTICACAO_CODEX="$OPENCLAW_ARQUIVO_AUTENTICACAO_CODEX" \
docker compose -f infraestrutura/openclaw/compose.yaml exec gateway \
  node dist/index.js security audit --deep

OPENCLAW_DIRETORIO_ESTADO="$OPENCLAW_DIRETORIO_ESTADO" \
OPENCLAW_DIRETORIO_CREDENCIAIS_MCP="$OPENCLAW_DIRETORIO_CREDENCIAIS_MCP" \
OPENCLAW_ARQUIVO_SEGREDOS="$OPENCLAW_ARQUIVO_SEGREDOS" \
OPENCLAW_ARQUIVO_AUTENTICACAO_CODEX="$OPENCLAW_ARQUIVO_AUTENTICACAO_CODEX" \
docker compose -f infraestrutura/openclaw/compose.yaml exec gateway \
  node dist/index.js secrets audit --check
```

O `secrets audit` cobre as superficies reconhecidas pelo OpenClaw. A validacao da Trilha acrescenta a verificacao de que nenhum `.mcp.json`, workspace, diretorio de agente ou `openclaw.json` contem bearer MCP. O token existe somente no arquivo externo por vinculo, regular e `0600`, montado apenas no broker.

Tambem confirme:

- nenhuma chave real aparece em `git grep`, logs ou exportacoes de suporte;
- `docker inspect` nao mostra segredos em variaveis de ambiente;
- nao ha `ports`, `privileged`, Docker socket ou mounts adicionais;
- cada agente tem `agentDir`, workspace, binding e plugin proprios;
- `openclaw.json` mantem `mcp.servers` vazio;
- o servico `gateway` nao monta `/run/secrets/credenciais-mcp` e o broker nao monta o estado;
- somente o `gateway` monta `OPENCLAW_ARQUIVO_AUTENTICACAO_CODEX`, como arquivo
  regular `0600`, somente leitura, sob `/run/secrets/codex-cli`;
- `grep -R 'mcp_' "$OPENCLAW_DIRETORIO_ESTADO"` nao encontra token literal;
- cada JSON em `OPENCLAW_DIRETORIO_CREDENCIAIS_MCP` e arquivo regular `0600`;
- o backend continua derivando o usuario exclusivamente da credencial MCP.

## 7. Incidentes

Em suspeita de vazamento, nesta ordem:

1. desabilite a feature flag da automacao no backend;
2. revogue as credenciais MCP afetadas;
3. revogue o token do bot no BotFather se o incidente o incluir;
4. pare o Compose;
5. preserve auditoria e metadados, sem copiar tokens;
6. revogue a sessao Codex afetada, refaca `codex login` no host e rotacione o
   token do Gateway quando aplicavel;
7. reprovisione vinculos somente apos identificar a causa;
8. execute as validacoes, o security audit e o teste A/B antes de reabilitar.

Depois de refazer o login, reinicie o Gateway para remontar o arquivo atualizado
e confirme `codex login status` antes do smoke. A autenticacao nunca deve ser
copiada para o estado do OpenClaw ou para o repositorio.

## 8. Atualizar o OpenClaw

Nao use tag flutuante. Para uma nova versao:

1. leia changelog e schemas de configuracao/MCP;
2. obtenha o digest OCI com `docker buildx imagetools inspect`;
3. atualize tag, digest, commit documentado e testes no mesmo PR;
4. execute `VALIDAR_COM_IMAGEM_OPENCLAW=1 infraestrutura/openclaw/scripts/validar.sh`;
5. valide `config validate`, `secrets audit --check` e `security audit --deep`;
6. faca smoke real com dois usuarios antes de substituir a imagem em uso.
