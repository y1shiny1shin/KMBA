package com.kmba.Utils;

import org.apache.logging.log4j.LogManager;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class ArthasWsClient extends WebSocketClient {
    Logger logger = LoggerFactory.getLogger(this.getClass().getName());
    public List<String> resultMsgBuffer = new ArrayList<>();
    public List<String> resultMsg = new ArrayList<>();
    public boolean end;

    public ArthasWsClient(URI serverUri) {
        super(serverUri);

    }

    @Override
    public void onOpen(ServerHandshake handshakedata) {
    }

    @Override
    public void onMessage(String message) {
        if (message == null || message.trim().isEmpty()) {
            return;
        }

        message = message.trim();
        if (isEndStream(message)){
            this.end = true;
            this.resultMsg = this.resultMsgBuffer;
            this.resultMsgBuffer = new ArrayList<>();
        } else {
            resultMsgBuffer.add(message);
        }

    }
    public boolean isEndStream(String message) {
        return message.matches("^\\[arthas@\\d{1,20}\\]\\$$");
    }


    public void setEnd(boolean end) {
        this.end = end;
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
    }

    @Override
    public void onError(Exception ex) {
        logger.error(ex.getMessage());
    }

    public void clear() {
        this.resultMsgBuffer.clear();
        this.resultMsg.clear();

    }

}
