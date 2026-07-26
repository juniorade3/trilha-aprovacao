# Auditoria do fluxo de confirmação OpenClaw/MCP

## Escopo

Esta auditoria cobre o caminho:

```text
Telegram
  -> dispatcher do OpenClaw
  -> plugin trilha-aprovacao
  -> integrador confiável
  -> endpoint HMAC do backend
  -> operação assistida
  -> registro de estudo e evidência
  -> recibo devolvido ao Telegram
```

Versão do runtime verificada:

- OpenClaw `2026.7.1`;
- revisão `2d2ddc43`;
- imagem fixada pelo projeto.

Nenhum código de confirmação, token, segredo HMAC, conteúdo de estudo ou
credencial foi incluído neste relatório.

## Diagnóstico

### Causa raiz do incidente

O usuário enviou `CONFIRMAR <codigo>` sem a barra inicial. O dispatcher do
OpenClaw somente encaminhava `/confirmar <codigo>` ao comando registrado pelo
plugin. A mensagem observada foi tratada como texto comum e enviada ao modelo,
portanto o integrador e o endpoint HMAC nunca receberam a confirmação.

Evidências independentes:

1. O transcript redigido do runtime registra a mensagem sem `/`.
2. Não existe requisição correspondente nos logs do plugin, integrador ou
   backend.
3. A tabela `requisicoes_confiaveis_da_automacao` não possui registro do
   endpoint de confirmação no horário do incidente.
4. As quatro operações afetadas permanecem em
   `AGUARDANDO_CONFIRMACAO`, sem método, update, confirmação, aplicação ou
   resultado.
5. Uma reprodução no dispatcher real da imagem fixada mostrou:
   - `/confirmar CODIGO`: roteado ao handler;
   - `CONFIRMAR CODIGO`: não roteado;
   - `CODIGO`: não roteado.

O backend não falhou ao aplicar o estudo nesse incidente: ele não foi chamado.

### Por que os testes anteriores não detectaram

Os testes do plugin localizavam o comando registrado e invocavam seu `handler`
diretamente. Isso provava a lógica interna, mas ignorava o dispatcher real,
onde textos sem `/` eram desviados para o modelo.

Os testes também simulavam:

- `messageId` e `updateId`, ausentes no contexto do comando nessa versão;
- `from`, `to` e `senderId` com o mesmo identificador;
- respostas `2xx` sem validar um recibo completo;
- backend falso retornando um estado de aplicação simplificado.

O teste backend existente chamava
`ServicoDeAplicacaoDeOperacoesAssistidas` diretamente. Ele não exercitava
plugin, integrador, assinatura HMAC, controller nem contrato HTTP do recibo.

## Achados adicionais

### Plugin

- Qualquer `2xx`, exceto o caso reconhecido de segunda confirmação, era
  anunciado como operação aplicada.
- O caminho de confirmação não usava o timeout configurado.
- O contexto de comando não oferece `messageId`, `updateId`, `chatId` nem
  metadado de reply.
- A validação exigia `from == to == senderId`; uma conversa direta válida pode
  ter chat e usuário distintos.
- A ausência ou invalidade de `accountId` caía silenciosamente para `default`.
- O fallback de idempotência dependia do texto não normalizado do comando.

### Integrador e provisionamento

- Qualquer resposta `2xx` do backend podia virar `OPERACAO_APLICADA`.
- A busca de provisionamento aceitava o primeiro arquivo compatível em vez de
  exigir exatamente um vínculo ativo, registrado e estruturalmente válido.
- A chave idempotente não incluía a conta do bot.
- Workspaces existentes não recebiam atualizações dos arquivos gerenciados.
- Metadados de conta não eram preservados no provisionamento e binding.
- Os logs não distinguiam com segurança vínculo, bot, Telegram, chat e sessão.
- O bundle declarava `toolFilter`, mas o adaptador Codex do OpenClaw `2026.7.1`
  descartava essa propriedade ao projetar `mcp_servers`. A allowlist não
  chegava ao controle nativo `enabled_tools`.

### Backend

- O vencimento era detectado por exceção, mas a operação não era persistida
  como `EXPIRADA`.
- Confirmação e aplicação não possuíam auditoria persistente suficiente.
- Métricas não cobriam recebimento, rejeição, idempotência, expiração,
  divergência e falha.
- A preparação idempotente podia devolver estado e código incompatíveis com
  uma operação já aplicada, expirada ou falha.
- O versionamento do registro de estudo não incluía a relação de cobertura
  entre material e tópico.
- A primeira etapa de confirmação reforçada não podia ser recuperada após
  perda da resposta.

### Hipóteses descartadas para o incidente

- Workspace desatualizado: os workspaces ativos já orientavam
  `/confirmar <codigo>`.
- Plugin desabilitado: plugin global e aliases estavam carregados.
- Sessão local divergente: não houve chamada ao integrador; a sessão não
  chegou a ser avaliada.
- Falha transacional do estudo: não houve requisição HMAC correspondente.

## Decisões de correção

### Captura determinística

O plugin deve usar captura anterior ao dispatcher do modelo para reconhecer
somente estes formatos exatos em conversa privada e autorizada:

```text
/confirmar CODIGO
CONFIRMAR CODIGO
CODIGO
```

O OpenClaw `2026.7.1` registra `inbound_claim`, mas somente o executa para
bindings `plugin-owned`. Os vínculos da Trilha usam bindings `route`. Por isso,
o plugin registra:

- `inbound_claim`, para bindings que suportam a captura direcionada;
- `before_dispatch`, hook global executado antes de comandos, resolução de
  resposta e modelo, como compatibilidade para bindings `route`.

Os dois hooks compartilham a mesma rotina e retornam `handled` quando
reconhecem uma confirmação. O código isolado continua restrito ao adaptador
confiável. O modelo não recebe capacidade de aplicar operações.

O comando `/confirmar` permanece registrado para compatibilidade. Ambos os
caminhos compartilham a mesma rotina.

### Identidade

- `senderId` identifica o usuário Telegram.
- `conversationId` identifica o chat.
- Chat e usuário podem ser diferentes.
- Grupos, canais, remetentes não autorizados e identificadores inválidos são
  rejeitados antes de qualquer chamada.
- `accountId` deve corresponder à conta configurada. Não existe fallback
  silencioso em conta nomeada.
- O comando nativo exige autorização explícita. No workaround
  `before_dispatch`, o hook roda depois da admissão `dmPolicy/allowFrom`; essa
  versão não repassa `commandAuthorized` ao hook. O teste na imagem oficial
  fixa essa ordem e confirma que o modelo não é chamado.

### Idempotência

O identificador real da mensagem é preferido quando o hook o fornece. O
fallback usa material canônico e hash, sem persistir ou registrar o código em
texto puro. Conta, bot, chat e Telegram participam da chave no integrador.

### Contrato do recibo

Sucesso exige resposta fechada:

- `codigo == OPERACAO_APLICADA`;
- identificador UUID;
- tipo da operação;
- estado `APLICADA`;
- resultado persistido.

Segunda etapa exige:

- `codigo == NOVA_CONFIRMACAO_EXIGIDA`;
- próximo código no alfabeto e tamanho esperados;
- frase canônica `/confirmar CODIGO`.

Qualquer outro `2xx` é falha de contrato e nunca é anunciado como sucesso.

### Sincronização de workspaces

Somente arquivos declarados como gerenciados pela aplicação são atualizados.
A atualização é versionada, validada, atômica e executada também para
provisionamentos existentes. Arquivos desconhecidos, sessão e credenciais são
preservados. Links simbólicos nos alvos gerenciados são recusados.

Além dos cinco arquivos de instrução, a sincronização reinstala o proxy,
manifesto e política MCP, restaura a entrada do plugin na allowlist e corrige o
binding de conta/chat. Assim, vínculo antigo não conserva adaptador ou política
de ferramentas de uma versão anterior.

### Isolamento de ferramentas MCP

O plugin de workspace continua declarando `toolFilter`, mas não depende de o
adaptador Codex preservá-lo. O proxy stdio carrega a política do `.mcp.json`
regular `0600`, valida formato e unicidade, filtra cada resposta `tools/list` e
rejeita `tools/call` fora da allowlist antes de qualquer acesso ao broker.
Política ausente, insegura, simbólica ou inválida faz o proxy falhar fechado.

### Transação backend

Registro de estudo, evidência, transição da operação e recibo permanecem na
mesma transação serializável. Falha durante aplicação não pode deixar cadastro
parcial.

Expiração precisa ser uma transição persistida. Rejeições que não representam
expiração continuam sem commit parcial.

## Observabilidade

Logs estruturados podem conter:

- correlation ID;
- operation ID;
- link ID;
- tipo e estado;
- etapa;
- status HTTP e código de erro;
- duração.

Logs não podem conter:

- código de confirmação;
- hash seguro do código;
- token MCP;
- segredo ou assinatura HMAC;
- observação/evidência do estudo;
- corpo integral da requisição ou resposta.

Métricas mínimas:

- preparação;
- confirmação recebida;
- confirmação rejeitada;
- aplicação;
- resposta idempotente;
- expiração;
- divergência de identidade/sessão;
- falha de aplicação.

Contadores de sucesso transacional devem ser registrados somente após commit.

## Testes de reprodução executados antes da correção

```text
Plugin local:                         10/10 passaram
Plugin na imagem OpenClaw 2026.7.1:  10/10 passaram
Dispatcher real:
  /confirmar CODIGO                  roteado
  CONFIRMAR CODIGO                   não roteado
  CODIGO                             não roteado
Integrador Node:                      6/6 passaram
Provisionador:                        passou
Validação OpenClaw:                   passou
Backend automação/schema:            16/16 passaram
Backend MCP:                          4/4 passaram
Backend domínio automação:           13/13 passaram
```

Esses resultados demonstram o ponto cego: todas as suítes antigas passavam
enquanto o formato usado no incidente não alcançava o handler.

## Validação pós-correção

Comandos e resultados efetivamente executados:

```text
make verificar-backend
  árvore de trabalho completa:            235/235 passaram
  snapshot isolado HEAD + staging:         199/199 passaram

ConfirmacaoDeRegistroDeEstudoPeloGatewayIntegracaoTest
  cenários de gateway/backend:               7/7 passaram

OpenClawConfirmacaoDeRegistroDeEstudoE2EIntegracaoTest
  caminho MCP -> plugin -> integrador
  -> HMAC -> estudo -> histórico:             1/1 passou

infraestrutura/openclaw/scripts/validar.sh
  integrador + proxy Node:                   16/16 passaram
  plugin local:                              29 passaram, 3 OCI ignorados
  provisionador/broker/Compose:              passaram

VALIDAR_COM_IMAGEM_OPENCLAW=1 \
  infraestrutura/openclaw/scripts/validar.sh
  plugin + mapper + dispatcher + runtime
  Codex na imagem oficial fixada:            32/32 passaram
  configuração OpenClaw:                     válida
  auditoria de segredos:                     limpa
  achados críticos:                          0

git diff --check
  staging:                                    passou
```

O gate isolado detectou e corrigiu dois problemas antes do commit: teste legado
que assumia versão Flyway inteira `17`, incompatível com `17.1`, e prompt
interativo do `mv` ao substituir proxy `0500` em terminal. O segundo gate
isolado passou integralmente.

A auditoria de segurança executada sem Gateway ativo produziu dois avisos
esperados, não críticos:

- a entrada dinâmica do plugin do vínculo parece “fantasma” para a varredura
  fria global, embora o teste do runtime Codex prove que o bundle é carregado
  no workspace;
- a sondagem WebSocket falha porque o comando isolado não inicia o Gateway.

O teste com a imagem oficial, o carregamento do bundle e a auditoria de
segredos passaram. A imagem validada foi:

```text
ghcr.io/openclaw/openclaw:2026.7.1
sha256:6a31d44b2944e7adcd2b582bf6fb463111264ebca97a0201795b799135bd102c
```

## Riscos residuais

- Um teste automatizado não substitui a validação final com atualização real do
  Telegram.
- O hook e o contexto usados pertencem ao contrato fixado do OpenClaw
  `2026.7.1`; a atualização do runtime exige repetir o teste de compatibilidade.
- Operações antigas já vencidas precisam de reconciliação controlada; não
  devem ser aplicadas automaticamente.
- O identificador canônico de mensagem deve continuar opaco e nunca virar
  credencial.
