import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.file.Files;
import java.nio.file.Paths;

public class TFTPServer 
{
	public static final int TFTPPORT = 4970;
	public static final int BUFSIZE = 516;
	public static final String READDIR = "TFTP/read/"; //custom address at your PC
	public static final String WRITEDIR = "TFTP/write/"; //custom address at your PC
	// OP codes
	public static final int OP_RRQ = 1;
	public static final int OP_WRQ = 2;
	public static final int OP_DAT = 3;
	public static final int OP_ACK = 4;
	public static final int OP_ERR = 5;

	public static void main(String[] args) {
		if (args.length > 0) 
		{
			System.err.printf("usage: java %s\n", TFTPServer.class.getCanonicalName());
			System.exit(1);
		}
		//Starting the server
		try 
		{
			TFTPServer server= new TFTPServer();
			server.start();
		}
		catch (Exception e) 
			{e.printStackTrace();}
	}
	
	private void start() throws Exception 
	{
		byte[] buf= new byte[BUFSIZE];
		
		// Create socket
		DatagramSocket socket= new DatagramSocket(null);
		
		// Create local bind point 
		SocketAddress localBindPoint= new InetSocketAddress(TFTPPORT);
		socket.bind(localBindPoint);

		System.out.printf("Listening at port %d for new requests\n", TFTPPORT);

		// Loop to handle client requests 
		while (true) 
		{        
			
			final InetSocketAddress clientAddress = receiveFrom(socket, buf);
			
			// If clientAddress is null, an error occurred in receiveFrom()
			if (clientAddress == null) 
				continue;

			final StringBuffer requestedFile= new StringBuffer();
			final int reqtype = ParseRQ(buf, requestedFile);
			
			new Thread() 
			{
				public void run() 
				{
					try 
					{
						DatagramSocket sendSocket= new DatagramSocket(0);

						// Connect to client
						sendSocket.connect(clientAddress);						
						
						System.out.printf("%s request from %s using port %d\n",
								(reqtype == OP_RRQ)?"Read":"Write",
								clientAddress.getHostName(), clientAddress.getPort());   
								
						// Read request
						if (reqtype == OP_RRQ) 
						{      
							requestedFile.insert(0, READDIR);
							HandleRQ(sendSocket, requestedFile.toString(), OP_RRQ);
						}
						// Write request
						else 
						{                       
							requestedFile.insert(0, WRITEDIR);
							HandleRQ(sendSocket,requestedFile.toString(),OP_WRQ);  
						}
						sendSocket.close();
					} 
					catch (Exception e) 
						{e.printStackTrace();}
				}
			}.start();
			
			
		}
	}
	
	/**
	 * Reads the first block of data, i.e., the request for an action (read or write).
	 * @param socket (socket to read from)
	 * @param buf (where to store the read data)
	 * @return socketAddress (the socket address of the client)
	 * @throws IOException 
	 */
	private InetSocketAddress receiveFrom(DatagramSocket socket, byte[] buf) throws IOException 
	{

		// Create datagram packet
		DatagramPacket receivePacket= new DatagramPacket(buf, buf.length);
		
		// Receive packet
	    socket.receive(receivePacket);
	    
		// Get client address and port from the packet
	    InetSocketAddress socketAddress = new InetSocketAddress(receivePacket.getAddress(), receivePacket.getPort());
		return socketAddress;
	}

	/**
	 * Parses the request in buf to retrieve the type of request and requestedFile
	 * 
	 * @param buf (received request)
	 * @param requestedFile (name of file to read/write)
	 * @return opcode (request type: RRQ or WRQ)
	 */
	private int ParseRQ(byte[] buf, StringBuffer requestedFile) 
	{
		// See "TFTP Formats" in TFTP specification for the RRQ/WRQ request contents
		
		int opcode = ((buf[0] & 0xff << 8) | (buf[1] & 0xff));
		
		int index = 2;
		while((int)buf[index] > 0){
			requestedFile.append((char)buf[index]);
			index++;
		}
		
		return opcode;
	}

	/**
	 * Handles RRQ and WRQ requests 
	 * 
	 * @param sendSocket (socket used to send/receive packets)
	 * @param requestedFile (name of file to read/write)
	 * @param opcode (RRQ or WRQ)
	 * @throws IOException 
	 */
	
	private void HandleRQ(DatagramSocket sendSocket, String requestedFile, int opcode) throws IOException 
	{		
		if(opcode == OP_RRQ)
		{
			// See "TFTP Formats" in TFTP specification for the DATA and ACK packet contents
			byte[] databuffer = Files.readAllBytes(Paths.get(requestedFile)); // read in data from file
			
			int block = 1, count = databuffer.length/512, error = 0;
			byte[] buffer = getSendData(block, databuffer); // initial buffer
			
			while(block <= count+1) {
				if(error > 3) { // max errors of 3 in this case
					System.out.println("Too many errors (send a error back!)");
					break;
				}
				
				if(send_DATA_receive_ACK(sendSocket, 1000, buffer)) {
					block++;
					buffer = getSendData(block, databuffer); // don't re-create if fail
				}
				else error++;
			}
			
		}
		else if (opcode == OP_WRQ) 
		{
			boolean result = receive_DATA_send_ACK(sendSocket, requestedFile);
		}
		else 
		{
			System.err.println("Invalid request. Sending an error packet.");
			// See "TFTP Formats" in TFTP specification for the ERROR packet contents
			send_ERR(sendSocket, requestedFile);
			return;
		}		
	}
	
	/**
	To be implemented
	*/
	
	private byte[] getSendData(int block, byte[] data){
		int length = 512; //initial value
		if(length * block > data.length) { // when the offset is larger then actual bytes left the read
			length = data.length-length*(block-1); // then we only read whats left
		}

		byte[] buffer = new byte[length+4]; // length of the data plus the 4 initial bytes (opcode+block)
		
		//opcode (pre-set)
		buffer[0] = 0;
		buffer[1] = 3;
		//current block
		buffer[2] = (byte)(block >= 255 ? block/255 : 0); // get the bits above 255
		buffer[3] = (byte) (block); // block
		
		for(int i = 0; i < length; i++){ // not the most elegant way..
			buffer[i+4] = data[i+(block-1)*512]; // read bytes from offset
		}
		
		return buffer;
	}
	private boolean send_DATA_receive_ACK(DatagramSocket socket, int timeout, byte[] buffer){
		DatagramPacket datagram = new DatagramPacket(buffer, buffer.length); // the datagram to be sent
		try {
			socket.send(datagram); // send the datagram
			byte[] recive = new byte[BUFSIZE]; // the recive buffer 

			socket.setSoTimeout(timeout); // the timeout limit
			try {
				socket.receive(new DatagramPacket(recive, recive.length)); // get the ACK packet
				int opcode = ((recive[0] & 0xff << 8) | (recive[1] & 0xff)); // retrive the opcode
				if(opcode == OP_ACK) return true; // hope for the best
				else return false; // oh no.. it failed
			}
			catch (SocketTimeoutException te){ //oh shit.. the timeout for the recive expired
				//timeout.. resend
				return false;
			}
		} catch (IOException e) { // an internal error occured
			return false;
		}
	}
	
	private boolean receive_DATA_send_ACK(DatagramSocket socket, String path)
	{return true;}
	
	private void send_ERR(DatagramSocket socket, String path)
	{
		
	}
	
}



