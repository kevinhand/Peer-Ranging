package nus.hande.playsound;


import nus.hande.playsound.AudioProcess.MyBinder;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.text.method.ScrollingMovementMethod;
import android.view.Menu;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

public class MainActivity extends Activity {

    private  double freqOfTone= 16500; // hz
    private TextView Textbox;
    private AudioProcess maudioProcess = null;//处理
	private Intent mIntent = null;
	boolean FirstTime = true;

   

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);	
		Textbox = (TextView)findViewById(R.id.result);
		Textbox.setMovementMethod(new ScrollingMovementMethod());
		
		if(maudioProcess == null)
    	{
	    	mIntent = new Intent(MainActivity.this, AudioProcess.class);
	    	this.getApplicationContext().bindService(mIntent, conn, Context.BIND_AUTO_CREATE);	    	
    	}
		 
	}

	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}
	
	public void startButtonOnclick(View view){
		if(FirstTime){
			maudioProcess.measureStart();
			FirstTime = false;
		}

	}
	
	public void ListenButtonOnclick(View view){
		
	}

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
	protected void onDestroy() {
		super.onDestroy();
		maudioProcess.stop();
		if(maudioProcess!=null)
    	{
			maudioProcess.stop();
    		this.getApplicationContext().unbindService(conn);
    	}
		maudioProcess = null;
		
	}
    
private ServiceConnection conn = new ServiceConnection() {
        
        public void onServiceDisconnected(ComponentName name) {
            // TODO Auto-generated method stub
        	maudioProcess.onDestroy();
        }
        
        public void onServiceConnected(ComponentName name, IBinder service) {
            // TODO Auto-generated method stub
            MyBinder binder = (MyBinder)service;
            maudioProcess = binder.getService();
            maudioProcess.setElement(Textbox);
            maudioProcess.start(freqOfTone);// Double.parseDouble(freqField.getText().toString()));
           
        }
    };
   

}
