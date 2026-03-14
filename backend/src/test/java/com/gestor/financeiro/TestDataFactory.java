package com.gestor.financeiro;

import com.gestor.financeiro.model.Categoria;
import com.gestor.financeiro.model.Transacao;
import com.gestor.financeiro.model.Usuario;
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
