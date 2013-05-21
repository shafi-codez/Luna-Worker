import java.util.Date;
 import java.util.Properties;
 import javax.mail.Authenticator;
 import javax.mail.PasswordAuthentication;
 import javax.mail.Message.RecipientType;
 import javax.mail.MessagingException;
 import javax.mail.Session;
 import javax.mail.Transport;
 import javax.mail.internet.AddressException;
 import javax.mail.internet.InternetAddress;
 import javax.mail.internet.MimeMessage;
 import javax.mail.internet.MimeMultipart;
/**
 *
 * @author Jwill
 */
public class Jmailserver {

    /**
     * @param args the command line arguments
     */
    
    private MimeMessage msg;
    
    public Jmailserver(String fromDomin, String userName, String password) {
         Properties p = new Properties();
         p.setProperty("mail.host", "smtp." + fromDomin);
         p.setProperty("mail.smtp.auth", "true");
	 p.setProperty("mail.smtp.starttls.enable", "true");
         Session ses = Session.getDefaultInstance(p, new MyAuthenticator(userName, password));
         msg = new MimeMessage(ses);
         try {
             msg.setFrom(new InternetAddress(userName + "@" + fromDomin));
         } catch (AddressException e) {
             e.printStackTrace();
         } catch (MessagingException e) {
             e.printStackTrace();
         }
     }
    
    
     public boolean sendMessage(String toAddress, String title, String content, String type) {
 
         try {
             msg.setRecipient(RecipientType.TO, new InternetAddress(toAddress));
             msg.setSentDate(new Date());
             msg.setSubject(title);
             msg.setContent(content, type);
             Transport.send(msg);
             System.out.println("Succeed");
             return true;
         } catch (MessagingException ex) {
             ex.printStackTrace();
             return false;
         }
         
     }
    
    
    class MyAuthenticator extends Authenticator {
     
     private String _userName;
     private String _password;
     
     public MyAuthenticator(String userName,String password){
         this._userName = userName;
         this._password = password;
     }
     
     @Override
     public PasswordAuthentication getPasswordAuthentication() {
         return new PasswordAuthentication(_userName, _password);
     }
 }
}
