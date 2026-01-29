package com.rabbittick.persister.domain.trade;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.rabbittick.persister.global.dto.MarketDataMessage;
import com.rabbittick.persister.global.dto.TradePayload;

import lombok.RequiredArgsConstructor;

/**
 * 거래 체결 저장 도메인 서비스 구현체.
 *
 * 주요 책임:
 *
 * 메시지 -> 엔티티 변환 호출
 * 트랜잭션 내 저장 처리
 */
@Service
@RequiredArgsConstructor
public class TradeServiceImpl implements TradeService {
	
	private final TradeRepository tradeRepository;
	private final TradeMapper tradeMapper;

	/**
	 * 거래 체결 메시지를 저장한다.
	 *
	 * @param message 표준 시장 데이터 메시지
	 */
	@Override
	@Transactional
	public void saveTrade(MarketDataMessage<TradePayload> message) {
		Trade trade = tradeMapper.toEntity(message);
		tradeRepository.save(trade);
	}
}
