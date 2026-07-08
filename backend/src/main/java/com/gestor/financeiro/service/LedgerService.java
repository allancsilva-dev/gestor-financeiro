package com.gestor.financeiro.service;

import com.gestor.financeiro.exception.BusinessException;
import com.gestor.financeiro.exception.ResourceNotFoundException;
import com.gestor.financeiro.model.Carteira;
import com.gestor.financeiro.model.MovimentoCarteira;
import com.gestor.financeiro.repository.CarteiraRepository;
import com.gestor.financeiro.repository.MovimentoCarteiraRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
public class LedgerService {

    private final CarteiraRepository carteiraRepository;
    private final MovimentoCarteiraRepository movimentoCarteiraRepository;

    public LedgerService(CarteiraRepository carteiraRepository,
                         MovimentoCarteiraRepository movimentoCarteiraRepository) {
        this.carteiraRepository = carteiraRepository;
        this.movimentoCarteiraRepository = movimentoCarteiraRepository;
    }

    @Transactional
    public MovimentoCarteira registrarMovimento(RegistrarMovimentoCommand command) {
        validarCommand(command);

        if (hasText(command.idempotencyKey())) {
            return movimentoCarteiraRepository
                    .findByUsuarioIdAndIdempotencyKey(command.usuarioId(), command.idempotencyKey())
                    .orElseGet(() -> registrarNovoMovimento(command));
        }

        return registrarNovoMovimento(command);
    }

    private MovimentoCarteira registrarNovoMovimento(RegistrarMovimentoCommand command) {
        Carteira carteira = carteiraRepository
                .findByIdAndUsuarioIdForUpdate(command.carteiraId(), command.usuarioId())
                .orElseThrow(() -> new ResourceNotFoundException("Carteira não encontrada"));

        BigDecimal valorAssinado = calcularValorAssinado(command);
        BigDecimal saldoAtual = carteira.getSaldo() == null ? BigDecimal.ZERO : carteira.getSaldo();
        BigDecimal novoSaldo = saldoAtual.add(valorAssinado);

        if (!command.permitirSaldoNegativo() && novoSaldo.signum() < 0) {
            throw new BusinessException("Saldo insuficiente");
        }

        carteira.setSaldo(novoSaldo);
        carteiraRepository.save(carteira);

        MovimentoCarteira movimento = criarMovimento(command, carteira, valorAssinado, novoSaldo);
        return movimentoCarteiraRepository.save(movimento);
    }

    private static MovimentoCarteira criarMovimento(RegistrarMovimentoCommand command,
                                                    Carteira carteira,
                                                    BigDecimal valorAssinado,
                                                    BigDecimal saldoResultante) {
        MovimentoCarteira movimento = new MovimentoCarteira();
        movimento.setUsuario(carteira.getUsuario());
        movimento.setCarteira(carteira);
        movimento.setTipo(command.tipo());
        movimento.setValor(command.valor());
        movimento.setValorAssinado(valorAssinado);
        movimento.setOrigem(command.origem());
        movimento.setReferenciaTipo(command.referenciaTipo());
        movimento.setReferenciaId(command.referenciaId());
        movimento.setDescricao(command.descricao());
        movimento.setDataMovimento(command.dataMovimento());
        movimento.setSaldoResultante(saldoResultante);
        movimento.setIdempotencyKey(command.idempotencyKey());
        return movimento;
    }

    private static BigDecimal calcularValorAssinado(RegistrarMovimentoCommand command) {
        return switch (command.direcao()) {
            case ENTRADA -> command.valor();
            case SAIDA -> command.valor().negate();
        };
    }

    private static void validarCommand(RegistrarMovimentoCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("command é obrigatório");
        }
        if (command.usuarioId() == null || command.carteiraId() == null) {
            throw new IllegalArgumentException("usuarioId e carteiraId são obrigatórios");
        }
        if (command.tipo() == null || command.origem() == null || command.direcao() == null) {
            throw new IllegalArgumentException("tipo, origem e direção são obrigatórios");
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
