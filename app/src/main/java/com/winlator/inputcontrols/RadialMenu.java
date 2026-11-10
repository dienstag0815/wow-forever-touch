package com.winlator.inputcontrols;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class RadialMenu {
    public static final int MAX_SLOTS = 12;
    public static final int MIN_SLOTS = 2;
    public static final int DEFAULT_BUTTON_SLOTS = 6;
    private static final Binding[] NUMBER_ROW_KEYS = {
            Binding.KEY_1, Binding.KEY_2, Binding.KEY_3, Binding.KEY_4, Binding.KEY_5, Binding.KEY_6,
            Binding.KEY_7, Binding.KEY_8, Binding.KEY_9, Binding.KEY_0, Binding.KEY_MINUS, Binding.KEY_EQUAL
    };
    public static final String DEFAULT_ID = "default";

    private String id = DEFAULT_ID;
    private String name = "Radial Menu";
    private final ArrayList<Slot> slots = new ArrayList<>();
    private final List<Slot> immutableSlots = Collections.unmodifiableList(slots);

    public static class Slot {
        private String label = "";
        private BindingCombo bindingCombo = BindingCombo.none();
        // Id of a WoW action (e.g. TOGGLEWORLDMAP); its current key replaces the stored one, see WowActionBindings.
        private String wowAction;
        // Chat text typed into the WoW chat when the field is chosen (Enter, text, Enter), e.g. "/dance"; no key is sent.
        private String chatCommand;

        public Slot() {
        }

        public Slot(String label, Binding binding) {
            setLabel(label);
            setBinding(binding);
        }

        public Slot(String label, BindingCombo bindingCombo) {
            setLabel(label);
            setBindingCombo(bindingCombo);
        }

        public Slot(String label, BindingCombo bindingCombo, String wowAction) {
            setLabel(label);
            setBindingCombo(bindingCombo);
            setWowAction(wowAction);
        }

        public Slot(String label, BindingCombo bindingCombo, String wowAction, String chatCommand) {
            this(label, bindingCombo, wowAction);
            setChatCommand(chatCommand);
        }

        public String getLabel() {
            return label;
        }

        public String getChatCommand() {
            return chatCommand;
        }

        public void setChatCommand(String chatCommand) {
            String trimmed = chatCommand != null ? chatCommand.trim() : "";
            this.chatCommand = trimmed.isEmpty() ? null : trimmed;
        }

        public boolean hasChatCommand() {
            return chatCommand != null;
        }

        public String getWowAction() {
            return wowAction;
        }

        public void setWowAction(String wowAction) {
            this.wowAction = wowAction != null && !wowAction.isEmpty() ? wowAction : null;
        }

        public void setLabel(String label) {
            this.label = label != null ? label : "";
        }

        public Binding getBinding() {
            return getBindingCombo().getPrimaryBinding();
        }

        public void setBinding(Binding binding) {
            this.bindingCombo = BindingCombo.of(binding);
        }

        /** The key of this field; for a WoW action the key currently set in WoW (else the stored one). */
        public BindingCombo getBindingCombo() {
            return wowAction == null ? bindingCombo : WowActionBindings.resolve(wowAction, bindingCombo);
        }

        public void setBindingCombo(BindingCombo bindingCombo) {
            this.bindingCombo = bindingCombo != null ? bindingCombo : BindingCombo.none();
        }

        public boolean isEnabled() {
            return chatCommand != null || !getBindingCombo().isEmpty();
        }

        public String getDisplayLabel() {
            if (!label.isEmpty()) return label;
            if (chatCommand != null) return chatCommand;
            return getBindingCombo().toString();
        }

        public JSONObject toJSONObject() throws JSONException {
            JSONObject object = new JSONObject();
            object.put("label", label);
            BindingCombo combo = getBindingCombo();
            object.put("binding", combo.getPrimaryBinding().name());
            if (!combo.isSingleBinding()) {
                combo.writeToJsonObject(object);
            }
            if (wowAction != null) object.put("wowAction", wowAction);
            if (chatCommand != null) object.put("chatCommand", chatCommand);
            return object;
        }

        public static Slot fromJSONObject(JSONObject object) {
            Slot slot = new Slot();
            slot.setLabel(object.optString("label", ""));
            if (object.has("bindings")) {
                slot.setBindingCombo(BindingCombo.fromJsonValue(object));
            }
            else {
                slot.setBinding(Binding.fromString(object.optString("binding", Binding.NONE.name())));
            }
            String wowAction = object.optString("wowAction", "");
            if (!wowAction.isEmpty()) slot.setWowAction(wowAction);
            slot.setChatCommand(object.optString("chatCommand", ""));
            return slot;
        }
    }

    public static RadialMenu createDefault() {
        RadialMenu menu = new RadialMenu();
        menu.addSlot(new Slot("Inventory", Binding.KEY_I));
        menu.addSlot(new Slot("Map", Binding.KEY_M));
        menu.addSlot(new Slot("Journal", Binding.KEY_J));
        menu.addSlot(new Slot("Quick Save", Binding.KEY_F5));
        menu.addSlot(new Slot("Quick Load", Binding.KEY_F9));
        menu.addSlot(new Slot("Escape", Binding.KEY_ESC));
        menu.addSlot(new Slot("Tab", Binding.KEY_TAB));
        menu.addSlot(new Slot("Keyboard", Binding.SHOW_KEYBOARD));
        return menu;
    }

    /**
     * Menu for a radial button: {@code count} fields (clamped to MIN_SLOTS..MAX_SLOTS) with the
     * number row keys 1, 2, 3 ... (10th field = 0, 11th = minus, 12th = equals). Field 0 is at the top.
     */
    public static RadialMenu createNumberKeys(String id, String name, int count) {
        RadialMenu menu = new RadialMenu();
        menu.setId(id);
        menu.setName(name);
        int clamped = Math.max(MIN_SLOTS, Math.min(MAX_SLOTS, count));
        for (int i = 0; i < clamped; i++) menu.addSlot(new Slot("", NUMBER_ROW_KEYS[i]));
        return menu;
    }

    /**
     * Number row key that continues the sequence 1, 2, 3 ... for the field at {@code index}
     * (0-based; field 6 = 7, ..., field 9 = 0, 10 = minus, 11 = equals). NONE outside 0..MAX_SLOTS-1.
     */
    public static Binding numberKeyForField(int index) {
        if (index < 0 || index >= NUMBER_ROW_KEYS.length) return Binding.NONE;
        return NUMBER_ROW_KEYS[index];
    }

    /**
     * Binding a field gets when the field count is raised: an empty field (no keys) receives the
     * number key that continues the sequence, a field that already has keys keeps them.
     */
    public static BindingCombo prefillForNewField(int index, BindingCombo existing) {
        if (existing != null && !existing.isEmpty()) return existing;
        return BindingCombo.of(numberKeyForField(index));
    }

    /**
     * Pure selection function: which field lies in the direction of the vector (dx, dy) from the menu
     * center. Screen coordinates (y points down). Field 0 is at the top, fields run clockwise.
     * Returns -1 inside the dead zone (distance &lt; deadZoneRadius) or when there are no fields.
     */
    public static int selectSlotIndex(double dx, double dy, double deadZoneRadius, int slotCount) {
        if (slotCount <= 0) return -1;
        double distance = Math.sqrt(dx * dx + dy * dy);
        if (distance < deadZoneRadius) return -1;

        double sweep = 360.0 / slotCount;
        double angle = Math.toDegrees(Math.atan2(dy, dx));
        double normalized = (angle + 90.0 + 360.0) % 360.0;
        int index = (int) ((normalized + sweep / 2.0) / sweep);
        return index % slotCount;
    }

    public RadialMenu copy() {
        JSONObject json = toJSONObject();
        RadialMenu copy = json != null ? fromJSONObject(json) : new RadialMenu();
        copy.setId(id);
        copy.setName(name);
        return copy;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id != null && !id.isEmpty() ? id : DEFAULT_ID;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name != null && !name.isEmpty() ? name : "Radial Menu";
    }

    public List<Slot> getSlots() {
        return immutableSlots;
    }

    public List<Slot> getEnabledSlots() {
        ArrayList<Slot> enabled = new ArrayList<>();
        for (Slot slot : slots) {
            if (slot.isEnabled()) enabled.add(slot);
        }
        return enabled;
    }

    public void addSlot(Slot slot) {
        if (slot != null && slots.size() < MAX_SLOTS) slots.add(slot);
    }

    public JSONObject toJSONObject() {
        try {
            JSONObject object = new JSONObject();
            object.put("id", id);
            object.put("name", name);

            JSONArray slotsJson = new JSONArray();
            for (Slot slot : slots) slotsJson.put(slot.toJSONObject());
            object.put("slots", slotsJson);
            return object;
        }
        catch (JSONException e) {
            return null;
        }
    }

    public static RadialMenu fromJSONObject(JSONObject object) {
        RadialMenu menu = new RadialMenu();
        if (object == null) return menu;

        menu.setId(object.optString("id", DEFAULT_ID));
        menu.setName(object.optString("name", "Radial Menu"));
        JSONArray slotsJson = object.optJSONArray("slots");
        if (slotsJson != null) {
            for (int i = 0; i < slotsJson.length() && i < MAX_SLOTS; i++) {
                JSONObject slotObject = slotsJson.optJSONObject(i);
                if (slotObject != null) menu.addSlot(Slot.fromJSONObject(slotObject));
            }
        }
        return menu;
    }
}
