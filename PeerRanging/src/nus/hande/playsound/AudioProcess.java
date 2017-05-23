package nus.hande.playsound;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutionException;

import org.hermit.dsp.FFTTransformer;
import org.hermit.dsp.Window;

import biz.source_code.dsp.filter.FilterPassType;
import biz.source_code.dsp.filter.IirFilter;
import biz.source_code.dsp.filter.IirFilterCoefficients;
import biz.source_code.dsp.filter.IirFilterDesignExstrom;

import android.app.Service;
import android.content.Intent;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.os.Binder;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.SystemClock;
import android.widget.TextView;

public class AudioProcess extends Service{
	public static final float pi= (float)Math.PI;
	static final int channelConfiguration = AudioFormat.CHANNEL_IN_MONO;  
	static final int audioEncodeing = AudioFormat.ENCODING_PCM_16BIT; 
	int bufferSizeInBytes;//采集数据需要的缓冲区大小
	AudioRecord audioRecord;//录音
	String filename ;

	// Fourier Transform calculator we use for calculating the spectrum
	private FFTTransformer spectrumAnalyser;
		
	// Analyzed audio spectrum data;
	private float[] spectrumData;

	// The selected windowing function.
	private Window.Function windowFunction = Window.Function.BLACKMAN_HARRIS;
	
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

	private static double  mfrequency;
	int logcounter=0;
	private TextView mresult;
	private Handler mHandler = new Handler();

	private int inputBlockSize =1024;
	int buf = 3000;
	private static DataOutputStream logWriter=null;

	int mHour, mMin;
	private IirFilter iirFilterLow;
	private IirFilter iirFilterMiddle;
	private IirFilter iirFilterHigh;
	private long timeoffset = 0;
	
	//启动程序
	public void start(double freqOfTone, int hour, int min) {	
		audioReader = new AudioReader();
//		spectrumAnalyser = new FFTTransformer(inputBlockSize, windowFunction);
		// Allocate the spectrum data.
//		spectrumData = new float[inputBlockSize / 2];
		
		mfrequency = freqOfTone;
		mHour = hour;
		mMin = min;
		
//		try {
//			long now = new SntpClient().execute("sg.pool.ntp.org", null, null).get()+System.nanoTime() / 1000;
//			final String current = new Date(now).toString();
//			updateText("off set is"+current+" ms");
//		} catch (InterruptedException e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//		} catch (ExecutionException e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//		}
		
		IirFilterCoefficients coeffsHigh = IirFilterDesignExstrom.design( FilterPassType.bandpass, 4, 16000.0/44100.0, 18000.0/44100.0);
		IirFilterCoefficients coeffsLow = IirFilterDesignExstrom.design( FilterPassType.bandpass, 4, 11000.0/44100.0, 13000.0/44100.0);
		IirFilterCoefficients coeffsMiddle = IirFilterDesignExstrom.design( FilterPassType.bandpass, 4, 13500.0/44100.0, 15500.0/44100.0);
		iirFilterLow = new IirFilter(coeffsLow); 
		iirFilterMiddle = new IirFilter(coeffsMiddle); 
		iirFilterHigh = new IirFilter(coeffsHigh); 
		
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
	
	  Timer timer;
	  
	  class RemindTask extends TimerTask {
	        public void run() {
	        	TimeCount1=1;
	        	TimeCount2=1;
	            timer.cancel(); //Terminate the timer thread
	        }
	    }
	
	public void measureStart() {
		LogCreate();
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
		
		 //Get the Date corresponding to 11:01:00 pm today.
//		Calendar calendar = Calendar.getInstance();
//		calendar.set(Calendar.HOUR_OF_DAY, mHour);
//		calendar.set(Calendar.MINUTE, mMin+1);
//		calendar.set(Calendar.SECOND, 20);
//		calendar.set(Calendar.MILLISECOND, 400);//master
////		calendar.set(Calendar.MILLISECOND, 0);//slave
//		Date time = calendar.getTime();
//
//		timer = new Timer();
//		timer.schedule(new RemindTask(), time);
		
	}
	
	
	/**
	 * We are stopping / pausing the run; stop measurements.
	 */
	public void measureStop() {
		if(audioReader !=null)
			audioReader.stopReader();
		if(logWriter!=null){
			try {
				logWriter.flush();
				logWriter.close();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}

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

	int tick =-1;
	int framenum =-1;
	double magnitude =0;
	double Bmag = 0;
	int PeakIndex =0;
	int PeakIndex2=0;
	int lastPeakIndex =0;
	int lastPeakIndex2 =0;
	double SingleValue = 0;
	
	int SnCount = 0;
	int detectionTime =2*buf;
	double[] noiseBufLow = new double[2*buf]; 
	double[] noiseBufHigh = new double[2*buf]; 
	double averageInPeak =0;
	double averageInBackground =0;
	double lastValue=0;
	int outwieghtNum = 0;
	int outwieghtNum2 = 0;
	
	double high, mid, low=0;
	double phigh, pmid,plow=0;
	double Tn=0, Tn_1=0,Sn=0,Sn_1=0;;
	int TnCount = 0;
	double averageHigh=0, deviationHigh=0;
	double averageLow=0, deviationLow=0;
	double[] tempresult = new double[2];
	
	int t1=0,t2=0;
	//**************************************************
	private final void processAudio(short[] buffer){
		// Process the buffer. While reading it, it needs to be locked.
		short[]tmpBuf = new short[inputBlockSize];		

		synchronized (buffer) {
			final int len = buffer.length;
			System.arraycopy(buffer, 0, tmpBuf, len-inputBlockSize, inputBlockSize);
			// Tell the reader we're done with the buffer.
			buffer.notify();
		}
		
		framenum ++;
		if(framenum>50){
			for(int i=0;i < inputBlockSize; i++){
				tick++;
				double tmp = (double)tmpBuf[i];
				low = Math.abs(iirFilterLow.step(tmp));
				high = Math.abs( iirFilterHigh.step(tmp));
				mid = Math.abs( iirFilterMiddle.step(tmp));
						
					if(tick <2*buf){
						noiseBufLow[tick] = low;
//						noiseBufHigh[tick] = high;
					}
					else if (tick ==2*buf){
						tempresult = CalculateAverageAndDeviation(noiseBufLow, noiseBufLow.length);
						averageLow = tempresult[0];
						deviationLow = tempresult[1];				
//						tempresult = CalculateAverageAndDeviation(noiseBufHigh, noiseBufHigh.length);
//						averageHigh = tempresult[0];
//						deviationHigh = tempresult[1];	
						
						ZeroCounter();
					}
					else if(tick > detectionTime){
					
								Sn = MaxOfTwo(Sn_1+(low-averageLow), 0);
//								Tn = MaxOfTwo(Tn_1+(high-averageHigh), 0);
								
								
								if(low > averageLow+2*deviationLow  ){ //for 11-13k
								if(t1>=5){
									try {							
										logWriter.write((tick+" "+low+"\n").getBytes());
									} catch (IOException e) {
										// TODO Auto-generated catch block
										e.printStackTrace();
									}
									t1=0;
								}
								else{
									t1++;
								}
								}
																
								if(Sn > averageLow+3*deviationLow  ){ //for 11-13k
									averageInPeak+=low;
									if(low >high && low >mid)
										outwieghtNum++;
									SnCount++;	
								}					
								else{
									averageInPeak=0;
									SnCount=0;
									outwieghtNum =0;
								}
								
//								if( Tn >averageHigh+3*deviationHigh ){ //for 16-1k
//									if(high>mid )
//										outwieghtNum2++;
//									TnCount++;
//								}					
//								else{
//									TnCount=0;
//									outwieghtNum2 =0;
//								}
								
								if(SnCount>=80 ){		// below 1 meter
									averageInPeak=averageInPeak/SnCount;
									if(outwieghtNum >0.8*SnCount && averageInPeak > averageLow+deviationLow){ //below 1 meter
										
										PeakIndex = tick-SnCount+1;
										
										detectionTime = PeakIndex+20000;
//										t1=PeakIndex+20000;
		
										final int temp = PeakIndex;
										final double temp3 = round((double)outwieghtNum/(double)SnCount, 3);
										final String timeStamp = new SimpleDateFormat("HH:mm:ss").format(Calendar.getInstance().getTime());
										final double tempaverage = round(averageInPeak,2);
																
										//for master node
										if(TimeCount1 ==1){
											updateText("beep");
											TimeCount1 =0;
											lastPeakIndex = PeakIndex;
											updateText("11-13k peak "+temp+" in "+timeStamp+ " "+ temp3 +"##"+tempaverage );
											try {	
												logWriter.write(("beep" +"\n").getBytes());
												logWriter.write(("11-13k peak "+temp+" in "+timeStamp+ " "+ temp3 +"\n").getBytes());
											} catch (IOException e) {
												// TODO Auto-generated catch block
												e.printStackTrace();
											}
										}
										else{
											final double  tempint = round((PeakIndex-lastPeakIndex)/44.0,3);
											updateText("11-13k peak "+temp+" in "+timeStamp+ " "+ temp3 +"##"+tempaverage );
											
											try {		
												logWriter.write(("11-13k peak "+temp+" in "+timeStamp+ " "+ temp3 +"\n").getBytes());
												logWriter.write(("gap is "+tempint+" ms" +"\n").getBytes());
											} catch (IOException e) {
												// TODO Auto-generated catch block
												e.printStackTrace();
											}
											updateText("11-13k is "+tempint+" ms");							
										} //master
									
										//for slave node
//										if(TimeCount1 ==0){
//											lastPeakIndex = PeakIndex;
//											updateText("11-13k peak "+temp+" in "+timeStamp+ " "+ temp3 +"##"+tempaverage );
//											Intent k = new Intent("nus.hande.playsound");
//											this.sendBroadcast(k);
//											
//											try {	
//												logWriter.write(("11-13k peak "+temp+" in "+timeStamp+ " "+ temp3 +"\n").getBytes());
//											} catch (IOException e) {
//												// TODO Auto-generated catch block
//												e.printStackTrace();
//											}
//										}
//										else{
//											updateText("beep");
//											final double  tempint = round((PeakIndex-lastPeakIndex)/44.0,3);
//											updateText("11-13k peak "+temp+" in "+timeStamp+ " "+ temp3 +"##"+tempaverage );
//											updateText("11-13k is "+tempint+" ms");	
//											try {
//												logWriter.write(("beep" +"\n").getBytes());
//												logWriter.write(("11-13k peak "+temp+" in "+timeStamp+ " "+ temp3 +"\n").getBytes());
//												logWriter.write(("gap is "+tempint+" ms" +"\n").getBytes());
//											} catch (IOException e) {
//												// TODO Auto-generated catch block
//												e.printStackTrace();
//											}
//											TimeCount1 =0;
//										}	//slave
//										
										Sn = 0;
										Sn_1=0;
										SnCount=0;								
										outwieghtNum = 0;
										
										
										} //>0.85
									else{
										if(i>60){
											try {
											logWriter.write(("reset" +"\n").getBytes());
										} catch (IOException e) {
											// TODO Auto-generated catch block
											e.printStackTrace();
										}
											i = i-60;
											tick = tick -60;
										}
										
										Sn = averageLow;
										Sn_1=0;
										SnCount=0;								
										outwieghtNum = 0;
										
									}
									
								
								}
								
//								if(TnCount>=200){		
//									if(outwieghtNum2 >0.8*TnCount && tick > t2){
//										PeakIndex2= tick -TnCount+1;
//										
//										t2 = PeakIndex2+40000;
//		
//										final int temp2 = PeakIndex2;
//										final double temp4 = round((double)outwieghtNum2/(double)TnCount, 3);
//										final String timeStamp = new SimpleDateFormat("HH:mm:ss").format(Calendar.getInstance().getTime());
//										
//										
//										//for master node
////										if(TimeCount2 ==1){
////											updateText("beep");
////											TimeCount2 =0;
////											lastPeakIndex2 = PeakIndex2;
////											updateText("16-18k peak "+temp2+" in "+timeStamp+ " "+ temp4 );
////										}
////										else{
////											final int  tempint2 = (PeakIndex2-lastPeakIndex2)/44;
////											updateText("16-18k peak "+temp2+" in "+timeStamp+ " "+ temp4 );
////											updateText( " 16-18k is "+tempint2+" ms");							
////										}
//									
//										//for slave node
//										if(TimeCount2 ==0){
//											lastPeakIndex2 = PeakIndex2;
//											updateText("16-18k peak "+temp2+" in "+timeStamp+ " "+ temp4 );
//											Intent k = new Intent("nus.hande.playsound");
//											this.sendBroadcast(k);
//										}
//										else{
//											updateText("beep");
//											final int  tempint2 = (PeakIndex2-lastPeakIndex2)/44;
//											updateText("16-18k peak "+temp2+" in "+timeStamp+ " "+ temp4 );
//											updateText(" 16-18k is "+tempint2+" ms");	
//											TimeCount2 =0;
//										}	
//										
//										} //>0.85
//									
//									Tn = 0;
//									Tn_1=0;
//									TnCount=0;
//									outwieghtNum2 = 0;
//								}
								
							Sn_1 = Sn;
//							Tn_1 = Tn;
					}//tick >detectionTime
	
//				lastValue = magnitude;
			}//for each input sample
		
		}//jump pass first one second	
	}

	private final void processAudio2(short[] buffer){
		//Process the buffer. While reading it, it needs to be locked.
		short[] tmpBuf = new short[inputBlockSize];		
		int[] inputBuf = new int[inputBlockSize];	
		
		synchronized (buffer) {
			final int len = buffer.length;
			System.arraycopy(buffer, 0, tmpBuf, len-inputBlockSize, inputBlockSize);
			// Tell the reader we're done with the buffer.
			buffer.notify();
		}
		
		tick++;
		magnitude=0;
		Bmag =0;
		
		for(int i=0;i < inputBlockSize; i++){
			int tmpint = tmpBuf[i];
			inputBuf[i] = tmpint;
		}
		for(int i=0;i<20;i++){
			magnitude += goertzelFilter(inputBuf, 11000+100*i,inputBlockSize);
		}
		magnitude = magnitude/20;
		
		for(int i=0;i<20;i++){
			Bmag += goertzelFilter(inputBuf, 16000+100*i,inputBlockSize);
		}
		
		Bmag = Bmag/20;	
				
		if(tick>=buf)
		{
			if(tick <buf*2){
				noiseBufLow[tick-buf] = magnitude;
			}
			else if (tick ==buf*2){
				tempresult = CalculateAverageAndDeviation(noiseBufLow, noiseBufLow.length);
				averageLow = tempresult[0];
				deviationLow = tempresult[1];	
				Sn = 0;
				Sn_1=0;
				SnCount=0;
			}
			else if(tick > detectionTime){
		//		
		//		try {							
		//			logWriter.write((tick+" "+Bmag+" "+magnitude+" "+inputBlockSize+" "+spectrumData.length).getBytes());
		//			logWriter.write(("\n").getBytes());
		//		} catch (IOException e) {
		//			// TODO Auto-generated catch block
		//			e.printStackTrace();
		//		}
				
					Sn = MaxOfTwo(Sn_1+(magnitude-averageLow), 0);
						if(Sn > averageLow+2*deviationLow ){
							if(magnitude>=Bmag)
								outwieghtNum++;
							SnCount++;
							averageInPeak+=magnitude;	
		//					System.arraycopy(tmpBuf, 0, BackupBuf, 0, inputBlockSize);
						}					
						else{
							SnCount=0;
							averageInPeak=0;
							outwieghtNum =0;
						}
						
						if(SnCount>=40){		
							averageInPeak /=  SnCount;
		//					if(averageInPeak > average && outwieghtNum >0.8*SnCount){ //slave
								if(averageInPeak > averageLow+deviationLow && outwieghtNum >0.9*SnCount){ //master
								PeakIndex = tick-SnCount+1;
								detectionTime =tick+400;// PeakIndex;
		
								final int temp = PeakIndex;
								final double temp2 = averageInPeak;
								final  String timeStamp = new SimpleDateFormat("HH:mm:ss").format(Calendar.getInstance().getTime());
								final double temp3 = round((double)outwieghtNum/(double)SnCount,3);
			
								//for master node
//								if(TimeCount1 ==1){
//									updateText("beep");
//									TimeCount1 =0;
//									lastPeakIndex = PeakIndex;
//									updateText("Peak detected at "+temp+" in "+timeStamp+ " "+ temp3 );
//								}
//								else{
//									final int  tempint = (PeakIndex-lastPeakIndex)/2;
//									updateText("Peak detected at "+temp+" in "+timeStamp+ " "+ temp3 );
//									updateText("gap is "+tempint+" ms");							
//								}
							
								//for slave node
								if(TimeCount1 ==0){
									lastPeakIndex = PeakIndex;
									updateText("Peak detected at "+temp+" in "+timeStamp+ " "+ temp3 );
									Intent k = new Intent("nus.hande.playsound");
									this.sendBroadcast(k);
								}
								else{
									updateText("beep");
									final int  tempint = (PeakIndex-lastPeakIndex)/2;
									updateText("Peak detected at "+temp+" in "+timeStamp+ " "+ temp3 );
									updateText("gap is "+tempint+" ms");	
									TimeCount1 =0;
								}		
								
							}
							Sn = 0;
							Sn_1=0;
							SnCount=0;
							averageInPeak=0;
							outwieghtNum =0;				
						}
					Sn_1 = Sn;
			}//tick >detectionTime
		}//tick >1500
//		lastValue = magnitude;
}
	
	
	private void ZeroCounter(){
		Sn = 0;
		Sn_1=0;
		SnCount=0;
//		Tn = 0;
//		Tn_1=0;
//		TnCount=0;
		outwieghtNum = 0;
//		outwieghtNum2 = 0;
	}
	private double MaxOfTwo(double a, double b){
		if (a>=b)
			return a;
		else
			return b;
	}
	
	private int MaxOfTwo(int a, int b){
		if (a>=b)
			return a;
		else
			return b;
	}
	
	
	private double[] CalculateAverageAndDeviation(double[] noiseBuf, int length) {
		// TODO Auto-generated method stub
		double sum=0;
		for(int i=0;i<length;i++){
			sum += noiseBuf[i];
		}
		double Theaverage = sum/length;
		
		sum =0;
		for(int i=0;i<length;i++){
			sum+=(noiseBuf[i]-Theaverage)*(noiseBuf[i]-Theaverage);
		}
		double Thedeviation =Math.sqrt(sum/(double)length);
		
		final double a = Theaverage;
		final double b = Thedeviation;//average+3*deviation;
		updateText("average is "+a);
		updateText("deviation is "+b);
		
		try {
			logWriter.write(("average is "+a +"\n").getBytes());
			logWriter.write(("deviation is "+b +"\n").getBytes());
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		
		double[] result = new double[2];
		result[0] = Theaverage;
		result[1] = Thedeviation;
		return result;
	}

	public void updateText(final String s){
		mHandler.post(new Runnable() {
            @Override
            public void run() {
                // This gets executed on the UI thread so it can safely modify Views
            	mresult.setText(mresult.getText()+s+ "\n");
            }
        });
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
	    if(power <1){
	    	power =1;
	    }
	    return Math.log10(power);
	}
	
	int TimeCount1 =0;
	int TimeCount2 =0;
	public void setBeepstart(){
		TimeCount1=1;
		TimeCount2=1;
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



//private final void processAudio(short[] buffer){
//// Process the buffer. While reading it, it needs to be locked.
//short[]tmpBuf = new short[inputBlockSize];		
//
//double[]inputBuf = new double[inputBlockSize];	
//double[]BackBuf = new double[inputBlockSize];	
//synchronized (buffer) {
//	final int len = buffer.length;
//	System.arraycopy(buffer, 0, tmpBuf, len-inputBlockSize, inputBlockSize);
//	// Tell the reader we're done with the buffer.
//	buffer.notify();
//}
//
//framenum ++;
//if(framenum>20){
//	for(int i=0;i < inputBlockSize; i++){
//		tick++;
//		double tmp = (double)tmpBuf[i];
//		magnitude = Math.abs(iirFilterLow.step(tmp));
//		Bmag = Math.abs( iirFilterHigh.step(tmp));
//					
//			if(tick <2000){
//				noiseBuf[tick] = magnitude;
//			}
//			else if (tick ==2000){
//				CalculateAverageAndDeviation(noiseBuf, noiseBuf.length);
//				Sn = 0;
//				Sn_1=0;
//				SnCount=0;
//			}
//			else if(tick > detectionTime){					
//						Sn = MaxOfTwo(Sn_1+(magnitude-average), 0);
//						if(Sn > average+3*deviation ){
//							if(magnitude>Bmag)
//								outwieghtNum++;
//							SnCount++;
//							averageInPeak+=magnitude;	
//							averageInBackground +=Bmag;
//						}					
//						else{
//							SnCount=0;
//							averageInPeak=0;
//							outwieghtNum =0;
//							averageInBackground =0;
//						}
//						
//						if(SnCount>=100){		
//							averageInPeak /=  SnCount;
//							averageInBackground /=SnCount;
//							if(averageInPeak > average+deviation && outwieghtNum >0.85*SnCount){
////							if(averageInPeak > average){
//								PeakIndex = tick-SnCount+1;
//								detectionTime = PeakIndex+80000;
//
//								final int temp = PeakIndex;
//								final double temp2 = averageInPeak;
//								final double temp3 = round((double)outwieghtNum/(double)SnCount, 3);
//								final String timeStamp = new SimpleDateFormat("HH:mm:ss").format(Calendar.getInstance().getTime());
//								
//								
//								//for master node
////								if(TimeCount ==1){
////									updateText("beep");
////									TimeCount =0;
////									lastPeakIndex = PeakIndex;
////									updateText("Peak detected at "+temp+" in "+timeStamp+ " "+ temp3 );
////								}
////								else{
////									final int  tempint = (PeakIndex-lastPeakIndex)/44;
////									updateText("Peak detected at "+temp+" in "+timeStamp+ " "+ temp3 );
////									updateText("gap is "+tempint+" ms");							
////								}
//							
//								//for slave node
//								if(TimeCount ==0){
//									lastPeakIndex = PeakIndex;
//									updateText("Peak detected at "+temp+" in "+timeStamp+ " "+ temp3 );
//									Intent k = new Intent("nus.hande.playsound");
//									this.sendBroadcast(k);
//								}
//								else{
//									updateText("beep");
//									final int  tempint = (PeakIndex-lastPeakIndex)/44;
//									updateText("Peak detected at "+temp+" in "+timeStamp+ " "+ temp3 );
//									updateText("gap is "+tempint+" ms");	
//									TimeCount =0;
//								}	
//								
//								}
//								Sn = 0;
//								Sn_1=0;
//								SnCount=0;
//								averageInPeak=0;
//								outwieghtNum =0;
//								averageInBackground =0;
//						}
//					Sn_1 = Sn;
//			}//tick >detectionTime
//
//		lastValue = magnitude;
//	}//for each input sample
//
//}//jump pass first one second	
//}






//if(SnCount>=150 && TnCount>=100){		
//	if(outwieghtNum >0.8*SnCount && outwieghtNum2 >0.8*TnCount ){
//		PeakIndex = tick-SnCount+1;
//		PeakIndex2= tick -TnCount+1;
//		
//		detectionTime = MaxOfTwo(PeakIndex, PeakIndex2)+40000;
//
//		final int temp = PeakIndex;
//		final int temp2 = PeakIndex2;
//		final double temp3 = round((double)outwieghtNum/(double)SnCount, 3);
//		final double temp4 = round((double)outwieghtNum2/(double)TnCount, 3);
//		final String timeStamp = new SimpleDateFormat("HH:mm:ss").format(Calendar.getInstance().getTime());
//		
//		
//		//for master node
//		if(TimeCount ==1){
//			updateText("beep");
//			TimeCount =0;
//			lastPeakIndex = PeakIndex;
//			lastPeakIndex2 = PeakIndex2;
//			updateText("11-13k peak "+temp+" in "+timeStamp+ " "+ temp3 );
//			updateText("16-18k peak "+temp2+" in "+timeStamp+ " "+ temp4 );
//		}
//		else{
//			final int  tempint = (PeakIndex-lastPeakIndex)/44;
//			final int  tempint2 = (PeakIndex2-lastPeakIndex2)/44;
//			updateText("11-13k peak "+temp+" in "+timeStamp+ " "+ temp3 );
//			updateText("16-18k peak "+temp2+" in "+timeStamp+ " "+ temp4 );
//			updateText("11-13k is "+tempint+" ms"+ " 16-18k is "+tempint2+" ms");							
//		}
//	
//		//for slave node
////		if(TimeCount ==0){
////			lastPeakIndex = PeakIndex;
////			lastPeakIndex2 = PeakIndex2;
////			updateText("11-13k peak "+temp+" in "+timeStamp+ " "+ temp3 );
////			updateText("16-18k peak "+temp2+" in "+timeStamp+ " "+ temp4 );
////			Intent k = new Intent("nus.hande.playsound");
////			this.sendBroadcast(k);
////		}
////		else{
////			updateText("beep");
////			final int  tempint = (PeakIndex-lastPeakIndex)/44;
////			final int  tempint2 = (PeakIndex2-lastPeakIndex2)/44;
////			updateText("11-13k peak "+temp+" in "+timeStamp+ " "+ temp3 );
////			updateText("16-18k peak "+temp2+" in "+timeStamp+ " "+ temp4 );
////			updateText("11-13k is "+tempint+" ms"+ " 16-18k is "+tempint2+" ms");	
////			TimeCount =0;
////		}	
//		
//		} //>0.85
//	
//		ZeroCounter();
//}