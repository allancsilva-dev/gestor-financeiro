package com.gestor.financeiro.service;

import lombok.RequiredArgsConstructor;
import com.gestor.financeiro.dto.OnboardingFinalizarRequest;
import com.gestor.financeiro.dto.UsuarioResponseDto;
import com.gestor.financeiro.exception.ResourceNotFoundException;
import com.gestor.financeiro.model.Carteira;
import com.gestor.financeiro.model.Categoria;
import com.gestor.financeiro.model.Conta;
import com.gestor.financeiro.model.ContaFixa;
import com.gestor.financeiro.model.Meta;
import com.gestor.financeiro.model.Usuario;
import com.gestor.financeiro.repository.CategoriaRepository;
import com.gestor.financeiro.repository.ContaFixaRepository;
import com.gestor.financeiro.repository.MetaRepository;
import com.gestor.financeiro.repository.UsuarioRepository;
import com.gestor.financeiro.security.AuthenticatedUserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class OnboardingService {
    private final AuthenticatedUserService authenticatedUserService;
    private final UsuarioRepository usuarioRepository;
    private final CategoriaRepository categoriaRepository;
    private final ContaFixaRepository contaFixaRepository;
    private final MetaRepository metaRepository;
    private final CarteiraService carteiraService;
    private final ContaService contaService;
    private final CategoriaService categoriaService;
    private final ContaFixaService contaFixaService;
    private final MetaService metaService;

    @Transactional
    public UsuarioResponseDto finalizar(OnboardingFinalizarRequest request) {
        Long usuarioId = authenticatedUserService.getAuthenticatedUserId();
        Usuario usuario = usuarioRepository.findById(usuarioId)
            .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado"));

        criarCarteiraSeNaoExistir(usuarioId, request.carteira());
        criarContaSeNaoExistir(usuarioId, request.conta());
        List<Categoria> categorias = criarCategoriasSeNaoExistirem(usuarioId, request.categorias());

        if (request.renda() != null) {
            criarRendaSeNaoExistir(usuarioId, request.renda(), categorias);
        }

        if (request.meta() != null) {
            criarMetaSeNaoExistir(usuarioId, request.meta());
        }

        usuario.setOnboardingCompleto(true);
        return UsuarioResponseDto.fromEntity(usuarioRepository.save(usuario));
    }

    private void criarCarteiraSeNaoExistir(Long usuarioId, OnboardingFinalizarRequest.CarteiraInicial request) {
        if (carteiraService.listarPorUsuario(usuarioId, org.springframework.data.domain.Pageable.unpaged())
            .stream()
            .anyMatch(c -> mesmoNome(c.getNome(), request.nome()))) {
            return;
        }

        Carteira carteira = new Carteira();
        carteira.setNome(request.nome());
        carteira.setTipo(request.tipo());
        carteira.setSaldo(request.saldo() == null ? BigDecimal.ZERO : request.saldo());
        carteira.setBanco(request.banco());
        carteiraService.criar(carteira, usuarioId);
    }

    private void criarContaSeNaoExistir(Long usuarioId, OnboardingFinalizarRequest.ContaInicial request) {
        if (contaService.listarPorUsuario(usuarioId, org.springframework.data.domain.Pageable.unpaged())
            .stream()
            .anyMatch(c -> mesmoNome(c.getNome(), request.nome()))) {
            return;
        }

        Conta conta = new Conta();
        conta.setNome(request.nome());
        conta.setTipo(request.tipo());
        conta.setLimiteTotal(request.limiteTotal());
        conta.setDiaFechamento(request.diaFechamento());
        conta.setDiaVencimento(request.diaVencimento());
        conta.setCor(request.cor());
        conta.setBanco(request.banco());
        contaService.criar(conta, usuarioId);
    }

    private List<Categoria> criarCategoriasSeNaoExistirem(
        Long usuarioId,
        List<OnboardingFinalizarRequest.CategoriaInicial> requests
    ) {
        List<Categoria> categorias = new ArrayList<>();

        for (OnboardingFinalizarRequest.CategoriaInicial request : requests) {
            Categoria categoria = categoriaRepository
                .findByUsuarioIdAndNomeIgnoreCase(usuarioId, request.nome())
                .orElseGet(() -> {
                    Categoria nova = new Categoria();
                    nova.setNome(request.nome());
                    nova.setCor(request.cor());
                    nova.setIcone(request.icone());
                    nova.setValorEsperado(request.valorEsperado());
                    return categoriaService.criar(nova);
                });
            categorias.add(categoria);
        }

        return categorias;
    }

    private void criarRendaSeNaoExistir(
        Long usuarioId,
        OnboardingFinalizarRequest.RendaInicial request,
        List<Categoria> categorias
    ) {
        if (contaFixaRepository.findByUsuarioIdAndAtivoTrue(usuarioId)
            .stream()
            .anyMatch(c -> mesmoNome(c.getNome(), request.nome()))) {
            return;
        }

        ContaFixa renda = new ContaFixa();
        renda.setNome(request.nome());
        renda.setValorPlanejado(request.valor());
        renda.setDiaVencimento(request.diaVencimento());
        renda.setCategoria(categorias.isEmpty() ? null : categorias.get(0));
        renda.setRecorrente(true);
        contaFixaService.criar(renda, usuarioId);
    }

    private void criarMetaSeNaoExistir(Long usuarioId, OnboardingFinalizarRequest.MetaInicial request) {
        if (metaRepository.findByUsuarioIdAndAtivaTrue(usuarioId)
            .stream()
            .anyMatch(m -> mesmoNome(m.getNome(), request.nome()))) {
            return;
        }

        Meta meta = new Meta();
        meta.setNome(request.nome());
        meta.setValorTotal(request.valorTotal());
        meta.setValorMensal(request.valorMensal());
        meta.setDataPrevista(request.dataLimite());
        meta.setCor(request.cor());
        meta.setIcone(request.icone());
        meta.setDescricao(request.descricao());
        metaService.criar(meta, usuarioId);
    }

    private boolean mesmoNome(String a, String b) {
        return a != null && b != null && a.trim().equalsIgnoreCase(b.trim());
    }
}
