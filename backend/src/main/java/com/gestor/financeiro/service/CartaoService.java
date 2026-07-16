package com.gestor.financeiro.service;

import lombok.RequiredArgsConstructor;
import com.gestor.financeiro.exception.ResourceNotFoundException;
import com.gestor.financeiro.model.Carteira;
import com.gestor.financeiro.model.Conta;
import com.gestor.financeiro.model.Usuario;
import com.gestor.financeiro.model.enums.LiquidezContaFinanceira;
import com.gestor.financeiro.model.enums.NaturezaContaFinanceira;
import com.gestor.financeiro.model.enums.EstadoConciliacaoConta;
import com.gestor.financeiro.model.enums.OrigemDadosConta;
import com.gestor.financeiro.model.enums.SubtipoContaFinanceira;
import com.gestor.financeiro.repository.CarteiraRepository;
import com.gestor.financeiro.repository.ContaRepository;
import com.gestor.financeiro.repository.UsuarioRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;

/**
 * Servico canonico de cartao (PR-F2-19): substitui o antigo ContaService
 * generico. Conta e somente a configuracao interna do cartao; a divida vive no
 * ledger da conta financeira PASSIVO pareada. Sem pareamento e corrupcao:
 * nenhum fallback cria a conta financeira sob demanda.
 */
@Service
@RequiredArgsConstructor
public class CartaoService {
    private final ContaRepository contaRepository;
    private final UsuarioRepository usuarioRepository;
    private final CarteiraRepository carteiraRepository;

    public Page<Conta> listarCartoesPorUsuario(Long usuarioId, Pageable pageable) {
        return contaRepository.findByUsuarioIdAndAtivoTrue(usuarioId, pageable);
    }

    // Valida ownership para evitar IDOR em endpoints por ID.
    public Conta buscarCartaoDoUsuario(Long id, Long usuarioId) {
        return contaRepository.findByIdAndUsuarioId(id, usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Cartão não encontrado"));
    }

    // Cartao nasce pareado com sua conta financeira passiva (PR-F2-06)
    @Transactional
    public Conta criar(Conta cartao, Long usuarioId) {
        Usuario usuario = usuarioRepository.findById(usuarioId)
            .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado"));

        cartao.setUsuario(usuario);
        if (cartao.getAtivo() == null) cartao.setAtivo(true);
        if (cartao.getLimiteTotal() == null) cartao.setLimiteTotal(BigDecimal.ZERO);

        Carteira passivo = new Carteira();
        passivo.setNome(cartao.getNome());
        passivo.setSubtipo(SubtipoContaFinanceira.CARTAO);
        passivo.setNatureza(NaturezaContaFinanceira.PASSIVO);
        passivo.setLiquidez(LiquidezContaFinanceira.IMEDIATA);
        passivo.setSaldo(BigDecimal.ZERO);
        passivo.setMoeda("BRL");
        passivo.setOrigemDados(OrigemDadosConta.MANUAL);
        passivo.setEstadoConciliacao(EstadoConciliacaoConta.CONCILIADA);
        passivo.setBanco(cartao.getBanco());
        passivo.setUsuario(usuario);
        cartao.setContaFinanceira(carteiraRepository.save(passivo));

        return contaRepository.save(cartao);
    }

    @Transactional
    public Conta atualizarCartao(Long id, Conta cartaoAtualizado, Long usuarioId) {
        Conta cartao = buscarCartaoDoUsuario(id, usuarioId);
        cartao.setNome(cartaoAtualizado.getNome());
        cartao.setLimiteTotal(cartaoAtualizado.getLimiteTotal());
        cartao.setDiaFechamento(cartaoAtualizado.getDiaFechamento());
        cartao.setDiaVencimento(cartaoAtualizado.getDiaVencimento());
        cartao.setCor(cartaoAtualizado.getCor());
        cartao.setBanco(cartaoAtualizado.getBanco());
        cartao.getContaFinanceira().setNome(cartaoAtualizado.getNome());
        cartao.getContaFinanceira().setBanco(cartaoAtualizado.getBanco());
        return contaRepository.save(cartao);
    }

    @Transactional
    public void deletarCartao(Long id, Long usuarioId) {
        Conta cartao = buscarCartaoDoUsuario(id, usuarioId);
        cartao.setAtivo(false);
        contaRepository.save(cartao);
    }
}
