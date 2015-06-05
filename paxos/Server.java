package paxos;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Scanner;

public class Server {
	/**
	 * Server ID
	 */
	private int ID;

	/**
	 * Local host name
	 */
	private InetAddress hostname;

	/**
	 * Local host port
	 */
	private int port;
	/**
	 * Server Address
	 */
	private final ArrayList<String[]> serverAddress;

	/**
	 * FAIL WAIT AFTER_PREPARE AFTER_SENDACCEPT
	 */
	private static enum STATUSTYPE {
		FAIL, WAIT, AFTER_PREPARE, AFTER_SENDACCEPT
	};

	private volatile STATUSTYPE STATUS;

	/**
	 * Normal Recovery
	 */
	private static enum MODETYPE {
		NORMAL, RECOVERY
	};

	private volatile MODETYPE MODE;

	/**
	 * BallotNum, ID
	 */
	private int[] BallotNum;

	/**
	 * Accept BallotNum, ID
	 */
	private int[] AcceptNum;

	/**
	 * Index, Content, BallotNum,
	 */
	private String[] AcceptVal;

	/**
	 * Ack counter
	 */
	private int ACKCount;

	/**
	 * Accept Counter
	 */
	private int ACPCount;

	/**
	 * Majority
	 * 
	 * @throws IOException
	 */
	private static int MAJORITY = 3;

	/**
	 * CLI Thread
	 */
	private CLIThread CIL;

	/**
	 * Communication Thread
	 */
	private COMMThread COMM;

	/**
	 * Log
	 */
	private ArrayList<String> log;

	public Server(ArrayList<String[]> config) throws IOException {
		serverAddress = config;
		ID = Integer.parseInt(serverAddress.get(0)[0]);
		hostname = InetAddress.getByName(serverAddress.get(7)[0]);
		port = Integer.parseInt(serverAddress.get(7)[1]);
		STATUS = STATUSTYPE.WAIT;
		MODE = MODETYPE.NORMAL;
		BallotNum = new int[2];
		BallotNum[0] = 0;
		BallotNum[1] = 0;
		AcceptNum = new int[2];
		AcceptNum[0] = 0;
		AcceptNum[1] = 0;
		AcceptVal = new String[3];
		ACKCount = 0;
		ACPCount = 0;
		log = new ArrayList<String>();
		COMM = new COMMThread();
		CIL = new CLIThread();
	}

	public void start() {
		CIL.start();
		COMM.start();
	}

	private class CLIThread extends Thread {
		@SuppressWarnings("resource")
		@Override
		public void run() {
			String command;
			Scanner sc = null;
			sc = new Scanner(System.in);
			System.out.println("Site" + ID + ": Please enter a command:");
			while (true) {
				command = sc.nextLine();
				synchronized (STATUS) {
					switch (STATUS) {
					case FAIL:
						fail_process(command);
						break;
					default:
						default_process(command);
						break;
					}
				}
			}
		}

		private void fail_process(String cmd) {
			if (cmd.equals("Fail")) {
				return;
			} else if (cmd.equals("Restore")) {
				BallotNum[0] = 0;
				BallotNum[1] = 0;
				Socket socket;
				try {
					socket = new Socket(hostname, port);
				} catch (NumberFormatException | IOException e) {
					e.printStackTrace();
					return;
				}
				PrintWriter out = null;
				try {
					out = new PrintWriter(socket.getOutputStream(), true);
					out.println("wake");
				} catch (IOException e) {
					e.printStackTrace();
				}
				try {
					socket.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}

		private void default_process(String cmd) {
			if (cmd.equals("Fail")) {
				STATUS = STATUSTYPE.FAIL;
			}
		}
	}

	private class COMMThread extends Thread {
		@SuppressWarnings("resource")
		@Override
		public void run() {
			ServerSocket serverSocket;
			try {
				serverSocket = new ServerSocket(port, 5, hostname);
			} catch (IOException e) {
				e.printStackTrace();
				return;
			}
			while (true) {
				Socket socket;
				try {
					socket = serverSocket.accept();
				} catch (IOException e) {
					e.printStackTrace();
					continue;
				}
				BufferedReader in;
				try {
					in = new BufferedReader(new InputStreamReader(
							socket.getInputStream()));
				} catch (IOException e) {
					e.printStackTrace();
					try {
						socket.close();
						socket = null;
					} catch (IOException e1) {
						e1.printStackTrace();
					}
					continue;
				}
				String input;

				try {
					input = in.readLine();
				} catch (IOException e) {
					e.printStackTrace();
					try {
						socket.close();
						socket = null;
					} catch (IOException e1) {
						e1.printStackTrace();
					}
					continue;
				}
				synchronized (STATUS) {
					switch (STATUS) {
					case FAIL:
						fail_process(input);
						break;
					case WAIT:
						wait_process(input);
						break;
					case AFTER_PREPARE:
						prepare_process(input);
						break;
					case AFTER_SENDACCEPT:
						sendaccept_process(input);
						break;
					default:
						continue;
					}
				}
				try {
					socket.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}

		private void fail_process(String input) {
			String cmd[] = input.split("\'");
			String operation = cmd[0];
			if (operation.equals("wake")) {
				String msg = "help'" + ID;
				STATUS = STATUSTYPE.WAIT;
				MODE = MODETYPE.RECOVERY;
				send(msg);
			}
		}

		private void reject_post(String address) {
			String[] address_split = address.split("\'");
			Socket socket;
			try {
				socket = new Socket(address_split[0],
						Integer.parseInt(address_split[1]));
			} catch (NumberFormatException | IOException e) {
				e.printStackTrace();
				return;
			}
			PrintWriter out = null;
			try {
				out = new PrintWriter(socket.getOutputStream(), true);
				out.println("Retry");
			} catch (IOException e) {
				e.printStackTrace();
			}
			try {
				socket.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		private void process_post() {
			BallotNum[0] = BallotNum[0]++;
			BallotNum[1] = ID;
			String msg = "prepare\"" + BallotNum[0] + "," + BallotNum[1];
			ACKCount = 1;
			send(msg);
			STATUS = STATUSTYPE.AFTER_PREPARE;
		}

		private void process_read(String[] address) {
			String ipAddress = address[0];
			int port = Integer.parseInt(address[1]);
			Socket socket;
			try {
				socket = new Socket(ipAddress, port);
			} catch (IOException e) {
				e.printStackTrace();
				return;
			}
			PrintWriter out;
			try {
				out = new PrintWriter(socket.getOutputStream(), true);
				String msg;
				if (MODE == MODETYPE.NORMAL) {
					StringBuffer sb = new StringBuffer();
					sb.append("Log\"");
					for (String i : log) {
						sb.append(i);
						sb.append("\"");
					}
					sb.deleteCharAt(sb.length()-1);
					msg = sb.toString();
				} else {
					msg = "Retry";
				}
				out.println(msg);
			} catch (IOException e) {
				e.printStackTrace();
			}
			try {
				socket.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		private void process_prepare(String[] ballot_string) {
			int[] ballot = { Integer.parseInt(ballot_string[0]),
					Integer.parseInt(ballot_string[1]) };
			if (ballot[0] > BallotNum[0]
					|| (ballot[0] == BallotNum[0] && ballot[1] >= BallotNum[1])) {
				BallotNum[0] = ballot[0];
				BallotNum[1] = ballot[1];
				String msg = "ack\"" + BallotNum[0] + "," + BallotNum[1] + "\""
						+ AcceptNum[0] + "," + AcceptNum[1] + "\"" + AcceptVal;
				send(msg);
			}
		}

		private void process_help() {
			StringBuffer msg = new StringBuffer();
			msg.append("log\"");
			for (String i : log) {
				msg.append(i);
				msg.append("\"");
			}
			send(msg.toString());
		}

		private void process_log(String[] cmd) {
			if (MODE == MODETYPE.NORMAL) {
				return;
			} else {
				int index = log.size();
				for (int i = index; i < cmd.length; i++) {
					log.add(cmd[i]);
				}
				MODE = MODETYPE.NORMAL;
			}
		}

		private void process_ack(String[] Accept_string) {
			if (Integer.parseInt(Accept_string[0]) >= Integer
					.parseInt(AcceptVal[0])) {
				AcceptVal[0] = Accept_string[0];
				AcceptVal[1] = Accept_string[1];
				AcceptVal[2] = Accept_string[2];
			}
			ACKCount++;
			if (ACKCount >= MAJORITY) {
				String val = AcceptVal[0] + "\'" + AcceptVal[1] + "\'"
						+ AcceptVal[2];
				String msg = "accept\"" + BallotNum[0] + "," + BallotNum[1]
						+ "\"" + val;
				send(msg);
				ACPCount = 1;
				STATUS = STATUSTYPE.AFTER_SENDACCEPT;
			}
		}

		private void wait_process(final String input) {
			String cmd[] = input.split("\'");
			String operation = cmd[0];
			switch (operation) {
			case "post":
				process_post();
				break;
			case "prepare":
				process_prepare(cmd[1].split(","));
				break;
			case "accpet": {
				String[] ballot_string = cmd[1].split(",");
				int[] ballot = { Integer.parseInt(ballot_string[0]),
						Integer.parseInt(ballot_string[1]) };
				if (ballot[0] > BallotNum[0]
						|| (ballot[0] == BallotNum[0] && ballot[1] >= BallotNum[1])) {
					AcceptNum[0] = ballot[0];
					AcceptNum[1] = ballot[1];
					String[] msg = cmd[2].split("\'");
					AcceptVal[0] = Integer.toString(log.size());
					AcceptVal[1] = msg[1];
					AcceptVal[2] = msg[2];
					ACPCount = 1;
					send(input);
					STATUS = STATUSTYPE.AFTER_SENDACCEPT;
				}
			}
				break;
			case "help":
				process_help();
				break;
			case "log":
				process_log(cmd);
				break;
			case "read":
				process_read(cmd[1].split("\'"));
				break;
			}
		}

		private void prepare_process(final String input) {
			String cmd[] = input.split("\'");
			String operation = cmd[0];
			switch (operation) {
			case "post":
				reject_post(cmd[1]);
				break;
			case "prepare":
				process_prepare(cmd[1].split(","));
				break;
			case "accpet": {
				String[] ballot_string = cmd[1].split(",");
				int[] ballot = { Integer.parseInt(ballot_string[0]),
						Integer.parseInt(ballot_string[1]) };
				if (ballot[0] > BallotNum[0]
						|| (ballot[0] == BallotNum[0] && ballot[1] >= BallotNum[1])) {
					AcceptNum[0] = ballot[0];
					AcceptNum[1] = ballot[1];
					String[] msg = cmd[2].split("\'");
					AcceptVal[0] = Integer.toString(log.size());
					AcceptVal[1] = msg[1];
					AcceptVal[2] = msg[2];
					ACPCount = 1;
					send(input);
					STATUS = STATUSTYPE.AFTER_SENDACCEPT;
				}
			}
				break;
			case "ack":
				process_ack(cmd[2].split(","));
				break;
			case "help":
				process_help();
				break;
			case "log":
				process_log(cmd);
				break;
			case "read":
				process_read(cmd[1].split("\'"));
				break;
			}
		}

		private void sendaccept_process(final String input) {
			String cmd[] = input.split("\'");
			String operation = cmd[0];
			switch (operation) {
			case "post":
				reject_post(cmd[1]);
				break;
			case "prepare":
				process_prepare(cmd[1].split(","));
				break;
			case "accpet": {
				String[] ballot_string = cmd[1].split(",");
				int[] ballot = { Integer.parseInt(ballot_string[0]),
						Integer.parseInt(ballot_string[1]) };
				if (ballot[0] > BallotNum[0]
						|| (ballot[0] == BallotNum[0] && ballot[1] >= BallotNum[1])) {
					AcceptNum[0] = ballot[0];
					AcceptNum[1] = ballot[1];
					String[] msg = cmd[2].split("\'");
					AcceptVal[0] = Integer.toString(log.size());
					AcceptVal[1] = msg[1];
					AcceptVal[2] = msg[2];
					ACPCount = 1;
					send(input);
					STATUS = STATUSTYPE.AFTER_SENDACCEPT;
				} else {
					ACPCount++;
					if (ACPCount >= MAJORITY) {
						String val = AcceptVal[0] + "\'" + AcceptVal[1] + "\'"
								+ AcceptVal[2];
						log.add(val);
						BallotNum[0] = 0;
						BallotNum[1] = 0;
						STATUS = STATUSTYPE.WAIT;
					}
				}
			}
				break;
			case "ack":
				process_ack(cmd[2].split(","));
				break;
			case "help":
				process_help();
				break;
			case "log":
				process_log(cmd);
			case "read":
				process_read(cmd[1].split("\'"));
				break;
			}
		}

		private void send(String msg) {
			for (int i = 1; i <= 5; i++) {
				if (i == ID) {
					continue;
				}
				Socket socket;
				try {
					socket = new Socket(serverAddress.get(i)[0],
							Integer.parseInt(serverAddress.get(i)[1]));
				} catch (NumberFormatException | IOException e1) {
					e1.printStackTrace();
					return;
				}
				PrintWriter out = null;
				try {
					out = new PrintWriter(socket.getOutputStream(), true);
					out.println(msg);
				} catch (IOException e) {
					e.printStackTrace();
				}
				try {
					socket.close();
				} catch (IOException e) {
					e.printStackTrace();
					return;
				}
			}
		}
	}
}
