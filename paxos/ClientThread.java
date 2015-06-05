import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.HashSet;
import java.util.Random;
import java.util.Scanner;
import java.util.Set;

public class Client extends Thread {
	InetAddress IP;
	int port;

	public Client(InetAddress IP, int port) {
		// TODO Auto-generated constructor stub
		this.IP = IP;
		this.port = port;
	}

	@Override
	public void run() {
		// TODO Auto-generated method stub
		super.run();
		String command;
		System.out.println("Please enter a command:");
		Scanner sc = null;
		sc = new Scanner(System.in);
		while ((command = sc.nextLine()) != null) {
			ServerSocket serverSocket;
			try {
				InetAddress hostname = InetAddress
						.getByName(config.get(siteID)[0]);
				int port = Integer.parseInt(config.get(siteID)[1]);
				serverSocket = new ServerSocket(port + 1, 5, hostname);
			} catch (IOException e) {
				e.printStackTrace();
				return;
			}

			connectSite(0, command, IP, port);
			Socket mysocket;
			try {
				// Wait for a client to connect (blocking)
				mysocket = serverSocket.accept();
				mysocket.setSoTimeout(50000);
			} catch (SocketTimeoutException e) {
				e.printStackTrace();
				continue;
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
			// String[] input_split = null;
			try {
				input = in.readLine();
				// input_split = input.split("\"");
			} catch (IOException e) {
				e.printStackTrace();
			}

		}
	}

	private void connectSite(int i, String command, InetAddress IP, int port) {
		// TODO Auto-generated method stub
		int siteID = i;
		int connection_time_out = 5000;
		Socket mysocket;
		Set<Integer> triedSet = new HashSet<Integer>();
		while (true) {
			try {
				triedSet.add(siteID);
				mysocket = new Socket(hostip[siteID], port_num[siteID]);
				break;
			} catch (IOException e) {
				System.out.println("Connect timeout! Choose another site!");
				Random rand = new Random();
				siteID = rand.nextInt(5);
				while (triedSet.contains(siteID))
					siteID = rand.nextInt(5);
			}
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
