package com.kmba.pojo;

public class AgentInfo {
    private String host;
    private int wsPort;
    private int httpPort;

    public void setHost(String host) {
        this.host = host;
    }

    public void setWsPort(int port) {
        this.wsPort = port;
    }
    public void setHttpPort(int port) {
        this.httpPort = port;
    }

    public String getConnectString() {
        return "ws://" + host + ":" + wsPort + "/ws";
    }
}
