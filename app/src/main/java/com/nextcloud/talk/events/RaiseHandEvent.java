package com.nextcloud.talk.events;

public class RaiseHandEvent {

    private final String type;

    private final String peerId;

    private final String videoStreamType;


    public RaiseHandEvent(String type, String peerId, String videoStreamType) {
        this.type = type;
        this.peerId = peerId;
        this.videoStreamType = videoStreamType;
    }

    public String getType() {
        return type;
    }

    public String getPeerId() {
        return peerId;
    }

    public String getVideoStreamType() {
        return videoStreamType;
    }
}
