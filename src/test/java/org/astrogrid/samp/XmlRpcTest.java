package org.astrogrid.samp;

import junit.framework.TestCase;
import org.astrogrid.samp.xmlrpc.XmlRpcImplementation;

public class XmlRpcTest extends TestCase {

    public void testImplementations() {
        assertTrue( XmlRpcImplementation.APACHE.isAvailable() );
        assertTrue( XmlRpcImplementation.INTERNAL.isAvailable() );
        XmlRpcImplementation[] impls = XmlRpcImplementation.KNOWN_IMPLS;
        for ( int i = 0; i < impls.length; i++ ) {
            assertTrue( impls[ i ].isAvailable() );
        }
        assertEquals( XmlRpcImplementation.APACHE,
                      XmlRpcImplementation.getInstanceByName( "apache" ) );
        assertEquals( XmlRpcImplementation.INTERNAL,
                      XmlRpcImplementation.getInstanceByName( "internal" ) );
    }
}
