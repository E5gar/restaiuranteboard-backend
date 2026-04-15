package com.restaiuranteboard.backend.repository.nosql;

import com.restaiuranteboard.backend.model.nosql.ConfiguracionSistema;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.Optional;

public interface ConfiguracionSistemaRepository extends MongoRepository<ConfiguracionSistema, String> {
    Optional<ConfiguracionSistema> findById(String id);
}