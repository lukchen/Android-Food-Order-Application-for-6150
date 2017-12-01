package com.androideatit;

import android.content.Context;
import android.content.DialogInterface;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.androideatit.Common.Common;
import com.androideatit.Database.Database;
import com.androideatit.Model.Food;
import com.androideatit.Model.Order;
import com.androideatit.Model.Request;
import com.androideatit.Model.Receipt;
import com.androideatit.ViewHolder.CartAdapter;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static android.content.ContentValues.TAG;
import static com.androideatit.Cart.inventory;
import static com.androideatit.Cart.inventoryList;
import static com.androideatit.Cart.requestList;
import static com.androideatit.Cart.requestId;
import static com.androideatit.Cart.total;

//this thread updates the inventoryList from firebase
class IventoryListThread implements Runnable
{
    DatabaseReference foods = FirebaseDatabase.getInstance().getReference("Foods");

    public void run()
    {
        foods.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                inventoryList.clear();
                for(DataSnapshot singleSnapshot : dataSnapshot.getChildren()){
                    inventory = singleSnapshot.getValue(Food.class);
                    inventoryList.add(inventory);
                }
                System.out.println(inventoryList.size());
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {

            }
        });

    }
}


public class Cart extends AppCompatActivity {

    RecyclerView recyclerView;
    RecyclerView.LayoutManager layoutManager;

    FirebaseDatabase database;
    DatabaseReference requests;

    TextView txtTotalPrice;
    Button btnPlace;




    List<Order> cart = new ArrayList<>();
    CartAdapter adapter;

    //name the threads
    Thread inventorylistthread = new Thread(new IventoryListThread());
    Thread kitchenthread = new Thread(new KitchenThread());

    //name the variables in static, so they can be accessed and updated by the inventorylistthread
    static List<List<Order>> orderList = new ArrayList<>();
    static List<Food> inventoryList = new ArrayList<>();
    static Food inventory;
    static String requestId;
    //The orderList is for inventoryList, the requestList is for the KitchenThread
    static List<Request> requestList = new ArrayList<>();
    static int total;

    //partial request flag
    private boolean partial = false;

    //unavailable food price
    static int unavailablefoodprice=0;

    //The executor can makes inventorylistthread running in interval, which is 1 hour
    ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cart);

        //run the kitchenthread

        kitchenthread.start();



        //thread running in 1 hour interval
        executor.scheduleAtFixedRate(inventorylistthread, 0, 60, TimeUnit.MINUTES);

        //Firebase
        database = FirebaseDatabase.getInstance();
        requests =  database.getReference("Requests");

        //Init
        recyclerView = findViewById(R.id.listCart);
        recyclerView.setHasFixedSize(true);
        layoutManager = new LinearLayoutManager(this);
        recyclerView.setLayoutManager(layoutManager);

        txtTotalPrice = findViewById(R.id.total);
        btnPlace = findViewById(R.id.btnPlaceOrder);

        //When the "Place Order" button clicked
        btnPlace.setOnClickListener(new View.OnClickListener() {
            @Override
           public void onClick(View v) {

                //update invetoryList immediately first
                executor.scheduleAtFixedRate(inventorylistthread, 0, 60, TimeUnit.MINUTES);
                //Check if the order can only be partial
                if(!checkavailability(cart)) {

                    //Create new Request
                    showAlertDialog();

                }else {

                    //Show user the "Partial order or cancel order options" dialog,


                    //If user choose Partial order, then do showAlertDialog() again, and set the partial flag to true in order to set this request partially

                    /*showAlertDialog();
                    partial = true;*/

                }
            }
        });

        loadListFood();

    }

    //Find out whether the foods in order contains unavailable food
    private boolean checkavailability(List<Order> cart){
        boolean partial = false;
        if(cart.size()==0){
            //Cart is empty, do nothing
            partial = true;
        }
        for(Order order : cart){
            for(Food food: inventoryList){
                /*System.out.println("SH-----------IT"+food.getFoodId());
                System.out.println("FU-----------CK"+order.getProductId());
                System.out.println("DA-----------MN"+food.getAvailabilityFlag());*/
                if(food.getFoodId().equals(order.getProductId())){
                    if(food.getAvailabilityFlag().equals("0")){
                        //if the availabilityFlag of this food is "0"
                        System.out.println("FUCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCK");
                        partial = true;
                        unavailablefoodprice += Integer.parseInt(food.getPrice());
                    }
                }
            }
        }
        return partial;
    }

    private void showAlertDialog() {
        AlertDialog.Builder alertDialog = new AlertDialog.Builder(Cart.this);
        alertDialog.setTitle("One more step!");
        alertDialog.setMessage("Enter your address");
        System.out.println("email address ");
        final EditText edtAddress = new EditText(Cart.this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
        );

        edtAddress.setLayoutParams(lp);
        alertDialog.setView(edtAddress);
        alertDialog.setIcon(R.drawable.ic_shopping_cart_black_24dp);

        alertDialog.setPositiveButton("YES", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                Request request = new Request(
                        Common.currentUser.getPhone(),
                        Common.currentUser.getName(),
                        edtAddress.getText().toString(),
                        txtTotalPrice.getText().toString(),
                        cart
                );

                //Submit to Firebase
                //We will using System.Current
                requestId=String.valueOf(System.currentTimeMillis());
                requests.child(requestId).setValue(request);
                if(partial) {
                    request.setPartial(true);
                    //add the request to top of the requestList if it's partial request
                    requestList.add(0,request);

                    //default partial is false, set it back to false to check next request
                    partial = false;
                }else {
                    requestList.add(request);
                }
                //Delete the cart
                new Database(getBaseContext()).cleanCart();
                Toast.makeText(Cart.this, "Thank you, Order placed", Toast.LENGTH_SHORT).show();
                finish();
            }
        });

        /*alertDialog.setPositiveButton("NO", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });*/

        alertDialog.show();

    }

    //this thread cooks requests
    class KitchenThread implements Runnable{

        @Override
        public void run() {
            while (true) {
                while (requestList.size() > 0) {
                    DatabaseReference requests = FirebaseDatabase.getInstance().getReference("Requests");
                    requests.child(requestId).child("status").setValue("1");
                    System.out.println("The chef is working on requests!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
                    try {
                        //cooking time: 180 sec
                        Thread.sleep(10000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }

                    System.out.println("Oder Prepared!");


                    requests.child(requestId).child("status").setValue("2");
                    try {
                        //packaging time: 180 sec
                        Thread.sleep(10000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    System.out.println("Order Packaged!");

                    requests.child(requestId).child("status").setValue("3");

                    //The first request finished, generate receipt, then remove the finished request, working on next request in list
                    Looper.prepare();
                    Toast.makeText(Cart.this,GenerateReceipt(requestList.get(0)),Toast.LENGTH_SHORT).show();
                    Looper.loop();
                    requestList.remove(0);

                }
                System.out.println("there are no requests yet, what a terrible day!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
            }
        }

        public String GenerateReceipt(Request request){
            Receipt receipt = new Receipt();
            receipt.items = request.getFoods();
            receipt.totalcost = request.getTotal();
            String message="";
            for(Order i:receipt.items){
                message+=i.getProductName()+" :"+i.getQuanlity()+"\n";
            }
            message+="Total: "+total;
            return message;


        }
    }


    private void loadListFood() {
        cart = new Database(this).getCarts();
        orderList.add(cart);
        adapter = new CartAdapter(cart,this);
        recyclerView.setAdapter(adapter);

        //Calculate total price
        total = 0;
        for(Order order:cart)
            total+=(Integer.parseInt(order.getPrice()))*(Integer.parseInt(order.getQuanlity()));
        Locale locale = new Locale("en","US");
        NumberFormat fmt = NumberFormat.getCurrencyInstance(locale);

        // add tax, profit to total, do we need to show the tax and profit on the app??
        int tax= (int) (total*0.06);
        int profit=(int)(total*0.3);
        total+=tax+profit;
        txtTotalPrice.setText(fmt.format(total));

    }



}
