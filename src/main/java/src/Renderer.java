/**
 * 
 */
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Date;
import java.util.zip.ZipFile;

import com.amazonaws.services.s3.AmazonS3;

/**
 * Class Responsible for the rendering work
 * 
 * @author Eduardo Hernandez Marquina
 * @author Hector Veiga
 * @author Gerardo Travesedo
 * 
 */
public class Renderer implements Runnable {

	private static int rowID;
	private static String receiptHandle;
	// SQS receipt Handle of a SQS message
	private static Message MessageObject;
	private InstanceManager instanceManager;
	private static AmazonS3 s3;
	private static DBManager db;

	public Renderer(int rowID, String receiptHandle, Message msg,
			InstanceManager instanceManager, AmazonS3 s3, DBManager db) {
		super();
		setReceiptHandle(receiptHandle);
		setRowID(rowID);
		setMessageObject(msg);
		setInstanceManager(instanceManager);
		setS3(s3);
		setDB(db);
		new Thread(this, "Renderer").start();
	}

	@Override
	public void run() {
		// Initializations
		VideoData vData;
		Logger l = new Logger();

		// Time mark before the rendering work
		Date time1 = new Date();
		long epoch1 = (long) System.currentTimeMillis() / 1000;

		try {
			// Creating a VideoData object with the info related to the
			// corresponding rowID
			vData = getDb().getInfo(getRowID(), getMessageObject(),
					getInstanceManager(), l);
			// A light validation of the info retrieved
			vData.validateS3KeyOriginal(getInstanceManager(),
					getMessageObject(), l);

			// Download Video from S3
			vData.print(getMessageObject());
			int origSize = vData.downloadVideo(getS3(), getMessageObject(), l);

			// Checks if the video is zipped
			// It works just with .zip files
			String[] nameExt = vData.nameExetension();
			boolean isZip = vData.isVideoZipped(getMessageObject(), l, nameExt);
			zipConversion(isZip, vData, l);

			// getting the Conversion script
			String command = setRenderingScript(isZip, vData,
					getMessageObject(), l);
			getMessageObject().putResource(l.logging("Ready to convert"));

			// Updating the state in the Data base from pending to converting
			db.updateStatus(getRowID(), "converting", l);

			// Execute the rendering process
			String outputProc = runRenderingCommmand(command, vData,
					getMessageObject(), l);

			// Upload the converted Video/Audio to S3
			File finishedFile = vData.uploadVideo(getS3(), getMessageObject(),
					l);

			// Upload to Walrus would be here!
			// Edu commend: No idea of what Hector and Gerardo did put the that
			// commend

			// Update Database Status with the rendered Video Data
			getMessageObject().putResource(l.logging("Updating databases...")); //
			db.UpdateStatusRenderingIsFinished(getRowID(), vData, l,
					getMessageObject(), getS3());

			// SES-SNS
			Date time2 = new Date();
			long epoch2 = (long) System.currentTimeMillis() / 1000;
			long elapsedTime = epoch2 - epoch1;

			StringBuilder sb = new StringBuilder();
			sb.append("File is located in: https://s3.amazonaws.com/"
					+ vData.getS3BucketFinished() + "/"
					+ vData.getS3KeyFinished() + " \n\n\n");
			sb.append("Debug information:\n");
			sb.append("Start time: " + time1 + "\n");
			sb.append("Finish time: " + time2 + "\n\n");
			sb.append("Output:\n");
			sb.append(outputProc);
			String output = sb.toString();

			/*
			 * Right now, everything is autorender. String subject = "";
			 * 
			 * if(format.equals("mp3")) { subject =
			 * "Luna: Your audio is ready!"; } else { subject =
			 * "Luna: Your video is ready!"; } if(!autorender) {
			 * logging("Sending email to user..."); Jmailserver mail = new
			 * Jmailserver("gmail.com", "noreplylunaproject", "LunaMoonlight");
			 * mail.sendMessage(userId, subject,
			 * "You can watch your media using our website: https://luna.sat.iit.edu"
			 * , "text/plain"); }
			 */

			// Logs for debugging
			// getMessageObject().putResource(
			// l.logging("Information for debugging:"));
			String instanceType = getInstanceManager().getInstanceType();
			String origType = nameExt[nameExt.length - 1];
			// getMessageObject().putResource(l.logging("ORIGTYPE = " +
			// origType));
			// getMessageObject().putResource(
			// l.logging("INSTANCETYPE = " + instanceType));
			// getMessageObject().putResource(l.logging("OrigSize = " +
			// origSize));
			int finSize = (int) finishedFile.length();
			// getMessageObject().putResource(l.logging("FinSize = " +
			// finSize));
			String output2 = output.replace("'", " ");
			String output3 = output2.replace("\"", " ");
			// getMessageObject().putResource(l.logging("Output = " + output3));

			// Insert logs into the DB
			db.updateStatusDebuggingData(epoch1, epoch2, origType,
					instanceType, origSize, finSize, output3, vData,
					getMessageObject(), l);

			// Updating parameters for statistics
			if (vData.isAutorender()) {
				db.updateParameter("autoConversions", 1, l);
			} else {
				db.updateParameter("onDemandConversions", 1, l);
			}
			if (instanceType.toLowerCase().contains("micro")) {
				db.updateParameter("elapsedTimeMicro", elapsedTime, l);
			} else {
				db.updateParameter("elapsedTimeMedium", elapsedTime, l);
			}

			// Everything Done!!
			// Insert in the share resource the key ="Autokilling!" to
			// terminate the AWS Instance
			getMessageObject().putResource(
					l.logging("The rendering has been finished"));
			// Do not l.logging the Autokiling!, if time marke is put the
			// instance will never terminate
			getMessageObject().putResource("Autokilling!");
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * Executes the rendering command
	 * 
	 * @param command
	 * @param vData
	 * @param messageObject2
	 * @param l
	 * @return the error outPut of the rendering process
	 * @throws IOException
	 * @throws InterruptedException
	 */
	private String runRenderingCommmand(String command, VideoData vData,
			Message messageObject2, Logger l) throws IOException,
			InterruptedException {

		// Percentage Test variables
		int totalSecs = 0;
		int linesPerUpdate = 35;
		int linesCount = 0;

		StringBuilder outputProc = new StringBuilder();
		String line = "";

		Process p = Runtime.getRuntime().exec(command);
		BufferedReader bri = new BufferedReader(new InputStreamReader(
				p.getErrorStream()));

		while ((line = bri.readLine()) != null) {
			outputProc.append(line + "\n");
			// getMessageObject().putResource(l.logging(line));
			// if ((!vData.getFormat().equals("mp3"))
			// && line.contains("work result = 0"))
			// p.destroy();

			if (!vData.isTs() || (vData.getFormat().equals("mp3"))) {
				// Percetage done (FFMPEG ONLY)
				if (line.contains("Duration")) {
					try {
						totalSecs = giveMeSeconds(line, "Duration: ", ",");
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
			}
			if ((!vData.isTs() && linesCount == linesPerUpdate)
					|| (vData.getFormat().equals("mp3") && linesCount == linesPerUpdate)) {
				if ((line.contains("frame=") && line.contains("fps=") && line
						.contains("time="))
						|| (line.contains("time=") && line.contains("size="))) {
					try {
						int elapsedSecs = giveMeSeconds(line, "time=", " ");
						double percentage = ((double) elapsedSecs / (double) totalSecs) * 100;
						db.updateStatus(getRowID(), Double.toString(percentage)
								.substring(0, 5), l);
					} catch (Exception e) {
					}
				}
				linesCount = 0;
			}
			linesCount++;
		}
		bri.close();

		getMessageObject().putResource(
				l.logging("Waiting for process to finish"));

		// Sending the status every 30 minutes to keep alive the
		// LunaCore Communication thread
		int i = 0;
		int hours, min;
		while (!processIsTerminated(p)) {
			hours = (i * 30) % 60;
			min = i * 30 - hours * 60;
			getMessageObject().putResource(
					l.logging("Renderer is working, elapsed time: " + hours
							+ " hours and " + min + " minutes"));
			i += 1;
			Thread.sleep(60 * 1000);
		}
		l.logging("File converted successfully");

		return outputProc.toString();

	}

	/**
	 * 
	 * @param isZip
	 * @param vData
	 * @param message
	 * @param l
	 * @return the rendering Unix Command
	 * 
	 */
	private String setRenderingScript(boolean isZip, VideoData vData,
			Message message, Logger l) {
		getMessageObject().putResource(
				l.logging("Setting up script to convert:"));

		String fileName = vData.nakedFileName();
		if (isZip) {
			int randNum = (int) (Math.random() * 1000000000);
			fileName = "LV" + randNum + "_" + fileName;
		}

		String command = "";
		String finishedName = "";
		if (vData.getFormat().equals("ipad")) {
			finishedName = fileName + "_ipad.mp4";
			getMessageObject()
					.putResource(
							l.logging("Converting file to iPad format. Output filename: "
									+ finishedName));
			command = "sudo HandBrakeCLI -i " + vData.getS3KeyOriginal()
					+ " -o " + finishedName
					+ " -e x264 -q 32 -B 128 -w 800 --loose-anamorphic -O";
		} else if (vData.getFormat().equals("mp4")) {
			finishedName = fileName + "_fullsize.mp4";
			getMessageObject()
					.putResource(
							l.logging("Converting file to Fullsize format. Output filename: "
									+ finishedName));
			command = "sudo HandBrakeCLI -v -i " + vData.getS3KeyOriginal()
					+ " -o " + finishedName + " -e x264 -q 32 -B 128 -O";
		} else if (vData.getFormat().equals("iphone")) {
			finishedName = fileName + "_iphone.mp4";
			getMessageObject()
					.putResource(
							l.logging("Converting file to iPhone format. Output filename: "
									+ finishedName));
			command = "sudo HandBrakeCLI -i " + vData.getS3KeyOriginal()
					+ " -o " + finishedName
					+ " -e x264 -q 32 -B 128 -w 640 --loose-anamorphic -O";
		} else if (vData.getFormat().equals("mp3")) {
			getMessageObject().putResource(
					l.logging("Extracting audio from file. Output filename: "
							+ finishedName));
			finishedName = fileName + "_audio.mp3";
			command = "sudo ffmpeg -y -i " + vData.getS3KeyOriginal()
					+ " -vn -ab 96k -ar 44100 -f mp3 " + finishedName;
		}

		vData.setS3KeyFinished(finishedName);
		getMessageObject().putResource(
				l.logging("Command to convert: " + command));

		return command;
	}

	/**
	 * Do the unzip the Video if it is zipped
	 * 
	 * @param isZip
	 * @param vData
	 * @param l
	 * @throws IOException
	 * @throws InterruptedException
	 */
	private void zipConversion(boolean isZip, VideoData vData, Logger l)
			throws IOException, InterruptedException {
		if (isZip) {
			ZipFile sourceZipFile = new ZipFile(vData.getS3KeyOriginal());
			getMessageObject()
					.putResource(
							l.logging("Zip File found. Decompressing and removing .zip file."));
			String unzip_filename = sourceZipFile.entries().nextElement()
					.getName();

			// Unzipping
			Process p = Runtime.getRuntime().exec(
					"sudo unzip " + vData.getS3KeyOriginal());
			waitingProcessWhile(p, l);
			sourceZipFile.close();

			// Deleting the zip file
			Process p2 = Runtime.getRuntime().exec(
					"sudo rm " + vData.getS3KeyOriginal());
			waitingProcessWhile(p2, l);

			// Updating the vData object
			vData.setS3KeyOriginal(unzip_filename);
		}
	}

	// To know when a external process is terminated
	private boolean processIsTerminated(Process p) {
		try {
			p.exitValue();
		} catch (IllegalThreadStateException itse) {
			return false;
		}
		return true;
	}

	// Sending the status every 30 minutes to keep alive the
	// LunaCore Communication thread
	private void waitingProcessWhile(Process p, Logger l)
			throws InterruptedException {
		int i = 0;
		int hours, min;
		while (!processIsTerminated(p)) {
			hours = (i * 30) % 60;
			min = i * 30 - hours * 60;
			getMessageObject().putResource(
					l.logging("Renderer is working, elapsed time: " + hours
							+ " hours and " + min + " minutes"));
			i += 1;
			Thread.sleep(60 * 1000);
		}
	}

	private String substringBetween(String str, String open, String close) {
		if (str == null || open == null || close == null) {
			return null;
		}
		int start = str.indexOf(open);
		if (start != -1) {
			int end = str.indexOf(close, start + open.length());
			if (end != -1) {
				return str.substring(start + open.length(), end);
			}
		}
		return null;
	}

	private int giveMeSeconds(String line, String del1, String del2) {
		String durVid = "";
		String[] durVidPieces;

		durVid = substringBetween(line, del1, del2);
		durVidPieces = durVid.split(":");
		int secs = (Integer.parseInt(durVidPieces[0]) * 3600)
				+ (Integer.parseInt(durVidPieces[1]) * 60)
				+ Integer.parseInt(durVidPieces[2].substring(0, 2));
		return secs;
	}

	public int getRowID() {
		return rowID;
	}

	public void setRowID(int rowID2) {
		this.rowID = rowID2;
	}

	public String getReceiptHandle() {
		return receiptHandle;
	}

	public void setReceiptHandle(String receiptHandle) {
		this.receiptHandle = receiptHandle;
	}

	public Message getMessageObject() {
		return MessageObject;
	}

	public void setMessageObject(Message messageObject) {
		MessageObject = messageObject;
	}

	public InstanceManager getInstanceManager() {
		return instanceManager;
	}

	public void setInstanceManager(InstanceManager instanceManager) {
		this.instanceManager = instanceManager;
	}

	public static AmazonS3 getS3() {
		return s3;
	}

	public static void setS3(AmazonS3 s3) {
		Renderer.s3 = s3;
	}

	public static DBManager getDb() {
		return db;
	}

	public static void setDB(DBManager db) {
		Renderer.db = db;
	}

}
