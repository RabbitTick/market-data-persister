package com.rabbittick.persister.domain.orderbook;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * OrderBook 엔티티 저장소.
 */
public interface OrderBookRepository extends JpaRepository<OrderBook, Long> {
}
