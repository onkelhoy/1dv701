import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
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
							HandleRQ(sendSocket, requestedFile.toString(), OP_RRQ, clientAddress.getPort());
						}
						// Write request
						else 
						{                       
							requestedFile.insert(0, WRITEDIR);
							HandleRQ(sendSocket,requestedFile.toString(),OP_WRQ, clientAddress.getPort());  
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
		
		int opcode = (buf[0] << 8) | (buf[1] & 0x00ff);
		
		if(opcode == 1 || opcode == 2) {
			// 2bytes 	Nbytes	 1byte Nbytes 0byte
			// opcode | filename | 0 | mode | 0 
			int index = filldata(2, buf, requestedFile);
			StringBuffer mode = new StringBuffer();
			filldata(index, buf, mode); //fill the mode
			
			if(!mode.toString().toLowerCase().equals("octet")) return -1; // will produce an illegal error stmt.
		}
		
		return opcode;
	}
	
	private int filldata(int index, byte[] buf, StringBuffer fill){
		char c;
		while((c = (char)buf[index]) != 0){
			fill.append(c);
			index++;
		}
		
		return ++index; //skip the zero
	}

	/**
	 * Handles RRQ and WRQ requests 
	 * 
	 * @param sendSocket (socket used to send/receive packets)
	 * @param requestedFile (name of file to read/write)
	 * @param opcode (RRQ or WRQ)
	 * @throws IOException 
	 */
	
	private void HandleRQ(DatagramSocket sendSocket, String requestedFile, int opcode, int port) throws IOException 
	{	
		String ip = sendSocket.getLocalAddress().getHostAddress();
		
		File f = new File(requestedFile);

		String abspath = new File(READDIR).getAbsolutePath();
		String canonicalPath = f.getCanonicalPath();
		
		if(!canonicalPath.startsWith(abspath)) { // its a file, check parent
			send_ERR(sendSocket, 2, "Access violation");
			return;
		}
		else if(canonicalPath.startsWith(abspath+"\\admin")){
			if(!ip.equals("1.3.3.7")){ // only the admin which has this ip has permission for this folder!!
				send_ERR(sendSocket, 7, "No Such User");
				return;
			}
		}
		
		
		if(opcode == OP_RRQ)
		{
			
			if(!(f.exists() && f.isFile())) {
				send_ERR(sendSocket, 1, "File not found."); // file not found
				return;
			} // dir/../a.txt -> a.txt
			
			// See "TFTP Formats" in TFTP specification for the DATA and ACK packet contents
			byte[] databuffer = Files.readAllBytes(Paths.get(requestedFile)); // read in data from file
			
			int block = 1, count = (databuffer.length/512)+1, error = 0;
			byte[] buffer = getSendData(block, databuffer); // initial buffer
			
			while(block <= count) {
				if(error > 3) { // max errors of 3 in this case
					send_ERR(sendSocket, 0, "too many errors");
					
					return;
				}
				
				int ackblock = send_DATA_receive_ACK(sendSocket, 1000, buffer, port);
				
				if(ackblock > 0) {
					if(ackblock == block){ // check if the acknowledgment block is the same as our block
						block++;
						error = 0;
						if(block <= count) buffer = getSendData(block, databuffer); 
					}
					else { // oh no.. the client is behind and wants the new ackblock that he/she missed
						if(ackblock+1 <= count) buffer = getSendData(ackblock+1, databuffer);
						error++;
					}
				}
				else if(ackblock == -2){
					send_ERR(sendSocket, 5, "Unknown transfer ID."); // error code 5
					return;
				}
				else error++;
			}
		}
		else if (opcode == OP_WRQ) 
		{	
			if(f.exists() && f.isFile()) {
				send_ERR(sendSocket, 6, "File already exists."); // error code 6
				return;
			}
			
			FileOutputStream writer = new FileOutputStream(requestedFile);
			ByteArrayOutputStream outputStream = new ByteArrayOutputStream( );
			int block = 0, error = 0;
			byte[] ack = getHead(4, OP_ACK, block);
			
			int total = 0;
			
			
			while(true){
				if(error >= 3) { // maybe
				send_ERR(sendSocket, 0, "too many errors [WRQ]");
					System.out.println("too many errors");
					return;
				}
				int readbytes = 0;
				if((readbytes = receive_DATA_send_ACK(sendSocket, ack, outputStream, port)) >= 0) {
					error = 0;
					total += readbytes;
					
					if(total > 800000) {
						// the file is exceeding 800kb 
						send_ERR(sendSocket, 3, "Disk full or allocation exceeded."); // error code 3
						return;
					}
					
					ack = getHead(4, OP_ACK, ++block);
				}
				else if(readbytes == -2){
					send_ERR(sendSocket, 5, "Unknown transfer ID."); // error code 5
					return;
				}
				else {
					send_ERR(sendSocket, 0, "resend data"); // type 0 error
					error++;
				}
				if(readbytes < 512 && readbytes != -1) break; //EoF
			}
			
			if(error < 3) {
				writer.write(outputStream.toByteArray());
				writer.flush();
				writer.close();
				outputStream.close();
				sendSocket.send(new DatagramPacket(ack, ack.length));
				return;
			}
		}
		else 
		{
			System.err.println("Invalid request. Sending an error packet.");
			// See "TFTP Formats" in TFTP specification for the ERROR packet contents
			send_ERR(sendSocket, 4, "Illegal TFTP operation."); // type 4 error
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
	private int send_DATA_receive_ACK(DatagramSocket socket, int timeout, byte[] buffer, int port){
		DatagramPacket datagram = new DatagramPacket(buffer, buffer.length); // the datagram to be sent
		try {
			socket.send(datagram); // send the datagram
			byte[] recive = new byte[BUFSIZE]; // the recive buffer 

			socket.setSoTimeout(timeout); // the timeout limit
			try {
				DatagramPacket db = new DatagramPacket(recive, recive.length);
				socket.receive(db); // get the ACK packet
				
				if(db.getPort() != port) return -2;
				
				
				int opcode = ParseRQ(recive, null); // retrive the opcode
				if(opcode == OP_ACK) return ((recive[2] << 8) | (recive[3] & 0x00ff)); // send the block number
				else return -1; // oh no.. it failed
			}
			catch (SocketTimeoutException te){ //oh shit.. the timeout for the recive expired
				//timeout.. resend
				return -1;
			}
		} catch (IOException e) { // an internal error occured
			return -1;
		}
	}
	
	// WRITE
	private byte[] getHead(int size, int OP, int block){
		
		ByteBuffer head = ByteBuffer.allocate(size); 
		
		head.putShort((short)OP);
		head.putShort((short)block);
		return head.array();
	}
	private int receive_DATA_send_ACK(DatagramSocket socket, byte[] ack, ByteArrayOutputStream writer, int port)
	{
		try {		
			// read in data
			socket.send(new DatagramPacket(ack, ack.length));
			socket.setSoTimeout(6000); // timeout
			byte[] buffer = new byte[512+4];
			DatagramPacket datagram = new DatagramPacket(buffer, buffer.length);
			socket.receive(datagram);
			
			if(datagram.getPort() != port) return -2;
			
			// check opcode = 4
			if(ParseRQ(buffer, new StringBuffer()) == OP_DAT) {

				// send ACK
				byte[] data = Arrays.copyOfRange(datagram.getData(), 4, datagram.getLength()); // the recived data
				
				writer.write(data);
				writer.flush();
				//if(data.length < 512) socket.close();
				return data.length;
			}
			else return -1;
		} catch (IOException e1) {
			// TODO Auto-generated catch block
			return -1;
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



