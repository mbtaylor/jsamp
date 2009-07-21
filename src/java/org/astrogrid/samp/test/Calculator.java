package org.astrogrid.samp.test;

import java.io.IOException;
import java.net.URL;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.logging.Logger;
import org.astrogrid.samp.ErrInfo;
import org.astrogrid.samp.Message;
import org.astrogrid.samp.Metadata;
import org.astrogrid.samp.Response;
import org.astrogrid.samp.SampUtils;
import org.astrogrid.samp.Subscriptions;
import org.astrogrid.samp.client.CallableClient;
import org.astrogrid.samp.client.HubConnection;
import org.astrogrid.samp.client.SampException;
import org.astrogrid.samp.httpd.DefaultServer;

/**
 * Test client.  Performs simple integer arithmetic.
 *
 * @author   Mark Taylor
 * @since    21 Jul 2008
 */
public class Calculator extends Tester implements CallableClient {

    private final HubConnection connection_;
    private final Map callMap_;
    private final Random random_;
    private volatile int nCalc_;
    private volatile int nCall_;
    private static final String ADD_MTYPE = "calc.int.add";
    private static final String SUB_MTYPE = "calc.int.sub";
    private static final String MUL_MTYPE = "calc.int.mul";
    private static final String DIV_MTYPE = "calc.int.div";
    private static final Logger logger_ =
        Logger.getLogger( Calculator.class.getName() );
    private static String iconUrl_;

    /** Sends messages using the Notify delivery pattern. */
    public static final SendMode NOTIFY_MODE = new SendMode( "notify" ) {
        void send( Calculator calc, String receiverId, CalcRequest request,
                   int iseq )
                throws SampException {
            calc.connection_.notify( receiverId, request.getMessage() );
        }
    };

    /** Sends messages using the Synchronous Call/Response delivery pattern. */
    public static final SendMode ASYNCH_MODE = new SendMode( "sync" ) {
        void send( Calculator calc, String receiverId, CalcRequest request,
                   int iseq )
                throws SampException {
            String msgTag = "tag-" + iseq;
            synchronized ( calc.callMap_ ) {
                calc.callMap_.put( msgTag, request );
            }
            calc.connection_.call( receiverId, msgTag, request.getMessage() );
        }
    };

    /** Sends messages using the Asynchronous Call/Response delivery pattern. */
    public static final SendMode SYNCH_MODE = new SendMode( "async" ) {
        void send( Calculator calc, String receiverId, CalcRequest request,
                   int iseq )
                throws SampException {
            Response response =
                calc.connection_.callAndWait( receiverId, request.getMessage(),
                                              0 );
            request.checkResponse( response );
        }
    };

    /** Sends messages using a random choice of one of the other modes. */
    public static final SendMode RANDOM_MODE = new SendMode( "mixture" ) {
        private final SendMode[] otherModes = new SendMode[] {
            NOTIFY_MODE, ASYNCH_MODE, SYNCH_MODE,
        };
        void send( Calculator calc, String receiverId, CalcRequest request,
                   int iseq )
                throws SampException {
            otherModes[ calc.random_.nextInt( otherModes.length ) ]
                .send( calc, receiverId, request, iseq );
        }
    };

    /**
     * Constructor.
     *
     * @param  connection   hub connection
     * @param  random       random number generator
     */
    public Calculator( HubConnection connection, Random random )
            throws SampException {
        connection_ = connection;
        random_ = random;
        callMap_ = Collections.synchronizedMap( new HashMap() );
        connection_.setCallable( this );
        Metadata meta = new Metadata();
        meta.setName( "Calculator" );
        meta.setDescriptionText( "Rudimentary integer arithmetic application" );
        String iconUrl = getIconUrl();
        if ( iconUrl != null ) {
            meta.setIconUrl( iconUrl );
        }
        connection_.declareMetadata( meta );
        Subscriptions subs = new Subscriptions();
        subs.addMType( ADD_MTYPE );
        subs.addMType( SUB_MTYPE );
        subs.addMType( MUL_MTYPE );
        subs.addMType( DIV_MTYPE );
        connection_.declareSubscriptions( subs );
    }

    /**
     * Sends a randomly generated message in a randomly generated way to
     * a given receiver.  The receiver should be another calculator client,
     * like this one.  If the message is sent according to one of the
     * call/response delivery patterns the response will be checked to
     * ensure that it has the correct value.
     *
     * @param  receiverId  client ID of another Calculator client.
     */
    public void sendMessage( String receiverId, SendMode mode )
            throws SampException {
        mode.send( this, receiverId, createRandomRequest(), nextCall() );
    }

    /**
     * Returns the total number of messages sent using any delivery pattern.
     * 
     * @return   number of sends
     */
    public int getSendCount() {
        return nCall_;
    }

    /**
     * Returns the total number of messages received using any delivery pattern.
     *
     * @return   number of receives
     */
    public int getReceiveCount() {
        return nCalc_;
    }

    /**
     * Returns the hub connection used by this client.
     *
     * @return  connection
     */
    public HubConnection getConnection() {
        return connection_;
    }

    /** 
     * Waits until all the responses this client is expecting to get 
     * have been safely received.
     */
    public void flush() {
        try {
            synchronized ( callMap_ ) {
                while ( ! callMap_.isEmpty() ) {
                    callMap_.wait( 100 );
                }
            }
        }
        catch ( InterruptedException e ) {
            throw new RuntimeException( "Interrupted" );
        }
    }

    public void receiveNotification( String senderId, Message msg ) {
        processCall( senderId, msg );
    }

    public void receiveCall( String senderId, String msgId, Message msg )
            throws SampException {
        Response response;
        try {
            response = Response
                      .createSuccessResponse( processCall( senderId, msg ) );
        }
        catch ( Throwable e ) {
            response = Response.createErrorResponse( new ErrInfo( e ) );
        }
        connection_.reply( msgId, response );
    }

    public void receiveResponse( String senderId, String msgTag,
                                 Response response ) {
        CalcRequest request;
        synchronized ( callMap_ ) {
            request = (CalcRequest) callMap_.remove( msgTag );
        }
        request.checkResponse( response );
    }

    /**
     * Does the work for both the receiveNotify and receiveCall methods.
     *
     * @param  senderId  sender public ID
     * @param  msg       message object
     * @return  content of the successful reply's samp.result entry
     */
    private Map processCall( String senderId, Message msg ) {
        String mtype = msg.getMType();
        if ( ADD_MTYPE.equals( mtype ) ||
             SUB_MTYPE.equals( mtype ) ||
             MUL_MTYPE.equals( mtype ) ||
             DIV_MTYPE.equals( mtype ) ) {
            synchronized ( this ) {
                nCalc_++;
            }
            int a = SampUtils.decodeInt( (String) msg.getParam( "a" ) );
            int b = SampUtils.decodeInt( (String) msg.getParam( "b" ) );
            final int x;
            if ( ADD_MTYPE.equals( mtype ) ) {
                x = a + b;
            }
            else if ( SUB_MTYPE.equals( mtype ) ) {
                x = a - b;
            }
            else if ( MUL_MTYPE.equals( mtype ) ) {
                x = a * b;
            }
            else if ( DIV_MTYPE.equals( mtype ) ) {
                x = a / b;
            }
            else {
                throw new AssertionError();
            }
            Map result = new HashMap();
            result.put( "x", SampUtils.encodeInt( x ) );
            return result;
        }
        else {
            throw new TestException();
        }
    }

    /**
     * Increments and then returns the number of calls so far made by this
     * object.
     *
     * @return  next value of the call counter
     */
    private synchronized int nextCall() {
        return ++nCall_;
    }

    /**
     * Generates a random calculation request.
     *
     * @return   new random request
     */
    private CalcRequest createRandomRequest() {
        String mtype = new String[] {
            ADD_MTYPE,
            SUB_MTYPE,
            MUL_MTYPE,
            DIV_MTYPE,
        }[ random_.nextInt( 4 ) ];
        return new CalcRequest( mtype,
                                random_.nextInt( 1000 ),
                                500 + random_.nextInt( 500 ) );
    }

    private static String getIconUrl() {
        if ( iconUrl_ == null ) {
            String resource = "/org/astrogrid/samp/images/tinycalc.gif";
            URL url;
            try {
                url = DefaultServer.exportResource( resource );
            }
            catch ( IOException e ) {
                url = null;
                logger_.warning( "Can't locate icon: " + resource );
            }
            iconUrl_ = url == null ? "" : url.toString();
        }
        return iconUrl_.length() > 0 ? iconUrl_ : null;
    }

    /**
     * Represents a delivery pattern.
     * Instances are provided as static members of class {@link Calculator}.
     */
    public static abstract class SendMode {
        private final String name_;

        /**
         * Constructor.
         *
         * @param  name  mode name
         */
        private SendMode( String name ) {
            name_ = name;
        }

        /**
         * Sends a message from one calculator client to another using this
         * send mode.
         *
         * @param  calc  sending client
         * @param  receiverId  public ID of receiving client
         * @param  request  calculation request object
         * @param  iseq  unique identifier for this request by this calculator
         */
        abstract void send( Calculator calc, String receiverId,
                            CalcRequest request, int iseq )
                throws SampException;

        /**
         * Returns the name of this mode.
         */
        public String toString() {
            return name_;
        }
    }

    /**
     * Represents a request which may be sent to a Calculator object.
     */
    private class CalcRequest {

        private final int a_;
        private final int b_;
        private final String mtype_;
        private final int x_;

        /**
         * Constructor.
         *
         * @param  mtype  operation type as an MType string
         * @param  a   first parameter
         * @param  b   second parameter
         */
        public CalcRequest( String mtype, int a, int b ) {
            mtype_ = mtype;
            a_ = a;
            b_ = b;
            if ( ADD_MTYPE.equals( mtype ) ) {
                x_ = a + b;
            }
            else if ( SUB_MTYPE.equals( mtype ) ) {
                x_ = a - b;
            }
            else if ( MUL_MTYPE.equals( mtype ) ) {
                x_ = a * b;
            }
            else if ( DIV_MTYPE.equals( mtype ) ) {
                x_ = a / b;
            }
            else {
                throw new IllegalArgumentException();
            }
        }

        /**
         * Returns a Message object corresponding to this request.
         */
        public Message getMessage() {
            Message msg = new Message( mtype_ );
            msg.addParam( "a", SampUtils.encodeInt( a_ ) );
            msg.addParam( "b", SampUtils.encodeInt( b_ ) );
            return msg;
        }

        /**
         * Checks that the given response is correct for this request.
         *
         * @param  response  response to check
         */
        public void checkResponse( Response response ) {
            assertEquals( Response.OK_STATUS, response.getStatus() );
            assertEquals( x_,
                          SampUtils.decodeInt( (String)
                                               response.getResult()
                                                       .get( "x" ) ) );
        }
    }
}
