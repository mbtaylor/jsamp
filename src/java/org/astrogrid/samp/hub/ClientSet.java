package org.astrogrid.samp.hub;

/**
 * Data structure for keeping track of clients currently registered with a hub.
 *
 * @author   Mark Taylor
 * @since    15 Jul 2008
 */
public interface ClientSet {

    /** 
     * Adds a new client to the set.
     *
     * @param   client  client to add
     */
    void add( HubClient client );

    /**
     * Removes a client from the set.
     *
     * @param  client  client to remove
     */
    void remove( HubClient client );

    /**
     * Returns the client in the set corresponding to a given public ID.
     *
     * @param  publicId  client public ID
     * @return  client with id <code>publicId</code> if registered, or null
     */
    HubClient getFromPublicId( String publicId );

    /**
     * Returns the client in the set corresponding to a given private key.
     *
     * @param  privateKey  client private key
     * @return  client with key <code>privateKey</code> if registered, or null
     */
    HubClient getFromPrivateKey( String privateKey );

    /**
     * Returns an array of all the currently contained clients.
     *
     * @return client list
     */
    HubClient[] getClients();
}
