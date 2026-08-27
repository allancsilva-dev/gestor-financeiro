package com.gestor.financeiro.service.notificacao;

import com.gestor.financeiro.exception.BusinessException;
import com.gestor.financeiro.exception.ResourceNotFoundException;
import com.gestor.financeiro.model.NotificacaoDispositivo;
import com.gestor.financeiro.model.Usuario;
import com.gestor.financeiro.repository.NotificacaoDispositivoRepository;
import com.gestor.financeiro.repository.UsuarioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Registro e revogação de aparelho para push.
 *
 * <p>O token identifica o <b>aparelho</b>, não a pessoa: quando outra conta entra no mesmo
 * aparelho, o registro migra de titular em vez de duplicar — senão o dono anterior continuaria
 * recebendo aviso da própria vida financeira em um telefone que não é mais dele.</p>
 */
@Service
public class PushDispositivoService {

    private static final Pattern TOKEN = Pattern.compile("Expo(nent)?PushToken\\[[A-Za-z0-9._%+-]{1,150}]");
    private static final Set<String> PLATAFORMAS = Set.of("IOS", "ANDROID");

    private final NotificacaoDispositivoRepository dispositivos;
    private final UsuarioRepository usuarios;

    public PushDispositivoService(NotificacaoDispositivoRepository dispositivos, UsuarioRepository usuarios) {
        this.dispositivos = dispositivos;
        this.usuarios = usuarios;
    }

    @Transactional
    public NotificacaoDispositivo registrar(Long usuarioId, String pushToken, String plataforma) {
        String token = pushToken == null ? "" : pushToken.trim();
        if (!TOKEN.matcher(token).matches()) {
            throw new BusinessException("Token de push inválido");
        }
        String plataformaNormalizada = plataforma == null ? "" : plataforma.trim().toUpperCase(Locale.ROOT);
        if (!PLATAFORMAS.contains(plataformaNormalizada)) {
            throw new BusinessException("Plataforma inválida");
        }
        Usuario usuario = usuarios.findById(usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado"));

        NotificacaoDispositivo dispositivo = dispositivos.findByPushToken(token)
                .orElseGet(NotificacaoDispositivo::new);
        dispositivo.setUsuario(usuario);
        dispositivo.setPushToken(token);
        dispositivo.setPlataforma(plataformaNormalizada);
        dispositivo.setAtivo(true);
        return dispositivos.save(dispositivo);
    }

    /** Revogação é do titular dono do registro; token de outra pessoa responde 404. */
    @Transactional
    public void revogar(Long usuarioId, String pushToken) {
        String token = pushToken == null ? "" : pushToken.trim();
        NotificacaoDispositivo dispositivo = dispositivos.findByPushToken(token)
                .filter(d -> d.getUsuario().getId().equals(usuarioId))
                .orElseThrow(() -> new ResourceNotFoundException("Dispositivo não encontrado"));
        dispositivo.setAtivo(false);
        dispositivos.save(dispositivo);
    }

    /** Desativa token que o serviço de entrega recusou (aparelho apagou o app, por exemplo). */
    @Transactional
    public void desativarTokenNaoRegistrado(String pushToken) {
        dispositivos.findByPushToken(pushToken).ifPresent(dispositivo -> {
            dispositivo.setAtivo(false);
            dispositivos.save(dispositivo);
        });
    }

    @Transactional(readOnly = true)
    public List<NotificacaoDispositivo> ativosDoTitular(Long usuarioId) {
        return dispositivos.findByUsuarioIdAndAtivoTrue(usuarioId);
    }
}
