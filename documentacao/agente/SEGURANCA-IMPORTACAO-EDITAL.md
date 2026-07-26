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

## PDF digitalizado, OCR e antivírus

PDF sem camada textual é classificado como digitalizado. Sem implementação de
OCR, a importação fica bloqueada e nenhuma estrutura é criada. Um futuro OCR
deve implementar a porta dedicada, manter limites de página/tempo e devolver
somente texto e proveniência.

Não há mecanismo antivírus instalado no repositório. A arquitetura admite um
verificador opcional; sua ausência deve aparecer como aviso. O sistema não pode
afirmar que um arquivo foi analisado quando isso não ocorreu.

DOCX não é aceito nesta versão. Consequentemente, a proteção contra ZIP bomb
será obrigatória antes de habilitar esse formato.

## Prompt injection

Frases que solicitam ignorar regras, executar comandos, revelar segredos ou
chamar ferramentas são tratadas como texto do edital e podem gerar aviso. Elas
não alteram o parser, não entram em prompt e não acionam ferramentas.

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
`identificadorDoUsuario` como argumento.
