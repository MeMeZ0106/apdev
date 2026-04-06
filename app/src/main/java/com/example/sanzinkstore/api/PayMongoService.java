package com.example.sanzinkstore.api;

import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.POST;
import retrofit2.http.Path;

public interface PayMongoService {
    @POST("sources")
    Call<ResponseBody> createSource(
            @Header("Authorization") String auth,
            @Body RequestBody body
    );

    @GET("sources/{id}")
    Call<ResponseBody> getSource(
            @Header("Authorization") String auth,
            @Path("id") String sourceId
    );
}
