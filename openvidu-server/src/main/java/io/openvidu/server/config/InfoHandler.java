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

import com.google.gson.*;
import io.openvidu.client.internal.ProtocolElements;
import io.openvidu.server.rpc.RpcConnection;
import org.kurento.client.IceCandidate;
import org.kurento.jsonrpc.message.Request;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

public class InfoHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(InfoHandler.class);

    private static final Gson gson = new GsonBuilder().create();

    Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    Semaphore semaphore = new Semaphore(1);

    public void sendInfo(String info) {
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
     * 自定义函数
     * 发送消息给指定的在线用户
     */
    public void sendMessageToUser(String userId, TextMessage message) {
        try {
            if (sessions.containsKey(userId)) {
                WebSocketSession session = sessions.get(userId);
                session.sendMessage(message);
            }
        } catch (IOException e) {
            e.printStackTrace();
            log.error(e.getLocalizedMessage());
        }
    }

    /**
     * modify by jeffrey for userId support
     * userId is client id 8bit
     * 在握手的时候设置了Attributes属性
     *
     * @param session
     * @throws Exception
     */
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        log.info("Info websocket stablished...");
        Map<String, Object> attributes = session.getAttributes();
        log.info("user info:" + attributes);
        /*for (String key : attributes.keySet()) {
            log.info("key:" + key + " and value:" + attributes.get(key));
        }*/
        //this.sessions.put(session.getId(), session);
        this.sessions.put(attributes.get("userId").toString(), session);
    }

    /**
     * modify by jeffrey
     *
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
        JsonObject jsonMessage = gson.fromJson(message.getPayload(), JsonObject.class);
        log.info("Message received: " + jsonMessage);
        switch (jsonMessage.get("method").getAsString()) {
            case ProtocolElements.INVITED_METHOD:
                try {
                    invited(session, jsonMessage);
                } catch (Throwable t) {
                    handleErrorResponse(t, session, ProtocolElements.INVITED_METHOD);
                }
                break;
            default:
                break;
        }
    }

    private void handleErrorResponse(Throwable throwable, WebSocketSession session, String responseId)
            throws IOException {
        log.error(throwable.getMessage(), throwable);
        JsonObject response = new JsonObject();
        response.addProperty("method", responseId);
        response.addProperty("response", "rejected");
        response.addProperty("message", throwable.getMessage());
        session.sendMessage(new TextMessage(response.toString()));
    }
    /*
        用户AAA发来的信息
        {
        "id":1,
            "method":"invited",
            "params":{
                "userId": "AAA",
                "session": "AAA",
                "type": “all”,
                "number": 4,
                "targets":[{"target_0":"dadasd","target_1":"dadasd","2":"target_2","target_3":"dadasd"}]

        },
        "jsonrpc":"2.0"

        发送消息到target_0
         {
        "id":1,
            "method":"onInvited",
            "params":{
                "fromId": "AAA",
                "session": "AAA",
                "type": “all”,
        },
        "jsonrpc":"2.0"
        //自身返回
       {
        "id":1,
            "method":"invited",
            "params":{
                "number": 4,
                "target_0": "online",
                "target_1": "online",
                "target_1": "offline",
                "target_2": "offline",
                "target_4": "offline",
                "session": "AAA",
                "type": “all”,
                }
        },
        "jsonrpc":"2.0"
    }*/
    private void invited(WebSocketSession session, JsonObject message) {
        try {
            String params = message.get("params").getAsString();
            JsonObject object = gson.fromJson(params, JsonObject.class);
            String userId = object.get(ProtocolElements.INVITED_USER_PARAM).getAsString();
            String sessionId = object.get(ProtocolElements.INVITED_ROOM_PARAM).getAsString();
            int number = Integer.valueOf(object.get(ProtocolElements.INVITED_NUMBER_PARAM).getAsString());
            String targets = object.get(ProtocolElements.INVITED_TARGETS_PARAM).getAsString();
            String mediaType = object.get(ProtocolElements.INVITED_MEDIA_TYPE_PARAM).getAsString();
            JsonObject response = new JsonObject();
            JsonObject responseParams = new JsonObject();
            /** 首先判断这个target id是否在userIdAndPrivateId集合当中有
             * 如果没有说明不在线需要返回,如果有则向目标发起通知,通知其加入房间*/
            if (number > 0) {
                JsonArray targetArray =
                        new JsonParser().parse(targets).getAsJsonArray();
                for (int i = 0; i < targetArray.size(); i++) {
                    JsonObject targetNofification = new JsonObject();
                    JsonObject targetNofificationParams = new JsonObject();

                    JsonObject target = targetArray.get(i).getAsJsonObject();
                    String targetId = target.get("target_" + i).getAsString();
                    log.info("targetId : {}", targetId);
                    //判断targetId是否在sessions集合当中
                    if (sessions.containsKey(targetId)) {
                        WebSocketSession targetSession = sessions.get(targetId);
                        targetNofification.addProperty("method", "onInvited");
                        targetNofificationParams.addProperty("fromId",userId);
                        targetNofificationParams.addProperty("session",sessionId);
                        targetNofificationParams.addProperty("number",number);
                        targetNofificationParams.addProperty("type",mediaType);
                        targetNofification.addProperty("params",targetNofificationParams.toString());
                        targetSession.sendMessage(new TextMessage(targetNofification.toString()));

                        responseParams.addProperty("target_" + i, "online");
                    } else {
                        responseParams.addProperty("target_" + i, "offline");
                    }
                    responseParams.addProperty("session", sessionId);
                }
            }
            response.addProperty("params", responseParams.toString());
            response.addProperty("number", number);
            response.addProperty("method", ProtocolElements.INVITED_METHOD);
            session.sendMessage(new TextMessage(response.toString()));
        } catch (IOException e) {
            //e.printStackTrace();
            log.error("error......");
        }

    }
}
