package org.astrogrid.samp;

public interface CallableClient {

    void receiveNotification( String senderId, Message message )
            throws SampException;

    void receiveCall( String senderId, String msgId, Message message )
            throws SampException;

    void receiveResponse( String responderId, String msgId, Response response )
            throws SampException;
}
