package paxos;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Random;
import java.util.Scanner;
import java.util.Set;

public class Client extends Thread {
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
		client.start();
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

	@Override
	public void run() {
		// TODO Auto-generated method stub
		super.run();
		String command;
		Scanner sc = null;
		sc = new Scanner(System.in);
		ServerSocket serverSocket;
		try {
			InetAddress hostname = InetAddress.getByName(address);
			serverSocket = new ServerSocket(port, 5, hostname);
			serverSocket.setSoTimeout(10000);
		} catch (IOException e) {
			e.printStackTrace();
			return;
		}
		
		Random rand = new Random();
		int server = 0;
		while ((command = sc.nextLine()) != null) {
			System.out.println("Please enter a command:");
				connectSite(server, command, address, port);
				Socket mysocket = null;
				try {
					// Wait for a client to connect (blocking)
					mysocket = serverSocket.accept();
				} catch (SocketTimeoutException e) {
					e.printStackTrace();
					int temp = rand.nextInt(5);
					while (temp == server) temp = rand.nextInt(5);
					server = temp;
					continue;
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				BufferedReader in;
				try {
					in = new BufferedReader(new InputStreamReader(
							mysocket.getInputStream()));
				} catch (IOException e) {
					e.printStackTrace();
					continue;
				}
				// Read event from client
				String input;
				try {
					input = in.readLine();
					System.out.println(input);
					if(input.contains("Retry")){
						int temp = rand.nextInt(5);
						while (temp == server) temp = rand.nextInt(5);
						server = temp;
					}
				} catch (IOException e) {
					e.printStackTrace();
				}
				
		}
		try {
			serverSocket.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	public void connectSite(int i, String command, String IP, int port) {
		// TODO Auto-generated method stub
		int siteID = i;
		Socket mysocket = null;
		try {
				mysocket = new Socket(serverAddress.get(siteID)[0], Integer.parseInt(serverAddress.get(siteID)[1]));
			} catch (IOException e) {
				e.printStackTrace();
			}

		PrintWriter out;
		try {
			out = new PrintWriter(mysocket.getOutputStream(), true);
		} catch (IOException e) {
			e.printStackTrace();
			return;
		}

		out.println(command + '\'' + IP + '\'' + port);

		// Close TCP connection
		try {
			mysocket.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
