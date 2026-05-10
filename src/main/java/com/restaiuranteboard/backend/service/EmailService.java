package com.restaiuranteboard.backend.service;

import com.restaiuranteboard.backend.model.nosql.ConfiguracionSistema;
import com.restaiuranteboard.backend.model.sql.User;
import com.restaiuranteboard.backend.repository.nosql.ConfiguracionSistemaRepository;
import com.restaiuranteboard.backend.repository.sql.UserRepository;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    public enum TipoCodigoCorreo {
        SETUP_SMTP,
        REGISTRO_USUARIO,
        ACTIVACION_EMPLEADO,
        RECUPERACION_PASSWORD
    }

    private final GithubEmailDispatchService githubEmailDispatchService;
    private final ConfiguracionSistemaRepository configRepository;
    private final UserRepository userRepository;

    public EmailService(
            GithubEmailDispatchService githubEmailDispatchService,
            ConfiguracionSistemaRepository configRepository,
            UserRepository userRepository) {
        this.githubEmailDispatchService = githubEmailDispatchService;
        this.configRepository = configRepository;
        this.userRepository = userRepository;
    }

    public void enviarCodigoVerificacion(
            String destino,
            String codigo,
            String emisor,
            String passwordSmtp,
            TipoCodigoCorreo tipo,
            String nombreNegocio,
            String notifyUserId
    ) {
        if (destino != null && usuarioRebotado(destino)) {
            throw new IllegalStateException("No se puede enviar correo a esta direcci?n.");
        }
        assertSmtpConfigPermiteEnvio(tipo);

        String negocio = (nombreNegocio == null || nombreNegocio.isBlank())
                ? "Restaiuranteboard" : nombreNegocio.trim();
        String subject;
        String body;

        switch (tipo) {
            case SETUP_SMTP -> {
                subject = "C?digo de verificaci?n SMTP - " + negocio;
                body = "Est?s validando el correo SMTP de " + negocio + ".\n\n"
                        + "C?digo: " + codigo + "\n"
                        + "Este c?digo expira en 1 minuto.";
            }
            case REGISTRO_USUARIO -> {
                subject = "C?digo de registro de cuenta - " + negocio;
                body = "Recibimos una solicitud de registro en " + negocio + ".\n\n"
                        + "C?digo de verificaci?n: " + codigo + "\n"
                        + "Este c?digo expira en 1 minuto.\n"
                        + "Si no realizaste esta acci?n, ignora este mensaje.";
            }
            case ACTIVACION_EMPLEADO -> {
                subject = "C?digo para activar tu cuenta de personal - " + negocio;
                body = "Tu cuenta de personal fue creada y requiere activaci?n.\n\n"
                        + "C?digo de activaci?n: " + codigo + "\n"
                        + "Este c?digo expira en 1 minuto.";
            }
            case RECUPERACION_PASSWORD -> {
                subject = "C?digo para restablecer contrase?a - " + negocio;
                body = "Recibimos una solicitud para restablecer tu contrase?a.\n\n"
                        + "C?digo de recuperaci?n: " + codigo + "\n"
                        + "Este c?digo expira en 1 minuto.\n"
                        + "Si no solicitaste este cambio, ignora este correo.";
            }
            default -> {
                subject = "C?digo de verificaci?n - " + negocio;
                body = "Tu c?digo de verificaci?n es: " + codigo
                        + "\nEste c?digo expira en 1 minuto.";
            }
        }

        githubEmailDispatchService.dispatchPlainEmail(
                destino,
                emisor,
                passwordSmtp,
                subject,
                body,
                notifyUserId
        );
    }

    public void enviarCorreoTextoPlano(
            String destino,
            String asunto,
            String cuerpo,
            String emisor,
            String passwordSmtp,
            String notifyUserId
    ) {
        if (destino == null || destino.isBlank() || emisor == null || emisor.isBlank()
                || passwordSmtp == null || passwordSmtp.isBlank()) {
            return;
        }
        if (usuarioRebotado(destino)) {
            return;
        }
        ConfiguracionSistema cfg = configRepository.findById("GLOBAL_CONFIG").orElse(null);
        if (cfg != null && cfg.isSmtpCredentialsInvalid()) {
            return;
        }

        githubEmailDispatchService.dispatchPlainEmail(
                destino,
                emisor,
                passwordSmtp,
                asunto,
                cuerpo,
                notifyUserId
        );
    }

    private void assertSmtpConfigPermiteEnvio(TipoCodigoCorreo tipo) {
        if (tipo == TipoCodigoCorreo.SETUP_SMTP) {
            return;
        }
        ConfiguracionSistema c = configRepository.findById("GLOBAL_CONFIG").orElse(null);
        if (c != null && c.isSmtpCredentialsInvalid()) {
            throw new IllegalStateException("Correo del sistema no disponible. Revisa la configuraci?n SMTP.");
        }
    }

    private boolean usuarioRebotado(String email) {
        return userRepository.findByEmailIgnoreCase(email.trim())
                .map(User::isEmailBounced)
                .orElse(false);
    }
}
