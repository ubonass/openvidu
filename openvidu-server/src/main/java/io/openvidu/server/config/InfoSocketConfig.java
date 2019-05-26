/*
 * (C) Copyright 2017-2019 OpenVidu (https://openvidu.io/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package io.openvidu.server.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;

import java.util.Map;

@Configuration
@EnableWebSocket
public class InfoSocketConfig implements WebSocketConfigurer {
	private Logger logger = LoggerFactory.getLogger(this.getClass());
	@Override
	public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
		registry.addHandler(infoHandler(), "/info").setAllowedOrigins("*")
				.addInterceptors(new InfoHandshakeInterceptor());
	}

	@Bean
	public InfoHandler infoHandler() {
		return new InfoHandler();
	}

	@Bean
	public ServletServerContainerFactoryBean createWebSocketContainer() {
		ServletServerContainerFactoryBean container = new ServletServerContainerFactoryBean();
		container.setMaxTextMessageBufferSize(50 * 1024);
		return container;
	}

	/**
	 * 在握手之前执行该方法, 继续握手返回true, 中断握手返回false.
	 * 通过map参数设置WebSocketSession的属性
	 * 然后在info handler中可以将信息取出来
	 */

	class InfoHandshakeInterceptor implements HandshakeInterceptor {

		@Override
		public boolean beforeHandshake(ServerHttpRequest serverHttpRequest,
									   ServerHttpResponse serverHttpResponse,
									   WebSocketHandler webSocketHandler, Map<String, Object> map)
				throws Exception {
			if (serverHttpRequest instanceof ServletServerHttpRequest) {
				String userId = ((ServletServerHttpRequest) serverHttpRequest)
						.getServletRequest().getParameter("userId");
				logger.info("user " + userId + " 尝试连接.......");
				map.put("userId", userId);
			}
			return true;
		}

		@Override
		public void afterHandshake(ServerHttpRequest serverHttpRequest,
								   ServerHttpResponse serverHttpResponse,
								   WebSocketHandler webSocketHandler, Exception e) {

		}
	}
}
