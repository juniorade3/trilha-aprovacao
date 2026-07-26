# ADR-001 — Importação segura e confirmada de editais

- Estado: aceita
- Data: 2026-07-26
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

O parser inicial é determinístico. Linhas do documento são tratadas apenas como
dados. Frases semelhantes a prompt injection são sinalizadas para revisão e
nunca executadas. Não há download de URL nem chamada a shell, filesystem
genérico, modelo ou ferramenta durante a extração.

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
- OCR e antivírus são portas opcionais. A ausência é informada; nunca é
  apresentada como varredura ou OCR concluído.
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
- Adicionar Tika/POI/serviço de OCR no primeiro corte: aumenta dependências e
  superfície sem infraestrutura operacional disponível.
