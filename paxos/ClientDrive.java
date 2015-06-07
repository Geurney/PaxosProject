package paxos;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;

public class ClientDrive {

	public static void main(String[] args) throws IOException {
		int port;
		if (args.length == 0) {
			port = 8000;
//			System.out.println("Please specifiy the port!");
            Client client = new Client(port,readFile("paxos/Server.txt"));
            client.start();
		} else {
			port = Integer.parseInt(args[0]);
            Client client = new Client(port,readFile(args[1]));
            client.start();
		}
		System.out.println("Client is running");
	}
	
		/**
		 * Read Server Address from File.
		 * 
		 * @param fileName
		 * @return
		 * @throws IOException
		 */
		public static ArrayList<String[]> readFile(String fileName)
				throws IOException {
			FileReader fileReader = new FileReader(fileName);
			BufferedReader bufferedReader = new BufferedReader(fileReader);
			ArrayList<String[]> serverAddress = new ArrayList<String[]>();
			String line = null;
			while ((line = bufferedReader.readLine()) != null) {
				String[] addr = line.split(" ");
				serverAddress.add(addr);
			}
			bufferedReader.close();
			return serverAddress;
		}
}
