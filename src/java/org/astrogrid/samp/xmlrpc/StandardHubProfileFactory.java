package org.astrogrid.samp.xmlrpc;

import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import org.astrogrid.samp.SampUtils;
import org.astrogrid.samp.hub.HubProfile;
import org.astrogrid.samp.hub.HubProfileFactory;

/**
 * HubProfileFactory implementation for Standard Profile.
 *
 * @author   Mark Taylor
 * @since    31 Jan 2011
 */
public class StandardHubProfileFactory implements HubProfileFactory {

    private static final String secretUsage_ = "[-std:secret <secret>]";
    private static final String lockUsage_ = "[-std:httplock]";

    /**
     * Returns "std".
     */
    public String getName() {
       return "std";
    }

    public String[] getFlagsUsage() {
        return new String[] {
            secretUsage_,
            lockUsage_,
        };
    }

    public HubProfile createHubProfile( List flagList ) throws IOException {
        String secret = null;
        boolean httpLock = false;
        for ( Iterator it = flagList.iterator(); it.hasNext(); ) {
            String arg = (String) it.next();
            if ( arg.equals( "-std:secret" ) ) {
                it.remove();
                if ( it.hasNext() ) {
                    secret = (String) it.next();
                    it.remove();
                }
                else {
                    throw new IllegalArgumentException( "Usage: "
                                                      + secretUsage_ );
                }
            }
            else if ( arg.equals( "-std:httplock" ) ) {
                it.remove();
                httpLock = true;
            }
            else if ( arg.equals( "-std:nohttplock" ) ) {
                it.remove();
                httpLock = false;
            }
        }
        File lockfile = httpLock
                  ? null
                  : SampUtils.urlToFile( StandardClientProfile.getLockUrl() );
        XmlRpcKit xmlrpc = XmlRpcKit.getInstance();
        if ( secret == null ) {
            secret = StandardHubProfile.createSecret();
        }
        return new StandardHubProfile( xmlrpc.getClientFactory(),
                                       xmlrpc.getServerFactory(),
                                       lockfile, secret );
    }

    public Class getHubProfileClass() {
        return StandardHubProfile.class;
    }
}
