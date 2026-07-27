# Segurança da importação de edital

## Limites e validação

- tamanho máximo padrão: 10 MiB;
- páginas de PDF: no máximo 500;
- texto extraído: no máximo 2.000.000 de caracteres;
- tipos aceitos: `text/plain` UTF-8 e `application/pdf` reconhecidos por bytes;
- nome de arquivo sanitizado e nunca usado como caminho;
- hash SHA-256 calculado pelo backend;
- nenhuma URL informada pelo documento é buscada;
- nenhum anexo é executado;
- o documento nunca é interpretado como instrução de agente;
- toda consulta e alteração recebe o usuário efetivo da sessão ou credencial.

O cabeçalho `Content-Type` e a extensão são somente indícios. PDF deve começar
com a assinatura esperada e texto deve ser UTF-8 válido, sem binário disfarçado.
O conteúdo integral não aparece em logs, auditoria MCP, operação assistida ou
resposta da API.

## Armazenamento e retenção

O bruto e o texto extraído ficam temporariamente no PostgreSQL privado. O prazo
padrão é 30 dias e pode ser reduzido por configuração. Uma rotina remove os dois
conteúdos quando o prazo vence, preservando metadados, hashes e trilha de
auditoria. Downloads públicos não são expostos.

Trechos de proveniência são limitados a 1.000 caracteres. Eles devem conter
apenas o mínimo necessário para revisão.

## PDF digitalizado, interpretação externa, OCR e antivírus

PDF sem camada textual é classificado como digitalizado. Sem OCR local, o
usuário pode recorrer ao editor manual ou consentir explicitamente com a
interpretação externa de um cargo.

Na implantação privada, o provedor preferencial é o Codex CLI `0.145.0`
autenticado com a sessão ChatGPT do operador. TXT e PDF com camada textual são
delimitados no prompt e enviados pelo `stdin`. PDF digitalizado é convertido em
páginas PNG temporárias; por padrão, no máximo 20 páginas a 144 DPI são
anexadas, com limites configuráveis de 1 a 100 páginas e 72 a 200 DPI. Se o
documento ultrapassar o limite visual, a chamada é recusada sem alterar o
staging e o editor manual continua disponível.

O processo é iniciado diretamente por argumentos, sem `/bin/sh`, `bash` ou
concatenação de comando. Ele recebe:

- `--ephemeral`, `--ignore-user-config` e `--ignore-rules`;
- `--sandbox read-only` em diretório temporário novo e fora do repositório;
- `--disable shell_tool`, `unified_exec`, `apps`, `multi_agent`, `hooks`,
  `browser_use`, `browser_use_external`, `browser_use_full_cdp_access`,
  `remote_plugin`, `image_generation` e `goals`;
- `web_search = "disabled"` e uma tabela MCP vazia por override;
- modelo e esforço fixados pela configuração do backend;
- `--output-schema` e destino de resposta dentro do mesmo temporário.

O prompt entra somente pelo `stdin`; nenhum trecho do documento vira argumento
de processo. `stderr` é descartado e o fluxo JSONL de `stdout` é limitado e
validado de forma fechada: qualquer evento de ferramenta, falha ou tipo
desconhecido invalida a chamada. O backend lê somente a resposta final, limita
seu tamanho, valida o JSON estrito e remove o diretório temporário. Timeout, uma
chamada concorrente por instância e ausência de retry automático limitam
disponibilidade e custo.

A Responses API permanece como alternativa. Ela envia PDF em Base64 com detalhe
visual alto ou TXT como texto, não usa Files API persistente, ferramentas,
shell, filesystem, URLs ou MCP e usa `store: false`, schema JSON estrito,
timeout finito e a mesma política de concorrência.

O modelo recebe somente o documento retido e a descrição do cargo alvo. A
resposta não é aplicada diretamente: torna-se uma nova versão imutável do
staging. Página e trecho são verificados contra o texto extraído localmente.
Evidência falsa ou não verificável vira dado inferido e exige revisão humana.
Recusa, JSON inválido, limite, timeout, indisponibilidade ou conflito de versão
não alteram a extração atual.

Metadados de confiança e proveniência devolvidos pelo navegador nunca são
aceitos como autoridade. Um valor inalterado conserva sua fonte original; um
valor efetivamente alterado ou confirmado de forma explícita recebe a fonte
`Correção do usuário`. A confirmação usa tipo, chave e campo estáveis e só é
aceita quando corresponde a uma pendência assistida da versão corrente. Assim,
salvar outro campo não confirma sugestões inferidas por acidente. Avisos
operacionais e pendências assistidas permanecem no versionamento até serem
resolvidos de forma seletiva.

O adaptador fica desabilitado por padrão e também quando o provedor escolhido
não está autenticado. No modo Codex CLI, `CODEX_HOME` aponta para um diretório
externo de credenciais montado somente em `/home/aplicacao/.codex` no backend.
Esse bind é segredo operacional: deve pertencer ao UID `1000`, permanecer
`0700`, nunca ser copiado para a imagem, repositório, backup sem criptografia,
frontend, log, relatório, operação assistida ou OpenClaw. O CLI lê a sessão para
autenticar e renová-la, mas seu conteúdo não entra no prompt nem na linha de
comando. No modo Responses API, a chave segue no arquivo externo de segredos e
as mesmas proibições se aplicam. Métricas limitam-se a sucesso/falha, duração e
tokens.

Autenticação ChatGPT só é aceita em host privado e confiável. Comprometimento do
container, do UID do backend ou do host pode expor a sessão e consumir limites
da conta ou do workspace. A operação deve manter acesso administrativo
restrito, acompanhar falhas e consumo, revogar com `codex logout` diante de
suspeita e reconstruir a imagem para atualizar o CLI pinado. A sessão pode
expirar ou ser revogada e os limites do plano podem variar; esses eventos
desabilitam somente a assistência e direcionam ao editor manual.

Não há mecanismo antivírus instalado no repositório. A arquitetura admite um
verificador opcional; sua ausência deve aparecer como aviso. O sistema não pode
afirmar que um arquivo foi analisado quando isso não ocorreu.

DOCX não é aceito nesta versão. Consequentemente, a proteção contra ZIP bomb
será obrigatória antes de habilitar esse formato.

## Prompt injection

Frases que solicitam ignorar regras, executar comandos, revelar segredos ou
chamar ferramentas são tratadas como dados não confiáveis e podem gerar aviso.
No caminho externo, o prompt delimita o documento como conteúdo, não como
instrução. As ferramentas do Codex CLI são desabilitadas por camadas, o sandbox
é somente leitura e o schema limita a saída à árvore do cargo. No provedor
Responses API, nenhuma ferramenta é disponibilizada.

## Confirmação e atomicidade

A prévia contém somente identificadores e hashes. Alterar a extração invalida a
operação preparada. A confirmação reforçada usa duas etapas; a segunda dura no
máximo cinco minutos e exige o mesmo contexto confiável da primeira.

A aplicação é atômica. Uma exceção reverte toda a estrutura visível. Relatório e
proveniência pertencem à mesma transação. Repetições consultam importação,
versão, hash, cargo e decisões para não duplicar o lote.

## Canal OpenClaw

O agente não recebe filesystem, mídia ou URL arbitrária. O usuário faz upload
na aplicação autenticada e fornece ao assistente o identificador da importação.
O MCP deriva o usuário da credencial ativa e nunca aceita
`identificadorDoUsuario` como argumento. O bruto não entra no OpenClaw nem no
MCP. OpenClaw permanece restrito à revisão das pendências e à confirmação já
existente.
