package com.rabbittick.persister.domain.ticker;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.rabbittick.persister.global.dto.MarketDataMessage;
import com.rabbittick.persister.global.dto.TickerPayload;

import lombok.RequiredArgsConstructor;

/**
 * 티커 저장 도메인 서비스 구현체.
 *
 * 주요 책임:
 *
 * 메시지 -> 엔티티 변환 호출
 * 트랜잭션 내 저장 처리
 */
@Service
@RequiredArgsConstructor
public class TickerServiceImpl implements TickerService {
	
	private final TickerRepository tickerRepository;
	private final TickerMapper tickerMapper;

	/**
	 * 티커 메시지를 저장한다.
	 *
	 * @param message 표준 시장 데이터 메시지
	 */
	@Override
	@Transactional
	public void saveTicker(MarketDataMessage<TickerPayload> message) {
		Ticker ticker = tickerMapper.toEntity(message);
		tickerRepository.save(ticker);
	}
}
