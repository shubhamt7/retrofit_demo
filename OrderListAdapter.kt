package com.paytm.business.notificationsettings.adapters.model
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.business.merchant_payments.notificationsettings.model.OrderListItemModel
import com.paytm.business.databinding.OrderListItemBinding
import kotlinx.android.synthetic.main.order_list_item.view.*
import java.math.BigInteger

class OrderListAdapter(private val context : Context, private var orderList: List<OrderListItemModel>) : RecyclerView.Adapter<OrderListAdapter.OrderViewHolder>(){

    class OrderViewHolder(private val itemView : OrderListItemBinding) : RecyclerView.ViewHolder(itemView.root){

        fun bind(orderItem : OrderListItemModel){
            itemView.name_of_sender.text = orderItem.oppositeNickname.toString()
            itemView.received_amount.text = orderItem.payMoneyAmount!!.value.toString()
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): OrderViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = OrderListItemBinding.inflate(inflater, parent, false)

        return OrderViewHolder(binding)
    }

    override fun onBindViewHolder(holder: OrderViewHolder, position: Int) {
        holder.bind(orderList[position])
    }

    override fun getItemCount(): Int {
        return orderList.size
    }

}