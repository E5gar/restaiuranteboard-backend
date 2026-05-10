package com.restaiuranteboard.backend.repository.nosql;

import com.restaiuranteboard.backend.model.nosql.EmailDispatchLog;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface EmailDispatchLogRepository extends MongoRepository<EmailDispatchLog, String> {
}
