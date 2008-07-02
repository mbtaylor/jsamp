package org.astrogrid.samp.hub;

public interface ClientSet {
    void add( HubClient client );
    void remove( HubClient client );
    HubClient getFromPublicId( String publicId );
    HubClient getFromPrivateKey( String privateKey );
    HubClient[] getClients();
}
