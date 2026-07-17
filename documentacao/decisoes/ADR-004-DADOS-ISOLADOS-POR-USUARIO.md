# ADR-004 — Dados isolados por usuario

## Contexto

Materias sao reutilizaveis para varios concursos de uma pessoa, mas nao formam um catalogo coletivo. Expor identificadores livres de usuario em comandos permitiria acesso indevido.

## Decisao

Resolver o Usuario autenticado pela sessao em todo caso de uso e filtrar/validar cada recurso por sua propriedade. Comandos de negocio nao aceitam `identificadorDoUsuario` livre.

## Consequencias

Consultas e alteracoes exigem escopo de usuario, inclusive em relacoes indiretas. Testes de seguranca devem provar que o Usuario A nao le nem muda dados do Usuario B.
