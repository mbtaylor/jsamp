package org.astrogrid.samp.gui;

import java.util.logging.Logger;
import org.astrogrid.samp.Client;
import org.astrogrid.samp.ErrInfo;
import org.astrogrid.samp.Message;
import org.astrogrid.samp.Response;
import org.astrogrid.samp.SampUtils;
import org.astrogrid.samp.client.HubConnection;

/**
 * ResultHandler implementation which outputs some information about 
 * responses received through the logging system.
 *
 * @author   Mark Taylor
 * @since    12 Nov 2008
 */
public class LogResultHandler implements ResultHandler {

    private final String mtype_;
    private static final Logger logger_ =
        Logger.getLogger( LogResultHandler.class.getName() );

    /**
     * Constructor.
     *
     * @param  msg  message which was sent
     */
    public LogResultHandler( Message msg ) {
        mtype_ = msg.getMType();
    }

    public void result( Client client, Response response ) {
        if ( response.isOK() ) {
            logger_.info( mtype_ + ": successful send to " + client );
        }
        else {
            logger_.warning( mtype_ + ": error sending to " + client );
            ErrInfo errInfo = response.getErrInfo();
            if ( errInfo != null ) {
                String errortxt = errInfo.getErrortxt();
                if ( errortxt != null ) {
                    logger_.warning( errortxt );
                }
                logger_.info( SampUtils.formatObject( errInfo, 3 ) );
            }
        }
    }

    public void done() {
    }
}
