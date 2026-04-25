package com.restaiuranteboard.backend.repository.nosql;

import com.restaiuranteboard.backend.model.nosql.UserInteraction;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface UserInteractionRepository extends MongoRepository<UserInteraction, String> {
    List<UserInteraction> findTop100ByUserIdOrderByTimestampDesc(String userId);
}
