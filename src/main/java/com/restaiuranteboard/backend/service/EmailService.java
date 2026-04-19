package com.restaiuranteboard.backend.service;

import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Service;
import java.util.Properties;

@Service
public class EmailService {

    public enum TipoCodigoCorreo {
        SETUP_SMTP,
        REGISTRO_USUARIO,
        ACTIVACION_EMPLEADO,
        RECUPERACION_PASSWORD
    }

    public void enviarCodigoVerificacion(
            String destino,
            String codigo,
            String emisor,
            String passwordSmtp,
            TipoCodigoCorreo tipo,
            String nombreNegocio
    ) {
        JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
        mailSender.setHost("smtp.gmail.com");
        mailSender.setPort(587);
        mailSender.setUsername(emisor);
        mailSender.setPassword(passwordSmtp);

        Properties props = mailSender.getJavaMailProperties();
        props.put("mail.transport.protocol", "smtp");
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");

        String negocio = (nombreNegocio == null || nombreNegocio.isBlank()) ? "Restaiuranteboard" : nombreNegocio.trim();
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
                body = "Tu código de verificación es: " + codigo + "\nEste código expira en 1 minuto.";
            }
        }

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(emisor);
        message.setTo(destino);
        message.setSubject(subject);
        message.setText(body);

        mailSender.send(message);
    }

    public void enviarCorreoTextoPlano(
            String destino,
            String asunto,
            String cuerpo,
            String emisor,
            String passwordSmtp
    ) {
        if (destino == null || destino.isBlank() || emisor == null || emisor.isBlank()
                || passwordSmtp == null || passwordSmtp.isBlank()) {
            return;
        }
        JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
        mailSender.setHost("smtp.gmail.com");
        mailSender.setPort(587);
        mailSender.setUsername(emisor);
        mailSender.setPassword(passwordSmtp);
        java.util.Properties props = mailSender.getJavaMailProperties();
        props.put("mail.transport.protocol", "smtp");
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(emisor);
        message.setTo(destino);
        message.setSubject(asunto);
        message.setText(cuerpo);
        mailSender.send(message);
    }
}