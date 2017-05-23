package nus.hande.playsound;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;

import android.app.Service;
import android.content.Intent;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.os.Binder;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.widget.TextView;

public class AudioProcess extends Service{
	public static final float pi= (float)Math.PI;
	static final int channelConfiguration = AudioFormat.CHANNEL_IN_MONO;  
	static final int audioEncodeing = AudioFormat.ENCODING_PCM_16BIT; 
	int bufferSizeInBytes;//采集数据需要的缓冲区大小
	AudioRecord audioRecord;//录音
	String filename ;
	
	// Buffered audio data, and sequence number of the latest block.
	private short[] audioData;
	private long audioSequence = 0;

	// If we got a read error, the error code.
	private int readError = AudioReader.Listener.ERR_OK;

	// Sequence number of the last block we processed.
	private long audioProcessed = 0;
	
	// Our audio input device.
	private AudioReader audioReader;

	public final static int AUDIO_SAMPLE_RATE = 44100;  //44.1KHz,普遍使用的频率   
	public static double REFERENCE = 0.00002;
	private static double  mfrequency;
	int logcounter=0;
	private TextView mresult;
	private Handler mHandler = new Handler();

	private int inputBlockSize =11;
	private static DataOutputStream logWriter=null;
	int runcounter =0;

	private int HistLength ;
	
	//启动程序
	public void start(double freqOfTone) {	
		audioReader = new AudioReader();
		HistLength = 20*44/inputBlockSize;	
		mfrequency = freqOfTone;
		
		 genTone();
	}
	
	/**
	 * We are starting the main run; start measurements.
	 */

	public void LogCreate(){
		if(logWriter == null){
			String path = Environment.getExternalStorageDirectory().getAbsolutePath();
			File folder= new File(path+"/SoundLog/");
	    	if (!folder.exists()) {
	    		folder.mkdirs();
	    	}		    	
	    	logcounter++;
	    	File f = new File(path+"/SoundLog/Sound"+logcounter+".txt");

	    	while(f.exists()){
	    		logcounter++;
	    		f = new File(path+"/SoundLog/Sound"+logcounter+".txt");
	    	}	    	
	    	
	    	try {
				f.createNewFile();
				FileOutputStream fos = new FileOutputStream(f);
				logWriter = new DataOutputStream(fos);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}
	
	public void measureStart() {
//		LogCreate();
		audioProcessed = audioSequence = 0;
		readError = AudioReader.Listener.ERR_OK;
		audioReader.startReader(AUDIO_SAMPLE_RATE, inputBlockSize,
				new AudioReader.Listener() {
					@Override
					public final void onReadComplete(short[] buffer) {
						receiveAudio(buffer);
						doUpdate();
					}

					@Override
					public void onReadError(int error) {
						handleError(error);
					}
				});
		
	}
	
	public void ResetCounter(){
		runcounter =0;
	}
	
	/**
	 * We are stopping / pausing the run; stop measurements.
	 */
	public void measureStop() {
		audioReader.stopReader();
//		try {
//			logWriter.flush();
//			logWriter.close();
//		} catch (IOException e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//		}
	}
	
	/**
	 * Handle audio input. This is called on the thread of the audio reader.
	 * 
	 * @param buffer
	 *            Audio data that was just read.
	 */
	private final void receiveAudio(short[] buffer) {
		// Lock to protect updates to these local variables. See run().
		synchronized (this) {
			audioData = buffer;
			++audioSequence;
		}
	}
	
	/**
	 * An error has occurred. The reader has been terminated.
	 * 
	 * @param error
	 *            ERR_XXX code describing the error.
	 */
	private void handleError(int error) {
		synchronized (this) {
			readError = error;
		}
	}
	
	public void doUpdate() {
			short[] buffer = null;
			synchronized (this) {
				if (audioData != null && audioSequence > audioProcessed) {				
					audioProcessed = audioSequence;
					buffer = audioData;
				}
			}

			// If we got data, process it without the lock.
			if (buffer != null && buffer.length ==inputBlockSize )
				processAudio(buffer);
		
	}
	
	
	 private final int numSamples = 400;//(int)( duration * sampleRate);
	 private final byte generatedSnd[] = new byte[2 * numSamples];
	 private final double sample[] = new double[numSamples];
	 
	 
   void genTone(){
  	 for (int i = 0; i < numSamples; ++i) {
           sample[i] = Math.sin(2 * Math.PI * i / (AUDIO_SAMPLE_RATE/(mfrequency+2000)));
       }   
  	
      // convert to 16 bit pcm sound array
      // assumes the sample buffer is normalised.
      int idx = 0;
      for (final double dVal : sample) {
          // scale to maximum amplitude
          final short val = (short) ((dVal * 32700));
          // in 16 bit wav PCM, first byte is the low order byte
          generatedSnd[idx++] = (byte) (val & 0x00ff);
          generatedSnd[idx++] = (byte) ((val & 0xff00) >>> 8);

      }
  }
    
   void playSound(){
       final AudioTrack audioTrack = new AudioTrack(AudioManager.STREAM_MUSIC,
    		   AUDIO_SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO,
               AudioFormat.ENCODING_PCM_16BIT, generatedSnd.length,
               AudioTrack.MODE_STATIC);
       audioTrack.write(generatedSnd, 0, generatedSnd.length);
       audioTrack.play();
       //System.nanoTime();
   }
	
   Handler handler = new Handler();
	double magnitude =1;
	double delta=0;
	double lastValue=0;
	boolean FirstPeakDetected= false;

	private final void processAudio(short[] buffer){
		short[]tmpBuf = new short[inputBlockSize];	
		int[]inputBuf = new int[inputBlockSize];	
		synchronized (buffer) {
			final int len = buffer.length;
			System.arraycopy(buffer, 0, tmpBuf, len-inputBlockSize, inputBlockSize);
			// Tell the reader we're done with the buffer.
			buffer.notify();
		}
		
		for(int i=0;i < inputBlockSize; i++){
			int tmpint = tmpBuf[i];
			inputBuf[i] = tmpint;
	}

		if(!FirstPeakDetected){
			magnitude = Math.log10(goertzelFilter(inputBuf, mfrequency,inputBlockSize));
			if(magnitude -lastValue>3.0 &&magnitude >5 &&lastValue>2){
				FirstPeakDetected =true;	
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
		    	mHandler.post(new Runnable() {
		            @Override
		            public void run() {
		                // This gets executed on the UI thread so it can safely modify Views
		            	mresult.setText(mresult.getText()+ "Send Back signal"+"\n");
		            }
		        });
				
				mHandler.post(new Runnable() {
		            @Override
		            public void run() {
		                // This gets executed on the UI thread so it can safely modify Views
		            	mresult.setText(mresult.getText()+ "First peak detected"+"\n");
		            }
		        });
			}
			lastValue = magnitude;
		}
}
	
	public double goertzelFilter(int samples[], double freq, int N) {
	    double s_prev = 0.0;
	    double s_prev2 = 0.0;    
	    double coeff,normalizedfreq,power,s;
	    int i;
	    normalizedfreq = freq / AUDIO_SAMPLE_RATE;
	    coeff = 2*Math.cos(2*Math.PI*normalizedfreq);
	    for (i=0; i<N; i++) {
	        s = samples[i] + coeff * s_prev - s_prev2;
	        s_prev2 = s_prev;
	        s_prev = s;
	    }
	    power = s_prev2*s_prev2+s_prev*s_prev-coeff*s_prev*s_prev2;
	    return power;
	}
	
	public  double round(double value, int places) {
	    if (places < 0) throw new IllegalArgumentException();

	    BigDecimal bd = new BigDecimal(value);
	    bd = bd.setScale(places, RoundingMode.HALF_UP);
	    return bd.doubleValue();
	}
	
	
	public void setElement(TextView t1)
	{
		mresult = t1;
		mresult.setText("");
	}
	
	//停止程序
	public void stop(){
		measureStop();
	}
	@Override
    public void onDestroy() {
    	super.onDestroy();
	}
	@Override
	public IBinder onBind(Intent intent) {
		// TODO Auto-generated method stub
		IBinder result = null;
	    if (null == result) {
	        result = new MyBinder();
	    }
	    return result;
	}
	
	public class MyBinder extends Binder{
	    
	    public AudioProcess getService(){
	        return AudioProcess.this;
	    }
	}

}
