package com.restaiuranteboard.backend.repository.nosql;

import com.restaiuranteboard.backend.model.nosql.ShoppingCart;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface ShoppingCartRepository extends MongoRepository<ShoppingCart, String> {
}
