# ADR-003 — Autenticacao por sessao

## Contexto

A aplicacao web usa frontend e backend no mesmo contexto local, e precisa proteger dados pessoais com fluxo simples de logout e CSRF.

## Decisao

Usar Spring Security, BCrypt e sessao HTTP. O cookie e HttpOnly, SameSite=Lax e recebe Secure no perfil de producao. Operacoes mutaveis exigem token CSRF.

## Consequencias

O frontend usa `credentials: include` e solicita o token CSRF. JWT, login social, segundo fator, recuperacao de senha e administracao de usuarios ficam fora do escopo.
