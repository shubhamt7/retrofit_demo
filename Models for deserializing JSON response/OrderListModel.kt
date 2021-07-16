package com.business.merchant_payments.notificationsettings.model

import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName
import java.io.Serializable

data class OrderListModel(
    @Expose
    @SerializedName("orderList")
    var orderList : List<OrderListItemModel>? = null
):Serializable