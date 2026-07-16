package com.gestor.financeiro;

import com.gestor.financeiro.model.Carteira;
import com.gestor.financeiro.model.Categoria;
import com.gestor.financeiro.model.Conta;
import com.gestor.financeiro.model.Transacao;
import com.gestor.financeiro.model.Usuario;
import com.gestor.financeiro.model.enums.NaturezaContaFinanceira;
import com.gestor.financeiro.model.enums.SubtipoContaFinanceira;
import com.gestor.financeiro.model.enums.TipoTransacao;

import java.math.BigDecimal;
import java.time.LocalDate;

public final class TestDataFactory {

    private TestDataFactory() {
    }

    public static Usuario usuario(String nome, String email, String senhaCriptografada) {
        Usuario usuario = new Usuario();
        usuario.setNome(nome);
        usuario.setEmail(email);
        usuario.setSenha(senhaCriptografada);
        return usuario;
    }

    public static Categoria categoria(Usuario usuario, String nome) {
        Categoria categoria = new Categoria();
        categoria.setUsuario(usuario);
        categoria.setNome(nome);
        categoria.setCor("#FF5733");
        categoria.setIcone("shopping-cart");
        categoria.setValorEsperado(BigDecimal.ZERO);
        categoria.setValorGasto(BigDecimal.ZERO);
        categoria.setAtivo(true);
        return categoria;
    }

    /**
     * Configuracao de cartao (contract V41): toda conta e cartao pareado com
     * sua conta financeira PASSIVO. A carteira passiva deve ser persistida
     * antes da conta.
     */
    public static Conta cartao(Usuario usuario, String nome, Carteira contaFinanceira) {
        Conta cartao = new Conta();
        cartao.setUsuario(usuario);
        cartao.setNome(nome);
        cartao.setLimiteTotal(BigDecimal.valueOf(5000));
        cartao.setDiaFechamento(1);
        cartao.setDiaVencimento(10);
        cartao.setAtivo(true);
        cartao.setContaFinanceira(contaFinanceira);
        return cartao;
    }

    public static Carteira contaPassivaCartao(Usuario usuario, String nome) {
        Carteira passivo = new Carteira();
        passivo.setUsuario(usuario);
        passivo.setNome(nome);
        passivo.setSubtipo(SubtipoContaFinanceira.CARTAO);
        passivo.setNatureza(NaturezaContaFinanceira.PASSIVO);
        passivo.setSaldo(BigDecimal.ZERO);
        return passivo;
    }

    public static Carteira carteira(Usuario usuario, String nome, BigDecimal saldo) {
        Carteira carteira = new Carteira();
        carteira.setUsuario(usuario);
        carteira.setNome(nome);
        carteira.setSubtipo(SubtipoContaFinanceira.CORRENTE);
        carteira.setSaldo(saldo);
        return carteira;
    }

    public static Transacao transacao(Usuario usuario, Categoria categoria, String descricao, BigDecimal valor) {
        Transacao transacao = new Transacao();
        transacao.setUsuario(usuario);
        transacao.setCategoria(categoria);
        transacao.setDescricao(descricao);
        transacao.setValorTotal(valor);
        transacao.setTipo(TipoTransacao.SAIDA);
        transacao.setData(LocalDate.now());
        transacao.setParcelado(false);
        transacao.setRecorrente(false);
        return transacao;
    }
}
