package com.example.chat_and_server;

import android.content.Context;
import android.os.Environment;
import android.util.Log;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Queue;
import java.security.*;

class ChatServer {
	HashMap<String, HandleASession> UserMap = new HashMap<>();

	ChatServer() {
		Log.d("1","qwwwqq");
		try {
			ServerSocket serverSocket = new ServerSocket(50004);
			while (true) {
				Socket msg = serverSocket.accept();
				Socket file = serverSocket.accept();
				System.out.println("client IP:" + msg.getInetAddress().getHostAddress());
				TargetConnection t = new TargetConnection(msg, file);// 将传输过程打包
				if (!t.check()) {// 需要在这里check还是login？
					System.out.println("client IP:" + msg.getInetAddress().getHostAddress() + "-连接失败");
					continue;
				}
				Login NewClinet = new Login(t);
				new Thread(NewClinet).start();// 建立线程实现多用户使用
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	class Login implements Runnable, Flag {
		TargetConnection t;

		Login(TargetConnection tar) {
			this.t = tar;
		}

		@Override
		public void run() {
			int sign;
			String username;
			String password;
			while (true) {
				try {// 从用户端接收登录信息
					sign = t.getMsgFromClient().readInt();// 接收登录or注册信号:登录1，注册2
					username = t.getMsgFromClient().readUTF();
					password = t.getMsgFromClient().readUTF();
					byte[] pswdCode = TransPswd(password);
					// 为了我们的文件目录统一，使用getProperty得到项目目录
//					String FilePath= Environment.getExternalStorageState();
//					File user = new File(
//							"users/" + username + "/userinfo.key");
//					if (sign == Flag.LOGIN) {// 登录
//						if (!user.exists()) {// 如果没有此账号
//							t.getMsgToClient().writeInt(Flag.FAIL);
//							continue;
//						}
//						// 对照密码
//						FileInputStream fis = new FileInputStream(user);
//						byte[] pswd=new byte[1024];
//						fis.read(pswd);
//						if (Arrays.equals(pswd, pswdCode)) {// 登录成功
							t.getMsgToClient().writeInt(Flag.SUCCESS);// 向用户发送成功信号
							t.setUsername(username);
							System.out.println(t.getMsgSocket().getInetAddress().getHostAddress() + ":登录为 " + username);
							HandleASession hand = new HandleASession(t);//
							UserMap.put(username, hand);// 将用户放入hashmap__还需要拿出来
//							fis.close();
//							break;
//						} else {
//							t.getMsgToClient().writeInt(Flag.FAIL);
//						}
//						fis.close();
//					}
//					if (sign == Flag.SIGNUP) {// 注册
//						if (user.exists()) {// 如果已经有此账号
//							t.getMsgToClient().writeInt(Flag.FAIL);
//							continue;
//						}
//						File parent = user.getParentFile();
//						parent.mkdirs();
//						user.createNewFile();
//						// 保存密码
//						FileOutputStream fos = new FileOutputStream(user);
//						fos.write(pswdCode, 0, pswdCode.length);
//						fos.flush();
//						fos.close();
//						CreateNewUser(parent);// 创建用户文件夹
//						t.getMsgToClient().writeInt(Flag.SUCCESS);
//					}

				} catch (IOException e) {
					System.out.println(t.getMsgSocket().getInetAddress().getHostAddress() + ":退出");
					return;
				}
			}
		}

		private byte[] TransPswd(String password) {
			MessageDigest md5 = null;
			try {
				md5 = MessageDigest.getInstance("MD5");
				byte[] srcBytes = password.getBytes();
				md5.update(srcBytes);
			} catch (NoSuchAlgorithmException e) {
				e.printStackTrace();
			}
			byte[] resultBytes = md5.digest();
			return resultBytes;
		}

		private void CreateNewUser(File f) {// 新建用户的文件格式在这里声明，目前已有userinfo
			try {
				new File(f.getAbsolutePath() + "/groupList.txt").createNewFile();
				new File(f.getAbsolutePath() + "/friendList.txt").createNewFile();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	class HandleASession {// 在这里实现信息交互
		TargetConnection t;
		Reciever reciever = new Reciever();
		Sender sender = new Sender();
		Queue<MsgPair> MsgQueue = new LinkedList<MsgPair>();
		int go = 1;

		HandleASession(TargetConnection t) {
			this.t = t;
			new Thread(reciever).start();
			new Thread(sender).start();
		}

		private class MsgPair {
			int flag;
			String MsgString;

			MsgPair(int flag, String Msg) {
				this.flag = flag;
				this.MsgString = Msg;
			}
		}

		private class Reciever implements Runnable {

			@Override
			public void run() {
				while (true) {
					int sign;
					try {
						sign = t.getMsgFromClient().readInt();
						switch (sign) {
						case Flag.SENDTEXT: {
							SendMsg();
							break;
						}
						case Flag.SENDFILE:/* 往下先等等 */
						{
							break;
						}
						default:
							break;
						}
					} catch (IOException e)// 如果是收信者下线该怎么办？_写函数，别在这里实现逻辑
					{
						System.out.println(t.getMsgSocket().getInetAddress().getHostAddress() + ":退出");
						// 从map中删掉
						sender.stop();
						UserMap.remove(t.getUsername());
						return;
					}
				}
			}

			private void SendMsg()// 发消息用不同函数实现可以么？是不是有点不优雅
			{// 先直接发，后面写存文件待发送
				String TargetName = null, Msg = null;
				try {
					TargetName = t.getMsgFromClient().readUTF();
					Msg = t.getMsgFromClient().readUTF();
				} catch (IOException e) {
					e.printStackTrace();
				}
				// String[] split = Msg.split("\\|");
				if (isFriend(TargetName)) {// 这里是不是要加上判断这个人是不是对方好友
					if (UserMap.containsKey(TargetName)) {
						System.out.println("向" + TargetName + "发送信息");
						HandleASession h2 = UserMap.get(TargetName);
						h2.sender.PutMsg(new MsgPair(Flag.SENDTEXT, Msg));// str格式再想一下
					} else {
						AddMsgToFile(TargetName, new MsgPair(Flag.SENDTEXT, Msg));
					}
				} else {// 这里实现发群的逻辑

				}
			}

			private boolean isFriend(String tar)// 判断这个目标是好友or群组
			{
				return true;
			}

			private void AddMsgToFile(String username, MsgPair mp) {// 图片怎么办？，但应该差别不大

			}
		}

		private class Sender implements Runnable {

			public void stop() {
				go = 0;
			}

			public void PutMsg(MsgPair ss) {
				MsgQueue.add(ss);
				// System.out.println("测试一下");
			}

			@Override
			public void run() {
				while (go == 1) {
					try {
						Thread.sleep(5);// 为啥能用了？
					} catch (InterruptedException e1) {
						e1.printStackTrace();
					}
					if (!MsgQueue.isEmpty()) {// 这个改成锁
						System.out.println("排好队！");
						MsgPair mp = MsgQueue.poll();
						try {
							t.getMsgToClient().writeInt(mp.flag);
							t.getMsgToClient().writeUTF(mp.MsgString);// 失败后写文件(吗？)
						} catch (IOException e) {// 写文件
							e.printStackTrace();
						}
					}
				}
			}

			Sender() {// 把文件中的指令扔进队列里

			}

		}

	}
}

class TargetConnection {// 建立一个类用以存放与用户的连接
	private Socket MsgSocket;
	private Socket FileSocket;
	private String username;
	private DataInputStream MsgFromClient;
	private DataOutputStream MsgToClient;
	private DataInputStream FileFromClient;
	private DataOutputStream FileToClient;

	TargetConnection(Socket msg, Socket file) {
		this.MsgSocket = msg;
		this.FileSocket = file;
		try {
			MsgFromClient = new DataInputStream(MsgSocket.getInputStream());
			MsgToClient = new DataOutputStream(MsgSocket.getOutputStream());
			FileFromClient = new DataInputStream(FileSocket.getInputStream());
			FileToClient = new DataOutputStream(FileSocket.getOutputStream());
		} catch (IOException e2) {
			e2.printStackTrace();
		}
	}

	boolean check() {// 判断两个Socket是否连接到同一个用户
		int rand = (int) (Math.random() * 100);
		int tip = Flag.FAIL;
		try {
			MsgToClient.writeInt(rand);
			FileToClient.writeInt(rand);
			tip = MsgFromClient.readInt();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return (tip == Flag.SUCCESS);
	}

	void setUsername(String str) {
		this.username = str;
	}

	String getUsername() {
		return this.username;
	}

	DataInputStream getMsgFromClient() {
		return MsgFromClient;
	}

	DataOutputStream getMsgToClient() {
		return MsgToClient;
	}

	DataInputStream getFileFromClient() {
		return FileFromClient;
	}

	DataOutputStream getFileToClient() {
		return FileToClient;
	}

	Socket getMsgSocket() {
		return this.MsgSocket;
	}

}
