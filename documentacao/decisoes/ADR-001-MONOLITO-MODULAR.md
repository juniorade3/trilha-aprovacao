# ADR-001 — Monolito modular

## Contexto

As capacidades compartilham usuario, sessao, transacoes e consultas de dashboard. A primeira versao precisa ser simples de executar localmente e verificavel de ponta a ponta.

## Decisao

Adotar um monolito modular Java, organizado por capacidade e com dominio independente de infraestrutura.

## Consequencias

As regras ficam proximas de seus casos de uso e a aplicacao usa uma unica unidade de deploy. Integracoes distribuidas, mensageria e consistencia eventual nao sao introduzidas prematuramente.
