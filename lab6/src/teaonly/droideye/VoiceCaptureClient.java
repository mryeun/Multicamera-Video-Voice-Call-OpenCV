package teaonly.droideye;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.UnknownHostException;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.util.Log;

public class VoiceCaptureClient extends Thread {
	private static final String TAG = "VCC";
	static public DatagramSocket socket;
	private String server;
	private int rPort; // redirect port
	//private final int lPort = 25202; // local port
	
	AudioRecord recorder;
	private int sampleRate = 8000;
	private int channelConfig = AudioFormat.CHANNEL_CONFIGURATION_MONO;
	private int audioFormat = AudioFormat.ENCODING_PCM_16BIT;
	
	private boolean close;
	
	//Constructor
	//Initailizes all the VoiceCaptureClient variables
	VoiceCaptureClient(String server, int port) {
		super();
		this.close=false;
		this.server = server;
		this.rPort = port;
		VoiceCaptureClient.socket = VoicePlayerServer.socket;
	}
	
	public void run() {
		try {
			android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO);		
			
			// Initialize
			byte[][] buffers = new byte[256][160];
			int ix = 0;
			DatagramPacket packet;
	
			// Retrieve the ServerName
			final InetAddress serverAddr = InetAddress.getByName(server);
			
			// Minimum buffer size (can be increased later)
			int N = AudioRecord.getMinBufferSize(sampleRate,channelConfig,audioFormat);
	
			// Construct instance of AudioRecord
			recorder = new AudioRecord(MediaRecorder.AudioSource.MIC,sampleRate,channelConfig,audioFormat,N*10);
	
			//Log.d("UDP","VCC: Connecting to " + usr.getUserName() + "...");
	
			recorder.startRecording();
			
			while(!close) {
				try {
					
					byte[] buffer = buffers[ix++ % buffers.length];
	
					//Read data from mic into buf
					N = recorder.read(buffer,0,buffer.length);
					//Put buffer in a packet
					packet=new DatagramPacket(buffer,buffer.length,serverAddr,rPort);
	
					//Send the packet
					socket.send(packet);
					
				} catch (Exception e) {
					Log.e(TAG, "C: Error", e);
				}
			}
		}
		catch(UnknownHostException e) {
			Log.e(TAG,e.toString());
			this.interrupt();
		}
		catch(IllegalStateException e) {
			Log.e(TAG,e.toString());
			this.interrupt();
		}
	}
	public void close() {
		close=true;
		if(recorder != null) {
			recorder.release();
		}
	}
	
	
}
