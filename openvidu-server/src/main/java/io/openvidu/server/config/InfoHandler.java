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

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.openvidu.client.internal.ProtocolElements;
import io.openvidu.server.rpc.RpcConnection;
import org.kurento.jsonrpc.message.Request;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

public class InfoHandler extends TextWebSocketHandler {
	
	private static final Logger log = LoggerFactory.getLogger(InfoHandler.class);
	
	Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
	Semaphore semaphore = new Semaphore(1);
	
	public void sendInfo(String info){
		for (WebSocketSession session : this.sessions.values()) {
			try {
				this.semaphore.acquire();
				session.sendMessage(new TextMessage(info));
				this.semaphore.release();
			} catch (IOException | InterruptedException e) {
				e.printStackTrace();
			}
		}
	}
	/**
	 * modify by jeffrey for userId support
	 * userId is client id 8bit
	 * 在握手的时候设置了Attributes属性
	 * @param session
	 * @throws Exception
	 */
	@Override
	public void afterConnectionEstablished(WebSocketSession session) throws Exception {
		log.info("Info websocket stablished...");
		Map<String, Object> attributes = session.getAttributes();
		log.info("user info:" + attributes);
		for (String key : attributes.keySet()) {
			log.info("key:" + key + " and value:" + attributes.get(key));
		}
		//this.sessions.put(session.getId(), session);
		this.sessions.put(attributes.get("userId").toString(), session);
	}

	/**
	 * modify by jeffrey
	 * @param session
	 * @param close
	 * @throws Exception
	 */
	@Override
	public void afterConnectionClosed(WebSocketSession session, CloseStatus close) throws Exception {
		log.info("Info websocket closed: " + close.getReason());
		//this.sessions.remove(session.getId());
		this.sessions.remove(session.getAttributes().get("userId"));
		session.close();
	}
	
	@Override
	protected void handleTextMessage(WebSocketSession session, TextMessage message)
			throws Exception {
		log.info("Message received: " + message.getPayload());
	}

	/**
	 * 例子为邀请4个人通话
	 */
	/*private void invited(RpcConnection rpcConnection, Request<JsonObject> request) {
        *//*{
            "id":1,
            "method":"invited",
            "params":{
                    "userId": "xxx",
                    "session": "AAA",
                    "type": “all”,
                    "number": 4,
                    "targets":[{"target_0":"dadasd","arget_1":"dadasd","arget_2":"dadasd","arget_3":"dadasd"}]

            },
            "jsonrpc":"2.0"
        }
        *//*
		String userId = getStringParam(request, ProtocolElements.INVITED_USER_PARAM);
		String sessionId = getStringParam(request, ProtocolElements.INVITED_ROOM_PARAM);
		int number = getIntParam(request, ProtocolElements.INVITED_NUMBER_PARAM);
		String targets = getStringParam(request, ProtocolElements.INVITED_TARGETS_PARAM);
		String mediaType = getStringParam(request, ProtocolElements.INVITED_MEDIA_TYPE_PARAM);
		*//**
		 * 首先判断这个target id是否在userIdAndPrivateId集合当中有
		 * 如果没有说明不在线需要返回,如果有则向目标发起通知,通知其加入房间
		 *//*
		if (number > 0) {
			JsonArray targetArray =
					new JsonParser().parse(targets).getAsJsonArray();
			for (int i= 0;i<targetArray.size();i++) {
				JsonObject object = targetArray.get(i).getAsJsonObject();
				String targetId =
						object.get("target_" + i).getAsString();
				log.info("targetId : {}",targetId);
			}
		}
	}*/
}
