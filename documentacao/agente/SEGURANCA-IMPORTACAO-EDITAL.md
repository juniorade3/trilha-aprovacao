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
interpretação externa de um cargo. Essa integração envia o PDF em Base64
diretamente à Responses API, com detalhe visual alto, ou envia TXT como texto.
Ela não usa Files API persistente, ferramentas, shell, filesystem, URLs ou MCP.
O pedido usa `store: false`, schema JSON estrito, timeout finito e uma chamada
concorrente por instância, sem repetição automática que possa duplicar custo.

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

O adaptador fica desabilitado por padrão e também quando não existe chave de
API. A chave deve ser injetada pelo arquivo externo de segredos da implantação,
nunca por variável versionada, frontend, log, relatório, operação assistida ou
documentação de exemplo. Métricas limitam-se a sucesso/falha, duração e tokens.

Não há mecanismo antivírus instalado no repositório. A arquitetura admite um
verificador opcional; sua ausência deve aparecer como aviso. O sistema não pode
afirmar que um arquivo foi analisado quando isso não ocorreu.

DOCX não é aceito nesta versão. Consequentemente, a proteção contra ZIP bomb
será obrigatória antes de habilitar esse formato.

## Prompt injection

Frases que solicitam ignorar regras, executar comandos, revelar segredos ou
chamar ferramentas são tratadas como dados não confiáveis e podem gerar aviso.
No caminho externo, o prompt delimita o documento como conteúdo, não como
instrução; de qualquer forma, nenhuma ferramenta é disponibilizada ao modelo e
o schema limita a saída à árvore do cargo.

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
