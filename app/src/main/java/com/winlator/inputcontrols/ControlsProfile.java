package com.winlator.inputcontrols;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import com.winlator.core.FileUtils;
import com.winlator.widget.InputControlsView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class ControlsProfile implements Comparable<ControlsProfile> {
    public final int id;
    private String name;
    private float cursorSpeed = 1.0f;
    private final ArrayList<ControlElement> elements = new ArrayList<>();
    private final ArrayList<ExternalController> controllers = new ArrayList<>();
    private final ArrayList<RadialMenu> radialMenus = new ArrayList<>();
    private final List<ControlElement> immutableElements = Collections.unmodifiableList(elements);
    private boolean elementsLoaded = false;
    private boolean controllersLoaded = false;
    private boolean radialMenusLoaded = false;
    private boolean virtualGamepad = false;
    private final Context context;
    private GamepadState gamepadState;

    public ControlsProfile(Context context, int id) {
        this.context = context;
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public float getCursorSpeed() {
        return cursorSpeed;
    }

    public void setCursorSpeed(float cursorSpeed) {
        this.cursorSpeed = cursorSpeed;
    }

    public boolean isVirtualGamepad() {
        return virtualGamepad;
    }

    public void setVirtualGamepad(boolean isVirtualGamepad) {
        virtualGamepad = isVirtualGamepad;
    }

    public GamepadState getGamepadState() {
        if (gamepadState == null) gamepadState = new GamepadState();
        return gamepadState;
    }

    public ExternalController addController(String id) {
        ExternalController controller = getController(id);
        if (controller == null) {
            controller = new ExternalController();
            controller.setId(id);
            controller.setName("Physical Controller");
            controllers.add(controller);
        }
        controllersLoaded = true;
        return controller;
    }

    public void removeController(ExternalController controller) {
        if (!controllersLoaded) loadControllers();
        controllers.remove(controller);
    }

    public ExternalController getController(String id) {
        if (!controllersLoaded) loadControllers();
        for (ExternalController controller : controllers) if (controller.getId().equals(id)) return controller;
        return null;
    }

    public ExternalController getController(int deviceId) {
        if (!controllersLoaded) loadControllers();

        // First try exact device ID match
        for (ExternalController controller : controllers) {
            if (controller.getDeviceId() == deviceId) return controller;
        }

        // Fall back to wildcard controller if no exact match
        for (ExternalController controller : controllers) {
            if (controller.getId().equals("*")) return controller;
        }

        return null;
    }

    public ArrayList<ExternalController> getControllers() {
        if (!controllersLoaded) loadControllers();
        return new ArrayList<>(controllers);
    }

    @NonNull
    @Override
    public String toString() {
        return name;
    }

    @Override
    public int compareTo(ControlsProfile o) {
        return Integer.compare(id, o.id);
    }

    public boolean isElementsLoaded() {
        return elementsLoaded;
    }

    public void save() {
        File file = getProfileFile(context, id);
        Log.d("ControlsProfile", "Saving profile: " + name + " (ID: " + id + ") to " + file.getAbsolutePath());

        try {
            JSONObject data = new JSONObject();
            data.put("id", id);
            data.put("name", name);
            data.put("cursorSpeed", Float.valueOf(cursorSpeed));

            JSONArray elementsJSONArray = new JSONArray();
            if (!elementsLoaded && file.isFile()) {
                JSONObject profileJSONObject = new JSONObject(FileUtils.readString(file));
                elementsJSONArray = profileJSONObject.getJSONArray("elements");
            }
            else for (ControlElement element : elements) elementsJSONArray.put(element.toJSONObject());
            data.put("elements", elementsJSONArray);

            JSONArray controllersJSONArray = new JSONArray();
            if (!controllersLoaded && file.isFile()) {
                JSONObject profileJSONObject = new JSONObject(FileUtils.readString(file));
                if (profileJSONObject.has("controllers")) controllersJSONArray = profileJSONObject.getJSONArray("controllers");
            }
            else {
                for (ExternalController controller : controllers) {
                    JSONObject controllerJSONObject = controller.toJSONObject();
                    if (controllerJSONObject != null) controllersJSONArray.put(controllerJSONObject);
                }
            }
            if (controllersJSONArray.length() > 0) data.put("controllers", controllersJSONArray);

            JSONArray radialMenusJSONArray = new JSONArray();
            if (!radialMenusLoaded && file.isFile()) {
                JSONObject profileJSONObject = new JSONObject(FileUtils.readString(file));
                JSONArray storedRadialMenus = profileJSONObject.optJSONArray("radialMenus");
                if (storedRadialMenus != null) radialMenusJSONArray = storedRadialMenus;
            }
            else {
                for (RadialMenu menu : radialMenus) {
                    JSONObject menuJSONObject = menu.toJSONObject();
                    if (menuJSONObject != null) radialMenusJSONArray.put(menuJSONObject);
                }
            }
            if (radialMenusJSONArray.length() > 0) data.put("radialMenus", radialMenusJSONArray);

            FileUtils.writeString(file, data.toString());
            Log.d("ControlsProfile", "Profile saved successfully: " + name + " (controllers: " + controllersJSONArray.length() + ", elements: " + elementsJSONArray.length() + ")");
        }
        catch (JSONException e) {
            Log.e("ControlsProfile", "Failed to save profile: " + name + " (ID: " + id + ")", e);
        }
    }

    public static File getProfileFile(Context context, int id) {
        return new File(InputControlsManager.getProfilesDir(context), "controls-"+id+".icp");
    }

    public void addElement(ControlElement element) {
        elements.add(element);
        elementsLoaded = true;
    }

    public void removeElement(ControlElement element) {
        elements.remove(element);
        elementsLoaded = true;
    }

    public List<ControlElement> getElements() {
        return immutableElements;
    }

    public RadialMenu getDefaultRadialMenu() {
        if (!radialMenusLoaded) loadRadialMenus();
        if (radialMenus.isEmpty()) {
            radialMenus.add(RadialMenu.createDefault());
            radialMenusLoaded = true;
        }
        return radialMenus.get(0);
    }

    public void setDefaultRadialMenu(RadialMenu menu) {
        if (!radialMenusLoaded) loadRadialMenus();
        RadialMenu next = menu != null ? menu : RadialMenu.createDefault();
        // Index 0 is the default menu; menus of radial buttons (index 1..) stay untouched.
        if (radialMenus.isEmpty()) radialMenus.add(next);
        else radialMenus.set(0, next);
        radialMenusLoaded = true;
    }

    /** All menus of this profile; index 0 is the default menu. */
    public List<RadialMenu> getRadialMenus() {
        getDefaultRadialMenu();
        return new ArrayList<>(radialMenus);
    }

    /** Replaces all menus; the first one becomes the default menu. */
    public void setRadialMenus(List<RadialMenu> menus) {
        if (!radialMenusLoaded) loadRadialMenus();
        radialMenus.clear();
        if (menus != null) {
            for (RadialMenu menu : menus) {
                if (menu != null) radialMenus.add(menu);
            }
        }
        if (radialMenus.isEmpty()) radialMenus.add(RadialMenu.createDefault());
        radialMenusLoaded = true;
    }

    /** Menu with this id, or null if the id is empty or unknown. */
    public RadialMenu findRadialMenu(String menuId) {
        if (menuId == null || menuId.isEmpty()) return null;
        getDefaultRadialMenu();
        for (RadialMenu menu : radialMenus) {
            if (menuId.equals(menu.getId())) return menu;
        }
        return null;
    }

    /** Menu of a radial button: its own menu, or the default menu for an empty or unknown id. */
    public RadialMenu getRadialMenu(String menuId) {
        RadialMenu menu = findRadialMenu(menuId);
        return menu != null ? menu : getDefaultRadialMenu();
    }

    /** Replaces the menu with the same id, or appends it. */
    public void putRadialMenu(RadialMenu menu) {
        if (menu == null) return;
        getDefaultRadialMenu();
        for (int i = 0; i < radialMenus.size(); i++) {
            if (radialMenus.get(i).getId().equals(menu.getId())) {
                radialMenus.set(i, menu);
                return;
            }
        }
        radialMenus.add(menu);
    }

    /** Removes a radial-button menu; the default menu (index 0) can not be removed. */
    public boolean removeRadialMenu(String menuId) {
        if (menuId == null || menuId.isEmpty()) return false;
        getDefaultRadialMenu();
        for (int i = 1; i < radialMenus.size(); i++) {
            if (menuId.equals(radialMenus.get(i).getId())) {
                radialMenus.remove(i);
                return true;
            }
        }
        return false;
    }

    /** Adds a new menu for a radial button (number keys 1..count) and returns it. */
    public RadialMenu createButtonRadialMenu(int slotCount) {
        getDefaultRadialMenu();
        int number = 1;
        String menuId;
        do {
            menuId = "button-" + number++;
        } while (findRadialMenu(menuId) != null);
        RadialMenu menu = RadialMenu.createNumberKeys(menuId, "Radial button", slotCount);
        radialMenus.add(menu);
        return menu;
    }

    public boolean isTemplate() {
        return name.toLowerCase(Locale.ENGLISH).contains("template");
    }

    public ArrayList<RadialMenu> loadRadialMenus() {
        radialMenus.clear();
        radialMenusLoaded = false;

        File file = getProfileFile(context, id);
        if (!file.isFile()) {
            radialMenus.add(RadialMenu.createDefault());
            radialMenusLoaded = true;
            return radialMenus;
        }

        try {
            JSONObject profileJSONObject = new JSONObject(FileUtils.readString(file));
            JSONArray radialMenusJSONArray = profileJSONObject.optJSONArray("radialMenus");
            if (radialMenusJSONArray == null || radialMenusJSONArray.length() == 0) {
                radialMenus.add(RadialMenu.createDefault());
            }
            else {
                for (int i = 0; i < radialMenusJSONArray.length(); i++) {
                    JSONObject radialMenuJSONObject = radialMenusJSONArray.optJSONObject(i);
                    if (radialMenuJSONObject != null) radialMenus.add(RadialMenu.fromJSONObject(radialMenuJSONObject));
                }
                if (radialMenus.isEmpty()) radialMenus.add(RadialMenu.createDefault());
            }
            radialMenusLoaded = true;
        }
        catch (JSONException e) {
            Log.e("ControlsProfile", "Failed to load radial menus for profile: " + name + " (ID: " + id + ")", e);
            radialMenus.add(RadialMenu.createDefault());
            radialMenusLoaded = true;
        }

        return radialMenus;
    }

    public ArrayList<ExternalController> loadControllers() {
        controllers.clear();
        controllersLoaded = false;

        File file = getProfileFile(context, id);
        Log.d("ControlsProfile", "Loading controllers for profile: " + name + " (ID: " + id + ") from " + file.getAbsolutePath());

        if (!file.isFile()) {
            Log.d("ControlsProfile", "Profile file does not exist: " + name);
            return controllers;
        }

        try {
            JSONObject profileJSONObject = new JSONObject(FileUtils.readString(file));
            if (!profileJSONObject.has("controllers")) {
                Log.d("ControlsProfile", "No controllers section in profile: " + name);
                return controllers;
            }
            JSONArray controllersJSONArray = profileJSONObject.getJSONArray("controllers");
            for (int i = 0; i < controllersJSONArray.length(); i++) {
                JSONObject controllerJSONObject = controllersJSONArray.getJSONObject(i);
                String id = controllerJSONObject.getString("id");
                ExternalController controller = new ExternalController();
                controller.setId(id);
                controller.setName(controllerJSONObject.getString("name"));

                JSONArray controllerBindingsJSONArray = controllerJSONObject.getJSONArray("controllerBindings");
                for (int j = 0; j < controllerBindingsJSONArray.length(); j++) {
                    JSONObject controllerBindingJSONObject = controllerBindingsJSONArray.getJSONObject(j);
                    ExternalControllerBinding controllerBinding = new ExternalControllerBinding();
                    controllerBinding.setKeyCode(controllerBindingJSONObject.getInt("keyCode"));
                    if (controllerBindingJSONObject.has("bindings")) {
                        controllerBinding.setBindingCombo(BindingCombo.fromJsonValue(controllerBindingJSONObject));
                    }
                    else {
                        controllerBinding.setBinding(Binding.fromString(controllerBindingJSONObject.getString("binding")));
                    }
                    controller.addControllerBinding(controllerBinding);
                }
                controllers.add(controller);
            }
            controllersLoaded = true;
            Log.d("ControlsProfile", "Loaded " + controllers.size() + " controllers for profile: " + name);
        }
        catch (JSONException e) {
            Log.e("ControlsProfile", "Failed to load controllers for profile: " + name + " (ID: " + id + ")", e);
            e.printStackTrace();
        }
        return controllers;
    }

    public void loadElements(InputControlsView inputControlsView) {
        elements.clear();
        elementsLoaded = false;
        virtualGamepad = false;

        // Check if view has valid dimensions before loading
        if (inputControlsView.getMaxWidth() == 0 || inputControlsView.getMaxHeight() == 0) {
            Log.w("ControlsProfile", "Cannot load elements - view has no dimensions yet (width: " +
                inputControlsView.getWidth() + ", height: " + inputControlsView.getHeight() + ")");
            return;
        }

        File file = getProfileFile(context, id);
        Log.d("ControlsProfile", "Loading elements for profile: " + name + " (ID: " + id + ") from " + file.getAbsolutePath());

        if (!file.isFile()) {
            Log.d("ControlsProfile", "Profile file does not exist: " + name);
            return;
        }

        try {
            JSONObject profileJSONObject = new JSONObject(FileUtils.readString(file));
            JSONArray elementsJSONArray = profileJSONObject.getJSONArray("elements");
            for (int i = 0; i < elementsJSONArray.length(); i++) {
                JSONObject elementJSONObject = elementsJSONArray.getJSONObject(i);
                ControlElement element = new ControlElement(inputControlsView);
                try {
                    element.setType(ControlElement.Type.valueOf(elementJSONObject.getString("type")));
                } catch (IllegalArgumentException e) {
                    Log.w("ControlsProfile", "Skipping element with unknown type: " + elementJSONObject.getString("type"));
                    continue;
                }
                if (elementJSONObject.has("lookThrough")) {
                    element.setLookThroughSetting(elementJSONObject.getBoolean("lookThrough"));
                }
                else {
                    element.setLookThroughSetting(null);
                }
                element.setShape(ControlElement.Shape.valueOf(elementJSONObject.getString("shape")));
                element.setToggleSwitch(elementJSONObject.getBoolean("toggleSwitch"));
                element.setX((int)(elementJSONObject.getDouble("x") * inputControlsView.getMaxWidth()));
                element.setY((int)(elementJSONObject.getDouble("y") * inputControlsView.getMaxHeight()));
                element.setScale((float)elementJSONObject.getDouble("scale"));
                element.setText(elementJSONObject.getString("text"));
                element.setIconId(elementJSONObject.getInt("iconId"));
                if (elementJSONObject.has("range")) {
                    try {
                        element.setRange(ControlElement.Range.valueOf(elementJSONObject.getString("range")));
                    } catch (IllegalArgumentException e) {
                        // unknown range name (newer or damaged file): the strip keeps the default A-Z
                        Log.w("ControlsProfile", "Unknown range: " + elementJSONObject.getString("range"));
                    }
                }
                if (elementJSONObject.has("orientation")) element.setOrientation((byte)elementJSONObject.getInt("orientation"));
                if (elementJSONObject.has("scrollLocked")) element.setScrollLocked(elementJSONObject.getBoolean("scrollLocked"));

                if (elementJSONObject.has("shooterMovementType")) element.setShooterMovementType(elementJSONObject.getString("shooterMovementType"));
                if (elementJSONObject.has("shooterLookType")) element.setShooterLookType(elementJSONObject.getString("shooterLookType"));
                if (elementJSONObject.has("shooterLookSensitivity")) element.setShooterLookSensitivity((float)elementJSONObject.getDouble("shooterLookSensitivity"));
                if (elementJSONObject.has("shooterJoystickSize")) element.setShooterJoystickSize((float)elementJSONObject.getDouble("shooterJoystickSize"));
                if (elementJSONObject.has("buttonColor")) {
                    element.setButtonColor(ControlElement.parseRgbColor(elementJSONObject.get("buttonColor"), ControlElement.DEFAULT_BUTTON_COLOR));
                }
                if (elementJSONObject.has("buttonActiveColor")) {
                    element.setButtonActiveColor(ControlElement.parseRgbColor(elementJSONObject.get("buttonActiveColor"), ControlElement.DEFAULT_BUTTON_ACTIVE_COLOR), true);
                }
                if (elementJSONObject.has("buttonOpacity")) element.setButtonOpacity((float)elementJSONObject.getDouble("buttonOpacity"));
                if (elementJSONObject.has("buttonStrokeScale")) element.setButtonStrokeScale((float)elementJSONObject.getDouble("buttonStrokeScale"));
                if (elementJSONObject.has("shooterLookThrough")) element.setShooterLookThrough(elementJSONObject.getBoolean("shooterLookThrough"));
                if (elementJSONObject.has("radialMenuId")) element.setRadialMenuId(elementJSONObject.optString("radialMenuId", null));
                // Free shape, scroll wheel and key ring (all optional, missing = old behaviour)
                element.readExtrasFromJson(elementJSONObject);

                boolean hasGamepadBinding = true;
                JSONArray bindingsJSONArray = elementJSONObject.getJSONArray("bindings");
                // A key strip stores its number of visible keys here, so it may have fewer than four; everything else has four slots.
                int minBindings = element.getType() == ControlElement.Type.RANGE_BUTTON ? 1 : 4;
                element.setBindingCount(Math.max(bindingsJSONArray.length(), minBindings));
                for (int j = 0; j < bindingsJSONArray.length(); j++) {
                    BindingCombo binding = BindingCombo.fromJsonValue(bindingsJSONArray.get(j));
                    element.setBindingComboAt(j, binding);
                    if (!binding.isGamepadOnly()) hasGamepadBinding = false;
                }
                // Free key strip: never more visible keys than entries; without entries (damaged file) back to A-Z.
                element.normalizeCustomRange();
                element.clampRangeVisible();
                if (elementJSONObject.has("wowAction")) {
                    // The key of a WoW action follows the player's key bindings in WoW (resolved when the game starts).
                    element.setWowAction(elementJSONObject.optString("wowAction", ""));
                    element.applyWowActionBinding();
                }

                if (elementJSONObject.has("chatCommand")) {
                    element.setChatCommand(elementJSONObject.optString("chatCommand", ""));
                }

                if (!virtualGamepad && (hasGamepadBinding || element.hasRingGamepadBinding())) virtualGamepad = true;
                elements.add(element);
            }
            elementsLoaded = true;
            Log.d("ControlsProfile", "Loaded " + elements.size() + " elements for profile: " + name + " (virtualGamepad: " + virtualGamepad + ")");
        }
        catch (JSONException e) {
            Log.e("ControlsProfile", "Failed to load elements for profile: " + name + " (ID: " + id + ")", e);
            e.printStackTrace();
        }
    }
}
