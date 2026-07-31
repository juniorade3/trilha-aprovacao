# ADR-001 — Importação segura e confirmada de editais

- Estado: aceita, revisada para autenticação ChatGPT pelo Codex CLI
- Data: 2026-07-26
- Revisão: 2026-07-26
- Domínios: importação de edital, concursos, conteúdo programático e automação

## Contexto

O cadastro assistido anterior recebia um objeto já extraído e criava parte da
estrutura do concurso. Ele não recebia arquivos, não versionava a extração e
mantinha o conteúdo completo dentro da operação assistida. Também não suportava
seleção segura entre vários cargos, hierarquia completa, proveniência ou
complemento rastreável de concurso existente.

Arquivos de edital são conteúdo não confiável. Uma leitura não pode produzir
escrita de domínio, executar instruções presentes no documento nem conceder ao
modelo acesso a arquivo, URL, usuário ou credencial.

## Decisão

Criar o módulo `importacaoedital`, separado em quatro fases persistentes:

1. recebimento privado, limitado, identificado por usuário e SHA-256;
2. extração determinística e imutável em contrato versionado;
3. seleção, correções e prévia sem alteração do domínio de concursos;
4. aplicação atômica somente após confirmação assistida reforçada.

O arquivo temporário fica em `BYTEA` no PostgreSQL, fora da raiz pública, com
retenção configurável. Essa escolha reduz o risco de inconsistência entre banco
e armazenamento externo no volume inicial. Os metadados, hashes, extrações,
relatório e proveniência permanecem após o descarte do conteúdo bruto.

A primeira versão aceita texto UTF-8 e PDF com camada textual. O extrator usa
Apache PDFBox. PDF sem texto é identificado como digitalizado e fica bloqueado
com o código `OCR_INDISPONIVEL` quando nenhum adaptador de OCR estiver
configurado. DOCX permanece fora do primeiro corte; portanto, não existe
processamento ZIP nesta versão.

O parser inicial é determinístico. Ele reconhece tanto o contrato de linhas
rotuladas quanto cabeçalhos e objetos de avaliação de editais Cebraspe. Rótulos
genéricos fora do contexto esperado não abortam todo o documento; ausências e
associações incompletas seguem para validação e revisão humana. Linhas do
documento são tratadas apenas como dados. Nos objetos de avaliação Cebraspe, a
descrição numerada integral permanece como item literal e também origina um
tópico hierárquico marcado como inferido, com sugestão de mapeamento pendente.
Frases semelhantes a prompt injection são sinalizadas para revisão e nunca
executadas. Não há download de URL nem chamada a shell, filesystem genérico ou
ferramenta durante a extração determinística.

Quando o parser local não for suficiente, o usuário pode acionar
explicitamente uma interpretação externa para um único cargo. O provedor
preferencial da implantação privada é o Codex CLI fixado em `0.145.0`,
autenticado por sessão ChatGPT obtida com login por código. A Responses API
permanece como provedor alternativo para ambientes que usam chave de API.

O Codex CLI é executado diretamente pelo backend, sem shell intermediário. Cada
chamada usa um diretório temporário isolado, sandbox `read-only`, sessão
efêmera, schema JSON estrito e timeout e concorrência finitos. A configuração do
usuário e as regras locais são ignoradas; shell, execução unificada, apps,
multiagente, busca e navegação web, plugins, hooks e MCP são desabilitados por
flags e overrides explícitos. TXT entra somente pelo `stdin`. PDF com camada
textual também entra como texto; PDF digitalizado é rasterizado em páginas PNG,
com quantidade, dimensões e tamanho total limitados, anexadas explicitamente ao
processo. Erros do CLI são descartados; os eventos JSONL são limitados e
validados de forma fechada para rejeitar qualquer uso de ferramenta ou tipo
inesperado, e somente o arquivo final validado pelo schema é aceito.

A sessão ChatGPT fica em diretório externo montado apenas no backend como
`CODEX_HOME`. A credencial é usada internamente pelo CLI, nunca é interpolada no
prompt, na linha de comando, no staging, em logs ou em métricas. O diretório não
é montado no OpenClaw. Esse modo só é aceito em host privado e confiável,
operado por uma única organização; expor a execução em ambiente público ou
multilocatário ampliaria o impacto de roubo de sessão e não faz parte desta
decisão.

Nos dois provedores, a chamada não recebe credencial MCP nem escreve no
domínio. A Responses API não expõe ferramentas e usa `store: false`. O resultado
é apenas uma candidata a nova versão do staging: o backend gera as chaves e
associações internas, verifica as evidências contra o texto local e exige
revisão quando não consegue comprovar página e trecho. Recusa, limite,
indisponibilidade ou resposta inválida preservam integralmente a versão corrente
e mantêm o editor manual disponível.

Correções do editor são sanitizadas no backend. Metadados enviados pelo
navegador são descartados: campos inalterados mantêm a proveniência anterior e
campos alterados recebem `Correção do usuário`. Sugestões assistidas também
podem ser confirmadas individualmente por referência estável de recurso e campo.
Somente uma pendência existente na versão esperada pode ser confirmada; as
demais pendências e os avisos de fonte são carregados para a versão seguinte.
Esse mecanismo permite concluir PDFs digitalizados sem transformar um único
salvamento em aprovação implícita de toda a resposta do modelo.

O modelo padrão é configurável e começa em `gpt-5.6-sol`, com esforço `low`. A
integração permanece desligada quando o provedor escolhido não está
autenticado. A credencial fica fora do repositório e do Compose base. Apenas
resultado operacional, duração e contagem de tokens são registrados como
métricas; documento, prompt e resposta não entram em logs.

O MCP recebe somente identificadores, versão da extração, cargo selecionado,
modo, política e decisões por identificador. O documento e o DTO grande não
entram em `OperacaoAssistida`. A assinatura inclui a versão e o hash confirmados.

Toda importação completa usa confirmação reforçada. A segunda etapa expira em
até cinco minutos e exige o mesmo usuário, vínculo, bot, Telegram, chat e sessão.
A aplicação participa da transação `SERIALIZABLE` da confirmação e bloqueia a
linha de staging. Concurso, edital, cargo, provas, grupos, matérias, tópicos,
itens, sugestões, proveniência e relatório são confirmados ou revertidos juntos.

Matérias e tópicos existentes são apenas sugeridos. Reutilização exige UUID
escolhido explicitamente. O texto literal, a descrição normalizada, a numeração,
a ordem e as relações pai-filho permanecem separados. Um lote aplica somente o
cargo selecionado; outros cargos exigem lotes próprios.

O concurso nasce em `PLANEJADO`. Ativação não faz parte da importação.

## Consequências

- Upload web autenticado é o canal confiável inicial. O OpenClaw prepara a
  operação usando o identificador retornado; não recebe acesso a anexos locais.
- O mesmo arquivo pode gerar novas versões de extração e lotes separados por
  cargo sem misturar conteúdo.
- A retenção do bruto limita exposição, mas o uso de `BYTEA` deve ser revisto se
  o volume justificar object storage privado e transacionalmente reconciliado.
- OCR, antivírus e interpretação externa são portas opcionais. A ausência é
  informada; nunca é apresentada como varredura, OCR ou interpretação concluída.
- PDF digitalizado pode ser interpretado pela integração externa somente após
  consentimento explícito. Sem ela, a correção manual continua sendo o caminho
  garantido para concluir um staging válido.
- A autenticação ChatGPT elimina a necessidade de uma chave de API na
  implantação privada, mas introduz dependência da sessão, dos limites e das
  políticas do workspace ChatGPT. Logout, expiração, revogação ou
  indisponibilidade tornam a IA temporariamente indisponível sem afetar o editor
  manual.
- Atualizações do Codex CLI podem alterar flags, formato de eventos ou
  comportamento do sandbox. A versão permanece fixada e qualquer atualização
  exige revisão desta ADR, reconstrução da imagem e smoke antes da implantação.
- A cor neutra de uma matéria nova é metadado visual do sistema, não dado
  extraído do edital, e é identificada como padrão no relatório.

## Alternativas rejeitadas

- Enviar o PDF ou o texto completo ao MCP: amplia superfície de prompt
  injection, excede limites do proxy e impede retomada confiável.
- Persistir diretamente após extrair: elimina revisão, invalidação por versão e
  confirmação confiável.
- Reutilizar automaticamente por nome normalizado: pode fundir matérias ou
  tópicos semanticamente diferentes.
- Dar filesystem ou mídia ao agente OpenClaw: viola o catálogo mínimo e expõe
  arquivos fora do vínculo efetivo.
- Encaminhar o bruto ao OpenClaw: mistura operação e extração e amplia o
  catálogo confiável; o OpenClaw permanece limitado à revisão e confirmação.
- Adicionar Tika/POI/serviço de OCR no primeiro corte: aumenta dependências e
  superfície sem infraestrutura operacional disponível.
