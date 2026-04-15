package com.restaiuranteboard.backend.service;

import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Service;
import java.util.Properties;

@Service
public class EmailService {

    public void enviarCodigoVerificacion(String destino, String codigo, String emisor, String passwordSmtp) {
        JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
        mailSender.setHost("smtp.gmail.com");
        mailSender.setPort(587);
        mailSender.setUsername(emisor);
        mailSender.setPassword(passwordSmtp);

        Properties props = mailSender.getJavaMailProperties();
        props.put("mail.transport.protocol", "smtp");
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(emisor);
        message.setTo(destino);
        message.setSubject("Código de Verificación - Restaiuranteboard");
        message.setText("Tu código de verificación para configurar el sistema es: " + codigo + 
                        "\nEste código expirará en 1 minuto.");

        mailSender.send(message);
    }
}