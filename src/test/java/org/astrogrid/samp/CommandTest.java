package org.astrogrid.samp;

import junit.framework.TestCase;

public class CommandTest extends TestCase {

    public CommandTest( String name ) {
        super( name );
    }

    public void testCommands() throws Exception {
        String[] cmdClasses = JSamp.COMMAND_CLASSES;
        for ( int i = 0; i < cmdClasses.length; i++ ) {
            String className = cmdClasses[ i ];
            Class clazz = Class.forName( className );
            assertNotNull( JSamp.getMainMethod( clazz ) );
        }
    }

    public void testVersion() {
        assertTrue( SampUtils.getSampVersion().trim().charAt( 0 ) != '?' );
        assertTrue( SampUtils.getSoftwareVersion().trim().charAt( 0 ) != '?' );
    }
}
