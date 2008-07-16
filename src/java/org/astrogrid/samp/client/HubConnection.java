package org.astrogrid.samp.client;

import java.util.Map;
import org.astrogrid.samp.Metadata;
import org.astrogrid.samp.RegInfo;
import org.astrogrid.samp.Response;
import org.astrogrid.samp.SampException;
import org.astrogrid.samp.Subscriptions;

/**
 * Represents a registered client's connection to a running hub.
 *
 * @author   Mark Taylor
 * @since    15 Jul 2008
 */
public interface HubConnection {

    /**
     * Returns the registration information associated with this connection.
     *
     * @return  registration info
     */
    RegInfo getRegInfo();

    /**
     * Tells the hub how it can perform callbacks on the client by providing
     * a CallableClient object.  This is required before the client 
     * can declare subscriptions or make asynchronous calls.
     * 
     * @param  callable  callable client
     */
    void setCallable( CallableClient callable ) throws SampException;

    /**
     * Tests whether the connection is currently open.
     *
     * @throws  SampException  if the hub has disappeared or communications
     *          are disrupted in some other way
     */
    void ping() throws SampException;

    /**
     * Unregisters the client and terminates this connection.
     */
    void unregister() throws SampException;

    /**
     * Declares this registered client's metadata.
     *
     * @param   meta  {@link org.astrogrid.samp.Metadata}-like map
     */
    void declareMetadata( Map meta ) throws SampException;

    /**
     * Returns the metadata for another registered client.
     *
     * @param  clientId  publid id for another registered client
     * @return  metadata map
     */
    Metadata getMetadata( String clientId ) throws SampException;

    /**
     * Declares this registered client's MType subscriptions.
     *
     * <p>Only permitted if this client is already callable.
     *
     * @param   subs  {@link org.astrogrid.samp.Subscriptions}-like map
     */
    void declareSubscriptions( Map subs ) throws SampException;

    /**
     * Returns the subscriptions for another registered client.
     *
     * @param   clientId  public id for another registered client
     * @return  subscriptions map
     */
    Subscriptions getSubscriptions( String clientId ) throws SampException;

    /**
     * Returns the list of client public IDs for those clients currently
     * registered.
     *
     * @return  array of client ids, excluding the one for this client
     */
    String[] getRegisteredClients() throws SampException;

    /**
     * Returns a map of subscriptions for a given MType.
     *
     * @param  mtype  MType
     * @return  map in which the keys are the publid IDs of clients subscribed
     *          to <code>mtype</code>
     */
    Map getSubscribedClients( String mtype ) throws SampException;

    /** 
     * Sends a message to a given client without wanting a response.
     *
     * @param  recipientId  public-id of client to receive message
     * @param  msg  {@link org.astrogrid.samp.Message}-like map
     */
    void notify( String recipientId, Map msg ) throws SampException;

    /**
     * Sends a message to all subscribed clients without wanting a response.
     *
     * @param  msg {@link org.astrogrid.samp.Message}-like map
     */
    void notifyAll( Map msg ) throws SampException;

    /**
     * Sends a message to a given client expecting a response.
     * The <code>receiveResponse</code> method of this connection's
     * {@link CallableClient} will be called with a 
     * response at some time in the future.
     *
     * <p>Only permitted if this client is already callable.
     *
     * @param  recipientId  public-id of client to receive message
     * @param  msgTag  arbitrary string tagging this message for caller's
     *         benefit
     * @param  msg {@link org.astrogrid.samp.Message}-like map
     */
    String call( String recipientId, String msgTag, Map msg )
        throws SampException;

    /**
     * Sends a message to all subscribed clients expecting responses.
     * The <code>receiveResponse</code> method of this connection's
     * {@link CallableClient} will be called with responses at some
     * time in the future.
     *
     * <p>Only permitted if this client is already callable.
     *
     * @param  msgTag  arbitrary string tagging this message for caller's
     *         benefit
     * @param  msg {@link org.astrogrid.samp.Message}-like map
     */
    String callAll( String msgTag, Map msg ) throws SampException;

    /**
     * Sends a message synchronously to a client, waiting for the response.
     * If more seconds elapse than the value of the <code>timeout</code>
     * parameter, an exception will result.
     *
     * @param  recipientId  public-id of client to receive message
     * @param  msg {@link org.astrogrid.samp.Message}-like map
     * @param  timeout  timeout in seconds, or &lt;0 for no timeout
     * @return  response
     */
    Response callAndWait( String recipientId, Map msg, int timeout )
        throws SampException;

    /**
     * Supplies a response to a previously received message.
     *
     * @param  msgId ID associated with earlier send
     * @param  response  {@link org.astrogrid.samp.Response}-like map
     */
    void reply( String msgId, Map response ) throws SampException;
}
