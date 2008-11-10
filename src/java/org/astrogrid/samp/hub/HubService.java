package org.astrogrid.samp.hub;

import java.util.List;
import java.util.Map;

/**
 * Interface defining the work that the hub has to do.
 * This is independent of profile or transport, and just concerns
 * keeping track of clients and routing messages between them.
 *
 * <p>All the methods which are to be called by registered clients take
 * an initial argument <code>caller</code>.  This is <em>not</em> the public
 * client-id; its nature (and class) will be profile-dependent, 
 * but basically it serves to identify the client making the call.
 * Other than that, the methods here pretty much 
 * reflect those defined by the SAMP standard.
 *
 * <p>Most methods are declared to throw {@link HubServiceException} which
 * is a catch-all exception to indicate that the request could not be 
 * fulfilled.  However, it's OK for any of these methods to throw 
 * unchecked exceptions if that is more convenient for the implementation -
 * the service user should catch these if they occur.
 *
 * @author   Mark Taylor
 * @since    15 Jul 2008
 */
public interface HubService {

    /**
     * Begin operation.  The other methods should not be called until
     * the hub has been started.
     */
    void start();

    /**
     * Registers a new client and returns a map with registration information.
     *
     * @return  {@link org.astrogrid.samp.RegInfo}-like map.
     */
    Map register() throws HubServiceException;

    /**
     * Unregisters a registered client.
     *
     * @param caller  calling client identifier
     */
    void unregister( Object caller ) throws HubServiceException;

    /**
     * Sets a receiver object to handle callbacks on behalf of the calling
     * client.
     *
     * @param caller  calling client identifier
     * @param  receiver  callback handler
     */
    void setReceiver( Object caller, Receiver receiver )
            throws HubServiceException;

    /**
     * Declares metadata for the calling client.
     *
     * @param caller  calling client identifier
     * @param  meta  {@link org.astrogrid.samp.Metadata}-like map
     */
    void declareMetadata( Object caller, Map meta )
            throws HubServiceException;

    /**
     * Returns metadata for a given client.
     *
     * @param caller  calling client identifier
     * @param clientId  public ID for client whose metadata is required
     * @return  {@link org.astrogrid.samp.Metadata}-like map
     */
    Map getMetadata( Object caller, String clientId )
            throws HubServiceException;

    /**
     * Declares subscription information for the calling client.
     *
     * @param caller  calling client identifier
     * @param  subs  {@link org.astrogrid.samp.Subscriptions}-like map
     */
    void declareSubscriptions( Object caller, Map subs )
            throws HubServiceException;

    /**
     * Returns subscriptions for a given client.
     *
     * @param caller  calling client identifier
     * @return {@link org.astrogrid.samp.Subscriptions}-like map
     */
    Map getSubscriptions( Object caller, String clientId )
            throws HubServiceException;

    /**
     * Returns a list of the public-ids of all currently registered clients.
     *
     * @param caller  calling client identifier
     * @return  list of Strings
     */
    List getRegisteredClients( Object caller ) throws HubServiceException;

    /**
     * Returns a map of the clients subscribed to a given MType.
     *
     * @param caller  calling client identifier
     * @param mtype  MType of interest
     * @return  map in which the keys are the public-ids of clients subscribed
     *          to <code>mtype</code>
     */
    Map getSubscribedClients( Object caller, String mtype )
            throws HubServiceException;

    /**
     * Sends a message to a given client without wanting a response.
     *
     * @param caller  calling client identifier
     * @param  recipientId  public-id of client to receive message
     * @param  msg  {@link org.astrogrid.samp.Message}-like map
     */
    void notify( Object caller, String recipientId, Map msg )
            throws HubServiceException;

    /**
     * Sends a message to all subscribed clients without wanting a response.
     *
     * @param caller  calling client identifier
     * @param  msg {@link org.astrogrid.samp.Message}-like map
     * @return  list of public-ids for clients to which the notify will be sent
     */
    List notifyAll( Object caller, Map msg ) throws HubServiceException;

    /**
     * Sends a message to a given client expecting a response.
     *
     * @param caller  calling client identifier
     * @param  recipientId  public-id of client to receive message
     * @param  msgTag  arbitrary string tagging this message for caller's
     *         benefit
     * @param  msg {@link org.astrogrid.samp.Message}-like map
     * @return  message ID
     */
    String call( Object caller, String recipientId, String msgTag, Map msg )
            throws HubServiceException;

    /**
     * Sends a message to all subscribed clients expecting responses.
     *
     * @param caller  calling client identifier
     * @param  msgTag  arbitrary string tagging this message for caller's
     *         benefit
     * @param  msg {@link org.astrogrid.samp.Message}-like map
     * @return  public-id->msg-id map for clients to which an attempt to
     *          send the call will be made
     */
    Map callAll( Object caller, String msgTag, Map msg )
            throws HubServiceException;

    /**
     * Sends a message synchronously to a client.
     *
     * @param caller  calling client identifier
     * @param  recipientId  public-id of client to receive message
     * @param  msg {@link org.astrogrid.samp.Message}-like map
     * @param  timeout  timeout in seconds encoded as a SAMP int
     * @return  {@link org.astrogrid.samp.Response}-like map
     */
    Map callAndWait( Object caller, String recipientId, Map msg,
                     String timeout )
            throws HubServiceException;

    /**
     * Responds to a previously sent message.
     *
     * @param caller  calling client identifier
     * @param  msgId ID associated with earlier send
     * @param  response  {@link org.astrogrid.samp.Response}-like map
     */
    void reply( Object caller, String msgId, Map response )
             throws HubServiceException;

    /**
     * Tidies up any resources owned by this object.
     * Should be called when no longer required.
     */
    void shutdown();
}
