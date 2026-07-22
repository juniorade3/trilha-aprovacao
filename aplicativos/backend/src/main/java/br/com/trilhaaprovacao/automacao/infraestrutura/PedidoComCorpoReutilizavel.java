package br.com.trilhaaprovacao.automacao.infraestrutura;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

public final class PedidoComCorpoReutilizavel extends HttpServletRequestWrapper {
    private final byte[] corpo;

    private PedidoComCorpoReutilizavel(HttpServletRequest pedido, byte[] corpo) {
        super(pedido);
        this.corpo = corpo.clone();
    }

    public static PedidoComCorpoReutilizavel ler(HttpServletRequest pedido,
            int limiteEmBytes) throws IOException {
        byte[] lido = pedido.getInputStream().readNBytes(limiteEmBytes + 1);
        if (lido.length > limiteEmBytes) {
            throw new CorpoDoGatewayMuitoGrande();
        }
        return new PedidoComCorpoReutilizavel(pedido, lido);
    }

    public byte[] corpo() {
        return corpo.clone();
    }

    @Override
    public ServletInputStream getInputStream() {
        ByteArrayInputStream entrada = new ByteArrayInputStream(corpo);
        return new ServletInputStream() {
            @Override public boolean isFinished() { return entrada.available() == 0; }
            @Override public boolean isReady() { return true; }
            @Override public void setReadListener(ReadListener listener) {
                if (listener == null) {
                    throw new IllegalArgumentException("ReadListener e obrigatorio.");
                }
            }
            @Override public int read() { return entrada.read(); }
        };
    }

    @Override
    public BufferedReader getReader() {
        Charset charset = getCharacterEncoding() == null
                ? StandardCharsets.UTF_8 : Charset.forName(getCharacterEncoding());
        return new BufferedReader(new InputStreamReader(getInputStream(), charset));
    }

    @Override public int getContentLength() { return corpo.length; }
    @Override public long getContentLengthLong() { return corpo.length; }

    public static final class CorpoDoGatewayMuitoGrande extends RuntimeException {
        CorpoDoGatewayMuitoGrande() {
            super("Corpo da requisicao excede o limite permitido.");
        }
    }
}
