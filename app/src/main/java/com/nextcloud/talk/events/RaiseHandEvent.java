package com.nextcloud.talk.events;

public class RaiseHandEvent {

    private final String type;

    private final String peerId;

    private final String videoStreamType;

    private final Boolean raiseHand;

    public RaiseHandEvent(String type, String peerId, String videoStreamType, Boolean raiseHand) {
        this.type = type;
        this.peerId = peerId;
        this.videoStreamType = videoStreamType;
        this.raiseHand = raiseHand;
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

    public Boolean getRaiseHand() {
        return raiseHand;
    }
}
