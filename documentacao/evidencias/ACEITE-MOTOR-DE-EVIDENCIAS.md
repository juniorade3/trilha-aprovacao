# Aceite do Motor de Evidencias e Diagnostico por Topico

Data da validacao: 21 de julho de 2026.

## Referencia

- Branch: `feature/motor-evidencias-diagnostico`.
- Base: `67ac591383bd9541794ba0c8d342c30215bfb39a`.
- Migracao final: V15.
- Escopo validado: evidencias objetivas em estudos manuais e execucoes,
  padroes de erro, diagnostico por topico e interface no Historico.

## Porta automatizada executada

- `docker compose config --quiet`: passou.
- `make verificar`: passou.
- Backend: 121 testes, sem falhas, erros ou testes ignorados.
- Frontend: 22 arquivos e 104 testes, sem falhas.
- Tipos, ESLint, build de producao e Prettier: passaram.
- `npm audit`: zero vulnerabilidades encontradas.
- `git diff --check`: passou.
- PostgreSQL vazio: Flyway aplicou V1 a V15; o teste verificou FKs sem cascata
  destrutiva, checks, unicidades, indices e o backfill nos dois sentidos da
  cadeia de correcoes.
- Arquitetura: as suites ArchUnit passaram, inclusive a independencia do
  dominio e a regressao da geracao deterministica.

## Validacao manual executada

- Aplicacao real verificada em 390 x 844, 768 x 1024 e 1280 x 800, sem
  rolagem horizontal da pagina; a tabela do diagnostico permanece navegavel no
  recipiente responsivo em 390 px.
- Registro de questoes exibiu os campos condicionais, calculou quatro erros
  para 15 questoes e 11 acertos e sugeriu um padrao ja utilizado no topico.
- Diagnostico exibiu percentual recente, ultima evidencia e padrao repetido.
- Teclado: foco inicial no modal, navegacao por Tab, fechamento por Escape e
  retorno do foco ao botao de origem foram confirmados.
- Erro de rede: a pagina mostrou mensagem recuperavel e `Tentar novamente`;
  a repeticao recarregou o diagnostico depois de restaurar a rede.
- Sessao expirada: houve redirecionamento para login, e um novo login funcionou
  sem recarregar a pagina, com renovacao do token CSRF.
- Isolamento A/B: o usuario B recebeu diagnostico vazio para seus dados e 404
  ao consultar o topico do usuario A; os testes automatizados tambem cobrem o
  filtro de materia pertencente a outro usuario.
- Swagger UI e `/v3/api-docs`: responderam 200; a rota de diagnostico sem sessao
  respondeu 401.
- Inicializacao final: a aplicacao validou as 15 migracoes e iniciou nas portas
  8080 e 5173.

## Limites preservados

Nao foram criados ranking, selecao automatica de topicos, banco de questoes,
revisao espacada, lacunas, IA ou outro modulo futuro.
