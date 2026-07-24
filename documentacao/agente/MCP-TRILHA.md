# MCP da Trilha da Aprovação

## 1. Objetivo

O MCP da Trilha expõe capacidades controladas da aplicação para um agente conversacional executado pelo OpenClaw e acessado pelo Telegram.

Ele não é uma API genérica e não substitui os casos de uso do sistema.

Seu papel é:

- autenticar uma integração individual;
- derivar o usuário correto;
- publicar ferramentas tipadas;
- consultar dados atuais;
- preparar operações sem aplicar;
- permitir que um componente confiável confirme e aplique operações;
- auditar chamadas;
- impedir acesso direto ao banco, filesystem, shell ou rotas arbitrárias.

## 2. Localização

Backend:

```text
aplicativos/backend/src/main/java/br/com/trilhaaprovacao/automacao/
```

Infraestrutura OpenClaw:

```text
infraestrutura/openclaw/
```

Testes principais:

```text
aplicativos/backend/src/test/java/br/com/trilhaaprovacao/automacao/mcp/McpIntegracaoTest.java
```

Documentação histórica e contratual:

```text
documentacao/assistente-telegram-mcp/
```

## 3. Dependência

O backend usa:

```xml
<dependency>
  <groupId>io.modelcontextprotocol.sdk</groupId>
  <artifactId>mcp</artifactId>
  <version>2.0.0</version>
</dependency>
```

## 4. Ativação

A automação é condicional:

```yaml
trilha:
  automacao:
    habilitada: ${ASSISTENTE_TELEGRAM_HABILITADO:false}
```

Quando a propriedade não é `true`:

- o servidor MCP não é criado;
- a cadeia de segurança MCP não é criada;
- a interface do Telegram deve permanecer escondida;
- o sistema web continua funcionando.

A ativação não deve ocorrer apenas porque o código compila.

## 5. Arquitetura

```text
Usuário
  -> DM do Telegram
  -> Gateway OpenClaw
  -> agente exclusivo
  -> proxy MCP stdio do workspace
  -> broker de credenciais
  -> backend /mcp
  -> catálogo de ferramentas
  -> casos de uso
  -> PostgreSQL
```

Canal de confirmação:

```text
Usuário
  -> /confirmar CODIGO
  -> plugin OpenClaw
  -> integrador confiável
  -> endpoint HMAC do backend
  -> validação e aplicação
```

## 6. Componentes do backend

### `ConfiguracaoDoServidorMcp`

Responsabilidades:

- registrar transporte HTTP stateless;
- publicar `/mcp`;
- validar hosts e origens;
- extrair contexto autenticado;
- criar servidor síncrono stateless;
- registrar ferramentas;
- validar nome e entrada;
- definir timeout.

Configuração atual:

- nome: `trilha-aprovacao`;
- versão: `1`;
- endpoint: `/mcp`;
- timeout: 15 segundos;
- validação estrita de nomes;
- validação de input;
- instrução para preservar ordem, estados, valores e avisos.

### `ConfiguracaoDeSegurancaMcp`

Cria uma cadeia Spring Security exclusiva:

- `@Order(1)`;
- matcher `/mcp` e `/mcp/**`;
- CSRF desabilitado somente nessa cadeia;
- stateless;
- sem request cache;
- sem Basic;
- sem form login;
- sem logout;
- autoridade exigida: `INTEGRACAO_MCP`;
- resposta 401 estruturada.

Não reutiliza a sessão web.

### `FiltroDeCredencialMcp`

Lê:

- `Authorization: Bearer ...`;
- `X-Identificador-Do-Agente`;
- `X-Identificador-Da-Sessao`.

Depois:

1. autentica o token;
2. cria autenticação Spring;
3. anexa `IdentidadeDaIntegracaoMcp` ao request;
4. limpa o contexto ao terminar.

### `AutenticadorDeCredencialMcp`

Valida:

- prefixo `mcp_`;
- tamanho;
- hash do token;
- validade e revogação da credencial;
- vínculo ativo;
- provisionamento concluído;
- correspondência do agente;
- correspondência da sessão;
- usuário ativo;
- registro de último uso.

Retorna uma identidade contendo:

- usuário;
- vínculo;
- credencial;
- bot;
- Telegram;
- agente;
- sessão;
- versão do vínculo;
- escopos.

### `ContextoDaChamadaMcp`

Transporta para a ferramenta:

- identidade autenticada;
- identificador de correlação;
- identificador do evento externo, quando presente.

O contexto não vem dos argumentos produzidos pelo modelo.

### `CatalogoDeFerramentasMcp`

Responsabilidades:

- declarar ferramentas;
- declarar schemas;
- declarar escopo;
- definir anotações MCP;
- executar serviços;
- estruturar sucesso e erro;
- auditar entrada e saída.

### `ServicoDeConsultasMcp`

Agrega dados de:

- planejamento;
- revisões;
- prioridades;
- dashboard;
- histórico;
- concurso;
- bloco;
- operação assistida.

As consultas recebem o usuário derivado.

### `ServicoDePreparacoesMcp`

Responsabilidades:

- validar o tipo;
- consultar versões atuais;
- gerar prévia determinística quando necessário;
- montar proposta canônica;
- criar operação assistida;
- definir chave de idempotência;
- emitir código de confirmação.

A preparação não altera o fato de negócio.

### `ServicoDeOperacoesAssistidas`

Responsabilidades:

- persistir operação;
- assinar proposta;
- controlar estado;
- expiração;
- idempotência;
- consulta;
- atualização de recibo.

### `ServicoDeAplicacaoDeOperacoesAssistidas`

Responsabilidades:

- localizar vínculo e operação;
- validar código, método, bot, Telegram, chat, sessão e update;
- bloquear a operação;
- validar expiração;
- recalcular versões;
- validar assinatura;
- realizar segunda etapa quando reforçada;
- chamar o caso de uso real;
- aplicar tudo em transação serializável;
- persistir recibo;
- retornar resultado anterior em repetição segura.

### `ServicoDeCadastroAssistidoDeConcursos`

Valida e prepara estruturas de:

- concurso;
- edital;
- cargo;
- provas;
- grupos;
- matérias;
- itens oficiais;
- mapeamentos.

Cadastro confirmado é aplicado em lote e nasce em rascunho.

### `ServicoDeOperacoesCriticasMcp`

Prepara:

- ativação;
- arquivamento;
- cancelamento.

Exige confirmação reforçada.

### `ServicoDeAuditoriaMcp`

Registra:

- usuário;
- vínculo;
- ator `IA_TELEGRAM`;
- ferramenta;
- ação;
- hash de entrada;
- hash de saída;
- fonte MCP;
- resultado;
- correlação.

A auditoria não armazena token nem corpo integral da conversa.

## 7. Protocolo

### Transporte

Streamable HTTP stateless em:

```text
POST /mcp
```

### Cabeçalhos

Obrigatórios pelo broker:

```http
Authorization: Bearer mcp_...
X-Identificador-Do-Agente: ...
X-Identificador-Da-Sessao: ...
```

O protocolo pode encaminhar:

```http
MCP-Protocol-Version: ...
X-Identificador-De-Correlacao: ...
X-Identificador-Do-Update: ...
```

### Resposta de sucesso

```json
{
  "versaoDoContrato": "1",
  "identificadorDaCorrelacao": "UUID",
  "geradoEm": "instante ISO 8601",
  "dados": {},
  "avisos": []
}
```

### Erro de ferramenta

```json
{
  "versaoDoContrato": "1",
  "erro": {
    "codigo": "CODIGO",
    "mensagem": "Mensagem segura",
    "recuperavel": true,
    "identificadorDaCorrelacao": "UUID",
    "campos": []
  }
}
```

O resultado MCP usa `isError=true`.

## 8. Schemas

Regras:

- ferramenta em `snake_case`;
- propriedades em `camelCase`;
- `type=object`;
- `additionalProperties=false`;
- datas em ISO;
- UUID textual;
- limites explícitos;
- enums fechados;
- output schema também fechado;
- sucesso e erro declarados no `oneOf`.

Anotações:

- leitura: `readOnlyHint=true`;
- preparação: `readOnlyHint=false`;
- todas: `destructiveHint=false`;
- todas: `idempotentHint=true`;
- todas: `openWorldHint=false`.

## 9. Escopos

Escopos usados:

- `planejamento:ler`;
- `prioridades:ler`;
- `concursos:ler`;
- `estudos:ler`;
- `operacoes:ler`;
- `operacoes:preparar`.

A descoberta pode expor o catálogo, mas a execução exige o escopo da ferramenta.

## 10. Catálogo atual

### Consultas

| Ferramenta | Escopo |
|---|---|
| `obter_agenda_de_estudos_de_hoje` | `planejamento:ler` |
| `obter_revisoes_devidas` | `planejamento:ler` |
| `obter_prioridades_atuais` | `prioridades:ler` |
| `obter_progresso_do_concurso` | `concursos:ler` |
| `obter_historico_recente` | `estudos:ler` |
| `obter_estrutura_do_concurso` | `concursos:ler` |
| `explicar_bloco_de_estudo` | `planejamento:ler` |
| `consultar_operacao_assistida` | `operacoes:ler` |

### Preparações de estudo e planejamento

| Ferramenta | Tipo interno |
|---|---|
| `preparar_registro_de_estudo` | `REGISTRO_DE_ESTUDO` |
| `preparar_conclusao_do_bloco` | `CONCLUSAO_DO_BLOCO` |
| `preparar_interrupcao_do_bloco` | `INTERRUPCAO_DO_BLOCO` |
| `preparar_correcao_do_estudo` | `CORRECAO_DO_ESTUDO` |
| `preparar_geracao_do_plano` | `GERACAO_DO_PLANO` |
| `preparar_replanejamento` | `REPLANEJAMENTO` |
| `preparar_alteracao_de_disponibilidade` | `ALTERACAO_DE_DISPONIBILIDADE` |
| `preparar_alteracao_de_prioridades` | `ALTERACAO_DE_PRIORIDADES` |

### Cadastro assistido

| Ferramenta | Finalidade |
|---|---|
| `preparar_cadastro_do_concurso` | estrutura integral |
| `preparar_catalogo_de_conteudos` | catálogo e estrutura |
| `preparar_conteudo_programatico` | itens oficiais |
| `preparar_mapeamentos_do_edital` | sugestões pendentes |
| `validar_contexto_do_concurso` | validação sem persistência |

### Operações críticas

| Ferramenta | Confirmação |
|---|---|
| `preparar_ativacao_do_concurso` | reforçada |
| `preparar_arquivamento_do_concurso` | reforçada |
| `preparar_cancelamento_do_concurso` | reforçada |

Total atual: 24 ferramentas.

## 11. Identidade e isolamento

O usuário nunca é aceito como argumento.

A resolução ocorre assim:

```text
token
  -> credencial
  -> vínculo
  -> usuário
```

Também são validados:

```text
agente informado == agente persistido
sessão informada == sessão persistida
vínculo ativo
usuário ativo
credencial ativa
```

Recurso de outro usuário retorna “não encontrado”.

## 12. Operações assistidas

Estados:

- `PREPARADA`;
- `AGUARDANDO_CONFIRMACAO`;
- `CONFIRMADA`;
- `APLICADA`;
- `CANCELADA`;
- `EXPIRADA`;
- `FALHOU`.

Fluxo:

1. ferramenta prepara;
2. backend calcula versões;
3. persiste proposta;
4. gera assinatura;
5. gera código;
6. usuário revisa;
7. confirmação chega fora do modelo;
8. backend recalcula versões;
9. compara assinatura;
10. aplica o caso de uso;
11. persiste recibo.

## 13. Idempotência

A preparação usa uma chave derivada de:

- tipo;
- correlação ou evento externo;
- vínculo;
- proposta canônica.

Resultado esperado:

- mesma chave e mesma proposta: mesma operação;
- mesma chave e proposta diferente: conflito;
- retry após timeout: consulta ou recebe resultado original.

A confirmação registra update e contexto. Operação aplicada não é aplicada novamente.

## 14. Concorrência

Preparações consultam versões.

Confirmação:

- usa isolamento serializável;
- bloqueia operação;
- recalcula versões;
- valida assinatura;
- rejeita prévia desatualizada;
- executa lote em transação;
- não permite aplicação parcial.

## 15. Confirmação comum

Aceita:

- botão;
- texto;
- voz.

Código:

```text
CONFIRMAR XXXXXXXX
```

Valida:

- código;
- método;
- bot;
- Telegram;
- chat;
- sessão;
- update;
- prazo;
- assinatura;
- versões.

O agente não possui ferramenta de aplicação.

## 16. Confirmação reforçada

Usada em operações críticas.

Etapa 1:

- valida contexto;
- não aplica;
- emite segundo código.

Etapa 2:

- exige o mesmo vínculo, bot, Telegram, chat e sessão;
- recalcula;
- aplica somente após validação completa.

## 17. Auditoria

Toda ferramenta tenta registrar sucesso ou falha.

A auditoria guarda hashes da entrada e saída. Falha de auditoria é registrada em log, mas o erro original da ferramenta permanece estruturado.

Nunca guardar:

- token;
- segredo HMAC;
- documento integral;
- chain-of-thought;
- SQL;
- stack trace na resposta.

## 18. Banco

A automação usa tabelas para:

- vínculos;
- credenciais;
- operações;
- eventos de auditoria;
- requisições confiáveis.

A migration V17 adiciona:

- coerência do provisionamento;
- unicidade da sessão ativa;
- tabela de requisições confiáveis;
- nonce;
- idempotência;
- hash do corpo;
- expiração;
- índices de limite, idempotência e limpeza.

## 19. OpenClaw

Versão documentada:

```text
v2026.7.1
```

Imagem fixada por tag e digest.

O OpenClaw:

- usa runtime Codex nativo;
- reutiliza autenticação do CLI montada somente leitura;
- não usa `OPENAI_API_KEY`;
- funciona em fail-closed;
- usa perfil `minimal`;
- não expõe shell, filesystem, navegador ou Docker;
- mantém `mcp.servers` global vazio;
- cria configuração MCP por workspace.

## 20. Plugin da Trilha

Comandos:

- `/conectar`;
- `/confirmar`.

O plugin:

- aceita somente Telegram;
- exige DM;
- valida identificador numérico;
- valida formato do código;
- envia DTO fechado ao integrador;
- não recebe segredo HMAC;
- não recebe token MCP;
- não lê corpo sensível do backend.

## 21. Integrador confiável

Responsabilidades:

- receber DTO do plugin;
- validar schema;
- aplicar rate limit;
- assinar requisição HMAC;
- trocar código;
- provisionar;
- registrar provisionamento;
- retomar falha após provisionamento local;
- encaminhar confirmação.

O integrador não é ferramenta do agente.

## 22. Broker de credenciais

O broker:

- monta diretório `0700`;
- lê arquivo por vínculo `0600`;
- rejeita symlink;
- valida chaves exatas;
- valida token;
- valida agente e sessão;
- valida URL MCP;
- aceita apenas `POST /mcp/{vinculo}`;
- limita corpo;
- usa timeout;
- não segue redirecionamento;
- injeta Bearer, agente e sessão;
- não repassa cabeçalhos de identidade do cliente.

O Gateway não monta o diretório de credenciais.

## 23. Configuração

Variáveis principais:

```dotenv
ASSISTENTE_TELEGRAM_HABILITADO=false
IDENTIFICADOR_DO_BOT_TELEGRAM=0
SEGREDO_DE_HASH_DA_AUTOMACAO=
IDENTIFICADOR_DA_CHAVE_DO_GATEWAY_OPENCLAW=gateway-openclaw
SEGREDO_DO_GATEWAY_OPENCLAW=
TOLERANCIA_DO_GATEWAY_OPENCLAW=PT2M
LIMITE_DO_GATEWAY_POR_MINUTO=60
LIMITE_DO_CORPO_DO_GATEWAY=65536
RETENCAO_DE_NONCES_DO_GATEWAY=PT168H
ORIGENS_PERMITIDAS_NO_MCP=http://localhost:5173,http://127.0.0.1:5173
HOSTS_PERMITIDOS_NO_MCP=localhost:*,127.0.0.1:*,backend:8080,host.docker.internal:8080
VALIDADE_DO_CODIGO_DO_TELEGRAM=PT10M
VALIDADE_DA_CREDENCIAL_MCP=P90D
```

Não preencha valores reais no Git.

## 24. Testes

O teste de integração MCP cobre:

- inicialização;
- nome do servidor;
- catálogo;
- 24 ferramentas;
- schemas fechados;
- anotações;
- chamada de consulta;
- resposta estruturada;
- atualização do último uso;
- auditoria;
- escopos;
- isolamento A/B;
- recurso alheio como inexistente;
- PostgreSQL real.

Também existem testes para:

- Gateway;
- segredos;
- HMAC;
- plugin;
- integrador;
- provisionador;
- broker;
- confirmação comum;
- confirmação reforçada;
- idempotência;
- migrations.

## 25. Operação

Validar:

```bash
infraestrutura/openclaw/scripts/validar.sh
```

Executar qualidade geral:

```bash
make verificar
```

Auditar OpenClaw:

```bash
docker compose -f infraestrutura/openclaw/compose.yaml exec gateway \
  node dist/index.js config validate --json

docker compose -f infraestrutura/openclaw/compose.yaml exec gateway \
  node dist/index.js secrets audit --check

docker compose -f infraestrutura/openclaw/compose.yaml exec gateway \
  node dist/index.js security audit --deep
```

## 26. Incidente

Ordem recomendada:

1. desabilitar feature flag;
2. revogar credenciais;
3. revogar token do bot, quando afetado;
4. parar OpenClaw;
5. preservar auditoria sem copiar segredos;
6. revogar/refazer autenticação Codex quando necessário;
7. identificar causa;
8. reprovisionar;
9. repetir validação e teste A/B;
10. reabilitar somente após aceite.

## 27. Como adicionar ferramenta

Checklist:

- [ ] caso de uso existente reutilizado;
- [ ] usuário derivado;
- [ ] schema fechado;
- [ ] output fechado;
- [ ] menor escopo;
- [ ] nenhuma aplicação direta;
- [ ] auditoria;
- [ ] erro estruturado;
- [ ] teste de sucesso;
- [ ] teste de escopo;
- [ ] teste A/B;
- [ ] teste de idempotência, se preparação;
- [ ] documentação atualizada;
- [ ] workspace OpenClaw atualizado;
- [ ] validação completa executada.

## 28. O que não fazer

- aceitar usuário no argumento;
- criar ferramenta `chamar_api`;
- criar ferramenta `executar_sql`;
- expor shell;
- expor filesystem;
- montar segredo no workspace;
- armazenar token em `openclaw.json`;
- permitir escrita sem confirmação;
- aplicar prévia antiga;
- adicionar operação destrutiva;
- usar documentação de planejamento como prova de execução;
- habilitar produção sem smoke real.
