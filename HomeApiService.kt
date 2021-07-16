package com.paytm.business.home.network

import com.business.merchant_payments.notificationsettings.model.OrderResponseModel
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.converter.scalars.ScalarsConverterFactory
import retrofit2.http.*

private const val BASE_URL = "https://dashboard.paytm.com/"

private val retrofit = Retrofit.Builder()
    .addConverterFactory(GsonConverterFactory.create())
    .baseUrl(BASE_URL)
    .build()


interface HomeApiService{

    @GET("api/v1/app/home/primary")
    suspend fun getHomeApiData(@HeaderMap headers: HashMap<String, Any>,
                               @Query("pageSize") pages:Int) : OrderResponseModel?

}

object HomeApi{
    val retrofitObj: HomeApiService by lazy{
        retrofit.create(HomeApiService::class.java)
    }
}