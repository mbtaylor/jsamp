package org.astrogrid.samp.test;

import java.io.IOException;
import java.io.PrintStream;
import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
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
import org.astrogrid.samp.SampUtils;
import org.astrogrid.samp.Subscriptions;
import org.astrogrid.samp.client.CallableClient;
import org.astrogrid.samp.client.ClientProfile;
import org.astrogrid.samp.client.HubConnection;
import org.astrogrid.samp.client.SampException;
import org.astrogrid.samp.xmlrpc.StandardClientProfile;
import org.astrogrid.samp.xmlrpc.XmlRpcKit;

/**
 * Sends a message to one or more other SAMP clients.
 * Intended for use from the command line.
 *
 * @author   Mark Taylor
 * @since    23 Jul 2008
 */
public abstract class MessageSender {

    private static Logger logger_ =
        Logger.getLogger( MessageSender.class.getName() );

    /**
     * Sends a message to a given list of recipients.
     * If <code>recipientIds</code> is null, then will be sent to all
     * subscribed clients.
     *
     * @param  connection  hub connection
     * @param  msg  message to send
     * @param  recipientIds  array of recipients to target, or null
     * @return  responder Client -> Response map
     */
    abstract Map getResponses( HubConnection connection, Message msg,
                               String[] recipientIds )
            throws IOException;

    /**
     * Sends a message to a list of recipients and displays the results
     * on an output stream.
     *
     * @param  connection  hub connection
     * @param  msg  message to send
     * @param  recipientIds  array of recipients to target, or null
     * @param  destination print stream
     */
    void showResults( HubConnection connection, Message msg,
                      String[] recipientIds, PrintStream out )
            throws IOException {
        Map responses = getResponses( connection, msg, recipientIds );
        for ( Iterator it = responses.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry entry = (Map.Entry) it.next();
            String responderId = (String) entry.getKey();
            Client responder = new MetaClient( responderId, connection );
            Object response = entry.getValue();
            out.println();
            out.println( responder );
            if ( response instanceof Throwable ) {
                ((Throwable) response).printStackTrace( out );
            }
            else {
                out.println( SampUtils.formatObject( response, 3 ) );
            }
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
            .append( "\n           " )
            .append( " [-help]" )
            .append( " [-/+verbose]" )
            .append( "\n           " )
            .append( " -mtype <mtype>" )
            .append( " [-param <name> <value> ...]" )
            .append( "\n           " )
            .append( " [-target <receiverId> ...]" )
            .append( " [-mode sync|async|notify]" )
            .append( "\n           " )
            .append( " [-sendername <appname>]" )
            .append( " [-sendermeta <metaname> <metavalue>]" )
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
        XmlRpcKit xmlrpc = null;

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
                paramMap.put( pName, SampUtils.parseValue( pValue ) );
            }
            else if ( arg.equals( "-mode" ) && it.hasNext() ) {
                it.remove();
                mode = (String) it.next();
                it.remove();
            }
            else if ( arg.equals( "-sendername" ) && it.hasNext() ) {
                it.remove();
                meta.setName( (String) it.next() );
                it.remove();
            }
            else if ( arg.equals( "-sendermeta" ) && it.hasNext() ) {
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
                meta.put( mName, SampUtils.parseValue( mValue ) );
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

        // Get profile.
        ClientProfile profile =
            xmlrpc == null ? StandardClientProfile.getInstance()
                           : new StandardClientProfile( xmlrpc );

        // Create a message sender object.
        final MessageSender sender;
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

        // Prepare to send the message.
        Message msg = new Message( mtype, paramMap );
        String[] targets = targetList.isEmpty()
                         ? null
                         : (String[]) targetList.toArray( new String[ 0 ] );

        // Register.
        HubConnection connection = profile.register();
        if ( connection == null ) {
            System.err.println( "No hub is running" );
            return 1;
        }
        connection.declareMetadata( meta );

        // Send the message, displaying the results on System.out.
        sender.showResults( connection, msg, targets, System.out );

        // Tidy up and exit.
        connection.unregister();
        return 0;
    }

    /**
     * MessageSender implementation which uses the Notify pattern.
     */
    private static class NotifySender extends MessageSender {
        public Map getResponses( HubConnection connection, Message msg,
                                 String[] recipientIds )
                throws IOException {
            final List recipientList;
            if ( recipientIds == null ) {
                recipientList = connection.notifyAll( msg );
                if ( recipientList.size() == 0 ) {
                    logger_.warning( "No clients subscribed to "
                                   + msg.getMType() );
                }
            }
            else {
                for ( int ir = 0; ir < recipientIds.length; ir++ ) {
                    connection.notify( recipientIds[ ir ], msg );
                }
                recipientList = Arrays.asList( recipientIds );
            }
            Map responseMap = new HashMap();
            for ( Iterator it = recipientList.iterator(); it.hasNext(); ) {
                responseMap.put( it.next(), "<no response from notify>" );
            }
            return responseMap;
        }
    }

    /**
     * MessageSender implementation which uses the Synchronous Call/Response
     * pattern.
     */
    private static class SynchSender extends MessageSender {
        private final int timeout_;

        /**
         * Constructor.
         *
         * @param  timeout in seconds
         */
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
            if ( recipientIds.length == 0 ) {
                logger_.warning( "No clients subscribed to " + msg.getMType() );
                return new HashMap();
            }
            else {
                int nsend = recipientIds.length;
                logger_.log( nsend == 0 ? Level.WARNING : Level.INFO,
                             "Waiting for " + nsend + " responses" );
            }
            final BlockingMap map = new BlockingMap();
            for ( int ir = 0; ir < recipientIds.length; ir++ ) {
                final String id = recipientIds[ ir ];
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
                        map.put( id, result );
                        if ( map.size() >= recipientIds.length ) {
                            map.done();
                        }
                    }
                }.start();
            }
            return map;
        }
    }

    /**
     * MessageSender implementation which uses the Asynchronous Call/Response
     * pattern.
     */
    private static class AsynchSender extends MessageSender {
        private int iseq_;

        public Map getResponses( HubConnection connection, Message msg,
                                 String[] recipientIds ) 
                throws IOException {
            String msgTag = "tag-" + ++iseq_;
            Collector collector = new Collector();

            // Sets the connection's callable client to a new object.
            // Since the Standard Profile doesn't say it's OK to do this 
            // more than once, this means that this it is not really safe
            // to call getResponses more than once for this object.
            connection.setCallable( collector );
            if ( recipientIds == null ) {
                Set recipientSet = connection.callAll( msgTag, msg ).keySet();
                collector.setRecipients( recipientSet );
            }
            else {
                Set recipientSet = new HashSet( Arrays.asList( recipientIds ) );
                for ( Iterator it = recipientSet.iterator(); it.hasNext(); ) {
                    String recipientId = (String) it.next();
                    connection.call( recipientId, msgTag, msg );
                }
                collector.setRecipients( recipientSet );
            }
            return collector.map_;
        }

        /**
         * CallableClient implementation which collects asynchronous message
         * responses.
         */
        private static class Collector implements CallableClient {
            final BlockingMap map_;
            Collection recipients_;

            /**
             * Constructor.
             *
             * @param  nExpected  number of responses expected by this collector
             */
            Collector() {
                map_ = new BlockingMap();
            }

            /**
             * Notifies this object which clients it should expect a response
             * from.  Must be called at some point, or the returned map's
             * iterator will block indefinitely.
             *
             * @param  recipients  set of client ids for expected responders
             */
            public void setRecipients( Collection recipients ) {
                int nsend = recipients.size();
                logger_.log( nsend == 0 ? Level.WARNING : Level.INFO,
                             "Waiting for " + nsend + " responses" );
                recipients_ = recipients;
                if ( map_.keySet().containsAll( recipients_ ) ) {
                    map_.done();
                }
            }

            public void receiveCall( String senderId, String msgId,
                                     Message msg ) {
                throw new UnsupportedOperationException();
            }

            public void receiveNotification( String senderId, Message msg ) {
                throw new UnsupportedOperationException();
            }

            public void receiveResponse( String responderId, String msgTag,
                                         Response response ) {
                map_.put( responderId, response );
                if ( recipients_ != null &&
                     map_.keySet().containsAll( recipients_ ) ) {
                    map_.done();
                }
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

        public synchronized Object put( final Object key, final Object value ) {
            entrySet_.add( new Map.Entry() {
                public Object getKey() {
                    return key;
                }
                public Object getValue() {
                    return value;
                }
                public Object setValue( Object value ) {
                    throw new UnsupportedOperationException();
                }
            } );
            return null;
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
