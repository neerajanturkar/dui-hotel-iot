package com.dui.iotClient.helper;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.preference.PreferenceManager;
import android.widget.ToggleButton;

import com.dui.iotClient.AppConstants;
import com.dui.iotClient.HomeActivity;
import com.dui.iotClient.MainActivity;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPubSub;

public class RedisHelper extends AsyncTask<Void, Void, String> {
    public String responseMessage = null;
    Jedis jedis = new Jedis(AppConstants.DUI_CORE_HOST);
    Context context;
    Activity activity;
    JedisPubSub jedisPubSub;
    public RedisHelper(Context context, Activity activity) {
        this.context = context;
        this.activity = activity;
    }
    @Override
    protected void onPreExecute() {
        super.onPreExecute();
    }

    @Override
    protected void onProgressUpdate(Void... values) {
    }

    @Override
    protected void onPostExecute(String s) {
        try {
            if (responseMessage != null) {

                System.out.println("In post execute : " + s);

                String arr[] = responseMessage.split("::");

                switch (arr[0]) {
                    case "CONNECTED":
                        System.out.println("Connected");
                        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
                        String sessionId = sharedPreferences.getString(AppConstants.SESSION_ID, null);
                        jedisPubSub.unsubscribe(sessionId);
                        if (sessionId != null) {
                            Intent intent = new Intent(context, HomeActivity.class);
                            intent.putExtra(AppConstants.SESSION_ID, sessionId);
                            context.startActivity(intent);

                        }

                        break;
                    case "BOOLEAN":
                        ToggleButton sw = activity.findViewById(context.getResources().getIdentifier(arr[1], "id", context.getPackageName()));
                        sw.setChecked(Boolean.parseBoolean(arr[2]));
                        new RedisHelper(context, activity).execute();
                        break;
                }
//            new RedisHelper(context, activity).execute();
            }
        }catch (Exception e){
            e.printStackTrace();
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
                    System.out.println("Client is Subscribed to channel : "+ channel);
                }

                @Override
                public void onUnsubscribe(String channel, int subscribedChannels) {
                    // TODO Auto-generated method stub
                    super.onUnsubscribe(channel, subscribedChannels);
                    System.out.println("Channel unsubscribed at : " + channel);
                }

            };
            SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
            String sessionId = sharedPreferences.getString(AppConstants.SESSION_ID, null);
            if (sessionId != null) {
                jedis.subscribe(jedisPubSub,sessionId);
                System.out.println("Channel Subscribed at : " + sessionId);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
}
