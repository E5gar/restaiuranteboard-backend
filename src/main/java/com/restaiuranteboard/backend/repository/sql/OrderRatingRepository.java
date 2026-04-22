package com.restaiuranteboard.backend.repository.sql;

import com.restaiuranteboard.backend.model.sql.OrderRating;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface OrderRatingRepository extends JpaRepository<OrderRating, Long> {

    boolean existsByOrder_Id(UUID orderId);
}
