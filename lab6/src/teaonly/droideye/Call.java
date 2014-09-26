package teaonly.droideye;

//Class used for setting the state of the call of the progrm
public class Call 
{
	public enum State { IDLE, INCOMING, OUTGOING, ONGOING };
	static State callState = State.IDLE;
	static String username2;
	static String ip;
	static int port;
	static boolean isPanorama;
	static String ipForPanorama;
	
	synchronized static public void setPanorama(boolean _isPanorama)
	{
		isPanorama = _isPanorama;
	}
	
	synchronized static public boolean isPanorama()
	{
		return isPanorama;
	}
	
	synchronized static public void setIpForPanorama(String strIP)
	{
		ipForPanorama = strIP;
	}
	
	synchronized static public String getIpForPanorama()
	{
		return ipForPanorama;
	}
	
	//Sets the state
	synchronized static public void setState(State s) {
			callState = s;
	}
	
	//Gets the states
	synchronized static public State getState() {
			return callState;
	}
	
	//Set the username of the person they are talking to
	synchronized static public void setUsername2(String un2) {
		username2 = un2;
	}
	
	//returns the undername of the person they are communication with
	synchronized static public String getUsernames() {
		return username2;
	}
	
	//Sets the port of the communication
	synchronized static public void setPort(int p) {
		port = p;
	}
	
	//Gets the port of the communication
	synchronized static public int getPort() {
		return port;
	}
	
	
}
