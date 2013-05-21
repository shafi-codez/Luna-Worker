import java.util.Date;

public class Logger {

	/**
	 * Class for logging methods
	 * 
	 * @author Eduardo Hernandez Marquina
	 * @author Hector Veiga
	 * @author Gerardo Travesedo
	 * 
	 */
	public Logger() {
	}

	/**
	 * Auxiliary Method
	 * 
	 * @param str
	 * @param open
	 * @param close
	 * @return
	 */
	public String substringBetween(String str, String open, String close) {
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

	/**
	 * Gets the time
	 * 
	 * @param line
	 * @param del1
	 * @param del2
	 * @return
	 */
	public int giveMeSeconds(String line, String del1, String del2) {
		String durVid = "";
		String[] durVidPieces;

		durVid = substringBetween(line, del1, del2);
		durVidPieces = durVid.split(":");
		int secs = (Integer.parseInt(durVidPieces[0]) * 3600)
				+ (Integer.parseInt(durVidPieces[1]) * 60)
				+ Integer.parseInt(durVidPieces[2].substring(0, 2));
		return secs;
	}

	/**
	 * Prints the logging line
	 * 
	 * @param lineToLog
	 * @return
	 */
	public String logging(String lineToLog) {
		Date time = new Date();
		String line = "[" + time.toString() + "] " + lineToLog;
		System.out.println(line);
		return line;
	}

}