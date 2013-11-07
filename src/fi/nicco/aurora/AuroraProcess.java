package fi.nicco.aurora;

import java.io.File;
import java.io.FileInputStream;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AuroraProcess {

    public static void main( String [] args ) {
        final Logger logger = LoggerFactory.getLogger( "Aurora" );
        String filename = null;

        if ( args.length > 0 ) {
            System.out.println( "Using: " + args[ 0 ] );
            filename = args[ 0 ];
            logger.info( "Using properties file: " + filename );
        }
        else {
            filename = "aurora.properties";
            logger.info( "using default properties file" );
        }

        String password = null;
        ParseGeddsAurora aurora = new ParseGeddsAurora();

        Properties props = loadProperties( filename, logger );
        int tries = 4;
        while ( tries > 0 ) {
            try {
                System.out.print( "Enter password for " + props.getProperty( "mail.smtp.host" ) + ": " );
                char [] psswd = System.console().readPassword();
                password = new String( psswd );
                tries = -1;
            }
            catch ( Exception e ) {
                System.out.println( "Incorrect password" );
            }
            tries--;
        }
        if ( password == null ) {
            logger.warn( "Trying to use insecure password from properties file, " + filename );
            password = props.getProperty( "password" );
        }

        AuroraSendMail auroraSendMail = new AuroraSendMail();
        auroraSendMail.setPassword( password );
        auroraSendMail.setUsername( props.getProperty( "username" ) );

        aurora.runProcess( logger, props, auroraSendMail );

    }

    public static Properties loadProperties( String fileLocation, Logger logger ) {
        try {
            File propsFile = new File( fileLocation );
            Properties props = new Properties();
            FileInputStream fis = new FileInputStream( propsFile );
            props.load( fis );
            fis.close();
            return props;
        }
        catch ( Exception e ) {
            logger.error( "Failed to load properties file", e );
        }
        return null;
    }

}
