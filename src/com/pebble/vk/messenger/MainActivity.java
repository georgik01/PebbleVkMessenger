package com.pebble.vk.messenger;

import com.perm.kate.api.Api;
import com.perm.kate.api.sample.R;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;

public class MainActivity extends Activity {
    
	private final int REQUEST_LOGIN=1;
    Object[] longPollParams;
    Object[] longPollParamsNew;
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
    	stopService(new Intent(this,PebbleService.class));
    }
    
    private void startLongPoll() {
        //Общение с сервером в отдельном потоке чтобы не блокировать UI поток
    	 new Thread(){
             @Override
             public void run(){
         try {
	        	 longPollParams = api.getLongPollServer();
	        	 Intent intent = new Intent(getApplicationContext(), PebbleService.class);
	             intent.putExtra("key",longPollParams[0].toString());
	             intent.putExtra("server",longPollParams[1].toString());
	             intent.putExtra("ts",longPollParams[2].toString());
	             intent.putExtra("access_token",account.access_token);
	             startService(intent);
             } catch (Exception e) {
	             e.printStackTrace();
             }
             }}.start();
    }
    
    void showButtons(){
        if(api!=null&&account.access_token!=null){
            authorizeButton.setVisibility(View.GONE);
            logoutButton.setVisibility(View.VISIBLE);
        }else{
            authorizeButton.setVisibility(View.VISIBLE);
            logoutButton.setVisibility(View.GONE);
        }
    }
}