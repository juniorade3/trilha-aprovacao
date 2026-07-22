#!/usr/bin/env node

import { createHmac } from "node:crypto";
import { readFileSync } from "node:fs";

const [arquivoDoSegredo, arquivoDoCanonico] = process.argv.slice(2);
if (!arquivoDoSegredo || !arquivoDoCanonico) {
  process.stderr.write("Uso: assinar-gateway.mjs ARQUIVO_DO_SEGREDO ARQUIVO_CANONICO\n");
  process.exit(2);
}

const segredo = readFileSync(arquivoDoSegredo, "utf8").trim();
if (segredo.length < 32 || segredo.length > 4096) {
  process.stderr.write("O segredo do Gateway deve ter entre 32 e 4096 caracteres.\n");
  process.exit(2);
}

const canonico = readFileSync(arquivoDoCanonico);
const assinatura = createHmac("sha256", Buffer.from(segredo, "utf8"))
  .update(canonico)
  .digest("hex");

process.stdout.write(assinatura);
