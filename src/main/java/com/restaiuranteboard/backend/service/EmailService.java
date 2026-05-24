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
            throw new IllegalStateException("No se puede enviar correo a esta dirección.");
        }
        assertSmtpConfigPermiteEnvio(tipo);

        String negocio = (nombreNegocio == null || nombreNegocio.isBlank())
                ? "Restaiuranteboard" : nombreNegocio.trim();
        String subject;
        String body;

        switch (tipo) {
            case SETUP_SMTP -> {
                subject = "Código de verificación SMTP - " + negocio;
                body = "Estás validando el correo SMTP de " + negocio + ".\n\n"
                        + "Código: " + codigo + "\n"
                        + "Este código expira en 1 minuto.";
            }
            case REGISTRO_USUARIO -> {
                subject = "Código de registro de cuenta - " + negocio;
                body = "Recibimos una solicitud de registro en " + negocio + ".\n\n"
                        + "Código de verificación: " + codigo + "\n"
                        + "Este código expira en 1 minuto.\n"
                        + "Si no realizaste esta acción, ignora este mensaje.";
            }
            case ACTIVACION_EMPLEADO -> {
                subject = "Código para activar tu cuenta de personal - " + negocio;
                body = "Tu cuenta de personal fue creada y requiere activación.\n\n"
                        + "Código de activación: " + codigo + "\n"
                        + "Este código expira en 1 minuto.";
            }
            case RECUPERACION_PASSWORD -> {
                subject = "Código para restablecer contraseña - " + negocio;
                body = "Recibimos una solicitud para restablecer tu contraseña.\n\n"
                        + "Código de recuperación: " + codigo + "\n"
                        + "Este código expira en 1 minuto.\n"
                        + "Si no solicitaste este cambio, ignora este correo.";
            }
            default -> {
                subject = "Código de verificación - " + negocio;
                body = "Tu código de verificación es: " + codigo
                        + "\nEste código expira en 1 minuto.";
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

    public void enviarCorreoPersonalAdministrador(
            String destino,
            String cuerpo,
            String notifyUserId
    ) {
        if (destino == null || destino.isBlank()) {
            throw new IllegalArgumentException("Destinatario vacío.");
        }
        if (cuerpo == null || cuerpo.isBlank()) {
            throw new IllegalArgumentException("Mensaje vacío.");
        }
        if (usuarioRebotado(destino)) {
            throw new IllegalStateException("No se puede enviar correo a esta dirección.");
        }
        ConfiguracionSistema cfg = configRepository.findById("GLOBAL_CONFIG").orElse(null);
        if (cfg == null || cfg.getEmailSmtp() == null || cfg.getEmailSmtp().isBlank()
                || cfg.getPasswordSmtp() == null || cfg.getPasswordSmtp().isBlank()) {
            throw new IllegalStateException("SMTP no configurado.");
        }
        if (cfg.isSmtpCredentialsInvalid()) {
            throw new IllegalStateException("Correo del sistema no disponible. Revisa la configuración SMTP.");
        }
        String negocio = (cfg.getNombreNegocio() == null || cfg.getNombreNegocio().isBlank())
                ? "Restaiuranteboard" : cfg.getNombreNegocio().trim();
        String subject = "Mensaje de Administrador de " + negocio;
        githubEmailDispatchService.dispatchPlainEmail(
                destino.trim(),
                cfg.getEmailSmtp().trim(),
                cfg.getPasswordSmtp(),
                subject,
                cuerpo.trim(),
                notifyUserId
        );
    }

    public void enviarConfirmacionTicketSoporte(
            String destino,
            String ticketCode,
            String categoriaLabel,
            String notifyUserId
    ) {
        ConfiguracionSistema cfg = requerirConfigCorreo();
        String negocio = nombreNegocio(cfg);
        String subject = "Recepcion de reporte - " + negocio;
        String body = "Hola,\n\n"
                + "Recibimos tu reporte de atencion al cliente.\n\n"
                + "Ticket: " + ticketCode + "\n"
                + "Tipo: " + categoriaLabel + "\n\n"
                + "Nuestro equipo revisara tu caso y se pondra en contacto contigo a la brevedad.\n\n"
                + "Gracias por escribirnos.\n"
                + negocio;
        dispatchSeguro(destino, cfg, subject, body, notifyUserId);
    }

    public void enviarCierreTicketSoporte(
            String destino,
            String ticketCode,
            String veredicto,
            String notifyUserId
    ) {
        ConfiguracionSistema cfg = requerirConfigCorreo();
        String negocio = nombreNegocio(cfg);
        String subject = "Ticket cerrado - " + negocio;
        String body = "Hola,\n\n"
                + "Tu ticket de atencion " + ticketCode + " fue cerrado.\n\n"
                + "Resolucion:\n" + veredicto + "\n\n"
                + negocio;
        dispatchSeguro(destino, cfg, subject, body, notifyUserId);
    }

    private ConfiguracionSistema requerirConfigCorreo() {
        ConfiguracionSistema cfg = configRepository.findById("GLOBAL_CONFIG").orElse(null);
        if (cfg == null || cfg.getEmailSmtp() == null || cfg.getEmailSmtp().isBlank()
                || cfg.getPasswordSmtp() == null || cfg.getPasswordSmtp().isBlank()) {
            throw new IllegalStateException("SMTP no configurado.");
        }
        if (cfg.isSmtpCredentialsInvalid()) {
            throw new IllegalStateException("Correo del sistema no disponible.");
        }
        return cfg;
    }

    private static String nombreNegocio(ConfiguracionSistema cfg) {
        String n = cfg.getNombreNegocio();
        return (n == null || n.isBlank()) ? "Restaiuranteboard" : n.trim();
    }

    private void dispatchSeguro(
            String destino,
            ConfiguracionSistema cfg,
            String subject,
            String body,
            String notifyUserId
    ) {
        if (destino == null || destino.isBlank()) {
            return;
        }
        if (usuarioRebotado(destino)) {
            return;
        }
        githubEmailDispatchService.dispatchPlainEmail(
                destino.trim(),
                cfg.getEmailSmtp().trim(),
                cfg.getPasswordSmtp(),
                subject,
                body,
                notifyUserId
        );
    }

    private void assertSmtpConfigPermiteEnvio(TipoCodigoCorreo tipo) {
        if (tipo == TipoCodigoCorreo.SETUP_SMTP) {
            return;
        }
        ConfiguracionSistema c = configRepository.findById("GLOBAL_CONFIG").orElse(null);
        if (c != null && c.isSmtpCredentialsInvalid()) {
            throw new IllegalStateException("Correo del sistema no disponible. Revisa la configuración SMTP.");
        }
    }

    private boolean usuarioRebotado(String email) {
        return userRepository.findByEmailIgnoreCase(email.trim())
                .map(User::isEmailBounced)
                .orElse(false);
    }
}
