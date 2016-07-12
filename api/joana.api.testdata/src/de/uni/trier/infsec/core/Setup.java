package de.uni.trier.infsec.core;

import de.uni.trier.infsec.environment.Environment;
import de.uni.trier.infsec.functionalities.pkenc.Decryptor;
import de.uni.trier.infsec.functionalities.pkenc.Encryptor;
import edu.kit.joana.ui.annotations.Source;

public class Setup {

	private final static String HOST="localhost";
	private final static int PORT = 5000;
	
	@Source
	public static boolean secret() {
		return true;
	}
	
	public static void main(String args[]) 
	{
		setup(secret());
	}

	public static void setup(boolean secret_bit) // secret_bit: the only HIGH input 
	{
		// Public-key encryption functionality for Server 
		Decryptor serverDec = new Decryptor();
		Encryptor serverEnc = serverDec.getEncryptor();

		// Creating the server
		Server server = new Server(serverDec, PORT);
		new Thread(server).start();
	
		// The adversary decides how many clients we create 
		while(Environment.untrustedInput()!=0){
			// determine the value the client encrypts:
			// the adversary gives two values
			byte[] msg1 = Environment.untrustedInputMessage();
			byte[] msg2 = Environment.untrustedInputMessage();
			if (msg1.length != msg2.length) 
				break;
			
			byte[] msg = new byte[msg1.length];
			for(int i=0; i<msg1.length; ++i)
				msg[i] = (secret_bit ? msg1[i] : msg2[i]);
			
			Client client = new Client(serverEnc, msg, HOST, PORT);
			new Thread(client).start();
		}
	}
}