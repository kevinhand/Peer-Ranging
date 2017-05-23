package nus.hande.playsound;


import java.util.Calendar;
import java.util.Timer;
import java.util.TimerTask;

import nus.hande.playsound.AudioProcess.MyBinder;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.SoundPool;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.text.method.ScrollingMovementMethod;
import android.view.Menu;
import android.view.View;
import android.widget.TextView;

public class MainActivity extends Activity {
//	private final double durationOfEmpty = 0.5; // seconds
	private final double durationOfBeep = 0.05; // seconds
    private final int sampleRate = 44100;
//    private final int numSamplesOfEmpty = (int)( durationOfEmpty * sampleRate);
    private final int numSamplesOfBeep = (int)( durationOfBeep * sampleRate);
    
    private final double sample[] = new double[numSamplesOfBeep];
    private  double freqOfTone= 16000; // hz
    private int numberOfFrequency = 20;
    private TextView Textbox;
    private final byte generatedSnd[] = new byte[2 * (numSamplesOfBeep)];
    private AudioProcess maudioProcess = null;//处理
	private Intent mIntent = null;
	boolean FirstTime = true;
	private SoundPool soundPool;  
	private int soundId;  
	 private boolean soundLoaded = false;  

    Handler handler = new Handler();
    
    private int hour, min, second, millisecond;
    IntentFilter mIntentfilter = new IntentFilter ("nus.hande.playsound"); 
    private BroadcastReceiver mReceiver;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);	
		Textbox = (TextView)findViewById(R.id.result);
		Textbox.setMovementMethod(new ScrollingMovementMethod());
//		genTone();
		if(maudioProcess == null)
    	{
	    	mIntent = new Intent(MainActivity.this, AudioProcess.class);
	    	this.getApplicationContext().bindService(mIntent, conn, Context.BIND_AUTO_CREATE);	    	
    	}	 
		
		 soundPool = new SoundPool(5, AudioManager.STREAM_MUSIC, 0);
    	 soundId = soundPool.load(this, R.raw.beep50low, 1);
    	 soundPool.setOnLoadCompleteListener(new SoundPool.OnLoadCompleteListener() {  
             @Override  
             public void onLoadComplete(SoundPool soundPool, int sampleId, int status) {  
            	 soundLoaded = true;    //  表示加载完成  
             }  
         });  
    	 
		
		Calendar current = Calendar.getInstance();
		hour = current.get(Calendar.HOUR_OF_DAY);
		min = current.get(Calendar.MINUTE);
		second = current.get(second);
		millisecond = current.get(millisecond);
		
		mReceiver = new BroadcastReceiver(){

			@Override
			public void onReceive(Context context, Intent intent) {
				// TODO Auto-generated method stub
                try {
					Thread.sleep(500);
                } catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
                }
				
				maudioProcess.setBeepstart();
				 Thread thread = new Thread(new Runnable() {
			            public void run() {       
			                handler.post(new Runnable() {              	
			                    public void run() {                 	
			                    	  playSound();
			                    }
			                });
			            }
			        });
			        thread.start();
			        thread =null;       
			}
		};
		this.registerReceiver(mReceiver, mIntentfilter);
		
	}

	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}
	
	
	
	public void startButtonOnclick(View view){
		maudioProcess.setBeepstart();
		 Thread thread = new Thread(new Runnable() {
	            public void run() {       
	                handler.post(new Runnable() {              	
	                    public void run() {
//	                      for(int i=0;i<5;i++){                     	
	                    	  playSound();
//	                          try {
//								Thread.sleep(500);
//	                          } catch (InterruptedException e) {
//								// TODO Auto-generated catch block
//								e.printStackTrace();
//	                          }
//	                      }
	                    }
	                });
	            }
	        });
	        thread.start();
	        thread =null;
	        
	        
	}
	
	 Timer timer;
	  
	  class RemindTask extends TimerTask {
		  
	        public void run() {
	        	maudioProcess.setBeepstart();
	   		 Thread thread = new Thread(new Runnable() {
	   	            public void run() {       
	   	                handler.post(new Runnable() {              	
	   	                    public void run() {                  	
	   	                    	  playSound();
	   	                    }
	   	                });
	   	            }
	   	        });
	   	        thread.start();
	   	        thread =null;
	            timer.cancel(); //Terminate the timer thread
	        }
	    }
	
	public void ListenButtonOnclick(View view){
		if(FirstTime){
			maudioProcess.measureStart();
			FirstTime = false;
		}
		
//		
//		Calendar calendar = Calendar.getInstance();
//		calendar.set(Calendar.HOUR_OF_DAY, hour);
//		calendar.set(Calendar.MINUTE, min+1);
//		calendar.set(Calendar.SECOND, 20);
//		calendar.set(Calendar.MILLISECOND, 100);//master
////		calendar.set(Calendar.MILLISECOND, 500);//slave
//		Date time = calendar.getTime();
//
//		timer = new Timer();
//		timer.schedule(new RemindTask(), time);
	}

    @Override
    protected void onResume() {
        super.onResume();
    }

    void genTone(){
//    	 for (int i = 0; i < numSamplesOfEmpty; ++i) {
//         		sample[i]=0;
//         }   
    	 for (int i = 0; i < numSamplesOfBeep; ++i) {
          	for(int j=0;j<numberOfFrequency;j++){
          		sample[i]+=Math.sin(2 * Math.PI * i / (sampleRate/(freqOfTone+100*j)));
          	}
          	sample[i]=sample[i]/numberOfFrequency;
          }   
    	 
    	
        // convert to 16 bit pcm sound array
        // assumes the sample buffer is normalised.
        int idx = 0;
        for (final double dVal : sample) {
            // scale to maximum amplitude
            final short val = (short) ((dVal * 32760));
            // in 16 bit wav PCM, first byte is the low order byte
            generatedSnd[idx++] = (byte) (val & 0x00ff);
            generatedSnd[idx++] = (byte) ((val & 0xff00) >>> 8);

        }
    }

    void playSound(){
    	if(soundLoaded){  
            //  播放声音池中的文件, 可以指定播放音量，优先级 声音播放的速率  
            soundPool.play(soundId, 1.0f, 1.0f, 1, 0, 1.0f);  
        }
    	
    	
//        final AudioTrack audioTrack = new AudioTrack(AudioManager.STREAM_MUSIC,
//                sampleRate, AudioFormat.CHANNEL_OUT_MONO,
//                AudioFormat.ENCODING_PCM_16BIT, generatedSnd.length,
//                AudioTrack.MODE_STATIC);
//        audioTrack.write(generatedSnd, 0, generatedSnd.length);
//        	audioTrack.play();
    }
    
    @Override
	protected void onDestroy() {
    	soundPool.release();  
        soundPool = null;  
        maudioProcess.stop();
		if(maudioProcess!=null)
    	{
			maudioProcess.stop();
    		this.getApplicationContext().unbindService(conn);
    	}
    	
    	
		maudioProcess = null;
		super.onDestroy();
		
		this.unregisterReceiver(mReceiver);
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
            maudioProcess.start(freqOfTone,hour,min);// Double.parseDouble(freqField.getText().toString()));
           
        }
    };
   

}
