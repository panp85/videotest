package com.example.videotest;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;

import android.media.MediaRecorder;
import android.net.LocalServerSocket;
import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import android.os.Bundle;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.util.Log;
import android.view.Menu;
import android.view.SurfaceHolder;
import android.view.SurfaceHolder.Callback;
import android.view.SurfaceView;
import android.view.View;

public class VideoTest extends Activity implements Callback, Runnable{

	private static final String TAG = "VideoCamera";
	
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.videotest);
        InitSurfaceView();
        InitMediaSharePreference();
    }

    //初始化SurfaceView
    private SurfaceView mSurfaceView;
	private void InitSurfaceView() {
		mSurfaceView = (SurfaceView) this.findViewById(R.id.surface_camera);
		SurfaceHolder holder = mSurfaceView.getHolder();
		holder.addCallback(this);
		holder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
		mSurfaceView.setVisibility(View.VISIBLE);
	}
	
	//初始化，记录mdat开始位置的参数
	SharedPreferences sharedPreferences;
	private final String mediaShare = "media";
    private void InitMediaSharePreference() {
		sharedPreferences = this.getSharedPreferences(mediaShare, MODE_PRIVATE);		
	}


    private SurfaceHolder mSurfaceHolder;
    private boolean mMediaRecorderRecording = false;
    
	public void surfaceCreated(SurfaceHolder holder) {
		mSurfaceHolder = holder;
	}

	public void surfaceChanged(SurfaceHolder holder, int format, int width,
			int height) {
		mSurfaceHolder = holder;
		if(!mMediaRecorderRecording) {
			InitLocalSocket();
			getSPSAndPPS();
			initializeVideo();
			startVideoRecording();
		}
		
	}

	public void surfaceDestroyed(SurfaceHolder holder) {
		// TODO Auto-generated method stub
		
	}
	
	//初始化LocalServerSocket LocalSocket
	LocalServerSocket lss;
	LocalSocket receiver, sender;
	
	private void InitLocalSocket(){
		try {
			lss = new LocalServerSocket("H264");
			receiver = new LocalSocket();
			
			receiver.connect(new LocalSocketAddress("H264"));
			receiver.setReceiveBufferSize(500000);
			receiver.setSendBufferSize(50000);
			
			sender = lss.accept();
			sender.setReceiveBufferSize(500000);
			sender.setSendBufferSize(50000);
			
		} catch (IOException e) {
			Log.e(TAG, e.toString());
			this.finish();
			return;
		}
		
	}
	
	//得到序列参数集SPS和图像参数集PPS,如果已经存储在本地
	private void getSPSAndPPS(){
		StartMdatPlace = sharedPreferences.getInt(
				String.format("mdata_%d%d.mdat", videoWidth, videoHeight), -1);
		
		if(StartMdatPlace != -1) {
			byte[] temp = new byte[100];
			try {
				FileInputStream file_in = VideoTest.this.openFileInput(
						String.format("%d%d.sps", videoWidth,videoHeight));
				
				int index = 0;
				int read=0;
				while(true)
				{
					read = file_in.read(temp,index,10);
					if(read==-1) break;
					else index += read;
				}
				Log.e(TAG, "=====get sps length:"+index);
				SPS = new byte[index];
				System.arraycopy(temp, 0, SPS, 0, index);
				               
				file_in.close();
				
				index =0;
				//read PPS
				file_in = VideoTest.this.openFileInput(
						String.format("%d%d.pps", videoWidth,videoHeight));
				while(true)
				{
					read = file_in.read(temp,index,10);
					if(read==-1) break;
					else index+=read;
				}
				Log.e(TAG, "==========get pps length:"+index);
				PPS = new byte[index];
				System.arraycopy(temp, 0, PPS, 0, index);
			} catch (FileNotFoundException e) {
				//e.printStackTrace();
				Log.e(TAG, e.toString());
			} catch (IOException e) {
				//e.printStackTrace();
				Log.e(TAG, e.toString());
			}
		} else {
			Log.e(TAG,"==============StartMdatPlace = -1");
			SPS = null;
			PPS = null;
		}
	}
	
	//初始化MediaRecorder
	private MediaRecorder mMediaRecorder = null;
	private int videoWidth = 320;
	private int videoHeight = 240;
	private int videoRate = 10;
	
	private String fd = "/sdcard/videotest.3gp";
	
	private boolean initializeVideo(){
		if(mSurfaceHolder == null) {
			return false;
		}
		
		mMediaRecorderRecording = true;
		
		if(mMediaRecorder == null) {
			mMediaRecorder = new MediaRecorder();
		} else {
			mMediaRecorder.reset();
		}
		
		mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);
		//mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
		mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
		mMediaRecorder.setVideoFrameRate(videoRate);
		mMediaRecorder.setVideoSize(videoWidth, videoHeight);
		mMediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
		//mMediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
		mMediaRecorder.setPreviewDisplay(mSurfaceHolder.getSurface());
		mMediaRecorder.setMaxDuration(0);
		mMediaRecorder.setMaxFileSize(0);
		if(SPS==null)
		{
			Log.e(TAG, "==============  SPS  is null!!!!!!!!!!");
			mMediaRecorder.setOutputFile(fd);
		}
		else
		{
			Log.e(TAG,"=============== SPS have value!!!!!!!");
			mMediaRecorder.setOutputFile(sender.getFileDescriptor());
		}

		try {
			mMediaRecorder.prepare();
			mMediaRecorder.start();
		} catch (IllegalStateException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
			releaseMediaRecorder();
		}
		
		return true;
	}

	//释放MediaRecorder资源
	private void releaseMediaRecorder(){
		if(mMediaRecorder != null) {
			if(mMediaRecorderRecording) {
				mMediaRecorder.stop();
				mMediaRecorderRecording = false;
			}
			mMediaRecorder.reset();
			mMediaRecorder.release();
			mMediaRecorder = null;
		}
	}
	
	//开始录像，启动线程
	private void startVideoRecording() {
		new Thread(this).start();
	}
	
	private final int MAXFRAMEBUFFER = 20480;//20K
	private byte[] h264frame = new byte[MAXFRAMEBUFFER];
	private final byte[] head = new byte[]{0x00,0x00,0x00,0x01};
	private RandomAccessFile file_test;
	public void run() {
		try {
			
			if(SPS == null) {
				Log.e(TAG, "Rlease MediaRecorder and get SPS and PPS");
				Thread.sleep(1000);
				//释放MediaRecorder资源
				releaseMediaRecorder();
				//从已采集的视频数据中获取SPS和PPS
				findSPSAndPPS();
				//找到后重新初始化MediaRecorder
				initializeVideo();
			}			
			
			DataInputStream dataInput = new DataInputStream(receiver.getInputStream());
			//先读取ftpy box and mdat box, 目的是skip ftpy and mdat data,(decisbe by phone)
			Log.d(TAG,"=============StartMdatPlace :" + StartMdatPlace);
			dataInput.read(h264frame, 0, StartMdatPlace);
			
			try {
				File file = new File("/sdcard/encoder.h264");
				if (file.exists())
					file.delete();
				file_test = new RandomAccessFile(file, "rw");
			} catch (Exception ex) {
				Log.v("System.out", ex.toString());
			}
			file_test.write(head);
			file_test.write(SPS);//write sps
			
			file_test.write(head);
			file_test.write(PPS);//write pps
			
			int h264length =0;
			
			while(mMediaRecorderRecording) {
				h264length = dataInput.readInt();
				Log.e(TAG, "h264 length :" + h264length);
//				int number=0 , num=0;
//				int frame_size = 1024;
//				file_test.write(head);
//				while(number<h264length)
//				{
//					int lost=h264length-number;
//					num = dataInput.read(h264frame,0,frame_size<lost?frame_size:lost);
//					Log.d(TAG,String.format("H264 %d,%d,%d", h264length,number,num));
//					number+=num;
//					file_test.write(h264frame, 0, num);
//				}
				ReadSize(h264length, dataInput);
				
				byte[] h264 = new byte[h264length];
				System.arraycopy(h264frame, 0, h264, 0, h264length);
				
				file_test.write(head);
				file_test.write(h264);//write selice
			}
			 
			file_test.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
		
	}
	
	private void ReadSize(int h264length,DataInputStream dataInput) throws IOException, InterruptedException{
		int read = 0;
		int temp = 0;
		while(read<h264length)
		{
			temp= dataInput.read(h264frame, read, h264length-read);
			Log.e(TAG, String.format("h264frame %d,%d,%d", h264length,read,h264length-read));
			if(temp==-1)
			{
				Log.e(TAG, "no data get wait for data coming.....");
				Thread.sleep(2000);
				continue;
			}
			read += temp;
		}
	}
	
	//从 fd文件中找到SPS And PPS
	private byte[] SPS;
	private byte[] PPS;
	private int StartMdatPlace = 0;
	private void findSPSAndPPS() throws Exception{
		File file = new File(fd);
		FileInputStream fileInput = new FileInputStream(file);
		
		int length = (int)file.length();
		byte[] data = new byte[length];
		
		fileInput.read(data);
		
		final byte[] mdat = new byte[]{0x6D,0x64,0x61,0x74};
		final byte[] avcc = new byte[]{0x61,0x76,0x63,0x43};
		
		for(int i=0 ; i<length; i++){
			if(data[i] == mdat[0] && data[i+1] == mdat[1] && data[i+2] == mdat[2] && data[i+3] == mdat[3]){
				StartMdatPlace = i+4;//find mdat
				break;
			}
		}
		Log.e(TAG, "StartMdatPlace:" + StartMdatPlace);
		//记录到xml文件里
		String mdatStr = String.format("mdata_%d%d.mdat",videoWidth,videoHeight);
		Editor editor = sharedPreferences.edit();
		editor.putInt(mdatStr, StartMdatPlace);
		editor.commit();
		
		for(int i=0 ; i<length; i++){
			if(data[i] == avcc[0] && data[i+1] == avcc[1] && data[i+2] == avcc[2] && data[i+3] == avcc[3]){
				int sps_start = i+3+7;//其中i+3指到avcc的c，再加7跳过6位AVCDecoderConfigurationRecord参数
				
				//sps length and sps data
				byte[] sps_3gp = new byte[2];//sps length
				sps_3gp[1] = data[sps_start];
				sps_3gp[0] = data[sps_start + 1];
				int sps_length = bytes2short(sps_3gp);
				Log.e(TAG, "sps_length :" + sps_length);
				
				sps_start += 2;//skip length
				SPS = new byte[sps_length];
				System.arraycopy(data, sps_start, SPS, 0, sps_length);
				for(int si=0;si<sps_length;si++)
				Log.e(TAG, "==========SPS :" + si + SPS[si]);
				//save sps
				FileOutputStream file_out = VideoTest.this.openFileOutput(
						String.format("%d%d.sps",videoWidth,videoHeight), 
						Context.MODE_PRIVATE);
				file_out.write(SPS);
				file_out.close();
				
				//pps length and pps data
				int pps_start = sps_start + sps_length + 1;
				byte[] pps_3gp =new byte[2];
				pps_3gp[1] = data[pps_start];
				pps_3gp[0] =data[pps_start+1];
				int pps_length = bytes2short(pps_3gp);
				Log.e(TAG, "PPS LENGTH:"+pps_length);
				
				pps_start+=2;
				
				PPS = new byte[pps_length];
				System.arraycopy(data, pps_start, PPS,0,pps_length);
				for (int pi =0;pi<pps_length;pi++)
				   Log.e(TAG, "==========PPS :" +pi + PPS[pi]);
				
				//Save PPS
				file_out = VideoTest.this.openFileOutput(
						String.format("%d%d.pps",videoWidth,videoHeight),
						Context.MODE_PRIVATE);
				file_out.write(PPS);
				file_out.close();
				Log.e(TAG, "==========SPS :" + SPS+ ",  PPS :" +PPS);
				break;
			}
		}
		
	}
	
	//计算长度
	public short bytes2short(byte[] b)
    {
	            short mask=0xff;
	            short temp=0;
	            short res=0;
	            for(int i=0;i<2;i++)
	            {
	                res<<=8;
	                temp=(short)(b[1-i]&mask);
	                res|=temp;
	            }
	            return res;
    }

	@Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.activity_main, menu);
        return true;
    }

	@Override
	protected void onPause() {
		// TODO Auto-generated method stub
		super.onPause();
		if(mMediaRecorderRecording)
		{
			releaseMediaRecorder();
			
			try {
				lss.close();
				receiver.close();
				sender.close();
			} catch (IOException e) {
				Log.e(TAG, e.toString());
			}
			
			mMediaRecorderRecording = false;
		}
		finish();
	}

	
	
}