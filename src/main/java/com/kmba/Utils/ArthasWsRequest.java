package com.kmba.Utils;

import static com.kmba.Utils.Dict.*;

public class ArthasWsRequest {
    public String action;

    public String data;

    public ArthasWsRequest(String cmd) {
        this.data = cmd + ENTER;
        this.action = READ;
    }
}
