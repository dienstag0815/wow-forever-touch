package com.winlator.inputcontrols;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Current keys of the WoW actions ("wowAction" ids such as TOGGLEWORLDMAP), resolved from the player's own WoW key
 * bindings when the game is started. A radial field or button that stores a WoW action takes the key from here, so a key
 * changed inside WoW is picked up without editing the layout. Without an entry (not resolved yet, action unbound) the
 * key stored in the profile stays in effect. Pure Java, unit tested.
 */
public final class WowActionBindings {
    private static volatile Map<String, BindingCombo> resolved = Collections.emptyMap();

    private WowActionBindings() {}

    /** Replaces the resolved keys (action id -> key combination). Null clears them. */
    public static void setResolved(Map<String, BindingCombo> keys) {
        resolved = keys == null ? Collections.<String, BindingCombo>emptyMap() : new HashMap<>(keys);
    }

    /** Key of [actionId] now, or [stored] when the action is empty/unknown or has no resolved key. */
    public static BindingCombo resolve(String actionId, BindingCombo stored) {
        if (actionId == null || actionId.isEmpty()) return stored;
        BindingCombo current = resolved.get(actionId);
        if (current == null || current.isEmpty()) return stored;
        return current;
    }
}
