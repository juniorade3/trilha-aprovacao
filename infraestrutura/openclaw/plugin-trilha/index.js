import { createHash } from "node:crypto";

const URL_PADRAO_DO_INTEGRADOR = "http://integrador:8090";
const CAMINHO_DO_VINCULO = "/v1/vinculos/telegram";
const CAMINHO_DA_CONFIRMACAO = "/v1/operacoes/telegram/confirmacao";
const TEMPO_LIMITE_PADRAO_EM_MS = 5_000;
const CONTA_PADRAO = "default";
const CODIGO_DE_VINCULO_VALIDO = /^[23456789A-HJ-NP-Z]{10}$/;
const IDENTIFICADOR_NUMERICO_VALIDO = /^[1-9][0-9]{0,19}$/;
const IDENTIFICADOR_DA_CONTA_VALIDO = /^[a-z0-9][a-z0-9_-]{0,63}$/;
const IDENTIFICADOR_DA_OPERACAO_VALIDO =
  /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
const TIPO_DA_OPERACAO_VALIDO = /^[A-Z][A-Z0-9_]{0,79}$/;
const CODIGO_DE_CONFIRMACAO_VALIDO = /^[23456789A-HJ-NP-Z]{8}$/;
const INSTANTE_DA_APLICACAO_VALIDO =
  /^[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}(?:\.[0-9]{1,9})?Z$/;

const MENSAGENS = Object.freeze({
  uso: "Use /conectar seguido do codigo de 10 caracteres exibido na Trilha.",
  canal: "O vinculo com a Trilha esta disponivel somente pelo Telegram.",
  privado: "O vinculo so pode ser feito em uma conversa privada com o bot.",
  conta: "Nao foi possivel validar a conta deste bot do Telegram.",
  codigo: "O codigo informado nao possui o formato esperado. Gere outro codigo na Trilha e tente novamente.",
  sucesso: "Telegram vinculado a sua conta da Trilha. Agora voce ja pode consultar seus estudos por aqui.",
  recusado: "O codigo e invalido, expirou ou ja foi utilizado. Gere outro codigo na Trilha e tente novamente.",
  conflito: "Nao foi possivel usar esse codigo porque ele ja foi consumido ou este Telegram ja esta vinculado.",
  limite: "Foram feitas muitas tentativas. Aguarde um pouco antes de tentar novamente.",
  indisponivel: "Nao foi possivel confirmar o resultado do vinculo agora. Tente novamente em alguns instantes.",
  usoConfirmacao: "Use /confirmar seguido do codigo exibido na previa.",
  confirmacaoInvalida: "O codigo de confirmacao nao possui o formato esperado.",
  confirmacaoAplicada: "Operacao confirmada e aplicada na Trilha. Recibo: ",
  confirmacaoReforcada: "Primeira confirmacao aceita. Confirme novamente com: ",
  confirmacaoRecusada: "A operacao expirou, mudou ou o codigo nao confere. Solicite uma nova previa.",
  confirmacaoLimitada: "Foram feitas muitas tentativas de confirmacao. Aguarde um pouco antes de tentar novamente.",
  confirmacaoIndisponivel: "Nao foi possivel confirmar o resultado da operacao agora. Consulte a operacao antes de tentar novamente.",
  confirmacaoTempoEsgotado: "A confirmacao demorou mais que o esperado. Consulte a operacao antes de tentar novamente.",
  contextoDaConfirmacaoInvalido: "O contexto do comando de confirmacao nao e valido para esta conversa.",
  vinculoDaConfirmacaoNaoEncontrado: "O adaptador nao encontrou o vinculo ativo deste Telegram.",
});

function objeto(valor) {
  return valor !== null && typeof valor === "object" && !Array.isArray(valor);
}

function possuiChavesExatas(valor, esperadas) {
  if (!objeto(valor)) return false;
  const atuais = Object.keys(valor).sort();
  const ordenadas = [...esperadas].sort();
  return atuais.length === ordenadas.length
    && atuais.every((chave, indice) => chave === ordenadas[indice]);
}

function identificadorNumericoPositivo(valor) {
  if (typeof valor !== "string") return null;
  const normalizado = valor.trim();
  if (!IDENTIFICADOR_NUMERICO_VALIDO.test(normalizado)
      || !Number.isSafeInteger(Number(normalizado))) {
    return null;
  }
  return normalizado;
}

function lerConfiguracao(pluginConfig) {
  const configuracao = pluginConfig && typeof pluginConfig === "object"
    ? pluginConfig
    : {};
  const urlInformada = typeof configuracao.urlDoIntegrador === "string"
    ? configuracao.urlDoIntegrador.trim()
    : URL_PADRAO_DO_INTEGRADOR;
  const url = new URL(urlInformada || URL_PADRAO_DO_INTEGRADOR);
  if ((url.protocol !== "http:" && url.protocol !== "https:")
      || url.username || url.password || url.search || url.hash
      || (url.pathname !== "/" && url.pathname !== "")) {
    throw new Error("Configuracao urlDoIntegrador invalida.");
  }
  const tempoInformado = configuracao.tempoLimiteEmMs;
  const tempoLimiteEmMs = Number.isInteger(tempoInformado)
    ? tempoInformado
    : TEMPO_LIMITE_PADRAO_EM_MS;
  if (tempoLimiteEmMs < 1_000 || tempoLimiteEmMs > 15_000) {
    throw new Error("Configuracao tempoLimiteEmMs invalida.");
  }
  const contaInformada = configuracao.identificadorDaContaDoBot;
  const identificadorDaContaDoBot = contaInformada === undefined
    ? CONTA_PADRAO
    : typeof contaInformada === "string" ? contaInformada.trim() : "";
  if (!IDENTIFICADOR_DA_CONTA_VALIDO.test(identificadorDaContaDoBot)) {
    throw new Error("Configuracao identificadorDaContaDoBot invalida.");
  }
  return {
    url: new URL(CAMINHO_DO_VINCULO, url).toString(),
    urlDaConfirmacao: new URL(CAMINHO_DA_CONFIRMACAO, url).toString(),
    tempoLimiteEmMs,
    identificadorDaContaDoBot,
  };
}

function registrarSeguro(logger, nivel, mensagem) {
  const escrever = logger?.[nivel];
  if (typeof escrever === "function") {
    escrever.call(logger, `trilha-aprovacao: ${mensagem}`);
  }
}

function interpretarTextoDeConfirmacao(texto) {
  if (typeof texto !== "string") return { tipo: "IGNORAR" };
  const normalizado = texto.trim();
  if (!normalizado) return { tipo: "IGNORAR" };
  const codigoIsolado = normalizado.toUpperCase();
  if (CODIGO_DE_CONFIRMACAO_VALIDO.test(codigoIsolado)) {
    return { tipo: "CONFIRMAR", codigo: codigoIsolado };
  }
  if (!/^\/?confirmar(?:\s|$)/i.test(normalizado)) {
    return { tipo: "IGNORAR" };
  }
  const partes = normalizado.match(/^\/?confirmar\s+([^\s]+)$/i);
  const codigo = partes?.[1]?.toUpperCase() ?? "";
  return CODIGO_DE_CONFIRMACAO_VALIDO.test(codigo)
    ? { tipo: "CONFIRMAR", codigo }
    : { tipo: "INVALIDA" };
}

function chatDoEnderecoDoTelegram(endereco) {
  if (typeof endereco !== "string") return null;
  const resultado = endereco.match(/^telegram:([1-9][0-9]{0,19})$/);
  return resultado ? identificadorNumericoPositivo(resultado[1]) : null;
}

function contextoDiretoDoComando(contexto, configuracao, exigeAutorizacao) {
  if (contexto.channel !== "telegram") {
    return { valido: false, mensagem: MENSAGENS.canal, motivo: "canal" };
  }
  if (exigeAutorizacao && contexto.isAuthorizedSender !== true) {
    return {
      valido: false,
      mensagem: MENSAGENS.contextoDaConfirmacaoInvalido,
      motivo: "autorizacao",
    };
  }
  const identificadorDoTelegram = identificadorNumericoPositivo(
    typeof contexto.senderId === "string" ? contexto.senderId : "");
  const chatDaOrigem = chatDoEnderecoDoTelegram(contexto.from);
  const chatDoDestino = chatDoEnderecoDoTelegram(contexto.to);
  if (!identificadorDoTelegram || !chatDaOrigem
      || chatDaOrigem !== chatDoDestino) {
    return { valido: false, mensagem: MENSAGENS.privado, motivo: "conversa" };
  }
  const conta = typeof contexto.accountId === "string"
    ? contexto.accountId.trim() : "";
  if (!IDENTIFICADOR_DA_CONTA_VALIDO.test(conta)
      || conta !== configuracao.identificadorDaContaDoBot) {
    return {
      valido: false,
      mensagem: exigeAutorizacao
        ? MENSAGENS.contextoDaConfirmacaoInvalido : MENSAGENS.conta,
      motivo: "conta",
    };
  }
  return {
    valido: true,
    identificadorDoTelegram,
    identificadorDoChat: chatDaOrigem,
    identificadorDaContaDoBot: conta,
    identificadorDoUpdate: contexto.messageId,
  };
}

function contextoDiretoDoHook(evento, contexto, configuracao, origem) {
  const canal = evento.channel ?? contexto.channelId;
  if (canal !== "telegram") {
    return { valido: false, ignorar: true, motivo: "canal" };
  }
  const autorizacaoInformada = evento.commandAuthorized
    ?? contexto.commandAuthorized;
  const autorizado = origem === "before_dispatch"
      && autorizacaoInformada === undefined
    ? true
    : autorizacaoInformada === true;
  const identificadorDoTelegram = identificadorNumericoPositivo(
    typeof evento.senderId === "string"
      ? evento.senderId
      : typeof contexto.senderId === "string" ? contexto.senderId : "");
  const identificadorDoChat = identificadorNumericoPositivo(
    typeof evento.conversationId === "string"
      ? evento.conversationId
      : typeof contexto.conversationId === "string"
        ? contexto.conversationId : "");
  const conta = typeof evento.accountId === "string"
    ? evento.accountId.trim()
    : typeof contexto.accountId === "string" ? contexto.accountId.trim() : "";
  if (evento.isGroup !== false || !autorizado
      || !identificadorDoTelegram || !identificadorDoChat
      || !IDENTIFICADOR_DA_CONTA_VALIDO.test(conta)
      || conta !== configuracao.identificadorDaContaDoBot) {
    return {
      valido: false,
      mensagem: MENSAGENS.contextoDaConfirmacaoInvalido,
      motivo: !autorizado ? "autorizacao"
        : evento.isGroup !== false ? "conversa"
          : !identificadorDoTelegram || !identificadorDoChat
            ? "identidade" : "conta",
    };
  }
  return {
    valido: true,
    identificadorDoTelegram,
    identificadorDoChat,
    identificadorDaContaDoBot: conta,
    identificadorDoUpdate: evento.messageId ?? contexto.messageId,
  };
}

function identificadorDoUpdate(origem, codigo) {
  const mensagem = origem.identificadorDoUpdate;
  const mensagemNormalizada = typeof mensagem === "number"
    ? String(mensagem)
    : typeof mensagem === "string" ? mensagem.trim() : "";
  if (mensagemNormalizada.length > 0 && mensagemNormalizada.length <= 160
      && !/[\u0000-\u001f\u007f]/.test(mensagemNormalizada)) {
    return mensagemNormalizada;
  }
  const materialCanonico = JSON.stringify([
    "confirmacao-v1",
    origem.identificadorDaContaDoBot,
    origem.identificadorDoTelegram,
    origem.identificadorDoChat,
    codigo,
  ]);
  const hash = createHash("sha256").update(materialCanonico).digest("hex");
  return `confirmacao-${hash}`;
}

function validarReciboAplicado(dados) {
  if (!possuiChavesExatas(dados, ["codigo", "recibo"])
      || dados.codigo !== "OPERACAO_APLICADA"
      || !possuiChavesExatas(dados.recibo, [
        "identificadorDaOperacao", "tipo", "estado", "aplicadaEm", "resultado",
      ])) {
    return null;
  }
  const recibo = dados.recibo;
  if (typeof recibo.identificadorDaOperacao !== "string"
      || !IDENTIFICADOR_DA_OPERACAO_VALIDO.test(
        recibo.identificadorDaOperacao)
      || typeof recibo.tipo !== "string"
      || !TIPO_DA_OPERACAO_VALIDO.test(recibo.tipo)
      || recibo.estado !== "APLICADA"
      || typeof recibo.aplicadaEm !== "string"
      || !INSTANTE_DA_APLICACAO_VALIDO.test(recibo.aplicadaEm)
      || !Number.isFinite(Date.parse(recibo.aplicadaEm))
      || !possuiChavesExatas(recibo.resultado, ["tipo", "dados"])
      || recibo.resultado.tipo !== recibo.tipo
      || !Object.hasOwn(recibo.resultado, "dados")) {
    return null;
  }
  return recibo;
}

function validarNovaConfirmacao(dados) {
  if (!possuiChavesExatas(dados, [
    "codigo", "proximoCodigo", "proximaFrase",
  ]) || dados.codigo !== "NOVA_CONFIRMACAO_EXIGIDA"
      || typeof dados.proximoCodigo !== "string"
      || !CODIGO_DE_CONFIRMACAO_VALIDO.test(dados.proximoCodigo)
      || dados.proximaFrase !== `/confirmar ${dados.proximoCodigo}`) {
    return null;
  }
  return dados;
}

async function descartarCorpo(resposta) {
  try {
    await resposta.body?.cancel?.();
  } catch {
    // Corpo de erro nunca e necessario para a resposta do Telegram.
  }
}

function mensagemDaRecusaDaConfirmacao(status) {
  if (status === 400) return MENSAGENS.contextoDaConfirmacaoInvalido;
  if (status === 404) return MENSAGENS.vinculoDaConfirmacaoNaoEncontrado;
  if (status === 409 || status === 410 || status === 422) {
    return MENSAGENS.confirmacaoRecusada;
  }
  if (status === 429) return MENSAGENS.confirmacaoLimitada;
  return MENSAGENS.confirmacaoIndisponivel;
}

async function confirmarOperacao({
  buscar, configuracao, logger, origem, codigo,
}) {
  const controlador = new AbortController();
  const temporizador = setTimeout(
    () => controlador.abort(), configuracao.tempoLimiteEmMs);
  temporizador.unref?.();
  try {
    const resposta = await buscar(configuracao.urlDaConfirmacao, {
      method: "POST",
      headers: {
        accept: "application/json",
        "content-type": "application/json",
      },
      body: JSON.stringify({
        versaoDoContrato: "1",
        canal: "TELEGRAM",
        codigo,
        metodo: "TEXTO",
        identificadorDoTelegram: origem.identificadorDoTelegram,
        identificadorDoChat: origem.identificadorDoChat,
        identificadorDaContaDoBot: origem.identificadorDaContaDoBot,
        identificadorDoUpdate: identificadorDoUpdate(origem, codigo),
      }),
      redirect: "error",
      signal: controlador.signal,
    });
    if (resposta.status < 200 || resposta.status >= 300) {
      await descartarCorpo(resposta);
      registrarSeguro(logger, "warn",
        `confirmacao recusada status_http=${resposta.status}`);
      return mensagemDaRecusaDaConfirmacao(resposta.status);
    }
    let dados;
    try {
      dados = await resposta.json();
    } catch {
      dados = null;
    }
    const proxima = validarNovaConfirmacao(dados);
    if (proxima) {
      registrarSeguro(logger, "info", "confirmacao reforcada etapa=1");
      return MENSAGENS.confirmacaoReforcada + proxima.proximaFrase;
    }
    const recibo = validarReciboAplicado(dados);
    if (recibo) {
      registrarSeguro(logger, "info",
        `confirmacao aplicada operacao=${recibo.identificadorDaOperacao}`);
      return MENSAGENS.confirmacaoAplicada + recibo.identificadorDaOperacao;
    }
    registrarSeguro(logger, "warn",
      `confirmacao resposta_invalida status_http=${resposta.status}`);
    return MENSAGENS.confirmacaoIndisponivel;
  } catch {
    if (controlador.signal.aborted) {
      registrarSeguro(logger, "warn", "confirmacao timeout");
      return MENSAGENS.confirmacaoTempoEsgotado;
    }
    registrarSeguro(logger, "warn", "confirmacao indisponivel");
    return MENSAGENS.confirmacaoIndisponivel;
  } finally {
    clearTimeout(temporizador);
  }
}

function criarManipuladorDeConfirmacao({
  buscar, configuracao, logger,
}) {
  return async (contexto) => {
    const analise = interpretarTextoDeConfirmacao(
      `/confirmar ${typeof contexto.args === "string" ? contexto.args : ""}`);
    if (analise.tipo !== "CONFIRMAR") {
      return { text: contexto.args?.trim()
        ? MENSAGENS.confirmacaoInvalida : MENSAGENS.usoConfirmacao };
    }
    const origem = contextoDiretoDoComando(contexto, configuracao, true);
    if (!origem.valido) {
      registrarSeguro(logger, "warn",
        `confirmacao contexto_invalido motivo=${origem.motivo}`);
      return { text: origem.mensagem };
    }
    return { text: await confirmarOperacao({
      buscar, configuracao, logger, origem, codigo: analise.codigo,
    }) };
  };
}

function criarHookDeConfirmacao({
  buscar, configuracao, logger, origemDoHook,
}) {
  return async (evento, contexto = {}) => {
    const analise = interpretarTextoDeConfirmacao(evento.content);
    if (analise.tipo === "IGNORAR") return undefined;
    const adaptar = (texto) => origemDoHook === "inbound_claim"
      ? { handled: true, reply: { text: texto } }
      : { handled: true, text: texto };
    const origem = contextoDiretoDoHook(
      evento, contexto, configuracao, origemDoHook);
    if (origem.ignorar) return undefined;
    if (!origem.valido) {
      registrarSeguro(logger, "warn",
        `confirmacao contexto_invalido motivo=${origem.motivo}`);
      return adaptar(origem.mensagem);
    }
    if (analise.tipo === "INVALIDA") {
      registrarSeguro(logger, "warn", "confirmacao formato_invalido");
      return adaptar(MENSAGENS.confirmacaoInvalida);
    }
    const texto = await confirmarOperacao({
      buscar, configuracao, logger, origem, codigo: analise.codigo,
    });
    return adaptar(texto);
  };
}

function codigoDoContexto(contexto) {
  if (typeof contexto.args !== "string") return null;
  const codigo = contexto.args.trim().toUpperCase();
  return CODIGO_DE_VINCULO_VALIDO.test(codigo) ? codigo : null;
}

function vinculoConcluido(dados) {
  return possuiChavesExatas(dados, ["codigo"])
    && dados.codigo === "VINCULO_CONCLUIDO";
}

function mensagemParaStatus(status) {
  if (status === 400 || status === 404 || status === 410 || status === 422) {
    return MENSAGENS.recusado;
  }
  if (status === 409) return MENSAGENS.conflito;
  if (status === 429) return MENSAGENS.limite;
  return MENSAGENS.indisponivel;
}

function criarManipulador({ buscar, configuracao }) {
  return async (contexto) => {
    const origem = contextoDiretoDoComando(contexto, configuracao, false);
    if (!origem.valido) return { text: origem.mensagem };
    const codigo = codigoDoContexto(contexto);
    if (!codigo) {
      return { text: contexto.args?.trim() ? MENSAGENS.codigo : MENSAGENS.uso };
    }
    const controlador = new AbortController();
    const temporizador = setTimeout(
      () => controlador.abort(), configuracao.tempoLimiteEmMs);
    temporizador.unref?.();
    try {
      const resposta = await buscar(configuracao.url, {
        method: "POST",
        headers: {
          accept: "application/json",
          "content-type": "application/json",
        },
        body: JSON.stringify({
          versaoDoContrato: "1",
          canal: "TELEGRAM",
          codigoDeVinculo: codigo,
          identificadorDoTelegram: origem.identificadorDoTelegram,
          identificadorDoChat: origem.identificadorDoChat,
          identificadorDaContaDoBot: origem.identificadorDaContaDoBot,
        }),
        redirect: "error",
        signal: controlador.signal,
      });
      if (resposta.status >= 200 && resposta.status < 300) {
        let dados;
        try {
          dados = await resposta.json();
        } catch {
          dados = null;
        }
        return {
          text: vinculoConcluido(dados)
            ? MENSAGENS.sucesso : MENSAGENS.indisponivel,
        };
      }
      const mensagem = mensagemParaStatus(resposta.status);
      await descartarCorpo(resposta);
      return { text: mensagem };
    } catch {
      return { text: MENSAGENS.indisponivel };
    } finally {
      clearTimeout(temporizador);
    }
  };
}

export function criarPluginDaTrilha(dependencias = {}) {
  const buscar = dependencias.buscar ?? globalThis.fetch;
  if (typeof buscar !== "function") {
    throw new Error("Runtime sem suporte a fetch.");
  }
  return {
    id: "trilha-aprovacao",
    name: "Trilha da Aprovacao",
    description: "Vincula o Telegram e confirma operacoes da Trilha.",
    register(api) {
      const configuracao = lerConfiguracao(api.pluginConfig);
      const logger = api.logger;
      api.registerCommand({
        name: "conectar",
        description: "Vincular este Telegram a uma conta da Trilha.",
        channels: ["telegram"],
        acceptsArgs: true,
        requireAuth: false,
        handler: criarManipulador({ buscar, configuracao }),
      });
      api.registerCommand({
        name: "confirmar",
        description: "Confirmar e aplicar uma previa da Trilha.",
        channels: ["telegram"],
        acceptsArgs: true,
        requireAuth: true,
        handler: criarManipuladorDeConfirmacao({
          buscar, configuracao, logger,
        }),
      });
      if (typeof api.on !== "function") {
        throw new Error("Runtime OpenClaw sem suporte a hooks tipados.");
      }
      const tempoDoHook = Math.min(
        configuracao.tempoLimiteEmMs + 1_000, 16_000);
      api.on("inbound_claim", criarHookDeConfirmacao({
        buscar,
        configuracao,
        logger,
        origemDoHook: "inbound_claim",
      }), { priority: 100, timeoutMs: tempoDoHook });
      // Workaround OpenClaw 2026.7.1: inbound_claim global so e despachado
      // para bindings runtime pertencentes a plugin. before_dispatch cobre
      // bindings configurados e tambem termina antes de comandos ou modelo.
      api.on("before_dispatch", criarHookDeConfirmacao({
        buscar,
        configuracao,
        logger,
        origemDoHook: "before_dispatch",
      }), { priority: 100, timeoutMs: tempoDoHook });
    },
  };
}

export { interpretarTextoDeConfirmacao, MENSAGENS };
export default criarPluginDaTrilha();
