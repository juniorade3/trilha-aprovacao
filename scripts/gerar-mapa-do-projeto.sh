#!/usr/bin/env bash
set -euo pipefail

RAIZ="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SAIDA="${1:-$RAIZ/documentacao/agente/MAPA-SIMBOLOS-GERADO.md}"
TEMPORARIO="$(mktemp)"
trap 'rm -f "$TEMPORARIO"' EXIT

command -v rg >/dev/null 2>&1 || {
  echo "Erro: ripgrep (rg) é obrigatório." >&2
  exit 1
}

{
  echo "# Mapa de símbolos gerado"
  echo
  echo "> Gerado automaticamente. Não editar manualmente."
  echo
  echo "Gerado em: $(date -u '+%Y-%m-%dT%H:%M:%SZ')"
  echo

  echo "## Backend Java"
  echo
  while IFS= read -r arquivo; do
    relativo="${arquivo#"$RAIZ/"}"
    echo "### \`$relativo\`"
    echo
    echo '```text'
    rg --no-heading --line-number \
      '^[[:space:]]*(public[[:space:]]+)?(class|interface|record|enum)[[:space:]]+[A-Za-z0-9_]+|^[[:space:]]*public[[:space:]]+([A-Za-z0-9_<>, ?\[\]]+[[:space:]]+)+[A-Za-z0-9_]+[[:space:]]*\(' \
      "$arquivo" \
      | sed -E 's/^[0-9]+://' \
      | sed -E 's/[[:space:]]+\{[[:space:]]*$//' \
      | head -80 || true
    echo '```'
    echo
  done < <(
    find "$RAIZ/aplicativos/backend/src/main/java" \
      -type f -name '*.java' -print | sort
  )

  echo "## Frontend TypeScript e Vue"
  echo
  while IFS= read -r arquivo; do
    relativo="${arquivo#"$RAIZ/"}"
    echo "### \`$relativo\`"
    echo
    echo '```text'
    rg --no-heading --line-number \
      '^(export[[:space:]]+)?(async[[:space:]]+)?function[[:space:]]+[A-Za-z0-9_]+|^(export[[:space:]]+)?(const|class|interface|type)[[:space:]]+[A-Za-z0-9_]+|defineProps|defineEmits|defineStore' \
      "$arquivo" \
      | sed -E 's/^[0-9]+://' \
      | head -60 || true
    echo '```'
    echo
  done < <(
    find "$RAIZ/aplicativos/frontend/src" \
      -type f \( -name '*.ts' -o -name '*.vue' \) -print | sort
  )
} > "$TEMPORARIO"

install -d "$(dirname "$SAIDA")"
mv "$TEMPORARIO" "$SAIDA"
echo "Mapa gerado em: $SAIDA"
