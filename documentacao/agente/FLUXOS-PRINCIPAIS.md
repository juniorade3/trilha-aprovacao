# Fluxos principais

## 1. Sessão web

```text
Usuário
  -> login
  -> backend cria sessão
  -> frontend mantém cookies
  -> CSRF protege mutações
  -> rota protegida restaura ou limpa sessão
```

## 2. Cadastro de concurso

```text
Usuário
  -> concurso
  -> edital/cargo/provas/grupos
  -> matérias da prova
  -> itens oficiais
  -> mapeamentos para tópicos pessoais
```

## 3. Registro de estudo

```text
Usuário
  -> seleciona tópico/material
  -> informa tipo, duração e evidência
  -> caso de uso valida
  -> persiste registro
  -> dashboard, revisões e progresso refletem o fato
```

## 4. Planejamento semanal

```text
Disponibilidade
  + prioridades
  + conteúdo
  + revisões
  -> plano
  -> blocos
  -> execução
  -> conclusão/interrupção
```

## 5. Geração determinística

```text
Plano em rascunho
  -> coleta prioridades e restrições
  -> calcula prévia
  -> assina prévia
  -> usuário aplica
  -> preserva blocos manuais ou ajustados
```

## 6. Replanejamento

```text
Pendências
  + capacidade restante
  + limites
  -> prévia
  -> assinatura
  -> confirmação
  -> aplicação
```

## 7. Vínculo Telegram

```text
Aplicação web gera código
  -> usuário envia /conectar em DM
  -> plugin chama integrador
  -> integrador assina chamada HMAC
  -> backend consome código
  -> cria vínculo e credencial
  -> provisionador cria agente/sessão/workspace
  -> backend registra provisionamento
```

## 8. Consulta MCP

```text
Mensagem
  -> agente escolhe ferramenta
  -> proxy stdio
  -> broker injeta credenciais
  -> POST /mcp
  -> filtro autentica
  -> catálogo verifica escopo
  -> serviço consulta caso de uso
  -> resposta estruturada
  -> auditoria
```

## 9. Escrita assistida

```text
Mensagem
  -> ferramenta preparar_*
  -> prévia persistida
  -> código de confirmação
  -> usuário envia /confirmar
  -> plugin chama integrador confiável
  -> backend bloqueia operação
  -> valida vínculo, código, prazo, assinatura e versões
  -> aplica caso de uso em transação
  -> persiste recibo
```

## 10. Confirmação reforçada

```text
Operação crítica
  -> primeira confirmação
  -> backend não aplica
  -> gera segundo código
  -> mesma DM, sessão e vínculo
  -> segunda confirmação
  -> revalidação
  -> aplicação
```

## 11. Revogação

```text
Backend revoga vínculo/credencial
  -> script remove rota e arquivo externo
  -> workspace é arquivado
  -> Gateway reinicia
  -> chamadas antigas falham fechadas
```
