package com.dui.iotClient;

import androidmads.library.qrgenearator.QRGContents;
import androidmads.library.qrgenearator.QRGEncoder;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPubSub;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.content.res.XmlResourceParser;
import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Switch;
import android.widget.Toast;
import android.widget.ToggleButton;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.dui.iotClient.helper.RedisHelper;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.LinkedHashMap;

public class MainActivity extends AppCompatActivity {
    ImageView qrImage;
    public String responseMessage = new String();
    public Activity self = this;
    Jedis jedis = new Jedis(AppConstants.DUI_CORE_HOST);
    JedisPubSub jedisPubSub;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        qrImage = findViewById(R.id.ivQrImage);
        requestPermission(Manifest.permission.INTERNET, PERMISSION_INTERNET);
        requestPermission(Manifest.permission.ACCESS_NETWORK_STATE, PERMISSION_ACCESS_NETWORK_STATE);
        ApplicationInfo ai = null;
        String sessionId = new String();
        String applicationId = new String();
        String applicationSecret = new String();
        String uiProfileId = new String();
        try {
            ai = this.getPackageManager().getApplicationInfo(this.getPackageName(), PackageManager.GET_META_DATA);
            applicationId = ai.metaData.get("applicationId").toString();
            applicationSecret = ai.metaData.get("applicationSecret").toString();
            uiProfileId = ai.metaData.get("uiProfileId").toString();
            if (applicationId.isEmpty() || applicationSecret.isEmpty() || uiProfileId.isEmpty()) {
                AlertDialog alertDialog = new AlertDialog.Builder(MainActivity.this).create();
                alertDialog.setTitle("Error!");
                alertDialog.setIcon(R.drawable.ic_action_warning);
                alertDialog.setMessage("Missing configuration");
                alertDialog.setButton(AlertDialog.BUTTON_NEUTRAL, "OK",
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.dismiss();
                            }
                        });
                alertDialog.show();
            } else {
                RequestQueue requestQueue = Volley.newRequestQueue(this);
                JSONObject postData = new JSONObject();
                try {
                    postData.put("applicationId", applicationId);
                    postData.put("secret", applicationSecret);
                    postData.put("uiProfileId", uiProfileId);

                } catch (JSONException e) {
                    e.printStackTrace();
                }
                JsonObjectRequest jsonObjectRequest = new JsonObjectRequest(Request.Method.POST, AppConstants.DUI_CORE_URL + "api/v1/session/", postData, new Response.Listener<JSONObject>() {
                    @Override
                    public void onResponse(JSONObject response) {
                        try {
                            JSONObject data = (JSONObject) response.get("data");
                            data.getString("_id");
//                            String uuid = data.getString("_id");
                            String uuid = data.getString("_id");

//
                            if(uuid.isEmpty()) {
                                Toast.makeText(getApplicationContext(), "Value is required", Toast.LENGTH_LONG).show();
                            } else {
                                QRGEncoder qrgEncoder = new QRGEncoder(uuid, null, QRGContents.Type.TEXT, 500);
                                try {
                                    Bitmap qrBits = qrgEncoder.getBitmap();
                                    qrImage.setImageBitmap(qrBits);
                                } catch (Exception e) {
                                    e.printStackTrace();
                                    Toast.makeText(getApplicationContext(), "Some error occurred", Toast.LENGTH_LONG);
                                }
                                SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(MainActivity.this);
                                SharedPreferences.Editor editor = sharedPref.edit();
                                editor.putString(AppConstants.SESSION_ID, uuid);
                                editor.putString("tbMainLight", ((JSONObject)data.get("profile")).get("tbMainLight").toString());
                                editor.putString("tbDoorLight", ((JSONObject)data.get("profile")).get("tbDoorLight").toString());
                                editor.putString("tvTemperatureValue", ((JSONObject)data.get("profile")).get("tvTemperatureValue").toString());
                                editor.putString("tvFanSpeedValue", ((JSONObject)data.get("profile")).get("tvFanSpeedValue").toString());
                                editor.apply();
                                new RedisHelper().execute();
                            }
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }

                    }
                }, new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        error.printStackTrace();
                    }
                });

                requestQueue.add(jsonObjectRequest);
            }
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        } catch (NullPointerException npe) {
            npe.printStackTrace();
            AlertDialog alertDialog = new AlertDialog.Builder(MainActivity.this).create();
            alertDialog.setTitle("Error!");
            alertDialog.setIcon(R.drawable.ic_action_warning);

            alertDialog.setMessage("Missing configuration");
            alertDialog.setButton(AlertDialog.BUTTON_POSITIVE, "OK",
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();
                        }
                    });
            alertDialog.show();
        }
    }
    public class RedisHelper extends AsyncTask<Void, Void, String> {

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
        }

        @Override
        protected void onProgressUpdate(Void... values) {
//            System.out.println("In post execute : " + responseMessage);
////            Toast.makeText(getApplicationContext(), "channel says : " +responseMessage, Toast.LENGTH_LONG).show();
//            scan.setText(responseMessage);
        }

        @Override
        protected void onPostExecute(String s) {
            if (responseMessage != null) {
                String arr[] = responseMessage.split("::");
                responseMessage = null;
                if (arr[0].equals("CONNECTED") && Integer.parseInt(arr[1]) < 2) {
                    System.out.println("Connected");
                    SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(MainActivity.this);
                    String sessionId = sharedPreferences.getString(AppConstants.SESSION_ID, null);
                    jedisPubSub.unsubscribe(sessionId);
                    if (sessionId != null) {
                        Intent intent = new Intent(MainActivity.this, HomeActivity.class);
                        intent.putExtra(AppConstants.SESSION_ID, sessionId);
                        startActivity(intent);
                        finish();
                    }
                } else {
                    new RedisHelper().execute();
                }
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
                SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(MainActivity.this);
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
    private static final int PERMISSION_INTERNET = 1;
    private static final int PERMISSION_ACCESS_NETWORK_STATE = 2;

    private void requestPermission(String permission, int requestId) {
        if (ContextCompat.checkSelfPermission(self,
                permission)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(self,
                    new String[]{permission},
                    requestId);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        switch (requestCode) {
            case PERMISSION_INTERNET: {
                if (grantResults.length <= 0
                        || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                    requestPermission(Manifest.permission.INTERNET, PERMISSION_INTERNET);
                }
                return;
            }
            case PERMISSION_ACCESS_NETWORK_STATE: {
                if (grantResults.length <= 0
                        || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                    requestPermission(Manifest.permission.ACCESS_NETWORK_STATE, PERMISSION_ACCESS_NETWORK_STATE);
                }
                return;
            }
        }
    }
}