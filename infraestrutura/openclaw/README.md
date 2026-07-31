# OpenClaw da Trilha

Infraestrutura do Gateway OpenClaw compartilhado para a integracao privada com
Telegram. Mantem isolamento de agente, sessao, workspace e credencial MCP por
vinculo e uma allowlist fechada de consultas e preparacoes da Trilha.

## Versao fixada

- OpenClaw: `v2026.7.1`, fonte oficial no commit `2d2ddc43d0dcf71f31283d780f9fe9ff4cc04fe4`.
- Imagem: `ghcr.io/openclaw/openclaw:2026.7.1`.
- Digest OCI multi-arquitetura: `sha256:6a31d44b2944e7adcd2b582bf6fb463111264ebca97a0201795b799135bd102c`.

O Compose usa `tag@digest`; atualizar somente depois de revisar a nova versao e repetir a validacao descrita no runbook.

## Arquitetura de isolamento

```text
DM Telegram privada -- /conectar CODIGO
        |                         |
        v                         v
Gateway OpenClaw          integrador confiavel interno
sem porta publicada       | HMAC + idempotencia + provisionamento
        |
        +-- binding Telegram A -> agente A -> workspace A -> bundle MCP A --+
        |
        +-- binding Telegram B -> agente B -> workspace B -> bundle MCP B --+
                                                                           |
                                                                           v
                                                    broker de credenciais isolado
                                                    | arquivo 0600 por vinculo
                                                    v
                                                    backend da Trilha /mcp
```

O plugin `trilha-aprovacao` recebe somente o codigo e os identificadores da DM
fornecidos pelo runtime. Ele chama o integrador por uma rede Docker interna e nao
recebe segredo, token MCP ou corpo do backend. O integrador valida um schema
fechado, exige identificadores positivos de remetente e chat vindos da conversa
direta validada, confere a conta do bot, aplica limites por Telegram e global,
assina a troca do codigo com HMAC e executa os provisionadores existentes.
Depois do provisionamento local, um retry conclui
diretamente o registro no backend sem depender de o codigo continuar valido.
Recibos de idempotencia guardam somente hash, estado e UUID.

O `mcp.servers` global permanece vazio. Cada workspace recebe um plugin Codex
minimo em `.openclaw/extensions`, descoberto somente pelo runtime daquele
agente e ativado explicitamente em `plugins.allow`. Um proxy stdio local
encaminha o protocolo para a URL interna e opaca do vinculo, contornando a
incompatibilidade do Codex com `cwd` em MCP HTTP nessa versao do OpenClaw.
Como o adaptador Codex do OpenClaw `2026.7.1` nao projeta `toolFilter`, o proxy
le a allowlist regular `0600` do proprio `.mcp.json`, filtra `tools/list` e
rejeita localmente qualquer `tools/call` fora dela antes do broker. O plugin
nao contem token, cabecalho de autenticacao nem caminho de segredo.

O broker, em outro processo e sem montar o estado do agente, resolve um arquivo externo `0600` por vinculo e injeta `Authorization`, `X-Identificador-Do-Agente` e `X-Identificador-Da-Sessao`. O backend valida os tres contra o vinculo ativo; nenhum deles e argumento controlado pelo modelo. O broker aceita somente `POST /mcp/{vinculo}`, limita o corpo, nao segue redirecionamentos e nunca repassa cabecalhos de identidade enviados pelo cliente.

O OpenClaw `v2026.7.1` aceita interpolacao de ambiente nos cabecalhos MCP, mas
o processo compartilhado faria todos os tokens existirem no ambiente do mesmo
runtime. A Trilha usa, em vez disso, um broker minimo: somente ele monta
`OPENCLAW_DIRETORIO_CREDENCIAIS_MCP`; o Gateway nao monta esse diretorio e
nenhum token aparece no workspace, no diretorio do agente ou no
`openclaw.json`.

As respostas do modelo usam `openai/gpt-5.5` pelo runtime Codex nativo do
OpenClaw, em modo fail-closed. O binario do Codex e gerenciado pelo OpenClaw;
ele apenas reutiliza o login ja feito pelo CLI no computador por meio de
`OPENCLAW_ARQUIVO_AUTENTICACAO_CODEX=$HOME/.codex/auth.json`. O arquivo e
montado somente para o Gateway, em modo somente leitura, com
`CODEX_HOME=/run/secrets/codex-cli`. Nao existe `OPENAI_API_KEY`, fallback para
chave de API ou copia dessa autenticacao nos workspaces. Telegram e
autenticacao do Gateway continuam usando `SecretRef` no arquivo externo de
segredos. O agente nao recebe ferramentas de filesystem, shell, execucao,
navegador, nodes, Docker, mensagens externas ou administracao.

## Protecoes aplicadas

- somente DMs numericas explicitamente permitidas; grupos desabilitados;
- um binding `per-channel-peer` por Telegram;
- runtime Codex nativo do OpenClaw em modo fail-closed, com o modelo
  `openai/gpt-5.5` e o plugin Codex;
- perfil de ferramentas `minimal`, sem `exec` ou `process`, e negacao explicita
  dos grupos de runtime e filesystem, desabilitando o modo de codigo nativo;
- somente ferramentas `trilha__*` explicitamente permitidas; `toolFilter`
  documenta a politica e o proxy stdio sempre a aplica antes do broker;
- configuracao por chat, comandos administrativos, exec approvals e cron desabilitados;
- container sem capacidades Linux, sem privilegios, sem Docker socket e com raiz somente leitura;
- Gateway em loopback e nenhuma porta publicada;
- long polling do Telegram, portanto sem webhook ou ingresso publico;
- comando `/conectar` disponivel antes do allowlist somente em DM, com schema
  fechado e sem credencial compartilhada com o plugin;
- integrador sem porta publicada, isolado do broker por rede, com segredos
  regulares `0600`, corpo limitado, timeout, rate limit e HMAC;
- arquivo de segredos, autenticacao Codex, estado e diretorio de credenciais
  MCP obrigatoriamente fora do repositorio e separados entre si;
- token MCP de entrada, credencial persistida e segredo HMAC devem ser arquivos regulares, nao links simbolicos, com permissao `0600`;
- alteracoes de configuracao serializadas com `flock` e escritas por troca atomica;
- bindings incluem conta do bot e chat; a allowlist identifica o remetente;
- cinco arquivos gerenciados e o adaptador MCP de cada workspace possuem
  sincronizacao atomica, preservando arquivos desconhecidos, sessao e credencial;
- metadados guardam somente SHA-256 do token MCP, nunca seu valor.
- o segredo HMAC do Gateway fica somente no backend e no orquestrador confiavel; nao e montado no container do agente;
- rotacao cria vinculo, agente, sessao, workspace e credencial novos, e revoga/arquiva integralmente o estado local anterior.

## Conteudo

- `compose.yaml`: processo compartilhado, sem exposicao de rede de entrada;
- `Dockerfile.integrador`: runtime fixado do integrador com `jq` para os scripts
  confiaveis;
- `modelos/openclaw.json`: configuracao base validada pelo schema oficial;
- `plugin-trilha/`: comando `/conectar` sem acesso a segredos;
- operacoes preparadas pelo MCP sao aplicadas pelo comando confiavel
  `/confirmar CODIGO` ou, quando comuns, por uma decisao explicita na sessao
  web com CSRF; ambos os caminhos revalidam versoes e assinatura no backend;
- `modelos/workspace/`: instrucoes copiadas para cada agente;
- `scripts/inicializar-estado.sh`: cria o estado externo com permissoes restritas;
- `scripts/provisionar-vinculo.sh`: cria agente, rota, workspace e bundle MCP isolados;
- `scripts/sincronizar-workspaces.sh`: atualiza arquivos gerenciados, proxy,
  manifesto/politica MCP, allowlist do plugin e bindings ativos antes de o
  integrador publicar saude;
- `scripts/broker-de-credenciais-mcp.mjs`: injeta a credencial do vinculo fora do processo do agente;
- `scripts/integrador-de-vinculos.mjs`: troca o codigo com HMAC, provisiona e
  registra o vinculo de forma retomavel;
- `scripts/registrar-provisionamento.sh`: assina com HMAC e confirma o provisionamento no endpoint confiavel do backend;
- `scripts/assinar-gateway.mjs`: calcula a assinatura sem expor o segredo na linha de comando;
- `scripts/rotacionar-token-mcp.sh`: substitui integralmente o vinculo anterior pelo novo;
- `scripts/revogar-vinculo.sh`: remove rota e credencial, preservando o workspace arquivado;
- `scripts/validar.sh`: sintaxe, invariantes, Compose e testes do plugin,
  integrador, provisionador e broker;
- `RUNBOOK.md`: implantacao, operacao e resposta a incidentes.

## Capacidades consolidadas

O Gateway consulta dados atuais, prepara operacoes de estudo e planejamento,
valida cadastro estruturado de concursos e encaminha confirmacoes ao adaptador
confiavel. Ativacao, arquivamento e cancelamento exigem dois codigos no mesmo
Telegram e sessao. O modelo nunca recebe ferramenta direta de aplicacao.

A allowlist possui 25 ferramentas. A ferramenta compacta
`trilha__preparar_importacao_completa_do_edital` recebe somente o identificador
de um staging validado, cargo, modo, politica e decisoes humanas. Arquivo bruto
e extracao integral nao entram no contexto MCP. A importacao sempre usa dois
codigos no primeiro corte; o segundo expira em no maximo cinco minutos, exige o
mesmo bot, Telegram, chat, sessao e metodo, e deve chegar em um update novo. O
concurso resultante permanece planejado ate uma ativacao separada.

Anexos disponibilizados pelo canal sao tratados como conteudo nao confiavel e
somente podem originar DTO fechado; o agente nao possui navegador ou filesystem.
Voz e validacao com Telegram real dependem das credenciais locais e fazem parte
da ativacao operacional. Provisionar o OpenClaw nao altera a feature flag do
backend automaticamente.

Execute a porta local com:

```bash
infraestrutura/openclaw/scripts/validar.sh
```

Para incluir a validacao pelo binario dentro da imagem oficial, que pode baixar uma imagem grande:

```bash
VALIDAR_COM_IMAGEM_OPENCLAW=1 infraestrutura/openclaw/scripts/validar.sh
```
