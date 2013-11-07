package fi.nicco.aurora;

import java.io.File;
import java.util.Properties;

import javax.activation.DataHandler;
import javax.activation.DataSource;
import javax.activation.FileDataSource;
import javax.mail.BodyPart;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;

import org.slf4j.Logger;

public class AuroraSendMail {

    private String sendFrom;
    private String sendTo;
    private File attatchment;
    private String alertSubject;
    private String alertMessage;

    public Message createAuroraNotificationMail( Logger logger, Properties props ) {

        final Properties properties = props;
        Message message = null;

        if ( properties.get( "mail.smtp.host" ) == null ) {
            logger.error( "Mail host not set, can not create message" );
            return null;
        }

        if ( properties.get( "mail.smtp.port" ) == null ) {
            logger.error( "Mail port not configured in properties file, can not create message" );
            return null;
        }

        if ( properties.get( "username" ) == null ) {
            logger.error( "Can not send message, mail username not set" );
            return null;
        }

        if ( getPassword() == null ) {
            logger.error( "Can not send message, mail password not set" );
            return null;
        }

        Session session = Session.getInstance( properties, new javax.mail.Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication( getUsername(), getPassword() );
            }
        } );

        try {
            if ( getAlertMessage() != null && getAlertSubject() != null ) {

                logger.info( "Creating e-mail." );

                message = new MimeMessage( session );
                message.setFrom( new InternetAddress( properties.getProperty( "mailfrom" ) ) );
                message.setRecipients( Message.RecipientType.TO, InternetAddress.parse( properties.getProperty( "mailto" ) ) );
                message.setSubject( getAlertSubject() );

                if ( getAttatchment() == null ) {

                    logger.info( "No attatchment in message" );
                    message.setText( getAlertMessage() );

                }

                else {

                    logger.info( "Adding attatchment (image) to message" );
                    BodyPart bodyPart = new MimeBodyPart();
                    bodyPart.setText( getAlertMessage() );
                    Multipart multipart = new MimeMultipart();
                    multipart.addBodyPart( bodyPart );

                    DataSource source = new FileDataSource( getAttatchment() );
                    MimeBodyPart attatchmentPart = new MimeBodyPart();
                    attatchmentPart.setDataHandler( new DataHandler( source ) );
                    attatchmentPart.setFileName( "image.png" );
                    multipart.addBodyPart( attatchmentPart );

                    message.setContent( multipart );

                }
                logger.info( "Mail message created successfully!" );
                return message;

            }
            else {
                logger.error( "Could not create messge with subject " + getAlertSubject() + " and text " + getAlertMessage() );
            }

        }
        catch ( MessagingException e ) {
            logger.error( "Faied to create message, reason ", e );
            throw new RuntimeException( e );
        }

        return message;

    }

    public void sendMessage( Message message, Logger logger ) {
        if ( message == null ) {

            logger.error( "Could not send message due to previous errors" );
            return;

        }

        try {

            Transport.send( message );
            logger.info( "Message sent" );

        }

        catch ( MessagingException e ) {

            logger.error( "Failed to send alert email reason", e );

        }

    }

    private String password;
    private String username;

    public String getPassword() {
        return password;
    }

    public void setPassword( String password ) {
        this.password = password;
    }

    public String getAlertSubject() {
        return alertSubject;
    }

    public String getSendFrom() {
        return sendFrom;
    }

    public void setSendFrom( String sendFrom ) {
        this.sendFrom = sendFrom;
    }

    public String getSendTo() {
        return sendTo;
    }

    public void setSendTo( String sendTo ) {
        this.sendTo = sendTo;
    }

    public void setAlertSubject( String alertSubject ) {
        this.alertSubject = alertSubject;
    }

    public String getAlertMessage() {
        return alertMessage;
    }

    public void setAlertMessage( String alertMessage ) {
        this.alertMessage = alertMessage;
    }

    public File getAttatchment() {
        return attatchment;
    }

    public void setAttatchment( File attatchment ) {
        this.attatchment = attatchment;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername( String username ) {
        this.username = username;
    }

}
