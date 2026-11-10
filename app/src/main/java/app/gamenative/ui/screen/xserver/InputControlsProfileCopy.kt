package app.gamenative.ui.screen.xserver

import com.winlator.inputcontrols.ControlElement
import com.winlator.inputcontrols.ControlsProfile
import com.winlator.inputcontrols.RadialMenu
import com.winlator.widget.InputControlsView

internal fun copyInputControlsProfileElements(
    sourceProfile: ControlsProfile,
    targetProfile: ControlsProfile,
    view: InputControlsView
) {
    sourceProfile.loadElements(view)
    targetProfile.elements.toList().forEach(targetProfile::removeElement)
    sourceProfile.elements.forEach { targetProfile.addElement(it.copyForView(view)) }
    // Index 0 is the default menu, the following ones belong to radial buttons (ControlElement.radialMenuId).
    val radialMenus = mutableListOf(sourceProfile.defaultRadialMenu.deepCopy())
    sourceProfile.radialMenus.drop(1).forEach { radialMenus.add(it.deepCopy()) }
    targetProfile.setRadialMenus(radialMenus)
    view.onControlsProfileContentChanged(true)
    view.invalidate()
}

private fun RadialMenu.deepCopy(): RadialMenu {
    return copy()
}

private fun ControlElement.copyForView(view: InputControlsView) = ControlElement(view).also { newElement ->
    newElement.setType(type)
    newElement.setShape(shape)
    newElement.setX(x.toInt())
    newElement.setY(y.toInt())
    newElement.setScale(scale)
    newElement.setText(text)
    newElement.setIconId(iconId.toInt())
    newElement.setToggleSwitch(isToggleSwitch)
    newElement.copyButtonAppearanceFrom(this)
    newElement.radialMenuId = radialMenuId
    newElement.wowAction = wowAction
    newElement.chatCommand = chatCommand
    // Free shape (width/height, corner, arc), scroll wheel settings and the key ring of a stick
    newElement.copyExtrasFrom(this)
    if (type == ControlElement.Type.SCROLL_WHEEL) newElement.setOrientation(orientation)

    if (type == ControlElement.Type.RANGE_BUTTON) {
        newElement.setRange(range)
        newElement.setOrientation(orientation)
        newElement.setBindingCount(bindingCount)
        newElement.isScrollLocked = isScrollLocked
    }

    for (i in 0 until bindingCount) {
        newElement.setBindingComboAt(i, getBindingComboAt(i))
    }

    if (type == ControlElement.Type.SHOOTER_MODE) {
        newElement.shooterMovementType = shooterMovementType
        newElement.shooterLookType = shooterLookType
        newElement.shooterLookSensitivity = shooterLookSensitivity
        newElement.shooterJoystickSize = shooterJoystickSize
    }
}
