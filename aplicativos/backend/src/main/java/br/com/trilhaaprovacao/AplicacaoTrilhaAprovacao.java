package br.com.trilhaaprovacao;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class AplicacaoTrilhaAprovacao {
    static {
        String debugDoAmbiente = System.getenv("DEBUG");
        if (debugDoAmbiente != null
                && !debugDoAmbiente.equalsIgnoreCase("true")
                && !debugDoAmbiente.equalsIgnoreCase("false")) {
            System.setProperty("debug", "false");
        }
    }

    public static void main(String[] argumentos) {
        SpringApplication.run(AplicacaoTrilhaAprovacao.class, argumentos);
    }
}
