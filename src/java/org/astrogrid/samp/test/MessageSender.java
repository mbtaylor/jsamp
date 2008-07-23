package org.astrogrid.samp.test;

import java.io.IOException;
import java.io.PrintStream;
import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.astrogrid.samp.Client;
import org.astrogrid.samp.Message;
import org.astrogrid.samp.Metadata;
import org.astrogrid.samp.Response;
import org.astrogrid.samp.SampException;
import org.astrogrid.samp.SampUtils;
import org.astrogrid.samp.Subscriptions;
import org.astrogrid.samp.client.CallableClient;
import org.astrogrid.samp.client.ClientProfile;
import org.astrogrid.samp.client.HubConnection;
import org.astrogrid.samp.client.StandardClientProfile;

/**
 * Sends a message to one or more other SAMP clients.
 * Intended for use from the command line.
 *
 * @author   Mark Taylor
 * @since    23 Jul 2008
 */
public class MessageSender {

    private final HubConnection connection_;
    private final Sender sender_;

    /**
     * Constructor.
     *
     * @param  profile  hub connection factory
     * @param  meta  metadata for registration of the sending client;
     *         may be null
     * @param  sender  sender object
     */
    MessageSender( ClientProfile profile, Map meta, Sender sender )
            throws SampException {
        connection_ = profile.register();
        connection_.declareMetadata( meta );
        sender_ = sender;
    }

    /**
     * Sends a message to a given list of recipients.
     * If <code>recipientIds</code> is null, then will be sent to all
     * subscribed clients.
     *
     * @param  msg  message to send
     * @param  recipientIds  array of recipients to target, or null
     * @return  responder Client -> Response map
     */
    Map sendMessages( Message msg, String[] recipientIds )
            throws IOException {
        return sender_.getResponses( connection_, msg, recipientIds );
    }

    /**
     * Sends a message to a list of recipients and displays the results
     * on an output stream.
     *
     * @param  msg  message to send
     * @param  recipientIds  array of recipients to target, or null
     * @param  destination print stream
     */
    void showResults( Message msg, String[] recipientIds, PrintStream out )
            throws IOException {
        Map responses = sendMessages( msg, recipientIds );
        for ( Iterator it = responses.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry entry = (Map.Entry) it.next();
            Client responder = (Client) entry.getKey();
            Response response = (Response) entry.getValue();
            out.println();
            out.println( responder );
            out.println( response );
        }
    }

    /**
     * Main method.
     * Use -help flag for documentation.
     */
    public static void main( String[] args ) throws IOException {
        int status = runMain( args );
        if ( status != 0 ) {
            System.exit( status );
        }
    }

    /**
     * Does the work for the main method.
     */
    public static int runMain( String[] args ) throws IOException {

        // Assemble usage string.
        String usage = new StringBuffer()
            .append( "\n   Usage:" )
            .append( "\n      " + MessageSender.class.getName() )
            .append( "\n        " )
            .append( " -mtype <mtype>" )
            .append( " [-param <name> <value> ...]" )
            .append( "\n        " )
            .append( " [-target <receiverId> ...]" )
            .append( " [-mode sync|async|notify]" )
            .append( "\n        " )
            .append( " [-appname <appname>]" )
            .append( " [-appmeta <metaname> <metavalue>]" )
            .append( "\n" )
            .toString();

        // Set up variables which can be set or changed by the argument list.
        String mtype = null;
        List targetList = new ArrayList();
        Map paramMap = new HashMap();
        String mode = "sync";
        Metadata meta = new Metadata();
        int timeout = 0;
        int verbAdjust = 0;

        // Parse the argument list.
        List argList = new ArrayList( Arrays.asList( args ) );
        for ( Iterator it = argList.iterator(); it.hasNext(); ) {
            String arg = (String) it.next();
            if ( arg.equals( "-mtype" ) && it.hasNext() ) {
                it.remove();
                if ( mtype != null ) {
                    System.err.println( usage );
                    return 1;
                }
                mtype = (String) it.next();
                it.remove();
            }
            else if ( arg.equals( "-target" ) && it.hasNext() ) {
                it.remove();
                targetList.add( (String) it.next() );
                it.remove();
            }
            else if ( arg.equals( "-param" ) && it.hasNext() ) {
                it.remove();
                String pName = (String) it.next();
                it.remove();
                String pValue;
                if ( it.hasNext() ) {
                    pValue = (String) it.next();
                    it.remove();
                }
                else {
                    System.err.println( usage );
                    return 1;
                }
                paramMap.put( pName, parseValue( pValue ) );
            }
            else if ( arg.equals( "-mode" ) && it.hasNext() ) {
                it.remove();
                mode = (String) it.next();
                it.remove();
            }
            else if ( arg.equals( "-appname" ) && it.hasNext() ) {
                it.remove();
                meta.setName( (String) it.next() );
                it.remove();
            }
            else if ( arg.equals( "-appmeta" ) && it.hasNext() ) {
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
                meta.put( mName, parseValue( mValue ) );
            }
            else if ( arg.equals( "-timeout" ) && it.hasNext() ) {
                it.remove();
                String stimeout = (String) it.next();
                it.remove();
                try {
                    timeout = Integer.parseInt( stimeout );
                }
                catch ( NumberFormatException e ) {
                    System.err.println( "Not numeric: " + stimeout );
                    System.err.println( usage );
                    return 1;
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
        if ( ! argList.isEmpty() ) {
            System.err.println( usage );
            return 1;
        }
        if ( mtype == null ) {
            System.err.println( usage );
            return 1;
        }

        // Set logging levels in accordance with flags.
        int logLevel = Level.WARNING.intValue() + 100 * verbAdjust;
        Logger.getLogger( "org.astrogrid.samp" )
              .setLevel( Level.parse( Integer.toString( logLevel ) ) );

        // Prepare to send the message.
        Message msg = new Message( mtype, paramMap );
        String[] targets = targetList.isEmpty()
                         ? null
                         : (String[]) targetList.toArray( new String[ 0 ] );
        final Sender sender;
        if ( mode.toLowerCase().startsWith( "async" ) ) {
            sender = new AsynchSender();
        }
        else if ( mode.toLowerCase().startsWith( "sync" ) ) {
            sender = new SynchSender( timeout );
        }
        else if ( mode.toLowerCase().startsWith( "notif" ) ) {
            sender = new NotifySender();
        }
        else {
            System.err.println( usage );
	    return 1;
        }
        MessageSender mSender =
            new MessageSender( StandardClientProfile.getInstance(),
                               meta, sender );

        // Send the message, displaying the results on System.out.
        mSender.showResults( msg, targets, System.out );
        return 0;
    }

    /**
     * Parses a command-line string as a SAMP object.
     * Currently, this just returns the same string, but it ought to have
     * some method of decoding (nested) list and maps as well (JSON?).
     *
     * @param   str   command-line argument
     * @return  SAMP object
     */
    private static Object parseValue( String str ) {
        Object sampObj = str;
        SampUtils.checkObject( sampObj );
        return sampObj;
    }

    private static abstract class Sender {
        abstract Map getResponses( HubConnection connection, Message msg,
                                   String[] recipientIds )
            throws IOException;
    }

    private static class NotifySender extends Sender {
        public Map getResponses( HubConnection connection, Message msg,
                                 String[] recipientIds )
                throws IOException {
            if ( recipientIds == null ) {
                connection.notifyAll( msg );
            }
            else {
                for ( int ir = 0; ir < recipientIds.length; ir++ ) {
                    connection.notify( recipientIds[ ir ], msg );
                }
            }
            return new HashMap();
        }
    }

    private static class SynchSender extends Sender {
        private final int timeout_;

        SynchSender( int timeout ) {
            timeout_ = timeout;
        }

        public Map getResponses( final HubConnection connection,
                                 final Message msg,
                                 String[] recIds )
                throws IOException {
            final String[] recipientIds = recIds == null
                ? (String[]) connection.getSubscribedClients( msg.getMType() )
                                       .keySet().toArray( new String[ 0 ] )
                : recIds;
            final BlockingMap map = new BlockingMap();
            for ( int ir = 0; ir < recipientIds.length; ir++ ) {
                final String id = recipientIds[ ir ];
                final Client client = new MetaClient( id, connection );
                new Thread() {
                    public void run() {
                        Object result;
                        try {
                            result = 
                                connection.callAndWait( id, msg, timeout_ );
                        }
                        catch ( Throwable e ) {
                            result = e;
                        }
                        map.putResult( client, result );
                        if ( map.size() >= recipientIds.length ) {
                            map.done();
                        }
                    }
                }.start();
            }
            return map;
        }
    }

    private static class AsynchSender extends Sender
                                      implements CallableClient {

        private int iseq_;
        private BlockingMap map_;
        private int nExpected_;
        private HubConnection conn_;

        public Map getResponses( HubConnection connection, Message msg,
                                 String[] recipientIds ) 
                throws IOException {
            conn_ = connection;
            connection.setCallable( this );
            String msgTag = "tag-" + ++iseq_;
            map_ = new BlockingMap();
            nExpected_ = recipientIds == null
                ? connection.getSubscribedClients( msg.getMType() ).size()
                : recipientIds.length;
            if ( recipientIds == null ) {
                connection.callAll( msgTag, msg );
            }
            else {
                for ( int i = 0; i < recipientIds.length; i++ ) {
                    connection.call( recipientIds[ i ], msgTag, msg );
                }
            }
            return map_;
        }

        public void receiveCall( String senderId, String msgId, Message msg ) {
            throw new UnsupportedOperationException();
        }

        public void receiveNotification( String senderId, Message msg ) {
            throw new UnsupportedOperationException();
        }

        public void receiveResponse( String responderId, String msgTag,
                                     Response response ) throws SampException {
            Client client = new MetaClient( responderId, conn_ );
            map_.putResult( client, response );
            if ( map_.size() >= nExpected_ ) {
                map_.done();
            }
        }
    }

    /**
     * Client implementation which may know about metadata.
     */
    private static class MetaClient implements Client {
        private final String id_;
        private final Metadata meta_;

        /**
         * Constructor which attempts to acquire metadata from a given
         * hub connection.
         *
         * @param  client id
         * @param  connection  hub connection
         */
        public MetaClient( String id, HubConnection connection )
                throws SampException {
            this( id, connection.getMetadata( id ) );
        }

        /**
         * Constructor which uses supplied metadata.
         *
         * @param  id  client id
         * @param  meta  metadata (may be null)
         */
        public MetaClient( String id, Metadata meta ) {
            id_ = id;
            meta_ = meta;
        }

        public String getId() {
            return id_;
        }

        public Metadata getMetadata() {
            return meta_;
        }

        public Subscriptions getSubscriptions() {
            return null;
        }

        public String toString() {
            StringBuffer sbuf = new StringBuffer();
            sbuf.append( getId() );
            String name = meta_ == null ? null
                                        : meta_.getName();
            if ( name != null ) {
                sbuf.append( " (" )
                    .append( name )
                    .append( ')' );
            }
            return sbuf.toString();
        }
    }

    /**
     * Map implementation which dispenses its contents via an iterator
     * which will block until all the results are in.  This makes it
     * suitable for use from other threads.
     */
    private static class BlockingMap extends AbstractMap {
        private final BlockingSet entrySet_;

        /**
         * Constructor.
         */
        BlockingMap() {
            entrySet_ = new BlockingSet();
        }

        public Set entrySet() {
            return entrySet_;
        }

        /**
         * Adds an entry to this map.
         *
         * @param  client  key
         * @param  result  value
         */
        synchronized void putResult( final Client client,
                                     final Object result ) {
            entrySet_.add( new Map.Entry() {
                public Object getKey() {
                    return client;
                }
                public Object getValue() {
                    return result;
                }
                public Object setValue( Object value ) {
                    throw new UnsupportedOperationException();
                }
            } );
        }

        /**
         * Indicates that no more entries will be added to this map.
         * Must be called by populator or entry set iterator will block
         * indefinitely.
         */
        synchronized void done() {
            entrySet_.done();
        }
    }

    /**
     * Set implementation which dispenses its contents via an iterator
     * which will block until all results are in.
     */
    private static class BlockingSet extends AbstractSet {
        private final List list_;
        private boolean done_;

        /**
         * Constructor.
         */
        BlockingSet() {
            list_ = Collections.synchronizedList( new ArrayList() );
        }

        public boolean add( Object o ) {
            assert ! list_.contains( o );
            synchronized ( list_ ) {
                list_.add( o );
                list_.notifyAll();
            }
            return true;
        }

        /**
         * Indicates that no more items will be added to this set.
         * Must be called by populator or iterator will block
         * indefinitely.
         */
        public void done() {
            done_ = true;
            synchronized ( list_ ) {
                list_.notifyAll();
            }
        }

        public int size() {
            return list_.size();
        }

        public Iterator iterator() {
            return new Iterator() {
                int index_;

                public void remove() {
                    throw new UnsupportedOperationException();
                }

                public Object next() {
                    return list_.get( index_++ );
                }

                public boolean hasNext() {
                    synchronized ( list_ ) {
                        while ( index_ >= list_.size() && ! done_ ) {
                            try {
                                list_.wait();
                            }
                            catch ( InterruptedException e ) {
                                throw new RuntimeException( "Interrupted", e );
                            }
                        }
                        return index_ < list_.size();
                    }
                }
            };
        }
    }
}
