package org.astrogrid.samp.xmlrpc;

import junit.framework.TestCase;

public class XmlRpcTest extends TestCase {

    public void testImplementations() {
        assertTrue( XmlRpcKit.APACHE.isAvailable() );
        assertTrue( XmlRpcKit.INTERNAL.isAvailable() );
        XmlRpcKit[] impls = XmlRpcKit.KNOWN_IMPLS;
        for ( int i = 0; i < impls.length; i++ ) {
            assertTrue( impls[ i ].isAvailable() );
        }
        assertEquals( XmlRpcKit.APACHE,
                      XmlRpcKit.getInstanceByName( "apache" ) );
        assertEquals( XmlRpcKit.INTERNAL,
                      XmlRpcKit.getInstanceByName( "internal" ) );
    }
}
