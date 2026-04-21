package com.rabbittick.persister.domain.orderbook;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Orderbook 엔티티 저장소.
 */
public interface OrderbookRepository extends JpaRepository<Orderbook, Long> {
}
