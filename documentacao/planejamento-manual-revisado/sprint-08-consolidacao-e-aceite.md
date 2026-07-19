# Sprint 08 — Consolidação, refinamento e aceite integral

> Ler primeiro: `CONTEXTO-COMUM.md`.

## 1. Objetivo

Consolidar a jornada completa do Planejamento Manual Estruturado, eliminar inconsistências comprovadas e produzir evidências executadas de qualidade.

## 2. Valor entregue

O módulo fica pronto para uso cotidiano e fornece uma base confiável para o desenvolvimento posterior do Motor de Evidências.

## 3. Dependências

Sprints 01 a 07 concluídas.

## 4. Escopo

- revisar contratos, estados e transições;
- validar migrations em PostgreSQL vazio;
- validar isolamento A/B e CSRF;
- revisar consultas de Hoje e Semana;
- eliminar N+1 evidente;
- consolidar estados carregando, vazio, erro e conflito;
- revisar acessibilidade e navegação por teclado;
- validar 390, 768 e 1280 px;
- confirmar navegação Planejamento, Hoje, Semana e Histórico;
- revisar integração com registro rápido, Histórico e dashboard;
- revisar Swagger/OpenAPI;
- documentar operação do módulo;
- executar fluxo completo de aceite;
- corrigir apenas falhas comprovadas;
- produzir relatório factual.

## 5. Fora do escopo

- nova funcionalidade;
- Motor de Evidências;
- indicadores de aderência;
- geração automática;
- recomendações;
- refatoração ampla sem falha comprovada;
- nova biblioteca de UI;
- framework E2E novo;
- deploy.

## 6. Regras de negócio

Nenhuma regra nova. Qualquer mudança deve corrigir ambiguidade ou falha comprovada e ser registrada em `CONTEXTO-COMUM.md` ou ADR quando arquitetural.

## 7. Modelo de domínio

Revisar:

- domínio sem dependência de framework;
- métodos de transição em vez de setters públicos;
- estados usados por comportamento real;
- invariantes cobertas por testes;
- concorrência otimista;
- uma execução por bloco;
- uma execução aberta por usuário;
- ausência de cascata destrutiva;
- relação com estudos somente por serviço de aplicação e identificador.

## 8. Backend

- conferir DTOs e contrato de erro;
- confirmar `201` com `Location` nas criações;
- confirmar `400`, `404`, `409` e `422` conforme o contrato existente;
- testar todos os endpoints com sessão e CSRF;
- provar isolamento A/B em plano, disponibilidade, bloco e execução;
- validar V6 em diante em banco vazio;
- conferir índices e constraints;
- executar testes de arquitetura;
- inspecionar consultas Hoje/Semana e evitar carregamento de todo o catálogo;
- confirmar idempotência da finalização e do vínculo com estudo;
- confirmar rollback quando a integração com estudos falha;
- confirmar correlação em erros inesperados.

## 9. Frontend

- jornada coerente entre Hoje, Semana e Histórico;
- item principal Planejamento funcionando no desktop e mobile;
- ações principais visíveis;
- ausência de `window.confirm`;
- foco devolvido após modal/gaveta;
- fechamento por `Escape`;
- labels, mensagens e `role=status`;
- estados vazios úteis;
- conflito 409 com ação de recarregar;
- cronômetro recuperado após refresh;
- sem dados simulados;
- sem rolagem horizontal obrigatória;
- sem quebrar registro rápido, dashboard, conteúdos, materiais e concursos.

## 10. Contrato da API

Não criar rotas novas nesta sprint.

Consolidar e conferir no Swagger:

- planos semanais;
- disponibilidades;
- blocos;
- ativação;
- visão Hoje;
- início, conclusão e interrupção;
- vínculo com estudos;
- edição, reagendamento e cancelamento;
- correção de execução;
- encerramento e cancelamento do plano.

## 11. Fluxo principal

1. Criar uma semana.
2. Informar disponibilidade.
3. Criar bloco livre e bloco com tópico.
4. Reordenar.
5. Tentar ativar com excesso e corrigir.
6. Ativar.
7. Abrir Hoje.
8. Iniciar bloco.
9. Recarregar a página.
10. Concluir e conferir o Histórico.
11. Iniciar outro bloco e concluir parcialmente.
12. Reagendar bloco pendente.
13. Corrigir execução e conferir correção do estudo.
14. Encerrar semana com bloco não realizado.
15. Entrar como usuário B e tentar acessar identificadores de A.

## 12. Critérios de aceite

- Dado banco vazio, quando iniciar a aplicação, então todas as migrations sobem e Hibernate valida o esquema.
- Dado fluxo completo, quando executado, então não ocorre erro inesperado.
- Dado usuário B, quando usar qualquer identificador de A, então não lê nem altera os dados.
- Dado refresh durante execução, então a execução é recuperada.
- Dado repetição da finalização, então não cria estudo duplicado.
- Dado correção de execução integrada, então o Histórico mantém rastreabilidade.
- Dado conflito de versão, então a interface explica e permite recarregar.
- Dado navegação por teclado, então todas as ações são alcançáveis e o foco é visível.
- Dado 390, 768 e 1280 px, então Hoje e Semana permanecem utilizáveis.
- Dado Swagger, então endpoints, estados e códigos correspondem à implementação.
- Dado porta de qualidade, então nenhum resultado é declarado aprovado sem execução.

## 13. Testes obrigatórios

- toda a suíte backend;
- toda a suíte frontend;
- migrations em PostgreSQL vazio;
- testes de arquitetura;
- testes de segurança, sessão, CSRF e A/B;
- concorrência de início de bloco;
- idempotência de conclusão;
- integração transacional com estudos;
- componentes, roteamento e layout;
- responsividade e teclado;
- regressões em autenticação, conteúdos, concursos, estudos e dashboard;
- fluxo manual completo.

## 14. Arquivos provavelmente afetados

- testes e documentação do módulo;
- configuração OpenAPI;
- consultas e índices apontados pela validação;
- páginas Hoje/Semana e componentes locais;
- layout/roteamento apenas para correções comprovadas;
- arquivos indicados por falhas executadas.

Não alterar Makefile ou workflow se os comandos atuais já cobrirem a capacidade.

## 15. Ordem de implementação

- [ ] inventariar critérios de aceite;
- [ ] executar portas antes de alterar;
- [ ] validar banco vazio;
- [ ] validar contratos e isolamento;
- [ ] executar fluxo completo;
- [ ] testar acessibilidade e responsividade;
- [ ] corrigir falhas uma a uma;
- [ ] repetir portas;
- [ ] atualizar documentação;
- [ ] emitir relatório factual e registro.

## 16. Validação final

Comandos reais do projeto:

```bash
docker compose config
make infra-subir
make testar-backend
make testar-frontend
make verificar-backend
make verificar-frontend
make verificar
git diff --check
```

Também validar:

- `http://localhost:8080/v3/api-docs`;
- `http://localhost:8080/swagger-ui.html`;
- frontend em `http://localhost:5173`;
- fluxo manual completo;
- encerramento de processos temporários ao final.

Registrar cada resultado como:

```text
APROVADO
PARCIAL
NÃO EXECUTADO
BLOQUEADO
```

Nunca declarar aprovado por inferência.

## 17. Registro de conclusão

```text
STATUS:
ARQUIVOS ALTERADOS:
DECISÕES TOMADAS:
TESTES EXECUTADOS:
PENDÊNCIAS:
PRÓXIMA SPRINT: iniciar o planejamento do Motor de Evidências
```
