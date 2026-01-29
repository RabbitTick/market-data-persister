package com.rabbittick.persister.domain.ticker;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Ticker 엔티티 저장소.
 */
public interface TickerRepository extends JpaRepository<Ticker, Long> {
}
