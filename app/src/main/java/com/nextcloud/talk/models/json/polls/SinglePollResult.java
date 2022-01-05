package com.nextcloud.talk.models.json.polls;

import com.bluelinelabs.logansquare.annotation.JsonField;
import com.bluelinelabs.logansquare.annotation.JsonObject;

import org.parceler.Parcel;


@Parcel
@JsonObject
public class SinglePollResult {

    @JsonField(name = "id")
    public Long id;

    @JsonField(name = "type")
    public String type;
}
