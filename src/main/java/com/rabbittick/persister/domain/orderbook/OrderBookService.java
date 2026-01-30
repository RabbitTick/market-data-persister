package com.rabbittick.persister.domain.orderbook;

import com.rabbittick.persister.global.dto.MarketDataMessage;
import com.rabbittick.persister.global.dto.OrderBookPayload;

/**
 * 호가 저장 도메인 서비스 인터페이스.
 */
public interface OrderBookService {
	
	/**
	 * 호가 메시지를 저장한다.
	 *
	 * @param message 표준 시장 데이터 메시지
	 */
	void saveOrderBook(MarketDataMessage<OrderBookPayload> message);
}
