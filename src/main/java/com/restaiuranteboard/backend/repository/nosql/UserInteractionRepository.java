package com.restaiuranteboard.backend.repository.nosql;

import com.restaiuranteboard.backend.model.nosql.UserInteraction;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface UserInteractionRepository extends MongoRepository<UserInteraction, String> {
}
