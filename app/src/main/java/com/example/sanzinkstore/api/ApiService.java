package com.example.sanzinkstore.api;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;

public interface ApiService {
    // Example endpoint for GCash payment or other backend services
    @POST("payments/gcash")
    Call<ResponseBody> processGCashPayment(@Body Object paymentData);
}
