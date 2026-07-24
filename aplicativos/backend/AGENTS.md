# AGENTS.md — Backend

## Escopo

Estas instruções se aplicam a `aplicativos/backend/`.

## Tecnologias

- Java 21;
- Spring Boot;
- Spring MVC;
- Spring Security;
- Spring Data JPA;
- JDBC para consultas agregadas;
- PostgreSQL;
- Flyway;
- Testcontainers;
- ArchUnit;
- SDK MCP Java.

## Organização

Os módulos ficam sob:

```text
src/main/java/br/com/trilhaaprovacao/
```

Cada capacidade pode conter:

```text
api/
aplicacao/
dominio/
infraestrutura/
```

Use apenas as camadas necessárias. Não crie camadas ou interfaces sem uso concreto.

## Dependências permitidas

Fluxo principal:

```text
api -> aplicacao -> dominio
              \-> infraestrutura
```

Regras:

- `dominio` não depende de Spring, JPA, HTTP, JSON ou banco;
- `api` não acessa repositório diretamente;
- entidades persistidas não são DTOs de resposta;
- adaptadores JPA e detalhes técnicos ficam em `infraestrutura`;
- consultas JDBC de leitura ficam na aplicação ou infraestrutura conforme o padrão existente;
- o usuário autenticado deve participar de todas as consultas de dados pessoais.

## Banco

- Hibernate usa `ddl-auto=validate`;
- toda alteração estrutural precisa de migration Flyway;
- nunca edite migrations já aplicadas;
- use a próxima versão disponível;
- constraints e índices devem representar invariantes importantes;
- testes de integração devem executar PostgreSQL real por Testcontainers quando o comportamento depender do banco.

## Segurança

- web: sessão e CSRF;
- MCP: cadeia stateless separada;
- Gateway confiável: HMAC, timestamp, nonce e idempotência;
- não faça fallback entre autenticação MCP e sessão web;
- recurso de outro usuário deve aparecer como inexistente;
- não retorne stack trace, SQL, hash, token ou detalhes internos.

## Testes

Preferência:

1. teste unitário de domínio;
2. teste de aplicação;
3. integração MVC/MCP;
4. Testcontainers quando houver SQL, concorrência, migration ou transação;
5. ArchUnit para fronteiras.

Comandos:

```bash
./mvnw test
./mvnw verify
```

Para testes direcionados:

```bash
./mvnw -Dtest=NomeDoTeste test
```

## Pontos de entrada frequentes

| Tarefa | Começar em |
|---|---|
| Sessão e autenticação | `autenticacao/` |
| Cadastro de concurso | `concursos/aplicacao/ServicoDaEstruturaDeConcursos.java` |
| Conteúdo oficial | `conteudoprogramatico/` |
| Estudo e material | `estudos/aplicacao/ServicoDeMateriaisEEstudos.java` |
| Plano semanal | `planejamento/aplicacao/ServicoDePlanejamento.java` |
| Geração | `planejamento/aplicacao/ServicoDeGeracaoDeterministica.java` |
| Replanejamento | `planejamento/aplicacao/ServicoDeReplanejamento.java` |
| MCP | `automacao/infraestrutura/CatalogoDeFerramentasMcp.java` |
| Confirmação | `automacao/aplicacao/ServicoDeAplicacaoDeOperacoesAssistidas.java` |

## Não fazer

- regra de negócio no controller;
- SQL sem filtro de usuário;
- `findById` seguido de autorização tardia quando a consulta pode filtrar usuário;
- nova implementação de geração apenas para o MCP;
- resposta MCP com schema aberto;
- operação de escrita MCP que aplique diretamente;
- token em log;
- teste que use H2 para comportamento específico do PostgreSQL.
