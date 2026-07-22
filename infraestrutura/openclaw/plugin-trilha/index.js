const URL_PADRAO_DO_INTEGRADOR = "http://integrador:8090";
const CAMINHO_DO_VINCULO = "/v1/vinculos/telegram";
const TEMPO_LIMITE_PADRAO_EM_MS = 5_000;
const CODIGO_DE_VINCULO_VALIDO = /^[23456789A-HJ-NP-Z]{10}$/;
const IDENTIFICADOR_DO_TELEGRAM_VALIDO = /^[1-9][0-9]{0,19}$/;
const IDENTIFICADOR_DA_CONTA_VALIDO = /^[A-Za-z0-9._:-]{1,100}$/;

const MENSAGENS = Object.freeze({
  uso: "Use /conectar seguido do codigo de 10 caracteres exibido na Trilha.",
  canal: "O vinculo com a Trilha esta disponivel somente pelo Telegram.",
  privado: "O vinculo so pode ser feito em uma conversa privada com o bot.",
  codigo: "O codigo informado nao possui o formato esperado. Gere outro codigo na Trilha e tente novamente.",
  sucesso: "Telegram vinculado a sua conta da Trilha. Agora voce ja pode consultar seus estudos por aqui.",
  recusado: "O codigo e invalido, expirou ou ja foi utilizado. Gere outro codigo na Trilha e tente novamente.",
  conflito: "Nao foi possivel usar esse codigo porque ele ja foi consumido ou este Telegram ja esta vinculado.",
  limite: "Foram feitas muitas tentativas. Aguarde um pouco antes de tentar novamente.",
  indisponivel: "Nao foi possivel confirmar o resultado do vinculo agora. Tente novamente em alguns instantes.",
});

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
  return {
    url: new URL(CAMINHO_DO_VINCULO, url).toString(),
    tempoLimiteEmMs,
  };
}

function contextoPrivadoDoTelegram(contexto) {
  if (contexto.channel !== "telegram") {
    return { valido: false, mensagem: MENSAGENS.canal };
  }
  const identificador = typeof contexto.senderId === "string"
    ? contexto.senderId.trim()
    : "";
  if (!IDENTIFICADOR_DO_TELEGRAM_VALIDO.test(identificador)) {
    return { valido: false, mensagem: MENSAGENS.privado };
  }
  const enderecoEsperado = `telegram:${identificador}`;
  if (contexto.from !== enderecoEsperado || contexto.to !== enderecoEsperado) {
    return { valido: false, mensagem: MENSAGENS.privado };
  }
  return { valido: true, identificador };
}

function codigoDoContexto(contexto) {
  if (typeof contexto.args !== "string") {
    return null;
  }
  const codigo = contexto.args.trim().toUpperCase();
  return CODIGO_DE_VINCULO_VALIDO.test(codigo) ? codigo : null;
}

function mensagemParaStatus(status) {
  if (status >= 200 && status < 300) {
    return MENSAGENS.sucesso;
  }
  if (status === 400 || status === 404 || status === 410 || status === 422) {
    return MENSAGENS.recusado;
  }
  if (status === 409) {
    return MENSAGENS.conflito;
  }
  if (status === 429) {
    return MENSAGENS.limite;
  }
  return MENSAGENS.indisponivel;
}

async function descartarCorpo(resposta) {
  try {
    await resposta.body?.cancel?.();
  } catch {
    // O corpo do integrador nunca e necessario para a resposta do Telegram.
  }
}

function criarManipulador({ buscar, configuracao }) {
  return async (contexto) => {
    const origem = contextoPrivadoDoTelegram(contexto);
    if (!origem.valido) {
      return { text: origem.mensagem };
    }
    const codigo = codigoDoContexto(contexto);
    if (!codigo) {
      return { text: contexto.args?.trim() ? MENSAGENS.codigo : MENSAGENS.uso };
    }

    const identificadorDaConta = typeof contexto.accountId === "string"
        && IDENTIFICADOR_DA_CONTA_VALIDO.test(contexto.accountId.trim())
      ? contexto.accountId.trim()
      : "default";
    const controlador = new AbortController();
    const temporizador = setTimeout(
      () => controlador.abort(), configuracao.tempoLimiteEmMs);
    temporizador.unref?.();
    try {
      const resposta = await buscar(configuracao.url, {
        method: "POST",
        headers: {
          "accept": "application/json",
          "content-type": "application/json",
        },
        body: JSON.stringify({
          versaoDoContrato: "1",
          canal: "TELEGRAM",
          codigoDeVinculo: codigo,
          identificadorDoTelegram: origem.identificador,
          identificadorDoChat: origem.identificador,
          identificadorDaContaDoBot: identificadorDaConta,
        }),
        redirect: "error",
        signal: controlador.signal,
      });
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
    description: "Vincula uma conversa privada do Telegram a Trilha.",
    register(api) {
      const configuracao = lerConfiguracao(api.pluginConfig);
      api.registerCommand({
        name: "conectar",
        description: "Vincular este Telegram a uma conta da Trilha.",
        channels: ["telegram"],
        acceptsArgs: true,
        requireAuth: false,
        handler: criarManipulador({ buscar, configuracao }),
      });
    },
  };
}

export { MENSAGENS };
export default criarPluginDaTrilha();
