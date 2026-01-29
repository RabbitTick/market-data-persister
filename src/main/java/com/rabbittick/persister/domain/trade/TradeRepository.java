package com.rabbittick.persister.domain.trade;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Trade 엔티티 저장소.
 */
public interface TradeRepository extends JpaRepository<Trade, Long> {
}
