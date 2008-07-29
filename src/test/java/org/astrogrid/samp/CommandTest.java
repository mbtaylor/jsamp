package org.astrogrid.samp;

import junit.framework.TestCase;

public class CommandTest extends TestCase {

    public CommandTest( String name ) {
        super( name );
    }

    public void testCommands() throws Exception {
        String[] cmdClasses = Samp.COMMAND_CLASSES;
        for ( int i = 0; i < cmdClasses.length; i++ ) {
            String className = cmdClasses[ i ];
            Class clazz = Class.forName( className );
            assertNotNull( Samp.getMainMethod( clazz ) );
        }
    }
}
