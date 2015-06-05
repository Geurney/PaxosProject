package site;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.Scanner;
import java.util.Set;

public class Site {
	private CLIThread CLI;
	private CommThread Comm;
	private ArrayList<String[]> config;
	private int siteID;
	private InetAddress hostname;
	private int port;

	public Site(ArrayList<String[]> config) {
		this.config = config;
		try {
			this.hostname = InetAddress.getByName(this.config.get(7)[0]);
			this.port = Integer.parseInt(this.config.get(7)[1]);
		} catch (NumberFormatException e) {
			e.printStackTrace();
		} catch (UnknownHostException e) {
			e.printStackTrace();
		}
		Comm = new CommThread();
		CLI = new CLIThread();
		this.siteID = Integer.parseInt(this.config.get(0)[0]);
		System.out.println("Site" + this.siteID + " is running on "
				+ this.config.get(7)[0] + " port: " + this.config.get(7)[1]);
	}

	public void start() {
		Comm.start();
		CLI.start();
	}

	private class CLIThread extends Thread {
		@Override
		public void run() {
			String command;
			System.out.println("Site" + siteID + ": Please enter a command:");
			Scanner sc = null;
			sc = new Scanner(System.in);

			while ((command = sc.nextLine()) != null) {
				if (command.length() == 0) {
					sc.close();
					Comm.interrupt();
					return;
				}
				System.out.println("Site" + siteID + ": Command: " + command);
				Random rand = new Random();
				int[] Q = new int[2];
				HashSet<Integer> quorum = new HashSet<Integer>();
				quorum.add(siteID);

				for (int i = 0; i < 2; i++) {
					do {
						Q[i] = rand.nextInt(5) + 1;
					} while(!quorum.add(Q[i]));
				}

				System.out.println("Site" + siteID + ": Quorum: " + siteID
						+ " " + Q[0] + " " + Q[1]);

				// accept grant or fail message. prepare server first.
				ServerSocket serverSocket;
				try {
					serverSocket = new ServerSocket(port + 1, 5, hostname);
				} catch (IOException e) {
					System.out.println("CLI ServerSocket Failed.");
					e.printStackTrace();
					return;
				}

				while (true) {
					List<String> result = new ArrayList<String>();
					// send requests to quorum
					sendRequest(Q[0], Q[1], command);
					// // Wait for a client to connect (blocking)
					for (int i = 0; i < 3; i++) {
						Socket mysocket;
						try {
							mysocket = serverSocket.accept();
						} catch (IOException e) {
							e.printStackTrace();
							System.out
									.println("CLI ServerSocket Accept Failed.");
							return;
						}
						BufferedReader in;
						try {
							in = new BufferedReader(new InputStreamReader(
									mysocket.getInputStream()));
						} catch (IOException e) {
							e.printStackTrace();
							System.out.println("CLI ServerSocket Read Failed.");
							return;
						}

						// Read event from Quorum
						String input;
						String[] input_split = null;
						try {
							input = in.readLine();
							input_split = input.split("\"");
						} catch (IOException e) {
							e.printStackTrace();
							System.out.println("CLI Read Quorum reply failed.");
							input = "Unknown";
						}

						String operation = input_split[1];
						switch (operation) {
						case "FAIL":
							result.add(input);
							break;
						case "GRANT":
							result.add(input);
							break;
						}
						System.out.println("Site" + siteID + ": Receive "
								+ operation + " from Site " + input_split[0]);
						try {
							mysocket.close();
						} catch (IOException e) {
							e.printStackTrace();
						}
					}

					Set<Integer> fail_site = new HashSet<Integer>();
					Set<Integer> grant_site = new HashSet<Integer>();
					for (int i = 0; i < 3; i++) {
						if (result.get(i).contains("FAIL")) {
							fail_site.add(Integer.parseInt(result.get(i).split(
									"\"")[0]));
						}
					}
					if (!fail_site.isEmpty()) {
						// find sites that send grant using set difference
						grant_site.addAll(quorum);
						grant_site.removeAll(fail_site);
						for (int q : grant_site) {
							sendRelease(q);
						}
						System.out.println("Site" + siteID
								+ ": The request is faild. Send it again!");
						try {
							Thread.sleep(new Random().nextInt(1500) + siteID
									* 200);
						} catch (InterruptedException e) {
							e.printStackTrace();
						}
					} else {
						accessLog(Q[0], Q[1], command);
						for (int q : quorum) {
							sendRelease(q);
						}
						System.out.println("Site" + siteID + ": " + command
								+ " is finished!");
						break;
					}
				}
				try {
					serverSocket.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
			sc.close();
		}

		private void accessLog(int Q1, int Q2, String command) {
			Socket mysocket;
			while (true) {
				try {
					mysocket = new Socket(config.get(6)[0],
							Integer.parseInt(config.get(6)[1]));
					break;
				} catch (IOException e) {
					e.printStackTrace();
					return;
				}
			}
			// Establish input and output streams with the server
			PrintWriter out;
			BufferedReader in;
			try {
				out = new PrintWriter(mysocket.getOutputStream(), true);
				in = new BufferedReader(new InputStreamReader(
						mysocket.getInputStream()));
			} catch (IOException e) {
				e.printStackTrace();
				try {
					mysocket.close();
				} catch (IOException e1) {
					e1.printStackTrace();
				}
				return;
			}
			// Send event to log
			StringBuilder sb = new StringBuilder();
			String operation = null;
			if (command.startsWith("R")) {
				operation = "READ";
			} else {
				operation = "APPEND";
			}
			sb.append("Site").append(siteID).append("\"").append(operation)
					.append("\"").append(siteID).append("\'").append(Q1)
					.append("\'").append(Q2).append("\"");
			if (operation.startsWith("A")) {
				String msg = command.substring(command.indexOf(' ') + 1,
						command.length());
				if (msg.length() > 140) {
					sb.append(msg.substring(0, 140));
				} else {
					sb.append(msg);
				}
			}
			System.out.println("Site" + siteID + ": Access to the log!");
			out.println(sb.toString());
			// Wait for a reply from the log (blocking)
			String reply = null;
			try {
				reply = in.readLine();
				if (command.startsWith("R")) {
					System.out.println("Site" + siteID
							+ ": Read from the log: " + reply);
					out.println("Site" + siteID + "\"RELEASEREAD\"" + siteID
							+ "\'" + Q1 + "\'" + Q2);
				} else {
					System.out.println("Site" + siteID + ": Receive " + reply
							+ " from the log!");
					out.println("Site" + siteID + "\"RELEASEAPPEND\"" + siteID
							+ "\'" + Q1 + "\'" + Q2);
				}
			} catch (IOException e) {
				e.printStackTrace();
				try {
					mysocket.close();
				} catch (IOException e1) {
					e1.printStackTrace();
				}
				return;
			}
			// Release to log.
			System.out.println("Site" + siteID + ": Send release to log");
			try {
				reply = in.readLine();
				System.out.println("Site" + siteID + ": Receive " + reply
						+ " from the log!");
			} catch (IOException e) {
				e.printStackTrace();
			}
			try {
				mysocket.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		private void sendRelease(int q) {
			Socket mysocket;
			try {
				mysocket = new Socket(config.get(q)[0], Integer.parseInt(config
						.get(q)[1]));
			} catch (IOException e) {
				e.printStackTrace();
				return;
			}
			PrintWriter out;
			try {
				out = new PrintWriter(mysocket.getOutputStream(), true);
			} catch (IOException e) {
				e.printStackTrace();
				try {
					mysocket.close();
				} catch (IOException e1) {
					e1.printStackTrace();
				}
				return;
			}
			// Send event to server
			out.println(siteID + "\"" + "RELEASE");
			System.out.println("Site" + siteID + ": Send release to Site " + q);
			try {
				mysocket.close();
			} catch (IOException e) {
				e.printStackTrace();
			}

		}

		private void sendRequest(int Q1, int Q2, String command) {
			Socket mysocket1;
			Socket mysocket2;
			Socket mysocket3;
			try {
				mysocket1 = new Socket(config.get(siteID)[0],
						Integer.parseInt(config.get(siteID)[1]));
				mysocket2 = new Socket(config.get(Q1)[0],
						Integer.parseInt(config.get(Q1)[1]));
				mysocket3 = new Socket(config.get(Q2)[0],
						Integer.parseInt(config.get(Q2)[1]));
			} catch (IOException e) {
				System.out.println("Send Socekt Fail!");
				e.printStackTrace();
				return;
			}

			// Establish input and output streams with the server
			PrintWriter out1;
			PrintWriter out2;
			PrintWriter out3;
			try {
				out1 = new PrintWriter(mysocket1.getOutputStream(), true);
				out2 = new PrintWriter(mysocket2.getOutputStream(), true);
				out3 = new PrintWriter(mysocket3.getOutputStream(), true);
			} catch (IOException e) {
				e.printStackTrace();
				try {
					mysocket1.close();
					mysocket2.close();
					mysocket3.close();
				} catch (IOException e1) {
					e1.printStackTrace();
				}
				return;
			}

			// Send event to server
			if (command.charAt(0) == 'R') {
				out1.println(siteID + "\"" + "READ");
				out2.println(siteID + "\"" + "READ");
				out3.println(siteID + "\"" + "READ");
			} else {
				out1.println(siteID + "\"" + "WRITE");
				out2.println(siteID + "\"" + "WRITE");
				out3.println(siteID + "\"" + "WRITE");
			}
			try {
				mysocket1.close();
				mysocket2.close();
				mysocket3.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	private class CommThread extends Thread {
		private List<String> activeLock;
		private List<String> requestLock;

		public CommThread() {
			this.activeLock = new ArrayList<String>();
			this.requestLock = new ArrayList<String>();
		}

		@Override
		public void run() {
			ServerSocket serverSocket;
			try {
				serverSocket = new ServerSocket(port, 5, hostname);
			} catch (IOException e) {
				e.printStackTrace();
				return;
			}
			while (!isInterrupted()) {
				Socket mysocket;
				try {
					// Wait for a client to connect (blocking)
					mysocket = serverSocket.accept();
				} catch (IOException e) {
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
				String[] input_split = null;
				try {
					input = in.readLine();
					input_split = input.split("\"");
				} catch (IOException e) {
					e.printStackTrace();
					input = "UnknownEvent";
				}

				String site = input_split[0];
				String operation = input_split[1];
				switch (operation) {
				case "RELEASE":
					// Remove the request from the list of active locks
					for (Iterator<String> it = activeLock.iterator(); it
							.hasNext();) {
						String val = it.next();
						if (val.contains(site)) {
							it.remove();
							break;
						}
					}
					// Check if this lock-release permits other requests from
					// the queue to be granted
					if (requestLock.isEmpty() != true) {
						if (activeLock.isEmpty()) {
							String newRequest = requestLock.remove(0);
							activeLock.add(newRequest);
							sendGrant(newRequest.substring(0,
									newRequest.indexOf('\"')));
						}
					}
					break;

				case "READ":
					if ((!activeLock.isEmpty())
							&& activeLock.get(0).contains("WRITE")) {
						System.out.println("Site" + siteID
								+ ": Reject to Site " + site + " Read");
						sendFail(site);
					} else {
						activeLock.add(input);
						System.out.println("Site" + siteID + ": Grant to Site "
								+ site + " Read");
						sendGrant(site);
					}
					break;

				case "WRITE":
					if (activeLock.isEmpty()) {
						activeLock.add(input);
						System.out.println("Site" + siteID + ": Grant to Site "
								+ site + " Write");
						sendGrant(site);
					} else {
						if (activeLock.get(0).contains("WRITE")) {
							System.out.println("Site" + siteID
									+ ": Reject to Site " + site + " Write");
							sendFail(site);
						} else {
							requestLock.add(input);
						}
					}
					break;
				}
			}
			try {
				serverSocket.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		private void sendFail(String site) {
			Socket mysocket;
			try {
				mysocket = new Socket(
						config.get(Integer.parseInt(site))[0],
						Integer.parseInt(config.get(Integer.parseInt(site))[1]) + 1);
			} catch (IOException e) {
				e.printStackTrace();
				return;
			}
			PrintWriter out;
			try {
				out = new PrintWriter(mysocket.getOutputStream(), true);
			} catch (IOException e) {
				e.printStackTrace();
				try {
					mysocket.close();
				} catch (IOException e1) {
					e1.printStackTrace();
				}
				return;
			}
			out.println(siteID + "\"" + "FAIL");
			try {
				mysocket.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		private void sendGrant(String site) {
			Socket mysocket;
			try {
				mysocket = new Socket(
						config.get(Integer.parseInt(site))[0],
						Integer.parseInt(config.get(Integer.parseInt(site))[1]) + 1);
			} catch (IOException e) {
				e.printStackTrace();
				return;
			}
			PrintWriter out;
			try {
				out = new PrintWriter(mysocket.getOutputStream(), true);
			} catch (IOException e) {
				e.printStackTrace();
				try {
					mysocket.close();
				} catch (IOException e1) {
					e1.printStackTrace();
				}
				return;
			}
			out.println(siteID + "\"" + "GRANT");
			try {
				mysocket.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}
}
