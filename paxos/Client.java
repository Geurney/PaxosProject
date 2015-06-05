package paxos;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.net.InetAddress;
import java.util.ArrayList;

public class Client {
	private final String address;
	/**
	 * Server Port
	 */
	private final int port;

	/**
	 * List of server addresses
	 */
	private final ArrayList<String[]> serverAddress;

	public Client(int port) throws IOException {
		InetAddress addr = InetAddress.getLocalHost();
		this.address = addr.getHostAddress();
		this.port = port;
		this.serverAddress = readFile("Server.txt");
	}

	public static void main(String[] args) throws IOException {
		int port;
		if (args.length == 0) {
			port = 8000;
			System.out.println("Please specifiy the port!");
		} else {
			port = Integer.parseInt(args[0]);
		}
		Client client = new Client(port);
		System.out.println("Client: " + client.address + ":" + client.port);
	}

	/**
	 * Read Server Address from File.
	 * 
	 * @param fileName
	 *            Server Address file name.
	 * @return Server Address List
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
