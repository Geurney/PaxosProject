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
import java.util.Random;
import java.util.Scanner;

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

	public Client(int port, ArrayList<String[]> serverAddress)
			throws IOException {
		InetAddress addr = InetAddress.getLocalHost();
		this.address = addr.getHostAddress();
		this.port = port;
		this.serverAddress = serverAddress;
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
			serverSocket.setSoTimeout(100000000);
		} catch (IOException e) {
			e.printStackTrace();
			return;
		}

		Random rand = new Random();
		int server = 0;
		System.out.println("Please enter a command:");
		while ((command = sc.nextLine()) != null) {
			connectSite(server, command, address, port);
			Socket mysocket = null;
			try {
				// Wait for a client to connect (blocking)
				mysocket = serverSocket.accept();
			} catch (SocketTimeoutException e) {
				e.printStackTrace();
				int temp = rand.nextInt(5);
				while (temp == server)
					temp = rand.nextInt(5);
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
				if (command.contains("post"))
					System.out.println(input);
				else {
					String[] inStrings = input.split("\"");
					for(String i:inStrings) System.out.println(i);
				}
				if (input.contains("Retry")) {
					int temp = rand.nextInt(5);
					while (temp == server)
						temp = rand.nextInt(5);
					server = temp;
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
			System.out.println("Please enter a command:");
		}
		try {
			serverSocket.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public void connectSite(int i, String command, String IP, int port) {
		Socket mysocket;
		try {
			mysocket = new Socket(serverAddress.get(i)[0],
					Integer.parseInt(serverAddress.get(i)[1]));
		} catch (IOException e) {
			e.printStackTrace();
			return;
		}

		PrintWriter out;
		try {
			out = new PrintWriter(mysocket.getOutputStream(), true);
		} catch (IOException e) {
			e.printStackTrace();
			return;
		}

		if (command.contains("post"))
			out.println(command.substring(0, command.indexOf(' '))
					+ "\""
					+ IP
					+ "\'"
					+ port
					+ "\""
					+ command.substring(command.indexOf(' ') + 1,
							command.length()));
		else
			out.println(command + "\"" + IP + "\'" + port);
		// Close TCP connection
		try {
			mysocket.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
