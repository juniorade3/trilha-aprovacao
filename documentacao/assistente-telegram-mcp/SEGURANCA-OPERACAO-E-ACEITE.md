# Segurança, operação e aceite do Assistente da Trilha

## Estado deste documento

Este documento define controles, portas de qualidade e procedimentos planejados para o Assistente da Trilha via Telegram, OpenClaw e MCP. Ele não registra testes como aprovados, serviços como implantados nem resultados como executados. Cada evidência deverá ser preenchida no PR da sprint correspondente somente depois da execução real.

O princípio central é que a aplicação web continua sendo a fonte de verdade. O modelo interpreta a conversa e prepara ações; os casos de uso da Trilha validam as regras; um componente confiável confirma a identidade; e somente o backend persiste alterações.

## Limites de confiança

```text
Usuário no Telegram
        |
        | rede e identidade externas
        v
Telegram Bot API
        |
        v
Gateway OpenClaw privado
  |              |
  |              +--> leitor isolado de documentos e links
  v
Agente e sessão exclusivos do vínculo
        |
        | credencial MCP individual e escopada
        v
Adaptador MCP da Trilha
        |
        | identidade derivada da credencial
        v
Casos de uso do monólito
        |
        v
PostgreSQL
```

São considerados não confiáveis:

- texto, voz, imagens, documentos, links e nomes enviados pelo Telegram;
- respostas e decisões do modelo;
- conteúdo de editais e páginas externas, inclusive instruções embutidas;
- identificadores informados no corpo de uma mensagem;
- callbacks repetidos, atrasados ou fora de ordem;
- metadados controláveis pelo cliente, como `username` e nome de exibição.

São confiáveis apenas após autenticação e validação:

- o identificador numérico do remetente fornecido pela Telegram Bot API;
- o vínculo ativo mantido pela Trilha;
- a credencial MCP emitida para aquele vínculo;
- assinaturas, versões, nonces e estados persistidos pelo backend;
- o adaptador de confirmação, que fica fora das ferramentas disponíveis ao modelo.

## Modelo de ameaças e controles

| Ameaça | Impacto | Controle obrigatório |
|---|---|---|
| Telegram não vinculado ou tentativa de personificação | Acesso a dados de outro usuário | Vínculo iniciado pela aplicação autenticada, código temporário de uso único e uso exclusivo do identificador numérico do Telegram |
| Vazamento entre agentes ou sessões | Exposição cruzada de dados e contexto | Um agente, workspace, sessão e credencial MCP por vínculo; testes A/B; nenhuma sessão global compartilhada |
| Roubo de token do bot ou credencial MCP | Consultas ou operações indevidas | Segredos fora do repositório, hash de credenciais no backend, menor privilégio, expiração, rotação e revogação imediata |
| Modelo inventar ou alterar um usuário | Escalada horizontal | Nenhuma ferramenta aceita `identificadorDoUsuario`; o backend deriva o usuário da credencial |
| Modelo acionar escrita sem consentimento | Mutação indevida | Ferramentas visíveis ao modelo apenas consultam ou preparam; confirmação e aplicação passam por adaptador confiável |
| Replay de mensagem, callback ou timeout após commit | Operação duplicada | Deduplicação do update, chave de idempotência persistida, nonce de uso único e retorno do resultado original |
| Confirmação aplicada a outra proposta | Mutação diferente da revisada | Confirmação vinculada ao hash canônico, usuário, Telegram, bot, chat, sessão, operação, versões e expiração |
| Alteração concorrente entre prévia e aplicação | Sobrescrita ou aplicação obsoleta | Recalcular sob bloqueio, comparar assinatura e versões e retornar `409 PREVIA_DE_AUTOMACAO_DESATUALIZADA` |
| Prompt injection em PDF, imagem, texto ou página | Abuso de ferramenta e exfiltração | Leitor isolado sem escrita, DTO estrito, proveniência, conteúdo sempre tratado como dado e validação no backend |
| SSRF por URL ou redirecionamento | Acesso à rede interna ou metadata | Buscador isolado, resolução e revalidação de IP, apenas HTTP(S) público e bloqueio de redes especiais |
| Arquivo malformado, excessivo ou bomba de descompressão | Indisponibilidade e custo | Limites de bytes, páginas, tempo, profundidade, memória e tipo; processamento isolado e remoção posterior |
| Alucinação ou dado ausente | Cadastro oficial incorreto | Campos sem evidência ficam `PENDENTE`; prévia mostra fonte e incerteza; não há confirmação automática por confiança |
| Abuso de custo ou negação de serviço | Indisponibilidade e gasto excessivo | Rate limit por vínculo, limites de anexos e tokens, filas limitadas, timeout, circuit breaker e orçamento observado |
| Comprometimento do OpenClaw ou plugin | Acesso ao host e segredos | Versão fixada, plugins mínimos, execução sem privilégio, filesystem restrito e ausência de Docker socket, banco e repositório |
| Dados sensíveis em logs ou prompts | Vazamento persistente | Redação de segredos, sem chain-of-thought, áudio ou documento integral; auditoria guarda metadados e hashes necessários |
| Falha parcial em cadastro em lote | Estrutura inconsistente | Transação única e rollback integral; recibo somente após commit |

Exclusão física, alteração de autenticação, administração de permissões, SQL, shell, filesystem genérico, navegador pessoal, Docker e chamadas HTTP arbitrárias não serão expostos à IA.

## Identidade, vínculo e isolamento de sessões

1. O usuário autenticado solicita na aplicação um código aleatório, de validade curta e uso único.
2. A Trilha armazena somente o hash do código, sua expiração e o usuário que o solicitou.
3. O usuário envia `/conectar <codigo>` em conversa direta com o bot aprovado.
4. O Gateway transmite ao endpoint confiável o código e os identificadores numéricos do bot, chat e remetente.
5. O backend consome o código atomicamente e cria o vínculo. Código expirado, consumido ou divergente não cria vínculo.
6. Uma credencial MCP distinta, escopada e revogável é emitida para o agente daquele vínculo. Seu valor integral não é persistido em texto puro pelo backend.
7. Cada mensagem posterior é roteada para o agente, sessão e credencial correspondentes. O modelo não escolhe o usuário.

Regras invariáveis:

- um vínculo ativo identifica exatamente um usuário, um canal, um bot e um identificador externo;
- `username`, nome e texto da mensagem não participam da autorização;
- usuário não vinculado recebe apenas orientação para conexão e não acessa dados;
- a conversa suportada inicialmente é direta; grupos e encaminhamento entre chats ficam desabilitados;
- revogação bloqueia imediatamente novas consultas, preparações e confirmações;
- rotação invalida a credencial anterior antes de habilitar a nova;
- desconexão encerra a sessão conversacional e invalida operações pendentes daquele vínculo;
- os escopos mínimos são separados em leitura, preparação e, apenas para o adaptador confiável, aplicação;
- cache, memória e telemetria são particionados pelo identificador interno do vínculo e nunca reutilizados entre usuários.

Parâmetros iniciais planejados: código de vínculo válido por 10 minutos; operação e prévia válidas por 30 minutos; confirmação comum válida por 10 minutos sem ultrapassar a expiração da operação; e segundo desafio reforçado válido por 5 minutos. Todos são de uso único. Esses valores poderão ser reduzidos por configuração do ambiente, mas não ampliados em produção sem nova revisão de segurança e testes de expiração.

## Operações e confirmação

### Níveis de autonomia

| Nível | Exemplos | Regra |
|---|---|---|
| Consulta | agenda, revisões, prioridades, progresso | Execução automática com credencial de leitura |
| Preparação | prévia de estudo, geração, replanejamento ou cadastro | Não escreve fatos de negócio; persiste somente a operação assistida e sua auditoria |
| Escrita comum | concluir bloco, registrar estudo, alterar disponibilidade | Exige confirmação comum válida |
| Alto impacto | ativar, arquivar, cancelar ou substituir | Exige confirmação reforçada e resumo integral do impacto |
| Proibida | exclusão física, segurança, permissões e infraestrutura | Não existe no catálogo MCP |

Estados previstos: `PREPARADA`, `AGUARDANDO_CONFIRMACAO`, `CONFIRMADA`, `APLICADA`, `CANCELADA`, `EXPIRADA` e `FALHOU`. Transições inválidas são rejeitadas. Uma operação terminal não volta a um estado anterior.

Toda prévia deve exibir a ação, seus efeitos, avisos, registros afetados e a frase explícita de que nada foi alterado. A aplicação compara o hash canônico e as versões novamente antes do commit. Se houver divergência, uma nova prévia é gerada e uma nova confirmação é obrigatória.

### Confirmação comum

A confirmação aceita um dos formatos abaixo:

- botão inline associado ao identificador opaco da operação;
- texto exato `CONFIRMAR <codigo>`;
- voz cuja transcrição normalizada seja exatamente `CONFIRMAR <codigo>`.

A mensagem não é considerada confirmação quando contém explicações adicionais, código incompleto, operação expirada ou resposta ambígua como “sim”, “pode fazer” ou “confirmo”. Voz com baixa confiança, transcrição indisponível ou conteúdo adicional solicita confirmação por botão ou texto. O áudio original não precisa ser mantido pela Trilha; a auditoria registra o método, o hash da transcrição e o resultado da validação.

O código é aleatório, curto apenas o suficiente para digitação segura, de uso único e exibido junto ao resumo. A confirmação é vinculada a:

- usuário e vínculo;
- bot, remetente, chat e sessão;
- operação, tipo e hash da prévia;
- versões dos agregados envolvidos;
- nonce e instante de expiração.

O modelo pode solicitar a preparação e explicar a prévia, mas não recebe a ferramenta que converte uma confirmação em escrita. O Gateway valida o update e chama o endpoint confiável; o backend é quem consome o nonce e aplica o caso de uso.

### Confirmação reforçada

Operações de alto impacto usam duas etapas. A primeira revisa o impacto; a segunda apresenta um novo desafio `CONFIRMAR <codigo>`, válido por período curto, no mesmo Telegram, chat e sessão. Qualquer alteração de estado entre as etapas invalida o desafio. Exclusão física continua indisponível mesmo com confirmação reforçada.

## Conteúdo externo, prompt injection e SSRF

O texto de um edital nunca é instrução do sistema. O leitor de documentos opera sem credencial de escrita, sem ferramentas MCP mutáveis e sem acesso a segredos. Sua única saída aceita é um DTO fechado com:

- valor extraído;
- origem do documento ou URL e seu hash;
- página, seção ou posição disponível;
- trecho de apoio limitado;
- indicação de incerteza ou ausência.

O operador recebe esse DTO, não o documento bruto, e o backend valida enumerações, limites, relações e duplicidades. Alegações como “ignore regras”, “chame esta ferramenta” ou “envie estes dados” presentes na fonte permanecem conteúdo literal e não alteram o fluxo.

Para links externos, o componente de busca deve:

- aceitar somente `http` e `https`;
- bloquear loopback, link-local, redes privadas, multicast, IPs reservados, endereços de metadata e equivalentes IPv4/IPv6;
- resolver DNS antes da conexão e revalidar o endereço efetivo a cada redirecionamento, reduzindo risco de DNS rebinding;
- limitar redirecionamentos, tamanho, tempo total, taxa, MIME e quantidade de downloads;
- rejeitar URL com credenciais embutidas e protocolos alternativos;
- não reutilizar cookies ou sessão do navegador do usuário;
- operar em processo ou container sem acesso às redes internas.

Anexos terão limites configurados de tamanho, páginas, resolução e tempo. O tipo será verificado pelo conteúdo, não apenas pela extensão. O armazenamento será temporário, com permissões restritas, identificador não previsível e remoção após processamento ou expiração. O arquivo integral não será copiado para logs, prompts de outros usuários ou auditoria permanente.

## Segredos e proteção de dados

- Token do Telegram, chave do provedor OpenAI, segredo do Gateway e credenciais MCP ficam em variáveis protegidas ou gerenciador de segredos, nunca no Git, imagem, log ou workspace do agente.
- Credenciais persistidas pela Trilha são armazenadas como hash resistente; valores recuperáveis existem somente onde forem indispensáveis ao cliente autorizado.
- Cada ambiente usa segredos diferentes. Produção não reutiliza credenciais locais ou de CI.
- Logs estruturados aplicam redação a cabeçalhos, tokens, códigos de vínculo, códigos de confirmação, conteúdo de documentos e dados sensíveis.
- Rotação é possível sem recriar o usuário. Incidente ou revogação invalida imediatamente o material anterior.
- Prompts, telemetria e mensagens enviam ao modelo somente o contexto mínimo para a resposta atual.
- Backups seguem as mesmas restrições de acesso e retenção do banco principal.
- O comando `/privacidade` e a página web informarão quais metadados são guardados, com que finalidade e como revogar a integração.

## Implantação privada do OpenClaw

O ambiente planejado usa um Gateway compartilhado somente para usuários previamente vinculados, com um agente isolado por vínculo.

- Executar OpenClaw em container ou usuário de sistema dedicado, sem privilégios e com versão e plugins fixados.
- Preferir imagem por digest, filesystem raiz somente leitura e diretórios graváveis mínimos e separados por agente.
- Não montar repositório, banco, diretórios pessoais, Docker socket ou segredos gerais do host.
- Não expor a porta do Gateway à internet. Usar long polling do Telegram e rede privada para alcançar o MCP.
- Restringir saída de rede à Telegram Bot API, provedor OpenAI e endpoints internos estritamente necessários. O buscador de links fica em isolamento próprio.
- Desabilitar `exec`, shell, nodes, filesystem, navegador pessoal, plugins administrativos e ferramentas não listadas.
- Aplicar limites de CPU, memória, processos, tamanho de fila, chamadas de modelo, anexos e tempo de execução.
- Manter health checks distintos para Gateway, integração Telegram, MCP e dependências; health check não executa escrita de negócio.
- Iniciar com a feature flag desligada. Habilitar leitura antes de preparação e preparação antes de escrita.
- Executar `openclaw security audit --deep` antes da habilitação e após mudanças de versão ou plugins.
- Manter rollback para a imagem e configuração anteriores. Desabilitar o bot não deve interromper a aplicação web.

## Auditoria, observabilidade e métricas

Os eventos de auditoria são append-only e registram, quando aplicável:

- identificador de correlação, usuário, vínculo e ator `IA_TELEGRAM`;
- ferramenta e versão do contrato;
- operação e transição de estado;
- hashes da entrada, prévia, confirmação e saída;
- origem e hash de fontes externas, sem conteúdo integral;
- método e instante da confirmação;
- versões consultadas e anteriores/posteriores;
- resultado, categoria do erro, latência e identificador idempotente.

Não registrar chain-of-thought, credenciais, códigos íntegros, cabeçalhos de autorização, áudio, documento integral ou texto pessoal além do mínimo explicitamente necessário. Acesso à auditoria é escopado ao próprio usuário e à operação técnica autorizada.

Métricas operacionais planejadas:

- volume, sucesso, erro e latência por ferramenta e classe de operação;
- fila, timeout, rate limit e circuit breaker;
- custo e tokens por classe de conversa, sem conteúdo da mensagem como rótulo;
- vínculos, revogações e falhas de autenticação;
- prévias desatualizadas, confirmações expiradas e retries idempotentes;
- campos aceitos sem correção, ambiguidades e duplicidades evitadas na importação;
- falhas de extração e fontes bloqueadas por política.

Metas de aceite de segurança:

- escritas sem confirmação válida: zero;
- operações duplicadas: zero;
- vazamentos entre usuários A e B: zero;
- aplicação parcial de lote: zero;
- campos inventados sem marcação de incerteza: zero.

Essas metas são critérios futuros. Só serão declaradas atingidas com evidências executadas no ambiente de aceite.

Alertas mínimos cobrem aumento de falhas de autenticação, repetição anormal de confirmações, erro do MCP, circuit breaker aberto, fila saturada, orçamento excedido e falha contínua do Gateway. Alertas não incluem conteúdo das conversas.

## Plano de testes e aceite

### Identidade e autorização

- código válido, expirado, consumido, incorreto e usado concorrentemente;
- Telegram duplicado, bot/chat divergente, usuário desconectado e vínculo revogado;
- rotação com rejeição imediata da credencial anterior;
- usuários A e B tentando consultar, preparar, confirmar e recuperar operações um do outro;
- ferramenta tentando fornecer `identificadorDoUsuario` ou campos adicionais não previstos;
- escopo de leitura tentando preparar ou aplicar uma operação.

### Confirmação, concorrência e idempotência

- botão, texto exato e voz com transcrição exata;
- respostas ambíguas, voz com conteúdo adicional, baixa confiança e código expirado;
- replay de update e callback, entrega fora de ordem e mesmo nonce em paralelo;
- timeout antes e depois do commit;
- mesma chave idempotente com corpo igual e diferente;
- alteração humana via web entre prévia e confirmação;
- operação cancelada, expirada, já aplicada ou com versão divergente;
- confirmação reforçada em Telegram, chat ou sessão diferente.

### MCP, conversação e isolamento

- schemas fechados e conformidade do transporte MCP;
- todas as ferramentas retornando apenas dados derivados da credencial;
- “Hoje” com e sem concurso, plano, bloco e revisão;
- modelo indisponível, MCP indisponível, resposta lenta e circuit breaker aberto;
- memória de uma conversa tentando referenciar dados de outra;
- catálogo sem `exec`, shell, filesystem, navegador, SQL ou HTTP genérico;
- aplicação web funcionando quando OpenClaw e Telegram estiverem desligados.

### Conteúdo hostil e rede

- PDF, imagem e página com prompt injection explícita e indireta;
- arquivo malformado, MIME divergente, excesso de páginas, tamanho e tempo;
- URL para localhost, redes privadas, link-local, metadata, IPv6 especial e protocolo proibido;
- cadeia de redirects, DNS rebinding, URL com credenciais e resposta grande;
- extração com campos ausentes ou conflitantes permanecendo pendente;
- documento temporário removido e ausente dos logs após processamento.

### Transações e regressão

- cadastro integral com rollback quando o último filho falha;
- conclusão, interrupção, evidência e correção preservando linhagem;
- geração e replanejamento determinísticos com prévia desatualizada;
- banco PostgreSQL vazio de V1 até a migration da sprint;
- ausência de N+1 nas consultas agregadas;
- OpenAPI e erros 400, 401, 403, 404, 409, 422 e 429;
- `docker compose config`, `make verificar` e `git diff --check`;
- validação da página web em 390×844, 768×1024 e 1280×800;
- `openclaw security audit --deep` no ambiente candidato.

Cada PR deverá registrar comando, ambiente, data e resultado real. Itens dependentes de Telegram, OpenClaw, voz ou infraestrutura externa permanecerão explicitamente “não executados” até que o ambiente exista.

## Portas de qualidade por sprint

| Sprint | Habilitação máxima | Porta de saída planejada |
|---|---|---|
| 0 — documentação | Nenhuma execução | Arquitetura, contratos, threat model e aceite revisados; nenhuma afirmação fictícia |
| 1 — identidade e operações | Feature flag desligada | Vínculo, revogação, rotação, estados, auditoria, idempotência e isolamento A/B comprovados no PostgreSQL |
| 2 — MCP e OpenClaw | Somente leitura interna | Transporte e schemas conformes, credencial por agente, consulta agregada, catálogo mínimo e sandbox verificados |
| 3 — conversa diária | Leitura para usuários piloto | Respostas baseadas no backend, memória isolada, estados vazios, privacidade e falhas recuperáveis validados |
| 4 — estudos e planejamento | Escrita comum para pilotos | Preparação sem mutação, confirmação, retry, concorrência, linhagem e determinismo comprovados |
| 5 — cadastro de concursos | Cadastro em rascunho | Extração hostil, proveniência, ambiguidades, transação integral e rollback comprovados |
| 6 — consolidação | Ativação gradual | Confirmação reforçada, métricas, alertas, runbook, rollback, auditoria e aceite integral concluídos |

Nenhuma sprint habilita a permissão da seguinte por simples presença de código. A liberação depende da porta anterior, CI verde, documentação factual e feature flag apropriada. Uma falha crítica de isolamento, confirmação, idempotência ou rollback bloqueia a habilitação e exige revogação preventiva dos vínculos afetados.

## Runbook operacional planejado

### Implantar ou atualizar

1. Confirmar backup, migrations pendentes e compatibilidade dos contratos MCP.
2. Fixar versões e digests; revisar a diferença de configuração e plugins.
3. Carregar segredos pelo mecanismo do ambiente e verificar que não aparecem em logs.
4. Executar verificações do repositório, banco vazio, auditoria do OpenClaw e testes de isolamento.
5. Implantar com feature flag desligada e validar health checks.
6. Habilitar primeiro para conta técnica, depois leitura de pilotos e, por último, escritas autorizadas.
7. Observar erros, latência, fila, custo, confirmações e auditoria durante a janela definida.

### Revogar usuário ou Telegram perdido

1. Revogar o vínculo pela aplicação e invalidar credencial e operações pendentes.
2. Encerrar sessão e workspace ativo no Gateway.
3. Confirmar por teste negativo que a credencial anterior foi rejeitada.
4. Registrar correlação e motivo sem armazenar conteúdo da conversa.
5. Um novo vínculo exige novo código gerado pela sessão web autenticada.

### Suspeita de segredo comprometido

1. Desabilitar a feature flag de escrita; se necessário, desabilitar toda a integração.
2. Revogar o segredo ou token afetado e interromper o componente comprometido.
3. Preservar auditoria, hashes e logs técnicos, sem copiar segredos para o incidente.
4. Rotacionar token do bot, chave do provedor, Gateway ou credenciais MCP conforme o alcance.
5. Verificar acessos, operações e confirmações desde o último uso confiável.
6. Restaurar gradualmente somente depois dos testes de isolamento e revogação.

### Operação travada ou confirmação sem resposta

1. Consultar o estado persistido pela correlação e pela chave idempotente.
2. Se `APLICADA`, devolver o recibo existente e não reaplicar.
3. Se expirada ou divergente, gerar nova prévia e exigir nova confirmação.
4. Se `FALHOU`, confirmar rollback e apresentar erro recuperável; nunca avançar estado manualmente no banco.
5. Falhas repetidas abrem o circuit breaker e preservam a aplicação web como alternativa.

### Indisponibilidade do modelo, Telegram ou OpenClaw

1. Manter backend e aplicação web operacionais.
2. Rejeitar novas escritas quando não for possível validar integralmente a confirmação.
3. Preservar operações preparadas até sua expiração; não prolongar automaticamente desafios.
4. Após recuperação, deduplicar updates e consultar o estado antes de responder ou tentar novamente.

### Desativação emergencial e rollback

1. Desligar primeiro a escrita e, se necessário, toda a feature flag.
2. Revogar credenciais de integração comprometidas e parar o Gateway.
3. Não reverter migrations destrutivamente; o código anterior deve tolerar as tabelas adicionais.
4. Validar login, navegação e operações web canônicas.
5. Reativar somente após correção, CI verde, testes da porta afetada e revisão da auditoria.

O runbook definitivo deverá acrescentar os nomes reais de serviços, comandos, dashboards, responsáveis e canais de incidente quando a infraestrutura existir. Até lá, nenhum exemplo deste documento deve ser tratado como evidência de implantação.
