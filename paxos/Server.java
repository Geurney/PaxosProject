package paxos;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Scanner;

public class Server {
	/**
	 * Server ID
	 */
	private int ID;
	
	/**
	 * Server Address
	 */
	private final ArrayList<String[]> serverAddress;
	/**
	 * CLI Thread
	 */
	private CLIThread cil;
	
	/**
	 * Communication Thread
	 */
	private COMMThread comm;
	
	private String STATUS;

	public Server() throws IOException {
		serverAddress = readFile("Server.txt");
	}

	public static void main(String[] args) throws IOException {
		Server server = new Server();
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
		ArrayList<String[]> ProcessAddress = new ArrayList<String[]>();
		String[] ID = new String[1];
		ID[0] = bufferedReader.readLine();
		ProcessAddress.add(ID);
		String line = null;
		while ((line = bufferedReader.readLine()) != null) {
			String[] addr = line.split(" ");
			ProcessAddress.add(addr);
		}
		bufferedReader.close();
		return ProcessAddress;
	}
	
	private class CLIThread extends Thread {
		@Override
		public void run() {
			String command;
			Scanner sc = null;
			sc = new Scanner(System.in);
			System.out.println("Site" + ID + ": Please enter a command:");
			command = sc.nextLine();
		}
	}
	
	
	private class COMMThread extends Thread {
		@Override
		public void run() {
			
		}
	}
}
