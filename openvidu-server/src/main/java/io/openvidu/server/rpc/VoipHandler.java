package io.openvidu.server.rpc;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.openvidu.client.OpenViduException;
import io.openvidu.client.OpenViduException.Code;
import io.openvidu.client.internal.ProtocolElements;
import org.kurento.jsonrpc.DefaultJsonRpcHandler;
import org.kurento.jsonrpc.Session;
import org.kurento.jsonrpc.Transaction;
import org.kurento.jsonrpc.internal.ws.WebSocketServerSession;
import org.kurento.jsonrpc.message.Request;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class VoipHandler extends DefaultJsonRpcHandler<JsonObject> {

    private static final Logger log = LoggerFactory.getLogger(VoipHandler.class);
    /**
     * 当用户发送joinCloud后会创建一个这样的Map
     */
    private Map<String, org.kurento.jsonrpc.Session> sessions = new ConcurrentHashMap<>();

    @Autowired
    private RpcNotificationService notificationService;

    @Override
    public void handleRequest(Transaction transaction, Request<JsonObject> request)
            throws Exception {
        String participantPrivateId =
                getParticipantPrivateIdByTransaction(transaction);
        log.info("WebSocket session #{} - Request: {}", participantPrivateId, request);
        RpcConnection rpcConnection;
        if (ProtocolElements.JOINCLOUD_METHOD.equals(request.getMethod())) {
            // Store new RpcConnection information if method 'joinCloud'
            rpcConnection = notificationService.newRpcConnection(transaction, request);
        } else if (notificationService.getRpcConnection(participantPrivateId) == null) {
            // Throw exception if any method is called before 'joinCloud'
            log.warn(
                    "No connection found for participant with privateId {} when trying to execute method '{}'. Method 'Session.connect()' must be the first operation called in any session",
                    participantPrivateId, request.getMethod());
            throw new OpenViduException(Code.TRANSPORT_ERROR_CODE,
                    "No connection found for participant with privateId " + participantPrivateId
                            + ". Method 'Session.connect()' must be the first operation called in any session");
        }

        rpcConnection = notificationService.addTransaction(transaction, request);

        transaction.startAsync();

        switch (request.getMethod()) {
            case ProtocolElements.JOINCLOUD_METHOD:
                joinCloud(rpcConnection, request);
                break;
            case ProtocolElements.INVITED_METHOD:
                invited(rpcConnection, request);
                break;
            default:
                //log.error("Unrecognized request {}", request);
                break;
        }
    }
    /*
        用户AAA发来的信息
        typeOfMedia：[audio/media/video]
        typeOfSession :"{"type":room,"session":"AA"}",或 "{"type":voip}",如果是单人通话就不带session
        {
        "id":1,
            "method":"invited",
            "params":{
                "userId": "AAA",
                "typeOfSession": "{"type":room,"session":"AA"}",
                "typeOfMedia": “all”,
                "number": 4,
                "targetUsers":[{"target_0":"dadasd","target_1":"dadasd","2":"target_2","target_3":"dadasd"}]

        },
        "jsonrpc":"2.0"

        发送消息到target_0
        {
            "method":"onInvited",
            "params":{
                "fromId": "AAA",
                "typeOfSession": "{"type":room,"session":"AA"}",
                "typeOfMedia": “all”,
            },
            "jsonrpc":"2.0"
        }
        //自身返回
        /*{  "result":
            {
                "invited"："OK"
                "userId": "AAA",
                "typeOfSession": "{"type":room,"session":"AA"}",
                "typeOfMedia": “all”,
                "number": 4,
                "targetsState":[{"target_0":"online","target_1":"online","2":"online","target_3":"offline"}]
            },
            "id":1,
            "jsonrpc":"2.0"
        }
         */

    private void joinCloud(RpcConnection rpcConnection, Request<JsonObject> request) {
        JsonObject result = new JsonObject();
        result.addProperty(ProtocolElements.JOINCLOUD_METHOD, "OK");
        notificationService.sendResponse(rpcConnection.getParticipantPrivateId(),
                request.getId(), result);

    }

    private void invited(RpcConnection rpcConnection, Request<JsonObject> request) {

        String userId = getStringParam(request, ProtocolElements.INVITED_USER_PARAM);
        int number = getIntParam(request, ProtocolElements.INVITED_NUMBER_PARAM);
        String targetUsers = getStringParam(request, ProtocolElements.INVITED_TARGETUSERS_PARAM);
        String typeOfMedia = getStringParam(request, ProtocolElements.INVITED_TYPEMEDIA_PARAM);
        String typeOfSession = getStringParam(request, ProtocolElements.INVITED_TYPESESSION_PARAM);

        JsonObject result = new JsonObject();
        JsonObject targetStateParams = new JsonObject();
        /** 首先判断这个target id是否在userIdAndPrivateId集合当中有
         * 如果没有说明不在线需要返回,如果有则向目标发起通知,通知其加入房间*/
        if (number > 0) {
            try {
                JsonArray targetArray =
                        new JsonParser().parse(targetUsers).getAsJsonArray();
                for (int i = 0; i < targetArray.size(); i++) {
                    JsonObject notifParams = new JsonObject();
                    JsonObject target = targetArray.get(i).getAsJsonObject();
                    String targetId = target.get("target_" + i).getAsString();
                    //判断targetId是否在sessions集合当中
                    boolean targetOnline = sessions.containsKey(targetId);
                    if (targetOnline) {
                        Session targetSession = sessions.get(targetId);
                        notifParams.addProperty("fromId", userId);
                        notifParams.addProperty("typeOfMedia", typeOfMedia);
                        notifParams.addProperty("typeOfSession", typeOfSession);
                        targetSession.sendNotification("onInvited", notifParams);
                    }
                    targetStateParams.addProperty("target_" + i,
                            targetOnline ? "online" : "offline");
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            result.addProperty("targetsState", String.valueOf(targetStateParams));
        }

        result.addProperty("invited", "OK");
        result.addProperty("userId", userId);
        result.addProperty("number", number);
        result.addProperty("typeOfSession", typeOfSession);
        result.addProperty("typeOfMedia", typeOfMedia);
        notificationService.sendResponse(rpcConnection.getParticipantPrivateId(),
                request.getId(), result);
    }


    @Override
    public void afterConnectionEstablished(Session rpcSession) throws Exception {
        super.afterConnectionEstablished(rpcSession);
        /*log.info("After connection established for WebSocket session: {},attributes={}",
                rpcSession.getSessionId(), rpcSession.getAttributes());
        if (rpcSession instanceof WebSocketServerSession) {
            String userId =
                    (String) rpcSession.getAttributes().get("userId");
            log.info("afterConnectionEstablished userId:" + userId);
            sessions.put(userId, rpcSession);
        }*/
    }

    @Override
    public void afterConnectionClosed(Session session, String status) throws Exception {
        super.afterConnectionClosed(session, status);
        String userId = (String) session.getAttributes().get("userId");
        sessions.remove(userId);
    }

    public static String getStringParam(Request<JsonObject> request, String key) {
        if (request.getParams() == null || request.getParams().get(key) == null) {
            throw new RuntimeException("Request element '" + key + "' is missing in method '" + request.getMethod()
                    + "'. CHECK THAT 'openvidu-server' AND 'openvidu-browser' SHARE THE SAME VERSION NUMBER");
        }
        return request.getParams().get(key).getAsString();
    }

    public static int getIntParam(Request<JsonObject> request, String key) {
        if (request.getParams() == null || request.getParams().get(key) == null) {
            throw new RuntimeException("Request element '" + key + "' is missing in method '" + request.getMethod()
                    + "'. CHECK THAT 'openvidu-server' AND 'openvidu-browser' SHARE THE SAME VERSION NUMBER");
        }
        return request.getParams().get(key).getAsInt();
    }

    public static boolean getBooleanParam(Request<JsonObject> request, String key) {
        if (request.getParams() == null || request.getParams().get(key) == null) {
            throw new RuntimeException("Request element '" + key + "' is missing in method '" + request.getMethod()
                    + "'. CHECK THAT 'openvidu-server' AND 'openvidu-browser' SHARE THE SAME VERSION NUMBER");
        }
        return request.getParams().get(key).getAsBoolean();
    }

    public static JsonElement getParam(Request<JsonObject> request, String key) {
        if (request.getParams() == null || request.getParams().get(key) == null) {
            throw new RuntimeException("Request element '" + key + "' is missing in method '" + request.getMethod()
                    + "'. CHECK THAT 'openvidu-server' AND 'openvidu-browser' SHARE THE SAME VERSION NUMBER");
        }
        return request.getParams().get(key);
    }

    public String getParticipantPrivateIdByTransaction(Transaction transaction) {
        String participantPrivateId = null;
        try {
            participantPrivateId = transaction.getSession().getSessionId();
        } catch (Throwable e) {
            log.error("Error getting WebSocket session ID from transaction {}", transaction, e);
            throw e;
        }
        return participantPrivateId;
    }
}
