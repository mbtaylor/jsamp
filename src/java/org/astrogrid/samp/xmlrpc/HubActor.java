package org.astrogrid.samp.xmlrpc;

import java.util.List;
import java.util.Map;
import org.astrogrid.samp.client.SampException;

/**
 * Defines the XML-RPC methods which must be implemented by a
 * Standard Profile hub.
 *
 * @author   Mark Taylor
 * @since    15 Jul 2008
 */
interface HubActor {

    /**
     * Throws an exception if service is not operating.
     */
    void ping() throws SampException;

    /**
     * Throws an exception if service is not operating.
     *
     * @param  privateKey  ignored
     */
    void ping( String privateKey ) throws SampException;

    /**
     * Registers a new client and returns a map with registration information.
     *
     * @param  secret  registration password
     * @return  {@link org.astrogrid.samp.RegInfo}-like map.
     */
    Map register( String secret ) throws SampException;

    /**
     * Unregisters a registered client.
     *
     * @param privateKey  calling client private key
     */
    void unregister( String privateKey ) throws SampException;

    /**
     * Sets the XML-RPC URL to use for callbacks for a callable client.
     *
     * @param  privateKey  calling client private key
     * @param  url    XML-RPC endpoint for client API callbacks
     */
    void setXmlrpcCallback( String privateKey, String url )
            throws SampException;

    /**
     * Declares metadata for the calling client.
     *
     * @param  privateKey  calling client private key
     * @param  meta  {@link org.astrogrid.samp.Metadata}-like map
     */
    void declareMetadata( String privateKey, Map meta )
            throws SampException;

    /**
     * Returns metadata for a given client.
     *
     * @param  privateKey  calling client private key
     * @param clientId  public ID for client whose metadata is required
     * @return  {@link org.astrogrid.samp.Metadata}-like map
     */
    Map getMetadata( String privateKey, String clientId )
            throws SampException;

    /**
     * Declares subscription information for the calling client.
     *
     * @param  privateKey  calling client private key
     * @param  subs  {@link org.astrogrid.samp.Subscriptions}-like map
     */
    void declareSubscriptions( String privateKey, Map subs )
            throws SampException;

    /**
     * Returns subscriptions for a given client.
     *
     * @param  privateKey  calling client private key
     * @return {@link org.astrogrid.samp.Subscriptions}-like map
     */
    Map getSubscriptions( String privateKey, String clientId )
            throws SampException;

    /**
     * Returns a list of the public-ids of all currently registered clients.
     *
     * @param  privateKey  calling client private key
     * @return  list of Strings
     */
    List getRegisteredClients( String privateKey ) throws SampException;

    /**
     * Returns a map of the clients subscribed to a given MType.
     *
     * @param  privateKey  calling client private key
     * @param mtype  MType of interest
     * @return  map in which the keys are the public-ids of clients subscribed
     *          to <code>mtype</code>
     */
    Map getSubscribedClients( String privateKey, String mtype )
            throws SampException;

    /**
     * Sends a message to a given client without wanting a response.
     *
     * @param  privateKey  calling client private key
     * @param  recipientId  public-id of client to receive message
     * @param  msg  {@link org.astrogrid.samp.Message}-like map
     */
    void notify( String privateKey, String recipientId, Map msg )
            throws SampException;

    /**
     * Sends a message to all subscribed clients without wanting a response.
     *
     * @param  privateKey  calling client private key
     * @param  msg {@link org.astrogrid.samp.Message}-like map
     * @return  list of public-ids for clients to which the notify will be sent
     */
    List notifyAll( String privateKey, Map msg ) throws SampException;

    /**
     * Sends a message to a given client expecting a response.
     *
     * @param  privateKey  calling client private key
     * @param  recipientId  public-id of client to receive message
     * @param  msgTag  arbitrary string tagging this message for caller's
     *         benefit
     * @param  msg {@link org.astrogrid.samp.Message}-like map
     * @return  message ID
     */
    String call( String privateKey, String recipientId, String msgTag,
                 Map msg ) throws SampException;

    /**
     * Sends a message to all subscribed clients expecting responses.
     *
     * @param  privateKey  calling client private key
     * @param  msgTag  arbitrary string tagging this message for caller's
     *         benefit
     * @param  msg {@link org.astrogrid.samp.Message}-like map
     * @return  public-id->msg-id map for clients to which an attempt to
     *          send the call will be made
     */
    Map callAll( String privateKey, String msgTag, Map msg )
            throws SampException;

    /**
     * Sends a message synchronously to a client.
     *
     * @param  privateKey  calling client private key
     * @param  recipientId  public-id of client to receive message
     * @param  msg {@link org.astrogrid.samp.Message}-like map
     * @param  timeout  timeout in seconds encoded as a SAMP int
     * @return  {@link org.astrogrid.samp.Response}-like map
     */
    Map callAndWait( String privateKey, String recipientId, Map msg,
                     String timeout ) throws SampException;

    /**
     * Responds to a previously sent message.
     *
     * @param  privateKey  calling client private key
     * @param  msgId ID associated with earlier send
     * @param  response  {@link org.astrogrid.samp.Response}-like map
     */
    void reply( String privateKey, String msgId, Map response )
            throws SampException;
}
