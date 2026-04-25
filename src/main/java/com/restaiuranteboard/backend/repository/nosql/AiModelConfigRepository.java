package com.restaiuranteboard.backend.repository.nosql;

import com.restaiuranteboard.backend.model.nosql.AiModelConfig;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface AiModelConfigRepository extends MongoRepository<AiModelConfig, String> {
}
