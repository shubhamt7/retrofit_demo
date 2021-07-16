package com.business.merchant_payments.notificationsettings.model

import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName
import java.io.Serializable

data class OrderListItemModel(
    @Expose
    @SerializedName("oppositeNickname")
    var oppositeNickname: String? = null,

    @Expose
    @SerializedName("payMoneyAmount")
    var payMoneyAmount: PayMoneyAmountModel? = null
) : Serializable


data class PayMoneyAmountModel(
    @Expose
    @SerializedName("value")
    var value: String? = null
):Serializable
