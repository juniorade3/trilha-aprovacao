# Mapa do backend

## Entrada da aplicação

```text
aplicativos/backend/
├── pom.xml
└── src/
    ├── main/
    │   ├── java/br/com/trilhaaprovacao/
    │   └── resources/
    │       ├── application.yml
    │       └── db/migration/
    └── test/
```

## Padrão de fluxo

### Comando

```text
Controller
  -> DTO validado
  -> serviço/caso de uso
  -> entidade ou regra de domínio
  -> repositório/adaptador
  -> DTO de resposta
```

### Consulta agregada

```text
Controller ou ferramenta MCP
  -> consulta de aplicação
  -> JDBC/JPA filtrado por usuário
  -> resultado imutável
```

## Arquivos de referência

### Arquitetura

- `documentacao/arquitetura/ARQUITETURA-INICIAL.md`
- `documentacao/arquitetura/MODELO-DE-DOMINIO.md`
- `documentacao/decisoes/`

### Planejamento

- `planejamento/aplicacao/ServicoDePlanejamento.java`
- `planejamento/aplicacao/ServicoDeGeracaoDeterministica.java`
- `planejamento/aplicacao/ServicoDeReplanejamento.java`
- `planejamento/dominio/ConfiguracaoDaGeracaoDeterministica.java`

### Estudos

- `estudos/aplicacao/ServicoDeMateriaisEEstudos.java`
- tipos do módulo `evidencias`.

### Concurso

- `concursos/aplicacao/ServicoDaEstruturaDeConcursos.java`
- módulo `conteudoprogramatico`.

### MCP

- `automacao/infraestrutura/ConfiguracaoDoServidorMcp.java`
- `automacao/infraestrutura/ConfiguracaoDeSegurancaMcp.java`
- `automacao/infraestrutura/FiltroDeCredencialMcp.java`
- `automacao/infraestrutura/AutenticadorDeCredencialMcp.java`
- `automacao/infraestrutura/CatalogoDeFerramentasMcp.java`
- `automacao/aplicacao/ServicoDeConsultasMcp.java`
- `automacao/aplicacao/ServicoDePreparacoesMcp.java`
- `automacao/aplicacao/ServicoDeAplicacaoDeOperacoesAssistidas.java`
- `automacao/aplicacao/ServicoDeAuditoriaMcp.java`.

## Configuração

`application.yml` contém:

- datasource;
- JPA em modo de validação;
- Flyway;
- Swagger;
- Actuator;
- porta;
- sessão;
- feature flag da automação;
- segurança do Gateway;
- validade de código e credencial;
- hosts e origens permitidos no MCP.

## Qualidade

- Spring Boot Test;
- Spring Security Test;
- ArchUnit;
- Testcontainers PostgreSQL;
- testes MCP com cliente oficial;
- Maven `verify`.

## Heurística de leitura

Para alterar um fluxo:

1. leia o teste existente;
2. leia o ponto de entrada;
3. leia o caso de uso;
4. leia somente a entidade afetada;
5. leia o adaptador persistente;
6. leia a migration se houver banco;
7. altere e execute teste direcionado.
