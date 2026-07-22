# Contratos MCP e conversas

## Finalidade e estado

Este documento define os contratos planejados entre o OpenClaw e a Trilha da
Aprovacao, os fluxos conversacionais e as fronteiras entre consulta, preparacao,
confirmacao e aplicacao. Ele nao registra ferramenta implementada, integracao
validada nem resultado de teste.

O MCP sera uma porta tipada para casos de uso especificos. Nao existira
ferramenta generica para chamar uma rota HTTP, executar SQL, acessar arquivos ou
escolher livremente um usuario. A aplicacao web e os casos de uso do monolito
continuarao sendo a fonte de verdade.

## Fronteiras do contrato

Uma chamada percorre as seguintes fronteiras:

```text
mensagem do usuario
        |
        v
Gateway confiavel do Telegram e OpenClaw
        |
        +-- resolve agente, sessao, credencial e contexto do canal
        |
        v
ferramenta MCP tipada
        |
        +-- autentica, autoriza e deriva o usuario
        |
        v
caso de uso da Trilha
```

As responsabilidades ficam separadas:

- o modelo interpreta a intencao, escolhe uma ferramenta permitida e explica o
  resultado;
- o adaptador MCP valida o contrato, o escopo e a identidade da integracao;
- os casos de uso consultam, validam e aplicam regras de negocio;
- o Gateway confiavel recebe a confirmacao diretamente do Telegram;
- somente o backend aplica uma operacao preparada e ainda atual;
- a memoria do agente ajuda na conversa, mas nunca substitui uma consulta.

Consultas nao alteram o estado de negocio. Elas podem atualizar metadados
tecnicos da credencial e produzir eventos de auditoria. Ferramentas
`preparar_*` podem persistir a operacao assistida, sua proposta e a auditoria,
mas nao alteram concurso, conteudo, estudo, evidencia ou planejamento.

## Identidade implicita e contexto confiavel

### Credencial MCP

Cada vinculo ativo tera uma credencial MCP exclusiva. O token apresentado ao
MCP resolve, no backend:

- o usuario da Trilha;
- o vinculo de Telegram;
- o bot e o identificador numerico externo autorizados;
- os escopos da credencial;
- a situacao, a expiracao e a revogacao;
- a versao do vinculo usada para invalidar sessoes antigas.

Nenhum schema MCP aceita `identificadorDoUsuario`, e um identificador informado
dentro de texto, documento ou mensagem nunca troca o usuario efetivo. Recursos
recebidos por identificador sao sempre buscados dentro do usuario derivado. Um
recurso de outro usuario responde como inexistente, sem revelar sua existencia.

Credencial ausente, expirada, revogada ou vinculada a agente diferente nao
degrada para sessao web e nao permite chamada anonima.

### Metadados fora dos argumentos do modelo

O cliente confiavel anexa, fora do objeto de argumentos da ferramenta:

- identificador de correlacao;
- identificador do update ou callback do Telegram;
- identificadores numericos de bot, chat e remetente;
- chave de idempotencia para preparacoes;
- versao do contrato solicitada.

O backend valida esses valores contra o vinculo resolvido. Eles nao podem ser
substituidos por texto do usuario nem por argumentos produzidos pelo modelo.
Chamadas fora do Telegram, como as iniciadas pela aplicacao web, recebem
contexto equivalente da propria sessao autenticada.

## Convencoes dos schemas

- Nomes de ferramentas usam `snake_case`; propriedades usam `camelCase`.
- Os schemas sao fechados: propriedades desconhecidas sao rejeitadas.
- Datas usam `AAAA-MM-DD`, instantes usam ISO 8601 com deslocamento e
  identificadores usam UUID textual.
- Duracoes sao minutos inteiros positivos. Percentuais sao numeros entre zero e
  cem quando o campo os admitir.
- O fuso de negocio e `America/Sao_Paulo` e aparece nas respostas temporais.
- Listas possuem limite declarado e ordem deterministica documentada pelo caso
  de uso correspondente.
- Campos ausentes permanecem ausentes ou `null`; o modelo nao os completa por
  inferencia.
- Enums usam os valores publicados pelo contrato, sem traducao pelo agente.
- Texto vindo de documento ou usuario e dado nao confiavel, nunca instrucao.

Toda resposta bem-sucedida segue conceitualmente:

```json
{
  "versaoDoContrato": "1",
  "identificadorDaCorrelacao": "00000000-0000-0000-0000-000000000000",
  "geradoEm": "2026-07-21T18:00:00-03:00",
  "dados": {},
  "avisos": [
    {
      "codigo": "CONTEXTO_INCOMPLETO",
      "mensagem": "Descricao segura para o usuario"
    }
  ]
}
```

Os valores acima e os exemplos deste documento sao ilustrativos; nao representam
dados reais nem comprovacao de execucao.

Um erro de ferramenta usa `isError: true` no resultado MCP e conteudo
estruturado equivalente a:

```json
{
  "versaoDoContrato": "1",
  "erro": {
    "codigo": "PREVIA_DE_AUTOMACAO_DESATUALIZADA",
    "mensagem": "A proposta mudou e precisa ser revisada novamente.",
    "recuperavel": true,
    "identificadorDaCorrelacao": "00000000-0000-0000-0000-000000000000",
    "campos": []
  }
}
```

Stack trace, SQL, token, hash de credencial, documento integral e detalhes de
outro usuario nunca fazem parte de uma resposta.

## Schemas conceituais compartilhados

### `ContextoTemporal`

```json
{
  "dataDeReferencia": "2026-07-21",
  "fusoHorario": "America/Sao_Paulo"
}
```

`dataDeReferencia` e determinada pelo backend quando a pergunta se refere a
"hoje". Ferramentas que admitem simulacao historica declaram o campo
explicitamente; o agente nao altera silenciosamente a data para obter resposta
diferente.

### `ReferenciaDeNegocio`

```json
{
  "identificador": "00000000-0000-0000-0000-000000000000",
  "tipo": "BLOCO_DE_ESTUDO",
  "rotulo": "Direito Administrativo - Atos administrativos",
  "versao": 3
}
```

A versao, quando existente, participa da assinatura da previa. O rotulo serve
somente para exibicao e nunca substitui o identificador.

### `AvisoDeOperacao`

```json
{
  "codigo": "EVIDENCIA_OBRIGATORIA",
  "nivel": "ATENCAO",
  "mensagem": "Informe o resultado antes de concluir.",
  "bloqueiaAplicacao": true
}
```

Niveis previstos: `INFORMACAO`, `ATENCAO` e `CRITICO`. Um aviso bloqueador
precisa ser resolvido por nova preparacao; ele nao pode ser apenas confirmado.

### `PreviaDeOperacao`

Todas as ferramentas `preparar_*` retornam a mesma estrutura externa:

```json
{
  "identificadorDaOperacao": "00000000-0000-0000-0000-000000000000",
  "tipo": "CONCLUIR_BLOCO",
  "estado": "AGUARDANDO_CONFIRMACAO",
  "resumo": "Concluir um bloco e registrar seu resultado",
  "efeitos": [
    {
      "acao": "ALTERAR",
      "recurso": {
        "identificador": "00000000-0000-0000-0000-000000000000",
        "tipo": "BLOCO_DE_ESTUDO",
        "rotulo": "Bloco selecionado",
        "versao": 3
      },
      "antes": {},
      "depois": {}
    }
  ],
  "avisos": [],
  "assinaturaDaPrevia": "sha256:...",
  "expiraEm": "2026-07-21T18:30:00-03:00",
  "confirmacao": {
    "nivel": "COMUM",
    "codigo": "7K9P2Q",
    "frase": "CONFIRMAR 7K9P2Q",
    "expiraEm": "2026-07-21T18:10:00-03:00"
  }
}
```

A proposta canonica, os identificadores, as versoes consultadas, o vinculo e o
contexto de confirmacao participam da assinatura. Campos de exibicao que nao
alteram a semantica nao mudam a assinatura.

Niveis de confirmacao:

- `COMUM`: um desafio valido aplica uma operacao comum;
- `DETALHADA`: exige que o resumo e as ambiguidades tenham sido apresentados;
- `REFORCADA`: exige segundo desafio, mesma conversa e mesma sessao.

Estados persistidos: `PREPARADA`, `AGUARDANDO_CONFIRMACAO`, `CONFIRMADA`,
`APLICADA`, `CANCELADA`, `EXPIRADA` e `FALHOU`.

### `FonteDaInformacao`

Usado no cadastro assistido:

```json
{
  "identificadorDoAnexo": "00000000-0000-0000-0000-000000000000",
  "hashDoConteudo": "sha256:...",
  "url": null,
  "pagina": 12,
  "secao": "Conhecimentos especificos",
  "trechoDeApoio": "Trecho limitado que sustenta o campo",
  "incerteza": "BAIXA"
}
```

Incertezas previstas: `BAIXA`, `MEDIA` e `ALTA`. Ausencia de fonte ou incerteza
alta impede confirmacao automatica de um mapeamento.

## Catalogo de ferramentas por sprint

### Sprint 2 — Consultas MCP

| Ferramenta | Argumentos controlados pelo modelo | Saida principal | Escopo minimo |
|---|---|---|---|
| `obter_agenda_de_estudos_de_hoje` | nenhum | data, capacidade, execucao do dia, blocos ordenados, revisoes e proximo bloco | `planejamento:ler` |
| `obter_revisoes_devidas` | `ate`, opcional e nunca anterior a hoje | revisoes vencidas, devidas, futuras no intervalo e blocos ja abertos | `planejamento:ler` |
| `obter_prioridades_atuais` | `identificadorDaMateria`, opcional | contexto oficial, grupos, faixas, posicoes, acoes e justificativas | `prioridades:ler` |
| `obter_progresso_do_concurso` | nenhum | concurso ativo, proxima prova, cobertura, lacunas, evidencias e resumo objetivo | `concursos:ler` |
| `obter_historico_recente` | `quantidadeDeDias` e `limite`, dentro dos limites do schema | estudos e execucoes ativos, totais e periodo efetivo | `estudos:ler` |
| `obter_estrutura_do_concurso` | `identificadorDoConcurso`, opcional; usa o ativo quando ausente | edital principal, cargo, provas, grupos, materias e resumo de mapeamento | `concursos:ler` |
| `explicar_bloco_de_estudo` | `identificadorDoBloco` | origem, topico, tipo, ranking, revisao, capacidade e justificativas persistidas | `planejamento:ler` |
| `consultar_operacao_assistida` | `identificadorDaOperacao` | estado, resumo, avisos, expiracao e recibo quando aplicado | `operacoes:ler` |

As consultas usam agregacoes no backend. Uma resposta nao instrui o agente a
percorrer listas e executar uma chamada por filho para reconstruir concurso,
plano ou historico.

`obter_agenda_de_estudos_de_hoje` e a fonte para perguntas como "o que estudar
hoje?". A ordem retornada pelo backend e preservada pelo agente. Ele pode
resumir, mas nao substituir um bloco por preferencia propria.

### Sprint 4 — Preparacoes de estudo e planejamento

| Ferramenta | Entrada de negocio | Previa produzida |
|---|---|---|
| `preparar_registro_de_estudo` | data, materia, topico opcional quando permitido, tipo, minutos, observacao e evidencia aplicavel | novo estudo, evidencia, padroes e impacto no diagnostico |
| `preparar_conclusao_do_bloco` | bloco, minutos executados, observacao e evidencia exigida pelo tipo | conclusao, registro, evidencia e efeitos no plano |
| `preparar_interrupcao_do_bloco` | bloco, minutos executados, observacao e evidencia opcional quando admitida | execucao parcial, minutos restantes e efeitos no plano |
| `preparar_correcao_do_estudo` | estudo original e dados corrigidos completos | preservacao da linhagem, novo estudo e nova evidencia |
| `preparar_geracao_do_plano` | plano, data de referencia, duracao principal e substituicao de gerados | proposta deterministica, revisoes, preservados e assinatura |
| `preparar_replanejamento` | plano, data de referencia, pendencias ignoradas e confirmacoes de limite | destinos, fragmentos, capacidade e pendencias sem alocacao |
| `preparar_alteracao_de_disponibilidade` | semana e minutos por dia | valores anteriores, novos e invalidacao de previas relacionadas |
| `preparar_alteracao_de_prioridades` | materias e prioridades desejadas | prioridades anteriores, novas e invalidacao de previas relacionadas |

Os objetos de evidencia reutilizam os contratos publicos da aplicacao:

```json
{
  "tipoDeEstudo": "QUESTOES",
  "resultadoDeQuestoes": {
    "quantidadeDeQuestoes": 20,
    "quantidadeDeAcertos": 14
  },
  "nivelDeRecordacao": null,
  "dificuldadePercebida": 4,
  "padroesDeErro": [
    {
      "descricao": "Confusao entre conceitos",
      "quantidadeDeOcorrencias": 2
    }
  ]
}
```

Erros continuam derivados por `quantidadeDeQuestoes - quantidadeDeAcertos`.
Obrigatoriedade, escalas, soma de ocorrencias, topico e estados da execucao sao
validados pelo caso de uso, nao pelo modelo.

Geracao e replanejamento reutilizam as assinaturas e regras deterministicas
existentes. O contrato da automacao nao cria uma segunda implementacao desses
algoritmos.

### Sprint 5 — Cadastro assistido de concursos

| Ferramenta | Entrada | Saida |
|---|---|---|
| `preparar_cadastro_do_concurso` | estrutura extraida, fontes e campos escolhidos pelo usuario | lote proposto de concurso, edital, cargo, provas, grupos, materias e itens |
| `preparar_catalogo_de_conteudos` | materias e topicos extraidos | correspondencias, criacoes, conflitos e pendencias |
| `preparar_conteudo_programatico` | edital, arvore de itens e fontes | arvore validada, duplicidades e itens sem destino seguro |
| `preparar_mapeamentos_do_edital` | itens e candidatos a topico | sugestoes justificadas e ambiguidades pendentes |
| `validar_contexto_do_concurso` | concurso ou operacao preparada | completude do edital principal, cargo, prova, conteudo e mapeamentos |

Cada elemento proposto usa uma das decisoes `CRIAR`, `REUTILIZAR`, `ALTERAR`,
`IGNORAR`, `CONFLITO` ou `PENDENTE`. Uma classificacao `CONFLITO` ou
`PENDENTE` nao e convertida em alteracao pelo agente.

Uma estrutura extraida identifica explicitamente:

- valor encontrado ou ausencia;
- fonte de cada campo;
- incerteza;
- candidato reutilizavel, quando houver;
- escolha humana, quando necessaria.

A aplicacao do cadastro ocorre em lote e em uma transacao. O concurso nasce em
rascunho, e ativacao nao faz parte da mesma operacao.

### Sprint 6 — Operacoes de alto impacto

Ativar, arquivar, cancelar ou substituir nao ganha uma ferramenta de aplicacao
visivel ao modelo. A ferramenta de preparacao adequada retorna confirmacao
`REFORCADA`; os dois desafios sao consumidos pelo endpoint confiavel do Gateway.
Exclusao fisica e alteracao de seguranca permanecem sem contrato.

## Contratos HTTP auxiliares

O MCP usa Streamable HTTP em `/mcp`. Autenticacao MCP e stateless e independente
da sessao e do CSRF da aplicacao web.

### Endpoints da aplicacao web

- `POST /api/v1/integracoes/telegram/codigos-de-vinculo`: cria um desafio de
  uso unico para o usuario da sessao;
- `GET /api/v1/integracoes/telegram/vinculo`: retorna o vinculo do usuario da
  sessao, sem segredo;
- `DELETE /api/v1/integracoes/telegram/vinculo`: revoga o vinculo e suas
  credenciais;
- `GET /api/v1/operacoes-assistidas`: lista apenas operacoes do usuario da
  sessao;
- `GET /api/v1/operacoes-assistidas/{id}`: consulta uma operacao do usuario da
  sessao.

### Endpoints exclusivos do Gateway confiavel

Contratos previstos:

- `POST /api/v1/integracoes/telegram/vinculos`: troca o codigo pelo vinculo e
  entrega uma credencial somente ao provisionamento seguro;
- `POST /api/v1/operacoes-assistidas/{id}/confirmacoes`: consome confirmacao
  comum ou o estagio atual da confirmacao reforcada;
- `POST /api/v1/operacoes-assistidas/{id}/cancelamento`: cancela uma operacao
  ainda confirmavel;
- `POST /api/v1/integracoes/telegram/anexos-temporarios`: recebe metadados e
  conteudo temporario dentro dos limites de seguranca.

Todos deduplicam update e callback antes de produzir efeito. A especificacao
OpenAPI interna detalhara os schemas sem mudar essas semanticas.

Esses endpoints exigem autenticacao propria do Gateway, assinatura da mensagem,
timestamp e nonce. Eles nao sao ferramentas MCP, nao ficam disponiveis ao modelo
e nao aceitam um usuario livre. A credencial MCP emitida na troca e entregue ao
provisionamento seguro do agente; ela nunca e enviada na conversa.

## Fluxos normativos

### Vinculo inicial

1. O usuario autenticado solicita um codigo na pagina de integracoes.
2. O backend cria um codigo aleatorio, de uso unico e validade de 10 minutos.
3. O usuario envia `/conectar <codigo>` em conversa direta com o bot.
4. O Gateway envia codigo, bot, chat, remetente, update, timestamp e nonce pelo
   endpoint confiavel.
5. O backend consome o codigo atomicamente e verifica se Telegram e conta podem
   ser vinculados.
6. O backend cria vinculo, credencial escopada e auditoria.
7. O Gateway provisiona agente e sessao exclusivos sem exibir a credencial.
8. O bot informa a conta vinculada de forma nao sensivel e orienta como revogar.

Repetir o mesmo update retorna o resultado anterior. Um Telegram ja vinculado a
outra conta nao e transferido silenciosamente. Usuario sem vinculo pode apenas
receber instrucoes de conexao e privacidade.

### Consulta conversacional

1. O agente identifica a intencao e escolhe uma ferramenta de leitura.
2. A ferramenta consulta novamente o estado atual do usuario derivado.
3. O agente preserva ordem, valores, estados e justificativas retornados.
4. A resposta diferencia fato, aviso e ausencia de dados.
5. Nenhuma consulta inicia uma escrita por consequencia implicita.

Se nao houver concurso, plano ou evidencia suficiente, a resposta explica a
ausencia. Ela pode oferecer uma preparacao permitida, mas nao inventa uma agenda
nem usa a memoria como substituta.

### Preparacao e confirmacao comum

1. O agente coleta somente os campos necessarios e chama uma ferramenta
   `preparar_*`.
2. O backend valida o caso de uso e persiste uma operacao assinada, sem alterar
   os recursos de negocio.
3. O bot apresenta resumo, efeitos, avisos, validade e frase de confirmacao.
4. O usuario confirma por botao, texto `CONFIRMAR <codigo>` ou voz.
5. Para voz, a transcricao normalizada apenas quanto a caixa e espacos externos
   precisa ser exatamente a frase esperada; texto adicional invalida o desafio.
6. O Gateway recebe a confirmacao diretamente do update ou callback, valida sua
   origem e a envia ao endpoint confiavel.
7. O backend bloqueia a operacao, valida estado, vinculo, conversa, expiracao,
   nonce, assinatura e versoes consultadas.
8. O caso de uso recalcula as pre-condicoes e aplica tudo em uma transacao.
9. O backend marca a operacao `APLICADA` e devolve recibo persistido.
10. Retry do mesmo update devolve o mesmo recibo, sem nova aplicacao.

Uma mensagem produzida pelo proprio agente nunca conta como confirmacao. Um
"sim", joinha ou audio sem a frase completa tambem nao confirma.

A operacao e sua previa valem 30 minutos. O desafio comum vale 10 minutos e
nunca ultrapassa a expiracao da operacao. Todos os prazos sao calculados pelo
relogio do backend; horario enviado pelo cliente nao prolonga a validade.

### Confirmacao reforcada

1. A primeira confirmacao valida que o usuario revisou o impacto e muda a
   operacao para o estagio do segundo desafio.
2. O backend gera novo nonce e novo codigo, com validade de 5 minutos e sem
   ultrapassar a expiracao da operacao.
3. O segundo desafio deve vir do mesmo Telegram, bot, chat e sessao.
4. A aplicacao recalcula a assinatura imediatamente antes de aplicar.
5. Falha ou expiracao de qualquer estagio exige nova preparacao completa.

### Cancelamento e expiracao

- `/cancelar <codigo>` ou o botao correspondente cancela somente uma operacao
  confirmavel da mesma conversa;
- cancelamento e idempotente e nao desfaz operacao ja aplicada;
- operacao expirada nunca pode voltar a confirmavel;
- uma nova tentativa cria nova operacao e nova assinatura;
- falha recuperavel preserva diagnostico seguro, mas nao autoriza aplicacao
  automatica.

### Previa desatualizada

Quando versao, contexto oficial, evidencias, prioridades, capacidade, blocos ou
outro dado assinado mudar:

1. a aplicacao recusa a operacao com
   `PREVIA_DE_AUTOMACAO_DESATUALIZADA`;
2. nenhum efeito da proposta antiga e persistido;
3. o agente explica qual categoria de dado mudou, sem expor informacao sensivel;
4. uma nova ferramenta de preparacao recalcula a proposta;
5. o usuario recebe novo resumo e precisa confirmar novamente.

O agente nunca substitui a assinatura antiga pela nova e aplica silenciosamente.

## Idempotencia e concorrencia

### Preparacao

A chave de idempotencia e vinculada a usuario, vinculo, ferramenta e versao do
contrato. O backend calcula o hash canonico dos argumentos de negocio:

- mesma chave e mesmo hash retornam a mesma operacao, inclusive apos timeout;
- mesma chave e hash diferente retornam
  `CHAVE_DE_IDEMPOTENCIA_REUTILIZADA`;
- nova chave cria nova preparacao, sem reaproveitar confirmacao anterior.

Campos de transporte, correlacao e ordem JSON nao alteram o hash. Datas, listas
e enums sao canonicalizados pelo contrato antes da assinatura.

### Confirmacao e aplicacao

Update, callback e desafio consumido possuem unicidades persistidas. Sob
concorrencia, apenas uma transacao pode mudar a operacao confirmavel para
aplicada. As demais recebem o recibo original ou conflito de estado, nunca uma
segunda escrita.

A aplicacao bloqueia a operacao e os agregados definidos pelo caso de uso. Ela
recalcula as pre-condicoes dentro da transacao e compara a assinatura canonica.
Mudanca concorrente invalida toda a aplicacao. Operacoes em lote, como cadastro
de concurso, possuem rollback integral.

## Erros e recuperacao

Falhas de autenticacao e transporte usam o status HTTP adequado. Uma chamada
MCP autenticada cujo caso de uso falhe retorna resultado de ferramenta
estruturado com `isError: true`. Erros de protocolo seguem os codigos JSON-RPC
do MCP.

| HTTP | Codigo de negocio | Recuperacao conversacional |
|---|---|---|
| 400 | `ENTRADA_INVALIDA` | pedir apenas os campos apontados e preparar novamente |
| 401 | `CREDENCIAL_DE_INTEGRACAO_INVALIDA` | interromper chamadas e orientar nova vinculacao |
| 403 | `ESCOPO_INSUFICIENTE` | informar que a integracao nao possui a permissao; nao repetir |
| 404 | `RECURSO_NAO_ENCONTRADO` | informar indisponibilidade sem sugerir que pertence a outro usuario |
| 409 | `CHAVE_DE_IDEMPOTENCIA_REUTILIZADA` | gerar nova intencao somente se o usuario repetir a solicitacao |
| 409 | `PREVIA_DE_AUTOMACAO_DESATUALIZADA` | recalcular e exigir nova confirmacao |
| 409 | `OPERACAO_NAO_CONFIRMAVEL` | consultar a operacao e mostrar seu estado atual |
| 409 | `CONFIRMACAO_INVALIDA_OU_EXPIRADA` | preparar novamente; nunca reaproveitar codigo |
| 409 | `VINCULO_DE_TELEGRAM_JA_EXISTENTE` | orientar revogacao na conta atualmente vinculada |
| 422 | `CODIGO_DE_VINCULO_INVALIDO_OU_EXPIRADO` | gerar novo codigo pela aplicacao web |
| 422 | codigo especifico do caso de uso | explicar a regra e coletar a correcao necessaria |
| 429 | `LIMITE_DE_REQUISICOES_ATINGIDO` | respeitar o prazo de nova tentativa e nao criar varias operacoes |
| 503 | `DEPENDENCIA_INDISPONIVEL` | informar indisponibilidade temporaria e manter a aplicacao web utilizavel |

Erros recuperaveis nunca autorizam repetir uma operacao diferente. Depois de
erro de rede, o agente consulta a operacao pela mesma correlacao ou chave antes
de tentar novamente.

## Conversas de referencia

Os exemplos sao ilustrativos e demonstram comportamento esperado, nao uma
validacao executada.

### Usuario ainda nao vinculado

```text
Usuario: O que tenho para estudar hoje?

Bot: Este Telegram ainda nao esta vinculado a uma conta da Trilha.
Abra Integracoes > Telegram na aplicacao, gere um codigo e envie:
/conectar CODIGO
```

### Consulta de hoje

```text
Usuario: O que devo estudar hoje?

Bot: Para hoje, a Trilha retornou 2h40 disponiveis e 2h20 planejadas:
1. Direito Administrativo - Questoes, 50 min
2. Portugues - Teoria, 45 min
3. Revisao de Banco de Dados, 20 min

O primeiro bloco aparece antes porque tem a maior prioridade atual no plano.
Quer que eu explique esse bloco?
```

O bot preserva a ordem recebida. Se a consulta nao trouxer uma justificativa,
ele nao cria uma; oferece `explicar_bloco_de_estudo`.

### Ausencia de plano

```text
Usuario: O que estudo hoje?

Bot: Nao existe um plano ativo para esta semana. Por isso nao ha uma agenda de
hoje na Trilha. Posso ajudar a preparar a geracao da semana, sem aplicar nada
antes da sua confirmacao.
```

### Preparacao de conclusao

```text
Usuario: Terminei o bloco. Foram 48 minutos, 20 questoes e 14 acertos.

Bot: Preparei esta operacao; nada foi alterado ainda.
- concluir: Direito Administrativo - Atos administrativos
- tempo executado: 48 min
- questoes: 20
- acertos: 14
- erros derivados: 6

Para registrar, use o botao Confirmar ou envie CONFIRMAR 7K9P2Q ate o horario
indicado. Para desistir, use Cancelar.
```

### Confirmacao por voz invalida

```text
Transcricao: Sim, pode confirmar 7K9P2Q para mim.

Bot: A confirmacao nao foi aceita. Por seguranca, a transcricao precisa ser
exatamente: CONFIRMAR 7K9P2Q
Nada foi alterado.
```

### Mudanca concorrente

```text
Bot: A proposta ficou desatualizada porque o estado do planejamento mudou.
Nada da proposta antiga foi aplicado. Vou recalcular e mostrar uma nova previa;
ela precisara de outra confirmacao.
```

### Cadastro de concurso

```text
Bot: Previa do cadastro
- concurso: TCE Exemplo
- edital: 01/2026
- cargo selecionado: Analista
- materias reutilizadas: 8
- materias novas propostas: 4
- itens do edital: 186
- mapeamentos ambiguos: 41
- itens pendentes: 22

Nada foi alterado. Os itens ambiguos continuarao pendentes. Revise os detalhes
antes de confirmar a criacao do concurso em rascunho.
```

## Regras de redacao do agente

- iniciar pelo resultado factual mais importante;
- distinguir explicitamente consulta de previa e previa de aplicacao;
- dizer "nada foi alterado" sempre que mostrar uma preparacao;
- informar data, fuso ou periodo quando isso mudar o sentido da resposta;
- preservar unidades, totais, ordem e justificativas retornadas;
- nao transformar ausencia de evidencia em desempenho ruim;
- nao criar diagnostico, prioridade ou recomendacao fora dos casos de uso;
- nao afirmar sucesso sem recibo `APLICADA`;
- nao expor identificadores tecnicos quando um rotulo seguro for suficiente;
- nao ocultar aviso bloqueador, ambiguidade ou minutos nao alocados;
- depois de falha, explicar a acao segura disponivel sem repetir
  silenciosamente uma escrita.

## Evolucao do contrato

O campo `versaoDoContrato` inicia em `1`. Adicoes compativeis exigem que
clientes tolerem novos valores somente nos pontos de extensao explicitamente
declarados; como os schemas de entrada sao fechados, qualquer novo argumento
precisa de nova versao publicada. Remocao, renomeacao, mudanca de enum ou de
semantica exige uma versao nova da ferramenta e periodo de compatibilidade.

O backend anuncia apenas ferramentas habilitadas pela feature flag e pelos
escopos da credencial. O agente nao deve inferir que uma ferramenta futura
existe antes de ela aparecer na descoberta MCP.
