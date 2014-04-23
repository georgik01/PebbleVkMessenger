package com.pebble.vk.messenger;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

import org.apache.http.client.HttpClient;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.perm.kate.api.Api;
import com.perm.kate.api.KException;
import com.perm.kate.api.sample.R;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;

public class MainActivity extends Activity {
    
	private final int REQUEST_LOGIN=1;
    Object[] longPollParams;
    Object[] longPollParamsNew;
    String[] messageInbox;
    Thread longPollThread;
    
    Button authorizeButton;
    Button logoutButton;
    Button postButton;
    Button showButton;
    Button show1Button;
    EditText messageEditText;
    
    Account account=new Account();
    Api api;
    
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        
        setupUI();
        
        //Восстановление сохранённой сессии
        account.restore(this);
        
        //Если сессия есть создаём API для обращения к серверу
        if(account.access_token!=null){
            api=new Api(account.access_token, Constants.API_ID);
            startLongPoll();}
        
        showButtons();        
    }

    private void setupUI() {
        authorizeButton=(Button)findViewById(R.id.authorize);
        logoutButton=(Button)findViewById(R.id.logout);    
        authorizeButton.setOnClickListener(authorizeClick);
        logoutButton.setOnClickListener(logoutClick);
    }
    
    private OnClickListener authorizeClick=new OnClickListener(){
        @Override
        public void onClick(View v) {
            startLoginActivity();
        }
    };
    
    private OnClickListener logoutClick=new OnClickListener(){
        @Override
        public void onClick(View v) {
            logOut();
        }
    };
    
    private void startLoginActivity() {
        Intent intent = new Intent();
        intent.setClass(this, LoginActivity.class);
        startActivityForResult(intent, REQUEST_LOGIN);
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_LOGIN) {
            if (resultCode == RESULT_OK) {
                //авторизовались успешно 
                account.access_token=data.getStringExtra("token");
                account.user_id=data.getLongExtra("user_id", 0);
                account.save(MainActivity.this);
                api=new Api(account.access_token, Constants.API_ID);
                showButtons();
                startLongPoll();
            }
        }
    }
    
    private void logOut() {
        api=null;
        account.access_token=null;
        account.user_id=0;
        account.save(MainActivity.this);
        showButtons();
        longPollThread.interrupt();
    }
    
    
    private void startLongPoll() {
        //Общение с сервером в отдельном потоке чтобы не блокировать UI поток
       longPollThread = new Thread(){
            @Override
            public void run(){
            	
                try {
                	longPollParams = api.getLongPollServer();
                	while (!Thread.interrupted()){
                	showPol();
                	}
                } catch (Exception e) {
                    e.printStackTrace();
                    sendAlert("exeption","error getting info from longpoll");
                }
                
            }
        };
        longPollThread.start();
    }
    
    private void showPol() {
            	
            	String infoFromLongPollServer = readFromLongPollServer();
                try {
                	JSONObject longPollRecieved = new JSONObject(infoFromLongPollServer);
                    longPollParams[2] = longPollRecieved.getLong("ts");
                    JSONArray updates = longPollRecieved.getJSONArray("updates");
                    String updateString =updates.toString();
                    if (updateString.contains("[4,")){
                    	stringSplitter(updateString);
                    
                    Log.i("ggg",
                            "блабла " + messageInbox[0] + messageInbox[1]);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            
    }
    
    public void stringSplitter(String message) throws IOException, JSONException, KException{
    	String messageToSplit = message;
    	String[] parts1 = messageToSplit.split("\\[4");
    	String[] parts2 = parts1[1].split("\\]");
    	String messageRaw = parts2[0];
    	String[] parts3 = messageRaw.split("\\,");
    	String userID = parts3[3];
    	String flags = parts3[2];
    	String messageRaw1 = parts3[6];
    	
    	JSONArray userInfo = api.getProfile(userID);
    	JSONObject userInfo1 = userInfo.getJSONObject(0);
    	
    	String userFirstName = userInfo1.getString("first_name");
    	String userLastName = userInfo1.getString("last_name");
    	String userName = userFirstName + " " + userLastName;
    	String flags1 = Long.toBinaryString(Long.valueOf(flags));
    	char inbox = flags1.charAt(flags1.length()-2);
    	if (inbox == '0') {
    		StringBuilder sb = new StringBuilder(messageRaw1);
    		sb.deleteCharAt(messageRaw1.length()-1);
    		sb.deleteCharAt(0);
    		String messageFinal = sb.toString();
    		messageInbox = new String[]{userName,messageFinal};
    		sendAlert(messageInbox[0],messageInbox[1]);
    	}
    };
    
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
    
    void showButtons(){
        if(api!=null){
            authorizeButton.setVisibility(View.GONE);
            logoutButton.setVisibility(View.VISIBLE);
        }else{
            authorizeButton.setVisibility(View.VISIBLE);
            logoutButton.setVisibility(View.GONE);
        }
    }
}