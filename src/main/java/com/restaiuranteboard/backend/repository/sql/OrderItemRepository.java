package com.restaiuranteboard.backend.repository.sql;

import com.restaiuranteboard.backend.model.sql.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;

public interface OrderItemRepository extends JpaRepository<OrderItem, Integer> {

    @Query("""
            SELECT CASE WHEN COUNT(oi) > 0 THEN true ELSE false END
            FROM OrderItem oi
            JOIN oi.restaurantOrder o
            WHERE oi.mongoProductId = :mongoId
            AND o.status IN :statuses
            """)
    boolean existsByMongoProductIdAndOrderStatusIn(
            @Param("mongoId") String mongoProductId,
            @Param("statuses") Collection<String> statuses);
}
