package com.restaiuranteboard.backend.repository.sql;

import com.restaiuranteboard.backend.model.sql.RestaurantOrder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RestaurantOrderRepository extends JpaRepository<RestaurantOrder, UUID> {

    List<RestaurantOrder> findByStatusOrderByCreatedAtDesc(String status);
}
