import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;

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
		
		if(opcode == 1 || opcode == 2) {
			int index = 2;
			while((int)buf[index] > 0){
				requestedFile.append((char)buf[index]);
				index++;
			}
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
			File f = new File(requestedFile);
			if(!(f.exists() && f.isFile())) {
				send_ERR(sendSocket, 1, "File not found.");
				return;
			}
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
					error = 0;
					if(block <= count+1) buffer = getSendData(block, databuffer); // don't re-create if fail
				}
				else error++;
			}
			
		}
		else if (opcode == OP_WRQ) 
		{
			FileOutputStream writer = new FileOutputStream(requestedFile);
			int block = 0, error = 0;
			byte[] ack = getHead(4, OP_ACK, block);
			
			
			//send the initial ACK [0]
			sendSocket.send(new DatagramPacket(ack, ack.length));
			ack = getHead(4, OP_ACK, ++block); // and get the new ACK
			
			while(!sendSocket.isClosed()){
				if(error >= 3) { // maybe
					System.out.println("error is more then 3");
					break;
				}
				if(receive_DATA_send_ACK(sendSocket, ack, writer)) {
					error = 0;
					ack = getHead(4, OP_ACK, ++block);
				}
				else error++;
			}
			
			writer.close();
		}
		else 
		{
			System.err.println("Invalid request. Sending an error packet.");
			// See "TFTP Formats" in TFTP specification for the ERROR packet contents
			send_ERR(sendSocket, 4, "Illegal TFTP operation.");
			return;
		}		
	}
	
	/**
	To be implemented
	*/
	
	// READ
	private byte[] getSendData(int block, byte[] data){
		int length = 512; //initial value
		if(length * block > data.length) { // when the offset is larger then actual bytes left the read
			length = data.length-length*(block-1); // then we only read whats left
		}
		
		byte[] buffer = getHead(length+4, OP_DAT, block); // length of the data plus the 4 initial bytes (opcode+block)
		
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
	
	// WRITE
	private byte[] getHead(int size, int OP, int block){
		
		ByteBuffer head = ByteBuffer.allocate(size); 
		
		head.putShort((short)OP);
		head.putShort((short)block);
		return head.array();
	}
	private boolean receive_DATA_send_ACK(DatagramSocket socket, byte[] ack, FileOutputStream writer)
	{
		try {		
			// read in data
			socket.setSoTimeout(6000); // timeout
			byte[] buffer = new byte[512+4];
			DatagramPacket datagram = new DatagramPacket(buffer, buffer.length);
			socket.receive(datagram);
			
			// check opcode = 4
			if(ParseRQ(buffer, new StringBuffer()) == OP_DAT) {

				// send ACK
				byte[] data = Arrays.copyOfRange(datagram.getData(), 4, datagram.getLength()); // the recived data
				
				writer.write(data);
				writer.flush();
				socket.send(new DatagramPacket(ack, ack.length));
				if(data.length < 512) socket.close();
				return true;
			}
			else return false;
		} catch (IOException e1) {
			// TODO Auto-generated catch block
			return false;
		}
	}
	
	private void send_ERR(DatagramSocket socket, int errorCode, String errorMessege) throws IOException
	{
		// 2bytes    2bytes      n bytes     1byte
		// 05    |  ErrorCode |   ErrMsg   |   0  |  <- error header
		
		byte[] msg = errorMessege.getBytes(), buffer = getHead(5+msg.length, OP_ERR, errorCode);
		
		for(int i = 0; i < msg.length; i++){
			buffer[i+4] = msg[i];
		}
		buffer[buffer.length-1] = 0;
		socket.send(new DatagramPacket(buffer, buffer.length));
	}
	
}



