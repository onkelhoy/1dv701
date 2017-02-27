import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.SocketException;
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
					catch (SocketException e) 
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
	 */
	
	private void HandleRQ(DatagramSocket sendSocket, String requestedFile, int opcode) 
	{		
		if(opcode == OP_RRQ)
		{
			// See "TFTP Formats" in TFTP specification for the DATA and ACK packet contents
			boolean result = send_DATA_receive_ACK(sendSocket, requestedFile);
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
	
	private boolean send_DATA_receive_ACK(DatagramSocket socket, String path) {
		try {
			byte[] databuffer = Files.readAllBytes(Paths.get(path)); // read in data from file
			
			//byte[] buffer = new byte[databuffer.length+4];
			

			int block = 1, tot = databuffer.length, length = 512, packetcount = tot/512;
			if(packetcount == 0) packetcount = 1;
			
			while(packetcount > 0){
				if(length * (block+1) > tot) { // the last packet
					length = tot-length*block; // 2560 > 2557 = 3
				}
				
				byte[] buffer = new byte[length+4];
				buffer[0] = 0;
				buffer[1] = 3; //Opcode
				
				for(int i = 0; i < length; i++){ // not the most elegant way..

					buffer[i+4] = databuffer[i+block*512]; // read bytes from offset
				}
				
				// block = 5
				String t = block+"";
				if(block < 10) t = "0"+block;
				
				buffer[2] = (byte) t.charAt(0); // 0 
				buffer[3] = (byte) t.charAt(1); // 5
				
				System.out.printf("\tsend [%d] dataLength: %d\n", block, length);
				
				DatagramPacket data = new DatagramPacket(buffer, buffer.length);
				socket.send(data);
				block++;
				packetcount--;
			}

		
			return true;
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return false;
		}
	}
	
	private boolean receive_DATA_send_ACK(DatagramSocket socket, String path)
	{return true;}
	
	private void send_ERR(DatagramSocket socket, String path)
	{
		
	}
	
}



