-- TouchForever, part 6: replacement keys for the action bars 2 to 5 (addon build 9). Own code, no Blizzard or third party addon parts.
--
-- The action bars 2 to 5 (BOTTOMLEFT, BOTTOMRIGHT, RIGHT, RIGHT 2: commands MULTIACTIONBAR1..4BUTTON1..12) have no key in WoW by
-- default, so the touch buttons of the launcher that stand for these slots could press nothing. This file gives every such action a
-- fixed key, but only while the player has no key of their own on it (the launcher reads the player's keys from bindings-cache.wtf
-- and sends the key it finds there, else this one). The keys are override bindings: not saved in the player's key bindings, set
-- again at every login, after combat and after a change of the key bindings; an action that gets a key of the player's own loses its
-- replacement key at the next pass. A key that already does something else is left alone.
--
-- The table B.KEYS is the same as app/gamenative/wowaction/ReplacementKeys.kt (the Kotlin side is the source; a unit test compares
-- the two entry by entry). Rules for the keys: no WoW default binding, no ALT-F4, nothing TouchForever uses elsewhere
-- (CTRL-SHIFT-F1..F12 in TouchForeverUI.lua / TouchForever.lua, ALT-CTRL-F1..F12 in TouchForeverUI.lua and TouchForeverContext.lua,
-- ALT-SHIFT-F1..F12 in TouchForeverContext.lua: every F key combination is taken, so digits and navigation keys are used; no ALT-CTRL
-- with digits (AltGr on some layouts), no ALT-TAB / ALT-SPACE / ALT-ENTER).
--   bar 2 (MULTIACTIONBAR1)  ALT-SHIFT-1 .. 0, -, =
--   bar 3 (MULTIACTIONBAR2)  CTRL-SHIFT-1 .. 0, -, =
--   bar 4 (MULTIACTIONBAR3)  ALT-SHIFT-INSERT, DELETE, HOME, END, PAGEUP, PAGEDOWN, UP, DOWN, LEFT, RIGHT, BACKSPACE, `
--   bar 5 (MULTIACTIONBAR4)  CTRL-SHIFT- the same twelve keys

local ADDON, ns = ...
ns = ns or {}
local B = {}
ns.Bindings = B
TouchForeverBindings = B

local NUMBER_ROW = { "1", "2", "3", "4", "5", "6", "7", "8", "9", "0", "-", "=" }
local NAVIGATION_ROW = { "INSERT", "DELETE", "HOME", "END", "PAGEUP", "PAGEDOWN", "UP", "DOWN", "LEFT", "RIGHT", "BACKSPACE", "`" }

B.PER_BAR = 12
B.BARS = 4
B.KEYS = {}      -- command -> key
B.COMMANDS = {}  -- the commands in the order of the table

for place = 1, B.PER_BAR do
    B.KEYS["MULTIACTIONBAR1BUTTON" .. place] = "ALT-SHIFT-" .. NUMBER_ROW[place]
    B.KEYS["MULTIACTIONBAR2BUTTON" .. place] = "CTRL-SHIFT-" .. NUMBER_ROW[place]
    B.KEYS["MULTIACTIONBAR3BUTTON" .. place] = "ALT-SHIFT-" .. NAVIGATION_ROW[place]
    B.KEYS["MULTIACTIONBAR4BUTTON" .. place] = "CTRL-SHIFT-" .. NAVIGATION_ROW[place]
end
for bar = 1, B.BARS do
    for place = 1, B.PER_BAR do B.COMMANDS[#B.COMMANDS + 1] = "MULTIACTIONBAR" .. bar .. "BUTTON" .. place end
end

local owner
local pending = false
local lastSet = 0
local applying = false -- our own change must not start the next pass (UPDATE_BINDINGS)

local function inCombat() return InCombatLockdown and InCombatLockdown() end

-- One pass: clear what this file set before, then give every action without a key of the player's own its replacement key.
-- Returns the number of keys set.
function B.apply()
    if inCombat() then
        pending = true
        return 0
    end
    pending = false
    if not (SetOverrideBinding and GetBindingKey and GetBindingAction) then return 0 end
    if not owner then owner = CreateFrame("Frame", "TouchForeverBindingsOwner", UIParent) end
    applying = true
    if ClearOverrideBindings then ClearOverrideBindings(owner) end
    local count = 0
    for _, command in ipairs(B.COMMANDS) do
        local key = B.KEYS[command]
        if not GetBindingKey(command) then
            local busy = GetBindingAction(key)
            if busy == nil or busy == "" then
                SetOverrideBinding(owner, false, key, command)
                count = count + 1
            end
        end
    end
    applying = false
    lastSet = count
    return count
end

function B.count() return lastSet end

local eventFrame = CreateFrame("Frame")
for _, event in ipairs({ "PLAYER_LOGIN", "PLAYER_ENTERING_WORLD", "PLAYER_REGEN_ENABLED", "UPDATE_BINDINGS" }) do
    pcall(eventFrame.RegisterEvent, eventFrame, event)
end
eventFrame:SetScript("OnEvent", function(_, event)
    if applying or (event == "PLAYER_REGEN_ENABLED" and not pending) then return end
    pcall(B.apply)
end)
