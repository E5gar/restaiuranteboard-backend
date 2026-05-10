package com.restaiuranteboard.backend.repository.nosql;

import com.restaiuranteboard.backend.model.nosql.BackupAutomatizacion;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface BackupAutomatizacionRepository extends MongoRepository<BackupAutomatizacion, String> {
}
