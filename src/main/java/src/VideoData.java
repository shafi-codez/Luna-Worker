import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.StorageClass;

/**
 * Container Class for the Video Data
 */

/**
 * @author Eduardo Hernandez Marquina
 * 
 */
public class VideoData {

	private String userId;
	private String className;
	private String classDate;
	private String format;
	private String S3BucketOriginal;
	private String S3BucketFinished;
	private String S3KeyOriginal;
	private String S3KeyFinished;
	private String part;
	private boolean autorender;

	public VideoData() {

	}

	public void print(Message ms) {
		Logger l = new Logger();
		ms.putResource(l.logging("userId= " + getUserId()));
		ms.putResource(l.logging("className= " + getClassName()));
		ms.putResource(l.logging("classDate= " + getClassDate()));
		ms.putResource(l.logging("format= " + getFormat()));
		ms.putResource(l.logging("S3BucketOriginal= " +
		getS3BucketOriginal()));
		ms.putResource(l.logging("S3BucketFinished= " +
		getS3BucketFinished()));
		ms.putResource(l.logging("S3KeyOriginal= " + getS3KeyOriginal()));
		ms.putResource(l.logging("S3KeyFinished= " + getS3KeyFinished()));
		ms.putResource(l.logging("parts= " + getPart()));
		ms.putResource(l.logging("Autorender= " + isAutorender()));
	}
	
	public void setAutorender(String autorender) {
		if (Integer.parseInt(autorender) == 1) {
			this.autorender = true;
		} else {
			this.autorender = false;
		}

	}

	/**
	 * Checks if the Video Data that is needed for the rendering work have been
	 * retrieved successfully form the Data Base. This function probably need to
	 * be improved, It is only checked 2 parameters.
	 * 
	 * @param im
	 *            The InstanceManager Object
	 * @param ms
	 *            The Message Object
	 * @param l
	 *            A Logger Object
	 */
	public void validateS3KeyOriginal(InstanceManager im, Message ms, Logger l) {
		if (getS3KeyOriginal() == null || getS3KeyOriginal().equals("")) {
			im.killmePlease();
			// TODO: ..CASO DE ERROR
		} else if (getS3BucketOriginal() == null
				|| getS3BucketOriginal().equals("")) {
			im.killmePlease();
			// TODO: ..CASO DE ERROR
		} else {
			ms.putResource(l
					.logging("Data retrieved from table 'requests' successfully"));
		}
	}

	/**
	 * Download the Video from the S3 bucket
	 * 
	 * @param s3
	 * @param ms
	 * @param l
	 * 
	 * @return The size of the Video to be converted
	 */
	public int downloadVideo(AmazonS3 s3, Message ms, Logger l) {
		ms.putResource(l.logging("Trying to download '" + getS3KeyOriginal()
				+ "' from luna-videos-before S3 bucket..."));
		GetObjectRequest S3Original = new GetObjectRequest(
				getS3BucketOriginal(), getS3KeyOriginal());
		S3Object originalObject = s3.getObject(S3Original);

		InputStream reader = new BufferedInputStream(
				originalObject.getObjectContent());
		File file = new File(getS3KeyOriginal());
		OutputStream writer;
		try {
			writer = new BufferedOutputStream(new FileOutputStream(file));
			int read = -1;
			// TODO: Be careful with the socketTimeOut, Next while can brake the
			// program if it will spend inside more than the SocketTimeOut
			// A solution could be to create a thread with a similar code than
			// the waitingProcessWhile function of the Renderer class, that goes
			// sending messages by sockets to the LunaCore while the download
			// take place
			while ((read = reader.read()) != -1) {
				writer.write(read);
			}

			writer.flush();
			writer.close();
			reader.close();
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block --> casos de Fallo
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		ms.putResource(l.logging("File retrieved succesfully."));

		return (int) file.length();

	}

	/**
	 * Checks if the Video is zipped
	 * 
	 * @param l
	 * @param nameExt
	 * 
	 * @param messageObject
	 * @return If the video is zipped return true, false in other cases
	 */
	public boolean isVideoZipped(Message ms, Logger l, String[] nameExt) {
		// It works just with .zip files
		ms.putResource(l.logging("Checking if it is a Zip File..."));

		return (nameExt[nameExt.length - 1].equals("zip") || nameExt[nameExt.length - 1]
				.equals("ZIP"));
	}

	/**
	 * 
	 * @return The extension of the S3KeyOriginal
	 */
	public String[] nameExetension() {
		return getS3KeyOriginal().split("\\.");
	}

	public boolean isTs() {
		String[] nameExt = nameExetension();
		return (nameExt[nameExt.length - 1].equals("TS") || nameExt[nameExt.length - 1]
				.equals("ts"));
	}

	public String nakedFileName() {
		String[] nameExt = nameExetension();
		int extensionSize = nameExt[nameExt.length - 1].length();
		return getS3KeyOriginal().substring(0,
				getS3KeyOriginal().length() - 1 - extensionSize);
	}

	/**
	 * Upload the converted Video/Audio to S3
	 * 
	 * @param s3
	 * @param ms
	 * @param l
	 * @return 
	 */
	public File uploadVideo(AmazonS3 s3, Message ms, Logger l) {
		ms.putResource(l.logging("Trying to upload it to S3..."));
		// Upload to S3 (Check multipart-uploading to upload faster)
		File finishedFile = new File(getS3KeyFinished());
		PutObjectRequest por = new PutObjectRequest(getS3BucketFinished(),
				getS3KeyFinished(), finishedFile);
		por.setStorageClass(StorageClass.ReducedRedundancy);
		// por.setCannedAcl(CannedAccessControlList.PublicRead);
		s3.putObject(por);
		ms.putResource(l.logging("File uploaded to S3 successfully"));
		return finishedFile;
	}


	public String getUserId() {
		return userId;
	}

	public void setUserId(String userId) {
		this.userId = userId;
	}

	public String getClassName() {
		return className;
	}

	public void setClassName(String className) {
		this.className = className;
	}

	public String getClassDate() {
		return classDate;
	}

	public void setClassDate(String classDate) {
		this.classDate = classDate;
	}

	public String getFormat() {
		return format;
	}

	public void setFormat(String format) {
		this.format = format;
	}

	public String getS3BucketOriginal() {
		return S3BucketOriginal;
	}

	public void setS3BucketOriginal(String s3BucketOriginal) {
		S3BucketOriginal = s3BucketOriginal.toLowerCase();
		;
	}

	public String getS3BucketFinished() {
		return S3BucketFinished;
	}

	public void setS3BucketFinished(String s3BucketFinished) {
		S3BucketFinished = s3BucketFinished.toLowerCase();
	}

	public String getS3KeyOriginal() {
		return S3KeyOriginal;
	}

	public void setS3KeyOriginal(String s3KeyOriginal) {
		S3KeyOriginal = s3KeyOriginal;
	}

	public String getS3KeyFinished() {
		return S3KeyFinished;
	}

	public void setS3KeyFinished(String s3KeyFinished) {
		S3KeyFinished = s3KeyFinished;
	}

	public String getPart() {
		return part;
	}

	public void setPart(String part) {
		this.part = part;
	}

	public boolean isAutorender() {
		return autorender;
	}

}
