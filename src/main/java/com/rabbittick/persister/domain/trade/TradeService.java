package com.rabbittick.persister.domain.trade;

import com.rabbittick.persister.global.dto.MarketDataMessage;
import com.rabbittick.persister.global.dto.TradePayload;

/**
 * 거래 체결 저장 도메인 서비스 인터페이스.
 */
public interface TradeService {
	
	/**
	 * 거래 체결 메시지를 저장한다.
	 *
	 * @param message 표준 시장 데이터 메시지
	 */
	void saveTrade(MarketDataMessage<TradePayload> message);
}
