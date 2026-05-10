package com.restaiuranteboard.backend.repository.nosql;

import com.restaiuranteboard.backend.model.nosql.FrontendErrorLog;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface FrontendErrorLogRepository extends MongoRepository<FrontendErrorLog, String> {
}
