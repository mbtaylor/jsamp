package org.astrogrid.samp.hub;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.astrogrid.samp.DataException;
import org.astrogrid.samp.Message;
import org.astrogrid.samp.Metadata;
import org.astrogrid.samp.RegInfo;
import org.astrogrid.samp.Response;
import org.astrogrid.samp.SampException;
import org.astrogrid.samp.SampMap;
import org.astrogrid.samp.SampUtils;
import org.astrogrid.samp.Subscriptions;

/**
 * Fairly minimal HubService implementation.
 *
 * @author   Mark Taylor
 * @since    15 Jul 2008
 */
public class BasicHubService implements HubService {

    private final KeyGenerator keyGen_;
    private final ClientIdGenerator idGen_;
    private final Map waiterMap_;
    private final ClientSet clientSet_;
    private final HubClient hubClient_;
    private boolean started_;
    private boolean shutdown_;
    private static final char ID_DELIMITER = '_';
    private final Logger logger_ =
        Logger.getLogger( BasicHubService.class.getName() );

    /** The maximum timeout for a synchronous call prermitted in seconds.
     *  Default is 43200 = 12 hours. */
    public static int MAX_TIMEOUT = 12 * 60 * 60;

    /** The maximum number of concurrently pending synchronous calls.
     *  Default is 100. */
    public static int MAX_WAITERS = 100;

    /**
     * Constructor.
     *
     * @param  random   random number generator used for private keys etc
     */
    public BasicHubService( Random random ) {
        keyGen_ = new KeyGenerator( "k:", 16, random );
        idGen_ = new ClientIdGenerator( "c" );

        // Prepare the client object which represents the hub itself
        // (the one that apparently sends samp.hub.event.shutdown messages etc).
        hubClient_ = new HubClient( keyGen_.next(), "hub" );
        Metadata meta = new Metadata();
        meta.setName( "Hub" );
        meta.setIconUrl( "http://www.star.bristol.ac.uk/"
                       + "~mbt/plastic/images/hub.png" );
        meta.put( "author.name", "Mark Taylor" );
        meta.put( "author.mail", "m.b.taylor@bristol.ac.uk" );
        meta.setDescriptionText( getClass().getName() );
        hubClient_.setMetadata( meta );
        HubReceiver hubRec =
            new HubReceiver( this, hubClient_.getPrivateKey() );
        hubClient_.setReceiver( hubRec );
        hubClient_.setSubscriptions( hubRec.getSubscriptions() );

        // Prepare the data structure which keeps track of registered clients.
        clientSet_ = new BasicClientSet( idGen_.getComparator() );

        // Prepare the data structure which keeps track of pending synchronous
        // calls.
        waiterMap_ = Collections.synchronizedMap( new HashMap() );

        // Ensure that things are tidied up (importantly, shutdown messages
        // get sent) in the case of a JVM termination.
        Runtime.getRuntime().addShutdownHook(
                new Thread( "HubService shutdown" ) {
            public void run() {
                shutdown();
            }
        } );
    }

    public void start() {
        getClientSet().add( getHubClient() );
        started_ = true;
    }

    protected HubClient getHubClient() {
        return hubClient_;
    }

    /**
     * Returns the structure which keeps track of registered clients.
     * May be overridden by subclasses which wish to intercept calls to
     * this object.
     *
     * @return   client set
     */
    protected ClientSet getClientSet() {
        return clientSet_;
    }

    public Map register() throws SampException {
        if ( ! started_ ) {
            throw new SampException( "Not started" );
        }
        HubClient client =
            new HubClient( keyGen_.next(), idGen_.next() );
        getClientSet().add( client );
        hubEvent( new Message( "samp.hub.event.register" )
                     .addParam( "id", client.getId() ) );
        return new RegInfo( getHubClient().getId(), client.getId(),
                            client.getPrivateKey() );
    }

    public void unregister( Object callerKey ) throws SampException {
        HubClient caller = getCaller( callerKey );
        getClientSet().remove( caller );
        hubEvent( new Message( "samp.hub.event.unregister" )
                     .addParam( "id", caller.getId() ) );
    }

    public void setReceiver( Object callerKey, Receiver receiver )
            throws SampException {
        HubClient caller = getCaller( callerKey );
        caller.setReceiver( receiver );
    }

    public void declareMetadata( Object callerKey, Map meta )
            throws SampException {
        HubClient caller = getCaller( callerKey );
        checkMap( Metadata.asMetadata( meta ) );
        caller.setMetadata( meta );
        hubEvent( new Message( "samp.hub.event.metadata" )
                     .addParam( "id", caller.getId() )
                     .addParam( "metadata", meta ) );
    }

    public Map getMetadata( Object callerKey, String clientId )
            throws SampException {
        checkCaller( callerKey );
        return getClient( clientId ).getMetadata();
    }

    public void declareSubscriptions( Object callerKey, Map subscriptions )
            throws SampException {
        HubClient caller = getCaller( callerKey );
        if ( caller.isCallable() ) {
            checkMap( Subscriptions.asSubscriptions( subscriptions ) );
            caller.setSubscriptions( subscriptions );
            hubEvent( new Message( "samp.hub.event.subscriptions" )
                         .addParam( "id", caller.getId() )
                         .addParam( "subscriptions", subscriptions ) );
        }
        else {
            throw new SampException( "Client is not callable" );
        }
    }

    public Map getSubscriptions( Object callerKey, String clientId ) 
            throws SampException {
        checkCaller( callerKey );
        return getClient( clientId ).getSubscriptions();
    }

    public List getRegisteredClients( Object callerKey ) throws SampException {
        HubClient caller = getCaller( callerKey );
        HubClient[] clients = getClientSet().getClients();
        List idList = new ArrayList( clients.length );
        for ( int ic = 0; ic < clients.length; ic++ ) {
            if ( ! clients[ ic ].equals( caller ) ) {
                idList.add( clients[ ic ].getId() );
            }
        }
        return idList;
    }

    public Map getSubscribedClients( Object callerKey, String mtype )
            throws SampException {
        HubClient caller = getCaller( callerKey );
        HubClient[] clients = getClientSet().getClients();
        Map subMap = new TreeMap(); 
        for ( int ic = 0; ic < clients.length; ic++ ) {
            HubClient client = clients[ ic ];
            if ( ! client.equals( caller ) ) {
                Map sub = client.getSubscriptions().getSubscription( mtype );
                if ( sub != null ) {
                    subMap.put( client.getId(), sub );
                }
            }
        }
        return subMap;
    }

    public void notify( Object callerKey, String recipientId, Map message )
            throws SampException {
        HubClient caller = getCaller( callerKey );
        Message msg = Message.asMessage( message );
        checkMap( msg );
        String mtype = msg.getMType();
        HubClient recipient = getClient( recipientId );
        if ( recipient.getSubscriptions().isSubscribed( mtype ) ) {
            recipient.getReceiver()
                     .receiveNotification( caller.getId(), msg );
        }
        else {
            throw new SampException( "Client " + recipient
                                   + " not subscribed to " + mtype );
        }
    }

    public String call( Object callerKey, String recipientId, String msgTag,
                        Map message )
            throws SampException {
        HubClient caller = getCaller( callerKey );
        Message msg = Message.asMessage( message );
        checkMap( msg );
        String mtype = msg.getMType();
        HubClient recipient = getClient( recipientId );
        String msgId = MessageId.encode( caller, msgTag, false );
        if ( recipient.getSubscriptions().isSubscribed( mtype ) ) {
            recipient.getReceiver()
                     .receiveCall( caller.getId(), msgId, msg );
        }
        else {
            throw new SampException( "Client " + recipient
                                   + " not subscribed to " + mtype );
        }
        return msgId;
    }

    public void notifyAll( Object callerKey, Map message )
            throws SampException {
        HubClient caller = getCaller( callerKey );
        Message msg = Message.asMessage( message );
        checkMap( msg );
        String mtype = msg.getMType();
        HubClient[] recipients = getClientSet().getClients();
        for ( int ic = 0; ic < recipients.length; ic++ ) {
            HubClient recipient = recipients[ ic ];
            if ( recipient != caller && recipient.isSubscribed( mtype ) ) {
                try {
                    recipient.getReceiver()
                             .receiveNotification( caller.getId(), msg );
                }
                catch ( SampException e ) {
                    logger_.log( Level.WARNING, 
                                 "Notification " + caller + " -> " + recipient
                               + " failed: " + e, e );
                }
            }
        }
    }

    public String callAll( Object callerKey, String msgTag, Map message )
            throws SampException {
        HubClient caller = getCaller( callerKey );
        Message msg = Message.asMessage( message );
        checkMap( msg );
        String mtype = msg.getMType();
        String msgId = MessageId.encode( caller, msgTag, false );
        HubClient[] recipients = getClientSet().getClients();
        for ( int ic = 0; ic < recipients.length; ic++ ) {
            HubClient recipient = recipients[ ic ];
            if ( recipient != caller && recipient.isSubscribed( mtype ) ) {
                recipient.getReceiver()
                         .receiveCall( caller.getId(), msgId, msg );
            }
        }
        return msgId;
    }

    public void reply( Object callerKey, String msgIdStr, Map response )
            throws SampException {
        HubClient caller = getCaller( callerKey );
        Response resp = Response.asResponse( response );
        checkMap( resp );
        MessageId msgId = MessageId.decode( msgIdStr );
        HubClient sender = getClient( msgId.getSenderId() );
        String senderTag = msgId.getSenderTag();

        // If we can see from the message ID that it was originally sent
        // synchronously, take steps to place the response in the map of
        // waiting messages where it will get picked up and returned to
        // the sender as a callAndWait return value.
        if ( msgId.isSynch() ) {
            synchronized ( waiterMap_ ) {
                if ( waiterMap_.containsKey( msgId ) ) {
                    if ( waiterMap_.get( msgId ) == null ) {
                         waiterMap_.put( msgId, resp );
                         waiterMap_.notifyAll();
                    }
                    else {
                        throw new SampException( "Response ignored"
                                               + " - you've already sent one" );
                    }
                }
                else {
                    throw new SampException( "Response ignored"
                                           + " - synchronous call timed out" );
                }
            }
        }

        // Otherwise, just pass it to the sender using a callback.
        else {
            sender.getReceiver()
                  .receiveResponse( caller.getId(), senderTag, resp );
        }
    }

    public Map callAndWait( Object callerKey, String recipientId, Map message,
                            String timeoutStr )
            throws SampException {
        HubClient caller = getCaller( callerKey );
        Message msg = Message.asMessage( message );
        checkMap( msg );
        String mtype = msg.getMType();
        HubClient recipient = getClient( recipientId );
        MessageId hubMsgId =
            new MessageId( caller.getId(), keyGen_.next(), true );
        int timeout;
        try { 
            timeout = SampUtils.decodeInt( timeoutStr );
        }
        catch ( Exception e ) {
            throw new SampException( "Bad timeout format (should be SAMP int)",
                                     e );
        }
        if ( recipient.getSubscriptions().isSubscribed( mtype ) ) {
            synchronized ( waiterMap_ ) {

                // If the number of pending synchronous calls exceeds the 
                // permitted maximum, remove the oldest calls until there is
                // space for the new one.
                if ( MAX_WAITERS > 0 && waiterMap_.size() >= MAX_WAITERS ) {
                    int excess = waiterMap_.size() - MAX_WAITERS + 1;
                    List keyList = new ArrayList( waiterMap_.keySet() );
                    Collections.sort( keyList, MessageId.AGE_COMPARATOR );
                    logger_.warning( "Pending synchronous calls exceeds limit "
                                   + MAX_WAITERS + " - giving up on " + excess
                                   + " oldest" );
                    for ( int ie = 0; ie < excess; ie++ ) {
                        Object removed = waiterMap_.remove( keyList.get( ie ) );
                        assert removed != null;
                    }
                    waiterMap_.notifyAll();
                }

                // Place an entry for this synchronous call in the waiterMap.
                waiterMap_.put( hubMsgId, null );
            }

            // Make the call asynchronously to the receiver.
            recipient.getReceiver()
                     .receiveCall( caller.getId(), hubMsgId.toString(),
                                   msg );

            // Wait until either the timeout expires, or the response to the
            // message turns up in the waiter map (placed there on another
            // thread by this the reply() method).
            timeout = Math.min( Math.max( 0, timeout ),
                                Math.max( 0, MAX_TIMEOUT ) );
            long finish = timeout > 0
                        ? System.currentTimeMillis() + timeout * 1000
                        : Long.MAX_VALUE;  // 3e8 years
            synchronized ( waiterMap_ ) {
                while ( waiterMap_.containsKey( hubMsgId ) &&
                        waiterMap_.get( hubMsgId ) == null &&
                        System.currentTimeMillis() < finish ) {
                    long millis = finish - System.currentTimeMillis();
                    if ( millis > 0 ) {
                        try {
                            waiterMap_.wait( millis );
                        }
                        catch ( InterruptedException e ) {
                            throw new SampException( "Wait interrupted", e );
                        }
                    }
                }

                // If the response is there, return it to the caller of this
                // method (the sender of the message).
                if ( waiterMap_.containsKey( hubMsgId ) ) {
                    Response response =
                        (Response) waiterMap_.remove( hubMsgId );
                    if ( response != null ) {
                        return response;
                    }
                    else {
                        assert System.currentTimeMillis() >= finish;
                        throw new SampException( "Synchronous call timeout" );
                    }
                }

                // Otherwise, it must have timed out.  Exit with an error.
                else {
                    throw new SampException( "Synchronous call aborted - "
                                           + "server load exceeded maximum?" );
                }
            }
        }
        else {
            throw new SampException( "Client " + recipient
                                   + " not subscribed to " + mtype );
        }
    }

    public synchronized void shutdown() {
        if ( ! shutdown_ ) {
            shutdown_ = true;
            hubEvent( new Message( "samp.hub.event.shutdown" ) );
        }
    }

    /**
     * Broadcast an event message to all subscribed clients.
     * The sender of this message is the hub application itself.
     *
     * @param  msg  message to broadcast
     */
    private void hubEvent( Message msg ) {
        try {
            notifyAll( getHubClient().getPrivateKey(), msg );
        }
        catch ( SampException e ) {
            assert false;
        }
    }

    /**
     * Check that a given callerKey represents a registered client.
     * If it does not, throw an exception.
     *
     * @param callerKey  calling client key
     */
    private void checkCaller( Object callerKey ) throws SampException {
        getCaller( callerKey );
    }

    /**
     * Check that a given map is legal for transmission over SAMP.
     * If it is not, throw a checked exception.
     *
     * @param  map  map to check
     */
    protected void checkMap( SampMap map ) throws SampException {
        try {
            map.check();
        }
        catch ( DataException e ) {
            throw new SampException( e.getMessage(), e );
        }
    }

    /**
     * Returns the client object corresponding to a caller private key.
     * If no such client is registered, throw an exception.
     *
     * @param  callerKey  calling client key
     * @return   hub client object representing caller
     */
    private HubClient getCaller( Object callerKey ) throws SampException {
        HubClient caller =
            getClientSet().getFromPrivateKey( (String) callerKey );
        if ( caller != null ) {
            return caller;
        }
        else {
            throw new SampException( "Invalid key " + callerKey
                                   + " for caller" );
        }
    }

    /**
     * Returns the client object corresponding to a public client ID.
     * If no such client is registered, throw an exception.
     *
     * @param   id  client public id
     * @return  HubClient object
     */
    private HubClient getClient( String id ) throws SampException {
        HubClient client = getClientSet().getFromPublicId( id );
        if ( client != null ) {
            return client;
        }
        else if ( idGen_.hasUsed( id ) ) {
            throw new SampException( "Client " + id
                                   + " is no longer registered" );
        }
        else {
            throw new SampException( "No registered client with ID \""
                                   + id + "\"" );
        }
    }

    /**
     * ClientSet implementation used by this class.
     */
    private static class BasicClientSet implements ClientSet {

        private final Map publicIdMap_;
        private final Map privateKeyMap_;

        /**
         * Constructor.
         *
         * @param  clientIdComparator  comparator for client IDs
         */
        public BasicClientSet( Comparator clientIdComparator ) {
            publicIdMap_ = Collections
                          .synchronizedMap( new TreeMap( clientIdComparator ) );
            privateKeyMap_ = Collections.synchronizedMap( new HashMap() );
        }

        public synchronized void add( HubClient client ) {
            assert client.getId().indexOf( ID_DELIMITER ) < 0;
            publicIdMap_.put( client.getId(), client );
            privateKeyMap_.put( client.getPrivateKey(), client );
        }

        public synchronized void remove( HubClient client ) {
            publicIdMap_.remove( client.getId() );
            privateKeyMap_.remove( client.getPrivateKey() );
        }

        public synchronized HubClient getFromPublicId( String publicId ) {
            return (HubClient) publicIdMap_.get( publicId );
        }

        public synchronized HubClient getFromPrivateKey( String privateKey ) {
            return (HubClient) privateKeyMap_.get( privateKey );
        }

        public synchronized HubClient[] getClients() {
            return (HubClient[])
                   publicIdMap_.values().toArray( new HubClient[ 0 ] );
        }
    }

    /**
     * Encapsulates information about a MessageId.
     * A message ID can be represented as a string, but encodes information
     * which can be retrieved later.
     */
    private static class MessageId {

        private final String senderId_;
        private final String senderTag_;
        private final boolean isSynch_;
        private final long birthday_;

        private static final String T_SYNCH_FLAG = "S";
        private static final String F_SYNCH_FLAG = "A";
        private static final int CHECK_SEED = (int) System.currentTimeMillis();
        private static final int CHECK_LENG = 4;
        private static final Comparator AGE_COMPARATOR = new Comparator() {
            public int compare( Object o1, Object o2 ) {
                return (int) (((MessageId) o1).birthday_ -
                              ((MessageId) o2).birthday_);
            }
        };

        /**
         * Constructor.
         *
         * @param  senderId  client id of the message sender
         * @param  senderTag  msgTag provided by the sender
         * @param  isSynch   whether the message was sent synchronously or not
         */
        public MessageId( String senderId, String senderTag, boolean isSynch ) {
            senderId_ = senderId;
            senderTag_ = senderTag;
            isSynch_ = isSynch;
            birthday_ = System.currentTimeMillis();
        }

        /**
         * Returns the sender's public client id.
         *
         * @return  sender's id
         */
        public String getSenderId() {
            return senderId_;
        }

        /**
         * Returns the msgTag attached to the message by the sender.
         *
         * @return   msgTag
         */
        public String getSenderTag() {
            return senderTag_;
        }

        /**
         * Returns whether the message was sent synchronously.
         *
         * @return  true iff message was sent using callAndWait
         */
        public boolean isSynch() {
            return isSynch_;
        }

        public int hashCode() {
            return checksum( senderId_, senderTag_, isSynch_ ).hashCode();
        }

        public boolean equals( Object o ) {
            if ( o instanceof MessageId ) {
                MessageId other = (MessageId) o;
                return this.senderId_.equals( other.senderId_ )
                    && this.senderTag_.equals( other.senderTag_ )
                    && this.isSynch_ == other.isSynch_;
            }
            else {
                return false;
            }
        }

        /**
         * Returns the string representation of this MessageId.
         *
         * @return  message ID string
         */
        public String toString() {
            Object checksum = checksum( senderId_, senderTag_, isSynch_ );
            return new StringBuffer()
                  .append( senderId_ )
                  .append( ID_DELIMITER )
                  .append( isSynch_ ? T_SYNCH_FLAG : F_SYNCH_FLAG )
                  .append( ID_DELIMITER )
                  .append( checksum )
                  .append( ID_DELIMITER )
                  .append( senderTag_ )
                  .toString();
        }

        /**
         * Decodes a msgId string to return the corresponding MessageId object.
         * This is the opposite of the {@link #toString} method.
         *
         * @param  msgId  string representation of message ID
         * @return   new MessageId object
         */
        public static MessageId decode( String msgId )
                throws SampException {
            int delim1 = msgId.indexOf( ID_DELIMITER );
            int delim2 = msgId.indexOf( ID_DELIMITER, delim1 + 1 );
            int delim3 = msgId.indexOf( ID_DELIMITER, delim2 + 1 );
            if ( delim1 < 0 || delim2 < 0 || delim3 < 0 ) {
                throw new SampException( "Badly formed message ID" );
            }
            String senderId = msgId.substring( 0, delim1 );
            String synchFlag = msgId.substring( delim1 + 1, delim2 );
            String checksum = msgId.substring( delim2 + 1, delim3 );
            String senderTag = msgId.substring( delim3 + 1 );
            boolean isSynch;
            if ( T_SYNCH_FLAG.equals( synchFlag ) ) {
                isSynch = true;
            }
            else if ( F_SYNCH_FLAG.equals( synchFlag ) ) {
                isSynch = false;
            }
            else {
                throw new SampException( "Badly formed message ID"
                                       + " (synch flag)" );
            }
            if ( ! checksum( senderId, senderTag, isSynch )
                  .equals( checksum ) ) {
                throw new SampException( "Bad message ID checksum" );
            }
            MessageId idObj = new MessageId( senderId, senderTag, isSynch );
            assert idObj.toString().equals( msgId );
            return idObj;
        }

        /**
         * Returns a message ID string corresponding to the arguments.
         *
         * @param   sender   sender client
         * @param   senderTag  msgTag attached by sender
         * @param   isSynch  whether message was sent synchronously
         * @return  string representation of message ID
         */
        public static String encode( HubClient sender, String senderTag,
                                     boolean isSynch ) {
            return new MessageId( sender.getId(), senderTag, isSynch )
                  .toString();
        }

        /**
         * Returns a checksum string which is a hash of the given arguments.
         *
         * @param  senderId  public client id of sender
         * @param   senderTag  msgTag attached by sender
         * @param   isSynch  whether message was sent synchronously
         * @return  checksum string
         */
        private static String checksum( String senderId, String senderTag,
                                        boolean isSynch ) {
            int sum = CHECK_SEED;
            sum = 23 * sum + senderId.hashCode();
            sum = 23 * sum + senderTag.hashCode();
            sum = 23 * sum + ( isSynch ? 3 : 5 );
            String check = Integer.toHexString( sum );
            check =
                check.substring( Math.max( 0, check.length() - CHECK_LENG ) );
            while ( check.length() < CHECK_LENG ) {
                check = "0" + check;
            }
            assert check.length() == CHECK_LENG;
            return check;
        }
    }

    /**
     * Object which can generate a sequence of private keys.
     * The values returned by the next() method should in general not be
     * easy to guess.
     */
    private static class KeyGenerator {

        private final String prefix_;
        private final int nchar_;
        private final Random random_;
        private int iseq_;
        private final char unused_;

        /**
         * Constructor.
         *
         * @param  prefix  prefix prepended to all generated keys
         * @param  nchar   number of characters in generated keys
         * @param  random  random number generator
         */
        public KeyGenerator( String prefix, int nchar, Random random ) {
            prefix_ = prefix;
            nchar_ = nchar;
            random_ = random;
            unused_ = '_';
        }

        /**
         * Returns the next key in the sequence.
         * Guaranteed different from any previous return value from this method.
         *
         * @return  key string
         */
        public synchronized String next() {
            StringBuffer sbuf = new StringBuffer();
            sbuf.append( prefix_ );
            sbuf.append( Integer.toString( ++iseq_ ) );
            sbuf.append( '_' );
            for ( int i = 0; i < nchar_; i++ ) {
                char c = (char) ( 'a' + (char) random_.nextInt( 'z' - 'a' ) );
                assert c != ID_DELIMITER;
                sbuf.append( c );
            }
            return sbuf.toString();
        }

        /**
         * Returns a character guaranteed to be absent from any key generated
         * by this object.
         *
         * @return  unused character
         */
        public char getUnusedChar() {
            return unused_;
        }
    }

    /**
     * Generates client public IDs.
     * These must be unique, but don't need to be hard to guess.
     */
    private static class ClientIdGenerator {
        private int iseq_;
        private final String prefix_;
        private final Comparator comparator_;

        /**
         * Constructor.
         *
         * @param  prefix  prefix for all generated ids
         */
        public ClientIdGenerator( String prefix ) {
            prefix_ = prefix;

            // Prepare a comparator which will order the keys generated here
            // in sequence of generation.
            comparator_ = new Comparator() {
                public int compare( Object o1, Object o2 ) {
                    String s1 = o1.toString();
                    String s2 = o2.toString();
                    Integer i1 = getIndex( s1 );
                    Integer i2 = getIndex( s2 );
                    if ( i1 == null && i2 == null ) {
                        return s1.compareTo( s2 );
                    }
                    else if ( i1 == null ) {
                        return +1;
                    }
                    else if ( i2 == null ) {
                        return -1;
                    }
                    else {
                        return i1.intValue() - i2.intValue();
                    }
                } 
            };
        }

        /**
         * Returns the next unused id.
         *
         * @return  next id
         */
        public synchronized String next() {
            return prefix_ + Integer.toString( ++iseq_ );
        }

        /**
         * Indicates whether a given client ID has previously been dispensed
         * by this object.
         *
         * @param  id  id to test
         * @return  true iff id has been returned by a previous call of
         *          <code>next</code>
         */
        public boolean hasUsed( String id ) {
            Integer ix = getIndex( id );
            return ix != null && ix.intValue() <= iseq_;
        }

        /**
         * Returns an Integer giving the sequence index of the given id string.
         * If <code>id</code> does not look like a string generated by this
         * object, null is returned.
         *
         * @param   id  identifier to test
         * @return   object containing sequence index of <code>id</code>,
         *           or null
         */
        private Integer getIndex( String id ) {
            if ( id.startsWith( prefix_ ) ) {
                try {
                    int iseq =
                        Integer.parseInt( id.substring( prefix_.length() ) );
                    return new Integer( iseq );
                }
                catch ( NumberFormatException e ) {
                    return null;
                }
            }
            else {
                return null;
            }
        }

        /**
         * Returns a comparator which will order the IDs generated by this
         * object in generation sequence.
         *
         * @return  id comparator
         */
        public Comparator getComparator() {
            return comparator_;
        }
    }
}
