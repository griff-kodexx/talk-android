package com.nextcloud.talk.models.json.polls;


import com.bluelinelabs.logansquare.annotation.JsonField;
import com.bluelinelabs.logansquare.annotation.JsonObject;
import org.parceler.Parcel;

import java.util.List;

@Parcel
@JsonObject
public class PollsResult {

    @JsonField(name = "votes")
    public List<SinglePollResult> votes;
}
