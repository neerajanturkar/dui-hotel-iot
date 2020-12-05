package com.dui.iotClient;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPubSub;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.ToggleButton;

public class HomeActivity extends AppCompatActivity {
    public String responseMessage = new String();
    public String sessionId = new String();
    public Activity self = this;
    public Context context = HomeActivity.this;
    Jedis jedis = new Jedis(AppConstants.DUI_CORE_HOST);
    JedisPubSub jedisPubSub;
    public TextView temperature, fanSpeed;
    public ToggleButton doorLight, mainLight;
    public EditText note;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);
        temperature = findViewById(R.id.tvTemperatureValue);
        fanSpeed = findViewById(R.id.tvFanSpeedValue);
        doorLight = findViewById(R.id.tbDoorLight);
        mainLight = findViewById(R.id.tbMainLight);

        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(HomeActivity.this);
        doorLight.setChecked(Boolean.parseBoolean(sharedPreferences.getString("tbDoorLight", null)));
        mainLight.setChecked(Boolean.parseBoolean(sharedPreferences.getString("tbMainLight", null)));
        temperature.setText(sharedPreferences.getString("tvTemperatureValue", null));
        fanSpeed.setText(sharedPreferences.getString("tvFanSpeedValue", null));
        note = findViewById(R.id.edNote);



        new RedisHelper().execute();
    }
    public class RedisHelper extends AsyncTask<Void, Void, String> {

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
        }

        @Override
        protected void onProgressUpdate(Void... values) {

        }

        @Override
        protected void onPostExecute(String s) {
            System.out.println("In post execute Home activity: " + s);

            final String arr[] = responseMessage.split("::");
            System.out.println("Message string" + arr[1]);
            try {
                switch (arr[0]) {
                    case "BOOLEAN":

                        final ToggleButton sw = findViewById(context.getResources().getIdentifier(arr[1], "id", context.getPackageName()));
                        HomeActivity.this.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                sw.setChecked(Boolean.parseBoolean(arr[2]));
                                sw.setSelected(Boolean.parseBoolean(arr[2]));
                                sw.callOnClick();
                            }
                        });
                        break;
                    case "INTEGER":
                        final TextView tv = findViewById(context.getResources().getIdentifier(arr[1], "id", context.getPackageName()));
                        HomeActivity.this.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                tv.setText(arr[2]);
                            }
                        });
                    case "TEXT":
                        final EditText ed = findViewById(context.getResources().getIdentifier(arr[1], "id", context.getPackageName()));
                        HomeActivity.this.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                ed.setText(arr[2]);
                            }
                        });
                }
            } catch (Exception e){
                e.printStackTrace();
            } finally {
                new RedisHelper().execute();
            }

        }

        @Override
        protected String doInBackground(Void... strings) {
            if(android.os.Debug.isDebuggerConnected())
                android.os.Debug.waitForDebugger();
            try  {

                jedis.connect();
                jedisPubSub = new JedisPubSub() {

                    @Override
                    public void onMessage(String channel, String message) {
                        // TODO Auto-generated method stub
                        responseMessage = message;
                        onPostExecute(responseMessage);
                    }

                    @Override
                    public void onSubscribe(String channel, int subscribedChannels) {
                        // TODO Auto-generated method stub
                        System.out.println("Client is Subscribed to channel Home activity: "+ channel);
                    }

                    @Override
                    public void onUnsubscribe(String channel, int subscribedChannels) {
                        // TODO Auto-generated method stub
                        super.onUnsubscribe(channel, subscribedChannels);
                        System.out.println("Channel unsubscribed at Home activity: " + channel);
                    }

                };
                SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(HomeActivity.this);
                String sessionId = sharedPreferences.getString(AppConstants.SESSION_ID, null);
                if (sessionId != null) {
                    jedis.subscribe(jedisPubSub,sessionId);
                    System.out.println("Channel Subscribed at Home activity: " + sessionId);
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
            return null;
        }
    }



}