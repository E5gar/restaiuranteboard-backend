package com.restaiuranteboard.backend.model.nosql;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Document(collection = "configuracion_sistema")
public class ConfiguracionSistema {
    @Id
    private String id;
    private String emailSmtp;
    private String passwordSmtp;
    private String nombreNegocio;
    private String logoBase64;
    private String telefonoNegocio;
    private String terminosCondiciones;
    private boolean configuracionCompleta = false;
}