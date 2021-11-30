
package com.nextcloud.talk.api

import com.nextcloud.talk.models.kikaoutitilies.RequestToActionGenericResult
import io.reactivex.Observable
import okhttp3.RequestBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Url

interface ApiService {

    @POST("apps/kikaoutilities/api/0.1/activities")
    fun requestToSpeak(@Header("Authorization") authorization: String?, @Body body: RequestBody?): Observable<RequestToActionGenericResult?>?

    @PUT("apps/kikaoutilities/api/0.1/activities/{id}")
    fun cancelRequestToSpeak(@Header("Authorization") authorization: String?,  @Path("id") id:
    Int, @Query("token") token:String, @Body body: RequestBody?):
        Observable<RequestToActionGenericResult?>?

    @POST("apps/kikaoutilities/api/0.1/activities")
    fun requestToIntervene(@Header("Authorization") authorization: String?, @Body body: RequestBody?): Observable<RequestToActionGenericResult?>?

    @PUT("apps/kikaoutilities/api/0.1/activities/{id}")
    fun cancelRequestToIntervene(@Header("Authorization") authorization: String?,  @Path("id") id:
    Int, @Query("token") token:String, @Body body: RequestBody?):
        Observable<RequestToActionGenericResult?>?

    @GET("apps/kikaoutilities/api/0.1/activities")
    fun getSpeakerActionResponses(@Header("Authorization") authorization: String?,  @Query("token") token:
    String,):
        Observable<List<RequestToActionGenericResult>?>?


    @PUT("apps/kikaoutilities/api/0.1/activities/{id}")
    fun userUnMuted(@Header("Authorization") authorization: String?, @Path("id") id:
    Int, @Query("token") token:String, @Body body: RequestBody?): Observable<RequestToActionGenericResult?>?
}