package org.astrogrid.samp.test;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.astrogrid.samp.Client;
import org.astrogrid.samp.ErrInfo;
import org.astrogrid.samp.Message;
import org.astrogrid.samp.Metadata;
import org.astrogrid.samp.Response;
import org.astrogrid.samp.SampUtils;
import org.astrogrid.samp.Subscriptions;
import org.astrogrid.samp.client.ClientProfile;
import org.astrogrid.samp.client.DefaultClientProfile;
import org.astrogrid.samp.client.HubConnection;
import org.astrogrid.samp.client.HubConnector;
import org.astrogrid.samp.client.MessageHandler;
import org.astrogrid.samp.httpd.UtilServer;

/**
 * Subscribes to SAMP messages and logs any received to an output stream.
 * The only responses to messages have samp.status=samp.warning.
 *
 * @author   Mark Taylor
 * @since    4 Sep 2008
 */
public class Snooper {

    private final OutputStream out_;
    private final Map clientMap_;
    private static final byte[] newline_;
    static {
        byte[] nl;
        try {
            nl = System.getProperty( "line.separator", "\n" )
                       .getBytes( "UTF-8" );
        }
        catch ( Exception e ) {
            nl = new byte[] { (byte) '\n' };
        }
        newline_ = nl;
    }
    
    private static final Logger logger_ =
        Logger.getLogger( Snooper.class.getName() );

    /**
     * Constructor using default metadata.
     *
     * @param   profile   profile
     * @param   subs    subscriptions defining which messages are received
     *                  and logged
     * @param   out     destination stream for logging info
     * @param   autoSec   number of seconds between auto connection attempts
     */
    public Snooper( ClientProfile profile, Subscriptions subs,
                    OutputStream out, int autoSec ) {
        this( profile, subs, createDefaultMetadata(), out, autoSec );
    }

    /**
     * Constructor using custom metadata.
     *
     * @param   profile   profile
     * @param   subs    subscriptions defining which messages are received
     *                  and logged
     * @param   meta    client metadata
     * @param   out     destination stream for logging info
     * @param   autoSec   number of seconds between auto connection attempts
     */
    public Snooper( ClientProfile profile, final Subscriptions subs,
                    Metadata meta, OutputStream out, int autoSec ) {
        HubConnector connector = new HubConnector( profile );
        connector.declareMetadata( meta );
        out_ = out;
        clientMap_ = connector.getClientMap();

        // Prepare all-purpose response to logged messages.
        final Response response = new Response();
        response.setStatus( Response.WARNING_STATUS );
        response.setResult( new HashMap() );
        response.setErrInfo( new ErrInfo( "Message logged, not acted on" ) );

        // Add a handler which will handle the subscribed messages.
        connector.addMessageHandler( new MessageHandler() {
            public Map getSubscriptions() {
                return subs;
            }
            public void receiveNotification( HubConnection connection,
                                             String senderId,
                                             Message msg )
                    throws IOException {
                log( senderId, msg, null );
            }
            public void receiveCall( HubConnection connection,
                                     String senderId,
                                     String msgId, Message msg )
                    throws IOException {
                log( senderId, msg, msgId );
                connection.reply( msgId, response );
            }
        } );
        connector.declareSubscriptions( connector.computeSubscriptions() );

        // Connect and ready to log.
        connector.setActive( true );
        connector.setAutoconnect( autoSec );
    }

    /**
     * Logs a received message.
     *
     * @param   senderId  message sender public ID
     * @param   msg   message object
     * @param   msgId  message ID for call/response type messages
     *                 (null for notify type messages)
     */
    private void log( String senderId, Message msg, String msgId )
            throws IOException {
        StringBuffer sbuf = new StringBuffer();
        sbuf.append( senderId );
        Client client = (Client) clientMap_.get( senderId );
        if ( client != null ) {
            Metadata meta = client.getMetadata();
            if ( meta != null ) {
                String name = meta.getName();
                if ( name != null ) {
                    sbuf.append( " (" )
                        .append( name )
                        .append( ")" );
                }
            }
        }
        sbuf.append( " --- " );
        if ( msgId == null ) {
            sbuf.append( "notify" );
        }
        else {
            sbuf.append( "call" )
                .append( " (" )
                .append( msgId )
                .append( ")" );
        }
        out_.write( newline_ );
        out_.write( sbuf.toString().getBytes( "UTF-8" ) );
        out_.write( newline_ );
        out_.write( SampUtils.formatObject( msg, 3 ).getBytes( "UTF-8" ) );
        out_.write( newline_ );
    }

    /**
     * Returns the default metadata for the Snooper client.
     *
     * @return  meta
     */
    public static Metadata createDefaultMetadata() {
        Metadata meta = new Metadata();
        meta.setName( "Snooper" );
        meta.setDescriptionText( "Listens in to messages"
                               + " for logging purposes" );
        try {
            meta.setIconUrl( UtilServer.getInstance()
                            .exportResource( "/org/astrogrid/samp/images/"
                                           + "ears.png" )
                            .toString() );
        }
        catch ( IOException e ) {
            logger_.warning( "Can't export icon" );
        }
        meta.put( "Author", "Mark Taylor" );
        return meta;
    }

    /**
     * Main method.  Runs a snooper.
     */
    public static void main( String[] args ) throws IOException {
        int status = runMain( args );
        if ( status != 0 ) {
            System.exit( status );
        }
    }

    /**
     * Does the work for the main method.
     * Use -help flag.
     */
    public static int runMain( String[] args ) throws IOException {
        String usage = new StringBuffer()
            .append( "\n   Usage:" )
            .append( "\n      " )
            .append( Snooper.class.getName() )
            .append( "\n         " )
            .append( " [-help]" )
            .append( " [-/+verbose]" )
            .append( "\n         " )
            .append( " [-clientname <appname>]" )
            .append( " [-clientmeta <metaname> <metavalue>]" )
            .append( "\n         " )
            .append( " [-mtype <pattern>]" )
            .append( "\n" )
            .toString();
        List argList = new ArrayList( Arrays.asList( args ) );
        int verbAdjust = 0;
        Subscriptions subs = new Subscriptions();
        Metadata meta = new Metadata();
        for ( Iterator it = argList.iterator(); it.hasNext(); ) {
            String arg = (String) it.next();
            if ( arg.startsWith( "-mtype" ) && it.hasNext() ) {
                it.remove();
                String mpat = (String) it.next();
                it.remove();
                subs.addMType( mpat );
            }
            else if ( arg.equals( "-clientname" ) && it.hasNext() ) {
                it.remove();
                meta.setName( (String) it.next() );
                it.remove();
            }
            else if ( arg.equals( "-clientmeta" ) && it.hasNext() ) {
                it.remove();
                String mName = (String) it.next();
                it.remove();
                String mValue;
                if ( it.hasNext() ) {
                    mValue = (String) it.next();
                    it.remove();
                }
                else {
                    System.err.println( usage );
                    return 1;
                }
                Object mVal = SampUtils.parseValue( mValue );
                if ( mVal == null ) {
                    meta.remove( mName );
                }
                else {
                    meta.put( mName, mVal );
                }
            }
            else if ( arg.startsWith( "-v" ) ) {
                it.remove();
                verbAdjust--;
            }
            else if ( arg.startsWith( "+v" ) ) {
                it.remove();
                verbAdjust++;
            }
            else if ( arg.startsWith( "-h" ) ) {
                it.remove();
                System.out.println( usage );
                return 0;
            }
            else {
                it.remove();
                System.err.println( usage );
                return 1;
            }
        }
        assert argList.isEmpty();

        // Combine custom and default metadata.
        Metadata m2 = createDefaultMetadata();
        m2.putAll( meta );
        meta = m2;

        // Set default subscriptions (everything) if none has been specified
        // explicitly.
        if ( subs.isEmpty() ) {
            subs.addMType( "*" );
        }

        // Adjust logging in accordance with verboseness flags.
        int logLevel = Level.WARNING.intValue() + 100 * verbAdjust;
        Logger.getLogger( "org.astrogrid.samp" )
              .setLevel( Level.parse( Integer.toString( logLevel ) ) );

        // Get profile.
        ClientProfile profile = DefaultClientProfile.getProfile();

        // Start and run snooper.
        new Snooper( profile, subs, meta, System.out, 2 );

        // Wait indefinitely.
        Object lock = new String( "Forever" );
        synchronized( lock ) {
            try {
                lock.wait();
            }
            catch ( InterruptedException e ) {
            }
        }
        return 0;
    }
}
