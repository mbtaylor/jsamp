package org.astrogrid.samp;

import java.io.IOException;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import junit.framework.TestCase;
import org.astrogrid.samp.bridge.Bridge;
import org.astrogrid.samp.client.AbstractMessageHandler;
import org.astrogrid.samp.client.ClientProfile;
import org.astrogrid.samp.client.HubConnection;
import org.astrogrid.samp.client.HubConnector;
import org.astrogrid.samp.client.ResultHandler;
import org.astrogrid.samp.client.SampException;
import org.astrogrid.samp.xmlrpc.XmlRpcKit;

public class BridgeTest extends TestCase {

    private final static String ECHO_MTYPE = "test.echo";

    public void setUp() {
        Logger.getLogger( "org.astrogrid.samp" ).setLevel( Level.WARNING );
        Logger.getLogger( "org.astrogrid.samp.xmlrpc.internal" )
              .setLevel( Level.SEVERE );
    }

    public void testBridge()
            throws IOException, SampException, InterruptedException {
        bridgeTest( 2 );
        bridgeTest( 4 );
    }

    private void bridgeTest( int nhub )
            throws IOException, SampException, InterruptedException {
        Random random = new Random( 232323 );
        XmlRpcKit kit = XmlRpcKit.getInstance();

        // Set up N hubs with one client each.
        // Clients all have similar (not identical) metadata, and all
        // subscribe to MTYPE_ECHO.
        TestClientProfile[] profiles = new TestClientProfile[ nhub ];
        HubConnector[] connectors = new HubConnector[ nhub ];
        HubConnection[] connections = new HubConnection[ nhub ];
        for ( int ih = 0; ih < nhub; ih++ ) {
            final int ih1 = ih + 1;
            final char iha = (char) ( ih + 'A' );
            TestClientProfile profile = new TestClientProfile( random, kit ) {
                public String toString() {
                    return "P." + iha;
                }
            };
            profiles[ ih ] = profile;
            profile.startHub();

            HubConnector connector = new HubConnector( profile );
            connectors[ ih ] = connector;
            Metadata meta = new Metadata();
            meta.setName( "Client " + ih1 );
            meta.put( "client.id", "C" + ih1 );
            connector.declareMetadata( meta );
            connector.addMessageHandler( new EchoMessageHandler() );
            connector.declareSubscriptions( connector.computeSubscriptions() );
            connections[ ih ] = connector.getConnection();
            assertNotNull( connections[ ih ] );
        }

        // Wait for all metadata and subscriptions.
        for ( int ih = 0; ih < nhub; ih++ ) {
            Map clientMap = connectors[ ih ].getClientMap();
            synchronized ( clientMap ) {
                while ( ! hasAtts( clientMap, true, true ) ) {
                    clientMap.wait();
                }
            }
        }

        // Check no (other) subscribed clients, one other client (the hub).
        for ( int ih = 0; ih < nhub; ih++ ) {
            HubConnection connection = connections[ ih ];
            assertEquals( 0, connection.getSubscribedClients( ECHO_MTYPE )
                                       .size() );
            assertEquals( 1, connection.getRegisteredClients().length );
        }

        // Start bridge.
        Bridge bridge = new Bridge( profiles );
        bridge.start();

        // Wait for all metadata and subscriptions from bridge start.
        for ( int ih = 0; ih < nhub; ih++ ) {
            Map clientMap = connectors[ ih ].getClientMap();
            synchronized ( clientMap ) {
                while ( ! hasAtts( clientMap, true, true ) ) {
                    clientMap.wait();
                }
            }
        }

        // Check that all proxy and bridge clients we expect to have appeared
        // are present and correct.
        for ( int ih = 0; ih < nhub; ih++ ) {
            HubConnection connection = connections[ ih ];
            Map clientMap = connectors[ ih ].getClientMap();

            assertEquals( nhub - 1,
                          connection.getSubscribedClients( ECHO_MTYPE )
                                    .size() );
            assertEquals( ( nhub - 1 ) + 2,
                          connection.getRegisteredClients().length );

            assertEquals( ( nhub - 1 ), getProxyCount( clientMap ) );
            assertTrue( hasBridge( clientMap ) );

            Set srcSet = new HashSet();
            for ( Iterator it = clientMap.values().iterator(); it.hasNext(); ) {
                Client client = (Client) it.next();
                srcSet.add( client.getMetadata().get( "bridge.proxy.source" ) );
            }
            assertTrue( srcSet.contains( null ) );
            assertEquals( nhub, srcSet.size() );
        }

        // Send one echo broadcast from each client.  This should get
        // anwered over the bridge from all the remote clients.
        EchoResultHandler[] resultHandlers = new EchoResultHandler[ nhub ];
        for ( int ih = 0; ih < nhub; ih++ ) {
            Map echoContent = new HashMap();
            echoContent.put( "text", "gur" );
            echoContent.put( "index", "ix" + ( ih + 1 ) );
            Message msg = new Message( ECHO_MTYPE, echoContent );
            resultHandlers[ ih ] = new EchoResultHandler( echoContent );
            connectors[ ih ].callAll( msg, resultHandlers[ ih ], 5 );
        }

        // Check the right number of results are in.
        for ( int ih = 0; ih < nhub; ih++ ) {
            EchoResultHandler handler = resultHandlers[ ih ];
            synchronized ( handler ) {
                while ( ! handler.isDone_ ) {
                    handler.wait();
                }
            }
            assertEquals( nhub - 1, handler.okResults_ );
        }

        // Stop the bridge.
        bridge.stop();

        // Check all clients have gone away.
        for ( int ih = 0; ih < nhub; ih++ ) {
            Map clientMap = connectors[ ih ].getClientMap();
            synchronized ( clientMap ) {
                while ( getProxyCount( clientMap ) > 0 ||
                        hasBridge( clientMap ) ) {
                    clientMap.wait();
                }
            }
            assertEquals( 2, clientMap.size() );
        }
    }

    private boolean hasAtts( Map clientMap,
                             boolean wantMeta, boolean wantSubs ) {
        for ( Iterator it = clientMap.values().iterator(); it.hasNext(); ) {
            Client client = (Client) it.next();
            if ( wantMeta ) {
                Metadata meta = client.getMetadata();
                if ( meta == null || meta.isEmpty() ) {
                    return false;
                }
            }
            if ( wantSubs ) {
                Subscriptions subs = client.getSubscriptions();
                if ( subs == null || subs.isEmpty() ) {
                    return false;
                }
            }
        }
        return true;
    }

    private int getProxyCount( Map clientMap ) {
        int nProxy = 0;
        for ( Iterator it = clientMap.values().iterator(); it.hasNext(); ) {
            Client client = (Client) it.next();
            if ( client.getMetadata().containsKey( "bridge.proxy.source" ) ) {
                nProxy++;
            }
        }
        return nProxy;
    }

    private boolean hasBridge( Map clientMap ) {
        for ( Iterator it = clientMap.values().iterator(); it.hasNext(); ) {
            Client client = (Client) it.next();
            if ( client.getMetadata().getName().equalsIgnoreCase( "bridge" ) ) {
                return true;
            }
        }
        return false;
    }

    private static class EchoMessageHandler extends AbstractMessageHandler {
        EchoMessageHandler() {
            super( ECHO_MTYPE );
        }
        public Map processCall( HubConnection conn, String senderId,
                                Message msg ) {
            return msg.getParams();
        }
    }

    private static class EchoResultHandler implements ResultHandler {
        private final Map expectedResult_;
        int okResults_;
        boolean isDone_;

        EchoResultHandler( Map expectedResult ) {
            expectedResult_ = new HashMap( expectedResult );
        }

        public synchronized void result( Client responder, Response response ) {
            if ( response.isOK() ) {
                Map result = response.getResult();
                if ( expectedResult_.equals( response.getResult() ) ) {
                    okResults_++;
                }
                else {
                    System.err.println( "echo mismatch" );
                }
            }
            else {
                System.err.println( "echo error response" );
            }
        }

        public synchronized void done() {
            isDone_ = true;
            notifyAll();
        }
    }
}
