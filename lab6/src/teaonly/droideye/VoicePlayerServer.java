package teaonly.droideye;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.util.Log;

public class VoicePlayerServer extends Thread {
	private static final String TAG = "VPS";
	static public DatagramSocket socket;

	private AudioTrack speaker;
	private int sampleRate = 8000;
	private int channelConfig = AudioFormat.CHANNEL_CONFIGURATION_MONO;
	private int audioFormat = AudioFormat.ENCODING_PCM_16BIT;
	
	private boolean close;
	VoicePlayerServer(String server, int port) throws UnknownHostException, SocketException {
		super();
		close = false;
		socket = new DatagramSocket();
	}
	
	public void run() {
		android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO);
		byte[][] buffers = new byte[256][160];
		int ix = 0;
		
		// Minimum buffer size (can be increased later)
		int N =AudioTrack.getMinBufferSize(sampleRate,channelConfig,audioFormat);

		// Create instance of AudioTrack
		speaker = new AudioTrack(AudioManager.STREAM_VOICE_CALL,sampleRate,channelConfig,audioFormat,N,AudioTrack.MODE_STREAM);

		speaker.play();
		
		while(!close) {
			try {
				byte[] buffer = buffers[ix++ % buffers.length];

				//Define the packet
				DatagramPacket packet = new DatagramPacket(buffer, buffer.length);

				// Receive the packet
				socket.receive(packet);

				// Read packet data and write to a speaker
				buffer=packet.getData();
				speaker.write(buffer,0,buffer.length);
				Log.i(TAG,"speaker write");
				
			} catch (IOException e) {
				Log.e(TAG, "Error: "+ e);
			}
		}
	}
	public void close() {
		if(speaker != null) {
			speaker.release();
		}
		close=true;
	}
}
