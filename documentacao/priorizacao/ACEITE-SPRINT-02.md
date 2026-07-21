# Aceite da Sprint 02 - revisoes espacadas

## Base

- `main`: `0a745803c4f1c7d3957f3aed0f37c015f90a54a9`;
- ultima migration: V15;
- branch: `feature/priorizacao-02-revisao-espacada`.

## Resultados executados

### Baseline antes da implementacao

- CI da Sprint 01: aprovado;
- `make verificar` final da Sprint 01: aprovado;
- backend: 129 testes aprovados;
- frontend: 111 testes aprovados.

## Porta de qualidade da Sprint 02

- `make verificar`: aprovado;
- backend: 139 testes aprovados;
- frontend: 125 testes aprovados em 25 arquivos;
- verificacao de tipos, lint, build e formatacao do frontend: aprovados;
- `npm audit`: nenhuma vulnerabilidade encontrada;
- PostgreSQL vazio: migrations V1 a V15 aplicadas pelos testes de integracao;
- testes focados da agenda, OpenAPI e arquitetura: 15 aprovados;
- testes focados da interface e foco: 48 aprovados.

## Validacao manual

- ambiente isolado com PostgreSQL 17 vazio: V1 a V15 aplicadas e backend e
  frontend iniciados em portas alternativas;
- API real: revisoes `VENCIDA`, `DEVIDA_HOJE`, `FUTURA` e `JA_PLANEJADA`,
  horizonte, etapa, intervalo, atraso e bloco aberto conferidos;
- revisao registrada pela interface com recordacao 4: etapa avancou de 0 para
  1, intervalo passou a 3 dias e a fila foi recalculada sem recarregar a pagina;
- isolamento A/B real: cada sessao recebeu somente o proprio topico;
- contratos: 401 sem sessao e 422 para periodo invalido;
- Swagger e OpenAPI: interface real, tag, parametros, respostas e schema
  conferidos;
- 390 x 844, 768 x 1024 e 1280 x 800: sem rolagem horizontal e com a fila,
  estados e acoes responsivos;
- teclado e foco: abertura preenchida do registro rapido, Escape com retorno ao
  botao de origem, foco no bloco semanal e foco no titulo apos registrar;
- erro de rede: repeticao recuperou a fila e restaurou o foco;
- sessao expirada: redirecionamento preservado, aviso apresentado e foco no
  campo de e-mail.

Capturas temporarias conferidas em
`/tmp/trilha-sprint2-revisoes-autenticado-390x844.png`,
`/tmp/trilha-sprint2-revisoes-autenticado-768x1024.png` e
`/tmp/trilha-sprint2-revisoes-autenticado-1280x800.png`. O ambiente isolado foi
encerrado e suas portas foram confirmadas como livres.
