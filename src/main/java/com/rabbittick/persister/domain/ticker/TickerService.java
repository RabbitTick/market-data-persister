package com.rabbittick.persister.domain.ticker;

import com.rabbittick.persister.global.dto.MarketDataMessage;
import com.rabbittick.persister.global.dto.TickerPayload;

/**
 * 티커 저장 도메인 서비스 인터페이스.
 */
public interface TickerService {
	
	/**
	 * 티커 메시지를 저장한다.
	 *
	 * @param message 표준 시장 데이터 메시지
	 */
	void saveTicker(MarketDataMessage<TickerPayload> message);
}
