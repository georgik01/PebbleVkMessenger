package com.pebble.vk.messenger;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.json.JSONArray;
import org.json.JSONObject;

import com.perm.kate.api.Api;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

public class PebbleService extends Service {
	
    Timer myTimer;

    private boolean timerRunning = false;
    private long RETRY_TIME = 1000;
    private long START_TIME = 10;
	
	String[] longPollParams = new String[3];
    String[] messageInbox;
    String accessToken;
    Api api;

    @Override
    public void onCreate() {
        // TODO Auto-generated method stub
        super.onCreate();
        myTimer = new Timer();
        myTimer.scheduleAtFixedRate(new Task(), START_TIME, RETRY_TIME);
        timerRunning = true;
        

    }

    @Override
    public void onDestroy() {
        // TODO Auto-generated method stub
        super.onDestroy();
        if (myTimer != null) {
            myTimer.cancel();
        }
        timerRunning = false;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {  
    	if (!timerRunning) {
            myTimer = new Timer();
            myTimer.scheduleAtFixedRate(new Task(), START_TIME, RETRY_TIME);
            timerRunning = true;
        }
        super.onStartCommand(intent, flags, startId);
        longPollParams[0] = intent.getStringExtra("key");
        longPollParams[1] = intent.getStringExtra("server");
        longPollParams[2] = intent.getStringExtra("ts");
        accessToken = intent.getStringExtra("access_token");
        api=new Api(accessToken, Constants.API_ID);
        return START_REDELIVER_INTENT;
    }

    @Override
    public IBinder onBind(Intent arg0) {        
        return null;
    }
    
    public class Task extends TimerTask {

    	@Override
        public void run() {
        	showPol();
        }
    }
    
    private void showPol() {
    	String infoFromLongPollServer = readFromLongPollServer();
        try {
        	JSONObject longPollRecieved = new JSONObject(infoFromLongPollServer);
            longPollParams[2] = longPollRecieved.getString("ts");
            JSONArray updates = longPollRecieved.getJSONArray("updates");
            
            String updateString =updates.toString();
            if (updateString.contains("[4,")){
            	ArrayList<JSONArray> inputMessages = new ArrayList<JSONArray>();
            	for (int i = 0; i<updates.length(); i++){
            		if (updates.getJSONArray(i).getInt(0) == 4){
            			inputMessages.add(updates.getJSONArray(i));
            		}
            	}
            	for (JSONArray inputMessage:inputMessages){
            		String flags = inputMessage.getString(2);
            		String userID = inputMessage.getString(3);
            		String message = inputMessage.getString(6);
            		
            		JSONArray userInfo = api.getProfile(userID);
            		JSONObject userInfo1 = userInfo.getJSONObject(0);
            		
            		String userFirstName = userInfo1.getString("first_name");
            		String userLastName = userInfo1.getString("last_name");
            		String userName = userFirstName + " " + userLastName;
            		
            		String flags1 = Long.toBinaryString(Long.valueOf(flags));
            		char inbox = flags1.charAt(flags1.length()-2);
            		if (inbox == '0') {
            			messageInbox = new String[]{userName,message};
            			sendAlert(messageInbox[0],messageInbox[1]);
            		}
            	}            
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    
    }	
	
	public String readFromLongPollServer() {
		StringBuilder builder = new StringBuilder();
		HttpClient client = new DefaultHttpClient();
		HttpGet httpGet = new HttpGet("http://"+longPollParams[1]+"?act=a_check&key="+longPollParams[0]+"&ts="+longPollParams[2]+"&wait=25&mode=2");
		try {
		    HttpResponse response = client.execute(httpGet);
		    StatusLine statusLine = response.getStatusLine();
		    int statusCode = statusLine.getStatusCode();
		    if (statusCode == 200) {
		        HttpEntity entity = response.getEntity();
		        InputStream content = entity.getContent();
		        BufferedReader reader = new BufferedReader(new InputStreamReader(content));
		        String line;
		        while ((line = reader.readLine()) != null) {
		            builder.append(line);
		        }
		    } else {
		        Log.e("ccc", "Failed to download file");
		    }
		} catch (ClientProtocolException e) {
		    e.printStackTrace();
		} catch (IOException e) {
		    e.printStackTrace();
		}
		return builder.toString();
	}
	
	public void sendAlert(String title,String message){
		sendAlertToPebble(title,message);
	}
	
	public void sendAlertToPebble(String title,String message) {
		final Intent i = new Intent("com.getpebble.action.SEND_NOTIFICATION");
		
		final Map data = new HashMap();
		data.put("title", title);
		data.put("body", message);
		final JSONObject jsonData = new JSONObject(data);
		final String notificationData = new JSONArray().put(jsonData).toString();
		
		i.putExtra("messageType", "PEBBLE_ALERT");
		i.putExtra("sender", "MyAndroidApp");
		i.putExtra("notificationData", notificationData);
		
		sendBroadcast(i);
	}
}