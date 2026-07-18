
A reconstrução será um projeto novo, sem depender do computador do trabalho. A especificação abaixo fixa Java 21 com Spring Boot 4.1, combinação suportada oficialmente. Para o frontend, usa Vue 3 com Vite 8.1. Para OpenAPI/Swagger com Spring Boot 4, o Codex deverá selecionar uma versão estável da linha **springdoc 3.x**, conforme a matriz oficial, e expor também `/v3/api-docs`. ([Home][1])

Esta é uma implementação grande. O documento exige desenvolvimento por marcos, validações e commits intermediários. A vida administrativa do candidato — pagamento, isenção, recurso, resultado e convocação — continuará fora desta primeira base.

Salve como:

```text
RECONSTRUCAO-COMPLETA-TRILHA-APROVACAO.md
```

ou cole diretamente no Codex.

````markdown
# Reconstrução completa do Trilha da Aprovação

## 1. Missão

Você atuará como arquiteto de software, desenvolvedor backend sênior, desenvolvedor frontend sênior, analista de requisitos e revisor técnico.

Crie do zero, nesta máquina, uma nova versão funcional do projeto:

```text
Trilha da Aprovação
````

Repositório:

```text
trilha-aprovacao
```

Diretório preferencial:

```text
~/Aplicativos/trilha-aprovacao
```

Não dependa de nenhum arquivo existente no computador do trabalho.

A aplicação deve possuir:

* monorepo;
* backend Java com Spring Boot;
* frontend Vue 3 com TypeScript;
* PostgreSQL;
* Flyway;
* autenticação;
* isolamento por usuário;
* API REST;
* documentação OpenAPI;
* Swagger UI;
* dashboard;
* CRUD funcional;
* testes;
* documentação;
* Git com commits reais.

## 2. Regra de segurança para o diretório

Antes de criar o projeto:

1. verifique se `~/Aplicativos/trilha-aprovacao` já existe;
2. caso exista e não esteja vazio, não apague nem sobrescreva;
3. examine o conteúdo;
4. pare e informe o conflito;
5. somente continue automaticamente se o diretório não existir ou estiver vazio.

Não use `sudo`.

Não instale ferramentas globalmente.

Não altere configurações globais da máquina.

## 3. Preflight obrigatório

Valide:

```bash
git --version
java --version
javac --version
mvn --version
node --version
npm --version
docker --version
docker compose version
```

Requisitos:

* Java 21;
* Git;
* Node em versão LTS compatível;
* npm;
* Docker com Compose;
* curl;
* navegador disponível, quando possível.

Se Java, Node ou Docker não estiverem disponíveis:

* não use `sudo`;
* não improvise uma tecnologia diferente;
* informe exatamente o que está faltando;
* prossiga apenas nas partes que possam ser criadas e verificadas com segurança;
* marque a execução como parcial ou bloqueada.

## 4. Tecnologias

### Backend

Utilize:

```text
Java 21
Spring Boot 4.1.x
Maven Wrapper
Spring Web MVC
Spring Validation
Spring Data JPA
Spring Security
Spring Actuator
PostgreSQL
Flyway
springdoc-openapi 3.x compatível com Spring Boot 4
JUnit
Spring Boot Test
MockMvc
Testcontainers
```

Antes de fixar a versão do `springdoc-openapi`:

1. consulte a documentação oficial;
2. confirme compatibilidade com Spring Boot 4.1.x;
3. use a última versão estável compatível da linha 3.x;
4. não use springdoc 2.x com Spring Boot 4;
5. registre a versão escolhida.

### Frontend

Utilize:

```text
Vue 3
TypeScript
Vite 8.1.x
Vue Router
Pinia
Bootstrap 5.3
Bootstrap Icons
Sass
Vitest
Vue Test Utils
ESLint
Prettier
```

Utilize `fetch`.

Não adicione Axios.

Não adicione:

```text
Nuxt
Vuetify
PrimeVue
Quasar
Tailwind CSS
BootstrapVue
biblioteca de formulários
biblioteca de validação
biblioteca de estado remoto
```

Uma dependência nova só pode ser adicionada quando houver necessidade concreta e documentada.

## 5. Estrutura do monorepo

Crie:

```text
trilha-aprovacao/
├── aplicativos/
│   ├── backend/
│   └── frontend/
├── pacotes/
│   └── cliente-api/
├── infraestrutura/
│   ├── docker/
│   ├── banco-de-dados/
│   └── implantacao/
├── documentacao/
│   ├── arquitetura/
│   ├── decisoes/
│   ├── diagramas/
│   ├── api/
│   └── operacao/
├── scripts/
├── .agent/
│   ├── tarefas/
│   └── planos/
├── .github/
│   └── workflows/
├── AGENTS.md
├── README.md
├── Makefile
├── compose.yaml
├── .gitignore
├── .editorconfig
└── .env.example
```

Não crie pastas vazias sem finalidade. Quando necessário, use um pequeno `README.md` explicando a reserva.

## 6. Documentação inicial obrigatória

Crie:

```text
AGENTS.md
.agent/PLANS.md
.agent/tarefas/reconstrucao-completa-do-zero.md
documentacao/arquitetura/VISAO-DO-PRODUTO.md
documentacao/arquitetura/ARQUITETURA-INICIAL.md
documentacao/arquitetura/MODELO-DE-DOMINIO.md
documentacao/arquitetura/ESTADO-ATUAL-E-VISAO-DE-EVOLUCAO.md
documentacao/decisoes/ADR-001-MONOLITO-MODULAR.md
documentacao/decisoes/ADR-002-POSTGRESQL-E-FLYWAY.md
documentacao/decisoes/ADR-003-AUTENTICACAO-POR-SESSAO.md
documentacao/decisoes/ADR-004-DADOS-ISOLADOS-POR-USUARIO.md
documentacao/api/COMO-USAR-O-SWAGGER.md
```

Salve uma cópia integral desta especificação em:

```text
.agent/tarefas/reconstrucao-completa-do-zero.md
```

## 7. Idioma

Todo código próprio deve utilizar nomes em português:

* classes;
* métodos;
* atributos;
* pacotes;
* módulos;
* rotas;
* DTOs;
* entidades;
* objetos de valor;
* casos de uso;
* exceções;
* testes;
* tabelas;
* colunas;
* mensagens;
* documentação.

Não use acentos em identificadores.

Exemplos:

```java
Concurso
CargoDoConcurso
RegistroDeEstudo
adicionarMateria()
selecionarCargo()
consultarDashboard()
```

Elementos exigidos pelas tecnologias podem permanecer em inglês.

## 8. Arquitetura

Utilize:

```text
Monólito modular
```

Organize o backend por capacidades:

```text
br.com.trilhaaprovacao.autenticacao
br.com.trilhaaprovacao.usuarios
br.com.trilhaaprovacao.concursos
br.com.trilhaaprovacao.conteudos
br.com.trilhaaprovacao.materiais
br.com.trilhaaprovacao.estudos
br.com.trilhaaprovacao.dashboard
br.com.trilhaaprovacao.compartilhado
```

Dentro de cada capacidade, utilize apenas as divisões necessárias:

```text
dominio
aplicacao
infraestrutura
api
```

Fluxo esperado:

```text
API
 ↓
Casos de uso
 ↓
Domínio
 ↓
Portas
 ↑
Infraestrutura
```

O domínio não deve depender de:

* Spring MVC;
* HTTP;
* JSON;
* JPA;
* PostgreSQL;
* frontend.

Não exponha entidades JPA diretamente pela API.

Não coloque regras de negócio em controllers ou componentes Vue.

Não crie interfaces para todas as classes.

Não use microsserviços, CQRS, event sourcing, Kafka, Redis ou Kubernetes.

## 9. Propriedade e isolamento dos dados

“Matéria global” significa reutilizável entre os concursos do mesmo usuário.

Não significa catálogo compartilhado entre todos os usuários.

Cada usuário deve possuir seus próprios:

* concursos;
* matérias;
* tópicos;
* materiais;
* registros de estudo.

Toda consulta e alteração deve usar o usuário autenticado.

A API não deve aceitar livremente `identificadorDoUsuario` nos comandos de negócio.

O usuário deve ser resolvido pela sessão autenticada.

Testes devem provar que um usuário não acessa dados de outro.

## 10. Autenticação

Implemente autenticação real.

Utilize Spring Security com sessão HTTP.

Requisitos:

* cadastro de conta;
* login;
* logout;
* consulta da sessão atual;
* senha armazenada com BCrypt;
* cookie de sessão `HttpOnly`;
* `Secure` no perfil de produção;
* `SameSite=Lax`;
* proteção CSRF;
* endpoint para obtenção do token CSRF;
* frontend enviando credenciais e token CSRF;
* ausência de senha em logs;
* ausência de usuário fornecido livremente nas operações.

Não utilize JWT nesta versão.

Não implemente login social.

### Entidade `Usuario`

Campos mínimos:

```text
identificador
nome
email
senhaHash
situacao
criadoEm
atualizadoEm
```

Situações:

```text
ATIVO
INATIVO
```

Regras:

* nome obrigatório;
* e-mail válido;
* e-mail único;
* senha com no mínimo 8 caracteres;
* senha nunca retornada;
* e-mail normalizado;
* conta inativa não realiza login.

### Endpoints

```text
POST /api/v1/autenticacao/cadastro
POST /api/v1/autenticacao/login
POST /api/v1/autenticacao/logout
GET  /api/v1/autenticacao/sessao
GET  /api/v1/autenticacao/csrf
```

Não implemente ainda:

* recuperação de senha;
* confirmação de e-mail;
* segundo fator;
* login social;
* administração de usuários.

## 11. Modelo de domínio

Implemente as entidades abaixo.

### 11.1 `Concurso`

Representa o objetivo ou processo seletivo acompanhado pelo usuário.

Campos:

```text
identificador
nome
descricao, opcional
orgao, opcional
banca, opcional
situacao
dataPrevistaPrincipal, opcional
ativo
criadoEm
atualizadoEm
versao
```

Situações:

```text
PLANEJADO
EDITAL_PUBLICADO
INSCRICOES_ABERTAS
EM_ANDAMENTO
ENCERRADO
SUSPENSO
CANCELADO
ARQUIVADO
```

Regras:

* nome obrigatório;
* apenas um concurso ativo por usuário;
* concurso arquivado não aceita novas alterações de conteúdo;
* a data pode ser futura ou passada;
* exclusão física somente quando não houver dependências;
* caso contrário, utilizar arquivamento.

### 11.2 `Edital`

Um concurso pode possuir um ou mais editais.

Campos:

```text
identificador
concurso
titulo
numero, opcional
ano, opcional
descricao, opcional
dataDePublicacao, opcional
enderecoDoDocumento, opcional
principal
criadoEm
atualizadoEm
```

Regras:

* título obrigatório;
* somente um edital principal por concurso;
* URL opcional e válida;
* não armazenar o arquivo PDF nesta versão;
* retificações completas ficam para etapa futura.

### 11.3 `CargoDoConcurso`

Campos:

```text
identificador
concurso
nome
area, opcional
especialidade, opcional
nivelDeEscolaridade
selecionado
ordem
criadoEm
atualizadoEm
```

Níveis:

```text
FUNDAMENTAL
MEDIO
TECNICO
SUPERIOR
NAO_INFORMADO
```

Regras:

* nome obrigatório;
* no máximo um cargo selecionado por concurso;
* cargo duplicado deve ser rejeitado;
* o dashboard considera inicialmente o cargo selecionado.

### 11.4 `Prova`

Campos:

```text
identificador
cargo
nome
tipo
carater
ordem
dataHoraPrevista, opcional
duracaoEmMinutos, opcional
quantidadeDeQuestoes, opcional
pontuacaoMaxima, opcional
pontuacaoMinima, opcional
```

Tipos:

```text
OBJETIVA
DISCURSIVA
PRATICA
TITULOS
OUTRA
```

Caráter:

```text
ELIMINATORIO
CLASSIFICATORIO
ELIMINATORIO_E_CLASSIFICATORIO
NAO_INFORMADO
```

Regras:

* nome e tipo obrigatórios;
* valores numéricos positivos;
* pontuação mínima não pode exceder a máxima;
* prova duplicada dentro do cargo deve ser rejeitada.

### 11.5 `GrupoDeConteudo`

Exemplos:

```text
Conhecimentos básicos
Conhecimentos gerais
Conhecimentos técnicos
Conhecimentos específicos
Bloco I
Bloco II
```

Campos:

```text
identificador
prova
nome
ordem
quantidadeDeQuestoes, opcional
pontuacaoMaxima, opcional
pontuacaoMinima, opcional
```

Regras:

* nome obrigatório;
* ordem positiva;
* grupo duplicado dentro da prova deve ser rejeitado;
* pontuação mínima não pode exceder a máxima.

### 11.6 `Materia`

A matéria pertence ao catálogo pessoal do usuário e é reutilizável entre concursos.

Campos:

```text
identificador
nome
descricao, opcional
cor, opcional
arquivada
criadoEm
atualizadoEm
versao
```

Regras:

* nome obrigatório;
* nome único por usuário, ignorando caixa e espaços externos;
* arquivamento em vez de exclusão quando houver histórico;
* nenhuma propriedade específica de concurso deve ficar em `Materia`.

### 11.7 `TopicoDaMateria`

Campos:

```text
identificador
materia
nome
descricao, opcional
topicoPai, opcional
ordem
arquivado
```

Regras:

* nome obrigatório;
* tópico-pai pertence à mesma matéria;
* tópico não pode ser pai de si mesmo;
* impedir ciclos;
* nome único entre irmãos;
* histórico permanece válido mesmo após arquivamento.

### 11.8 `MateriaDaProva`

Relaciona uma matéria existente a um grupo da prova.

Campos:

```text
identificador
grupoDeConteudo
materia
ordem
peso, opcional
quantidadeDeQuestoes, opcional
pontuacaoMaxima, opcional
```

Regras:

* a matéria deve pertencer ao mesmo usuário;
* a mesma matéria não pode aparecer duas vezes no mesmo grupo;
* não copiar tópicos;
* não copiar histórico;
* valores numéricos positivos.

### 11.9 `ItemDoEdital`

Representa a redação oficial de um conteúdo programático.

Campos:

```text
identificador
edital
materiaDaProva
descricaoOriginal
itemPai, opcional
ordem
```

Regras:

* descrição obrigatória;
* item-pai deve pertencer à mesma matéria da prova;
* impedir ciclos;
* preservar a redação original.

### 11.10 `MapeamentoDeItemDoEdital`

Liga um item oficial a um tópico reutilizável.

Campos:

```text
identificador
itemDoEdital
topicoDaMateria
confirmado
criadoEm
```

Regras:

* item e tópico devem representar a mesma matéria;
* par item/tópico não pode ser duplicado;
* mapeamento manual é confirmado;
* remover mapeamento não remove item, tópico ou estudos.

### 11.11 `MaterialDeEstudo`

Representa aula, PDF ou outro recurso.

Campos:

```text
identificador
titulo
tipo
descricao, opcional
fonte, opcional
endereco, opcional
duracaoEstimadaEmMinutos, opcional
arquivado
criadoEm
atualizadoEm
```

Tipos:

```text
AULA
PDF
OUTRO
```

Regras:

* título obrigatório;
* URL opcional e válida;
* não armazenar arquivos;
* material pode cobrir vários tópicos;
* material arquivado permanece no histórico.

### 11.12 `CoberturaDeTopicoPorMaterial`

Campos:

```text
identificador
material
topico
criadoEm
```

Regras:

* par material/tópico não pode ser duplicado;
* material e tópico devem pertencer ao mesmo usuário;
* remoção não apaga material, tópico ou estudo.

### 11.13 `RegistroDeEstudo`

Campos:

```text
identificador
topico
material, opcional
dataHora
duracaoEmMinutos
observacao, opcional
situacao
criadoEm
atualizadoEm
versao
```

Situações:

```text
ATIVO
CORRIGIDO
CANCELADO
```

Regras:

* tópico obrigatório;
* material opcional;
* se houver material, ele deve cobrir o tópico ou a inconsistência deve ser rejeitada;
* duração mínima de 1 minuto;
* duração máxima de 1.440 minutos;
* registro pertence ao tópico, não ao concurso;
* não apagar fisicamente registros históricos;
* correção deve preservar rastreabilidade mínima;
* cancelamento substitui exclusão destrutiva.

## 12. Progresso

Não crie uma entidade persistente chamada `Progresso`.

Calcule o progresso a partir de fatos.

Um tópico é considerado estudado quando possui pelo menos um registro ativo.

Para o concurso ativo:

```text
topicosExigidos
topicosComEstudo
itensMapeados
itensSemMapeamento
tempoEstudadoNaSemana
atividadeRecente
```

Não invente domínio ou domínio de conhecimento percentual.

Apresente inicialmente medidas objetivas:

```text
12 de 38 tópicos exigidos possuem estudo registrado
```

## 13. Dashboard

Crie endpoint agregador:

```text
GET /api/v1/dashboard
```

Resposta mínima:

```json
{
  "concursoAtivo": {},
  "dataDaProximaProva": null,
  "diasAteAProva": null,
  "tempoEstudadoNaSemanaEmMinutos": 0,
  "quantidadeDeMaterias": 0,
  "quantidadeDeTopicosExigidos": 0,
  "quantidadeDeTopicosComEstudo": 0,
  "quantidadeDeItensSemMapeamento": 0,
  "atividadeRecente": [],
  "alertas": []
}
```

Os alertas iniciais devem ser determinísticos:

* concurso sem cargo selecionado;
* concurso sem prova;
* grupo sem matéria;
* item sem mapeamento;
* matéria sem tópico;
* prova próxima sem estudos registrados.

Não implemente recomendações por IA.

## 14. Persistência

Utilize PostgreSQL.

Crie `compose.yaml` com:

```text
PostgreSQL
volume persistente
healthcheck
porta configurável
credenciais por variáveis de ambiente
```

Não versione credenciais reais.

Use Flyway para todas as tabelas.

Não use `ddl-auto=create` ou `update`.

Em desenvolvimento:

```text
spring.jpa.hibernate.ddl-auto=validate
```

Crie migrations versionadas.

Requisitos do banco:

* UUID como identificador;
* chaves estrangeiras;
* restrições únicas;
* índices por usuário;
* índices nas relações principais;
* `timestamp with time zone`;
* colunas de auditoria;
* coluna de versão onde houver concorrência;
* nomes em português;
* cascatas destrutivas evitadas.

Crie diagrama ER em Mermaid.

## 15. API REST

Prefixo:

```text
/api/v1
```

Use DTOs.

Não exponha entidades de domínio ou JPA.

Use paginação nas coleções principais.

### Matérias

```text
POST   /api/v1/materias
GET    /api/v1/materias
GET    /api/v1/materias/{id}
PUT    /api/v1/materias/{id}
DELETE /api/v1/materias/{id}
POST   /api/v1/materias/{id}/arquivamento
```

### Tópicos

```text
POST   /api/v1/materias/{materiaId}/topicos
GET    /api/v1/materias/{materiaId}/topicos
GET    /api/v1/topicos/{id}
PUT    /api/v1/topicos/{id}
DELETE /api/v1/topicos/{id}
POST   /api/v1/topicos/{id}/arquivamento
```

### Concursos

```text
POST   /api/v1/concursos
GET    /api/v1/concursos
GET    /api/v1/concursos/{id}
PUT    /api/v1/concursos/{id}
DELETE /api/v1/concursos/{id}
POST   /api/v1/concursos/{id}/ativacao
POST   /api/v1/concursos/{id}/arquivamento
```

### Editais

```text
POST   /api/v1/concursos/{concursoId}/editais
GET    /api/v1/concursos/{concursoId}/editais
GET    /api/v1/editais/{id}
PUT    /api/v1/editais/{id}
DELETE /api/v1/editais/{id}
POST   /api/v1/editais/{id}/definicao-como-principal
```

### Cargos

```text
POST   /api/v1/concursos/{concursoId}/cargos
GET    /api/v1/concursos/{concursoId}/cargos
GET    /api/v1/cargos/{id}
PUT    /api/v1/cargos/{id}
DELETE /api/v1/cargos/{id}
POST   /api/v1/cargos/{id}/selecao
```

### Provas

```text
POST   /api/v1/cargos/{cargoId}/provas
GET    /api/v1/cargos/{cargoId}/provas
GET    /api/v1/provas/{id}
PUT    /api/v1/provas/{id}
DELETE /api/v1/provas/{id}
```

### Grupos

```text
POST   /api/v1/provas/{provaId}/grupos
GET    /api/v1/provas/{provaId}/grupos
GET    /api/v1/grupos-de-conteudo/{id}
PUT    /api/v1/grupos-de-conteudo/{id}
DELETE /api/v1/grupos-de-conteudo/{id}
```

### Matérias da prova

```text
POST   /api/v1/grupos-de-conteudo/{grupoId}/materias
GET    /api/v1/grupos-de-conteudo/{grupoId}/materias
GET    /api/v1/materias-da-prova/{id}
PUT    /api/v1/materias-da-prova/{id}
DELETE /api/v1/materias-da-prova/{id}
```

### Itens do edital

```text
POST   /api/v1/materias-da-prova/{materiaDaProvaId}/itens
GET    /api/v1/materias-da-prova/{materiaDaProvaId}/itens
GET    /api/v1/itens-do-edital/{id}
PUT    /api/v1/itens-do-edital/{id}
DELETE /api/v1/itens-do-edital/{id}
```

### Mapeamentos

```text
POST   /api/v1/itens-do-edital/{itemId}/mapeamentos
GET    /api/v1/itens-do-edital/{itemId}/mapeamentos
DELETE /api/v1/itens-do-edital/{itemId}/mapeamentos/{topicoId}
```

### Materiais

```text
POST   /api/v1/materiais
GET    /api/v1/materiais
GET    /api/v1/materiais/{id}
PUT    /api/v1/materiais/{id}
DELETE /api/v1/materiais/{id}
POST   /api/v1/materiais/{id}/arquivamento
```

### Cobertura de materiais

```text
POST   /api/v1/materiais/{materialId}/topicos
GET    /api/v1/materiais/{materialId}/topicos
DELETE /api/v1/materiais/{materialId}/topicos/{topicoId}
```

### Estudos

```text
POST   /api/v1/estudos
GET    /api/v1/estudos
GET    /api/v1/estudos/{id}
PUT    /api/v1/estudos/{id}/correcao
POST   /api/v1/estudos/{id}/cancelamento
```

Use status HTTP apropriados.

Em criações, retorne `201` e cabeçalho `Location`.

## 16. Tratamento de erros

Contrato:

```json
{
  "codigo": "MATERIA_JA_CADASTRADA",
  "mensagem": "Já existe uma matéria com esse nome.",
  "identificadorDeCorrelacao": "uuid",
  "detalhes": []
}
```

Trate:

* entrada inválida;
* autenticação ausente;
* acesso negado;
* recurso inexistente;
* conflito;
* regra de domínio;
* concorrência otimista;
* erro inesperado.

Registre erros inesperados com stack trace no servidor.

Não exponha stack trace ao cliente.

Adicione identificador de correlação por requisição.

## 17. OpenAPI e Swagger

Configure OpenAPI com:

```text
Título: API Trilha da Aprovação
Versão: v1
Descrição
Contato do projeto, sem dados pessoais sensíveis
Tags por capacidade
Respostas de erro
Esquema de autenticação por cookie de sessão
Informação sobre CSRF
```

Exponha em desenvolvimento:

```text
/v3/api-docs
/swagger-ui.html
/swagger-ui/index.html
```

Confirme a rota real usada pela versão instalada.

Permita Swagger e documentação no perfil local.

No perfil de produção:

* Swagger UI desabilitado por padrão;
* OpenAPI configurável por variável de ambiente.

Documente:

* como fazer cadastro;
* como fazer login;
* como obter CSRF;
* como testar endpoints autenticados;
* limitações do Swagger com sessão e CSRF.

Adicione testes que confirmem que `/v3/api-docs` responde e contém os grupos principais.

## 18. Frontend

Organize:

```text
src/
├── aplicacao/
│   ├── layouts/
│   ├── roteamento/
│   └── estado/
├── compartilhado/
│   ├── api/
│   ├── componentes/
│   ├── composables/
│   ├── estilos/
│   └── tipos/
└── modulos/
    ├── autenticacao/
    ├── dashboard/
    ├── concursos/
    ├── materias/
    ├── materiais/
    └── estudos/
```

Não crie pastas vazias.

### Layouts

Crie:

```text
LayoutDeAutenticacao
LayoutPrincipal
```

### Rotas públicas

```text
/login
/cadastro
```

### Rotas autenticadas

```text
/dashboard
/concursos
/concursos/novo
/concursos/:id
/materias
/materias/:id
/materiais
/materiais/:id
/estudos
/estudos/novo
```

Implemente guarda de autenticação.

Ao perder a sessão:

* limpe estado local;
* redirecione ao login;
* preserve uma mensagem compreensível.

## 19. Páginas

### Login

* e-mail;
* senha;
* mostrar/ocultar senha;
* lembrar visualmente, sem persistência desnecessária;
* carregamento;
* erros;
* link para cadastro.

### Cadastro

* nome;
* e-mail;
* senha;
* confirmação;
* validações;
* login automático ou redirecionamento após sucesso.

### Dashboard

Dois estados:

#### Sem concurso

```text
Você ainda não possui um concurso cadastrado.
[Adicionar meu primeiro concurso]
```

#### Com concurso ativo

Mostrar:

* concurso ativo;
* cargo selecionado;
* próxima prova;
* dias restantes;
* tempo estudado na semana;
* matérias;
* tópicos exigidos;
* tópicos estudados;
* itens sem mapeamento;
* atividade recente;
* alertas;
* ações rápidas.

Não use gráficos de biblioteca externa.

Use cards, badges e progress bars do Bootstrap.

### Concursos

* listagem;
* pesquisa;
* situação;
* data;
* ativar;
* editar;
* arquivar;
* abrir detalhes.

### Cadastro de concurso

Use etapas ou seções:

```text
1. Dados gerais
2. Edital
3. Cargo
4. Prova
5. Grupos
6. Matérias
```

Não obrigue o preenchimento de toda a árvore em um único envio.

### Detalhes do concurso

Organização:

```text
Dados gerais
Editais
Cargos
  Provas
    Grupos
      Matérias
        Itens
          Mapeamentos
```

Permita CRUD sem recarregar toda a aplicação.

### Matérias

* listar;
* criar;
* editar;
* arquivar;
* abrir detalhes.

### Detalhes da matéria

* dados;
* árvore de tópicos;
* materiais relacionados;
* estudos recentes;
* concursos que utilizam a matéria.

### Materiais

* CRUD;
* tipo;
* fonte;
* endereço;
* tópicos cobertos;
* arquivamento.

### Estudos

* registrar estudo;
* selecionar matéria;
* selecionar tópico;
* selecionar material opcional;
* data e hora;
* duração;
* observação;
* histórico;
* correção;
* cancelamento.

## 20. Componentes compartilhados

Crie somente componentes com utilidade real:

```text
BarraDeNavegacao
MenuLateral
CabecalhoDePagina
BotaoDeAcao
CampoDeTexto
CampoDeSelecao
CampoDeData
AlertaDaAplicacao
IndicadorDeCarregamento
EstadoVazio
CartaoDeResumo
PainelDeSecao
ModalDeConfirmacao
Paginacao
```

Não envolva cada classe Bootstrap em um componente.

Não crie formulário dinâmico universal.

## 21. Estado frontend

Use Pinia para:

```text
sessão autenticada
usuário atual
concurso ativo
preferências de navegação realmente compartilhadas
```

Dados remotos específicos de página devem permanecer em serviços ou composables.

Não armazene senha.

Não armazene dados como persistência paralela em `localStorage`.

## 22. Cliente HTTP

Crie cliente central com:

* URL relativa `/api`;
* `credentials: include`;
* JSON;
* CSRF;
* tratamento padronizado de erros;
* 401 redirecionando ao login;
* cancelamento por `AbortController`;
* sem URLs absolutas fixas.

Vite:

```text
VITE_ALVO_API=http://localhost:8080
```

A variável configura apenas o proxy.

## 23. Visual

Use:

```text
Bootstrap 5.3
Bootstrap Icons
Sass
```

Identidade:

* azul profundo;
* verde ou turquesa;
* fundos claros;
* cartões brancos;
* sombras discretas;
* cantos moderados;
* boa hierarquia;
* aparência moderna e profissional.

Não crie modo escuro agora.

Não adicione outra biblioteca visual.

## 24. Acessibilidade

Implemente:

* HTML semântico;
* um `h1` por página;
* labels;
* foco visível;
* navegação por teclado;
* `aria-invalid`;
* `aria-describedby`;
* `aria-live`;
* botões apenas com ícone usando `aria-label`;
* estados que não dependam somente de cor;
* contraste adequado;
* responsividade em 390, 768 e 1280 px.

## 25. Dados de demonstração

Não crie dados fictícios no ambiente normal.

Crie um perfil opcional:

```text
demo
```

Somente nesse perfil, pode existir carga de demonstração claramente identificada.

O perfil local padrão deve iniciar sem dados, permitindo cadastro real.

Não versione senhas reais.

## 26. Testes do backend

Crie:

* testes de domínio;
* testes de casos de uso;
* testes de repositórios;
* testes com PostgreSQL via Testcontainers;
* testes de migrations;
* testes Web MVC;
* testes de segurança;
* testes de isolamento por usuário;
* testes do dashboard;
* testes da OpenAPI;
* testes de arquitetura.

Cenários obrigatórios:

1. usuário A não acessa dados do usuário B;
2. apenas um concurso ativo por usuário;
3. apenas um cargo selecionado por concurso;
4. matéria reutilizada em dois concursos;
5. estudo ligado ao tópico conta nos dois concursos;
6. item não mapeado não gera progresso;
7. material só pode ser usado com tópico compatível;
8. duração extrema retorna erro e não HTTP 500;
9. migrations sobem em banco vazio;
10. Swagger/OpenAPI é gerado.

## 27. Testes do frontend

Crie testes para:

* login;
* cadastro;
* guarda de rota;
* dashboard vazio;
* dashboard com dados;
* cadastro de concurso;
* edição de concurso;
* estrutura de cargo/prova/grupo;
* matéria e tópico;
* item e mapeamento;
* material e cobertura;
* registro de estudo;
* correção e cancelamento;
* erros;
* loading;
* estados vazios;
* sessão expirada;
* acessibilidade básica.

## 28. Teste funcional completo

Valide este fluxo:

```text
1. Criar uma conta.
2. Fazer login.
3. Criar Direito Constitucional.
4. Criar o tópico Direitos fundamentais.
5. Criar um material Aula 01.
6. Vincular o material ao tópico.
7. Criar Concurso A.
8. Adicionar edital.
9. Adicionar cargo e selecioná-lo.
10. Adicionar prova objetiva.
11. Adicionar Conhecimentos básicos.
12. Relacionar Direito Constitucional.
13. Criar item do edital.
14. Mapear o item para Direitos fundamentais.
15. Ativar Concurso A.
16. Registrar 60 minutos de estudo.
17. Conferir o dashboard.
18. Criar Concurso B.
19. Utilizar a mesma matéria e tópico.
20. Mapear o novo item ao mesmo tópico.
21. Conferir que o estudo anterior é reconhecido.
22. Fazer logout.
23. Confirmar bloqueio das rotas autenticadas.
```

## 29. Makefile

Crie alvos:

```text
ajuda
infra-subir
infra-parar
backend-executar
frontend-executar
testar-backend
testar-frontend
verificar-backend
verificar-frontend
verificar
limpar
```

Os alvos devem usar wrappers e scripts locais.

## 30. CI

Crie GitHub Actions para:

* backend;
* frontend;
* build;
* testes;
* formatação;
* migrations/Testcontainers quando possível.

Não faça deploy.

Não use segredos reais.

## 31. Git e commits

O usuário autoriza explicitamente:

* inicializar o Git;
* usar a branch `main`;
* criar commits locais;
* não fazer push.

Antes do primeiro commit:

* revise `.gitignore`;
* confirme que `node_modules`, `target`, `dist`, `.env`, logs, volumes e screenshots não serão versionados.

Crie commits somente com etapas aprovadas e testes passando.

Sugestão:

```text
chore: cria fundacao do monorepo
feat: implementa autenticacao e persistencia
feat: implementa dominio de concursos e conteudos
feat: implementa materiais e registros de estudo
feat: implementa frontend e dashboard
docs: consolida documentacao e openapi
test: adiciona validacao funcional completa
```

Se um marco estiver quebrado, não o comite como concluído.

Não faça push.

## 32. Marcos de execução

### Marco 1 — Fundação

* diretórios;
* Git;
* AGENTS;
* documentos;
* backend mínimo;
* frontend mínimo;
* Docker;
* PostgreSQL;
* health check;
* primeiro commit.

### Marco 2 — Autenticação

* usuário;
* segurança;
* sessão;
* CSRF;
* login;
* cadastro;
* testes;
* frontend de autenticação;
* commit.

### Marco 3 — Conteúdos reutilizáveis

* matéria;
* tópico;
* CRUD;
* testes;
* frontend;
* commit.

### Marco 4 — Concurso

* concurso;
* edital;
* cargo;
* prova;
* grupo;
* matéria da prova;
* CRUD;
* testes;
* frontend;
* commit.

### Marco 5 — Conteúdo programático

* item;
* mapeamento;
* CRUD;
* testes;
* frontend;
* commit.

### Marco 6 — Materiais e estudos

* material;
* cobertura;
* registro;
* correção;
* cancelamento;
* testes;
* frontend;
* commit.

### Marco 7 — Dashboard

* agregações;
* alertas;
* página;
* responsividade;
* testes;
* commit.

### Marco 8 — OpenAPI e consolidação

* Swagger;
* documentação;
* CI;
* validação final;
* commit.

Não avance para o próximo marco enquanto o atual estiver quebrado.

## 33. Comandos de validação

Backend:

```bash
cd ~/Aplicativos/trilha-aprovacao/aplicativos/backend
./mvnw test
./mvnw verify
```

Frontend:

```bash
cd ~/Aplicativos/trilha-aprovacao/aplicativos/frontend
npm run verificar-tipos
npm run lint
npm run test
npm run build
npm run verificar-formatacao
npm audit
```

Infraestrutura:

```bash
cd ~/Aplicativos/trilha-aprovacao
docker compose config
docker compose up -d
docker compose ps
```

Completo:

```bash
make verificar
```

Validação real:

* iniciar backend;
* iniciar frontend;
* abrir Swagger;
* executar fluxo funcional;
* validar celular, tablet e desktop;
* encerrar processos temporários.

## 34. Critérios finais

A tarefa só estará concluída quando:

* repositório existir;
* Git possuir commits;
* PostgreSQL funcionar;
* migrations funcionarem;
* cadastro e login funcionarem;
* isolamento por usuário estiver testado;
* CRUDs estiverem funcionais;
* dashboard estiver funcional;
* frontend estiver integrado;
* Swagger estiver acessível;
* `/v3/api-docs` estiver válido;
* testes passarem;
* builds passarem;
* `npm audit` não apresentar vulnerabilidades conhecidas;
* documentação estiver atualizada;
* fluxo entre dois concursos estiver validado;
* nenhuma senha ou segredo estiver versionado;
* nenhum processo temporário permanecer em execução.

## 35. Fora do escopo

Não implementar nesta reconstrução:

* pagamento de inscrição;
* pedido de isenção;
* local de prova do candidato;
* recursos;
* resultados;
* convocações;
* notificações;
* e-mail;
* recuperação de senha;
* login social;
* segundo fator;
* upload de PDF;
* armazenamento de vídeo;
* leitura automática de editais;
* IA;
* MCP;
* OpenClaw;
* planejamento avançado;
* revisão espaçada;
* questões e desempenho;
* deploy;
* aplicativo móvel;
* microsserviços.

Não crie classes vazias para esses conceitos.

## 36. Relatório final obrigatório

Ao finalizar, responda:

```text
ETAPA:
- Reconstrução completa da base do Trilha da Aprovação

STATUS:
- CONCLUÍDA, PARCIAL ou BLOQUEADA

AMBIENTE:
- sistema:
- Java:
- Maven Wrapper:
- Node:
- npm:
- Docker:
- PostgreSQL:

REPOSITÓRIO:
- caminho:
- branch:
- commits criados:
- arquivos não rastreados:

ARQUITETURA:
- ...

ENTIDADES:
- ...

AUTENTICAÇÃO:
- ...

BANCO E MIGRATIONS:
- ...

ENDPOINTS:
- ...

OPENAPI E SWAGGER:
- dependência:
- versão:
- Swagger UI:
- OpenAPI JSON:
- validação:

FRONTEND:
- páginas:
- componentes:
- responsividade:
- acessibilidade:

DASHBOARD:
- ...

TESTES DO BACKEND:
- ...

TESTES DO FRONTEND:
- ...

TESTE FUNCIONAL COMPLETO:
- ...

COMANDOS EXECUTADOS:
- ...

RESULTADOS:
- Maven test:
- Maven verify:
- tipos:
- lint:
- Vitest:
- build:
- formatação:
- npm audit:
- make verificar:

VALIDAÇÃO REAL:
- banco:
- backend:
- frontend:
- cadastro:
- login:
- Swagger:
- fluxo entre concursos:
- logout:
- celular:
- tablet:
- desktop:
- processos encerrados:

DOCUMENTAÇÃO:
- ...

DIVERGÊNCIAS:
- ...

PROBLEMAS:
- ...

RISCOS RESTANTES:
- ...

ITENS FORA DO ESCOPO:
- ...

PRÓXIMA DECISÃO NECESSÁRIA:
- ...

GIT:
- commits realizados:
- push realizado: não
```

Não declare como aprovado algo que não foi executado.

Comece pelo preflight, valide que o diretório de destino está livre e crie o plano de execução antes de implementar.

````

Para iniciar no Codex, use somente:

```text
Crie do zero o projeto descrito integralmente no documento fornecido.

Implemente por marcos, não pule testes, crie os commits locais autorizados e não faça push. Caso o diretório de destino já contenha arquivos, não sobrescreva: pare e informe o conflito.
````

[1]: https://docs.spring.io/spring-boot/system-requirements.html?utm_source=chatgpt.com "System Requirements :: Spring Boot"
