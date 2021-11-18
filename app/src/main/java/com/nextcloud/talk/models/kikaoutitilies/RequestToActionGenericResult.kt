package com.nextcloud.talk.models.kikaoutitilies

import com.bluelinelabs.logansquare.annotation.JsonField
import com.bluelinelabs.logansquare.annotation.JsonObject
import com.nextcloud.talk.models.json.generic.GenericOCS
import org.parceler.Parcel

@Parcel
@JsonObject
class RequestToActionGenericResult {

    @JsonField(name = ["id"])
    var id: Int? = null

    @JsonField(name = ["token"])
    var token: String? = null

    @JsonField(name = ["userId"])
    var userId: String? = null

    @JsonField(name = ["activityType"])
    var activityType: Int? = null

    @JsonField(name = ["approved"])
    var approved: Boolean? = null

    @JsonField(name = ["started"])
    var started: Boolean? = null

    @JsonField(name = ["paused"])
    var paused: Boolean? = null

    @JsonField(name = ["canceled"])
    var canceled: Boolean? = null

    @JsonField(name = ["duration"])
    var duration: Int? = 0

    @JsonField(name = ["talkingSince"])
    var talkingSince: Long? = 0L



}