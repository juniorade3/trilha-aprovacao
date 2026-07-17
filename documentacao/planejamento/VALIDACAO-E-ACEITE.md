# Validacao e aceite

## Comandos por porta

Infraestrutura:

```bash
docker compose config
docker compose up -d
docker compose ps
```

Backend:

```bash
cd aplicativos/backend
./mvnw test
./mvnw verify
```

Frontend:

```bash
cd aplicativos/frontend
npm run verificar-tipos
npm run lint
npm run test
npm run build
npm run verificar-formatacao
npm audit
```

Integrado:

```bash
make verificar
```

Os nomes finais dos alvos do Makefile devem corresponder aos requeridos pela especificacao: `ajuda`, `infra-subir`, `infra-parar`, `backend-executar`, `frontend-executar`, `testar-backend`, `testar-frontend`, `verificar-backend`, `verificar-frontend`, `verificar` e `limpar`.

## Cenários obrigatorios

1. Usuario A cria dados e Usuario B recebe 404/403 ao tentar le-los ou alterá-los.
2. O mesmo usuario nao consegue ativar dois concursos nem selecionar dois cargos no mesmo concurso.
3. Uma Materia e seu Topico sao reutilizados em dois concursos sem copiar o registro de estudo.
4. Item sem mapeamento nao aparece como estudado; material incompativel e duracao extrema retornam erro de negocio, nunca 500.
5. Uma base PostgreSQL vazia sobe todas as migrations; `/v3/api-docs` responde e apresenta as capacidades principais.
6. Fluxo completo: conta, login, materia/topico/material/cobertura, Concurso A, edital/cargo/prova/grupo/materia/item/mapa, estudo de 60 minutos, dashboard, Concurso B reutilizando o topico, logout e bloqueio de rotas.
7. As telas principais funcionam em 390, 768 e 1280 px, com navegacao por teclado e mensagens anunciadas.

## Evidencias por sprint

Para cada sprint, registrar no PR ou no log de execucao: commit local, comandos executados, resultado, versoes relevantes, migrations criadas, testes novos, capturas apenas quando ajudarem a provar o comportamento e divergencias conhecidas. Falhas devem bloquear o proximo sprint ou ser explicitamente autorizadas como excecao.

## Relatorio de encerramento

Usar exatamente as secoes exigidas no item 36 da especificacao-fonte. Resultados nao executados devem constar como `nao executado`, `parcial` ou `bloqueado`; nunca como aprovados por inferencia.
