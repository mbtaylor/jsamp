package org.astrogrid.samp.hub;

/**
 * Marker interface that identifies a hub profile.
 * Objects implementing this interface can be identified as the provider of
 * a connection to the hub.
 *
 * @author   Mark Taylor
 * @since    20 Jul 2011
 */
public interface ProfileToken {

    /**
     * Returns the name by which this token is to be identified.
     *
     * @return  profile identifier, usually one word
     */
    String getProfileName();

    /**
     * Returns a MessageRestriction object which controls what messages
     * may be sent by clients registering under ths profile.
     * If null is returned, any messages may be sent.
     *
     * @return  message restriction, or null
     */
    MessageRestriction getMessageRestriction();
}
