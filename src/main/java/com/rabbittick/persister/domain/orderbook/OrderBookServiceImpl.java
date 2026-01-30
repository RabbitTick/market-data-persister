package com.rabbittick.persister.domain.orderbook;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.rabbittick.persister.global.dto.MarketDataMessage;
import com.rabbittick.persister.global.dto.OrderBookPayload;

import lombok.RequiredArgsConstructor;

/**
 * 호가 저장 도메인 서비스 구현체.
 *
 * 주요 책임:
 *
 * 메시지 -> 엔티티 변환 호출
 * 트랜잭션 내 저장 처리
 */
@Service
@RequiredArgsConstructor
public class OrderBookServiceImpl implements OrderBookService {
	
	private final OrderBookRepository orderBookRepository;
	private final OrderBookMapper orderBookMapper;

	/**
	 * 호가 메시지를 저장한다.
	 *
	 * @param message 표준 시장 데이터 메시지
	 */
	@Override
	@Transactional
	public void saveOrderBook(MarketDataMessage<OrderBookPayload> message) {
		OrderBook orderBook = orderBookMapper.toEntity(message);
		orderBookRepository.save(orderBook);
	}
}
