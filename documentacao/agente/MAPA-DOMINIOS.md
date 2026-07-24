# Mapa de domínios

## Relações centrais

```text
Usuário
├── concursos
│   ├── editais
│   ├── cargos
│   ├── provas
│   ├── grupos
│   ├── matérias da prova
│   └── itens oficiais
├── catálogo pessoal
│   ├── matérias
│   └── tópicos
├── materiais
├── registros de estudo
├── evidências
├── planos semanais
│   ├── disponibilidades
│   ├── prioridades
│   ├── blocos
│   └── execuções
├── revisões
└── automação
    ├── vínculo Telegram
    ├── credencial MCP
    ├── operação assistida
    └── auditoria
```

## Módulos

### `autenticacao`

Responsabilidade:

- usuário;
- cadastro;
- login;
- sessão;
- autorização web.

Não deve conter regra de concurso, estudo ou planejamento.

### `concursos`

Responsabilidade:

- concurso;
- edital;
- cargo;
- prova;
- grupo de conteúdo;
- matéria da prova;
- seleção e ativação.

### `conteudoprogramatico`

Responsabilidade:

- item oficial do edital;
- árvore oficial;
- mapeamento para tópico pessoal;
- preservação da redação oficial.

### `conteudos`

Responsabilidade:

- matéria pessoal;
- tópico pessoal;
- árvore reutilizável;
- arquivamento.

### `estudos`

Responsabilidade:

- material de estudo;
- cobertura por tópico;
- registro de estudo;
- correção e cancelamento com histórico.

### `evidencias`

Responsabilidade:

- questões;
- acertos;
- recordação;
- dificuldade;
- padrões de erro;
- validações relacionadas ao tipo de estudo.

### `planejamento`

Responsabilidade:

- plano semanal;
- disponibilidade;
- prioridade manual;
- blocos;
- execução;
- geração determinística;
- regeneração segura;
- replanejamento.

### `priorizacao`

Responsabilidade:

- ranking consultivo de tópicos oficiais;
- justificativas e faixas;
- contexto usado pela geração.

### `revisoes`

Responsabilidade:

- agenda de revisões;
- revisões vencidas, devidas e futuras;
- integração com blocos já abertos.

### `dashboard`

Responsabilidade:

- consultas derivadas;
- cobertura;
- atividade recente;
- lacunas;
- progresso do concurso ativo.

### `automacao`

Responsabilidade:

- vínculo e revogação do Telegram;
- credenciais MCP;
- ferramentas;
- operações preparadas;
- confirmação;
- auditoria;
- Gateway confiável;
- métricas e saúde.

### `compartilhado`

Responsabilidade:

- tipos realmente compartilhados;
- erros e respostas comuns;
- infraestrutura transversal mínima.

Não use como depósito de classes sem domínio claro.

## Invariantes transversais

- todo agregado pessoal pertence a um usuário;
- no máximo um concurso ativo por usuário;
- matéria pessoal é única por usuário, ignorando caixa e espaços externos;
- tópico não forma ciclo;
- mapeamento oficial aponta para tópico da mesma matéria;
- material e tópico pertencem ao mesmo usuário;
- progresso é derivado, não entidade persistida;
- regeneração não destrói bloco manual ou ajustado;
- automação nunca troca o usuário por argumento do modelo.
