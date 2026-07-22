package br.com.trilhaaprovacao.automacao.infraestrutura;

import br.com.trilhaaprovacao.automacao.dominio.EventoDeAuditoriaDaAutomacao;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "eventos_de_auditoria_da_automacao")
public class EventoDeAuditoriaDaAutomacaoPersistido {
    @Id private UUID identificador;
    @Column(name = "usuario_id", nullable = false) private UUID identificadorDoUsuario;
    @Column(name = "vinculo_id") private UUID identificadorDoVinculo;
    @Column(name = "operacao_assistida_id") private UUID identificadorDaOperacao;
    @Column(nullable = false, length = 30) private String ator;
    @Column(length = 100) private String ferramenta;
    @Column(nullable = false, length = 100) private String acao;
    @Column(name = "hash_da_entrada", length = 128) private String hashDaEntrada;
    @Column(name = "hash_da_saida", length = 128) private String hashDaSaida;
    @Column(nullable = false, length = 80) private String fonte;
    @Column(nullable = false, length = 40) private String resultado;
    @Column(nullable = false) private UUID correlacao;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb") private String metadados;
    @Column(name = "ocorrido_em", nullable = false) private OffsetDateTime ocorridoEm;

    protected EventoDeAuditoriaDaAutomacaoPersistido() {
    }

    public EventoDeAuditoriaDaAutomacaoPersistido(EventoDeAuditoriaDaAutomacao evento) {
        identificador = evento.identificador();
        identificadorDoUsuario = evento.identificadorDoUsuario();
        identificadorDoVinculo = evento.identificadorDoVinculo();
        identificadorDaOperacao = evento.identificadorDaOperacao();
        ator = evento.ator();
        ferramenta = evento.ferramenta();
        acao = evento.acao();
        hashDaEntrada = evento.hashDaEntrada();
        hashDaSaida = evento.hashDaSaida();
        fonte = evento.fonte();
        resultado = evento.resultado();
        correlacao = evento.identificadorDeCorrelacao();
        metadados = evento.metadados();
        ocorridoEm = evento.ocorridoEm();
    }
}
