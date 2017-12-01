package com.androideatit.ViewHolder;

import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.widget.TextView;

import com.androideatit.Interface.ItemClickListener;
import com.androideatit.R;

/**
 * Created by 123456 on 2017/11/20.
 */

public class OrderViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener{

    public TextView txtOrderId, txtOrderStatus, txtOrderphone, txtOrderAddress;

    private ItemClickListener itemClickListener;

    public OrderViewHolder(View itemView){
        super(itemView);

        txtOrderAddress = itemView.findViewById(R.id.order_address);
        txtOrderId = itemView.findViewById(R.id.order_id);
        txtOrderStatus = itemView.findViewById(R.id.order_status);
        txtOrderphone = itemView.findViewById(R.id.order_address);

        itemView.setOnClickListener(this);

    }

    public void setItemClickListener(ItemClickListener itemClickListener){
        this.itemClickListener = itemClickListener;
    }

    @Override
    public void onClick(View v) {
        itemClickListener.onClick(v, getAdapterPosition(),false);

    }
}
