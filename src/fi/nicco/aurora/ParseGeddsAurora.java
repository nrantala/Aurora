package fi.nicco.aurora;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.Properties;

import javax.mail.Message;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ParseGeddsAurora {

    private final String GEDDS_URL = "http://www.gi.alaska.edu";
    private static final Logger logger = LoggerFactory.getLogger( "Aurora Parser" );

    public String getAuroraData(Properties properties , Logger logger ) {

        String data = null;

        try {

            StringBuilder urlBuilder = new StringBuilder();
            urlBuilder.append( properties.get( "aurorabaseurl" ) );
            urlBuilder.append( properties.get( "location" ) );
            urlBuilder.append( "/" );

            String urlString = urlBuilder.toString();

            URL google = new URL( urlString );
            String begin = "<div class=\"main\">";
            String end = "<!-- /main -->";

            URLConnection yc = google.openConnection();
            BufferedReader in = new BufferedReader( new InputStreamReader( yc.getInputStream() ) );
            String inputLine;
            StringBuilder sb = new StringBuilder();
            while ( ( inputLine = in.readLine() ) != null ) {
                sb.append( inputLine + "\n" );
            }
            in.close();
            int indexBegin = sb.toString().indexOf( begin );
            int indexEnd = sb.toString().indexOf( end );
            data = sb.toString().substring( indexBegin, indexEnd );
            // logger.info( "Got data:\n" + data );
        }

        catch ( Exception e ) {

            logger.error( "Failed to parse data for Europe" );
            e.printStackTrace();

        }

        return data;

    }

    public String getString( String data, Logger logger, String lookForBegin, String lookforEnd ) {

        try {

            int beginIndex = data.indexOf( lookForBegin );
            String after = data.substring( beginIndex );
            int endIndex = after.indexOf( lookforEnd );
            return data.substring( ( beginIndex + lookForBegin.length() ), ( beginIndex + endIndex + lookforEnd.length() ) );

        }

        catch ( Exception e ) {

            logger.error( "Substring not found" );

        }
        return null;
    }

    private String getImageUrl( String data, Logger logger ) {

        StringBuilder sb = new StringBuilder();

        try {

            sb.append( GEDDS_URL );
            String restOfUrl = getString( data, logger, "img src=\"", ".png" );
            if ( restOfUrl != null ) {

                sb.append( restOfUrl );

            }

            else {

                logger.error( "Could nog retrieve complete url" );
                return null;
            }

        }

        catch ( Exception e ) {

            logger.error( "Failed to parse image url reason ", e );

        }

        return sb.toString();

    }

    private String getForecastText( String data, Logger logger ) {

        try {

            String lookFrom = "<p><em>Forecast:</em> ";
            String lookTo = "</p>";
            String text = getString( data, logger, lookFrom, lookTo );

            if ( text != null ) {

                return text.substring( 0, text.length() - lookTo.length() );

            }

        }

        catch ( Exception e ) {

            logger.error( "Failed to get forecast text reason", e );

        }

        logger.error( "Could not retrieve forecast text" );

        return null;

    }

    private int getLevel( String data, Logger logger ) {

        String begin = "level-";
        int index = data.indexOf( begin );
        int level = 0;

        try {

            String s = data.charAt( index + begin.length() ) + "";
            level = Integer.parseInt( s );

        }

        catch ( Exception e ) {

            logger.error( "could not parse level, reason", e );

        }

        return level;

    }

    private byte [] downloadImage( String imageUrl ) {

        URL url;
        InputStream in = null;
        ByteArrayOutputStream out = null;

        try {

            url = new URL( imageUrl );
            in = new BufferedInputStream( url.openStream() );
            out = new ByteArrayOutputStream();
            byte [] buf = new byte [ 1024 ];
            int n = 0;
            while ( -1 != ( n = in.read( buf ) ) ) {
                out.write( buf, 0, n );
            }

            out.close();
            in.close();
            return out.toByteArray();

        }

        catch ( MalformedURLException e ) {
            logger.error( "Failed to download image from bad url " + imageUrl, e );
        }

        catch ( IOException e ) {
            logger.error( "Caught IOException trying to download image from " + imageUrl, e );
        }

        finally {
            IOUtils.closeQuietly( out );
            IOUtils.closeQuietly( in );
        }
        return null;
    }

    public void runProcess( Logger logger, Properties properties, AuroraSendMail auroraSendMail ) {

        String data = getAuroraData( properties, logger );

        DateTime lastRun = DateTime.now();
        DateTime mailLastSent = DateTime.now();

        boolean firstRun = true;

        while ( true ) {

            String currentData = getAuroraData( properties, logger );
            int forecastLevel = getLevel( currentData, logger );

            boolean sendAllways = false;

            try {

                sendAllways = Boolean.parseBoolean( properties.getProperty( "sendallways" ) );

            }

            catch(Exception e) {

                logger.error( "Failed to parse check allways variable" );

            }

            if ( ( currentData.equals( data ) && !firstRun ) || sendAllways ) {

                try {

                    int timeout = 3600000;
                    logger.info( "Data unchanged since last poll, waiting for " + properties.getProperty( "datafetchintervalms", ( timeout + "" ) )
                            + " milliseconds before next poll" );
                    try {

                        timeout = Integer.parseInt( properties.getProperty( "datafetchintervalms" ) );

                    }
                    catch ( NumberFormatException e ) {

                        logger.warn( "Using default timeout" );

                    }
                    Thread.sleep( timeout );
                }
                catch ( InterruptedException e ) {

                    logger.error( "Thread interrupted ", e );

                }
            }

            else {

                if ( firstRun ) {
                    logger.info( "First run of data fetch" );

                }

                try {

                    data = currentData;
                    int minLevel = 3;

                    try {

                        Integer.parseInt( properties.getProperty( "minimumlevel" ) );

                    }

                    catch ( NumberFormatException e ) {

                        logger.warn( "using default minimum level = " + minLevel );

                    }

                    if ( forecastLevel >= minLevel ) {

                        logger.info( "Forecast level " + forecastLevel + " is high enough to create mail" );
                        String forecastText = getForecastText( currentData, logger );
                        String forecastImageUrl = getImageUrl( currentData, logger );

                        auroraSendMail.setAlertSubject( "Aurora alert Level: " + forecastLevel );

                        StringBuilder bodyBuilder = new StringBuilder();
                        bodyBuilder.append( "Level: " );
                        bodyBuilder.append( forecastLevel );
                        bodyBuilder.append( "\n" );

                        bodyBuilder.append( forecastText );
                        bodyBuilder.append( "\n" );

                        bodyBuilder.append( "Image URL: " );
                        bodyBuilder.append( forecastImageUrl );

                        auroraSendMail.setAlertMessage( bodyBuilder.toString() );

                        byte [] image = null;

                        if ( forecastImageUrl != null ) {

                            try {

                                image = downloadImage( forecastImageUrl );

                                File file = new File( "image.png" );
                                FileUtils.writeByteArrayToFile( file, image );
                                auroraSendMail.setAttatchment( file );

                            }

                            catch ( Exception e ) {

                                logger.error( "Failed to add image as attatchment", e );

                            }
                        }

                        else {

                            logger.error( "Could not get image" );

                        }


                        double seconds = ( lastRun.getMillis() - mailLastSent.getMillis() ) / 1000;

                        int dontsenduntil = 86400;

                        try {

                            dontsenduntil = Integer.parseInt( properties.getProperty( "dontsenduntilseconds" ) );

                        }

                        catch ( NumberFormatException e ) {

                            logger.error( "Could not parse dontsenduntilseconds variable, waiting for 1 day until next send" );

                        }

                        
                        if ( ( seconds > dontsenduntil ) || firstRun ) {
                            firstRun = false;
                            Message message = auroraSendMail.createAuroraNotificationMail( logger, properties );
                            boolean dontsendmail = true;

                            try {

                                dontsendmail = Boolean.parseBoolean( properties.getProperty( "dontsendmail" ) );

                            }
                            catch (Exception e) {

                                logger.error( "Failed to parse parameter dontsendmail" );

                            }

                            StringBuilder sb = new StringBuilder();


                            if ( dontsendmail ) {

                                sb.append( "############################### FAKE SEND ###############################\n" );

                            }
                            else {

                                sb.append( "############################# SENDING MAIL ##############################\n" );

                                auroraSendMail.sendMessage( message, logger );

                            }

                            sb.append( "Subject: " );
                            sb.append( message.getSubject() );
                            sb.append( "\nMessage: " );
                            sb.append( auroraSendMail.getAlertMessage() );

                            if ( auroraSendMail.getAttatchment().length() > 0 ) {

                                sb.append( "\nAttatchment: YES" );

                            }
                            else {

                                sb.append( "\nAttatchment: NO" );

                            }
                            sb.append( "\n#########################################################################\n" );

                            logger.info( sb.toString() );
                            mailLastSent = DateTime.now();

                        }

                        lastRun = DateTime.now();

                    }
                    else {
                        logger.info( "Waiting...." );
                    }
                }

                catch ( Exception e ) {

                    logger.error( "Something went wrong while running reason", e );

                }
            }
        }
    }
}
