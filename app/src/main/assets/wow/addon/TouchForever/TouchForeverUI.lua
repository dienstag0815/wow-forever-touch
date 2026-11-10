-- TouchForever, part 2: a quiet, touch friendly interface. Own code, no Blizzard or third party addon parts.
--   * action bars: hide / show / peek (alpha + mouse off, the key bindings keep working), shown by itself while the
--     spellbook, the macro window, the collections or the edit mode is open or while an ability is held on the cursor
--   * chat: smaller font, fewer lines, transparent background, spam out of the main window, short channel tags, no
--     time stamps, "compact" mode (fades after rest, wakes up on whisper / group / guild), hide / show
--   * chat "compact": the buttons beside the chat (menu, channels, social "little man", voice, scroll buttons) are taken
--     away and their space goes to the chat; the tabs show only while the chat is touched
--   * minimap and quest tracker: hide / show; "peek all" shows everything for a few seconds
--   * minimap shape: round (preset) or square (square mask, no ring and header strip, the small items on the corners)
--   * status bars (experience, reputation, honor ...): standard / compact (percent of the screen width) / under the minimap / off
--   * minimap at the corner: one nudge moves the map to the top right corner of the screen (small gap, same size) and the bag bar
--     below it follows; Edit Mode is left alone (clamp opened so that the map can be dragged to the corner); an own place far away stays
--   * menu bar (the classic micro menu): hide / show (the radial menu "WoW-Menü komplett" replaces it)
--   * bag bar in three steps: whole / backpack + ammo bag only (default) / off
-- Key bindings live in Bindings.xml (category "TouchForever"). Slash commands: /tf help
-- Combat lockdown: protected frames are never shown / hidden / moved here. In combat only SetAlpha runs (allowed),
-- the rest (mouse switch, minimap / tracker) is caught up on PLAYER_REGEN_ENABLED.

local ADDON, ns = ...
local UI = {}
ns = ns or {}
ns.UI = UI
TouchForeverUI = UI -- global name used by Bindings.xml

local PEEK_SECONDS = 8
local CHAT_IDLE_SECONDS = 10
local LATE = 0.05

local L = ns.L

-- Standard keys (set once per command at login when the command has no key and the key is free).
UI.COMMANDS = {
    "WFB_TOGGLEBARS", "WFB_PEEKBARS", "WFB_TOGGLECHAT", "WFB_TOGGLEMINIMAP", "WFB_TOGGLEQUESTS", "WFB_PEEKALL", "WFB_OPTIONS",
    "WFB_TOGGLEMENUBAR", "WFB_CHANNELS", "WFB_CHATMENU", "WFB_HOUSING",
}
UI.DEFAULT_KEYS = {
    WFB_TOGGLEBARS = "CTRL-SHIFT-F5",
    WFB_PEEKBARS = "CTRL-SHIFT-F6",
    WFB_TOGGLECHAT = "CTRL-SHIFT-F7",
    WFB_TOGGLEMINIMAP = "CTRL-SHIFT-F8",
    WFB_TOGGLEQUESTS = "CTRL-SHIFT-F9",
    WFB_PEEKALL = "CTRL-SHIFT-F10",
    WFB_OPTIONS = "CTRL-SHIFT-F11",
    WFB_TOGGLEMENUBAR = "CTRL-SHIFT-F12",
    WFB_CHANNELS = "CTRL-SHIFT-F1",
    WFB_CHATMENU = "CTRL-SHIFT-F2",
    WFB_HOUSING = "CTRL-SHIFT-F3",
}

-- Windows of the game with an own binding but no key we can rely on (reputation, currencies, PvP, raid): the radial menu
-- "WoW-Menü Rest" sends these keys. They are set once at login, as a key IN ADDITION to what the player has (a command can have two).
UI.EXTRA_COMMANDS = { "TOGGLECHARACTER2", "TOGGLECURRENCY", "TOGGLECHARACTER4", "TOGGLERAIDTAB" }
UI.EXTRA_KEYS = {
    TOGGLECHARACTER2 = "ALT-CTRL-F1",
    TOGGLECURRENCY = "ALT-CTRL-F2",
    TOGGLECHARACTER4 = "ALT-CTRL-F3",
    TOGGLERAIDTAB = "ALT-CTRL-F4",
}

-- Names shown in WoW's key bindings window.
BINDING_HEADER_WOWFOREVER = "TouchForever"
BINDING_CATEGORY_WOWFOREVER = "TouchForever"
BINDING_NAME_WFB_TOGGLEBARS = L.action_bars_on_off
BINDING_NAME_WFB_PEEKBARS = L.peek_at_the_action_bars
BINDING_NAME_WFB_TOGGLECHAT = L.chat_on_off
BINDING_NAME_WFB_TOGGLEMINIMAP = L.minimap_on_off
BINDING_NAME_WFB_TOGGLEQUESTS = L.quest_tracker_on_off
BINDING_NAME_WFB_PEEKALL = L.peek_at_everything
BINDING_NAME_WFB_OPTIONS = L.addon_settings
BINDING_NAME_WFB_TOGGLEMENUBAR = L.menu_bar_and_bag_bar_on
BINDING_NAME_WFB_CHANNELS = L.chat_channels
BINDING_NAME_WFB_CHATMENU = L.chat_menu
BINDING_NAME_WFB_HOUSING = L.housing_dashboard

local db                       -- TouchForeverDB.ui
local peekUntil = { bars = 0, chat = 0, minimap = 0, quests = 0, xp = 0, menu = 0 }
local reasons = {}             -- reason -> true (edit mode, spellbook, cursor ...): bars stay visible
local chatActiveUntil = 0
local pendingCombat = false    -- something could not be done in combat
local started = false

local function now() return GetTime and GetTime() or 0 end
local function inCombat() return InCombatLockdown and InCombatLockdown() end
local function later(seconds, fn) C_Timer.After(seconds + LATE, fn) end

local function frameOf(name)
    local frame = _G[name]
    if type(frame) == "table" and frame.GetObjectType then return frame end
end

local function say(text)
    print("|cffffd100TouchForever:|r " .. text)
end

-- Short message in the middle of the screen: works even while the chat is hidden.
local function notice(text)
    if UIErrorsFrame and UIErrorsFrame.AddMessage then
        UIErrorsFrame:AddMessage("TouchForever: " .. text, 1, 0.82, 0)
    else
        say(text)
    end
end

---------------------------------------------------------------------------
-- A group of frames that can be made invisible (alpha) and click-through (mouse off).
-- Blizzard code that sets the alpha meanwhile is caught by a post hook and remembered, so the
-- frame returns to what Blizzard wanted when the group is shown again.
---------------------------------------------------------------------------
local function newGroup(namesFn, mouseNamesFn)
    local group = { factor = 1, saved = {}, mouse = {}, hooked = {}, guard = false }

    local function hook(frame)
        if group.hooked[frame] or not frame.SetAlpha then return end
        group.hooked[frame] = true
        pcall(hooksecurefunc, frame, "SetAlpha", function(self, alpha)
            if group.guard or group.factor >= 1 or type(alpha) ~= "number" then return end
            group.saved[self] = alpha
            group.guard = true
            pcall(self.SetAlpha, self, alpha * group.factor)
            group.guard = false
        end)
    end

    function group:setFactor(factor)
        self.factor = factor
        for _, name in ipairs(namesFn()) do
            local frame = frameOf(name)
            if frame and frame.SetAlpha then
                hook(frame)
                self.guard = true
                if factor < 1 then
                    if self.saved[frame] == nil then self.saved[frame] = frame:GetAlpha() or 1 end
                    pcall(frame.SetAlpha, frame, self.saved[frame] * factor)
                elseif self.saved[frame] ~= nil then
                    pcall(frame.SetAlpha, frame, self.saved[frame])
                    self.saved[frame] = nil
                end
                self.guard = false
            end
        end
    end

    -- Click-through while invisible. Not in combat (EnableMouse is restricted on protected frames); returns false then.
    function group:setMouse(enabled)
        if inCombat() then return false end
        for _, name in ipairs((mouseNamesFn or namesFn)()) do
            local frame = frameOf(name)
            if frame and frame.EnableMouse and frame.IsMouseEnabled then
                if enabled then
                    local was = self.mouse[frame]
                    if was ~= nil then
                        pcall(frame.EnableMouse, frame, was)
                        self.mouse[frame] = nil
                    end
                else
                    if self.mouse[frame] == nil then self.mouse[frame] = frame:IsMouseEnabled() and true or false end
                    pcall(frame.EnableMouse, frame, false)
                end
            end
        end
        return true
    end

    return group
end

---------------------------------------------------------------------------
-- Action bars
---------------------------------------------------------------------------
local BAR_FRAMES = {
    "MainActionBar", "MainMenuBar", "MultiBarBottomLeft", "MultiBarBottomRight", "MultiBarRight", "MultiBarLeft",
    "MultiBar5", "MultiBar6", "MultiBar7", "StanceBar", "PetActionBar", "PossessActionBar", "StatusTrackingBarManager",
    "MultiCastActionBarFrame",
}
local BUTTON_PREFIXES = {
    { "ActionButton", 12 }, { "MultiBarBottomLeftButton", 12 }, { "MultiBarBottomRightButton", 12 },
    { "MultiBarRightButton", 12 }, { "MultiBarLeftButton", 12 }, { "MultiBar5Button", 12 }, { "MultiBar6Button", 12 },
    { "MultiBar7Button", 12 }, { "StanceButton", 10 }, { "PetActionButton", 10 }, { "PossessButton", 2 },
}

local barButtonNames
local function barMouseNames()
    if not barButtonNames then
        barButtonNames = { "ActionBarUpButton", "ActionBarDownButton" }
        for _, entry in ipairs(BUTTON_PREFIXES) do
            for index = 1, entry[2] do barButtonNames[#barButtonNames + 1] = entry[1] .. index end
        end
    end
    return barButtonNames
end

local barGroup = newGroup(function() return BAR_FRAMES end, barMouseNames)

local function barsWanted()
    if not db.barsHidden then return true end
    if now() < peekUntil.bars then return true end
    return next(reasons) ~= nil
end

local function applyBars()
    if not db then return end
    local want = barsWanted()
    barGroup:setFactor(want and 1 or 0)
    if not barGroup:setMouse(want) then pendingCombat = true end
end

---------------------------------------------------------------------------
-- Minimap and quest tracker: Hide / Show (not protected), a hook keeps Blizzard from showing them again
---------------------------------------------------------------------------
local function newHideable(names, dbKey, peekKey, forceFn)
    local item = { hiddenByUs = {}, hooked = {} }

    -- dbKey: name of a flag in the saved data, or a function that says "hidden"; forceFn: shows it anyway (edit mode)
    function item.wanted()
        local hidden
        if type(dbKey) == "function" then hidden = dbKey() else hidden = db[dbKey] end
        return not hidden or now() < peekUntil[peekKey] or (forceFn ~= nil and forceFn() == true)
    end

    function item.apply()
        if not db then return end
        local want = item.wanted()
        for _, name in ipairs(names) do
            local frame = frameOf(name)
            if frame and frame.Hide and frame.Show then
                if not item.hooked[frame] then
                    item.hooked[frame] = true
                    pcall(hooksecurefunc, frame, "Show", function(self)
                        if not item.wanted() then
                            item.hiddenByUs[self] = true
                            local ok = pcall(self.Hide, self)
                            if not ok then pendingCombat = true end
                        end
                    end)
                end
                if want then
                    if item.hiddenByUs[frame] then
                        item.hiddenByUs[frame] = nil
                        if not pcall(frame.Show, frame) then pendingCombat = true end
                    end
                elseif frame:IsShown() then
                    item.hiddenByUs[frame] = true
                    if not pcall(frame.Hide, frame) then pendingCombat = true end
                end
            end
        end
    end

    return item
end

local minimap, quests, menuBar, bagBar, xpHideable
local bags = { frames = { "BagsBar" }, modes = { full = true, pack = true, off = true } } -- the bag bar code is further down

-- Menu bar and bag bar are two hideables (see MENU_BAR_FRAMES); everything that used to apply the menu bar applies both.
local function applyMenuBars()
    if menuBar then menuBar.apply() end
    if bagBar then bagBar.apply() end
    if bags.apply then bags.apply() end
end

---------------------------------------------------------------------------
-- Quest tracker size: scale (percent, 50-100) and an optional maximum height.
-- The tracker cannot be made smaller in the game. Edit Mode and Blizzard code set the scale again on layout changes, so a
-- post hook on SetScale keeps ours (Blizzard's value is remembered and multiplied). Not in combat: the tracker holds
-- protected item buttons; it is caught up on PLAYER_REGEN_ENABLED.
---------------------------------------------------------------------------
local QUEST_FRAME_NAMES = { "ObjectiveTrackerFrame", "QuestWatchFrame", "WatchFrame" }
local QUEST_SCALE_DEFAULT, QUEST_SCALE_MIN = 70, 50
local questSize = { baseScale = {}, baseHeight = {}, hooked = {}, guard = false }

local function questFactor()
    local percent = db and db.questsScale
    if type(percent) == "number" and percent < 100 then return math.max(QUEST_SCALE_MIN, percent) / 100 end
    return 1
end

local function questHeightLimit()
    local height = db and db.questsHeight
    if type(height) == "number" and height > 0 then return height end
end

local function hookQuestFrame(frame)
    if questSize.hooked[frame] then return end
    questSize.hooked[frame] = true
    if frame.SetScale then
        pcall(hooksecurefunc, frame, "SetScale", function(self, scale)
            if questSize.guard or type(scale) ~= "number" then return end
            local factor = questFactor()
            if factor >= 1 then return end
            questSize.baseScale[self] = scale
            if inCombat() then pendingCombat = true; return end
            questSize.guard = true
            pcall(self.SetScale, self, scale * factor)
            questSize.guard = false
        end)
    end
    if frame.SetHeight then
        pcall(hooksecurefunc, frame, "SetHeight", function(self, height)
            local limit = questHeightLimit()
            if questSize.guard or not limit or type(height) ~= "number" or height <= limit then return end
            questSize.baseHeight[self] = height
            if inCombat() then pendingCombat = true; return end
            questSize.guard = true
            pcall(self.SetHeight, self, limit)
            questSize.guard = false
        end)
    end
end

local function applyQuestSize()
    if not db then return end
    if inCombat() then pendingCombat = true; return end
    local factor, limit = questFactor(), questHeightLimit()
    for _, name in ipairs(QUEST_FRAME_NAMES) do
        local frame = frameOf(name)
        if frame and frame.GetScale and frame.SetScale then
            hookQuestFrame(frame)
            questSize.guard = true
            if factor < 1 then
                if questSize.baseScale[frame] == nil then questSize.baseScale[frame] = frame:GetScale() or 1 end
                pcall(frame.SetScale, frame, questSize.baseScale[frame] * factor)
            elseif questSize.baseScale[frame] ~= nil then
                pcall(frame.SetScale, frame, questSize.baseScale[frame])
                questSize.baseScale[frame] = nil
            end
            if frame.GetHeight and frame.SetHeight then
                if limit then
                    local height = frame:GetHeight() or 0
                    if height > limit then
                        if questSize.baseHeight[frame] == nil then questSize.baseHeight[frame] = height end
                        pcall(frame.SetHeight, frame, limit)
                    end
                elseif questSize.baseHeight[frame] ~= nil then
                    pcall(frame.SetHeight, frame, questSize.baseHeight[frame])
                    questSize.baseHeight[frame] = nil
                end
            end
            questSize.guard = false
        end
    end
end

-- Edit Mode and the layout change apply their values with a delay: apply ours again afterwards.
local function applyQuestSizeLater()
    applyQuestSize()
    later(0.5, applyQuestSize)
    later(2, applyQuestSize)
end

function UI.SetQuestScale(percent)
    if not db then return end
    db.questsScale = math.max(QUEST_SCALE_MIN, math.min(100, math.floor(percent)))
    applyQuestSize()
end

function UI.SetQuestHeight(height)
    if not db then return end
    db.questsHeight = (type(height) == "number" and height >= 100) and math.floor(height) or nil
    applyQuestSize()
end

local function questSizeCommand(rest)
    local word, arg = string.match(rest or "", "^(%S*)%s*(.-)%s*$")
    local number = tonumber(arg)
    if word == "size" or word == "scale" then
        if not number then
            say(L.tf_quests_size_50_100_percent)
            return
        end
        db.questsScale = math.max(QUEST_SCALE_MIN, math.min(100, math.floor(number)))
    elseif word == "reset" then
        db.questsScale = 100
        db.questsHeight = nil
    elseif word == "height" or word == "hoehe" then
        if number and number >= 100 then db.questsHeight = math.floor(number)
        elseif arg == "off" or arg == "aus" then db.questsHeight = nil
        else say(L.tf_quests_height_100_1000_off); return end
    end
    applyQuestSize()
    say(string.format(L.quest_tracker_size_s_max_height,
        tostring(db.questsScale or 100), tostring(db.questsHeight or "-")))
end

---------------------------------------------------------------------------
-- Status bars (experience, reputation, honor, artifact / azerite power, house favor).
-- Retail 12.x: StatusTrackingBarManager (child of UIParent) holds MainStatusTrackingBarContainer and
-- SecondaryStatusTrackingBarContainer (Edit Mode systems, 571 x 17, anchored BOTTOM of the manager); every bar sits in one of the
-- two containers. The bars have a fixed size from the start, so the whole container is scaled (one number, keeps the
-- proportions and the mouse-over text / tooltip). Modes:
--   standard  Blizzard's size and place (the values Blizzard / Edit Mode set last are put back)
--   compact   scale so that the bar is "xpWidth" percent of the screen width wide, place stays
--   minimap   as wide as the minimap, the main bar directly below the minimap, the second one below that
--   off       the whole manager is hidden
-- Blizzard code and Edit Mode set scale and anchors again on layout changes: post hooks remember their values and call
-- ours again (delayed for the anchors). Not in combat; caught up on PLAYER_REGEN_ENABLED. While Edit Mode is open the
-- bars are at the standard values.
---------------------------------------------------------------------------
local XP_CONTAINER_NAMES = { "MainStatusTrackingBarContainer", "SecondaryStatusTrackingBarContainer" }
local XP_MANAGER_NAME = "StatusTrackingBarManager"
local XP_MODES = { standard = true, compact = true, minimap = true, off = true }
local XP_WIDTH_DEFAULT, XP_WIDTH_MIN, XP_WIDTH_MAX = 40, 30, 100
local unpackArgs = unpack or table.unpack
local xp = { baseScale = {}, basePoints = {}, hooked = {}, touched = {}, guard = false, scheduled = false }

local function xpMode()
    local mode = db and db.xpMode
    if XP_MODES[mode] then return mode end
    return "compact"
end

local function xpWidthPercent()
    local percent = db and db.xpWidth
    if type(percent) ~= "number" then return XP_WIDTH_DEFAULT end
    return math.max(XP_WIDTH_MIN, math.min(XP_WIDTH_MAX, percent))
end

local function xpContainers()
    local list = {}
    for _, name in ipairs(XP_CONTAINER_NAMES) do
        local frame = frameOf(name)
        if frame and frame.SetScale and frame.SetPoint then list[#list + 1] = frame end
    end
    return list
end

-- The mode that counts now: the standard one while Edit Mode is open or when the minimap (the anchor) is not there.
local function xpEffectiveMode()
    local mode = xpMode()
    if reasons.edit then return "standard" end
    if mode == "minimap" then
        local map = frameOf("Minimap")
        local hiddenByUs = db.minimapHidden and now() >= peekUntil.minimap
        if not map or hiddenByUs or not map.GetWidth then return "compact" end
    end
    return mode
end

-- What Blizzard wanted last (scale, anchors); read once when we first touch a container, then kept by the hooks.
local function xpRemember(frame)
    if xp.baseScale[frame] == nil and frame.GetScale then xp.baseScale[frame] = frame:GetScale() or 1 end
    if xp.basePoints[frame] == nil and frame.GetNumPoints and frame.GetPoint then
        local points = {}
        for index = 1, frame:GetNumPoints() do
            local point, relativeTo, relativePoint, x, y = frame:GetPoint(index)
            points[#points + 1] = { n = 5, point, relativeTo, relativePoint, x, y }
        end
        xp.basePoints[frame] = points
    end
end

local function xpRestore(frame)
    if not xp.touched[frame] then return end
    xp.touched[frame] = nil
    xp.guard = true
    if xp.baseScale[frame] ~= nil then pcall(frame.SetScale, frame, xp.baseScale[frame]) end
    local points = xp.basePoints[frame]
    if points and #points > 0 and frame.ClearAllPoints then
        pcall(frame.ClearAllPoints, frame)
        for _, args in ipairs(points) do pcall(frame.SetPoint, frame, unpackArgs(args, 1, args.n)) end
    end
    xp.guard = false
end

-- Scale that makes the container [targetWidth] wide (screen units of UIParent); second value: scale of its parent.
local function xpScaleFor(frame, targetWidth)
    local natural = frame.GetWidth and frame:GetWidth() or 0
    if not natural or natural <= 1 then return nil end
    local parent = frame.GetParent and frame:GetParent()
    local parentScale = (parent and parent.GetEffectiveScale and parent:GetEffectiveScale() or 1) / (UIParent:GetEffectiveScale() or 1)
    if parentScale <= 0 then parentScale = 1 end
    return math.max(0.2, math.min(2, targetWidth / (natural * parentScale))), parentScale
end

local function xpApplyAll()
    local frames = xpContainers()
    if #frames == 0 then return end
    local mode = xpEffectiveMode()
    if mode == "standard" or mode == "off" then
        for _, frame in ipairs(frames) do xpRestore(frame) end
        return
    end
    for _, frame in ipairs(frames) do xpRemember(frame) end
    local targetWidth
    if mode == "minimap" then
        local map = frameOf("Minimap")
        targetWidth = map:GetWidth() * (map:GetEffectiveScale() or 1) / (UIParent:GetEffectiveScale() or 1)
    else
        targetWidth = UIParent:GetWidth() * xpWidthPercent() / 100
    end
    xp.guard = true
    local previous
    for _, frame in ipairs(frames) do
        local scale, parentScale = xpScaleFor(frame, targetWidth)
        if scale then
            xp.touched[frame] = true
            pcall(frame.SetScale, frame, scale)
            if mode == "minimap" and frame.ClearAllPoints then
                local unit = scale * (parentScale or 1) -- size of one unit of this frame, in screen units
                pcall(frame.ClearAllPoints, frame)
                if previous then
                    pcall(frame.SetPoint, frame, "TOP", previous, "BOTTOM", 0, -1 / unit)
                else
                    pcall(frame.SetPoint, frame, "TOP", frameOf("Minimap"), "BOTTOM", 0, -3 / unit)
                end
                previous = frame
            end
        end
    end
    xp.guard = false
end

local function applyXp()
    if not db then return end
    if xpHideable then xpHideable.apply() end
    if inCombat() then pendingCombat = true; return end
    xpApplyAll()
    if xp.after then xp.after() end
end

local function applyXpLater()
    applyXp()
    later(0.5, applyXp)
    later(2, applyXp)
end

-- Anchors are set in several calls (ClearAllPoints, SetPoint ...): wait until Blizzard is done.
local function applyXpSoon()
    if xp.scheduled then return end
    xp.scheduled = true
    later(0, function()
        xp.scheduled = false
        applyXp()
    end)
end

local function hookXpFrames()
    for _, frame in ipairs(xpContainers()) do
        if not xp.hooked[frame] then
            xp.hooked[frame] = true
            pcall(hooksecurefunc, frame, "SetScale", function(self, scale)
                if xp.guard or type(scale) ~= "number" then return end
                xp.baseScale[self] = scale
                xp.touched[self] = nil -- Blizzard's value is in place now; ours is applied again below if a mode needs it
                applyXpSoon()
            end)
            pcall(hooksecurefunc, frame, "ClearAllPoints", function(self)
                if xp.guard then return end
                xp.basePoints[self] = {}
                xp.touched[self] = nil
                applyXpSoon()
            end)
            pcall(hooksecurefunc, frame, "SetPoint", function(self, ...)
                if xp.guard then return end
                local points = xp.basePoints[self]
                if points == nil then points = {}; xp.basePoints[self] = points end
                points[#points + 1] = { n = select("#", ...), ... }
                xp.touched[self] = nil
                applyXpSoon()
            end)
        end
    end
end

function UI.SetXpMode(mode)
    if not db then return end
    if not XP_MODES[mode] then mode = "compact" end
    db.xpMode = mode
    hookXpFrames()
    applyXp()
end

function UI.SetXpWidth(percent)
    if not db then return end
    db.xpWidth = math.max(XP_WIDTH_MIN, math.min(XP_WIDTH_MAX, math.floor(percent)))
    applyXp()
end

local function xpCommand(rest)
    local word = string.match(rest or "", "^(%S*)")
    local number = tonumber(word)
    if number then
        UI.SetXpWidth(number)
        if xpMode() == "standard" or xpMode() == "off" then UI.SetXpMode("compact") end
    elseif word == "standard" or word == "compact" or word == "kompakt" or word == "minimap" or word == "off" or word == "aus" then
        UI.SetXpMode(word == "kompakt" and "compact" or word == "aus" and "off" or word)
    else
        say(L.tf_xp_standard_compact_minimap_off)
    end
    say(string.format(L.status_bars_s_width_s, xpMode(), tostring(xpWidthPercent())))
end

---------------------------------------------------------------------------
-- Menu bar and bag bar (the classic micro menu). 12.x: MicroMenuContainer (Edit Mode system, the buttons are in MicroMenu) and
-- BagsBar (Edit Mode system), both children of UIParent. Hidden like minimap / tracker (Show hook), shown in Edit Mode.
---------------------------------------------------------------------------
local MENU_BAR_FRAMES = { "MicroMenuContainer", "MicroButtonAndBagsBar" }
-- The bag bar (the row of bag buttons under the minimap) has three steps (db.bagsMode): "full" (Blizzard's bar), "pack" (default: only the
-- backpack, with its free slots counter, and the ammo bag of a hunter, the other buttons are hidden one by one) and "off" (the bar
-- is hidden like the menu bar; bags stay reachable by key (B, SHIFT-B ...) and by the radial menu). "Menü- und Taschenleiste ausblenden"
-- (menuHidden) hides it together with the menu bar, too. Old saved values (fold8.16: bagsHidden) become "pack".
-- (bags is one table instead of several locals: the main chunk has room for 200 local variables only; bags.apply hides the unwanted bag buttons)
do
local BAG_SLOT_NAMES = { "CharacterBag0Slot", "CharacterBag1Slot", "CharacterBag2Slot", "CharacterBag3Slot" } -- bag IDs 1 to 4
local BAG_OTHER_NAMES = { "CharacterReagentBag0Slot", "BagBarExpandToggle", "KeyRingButton" } -- hidden in "pack" (the backpack button stays)
local BAG_FIT_NAMES = { "MainMenuBarBackpackButton", "CharacterBag0Slot", "CharacterBag1Slot", "CharacterBag2Slot", "CharacterBag3Slot",
    "CharacterReagentBag0Slot", "BagBarExpandToggle", "KeyRingButton" } -- every button that can be in the bar (the shown ones count)
local bagPart = { hide = {}, hiddenByUs = {}, hooked = {}, guard = false, dirty = false, fitted = false, fitHooked = false }

-- left, right, top, bottom of a frame on the screen (own units times effective scale); nil while the game has not laid it out
function UI.ScreenRect(frame)
    if not (frame and frame.GetRight and frame.GetLeft and frame.GetTop and frame.GetBottom and frame.GetEffectiveScale) then return nil end
    local ok1, right = pcall(frame.GetRight, frame)
    local ok2, top = pcall(frame.GetTop, frame)
    local ok3, bottom = pcall(frame.GetBottom, frame)
    local ok4, scale = pcall(frame.GetEffectiveScale, frame)
    local ok5, left = pcall(frame.GetLeft, frame)
    if ok1 and ok2 and ok3 and ok4 and ok5 and type(right) == "number" and type(top) == "number" and type(bottom) == "number"
        and type(left) == "number" and type(scale) == "number" and scale > 0 then
        return left * scale, right * scale, top * scale, bottom * scale
    end
end

function bags.mode()
    local mode = db and db.bagsMode
    if bags.modes[mode] then return mode end
    return "pack"
end

-- Family of a bag (bit field of the game): 1 = arrows (quiver), 2 = bullets (ammo pouch), 0 = a normal bag.
local function hasAmmoFamily(family) return type(family) == "number" and family % 4 >= 1 end

-- Is the bag in this container ID (1 to 4) a quiver or an ammo pouch? First the free slots call of the game (answers the family of the
-- bag), then, as a fallback, the item in the bag slot (family of the item, or item class "quiver").
local function bagFamily(bagID)
    if C_Container and C_Container.GetContainerNumFreeSlots then
        local ok, _, family = pcall(C_Container.GetContainerNumFreeSlots, bagID)
        if ok and type(family) == "number" then return family end
    end
end

local function isAmmoBag(bagID)
    if hasAmmoFamily(bagFamily(bagID)) then return true end
    local inventoryID
    if C_Container and C_Container.ContainerIDToInventoryID then
        local ok, id = pcall(C_Container.ContainerIDToInventoryID, bagID)
        if ok then inventoryID = id end
    elseif ContainerIDToInventoryID then
        local ok, id = pcall(ContainerIDToInventoryID, bagID)
        if ok then inventoryID = id end
    end
    local link
    if inventoryID and GetInventoryItemLink then
        local ok, value = pcall(GetInventoryItemLink, "player", inventoryID)
        if ok then link = value end
    end
    if not link then return false end
    if GetItemFamily then
        local ok, family = pcall(GetItemFamily, link)
        if ok and hasAmmoFamily(family) then return true end
    end
    local instant = (C_Item and C_Item.GetItemInfoInstant) or GetItemInfoInstant
    if instant then
        local ok, _, _, _, _, _, classID = pcall(instant, link)
        local quiver = (Enum and Enum.ItemClass and Enum.ItemClass.Quiver) or 11
        if ok and classID == quiver then return true end
    end
    return false
end

local function hookBagPart(frame)
    if bagPart.hooked[frame] or not hooksecurefunc or not frame.Show then return end
    bagPart.hooked[frame] = true
    pcall(hooksecurefunc, frame, "Show", function(self)
        if bagPart.guard or not bagPart.hide[self] then return end
        bagPart.hiddenByUs[self] = true
        bagPart.guard = true
        if not pcall(self.Hide, self) then pendingCombat = true end
        bagPart.guard = false
    end)
end

-- Hides the bag buttons that "pack" does not want, shows them again when the step changes (or Edit Mode is open).
function bags.apply()
    if not db then return end
    local pack = bags.mode() == "pack" and not reasons.edit
    local hide = {}
    if pack then
        for index, name in ipairs(BAG_SLOT_NAMES) do
            local frame = frameOf(name)
            if frame and not isAmmoBag(index) then hide[frame] = true end
        end
        for _, name in ipairs(BAG_OTHER_NAMES) do
            local frame = frameOf(name)
            if frame then hide[frame] = true end
        end
    end
    bagPart.hide = hide
    local changed = false
    for frame in pairs(hide) do
        hookBagPart(frame)
        if frame.IsShown and frame:IsShown() then
            bagPart.hiddenByUs[frame] = true
            bagPart.guard = true
            if pcall(frame.Hide, frame) then changed = true else pendingCombat = true end
            bagPart.guard = false
        end
    end
    for frame in pairs(bagPart.hiddenByUs) do
        if not hide[frame] then
            bagPart.hiddenByUs[frame] = nil
            bagPart.guard = true
            if pcall(frame.Show, frame) then changed = true else pendingCombat = true end
            bagPart.guard = false
        end
    end
    local bar = frameOf("BagsBar")
    if changed and bar then
        if bar.MarkDirty then pcall(bar.MarkDirty, bar) end
        if bar.Layout then pcall(bar.Layout, bar) end
    end
    bags.fit()
end

-- The bar frame (and the plate behind the buttons) has to be as long as the visible buttons only. Blizzard's layout does that when it
-- runs after the hiding; when it leaves the frame at the width of the whole bar (fold8.17 on the device: two buttons, bar as long as
-- before) the width is set by hand: union of the visible buttons (screen units), plus the padding of the layout. Every layout of the
-- game (bag change, Edit Mode) is followed by a check (post hook on Layout, no position is touched). Outside "pack" (or in Edit Mode)
-- the game's layout runs again and sets its own width.
function bags.fit()
    local bar = frameOf("BagsBar")
    if not (db and bar and bar.GetWidth and bar.SetWidth) or bagPart.fitting then return end
    if not bagPart.fitHooked and hooksecurefunc and bar.Layout then
        bagPart.fitHooked = true
        pcall(hooksecurefunc, bar, "Layout", function() if not bagPart.fitting then later(0, bags.fit) end end)
    end
    if not (bags.mode() == "pack" and not reasons.edit) then
        if bagPart.fitted then
            bagPart.fitted = false
            bagPart.fitting = true
            if bar.MarkDirty then pcall(bar.MarkDirty, bar) end
            if bar.Layout then pcall(bar.Layout, bar) end
            bagPart.fitting = false
        end
        return
    end
    local left, right, count, buttons = nil, nil, 0, 0
    for _, name in ipairs(BAG_FIT_NAMES) do
        local frame = frameOf(name)
        if frame and frame.IsShown and frame:IsShown() then
            count = count + 1
            buttons = buttons + (frame.GetWidth and frame:GetWidth() or 0) * ((frame.GetEffectiveScale and frame:GetEffectiveScale()) or 1)
            local l, r = UI.ScreenRect(frame)
            if l then left, right = math.min(left or l, l), math.max(right or r, r) end
        end
    end
    if count == 0 then return end
    local eff = (bar.GetEffectiveScale and bar:GetEffectiveScale()) or 1
    if eff <= 0 then eff = 1 end
    local pad = (type(bar.leftPadding) == "number" and bar.leftPadding or 0) + (type(bar.rightPadding) == "number" and bar.rightPadding or 0)
    local want = ((left and right - left) or buttons) / eff + pad
    local have = bar:GetWidth() or 0
    bagPart.wanted = want
    if want <= 1 or have <= want + 4 then return end
    bagPart.fitting = true
    if pcall(bar.SetWidth, bar, want) then bagPart.fitted = true else pendingCombat = true end
    bagPart.fitting = false
end

-- Bags are swapped or put on / taken off: a moment later the buttons are checked again (a bag may have become or stopped being a quiver).
function bags.soon()
    if not db or bagPart.dirty then return end
    bagPart.dirty = true
    later(0.3, function()
        bagPart.dirty = false
        bags.apply()
    end)
end

function bags.wanted() return bagPart.wanted end
function bags.fitted() return bagPart.fitted end

-- For "/tf minimap info": what the bag bar is made of apart from the buttons (plate / background candidates): child frames and textures with size and shown.
function bags.parts(bar)
    local lines = {}
    local function size(f)
        local w, h = f.GetWidth and f:GetWidth(), f.GetHeight and f:GetHeight()
        return string.format("%sx%s", w and string.format("%.0f", w) or "-", h and string.format("%.0f", h) or "-")
    end
    if bar.GetChildren then
        for index, child in ipairs({ bar:GetChildren() }) do
            if index > 12 then break end
            local name = child.GetName and child:GetName() or "-"
            lines[#lines + 1] = string.format("BagsBar child %d %s %s shown=%s", index, tostring(name), size(child), tostring(child.IsShown and child:IsShown()))
        end
    end
    if bar.GetRegions then
        for index, region in ipairs({ bar:GetRegions() }) do
            if index > 8 then break end
            lines[#lines + 1] = string.format("BagsBar region %d %s %s shown=%s", index, tostring(region.GetObjectType and region:GetObjectType()), size(region),
                tostring(region.IsShown and region:IsShown()))
        end
    end
    return lines
end

-- For "/tf minimap info": the bag buttons of this client.
function bags.describe()
    local lines = {}
    lines[#lines + 1] = string.format("bags mode=%s", bags.mode())
    for index, name in ipairs(BAG_SLOT_NAMES) do
        local frame = frameOf(name)
        if frame then
            lines[#lines + 1] = string.format("%s shown=%s family=%s ammo=%s", name, tostring(frame.IsShown and frame:IsShown()),
                tostring(bagFamily(index)), tostring(isAmmoBag(index)))
        end
    end
    for _, name in ipairs({ "MainMenuBarBackpackButton", "BagBarExpandToggle", "KeyRingButton", "CharacterReagentBag0Slot" }) do
        local frame = frameOf(name)
        if frame then lines[#lines + 1] = string.format("%s shown=%s", name, tostring(frame.IsShown and frame:IsShown())) end
    end
    if #lines == 1 then lines[#lines + 1] = "no bag buttons found" end
    return lines
end
end -- of the bag bar block

---------------------------------------------------------------------------
-- Minimap shape: round (the game's own, preset) or square. Square = a square mask, the round ring (MinimapCompassTexture) and the
-- header strip (MinimapCluster.BorderTop) invisible, a thin border of our own, the map moved up into the strip. The header is
-- built again from the inside corners of the map: the zone name is a text of our own (outlined, no bar behind it; Blizzard's
-- ZoneTextButton with its background is made invisible and click-through), top left; calendar (day / night moon) below it;
-- tracking and mail top right; dungeon difficulty and clock bottom left (the bottom right belongs to the zoom buttons). Nothing
-- hangs over the rim. Retail 12.x frame names (Blizzard_Minimap): MinimapCluster.{MinimapContainer.Minimap, BorderTop,
-- ZoneTextButton, Tracking, IndicatorFrame, InstanceDifficulty}, GameTimeFrame, TimeManagerClockButton; older names are tried as
-- well. Only outside of combat (caught up on PLAYER_REGEN_ENABLED). Back to round without /reload: mask, anchors, alpha and mouse
-- state are restored. When Blizzard puts one of the corner items back (zone change, mail, Edit Mode ...), it is moved again
-- (hook on SetPoint, a few times a minute at most).
-- GetMinimapShape() answers "SQUARE" / "ROUND" for addons that put buttons on the rim (LibDBIcon).
--
-- Zoom buttons: Blizzard's + / - are small and often only shown while the mouse is over the map (no mouse on a phone). Our own
-- buttons (40 pixels of the game picture, about 48 dp on the Fold) sit inside the map, bottom right, in round and square mode.
-- Option "big zoom buttons" (on by default); off = Blizzard's buttons are back and ours are hidden.
---------------------------------------------------------------------------
local MINIMAP_SQUARE_MASK = "Interface\\BUTTONS\\WHITE8X8"
local MINIMAP_ROUND_MASK = "Textures\\MinimapMask"
local MINIMAP_HEADER_SHIFT = 26   -- height of the header strip the map moves up into
local ZOOM_BUTTON_PX = 40         -- edge of a zoom button in pixels of the game picture
local ZOOM_GAP_PX = 4
local mapShape = {
    applied = false, saved = {}, alphas = {}, mice = {}, order = {}, border = nil, hooked = false,
    label = nil, zoom = nil, zoomAlphas = {}, zoomMice = {}, watched = {}, placing = false, recheck = false,
    budgetFrom = 0, budget = 0,
    flush = { x = 0, y = 0 }, -- how far the map container was moved inside the cluster to sit flush top right (container units)
}
-- Blizzard puts these back to their own anchors when the header is laid out again (Edit Mode "header underneath"), the mail icon
-- alone when the tracking button appears or goes: the snapshot of such a frame is taken again (it is Blizzard's new state).
local MAP_RESET_BY_LAYOUT = { MinimapContainer = true, BorderTop = true, InstanceDifficulty = true, IndicatorFrame = true }
local MAP_RESET_BY_INDICATOR = { IndicatorFrame = true }

local previousMinimapShape = rawget(_G, "GetMinimapShape")
function GetMinimapShape()
    if db and db.minimapShape == "square" then return "SQUARE" end
    if type(previousMinimapShape) == "function" then
        local ok, shape = pcall(previousMinimapShape)
        if ok and shape then return shape end
    end
    return "ROUND"
end

local function isFrame(frame) return type(frame) == "table" and frame.GetObjectType ~= nil end

local function mapRemember(key, frame)
    if mapShape.saved[key] == nil and frame.GetNumPoints and frame.GetPoint then
        local points = {}
        for index = 1, frame:GetNumPoints() do
            local point, relativeTo, relativePoint, x, y = frame:GetPoint(index)
            points[#points + 1] = { n = 5, point, relativeTo, relativePoint, x, y }
        end
        mapShape.saved[key] = { frame = frame, points = points }
        mapShape.order[#mapShape.order + 1] = key
    end
end

local function mapPlace(key, frame, point, relativeTo, relativePoint, x, y)
    if not (isFrame(frame) and frame.SetPoint and frame.ClearAllPoints) then return end
    mapRemember(key, frame)
    pcall(frame.ClearAllPoints, frame)
    pcall(frame.SetPoint, frame, point, relativeTo, relativePoint, x, y)
end

-- store: where the old values are kept (default: the square mode's own table)
local function mapAlpha(key, frame, alpha, store)
    if not (isFrame(frame) and frame.SetAlpha) then return end
    store = store or mapShape.alphas
    local known = false
    for _, entry in pairs(store) do
        if entry.frame == frame then known = true; break end -- the same frame under two names: the first (true) value stays
    end
    if not known and frame.GetAlpha then store[key] = { frame = frame, alpha = frame:GetAlpha() or 1 } end
    pcall(frame.SetAlpha, frame, alpha)
end

-- Click-through (an invisible frame must not catch taps). The old state is restored with the alpha.
local function mapMouse(key, frame, enabled, store)
    if not (isFrame(frame) and frame.EnableMouse and frame.IsMouseEnabled) then return end
    store = store or mapShape.mice
    if store[key] == nil then
        local ok, was = pcall(frame.IsMouseEnabled, frame)
        store[key] = { frame = frame, enabled = ok and was and true or false }
    end
    pcall(frame.EnableMouse, frame, enabled)
end

local function mapRestore(store, apply)
    for key, entry in pairs(store) do
        pcall(apply, entry)
        store[key] = nil
    end
end

local function mapParts(cluster)
    return {
        borderTop = cluster.BorderTop or frameOf("MinimapBorderTop"),
        zone = cluster.ZoneTextButton or frameOf("MinimapZoneTextButton"),
        tracking = cluster.Tracking or cluster.TrackingFrame or frameOf("MiniMapTracking") or frameOf("MiniMapTrackingFrame"),
        indicator = cluster.IndicatorFrame or frameOf("MiniMapMailFrame"),
        difficulty = cluster.InstanceDifficulty or frameOf("MiniMapInstanceDifficulty"),
        calendar = frameOf("GameTimeFrame"),
        clock = frameOf("TimeManagerClockButton"),
    }
end

-- Thin dark border of our own around the square map: four 1 pixel edges on a click-through child frame of the minimap.
local function mapBorder(map)
    if mapShape.border then return mapShape.border end
    local ok, border = pcall(CreateFrame, "Frame", "TouchForeverMinimapBorder", map)
    if not ok or not border then return nil end
    pcall(border.SetAllPoints, border, map)
    pcall(border.EnableMouse, border, false)
    local function edge(point1, point2, horizontal)
        local ok2, texture = pcall(border.CreateTexture, border, nil, "OVERLAY")
        if not ok2 or not texture then return end
        pcall(texture.SetColorTexture, texture, 0.05, 0.05, 0.06, 0.95)
        pcall(texture.SetPoint, texture, point1, border, point1)
        pcall(texture.SetPoint, texture, point2, border, point2)
        if horizontal then pcall(texture.SetHeight, texture, 1) else pcall(texture.SetWidth, texture, 1) end
    end
    edge("TOPLEFT", "TOPRIGHT", true)
    edge("BOTTOMLEFT", "BOTTOMRIGHT", true)
    edge("TOPLEFT", "BOTTOMLEFT", false)
    edge("TOPRIGHT", "BOTTOMRIGHT", false)
    mapShape.border = border
    return border
end

-- The zone name as a text of our own: no bar behind it, outlined, left aligned, cut off before the tracking button.
local function zoneName()
    local text = GetMinimapZoneText and GetMinimapZoneText() or nil
    if not text or text == "" then text = GetZoneText and GetZoneText() or "" end
    return text or ""
end

local function updateZoneLabel()
    local label = mapShape.label
    if label and label.text then pcall(label.text.SetText, label.text, zoneName()) end
end

local function zoneLabel(map)
    if mapShape.label then return mapShape.label end
    local ok, holder = pcall(CreateFrame, "Frame", "TouchForeverZoneLabel", map)
    if not ok or not holder then return nil end
    pcall(holder.EnableMouse, holder, false)
    pcall(holder.SetHeight, holder, 14)
    local okText, text = pcall(holder.CreateFontString, holder, nil, "OVERLAY", "GameFontNormal")
    if okText and text then
        pcall(text.SetAllPoints, text, holder)
        pcall(text.SetJustifyH, text, "LEFT")
        pcall(text.SetWordWrap, text, false)
        if text.GetFont and text.SetFont then
            local okFont, font, size = pcall(text.GetFont, text)
            if okFont and font and size then pcall(text.SetFont, text, font, size, "OUTLINE") end
        end
        holder.text = text
    end
    for _, event in ipairs({ "ZONE_CHANGED", "ZONE_CHANGED_INDOORS", "ZONE_CHANGED_NEW_AREA", "PLAYER_ENTERING_WORLD" }) do
        pcall(holder.RegisterEvent, holder, event)
    end
    pcall(holder.SetScript, holder, "OnEvent", updateZoneLabel)
    mapShape.label = holder
    return holder
end

-- A corner item that Blizzard moves is put back (called a moment after Blizzard's SetPoint, see mapWatch).
local mapCorners

local function mapMoved()
    if mapShape.placing or mapShape.recheck then return end
    if not (mapShape.applied and db and db.minimapShape == "square") then return end
    mapShape.recheck = true
    later(0.3, function()
        mapShape.recheck = false
        if not (mapShape.applied and db and db.minimapShape == "square") then return end
        if inCombat() then pendingCombat = true; return end
        local t = now()
        if t - mapShape.budgetFrom > 60 then mapShape.budgetFrom, mapShape.budget = t, 0 end
        mapShape.budget = mapShape.budget + 1
        if mapShape.budget > 20 then return end -- something keeps fighting us: leave it alone until the next change of the shape
        local cluster, map = frameOf("MinimapCluster"), frameOf("Minimap")
        if cluster and map then mapCorners(cluster, map) end
    end)
end

local function mapWatch(frame)
    if not isFrame(frame) or mapShape.watched[frame] or not hooksecurefunc or not frame.SetPoint then return end
    mapShape.watched[frame] = true
    pcall(hooksecurefunc, frame, "SetPoint", mapMoved)
end

function mapCorners(cluster, map)
    local parts = mapParts(cluster)
    mapShape.placing = true
    local label = zoneLabel(map)
    if label then
        pcall(label.ClearAllPoints, label)
        pcall(label.SetPoint, label, "TOPLEFT", map, "TOPLEFT", 4, -3)
        local width = map.GetWidth and map:GetWidth() or nil
        if type(width) == "number" and width > 60 then pcall(label.SetWidth, label, width - 34) end
        pcall(label.Show, label)
        updateZoneLabel()
    end
    mapPlace("GameTimeFrame", parts.calendar, "TOPLEFT", map, "TOPLEFT", 2, -18)
    mapPlace("Tracking", parts.tracking, "TOPRIGHT", map, "TOPRIGHT", -3, -3)
    mapPlace("IndicatorFrame", parts.indicator, "TOPRIGHT", map, "TOPRIGHT", -3, -23)
    mapPlace("InstanceDifficulty", parts.difficulty, "BOTTOMLEFT", map, "BOTTOMLEFT", 3, 22)
    mapPlace("TimeManagerClockButton", parts.clock, "BOTTOMLEFT", map, "BOTTOMLEFT", 4, 3)
    mapShape.placing = false
    for _, frame in ipairs({ parts.calendar, parts.tracking, parts.indicator, parts.difficulty, parts.clock }) do mapWatch(frame) end
end

-- The header strip and everything that has a background of its own up there: invisible (alpha), the zone button also click-through.
local function mapHideHeader(cluster, parts)
    mapAlpha("BorderTop", parts.borderTop, 0)
    mapAlpha("ZoneTextButton", parts.zone, 0)
    mapMouse("ZoneTextButton", parts.zone, false)
    for _, key in ipairs({ "Background", "HeaderBackground", "TopBorder" }) do mapAlpha("cluster." .. key, cluster[key], 0) end
    if cluster.GetRegions then
        local regions = { cluster:GetRegions() }
        for index, region in ipairs(regions) do
            if isFrame(region) and region:GetObjectType() == "Texture" then mapAlpha("clusterRegion" .. index, region, 0) end
        end
    end
end

local function mapSquareOn(cluster, map)
    local parts = mapParts(cluster)
    pcall(map.SetMaskTexture, map, MINIMAP_SQUARE_MASK)
    mapAlpha("ring", frameOf("MinimapCompassTexture"), 0)
    mapHideHeader(cluster, parts)
    local container = cluster.MinimapContainer
    if isFrame(container) and container.GetPoint then
        mapShape.placing = true
        mapRemember("MinimapContainer", container)
        local saved = mapShape.saved.MinimapContainer
        local base = saved and saved.points[1]
        if base and (base[1] == "TOP" or base[1] == "BOTTOM") and type(base[5]) == "number" then
            local scale = container.GetScale and container:GetScale() or 1
            if scale == nil or scale <= 0 then scale = 1 end
            local shift = MINIMAP_HEADER_SHIFT / scale
            local flush = mapShape.flush
            pcall(container.ClearAllPoints, container)
            pcall(container.SetPoint, container, base[1], base[2], base[3], (base[4] or 0) + flush.x,
                (base[1] == "TOP" and base[5] + shift or base[5] - shift) + flush.y)
        end
        mapShape.placing = false
    end
    mapCorners(cluster, map)
    local border = mapBorder(map)
    if border and border.Show then pcall(border.Show, border) end
end

local function mapSquareOff(cluster, map)
    mapShape.placing = true
    pcall(map.SetMaskTexture, map, MINIMAP_ROUND_MASK)
    mapRestore(mapShape.alphas, function(entry) entry.frame:SetAlpha(entry.alpha) end)
    mapRestore(mapShape.mice, function(entry) entry.frame:EnableMouse(entry.enabled) end)
    for _, key in ipairs(mapShape.order) do
        local entry = mapShape.saved[key]
        if entry and #entry.points > 0 then
            pcall(entry.frame.ClearAllPoints, entry.frame)
            for _, args in ipairs(entry.points) do pcall(entry.frame.SetPoint, entry.frame, unpackArgs(args, 1, args.n)) end
        end
    end
    mapShape.placing = false
    mapShape.saved, mapShape.order = {}, {}
    mapShape.flush = { x = 0, y = 0 }
    mapShape.residual = nil
    if mapShape.border and mapShape.border.Hide then pcall(mapShape.border.Hide, mapShape.border) end
    if mapShape.label and mapShape.label.Hide then pcall(mapShape.label.Hide, mapShape.label) end
end

-- Square map flush with the top right corner of its cluster (fold8.26). The cluster is what the Edit Mode frame (the yellow box) and
-- the screen edge see; Blizzard lays the map into it with a margin (header strip, corner items) and the header shift above adds one more
-- for the square map. So the map stayed 20 to 60 units away from the corner even when the box stood in the corner. The map container is
-- moved inside the cluster by what is left over between the cluster edge and the map edge (right and top, measured on the screen, in
-- steps: every call corrects the rest, a stale rectangle only costs one more step). Empty room stays at the bottom and the left of the
-- cluster. The cluster keeps its size (a bigger map would cover the bag bar, status bars and the quest tracker). Nothing is saved: the
-- value lives in mapShape.flush and is put on again by mapSquareOn after every layout of Blizzard. Returns true when it moved the map.
function mapShape.flushNow(cluster, map)
    local FLUSH_MAX, FLUSH_EPS = 80, 0.4 -- units: a rest above MAX is a wrong measurement (frame not laid out); below EPS nothing to do
    if not (mapShape.applied and db and db.minimapShape == "square") then return false end
    local container = cluster.MinimapContainer
    if not (isFrame(container) and container.GetNumPoints and container.GetPoint and container.SetPoint
        and container.GetEffectiveScale) or container:GetNumPoints() ~= 1 then
        mapShape.residual = nil
        return false
    end
    local _, cRight, cTop = UI.ScreenRect(cluster)
    local _, mRight, mTop = UI.ScreenRect(map)
    if not (cRight and mRight) then return false end
    local unit = UIParent.GetEffectiveScale and UIParent:GetEffectiveScale() or 1
    local eff = container:GetEffectiveScale()
    if not eff or eff <= 0 or not unit or unit <= 0 then return false end
    local rx, ry = (cRight - mRight) / unit, (cTop - mTop) / unit -- what is left between the cluster edge and the map edge, in units
    mapShape.residual = { x = rx, y = ry }
    if math.abs(rx) <= FLUSH_EPS and math.abs(ry) <= FLUSH_EPS then return false end
    if math.abs(rx) > FLUSH_MAX or math.abs(ry) > FLUSH_MAX then return false end
    local point, relativeTo, relativePoint, x, y = container:GetPoint(1)
    if not point then return false end
    local dx, dy = rx * unit / eff, ry * unit / eff -- container units
    mapShape.placing = true
    pcall(container.ClearAllPoints, container)
    pcall(container.SetPoint, container, point, relativeTo, relativePoint, (x or 0) + dx, (y or 0) + dy)
    mapShape.placing = false
    mapShape.flush = { x = mapShape.flush.x + dx, y = mapShape.flush.y + dy }
    return true
end

-- Zoom buttons ------------------------------------------------------------------------------------------------------------
local function zoomBy(step)
    local map = frameOf("Minimap")
    if not (map and map.GetZoom and map.SetZoom) then return end
    local ok, zoom = pcall(map.GetZoom, map)
    if not ok or type(zoom) ~= "number" then return end
    local levels = 6
    if map.GetZoomLevels then
        local okLevels, count = pcall(map.GetZoomLevels, map)
        if okLevels and type(count) == "number" and count > 0 then levels = count end
    end
    local target = math.max(0, math.min(levels - 1, zoom + step))
    if target ~= zoom then pcall(map.SetZoom, map, target) end
end

-- One unit of the buttons is one pixel of the game picture (like the cooldown strip): their size does not depend on the UI scale.
local function pixelScale()
    local height
    if GetPhysicalScreenSize then
        local _, h = GetPhysicalScreenSize()
        height = h
    end
    if not height or height <= 0 then
        height = (GetScreenHeight and UIParent and UIParent.GetEffectiveScale) and GetScreenHeight() * UIParent:GetEffectiveScale() or 768
    end
    return 768 / height
end

local function zoomButtons(map)
    if mapShape.zoom then return mapShape.zoom end
    local function make(name, text, step)
        local ok, button = pcall(CreateFrame, "Button", name, map)
        if not ok or not button then return nil end
        pcall(button.SetSize, button, ZOOM_BUTTON_PX, ZOOM_BUTTON_PX)
        pcall(button.SetIgnoreParentScale, button, true)
        local okLevel, level = pcall(map.GetFrameLevel, map)
        pcall(button.SetFrameLevel, button, (okLevel and type(level) == "number" and level or 1) + 10)
        local okBack, back = pcall(button.CreateTexture, button, nil, "BACKGROUND")
        if okBack and back then
            pcall(back.SetAllPoints, back, button)
            pcall(back.SetColorTexture, back, 0.04, 0.05, 0.08, 0.78)
        end
        local okLight, light = pcall(button.CreateTexture, button, nil, "HIGHLIGHT")
        if okLight and light then
            pcall(light.SetAllPoints, light, button)
            pcall(light.SetColorTexture, light, 1, 1, 1, 0.16)
        end
        local okLabel, label = pcall(button.CreateFontString, button, nil, "OVERLAY", "GameFontNormalLarge")
        if okLabel and label then
            pcall(label.SetPoint, label, "CENTER", button, "CENTER", 0, 1)
            pcall(label.SetText, label, text)
        end
        pcall(button.RegisterForClicks, button, "LeftButtonUp")
        pcall(button.SetScript, button, "OnClick", function() zoomBy(step) end)
        return button
    end
    local zoomIn = make("TouchForeverZoomIn", "+", 1)
    local zoomOut = make("TouchForeverZoomOut", "-", -1)
    if not (zoomIn and zoomOut) then return nil end
    mapShape.zoom = { zoomIn = zoomIn, zoomOut = zoomOut }
    return mapShape.zoom
end

-- Blizzard's own + / -: invisible and click-through while ours are on, as they were when ours are off.
local function blizzardZoomButtons(map)
    return { map.ZoomIn, map.ZoomOut, frameOf("MinimapZoomIn"), frameOf("MinimapZoomOut") }
end

local function applyMinimapZoom()
    if not db then return end
    local map = frameOf("Minimap")
    if not map then return end
    local want = db.zoomBig ~= false
    local own = zoomButtons(map)
    local standard = blizzardZoomButtons(map)
    if want and own then
        local scale = pixelScale()
        local square = db.minimapShape == "square" and mapShape.applied
        local inset = square and 3 or 2
        for _, button in pairs(own) do pcall(button.SetScale, button, scale) end
        pcall(own.zoomIn.ClearAllPoints, own.zoomIn)
        pcall(own.zoomIn.SetPoint, own.zoomIn, "BOTTOMRIGHT", map, "BOTTOMRIGHT", -inset, inset)
        pcall(own.zoomOut.ClearAllPoints, own.zoomOut)
        pcall(own.zoomOut.SetPoint, own.zoomOut, "BOTTOMRIGHT", own.zoomIn, "BOTTOMLEFT", -ZOOM_GAP_PX, 0)
        pcall(own.zoomIn.Show, own.zoomIn)
        pcall(own.zoomOut.Show, own.zoomOut)
        for index = 1, 4 do
            local button = standard[index]
            if button then
                mapAlpha("zoom" .. index, button, 0, mapShape.zoomAlphas)
                mapMouse("zoom" .. index, button, false, mapShape.zoomMice)
            end
        end
    else
        if own then
            pcall(own.zoomIn.Hide, own.zoomIn)
            pcall(own.zoomOut.Hide, own.zoomOut)
        end
        mapRestore(mapShape.zoomAlphas, function(entry) entry.frame:SetAlpha(entry.alpha) end)
        mapRestore(mapShape.zoomMice, function(entry) entry.frame:EnableMouse(entry.enabled) end)
    end
end

local function applyMapShape()
    if not db then return end
    local cluster, map = frameOf("MinimapCluster"), frameOf("Minimap")
    if not cluster or not map or not map.SetMaskTexture then return end
    local want = db.minimapShape == "square"
    if not want and not mapShape.applied then return end -- round and never changed: the game's own map stays untouched
    if inCombat() then pendingCombat = true; return end
    mapShape.budgetFrom, mapShape.budget = 0, 0
    if want then mapSquareOn(cluster, map) else mapSquareOff(cluster, map) end
    mapShape.applied = want
end

-- Minimap at the corner ------------------------------------------------------------------------------------------------------
-- Blizzard leaves a gap between the map and the top / right edge of the screen (the cluster is bigger than the map: header strip,
-- corner items). ONE nudge (fold8.18, no scaling, no permanent hook on the position): the whole MinimapCluster is moved so that the
-- map has CORNER_GAP units left to both edges. Measured on the screen (edges of the map and of UIParent) BEFORE anything is changed,
-- the new place is calculated from that (a rectangle that is read right after a change can be old). The cluster is not made bigger:
-- a bigger map would cover what is below it (bag bar, status bars, coordinates, quest tracker), and only the nudge gains the room.
-- Edit Mode: nothing is moved, restored or measured while it is open (no jump when it opens); the clamp insets of the cluster are
-- opened by the margin of the cluster around the map, so that the map can be dragged to the very corner. When Edit Mode closes
-- (and on a layout update, login, option on) the nudge is done again from what is there then. The bag bar that sat right below the
-- map follows it (anchored right aligned under the map, under the status bars of "at the minimap"). Blizzard's anchors are
-- remembered and put back (only when the frame still has exactly ours) when the option is switched off. A map or bag bar far from
-- the corner (own place from Edit Mode) stays where it is. Not in combat (caught up after the fight).
local corner = {
    applied = false, base = nil, ours = nil, tries = 0, clamp = nil, clampHooked = false, clampGuard = false,
    bagOn = false, bagBase = nil, bagOurs = nil, geo = nil, last = nil,
}
do
local CORNER_GAP = 2           -- units of UIParent between the map and the screen edge after the nudge
local CORNER_TOL = 4           -- a map already 0 to 4 units from an edge stays (a player's drag to the corner is not pushed away again)
local CORNER_NEAR = 50         -- an anchor of an own kind: only when the map is within this distance of the corner
local CORNER_OWN = 60          -- Blizzard's own anchor (TOPRIGHT of UIParent): offsets up to this count as "not placed by the player"
local BAG_GAP = 2              -- units between the map (or the status bars) and the bag bar
local BAG_BELOW = 80           -- the bag bar counts as "under the map" up to this distance below the lower edge

local function cornerSnapshot(frame)
    local points = {}
    for index = 1, frame:GetNumPoints() do
        local point, relativeTo, relativePoint, x, y = frame:GetPoint(index)
        points[#points + 1] = { n = 5, point, relativeTo, relativePoint, x, y }
    end
    return { points = points }
end

local function cornerSame(a, b)
    if not a or not b or #a.points ~= #b.points then return false end
    for index, p in ipairs(a.points) do
        local q = b.points[index]
        if p[1] ~= q[1] or p[2] ~= q[2] or p[3] ~= q[3] or math.abs((p[4] or 0) - (q[4] or 0)) > 0.05
            or math.abs((p[5] or 0) - (q[5] or 0)) > 0.05 then
            return false
        end
    end
    return true
end

local function cornerPut(frame, snapshot)
    pcall(frame.ClearAllPoints, frame)
    for _, args in ipairs(snapshot.points) do pcall(frame.SetPoint, frame, unpackArgs(args, 1, args.n)) end
end

-- The cluster still hangs where the game puts it: one anchor TOPRIGHT of UIParent with small offsets (the Edit Mode default), or the
-- layout says "default position". Then the nudge is done whatever the distance is (the gap of the map to the edge is the margin of the
-- cluster plus the header, 55 to 70 units on the Fold: a distance limit made the nudge skip itself). Anything else is a place of the
-- player and only moves when it is already near the corner.
local function cornerAtGameAnchor(snapshot)
    if not snapshot or #snapshot.points ~= 1 then return false end
    local p = snapshot.points[1]
    return p[1] == "TOPRIGHT" and p[3] == "TOPRIGHT" and (p[2] == UIParent or p[2] == nil)
        and math.abs(p[4] or 0) <= CORNER_OWN and math.abs(p[5] or 0) <= CORNER_OWN
end

local function cornerLayoutDefault()
    local ok, info = pcall(function()
        return EditModeManagerFrame and EditModeManagerFrame.GetActiveLayoutInfo and EditModeManagerFrame:GetActiveLayoutInfo()
    end)
    if not (ok and type(info) == "table" and type(info.systems) == "table" and Enum and Enum.EditModeSystem) then return nil end
    local id = Enum.EditModeSystem.Minimap
    for _, system in ipairs(info.systems) do
        if system.system == id and system.anchorInfo then return system.isInDefaultPosition and true or false end
    end
    return nil
end

-- The edges the player sees (screen units): the map; with the round map also the header strip above it and the compass ring, when they
-- are shown (what hangs out by more than 60 units is not counted: a wrong frame). Right, top.
local function cornerVisible(cluster, map)
    local _, right, top = UI.ScreenRect(map)
    if not right then return nil end
    if db and db.minimapShape == "square" and mapShape.applied then return right, top end
    local unit = UIParent.GetEffectiveScale and UIParent:GetEffectiveScale() or 1
    local limit = 60 * unit
    for _, part in ipairs({ cluster.BorderTop, frameOf("MinimapCompassTexture") }) do
        if isFrame(part) and part.IsShown and part.GetAlpha then
            local okShown, shown = pcall(part.IsShown, part)
            local okAlpha, alpha = pcall(part.GetAlpha, part)
            if okShown and shown and okAlpha and (alpha or 0) > 0.05 then
                local _, pRight, pTop = UI.ScreenRect(part)
                if pRight and pRight - right <= limit then right = math.max(right, pRight) end
                if pTop and pTop - top <= limit then top = math.max(top, pTop) end
            end
        end
    end
    return right, top
end

local function cornerRetry()
    if corner.tries >= 4 then return end
    corner.tries = corner.tries + 1
    later(1.5, function() if corner.apply then corner.apply() end end)
end

-- Clamp insets (SetClampRectInsets: positive = the frame may leave the screen by that much). The cluster may go beyond the edge by
-- the margin it has around the map. Blizzard's values are remembered (also when it sets them again) and come back when switched off.
local function cornerClampOpen(cluster, map)
    if not (cluster.GetClampRectInsets and cluster.SetClampRectInsets) then return end
    local _, cRight, cTop = UI.ScreenRect(cluster)
    local _, mRight, mTop = UI.ScreenRect(map)
    local eff = cluster.GetEffectiveScale and cluster:GetEffectiveScale() or 1
    if not cRight or not mRight or not eff or eff <= 0 then return end
    local ok, l, r, t, b = pcall(cluster.GetClampRectInsets, cluster)
    if not ok or type(r) ~= "number" or type(t) ~= "number" then return end
    if not corner.clamp then corner.clamp = { l, r, t, b } end
    if not corner.clampHooked and hooksecurefunc then
        corner.clampHooked = true
        pcall(hooksecurefunc, cluster, "SetClampRectInsets", function(_, nl, nr, nt, nb)
            if corner.clampGuard then return end
            corner.clamp = { nl, nr, nt, nb } -- Blizzard set its own values again (Edit Mode opens): remember, open again a moment later
            later(0, function() if db and db.minimapCorner ~= false and corner.openClamp then corner.openClamp() end end)
        end)
    end
    local wantRight = math.max(corner.clamp[2] or 0, math.ceil((cRight - mRight) / eff) + 1)
    local wantTop = math.max(corner.clamp[3] or 0, math.ceil((cTop - mTop) / eff) + 1)
    if math.abs(wantRight - r) < 0.5 and math.abs(wantTop - t) < 0.5 then return end
    corner.clampGuard = true
    pcall(cluster.SetClampRectInsets, cluster, l, wantRight, wantTop, b)
    corner.clampGuard = false
end

local function cornerClampBack(cluster)
    local saved = corner.clamp
    corner.clamp = nil
    if not saved or not cluster.SetClampRectInsets then return end
    corner.clampGuard = true
    pcall(cluster.SetClampRectInsets, cluster, saved[1], saved[2], saved[3], saved[4])
    corner.clampGuard = false
end

function corner.openClamp()
    if inCombat() then return end
    local cluster, map = frameOf("MinimapCluster"), frameOf("Minimap")
    if cluster and map then cornerClampOpen(cluster, map) end
end

-- What is on the frame now is ours (put Blizzard's back) or Blizzard's new state (keep it, measure from it).
local function cornerLeave()
    local cluster, bar = frameOf("MinimapCluster"), frameOf("BagsBar")
    if corner.applied and cluster and corner.base and cornerSame(cornerSnapshot(cluster), corner.ours) then cornerPut(cluster, corner.base) end
    if corner.bagOurs and bar and corner.bagBase and cornerSame(cornerSnapshot(bar), corner.bagOurs) then cornerPut(bar, corner.bagBase) end
    corner.applied, corner.ours, corner.bagOn, corner.bagOurs, corner.bagBase, corner.geo = false, nil, false, nil, nil, nil
end

-- The bag bar right aligned under the map (and under the status bars of the mode "at the minimap"). Calculated from the geometry of
-- the last nudge (corner.geo, screen units), also called when the status bars were laid out (xp.after).
function corner.bagPlace()
    local g = corner.geo
    if not (db and corner.bagOn and g) or reasons.edit then return end
    local bar = frameOf("BagsBar")
    if not (bar and bar.SetPoint and bar.ClearAllPoints) then return end
    if inCombat() then pendingCombat = true; return end
    local drop = BAG_GAP * g.unit
    if xpEffectiveMode() == "minimap" then
        local first = true
        for _, frame in ipairs(xpContainers()) do
            if xp.touched[frame] then
                drop = drop + ((frame.GetHeight and frame:GetHeight()) or 0) * ((frame.GetEffectiveScale and frame:GetEffectiveScale()) or 1)
                    + (first and 3 or 1) * g.unit
                first = false
            end
        end
    end
    local eff = bar.GetEffectiveScale and bar:GetEffectiveScale() or 1
    if not eff or eff <= 0 then return end
    local x, y = (g.mapRight - g.uiRight) / eff, (g.mapBottom - drop - g.uiTop) / eff
    local ok = pcall(function()
        bar:ClearAllPoints()
        bar:SetPoint("TOPRIGHT", UIParent, "TOPRIGHT", x, y)
    end)
    if not ok then pendingCombat = true; return end
    corner.bagOurs = cornerSnapshot(bar)
    if corner.last then corner.last.bag = string.format("%.1f,%.1f", x, y) end
end

function corner.apply()
    if not db then return end
    local cluster, map = frameOf("MinimapCluster"), frameOf("Minimap")
    if not cluster or not map or not cluster.GetPoint or not cluster.GetNumPoints or not cluster.SetPoint then return end
    local on = db.minimapCorner ~= false
    if reasons.edit then
        if on then cornerClampOpen(cluster, map) end -- only the room to drag; the place is Blizzard's / the player's
        return
    end
    if inCombat() then
        if on ~= corner.applied then pendingCombat = true end
        return
    end
    local wasApplied = corner.applied
    cornerLeave()
    if not on then
        cornerClampBack(cluster)
        if wasApplied then applyXpLater() end
        return
    end
    cornerClampOpen(cluster, map)
    local base = cornerSnapshot(cluster)
    if #base.points == 0 then return end
    local _, uiRight, uiTop = UI.ScreenRect(UIParent)
    local _, mapRight, _, mapBottom = UI.ScreenRect(map)
    local seenRight, seenTop = cornerVisible(cluster, map)
    if not uiRight or not mapRight or not seenRight then cornerRetry(); return end
    local unit = UIParent.GetEffectiveScale and UIParent:GetEffectiveScale() or 1
    local gap, near, tol = CORNER_GAP * unit, CORNER_NEAR * unit, CORNER_TOL * unit
    local gapRight, gapTop = uiRight - seenRight, uiTop - seenTop
    local ownAnchor = cornerAtGameAnchor(base)
    local layoutDefault = cornerLayoutDefault()
    local again = corner.again
    corner.again = false
    if not again then corner.verifies = 0 end
    corner.last = { gapRight = gapRight / unit, gapTop = gapTop / unit, gameAnchor = ownAnchor, layoutDefault = layoutDefault,
        round = not (db.minimapShape == "square" and mapShape.applied) }
    local far = math.abs(gapRight) > near or math.abs(gapTop) > near
    if far and not ownAnchor and layoutDefault ~= true then
        corner.last.reason = "not pushed: own place (anchor is not the game's, gap above " .. CORNER_NEAR .. ")"
        return
    end
    corner.last.reason = (ownAnchor or layoutDefault == true) and "pushed: the cluster is at the game's anchor" or "pushed: near the corner"
    local effective = cluster.GetEffectiveScale and cluster:GetEffectiveScale() or 1
    if not effective or effective <= 0 then return end
    -- the bag bar that sits right below the map follows it (measured now, before the map moves)
    local bar = frameOf("BagsBar")
    local follow = false
    if bar and bar.IsShown and bar:IsShown() then
        local _, barRight, barTop = UI.ScreenRect(bar)
        if barRight and barTop and barTop <= mapBottom + 6 * unit and barTop >= mapBottom - BAG_BELOW * unit
            and math.abs(uiRight - barRight) <= near then
            follow = true
        end
    end
    local bagBase = follow and cornerSnapshot(bar) or nil
    -- per axis: a gap between 0 and CORNER_TOL stays, otherwise the visible edge goes to CORNER_GAP
    local dx = (gapRight < 0 or gapRight > tol) and (gapRight - gap) / effective or 0
    local dy = (gapTop < 0 or gapTop > tol) and (gapTop - gap) / effective or 0
    if dx == 0 and dy == 0 then corner.last.reason = "not pushed: already at the corner (0 to " .. CORNER_TOL .. " units)" end
    if math.abs(dx) > 0.05 or math.abs(dy) > 0.05 then
        pcall(cluster.ClearAllPoints, cluster)
        for index, args in ipairs(base.points) do
            if index == 1 then
                pcall(cluster.SetPoint, cluster, args[1], args[2], args[3], (args[4] or 0) + dx, (args[5] or 0) + dy)
            else
                pcall(cluster.SetPoint, cluster, unpackArgs(args, 1, args.n))
            end
        end
    end
    corner.base, corner.ours, corner.applied, corner.tries = base, cornerSnapshot(cluster), true, 0
    corner.last.dx, corner.last.dy = dx, dy
    -- where the map ends up (calculated, screen units): the bag bar and the status bars are placed from this
    corner.geo = { uiRight = uiRight, uiTop = uiTop, unit = unit, mapRight = mapRight + dx * effective,
        mapBottom = mapBottom + dy * effective }
    if follow then corner.bagOn, corner.bagBase = true, bagBase end
    applyXpLater() -- the status bars "at the minimap" are as wide as the map; xp.after places the bag bar under them
    corner.bagPlace()
    later(0.6, corner.verify)
end

-- A moment after the nudge, from the settled layout: how far is the visible map from the edges now? (diagnosis: "after" in
-- /tf minimap info). Off by more than the tolerance (a rectangle was old, the engine clamped the cluster): the nudge is done again from
-- Blizzard's anchor, at most twice in a row.
function corner.verify()
    if not (db and corner.applied and corner.last and not reasons.edit) then return end
    local cluster, map = frameOf("MinimapCluster"), frameOf("Minimap")
    if not (cluster and map) then return end
    local _, uiRight, uiTop = UI.ScreenRect(UIParent)
    local seenRight, seenTop = cornerVisible(cluster, map)
    if not (uiRight and seenRight) then return end
    local unit = UIParent.GetEffectiveScale and UIParent:GetEffectiveScale() or 1
    local afterRight, afterTop = (uiRight - seenRight) / unit, (uiTop - seenTop) / unit
    corner.last.after = string.format("right %.1f top %.1f", afterRight, afterTop)
    local off = afterRight < -0.5 or afterRight > CORNER_TOL + 0.5 or afterTop < -0.5 or afterTop > CORNER_TOL + 0.5
    if off and (corner.verifies or 0) < 2 and not inCombat() then
        corner.verifies = (corner.verifies or 0) + 1
        corner.again = true
        corner.apply()
        corner.again = false
    end
end
xp.after = function() corner.bagPlace() end
end -- of the minimap corner block

-- The map flush in its cluster first (the nudge measures the map), then the nudge. A moment later once more from the settled layout:
-- rectangles read right after a change can be old, the flush corrects its rest step by step (three times at most).
mapShape.settleSteps = 0
function mapShape.settle()
    if not db or reasons.edit or inCombat() then return end
    local cluster, map = frameOf("MinimapCluster"), frameOf("Minimap")
    local moved = cluster and map and mapShape.flushNow(cluster, map)
    if moved then corner.apply() end
    if moved and mapShape.settleSteps < 3 then
        mapShape.settleSteps = mapShape.settleSteps + 1
        later(0.6, mapShape.settle)
    elseif not moved then
        corner.verify()
    end
end

local function applyMinimapShape()
    applyMapShape()
    local cluster, map = frameOf("MinimapCluster"), frameOf("Minimap")
    if cluster and map and not reasons.edit and not inCombat() then mapShape.flushNow(cluster, map) end
    corner.apply()
    applyMinimapZoom()
    mapShape.settleSteps = 0
    later(0.6, mapShape.settle)
end

-- Blizzard laid out again the frames in [reset]: forget their snapshots and put ours back.
local function minimapLayoutChanged(reset)
    if not (db and mapShape.applied and db.minimapShape == "square") then return end
    for key in pairs(reset) do mapShape.saved[key] = nil end
    if reset.MinimapContainer then mapShape.flush = { x = 0, y = 0 } end -- Blizzard's new anchor: measured again from scratch
    local kept = {}
    for _, key in ipairs(mapShape.order) do
        if mapShape.saved[key] then kept[#kept + 1] = key end
    end
    mapShape.order = kept
    mapShape.applied = false
    applyMinimapShape()
end

local function hookMinimapLayout()
    if mapShape.hooked or not hooksecurefunc then return end
    mapShape.hooked = true
    local cluster = frameOf("MinimapCluster")
    if cluster and cluster.SetHeaderUnderneath then
        pcall(hooksecurefunc, cluster, "SetHeaderUnderneath", function() minimapLayoutChanged(MAP_RESET_BY_LAYOUT) end)
    end
    if type(MiniMapIndicatorFrame_UpdatePosition) == "function" then
        pcall(hooksecurefunc, "MiniMapIndicatorFrame_UpdatePosition", function() minimapLayoutChanged(MAP_RESET_BY_INDICATOR) end)
    end
end

-- For "/tf minimap info": what the cluster is made of on this client (names, type, shown, alpha, size, first anchor).
local function describeFrame(label, f)
    local function call(method, ...)
        if not f[method] then return nil end
        local ok, a, b, c, d, e = pcall(f[method], f, ...)
        if ok then return a, b, c, d, e end
    end
    local kind = call("GetObjectType") or "?"
    local name = call("GetName") or "-"
    local shown = call("IsShown")
    local alpha = call("GetAlpha")
    local width, height = call("GetWidth"), call("GetHeight")
    local point, rel, relPoint, x, y = call("GetPoint", 1)
    local relName = type(rel) == "table" and rel.GetName and (rel:GetName() or "?") or "-"
    return string.format("%s [%s %s] shown=%s alpha=%s %sx%s %s>%s:%s %s,%s", label, kind, name, tostring(shown),
        alpha and string.format("%.2f", alpha) or "-", width and string.format("%.0f", width) or "-",
        height and string.format("%.0f", height) or "-", tostring(point), relName, tostring(relPoint),
        x and string.format("%.0f", x) or "-", y and string.format("%.0f", y) or "-")
end

-- Rectangle of a frame as text: left/right/top/bottom in UIParent units (nil: not laid out yet).
local function describeRect(label, frame)
    local l, r, t, b = UI.ScreenRect(frame)
    if not l then return label .. ": no rectangle" end
    local unit = UIParent.GetEffectiveScale and UIParent:GetEffectiveScale() or 1
    return string.format("%s: L%.1f R%.1f T%.1f B%.1f (%.1fx%.1f)", label, l / unit, r / unit, t / unit, b / unit, (r - l) / unit, (t - b) / unit)
end

-- "/tf minimap info": the geometry that decides the corner: rectangles, gaps to the screen edge, clamp insets, anchors of cluster and
-- bag bar (ours and Blizzard's), the anchor the Edit Mode layout holds for the minimap and the bag bar.
function UI.DescribeCorner(cluster, map)
    local lines = {}
    local unit = UIParent.GetEffectiveScale and UIParent:GetEffectiveScale() or 1
    lines[#lines + 1] = describeRect("UIParent", UIParent)
    lines[#lines + 1] = describeRect("cluster rect", cluster)
    if map then
        lines[#lines + 1] = describeRect("map rect", map)
        local _, ur, ut = UI.ScreenRect(UIParent)
        local _, mr, mt = UI.ScreenRect(map)
        if ur and mr then lines[#lines + 1] = string.format("gap map-screen: right %.1f top %.1f units (goal 0 to 4)", (ur - mr) / unit, (ut - mt) / unit) end
    end
    if cluster.GetClampRectInsets then
        local ok, l, r, t, b = pcall(cluster.GetClampRectInsets, cluster)
        local saved = corner.clamp
        if ok then
            lines[#lines + 1] = string.format("clamp insets L%s R%s T%s B%s (Blizzard: %s) clamped=%s", tostring(l), tostring(r), tostring(t), tostring(b),
                saved and string.format("%s,%s,%s,%s", tostring(saved[1]), tostring(saved[2]), tostring(saved[3]), tostring(saved[4])) or "not changed",
                tostring(cluster.IsClampedToScreen and cluster:IsClampedToScreen()))
        end
    end
    local function anchors(label, frame, snapshotBase, snapshotOurs)
        if not (frame and frame.GetNumPoints) then return end
        local parts = {}
        for index = 1, frame:GetNumPoints() do
            local point, rel, relPoint, x, y = frame:GetPoint(index)
            parts[#parts + 1] = string.format("%s>%s:%s %.1f,%.1f", tostring(point), type(rel) == "table" and rel.GetName and tostring(rel:GetName()) or "-",
                tostring(relPoint), x or 0, y or 0)
        end
        lines[#lines + 1] = label .. " anchors: " .. table.concat(parts, " | ") .. (snapshotBase and " (ours, Blizzard's put back when off)" or "")
    end
    anchors("cluster", cluster, corner.applied and corner.base)
    local bar = frameOf("BagsBar")
    if bar then
        lines[#lines + 1] = describeRect("BagsBar rect", bar)
        anchors("BagsBar", bar, corner.bagOn and corner.bagBase)
        lines[#lines + 1] = string.format("BagsBar width=%s want=%s fitted=%s follows map=%s", bar.GetWidth and string.format("%.1f", bar:GetWidth()) or "-",
            bags.wanted() and string.format("%.1f", bags.wanted()) or "-", tostring(bags.fitted()), tostring(corner.bagOn))
        for _, line in ipairs(bags.parts(bar)) do lines[#lines + 1] = line end
    end
    local last = corner.last
    if last then
        lines[#lines + 1] = string.format("last nudge: gap before right %.1f top %.1f, moved dx %s dy %s, bag bar anchor %s", last.gapRight or 0, last.gapTop or 0,
            last.dx and string.format("%.1f", last.dx) or "-", last.dy and string.format("%.1f", last.dy) or "-", tostring(last.bag))
        lines[#lines + 1] = string.format("corner decision: %s | game anchor=%s layout default=%s | gap after: %s", tostring(last.reason),
            tostring(last.gameAnchor), tostring(last.layoutDefault), tostring(last.after or "not measured yet"))
    else
        lines[#lines + 1] = "corner decision: no nudge measured yet"
    end
    local res = mapShape.residual
    lines[#lines + 1] = string.format("map in cluster: moved by x %.1f y %.1f (container units), rest right %s top %s units", mapShape.flush.x,
        mapShape.flush.y, res and string.format("%.1f", res.x) or "-", res and string.format("%.1f", res.y) or "-")
    -- the anchors the Edit Mode layout holds for these two systems
    local okLayout, info = pcall(function()
        return EditModeManagerFrame and EditModeManagerFrame.GetActiveLayoutInfo and EditModeManagerFrame:GetActiveLayoutInfo()
    end)
    if okLayout and type(info) == "table" and type(info.systems) == "table" and Enum and Enum.EditModeSystem then
        for _, key in ipairs({ "Minimap", "BagsBar" }) do
            local id = Enum.EditModeSystem[key]
            for _, system in ipairs(info.systems) do
                if system.system == id and system.anchorInfo then
                    local a = system.anchorInfo
                    lines[#lines + 1] = string.format("Edit Mode layout %s: %s>%s:%s %.1f,%.1f default=%s", key, tostring(a.point), tostring(a.relativeTo),
                        tostring(a.relativePoint), a.offsetX or 0, a.offsetY or 0, tostring(system.isInDefaultPosition))
                end
            end
        end
    else
        lines[#lines + 1] = "Edit Mode layout: not readable"
    end
    return lines
end

function UI.DescribeMinimap()
    local lines = {}
    local cluster, map = frameOf("MinimapCluster"), frameOf("Minimap")
    lines[#lines + 1] = string.format("shape=%s zoom buttons=%s applied=%s corner=%s (placed %s)", UI.GetMinimapShape(),
        db and db.zoomBig ~= false and "big" or "standard", tostring(mapShape.applied), UI.GetMinimapCorner() and "on" or "off",
        tostring(corner.applied))
    if not cluster then
        lines[#lines + 1] = "MinimapCluster: not found"
        for _, line in ipairs(bags.describe()) do lines[#lines + 1] = line end
        return lines
    end
    lines[#lines + 1] = describeFrame("MinimapCluster", cluster)
    if map then lines[#lines + 1] = describeFrame("Minimap", map) end
    for _, line in ipairs(UI.DescribeCorner(cluster, map)) do lines[#lines + 1] = line end
    local keys = {}
    local okPairs = pcall(function()
        for key, value in pairs(cluster) do
            if type(key) == "string" and isFrame(value) then keys[#keys + 1] = key end
        end
    end)
    if okPairs then table.sort(keys) end
    for _, key in ipairs(keys) do lines[#lines + 1] = describeFrame("cluster." .. key, cluster[key]) end
    if cluster.GetRegions then
        local regions = { cluster:GetRegions() }
        for index, region in ipairs(regions) do
            if isFrame(region) then lines[#lines + 1] = describeFrame("region" .. index, region) end
        end
    end
    for _, name in ipairs({ "GameTimeFrame", "TimeManagerClockButton", "MinimapZoomIn", "MinimapZoomOut" }) do
        local f = frameOf(name)
        if f then lines[#lines + 1] = describeFrame(name, f) end
    end
    for _, line in ipairs(bags.describe()) do lines[#lines + 1] = line end
    return lines
end

---------------------------------------------------------------------------
-- World map ("Map & Quest Log", also maximised). Blizzard sizes it for a wide screen: on the Fold's inner screen (about 1010 to 1100
-- units wide at 720p) the right side with the close button hangs out of the picture. The frame is scaled down until it fits into the
-- screen (width and height, a small margin all round) and moved in if it still sticks out. Sizes are compared in units of UIParent
-- (own size * scale * the scale of the parents). Blizzard's own scale is remembered (post hook on SetScale) and comes back when the
-- option is switched off. Not in combat (caught up after it).
---------------------------------------------------------------------------
local MAP_MARGIN = 4
local MAP_SCALE_MIN = 0.4
local mapFit = { base = nil, hooked = false, guard = false }

local function num(value)
    if type(value) == "number" and value == value then return value end
end

function UI.GetMapFit()
    return db == nil or db.mapFit ~= false
end

-- Size of the frame in UIParent units at the scale [scale] (nil = the current one), the factor "UIParent units per own unit and
-- scale" of its parents, and the current scale.
local function mapExtent(map, scale)
    local width, height, current = num(map:GetWidth()), num(map:GetHeight()), num(map:GetScale())
    local effective, parentEffective = num(map:GetEffectiveScale()), num(UIParent:GetEffectiveScale())
    if not (width and height and current and effective and parentEffective) or current <= 0 or parentEffective <= 0 then return end
    local ancestors = effective / parentEffective / current -- 1 when the parent is UIParent
    scale = scale or current
    return width * scale * ancestors, height * scale * ancestors, ancestors, current
end

local function mapApplyFit()
    if not db then return end
    local map = frameOf("WorldMapFrame")
    if not (map and map.GetWidth and map.GetHeight and map.GetScale and map.SetScale and map.GetEffectiveScale and UIParent
        and UIParent.GetEffectiveScale and GetScreenWidth and GetScreenHeight) then return end
    local screenW, screenH = num(GetScreenWidth()), num(GetScreenHeight())
    if not (screenW and screenH) or screenW <= 0 or screenH <= 0 then return end
    local _, _, _, current = mapExtent(map)
    if not current then return end
    if mapFit.base == nil then mapFit.base = current end
    local base = mapFit.base
    local wanted = base
    if UI.GetMapFit() then
        local w, h = mapExtent(map, base)
        if not w or w <= 0 or h <= 0 then return end
        local fit = math.min(1, (screenW - 2 * MAP_MARGIN) / w, (screenH - 2 * MAP_MARGIN) / h)
        wanted = math.max(base * MAP_SCALE_MIN, base * fit)
    end
    if inCombat() then pendingCombat = true; return end
    if math.abs(wanted - current) > 0.002 then
        mapFit.guard = true
        pcall(map.SetScale, map, wanted)
        mapFit.guard = false
    end
    if not UI.GetMapFit() or not (map.GetRect and map.GetNumPoints and map.GetPoint and map.SetPoint and map.ClearAllPoints) then return end
    -- Still outside (the anchor of the panel did not expect the new scale)? One anchor can be shifted.
    local okRect, left, bottom, width, height = pcall(map.GetRect, map)
    left, bottom, width, height = num(left), num(bottom), num(width), num(height)
    if not (okRect and left and bottom and width and height) then return end
    local _, _, ancestors, scale = mapExtent(map)
    if not scale then return end
    local ratio = scale * ancestors -- UIParent units per own unit
    local dx, dy = 0, 0
    if (left + width) * ratio > screenW - MAP_MARGIN then dx = screenW - MAP_MARGIN - (left + width) * ratio end
    if left * ratio + dx < MAP_MARGIN then dx = MAP_MARGIN - left * ratio end
    if (bottom + height) * ratio > screenH - MAP_MARGIN then dy = screenH - MAP_MARGIN - (bottom + height) * ratio end
    if bottom * ratio + dy < MAP_MARGIN then dy = MAP_MARGIN - bottom * ratio end
    if math.abs(dx) < 0.5 and math.abs(dy) < 0.5 then return end
    local okCount, count = pcall(map.GetNumPoints, map)
    if not okCount or count ~= 1 then return end
    local okPoint, point, relative, relativePoint, x, y = pcall(map.GetPoint, map, 1)
    if not okPoint or type(point) ~= "string" or type(x) ~= "number" or type(y) ~= "number" then return end
    pcall(function()
        map:ClearAllPoints()
        map:SetPoint(point, relative, relativePoint, x + dx / ratio, y + dy / ratio)
    end)
end

local function mapFitLater()
    later(0, function() if db then mapApplyFit() end end)
end

-- The map frame is created with its load-on-demand addon: tried at start-up and after every addon that loads.
local function hookMap()
    if mapFit.hooked then return end
    local map = frameOf("WorldMapFrame")
    if not (map and map.HookScript and hooksecurefunc) then return end
    mapFit.hooked = true
    pcall(map.HookScript, map, "OnShow", mapFitLater)
    pcall(map.HookScript, map, "OnSizeChanged", mapFitLater)
    pcall(hooksecurefunc, map, "SetScale", function(_, scale)
        if mapFit.guard or type(scale) ~= "number" then return end
        mapFit.base = scale -- Blizzard chose a scale: that is the one to fit from
        mapFitLater()
    end)
    if map.IsShown and map:IsShown() then mapFitLater() end
end

function UI.SetMapFit(on)
    if not db then return end
    db.mapFit = on and true or false
    hookMap()
    mapApplyFit()
end

---------------------------------------------------------------------------
-- Chat
---------------------------------------------------------------------------
local CHAT_PRESET = { size = 10, lines = 8, bg = 20, filter = true, short = true, stamps = false }
local OLD_CHAT_PRESET = { size = 12, lines = 5 } -- preset of addon 1.1.0 (migrated once, see ADDON_LOADED)
local CHAT_SIZE_MIN, CHAT_SIZE_MAX = 6, 24
local CHAT_LINES_MIN, CHAT_LINES_MAX = 3, 20
-- "Compact" chat: the space of the buttons column (29 + gap) goes to the chat; Blizzard's buttons next to the chat are taken away.
local CHAT_EXTRA_DEFAULT, CHAT_EXTRA_MAX = 36, 120

-- Message groups taken out of the main window (reversible); whisper, party, raid, guild, say, yell stay.
local FILTER_GROUPS = {
    "SYSTEM", "LOOT", "MONEY", "COMBAT_XP_GAIN", "COMBAT_HONOR_GAIN", "COMBAT_FACTION_CHANGE", "SKILL", "TRADESKILLS",
    "OPENING", "PET_INFO", "COMBAT_MISC_INFO", "CURRENCY", "ACHIEVEMENT", "TARGETICONS",
}
-- Channels (trade, general ...) are named by language; the beginning of the name decides.
local FILTER_CHANNELS = {
    "trade", "handel", "general", "allgemein", "localdefense", "lokale verteidigung", "worlddefense", "weltverteidigung",
    "services", "dienste", "lookingforgroup", "gruppensuche",
}

-- Short channel tags: format strings of the chat (global strings), original kept for the reset.
local SHORT_TAGS = {
    CHAT_SAY_GET = "%s: ",
    CHAT_YELL_GET = "[Y] %s: ",
    CHAT_PARTY_GET = "|Hchannel:PARTY|h[P]|h %s: ",
    CHAT_PARTY_LEADER_GET = "|Hchannel:PARTY|h[PL]|h %s: ",
    CHAT_PARTY_GUIDE_GET = "|Hchannel:PARTY|h[PL]|h %s: ",
    CHAT_RAID_GET = "|Hchannel:RAID|h[R]|h %s: ",
    CHAT_RAID_LEADER_GET = "|Hchannel:RAID|h[RL]|h %s: ",
    CHAT_RAID_WARNING_GET = "[RW] %s: ",
    CHAT_INSTANCE_CHAT_GET = "|Hchannel:INSTANCE_CHAT|h[I]|h %s: ",
    CHAT_INSTANCE_CHAT_LEADER_GET = "|Hchannel:INSTANCE_CHAT|h[IL]|h %s: ",
    CHAT_GUILD_GET = "|Hchannel:GUILD|h[G]|h %s: ",
    CHAT_OFFICER_GET = "|Hchannel:OFFICER|h[O]|h %s: ",
    CHAT_WHISPER_GET = "[W] %s: ",
    CHAT_WHISPER_INFORM_GET = "[W>] %s: ",
    CHAT_BN_WHISPER_GET = "[W] %s: ",
    CHAT_BN_WHISPER_INFORM_GET = "[W>] %s: ",
}
local shortOriginals = {}

local chatState = { originalFont = {}, originalAlpha = {}, originalClamp = {}, originalHeight = nil, originalWidth = nil, widthGuard = false, widthHooked = false, removed = nil, stampsOriginal = nil }

local function chatWindowCount()
    return NUM_CHAT_WINDOWS or 10
end

-- Chat windows and their dock (hidden together with the chat); the tabs have a group of their own.
local chatNames
local function chatFrameNames()
    if not chatNames then
        chatNames = { "GeneralDockManager" }
        for index = 1, chatWindowCount() do chatNames[#chatNames + 1] = "ChatFrame" .. index end
    end
    return chatNames
end

local tabNames
local function chatTabNames()
    if not tabNames then
        tabNames = {}
        for index = 1, chatWindowCount() do tabNames[#tabNames + 1] = "ChatFrame" .. index .. "Tab" end
    end
    return tabNames
end

-- The things beside the chat that "compact" takes away (12.x: ChatFrame1ButtonFrame holds the menu button, the channel button and
-- the minimize button; QuickJoinToastButton is the little man for social / quick join; the voice buttons and the text to speech
-- button hang beside it). Older clients also have ChatFrameNButtonFrame with up / down / bottom buttons.
local sideNames
local function sideFrameNames()
    if not sideNames then
        sideNames = {
            "ChatFrameMenuButton", "ChatFrameChannelButton", "QuickJoinToastButton", "TextToSpeechButtonFrame",
            "ChatFrameToggleVoiceDeafenButton", "ChatFrameToggleVoiceMuteButton",
        }
        for index = 1, chatWindowCount() do sideNames[#sideNames + 1] = "ChatFrame" .. index .. "ButtonFrame" end
    end
    return sideNames
end

local sideMouseNames
local function sideMouseFrameNames()
    if not sideMouseNames then
        sideMouseNames = {}
        for _, name in ipairs(sideFrameNames()) do sideMouseNames[#sideMouseNames + 1] = name end
        sideMouseNames[#sideMouseNames + 1] = "TextToSpeechButton"
        for index = 1, chatWindowCount() do
            local base = "ChatFrame" .. index .. "ButtonFrame"
            for _, part in ipairs({ "UpButton", "DownButton", "BottomButton", "MinimizeButton" }) do
                sideMouseNames[#sideMouseNames + 1] = base .. part
            end
        end
    end
    return sideMouseNames
end

local chatMouseNames
local function chatMouseFrameNames()
    if not chatMouseNames then
        chatMouseNames = {}
        for _, name in ipairs(chatFrameNames()) do chatMouseNames[#chatMouseNames + 1] = name end
    end
    return chatMouseNames
end

local chatGroup = newGroup(chatFrameNames, chatMouseFrameNames)
local sideGroup = newGroup(sideFrameNames, sideMouseFrameNames)
local tabGroup = newGroup(chatTabNames)
local tabsTouched = false          -- the mouse / finger is on the chat: the tabs show (compact chat)
local fadeToken = 0

local function chatEditing()
    for index = 1, chatWindowCount() do
        local box = _G["ChatFrame" .. index .. "EditBox"]
        if box and box.IsShown and box:IsShown() then return true end
    end
    return false
end

local function chatOptions()
    db.chat = db.chat or {}
    return db.chat
end

-- "Chat kompakt": no buttons beside the chat, more width for the chat, tabs only on touch. Independent of the phone preset.
local function chatCompact()
    return chatOptions().compact ~= false
end

local function chatTabsOnTouch()
    return chatCompact() and chatOptions().tabsTouch ~= false
end

local function chatExtraWidth()
    if not chatCompact() then return 0 end
    local extra = chatOptions().extra
    if type(extra) ~= "number" then return CHAT_EXTRA_DEFAULT end
    return math.max(0, math.min(CHAT_EXTRA_MAX, extra))
end

-- Option value; none while the smart chat is switched off (then everything is at Blizzard's values).
local function chatOpt(key)
    local o = chatOptions()
    if o.enabled then return o[key] end
end

local function chatTarget()
    local forced = chatEditing() or now() < peekUntil.chat
    if db.chatHidden and not forced then return 0 end
    if chatOptions().mode == "compact" and not forced and now() >= chatActiveUntil then return 0 end
    return 1
end

-- One place sets how visible the chat is: the windows and the dock follow [factor], the side buttons only when the chat is
-- not "compact", the tabs also wait for a touch.
local function setChatFactor(factor)
    chatGroup:setFactor(factor)
    sideGroup:setFactor(chatCompact() and 0 or factor)
    tabGroup:setFactor((chatTabsOnTouch() and not tabsTouched) and 0 or factor)
end

local function chatFadeTo(target)
    fadeToken = fadeToken + 1
    local token = fadeToken
    local start = chatGroup.factor
    if target >= start then
        setChatFactor(target) -- showing is immediate
        return
    end
    local steps = 6 -- hiding fades out in a few steps
    for step = 1, steps do
        later(0.08 * step, function()
            if token == fadeToken then setChatFactor(start + (target - start) * step / steps) end
        end)
    end
end

local function refreshChat()
    if not db then return end
    local target = chatTarget()
    if chatGroup.factor ~= target then chatFadeTo(target) else setChatFactor(chatGroup.factor) end
    local ok = chatGroup:setMouse(target > 0)
    ok = sideGroup:setMouse(target > 0 and not chatCompact()) and ok
    ok = tabGroup:setMouse(target > 0 and not (chatTabsOnTouch() and not tabsTouched)) and ok
    if not ok then pendingCombat = true end
end

local function wakeChat()
    chatActiveUntil = now() + CHAT_IDLE_SECONDS
    refreshChat()
    later(CHAT_IDLE_SECONDS, refreshChat)
end

-- Font size of all chat windows.
local function applyChatSize()
    local size = chatOpt("size")
    for index = 1, chatWindowCount() do
        local frame = frameOf("ChatFrame" .. index)
        if frame and frame.GetFont then
            local font, current, flags = frame:GetFont()
            if size and size ~= current then
                if chatState.originalFont[index] == nil then chatState.originalFont[index] = current end
                if FCF_SetChatWindowFontSize then
                    pcall(FCF_SetChatWindowFontSize, nil, frame, size)
                else
                    pcall(frame.SetFont, frame, font, size, flags)
                end
            elseif not size and chatState.originalFont[index] then
                local original = chatState.originalFont[index]
                chatState.originalFont[index] = nil
                if FCF_SetChatWindowFontSize then
                    pcall(FCF_SetChatWindowFontSize, nil, frame, original)
                else
                    pcall(frame.SetFont, frame, font, original, flags)
                end
            end
        end
    end
end

-- Number of visible lines of the main window: its height follows the font size.
local function applyChatLines()
    local frame = frameOf("ChatFrame1")
    if not frame or not frame.SetHeight then return end
    local lines = chatOpt("lines")
    if lines then
        if chatState.originalHeight == nil then chatState.originalHeight = frame:GetHeight() end
        local _, size = frame:GetFont()
        pcall(frame.SetHeight, frame, lines * ((size or 12) + 3))
    elseif chatState.originalHeight then
        pcall(frame.SetHeight, frame, chatState.originalHeight)
        chatState.originalHeight = nil
    else
        return
    end
    if FCF_SavePositionAndDimensions then pcall(FCF_SavePositionAndDimensions, frame) end
    if frame.ScrollToBottom then pcall(frame.ScrollToBottom, frame) end
end

-- Width of the main window: Blizzard's width plus the space of the removed buttons (compact chat). Edit Mode sets the width
-- again on layout changes; a hook remembers its value, applyChatAll runs again after the layout update.
local applyChatWidth

local function hookChatWidth(frame)
    if chatState.widthHooked then return end
    chatState.widthHooked = true
    local function remember(self, width)
        if chatState.widthGuard or type(width) ~= "number" then return end
        chatState.originalWidth = width
        later(0, function() if db then applyChatWidth() end end)
    end
    pcall(hooksecurefunc, frame, "SetWidth", remember)
    pcall(hooksecurefunc, frame, "SetSize", remember)
end

applyChatWidth = function()
    local frame = frameOf("ChatFrame1")
    if not frame or not frame.SetWidth or not frame.GetWidth then return end
    hookChatWidth(frame)
    local extra = chatExtraWidth()
    if extra > 0 then
        if chatState.originalWidth == nil then chatState.originalWidth = frame:GetWidth() end
        if inCombat() then pendingCombat = true; return end
        chatState.widthGuard = true
        pcall(frame.SetWidth, frame, chatState.originalWidth + extra)
        chatState.widthGuard = false
    elseif chatState.originalWidth ~= nil then
        if inCombat() then pendingCombat = true; return end
        chatState.widthGuard = true
        pcall(frame.SetWidth, frame, chatState.originalWidth)
        chatState.widthGuard = false
        chatState.originalWidth = nil
    else
        return
    end
    if FCF_SavePositionAndDimensions then pcall(FCF_SavePositionAndDimensions, frame) end
end

local applyChatClamp

-- Clamp insets of the chat windows. Blizzard lets a chat window leave the screen by 35 units less on the left than on the other
-- sides (SetClampRectInsets(-35, 35, 26, -50)): the room of the button column beside it. The compact chat has no such column, so
-- the left inset becomes 0 and the window can be pushed flush to the left edge (Edit Mode too). Back to standard: the saved
-- original is set again. Not in combat (caught up after it).
local clampHooked = {}

-- Blizzard (layout changes, Edit Mode opening) sets the standard insets again now and then: a hook catches that and runs the clamp
-- again a moment later (never inside Blizzard's own call, never in a loop: our own value has left = 0).
local function hookChatClamp(index, frame)
    if clampHooked[index] or not hooksecurefunc then return end
    clampHooked[index] = true
    pcall(hooksecurefunc, frame, "SetClampRectInsets", function(_, left)
        if not db or type(left) ~= "number" or left >= 0 or not chatCompact() then return end
        later(0, function() if db then applyChatClamp() end end)
    end)
end

applyChatClamp = function()
    if not db then return end
    local compact = chatCompact()
    for index = 1, chatWindowCount() do
        local frame = frameOf("ChatFrame" .. index)
        if frame and frame.GetClampRectInsets and frame.SetClampRectInsets then
            hookChatClamp(index, frame)
            local ok, left, right, top, bottom = pcall(frame.GetClampRectInsets, frame)
            if ok and type(left) == "number" and type(right) == "number" and type(top) == "number" and type(bottom) == "number" then
                local saved = chatState.originalClamp[index]
                if compact then
                    if left < 0 then -- Blizzard's value (again): remember it, then take the room away
                        if inCombat() then pendingCombat = true; return end
                        chatState.originalClamp[index] = { left, right, top, bottom }
                        pcall(frame.SetClampRectInsets, frame, 0, right, top, bottom)
                    end
                elseif saved then
                    if inCombat() then pendingCombat = true; return end
                    chatState.originalClamp[index] = nil
                    pcall(frame.SetClampRectInsets, frame, saved[1], right, top, bottom)
                end
            end
        end
    end
end

-- Flush left: Blizzard keeps the main chat window about 35 units from the left edge (the room of the button column; the selection
-- of the window in Edit Mode includes it). The compact chat has no such column, so a window that sits near the edge is moved the last
-- bit, and Edit Mode can no longer leave a gap. Only when the window is close (a place chosen on purpose further in stays), not while
-- Edit Mode is open (the player is dragging), not in combat. "chatDock(true)" (the settings button) moves it from anywhere.
local CHAT_DOCK_SNAP = 44

local function chatDockWanted()
    return chatCompact() and chatOptions().dockLeft ~= false
end

local function chatDock(force)
    if not db or reasons.edit then return false end
    if not force and not chatDockWanted() then return false end
    local frame = frameOf("ChatFrame1")
    if not (frame and frame.GetRect and frame.GetPoint and frame.GetNumPoints and frame.SetPoint and frame.ClearAllPoints) then return false end
    local okRect, left = pcall(frame.GetRect, frame)
    if not okRect or type(left) ~= "number" or left ~= left or left <= 0.5 then return false end
    if not force and left > CHAT_DOCK_SNAP then return false end
    local okCount, count = pcall(frame.GetNumPoints, frame)
    if not okCount or count ~= 1 then return false end -- more than one anchor: not ours to shift
    local okPoint, point, relative, relativePoint, x, y = pcall(frame.GetPoint, frame, 1)
    if not okPoint or type(point) ~= "string" or type(x) ~= "number" or type(y) ~= "number" then return false end
    if inCombat() then pendingCombat = true; return false end
    local okSet = pcall(function()
        frame:ClearAllPoints()
        frame:SetPoint(point, relative, relativePoint, x - left, y)
    end)
    if not okSet then return false end
    if FCF_SavePositionAndDimensions then pcall(FCF_SavePositionAndDimensions, frame) end
    return true
end

-- Background opacity (percent) of all chat windows.
local function applyChatBackground()
    local percent = chatOpt("bg")
    for index = 1, chatWindowCount() do
        local frame = frameOf("ChatFrame" .. index)
        if frame and FCF_SetWindowAlpha then
            if percent then
                if chatState.originalAlpha[index] == nil then
                    chatState.originalAlpha[index] = frame.oldAlpha or DEFAULT_CHATFRAME_ALPHA or 0.25
                end
                pcall(FCF_SetWindowAlpha, frame, percent / 100)
            elseif chatState.originalAlpha[index] then
                pcall(FCF_SetWindowAlpha, frame, chatState.originalAlpha[index])
                chatState.originalAlpha[index] = nil
            end
        end
    end
end

-- Spam out of the main window: message groups and channels, remembered so that "off" puts exactly these back.
local function channelIsSpam(name)
    local lower = string.lower(name or "")
    for _, prefix in ipairs(FILTER_CHANNELS) do
        if string.find(lower, prefix, 1, true) == 1 or string.find(lower, "%d+%.%s*" .. prefix) == 1 then return true end
    end
    return false
end

local function applyChatFilter()
    local frame = frameOf("ChatFrame1")
    if not frame then return end
    if chatOpt("filter") then
        if chatState.removed then return end
        local removed = { groups = {}, channels = {} }
        local present = {}
        if frame.messageTypeList then
            for _, group in ipairs(frame.messageTypeList) do present[group] = true end
        end
        if ChatFrame_RemoveMessageGroup then
            for _, group in ipairs(FILTER_GROUPS) do
                if present[group] then
                    pcall(ChatFrame_RemoveMessageGroup, frame, group)
                    removed.groups[#removed.groups + 1] = group
                end
            end
        end
        if ChatFrame_RemoveChannel and frame.channelList then
            local names = {}
            for _, name in ipairs(frame.channelList) do names[#names + 1] = name end
            for _, name in ipairs(names) do
                if channelIsSpam(name) then
                    pcall(ChatFrame_RemoveChannel, frame, name)
                    removed.channels[#removed.channels + 1] = name
                end
            end
        end
        chatState.removed = removed
        db.chatRemoved = removed -- survives /reload, so "off" also works in the next session
    else
        local removed = chatState.removed or db.chatRemoved
        if not removed then return end
        for _, group in ipairs(removed.groups or {}) do
            if ChatFrame_AddMessageGroup then pcall(ChatFrame_AddMessageGroup, frame, group) end
        end
        for _, name in ipairs(removed.channels or {}) do
            if ChatFrame_AddChannel then pcall(ChatFrame_AddChannel, frame, name) end
        end
        chatState.removed = nil
        db.chatRemoved = nil
    end
end

local function applyChatShortTags()
    if chatOpt("short") then
        for key, value in pairs(SHORT_TAGS) do
            if _G[key] ~= nil then
                if shortOriginals[key] == nil then shortOriginals[key] = _G[key] end
                _G[key] = value
            end
        end
    else
        for key, value in pairs(shortOriginals) do _G[key] = value end
        shortOriginals = {}
    end
end

local function applyChatStamps()
    if chatOpt("stamps") == false then
        if chatState.stampsOriginal == nil then
            chatState.stampsOriginal = { cvar = GetCVar and GetCVar("showTimestamps"), format = CHAT_TIMESTAMP_FORMAT }
        end
        if SetCVar then pcall(SetCVar, "showTimestamps", "none") end
        CHAT_TIMESTAMP_FORMAT = nil
    elseif chatState.stampsOriginal then
        local original = chatState.stampsOriginal
        chatState.stampsOriginal = nil
        if SetCVar and original.cvar then pcall(SetCVar, "showTimestamps", original.cvar) end
        CHAT_TIMESTAMP_FORMAT = original.format
    end
end

local function applyChatAll()
    if not db then return end
    applyChatSize()
    applyChatLines()
    applyChatWidth()
    applyChatClamp()
    chatDock(false)
    applyChatBackground()
    applyChatFilter()
    applyChatShortTags()
    applyChatStamps()
    refreshChat()
end

---------------------------------------------------------------------------
-- Setters used by the options page and the window (same effect as the /tf chat commands)
---------------------------------------------------------------------------
UI.ChatOptions = chatOptions
function UI.GetDB() return db end

-- Phone preset on (values of the preset) / off (everything back at the values of the game).
function UI.ChatPhone(on)
    local o = chatOptions()
    if on then
        o.enabled = true
        for key, value in pairs(CHAT_PRESET) do o[key] = value end
    else
        o.enabled = false
    end
    applyChatAll()
end

-- "Chat links andocken" (settings button, /tf chat left): the main window flush to the left edge, from wherever it is.
function UI.ChatDockNow()
    if not db then return false end
    local moved = chatDock(true)
    notice(moved and L.chat_is_now_flush_left or L.chat_is_already_at_the_left)
    return moved
end

function UI.ChatSet(key, value)
    local o = chatOptions()
    if key == "mode" then
        o.mode = (value == "compact") and "compact" or "normal"
        chatActiveUntil = now() + CHAT_IDLE_SECONDS
        later(CHAT_IDLE_SECONDS, refreshChat)
    elseif key == "filter" or key == "short" then
        o[key] = value and true or nil
        o.enabled = true
    elseif key == "stamps" then
        o.stamps = value and true or false
        o.enabled = true
    elseif key == "size" then
        o.size = math.max(CHAT_SIZE_MIN, math.min(CHAT_SIZE_MAX, math.floor(value)))
        o.enabled = true
    elseif key == "lines" then
        o.lines = math.max(CHAT_LINES_MIN, math.min(CHAT_LINES_MAX, math.floor(value)))
        o.enabled = true
    elseif key == "compact" then
        o.compact = value and true or false
    elseif key == "tabs" then
        o.tabsTouch = value and true or false
    elseif key == "dock" then
        o.dockLeft = value and true or false
    elseif key == "extra" then
        o.extra = math.max(0, math.min(CHAT_EXTRA_MAX, math.floor(value)))
    elseif key == "bg" then
        o.bg = math.max(0, math.min(100, math.floor(value)))
        o.enabled = true
    end
    applyChatAll()
end

---------------------------------------------------------------------------
-- Peek and toggles
---------------------------------------------------------------------------
local function refreshKind(kind)
    if kind == "bars" then applyBars()
    elseif kind == "chat" then refreshChat()
    elseif kind == "minimap" then minimap.apply()
    elseif kind == "quests" then quests.apply()
    elseif kind == "xp" then applyXp()
    elseif kind == "menu" then applyMenuBars()
    end
end

local function peek(kind, seconds)
    seconds = seconds or PEEK_SECONDS
    peekUntil[kind] = math.max(peekUntil[kind], now() + seconds)
    refreshKind(kind)
    later(seconds, function() refreshKind(kind) end)
end

function UI.SetBarsHidden(hidden)
    if not db then return end
    db.barsHidden = hidden and true or false
    if db.barsHidden then peekUntil.bars = 0 end
    applyBars()
end

function UI.ToggleBars()
    if not db then return end
    UI.SetBarsHidden(not db.barsHidden)
    notice(db.barsHidden and L.action_bars_off or L.action_bars_on)
end

function UI.PeekBars()
    if not db then return end
    if not db.barsHidden then
        notice(L.action_bars_are_already_shown)
        return
    end
    peek("bars")
    notice(L.action_bars_shown_for_a_moment)
end

function UI.SetChatHidden(hidden)
    if not db then return end
    db.chatHidden = hidden and true or false
    if db.chatHidden then
        peekUntil.chat = 0
    else
        chatActiveUntil = now() + CHAT_IDLE_SECONDS
        later(CHAT_IDLE_SECONDS, refreshChat)
    end
    refreshChat()
end

function UI.ToggleChat()
    if not db then return end
    UI.SetChatHidden(not db.chatHidden)
    notice(db.chatHidden and L.chat_off or L.chat_on)
end

function UI.SetMinimapHidden(hidden)
    if not db then return end
    db.minimapHidden = hidden and true or false
    if db.minimapHidden then peekUntil.minimap = 0 end
    minimap.apply()
end

function UI.ToggleMinimap()
    if not db then return end
    UI.SetMinimapHidden(not db.minimapHidden)
    notice(db.minimapHidden and L.minimap_off or L.minimap_on)
end

-- shape: "square" or "round" (anything else = round). Not in combat: then it is caught up after the fight.
function UI.SetMinimapShape(shape)
    if not db then return end
    db.minimapShape = shape == "square" and "square" or "round"
    if inCombat() then
        notice(L.not_possible_in_combat_follows_after)
    end
    applyMinimapShape()
end

function UI.GetMinimapShape()
    return db and db.minimapShape == "square" and "square" or "round"
end

-- Minimap moved to the corner of the screen (default on); off = Blizzard's place and size again. Not in combat: then it is caught up after the fight.
function UI.SetMinimapCorner(on)
    if not db then return end
    db.minimapCorner = on and true or false
    if inCombat() then
        notice(L.not_possible_in_combat_follows_after)
    end
    corner.apply()
end

function UI.GetMinimapCorner()
    return not db or db.minimapCorner ~= false
end

-- Big zoom buttons (own + / - inside the map, 40 pixels) on / off (off = Blizzard's small buttons again).
function UI.SetZoomBig(on)
    if not db then return end
    db.zoomBig = on and true or false
    applyMinimapZoom()
end

function UI.GetZoomBig()
    return not db or db.zoomBig ~= false
end

function UI.SetQuestsHidden(hidden)
    if not db then return end
    db.questsHidden = hidden and true or false
    if db.questsHidden then peekUntil.quests = 0 end
    quests.apply()
end

function UI.ToggleQuests()
    if not db then return end
    UI.SetQuestsHidden(not db.questsHidden)
    notice(db.questsHidden and L.quest_tracker_off or L.quest_tracker_on)
end

function UI.SetMenuHidden(hidden)
    if not db then return end
    db.menuHidden = hidden and true or false
    if db.menuHidden then peekUntil.menu = 0 end
    applyMenuBars()
end

-- Bag bar alone, three steps: "full" / "pack" (backpack + ammo bag, default) / "off" (anything else = "pack").
function UI.SetBagsMode(mode)
    if not db then return end
    if not bags.modes[mode] then mode = "pack" end
    db.bagsMode = mode
    if mode == "off" then peekUntil.menu = 0 end
    bagBar.apply()
    bags.apply()
end

function UI.GetBagsMode()
    return bags.mode()
end

-- The two-step switch of fold8.16 (hidden / shown) still works: hidden = off, shown = the whole bar.
function UI.SetBagsHidden(hidden)
    UI.SetBagsMode(hidden and "off" or "full")
end

function UI.GetBagsHidden()
    return db ~= nil and bags.mode() == "off"
end

function UI.ToggleMenuBar()
    if not db then return end
    UI.SetMenuHidden(not db.menuHidden)
    notice(db.menuHidden and L.menu_bar_and_bag_bar_off
        or L.menu_bar_and_bag_bar_on_2)
end

-- Windows the classic buttons beside the chat opened (the buttons are taken away by the compact chat): own keys for the radial menu.
function UI.OpenChannels()
    if ToggleChannelFrame then ToggleChannelFrame() end
end

function UI.OpenChatMenu()
    local button = frameOf("ChatFrameMenuButton")
    if button and button.Click then button:Click() end
end

function UI.OpenHousing()
    if HousingFramesUtil and HousingFramesUtil.ToggleHousingDashboard then HousingFramesUtil.ToggleHousingDashboard() end
end

-- One area for a moment (settings window row "show briefly"): kind = bars | chat | minimap | quests | xp | menu.
function UI.PeekKind(kind)
    if not db or peekUntil[kind] == nil then return end
    if kind == "chat" then
        peek("chat")
        wakeChat()
    else
        peek(kind)
    end
    notice(L.shown_for_a_moment)
end

-- Quest tracker: size and height back to the preset ("/tf quests reset").
function UI.QuestReset()
    if not db then return end
    db.questsScale = 100
    db.questsHeight = nil
    applyQuestSize()
end

-- Chat edit box with a prefix: say | party | guild | whisper (the /tf say ... commands).
function UI.OpenChatWith(kind)
    if kind == "say" then openChat("/s ")
    elseif kind == "party" then openChat("/p ")
    elseif kind == "guild" then openChat("/g ")
    elseif kind == "whisper" then openWhisper() end
end

function UI.ChatStatus()
    chatStatus()
end

function UI.PeekAll()
    if not db then return end
    for _, kind in ipairs({ "bars", "chat", "minimap", "quests", "xp", "menu" }) do peek(kind) end
    chatActiveUntil = now() + PEEK_SECONDS
    notice(L.everything_shown_for_a_moment)
end

---------------------------------------------------------------------------
-- Reasons that show the bars by themselves
---------------------------------------------------------------------------
local function setReason(reason, on)
    local was = reasons[reason]
    reasons[reason] = on and true or nil
    if reason == "edit" then
        applyMenuBars()
        applyXpLater()
    end
    if on then
        applyBars()
    elseif was then
        peek("bars", 1.5) -- a short moment to drop the ability, then they go away again
    end
end

local DRAG_TYPES = { spell = true, item = true, macro = true, mount = true, companion = true, petaction = true, flyout = true, equipmentset = true, battlepet = true }
local holding = false

local watched = {}
local WATCH_FRAMES = {
    EditModeManagerFrame = "edit", PlayerSpellsFrame = "spellbook", SpellBookFrame = "spellbook",
    CollectionsJournal = "collections", MacroFrame = "macro", ProfessionsBookFrame = "professions",
}

local function watchFrames()
    for name, reason in pairs(WATCH_FRAMES) do
        local frame = frameOf(name)
        if frame and not watched[name] and frame.HookScript then
            watched[name] = true
            pcall(frame.HookScript, frame, "OnShow", function() setReason(reason, true) end)
            pcall(frame.HookScript, frame, "OnHide", function() setReason(reason, false) end)
            if frame:IsShown() then setReason(reason, true) end
        end
    end
end

local function watchEditBoxes()
    for index = 1, chatWindowCount() do
        local box = _G["ChatFrame" .. index .. "EditBox"]
        if box and box.HookScript and not watched["edit" .. index] then
            watched["edit" .. index] = true
            box:HookScript("OnShow", function() refreshChat() end)
            box:HookScript("OnHide", function()
                chatActiveUntil = now() + CHAT_IDLE_SECONDS
                refreshChat()
                later(CHAT_IDLE_SECONDS, refreshChat)
            end)
        end
    end
end

local function onCursorChanged()
    local kind = GetCursorInfo and GetCursorInfo()
    local nowHolding = kind ~= nil and DRAG_TYPES[kind] == true
    if nowHolding ~= holding then
        holding = nowHolding
        setReason("cursor", holding)
    end
end

---------------------------------------------------------------------------
-- Standard keys
---------------------------------------------------------------------------
-- force = false: at login, once per command. force = true: the options page / window ("set standard keys again").
local function ensureDefaultKeys(force)
    if inCombat() or not (GetBindingKey and SetBinding and GetBindingAction) then return 0, 0 end
    db.keysSet = db.keysSet or {}
    local changed, conflicts = 0, 0
    for _, command in ipairs(UI.COMMANDS) do
        if force or not db.keysSet[command] then
            db.keysSet[command] = true -- once only: a key the player removes later is not set again at login
            local key = UI.DEFAULT_KEYS[command]
            local has = GetBindingKey(command)
            if force then
                local owner = GetBindingAction(key)
                if owner == command then
                    -- already there
                elseif owner == nil or owner == "" then
                    if SetBinding(key, command) then changed = changed + 1 end
                else
                    conflicts = conflicts + 1
                end
            elseif not has then
                local owner = GetBindingAction(key)
                if (owner == nil or owner == "") and SetBinding(key, command) then changed = changed + 1 end
            end
        end
    end
    for _, command in ipairs(UI.EXTRA_COMMANDS) do
        if force or not db.keysSet[command] then
            db.keysSet[command] = true
            local key = UI.EXTRA_KEYS[command]
            local key1, key2 = GetBindingKey(command)
            if key1 == key or key2 == key or key2 ~= nil then
                -- already there, or the player has two keys of their own on it: nothing to add (a third would push one out)
            else
                local owner = GetBindingAction(key)
                if owner == nil or owner == "" then
                    if SetBinding(key, command) then changed = changed + 1 end
                else
                    conflicts = conflicts + 1
                end
            end
        end
    end
    if changed > 0 and SaveBindings and GetCurrentBindingSet then SaveBindings(GetCurrentBindingSet()) end
    return changed, conflicts
end

function UI.SetDefaultKeys(force)
    local changed, conflicts = ensureDefaultKeys(force)
    if inCombat() then
        notice(L.not_possible_in_combat)
    else
        notice(string.format(L.standard_keys_d_set_d_taken, changed, conflicts))
    end
end

---------------------------------------------------------------------------
-- Chat openers (slash commands for the radial menu: "/tf say" opens the edit box with the prefix)
---------------------------------------------------------------------------
local function openChat(prefix)
    -- 12.x: ChatFrameUtil.OpenChat; the old global name is a deprecated alias that goes away with the next expansion
    if ChatFrameUtil and ChatFrameUtil.OpenChat then ChatFrameUtil.OpenChat(prefix)
    elseif ChatFrame_OpenChat then ChatFrame_OpenChat(prefix) end
end

local function openWhisper()
    local prefix = "/w "
    if UnitExists and UnitExists("target") and UnitIsPlayer("target") and not UnitIsUnit("target", "player") then
        local name = GetUnitName and GetUnitName("target", true)
        if name then prefix = "/w " .. name .. " " end
    end
    openChat(prefix)
end

---------------------------------------------------------------------------
-- Slash commands (called from /tf in TouchForever.lua)
---------------------------------------------------------------------------
local function onoff(value) return value and L.on or L.off end

local function chatStatus()
    local o = chatOptions()
    say(L.chat .. (db.chatHidden and L.hidden or L.shown)
        .. ", " .. L.mode .. (o.mode or "normal")
        .. ", " .. L.smart_chat .. onoff(o.enabled))
    say(string.format("  size=%s lines=%s bg=%s filter=%s short=%s stamps=%s",
        tostring(o.size or "-"), tostring(o.lines or "-"), o.bg and (o.bg .. "%") or "-",
        onoff(o.filter), onoff(o.short), onoff(o.stamps)))
    say(string.format("  compact=%s extra=%s tabs-on-touch=%s dock-left=%s", onoff(chatCompact()), tostring(chatExtraWidth()), onoff(chatTabsOnTouch()),
        onoff(chatDockWanted())))
end

local function chatHelp()
    say(L.tf_chat_status_tf_chat_phone)
    say(L.show_hide_toggle_peek_mode_normal)
    say("  filter on|off, short on|off, stamps on|off, compact on|off, extra 0-120, tabs on|off, left (flush to the left edge), dock on|off")
end

local function parseOnOff(word)
    word = string.lower(word or "")
    if word == "on" or word == "an" or word == "ein" or word == "1" then return true end
    if word == "off" or word == "aus" or word == "0" then return false end
end

local function chatCommand(rest)
    local o = chatOptions()
    local word, arg = string.match(rest or "", "^(%S*)%s*(.-)%s*$")
    word = string.lower(word or "")
    local number = tonumber(arg)
    if word == "" then
        chatStatus()
        chatHelp()
        return
    elseif word == "phone" or word == "handy" then
        o.enabled = true
        for key, value in pairs(CHAT_PRESET) do o[key] = value end
    elseif word == "reset" then
        o.enabled = false
    elseif word == "show" then
        db.chatHidden = false
    elseif word == "hide" then
        db.chatHidden = true
        peekUntil.chat = 0
    elseif word == "toggle" then
        UI.ToggleChat()
        return
    elseif word == "peek" then
        peek("chat")
        wakeChat()
        return
    elseif word == "mode" then
        arg = string.lower(arg)
        if arg == "compact" or arg == "kompakt" then o.mode = "compact"
        elseif arg == "normal" then o.mode = "normal"
        else chatHelp(); return end
        chatActiveUntil = now() + CHAT_IDLE_SECONDS
        later(CHAT_IDLE_SECONDS, refreshChat)
    elseif word == "size" then
        if number then o.size = math.max(CHAT_SIZE_MIN, math.min(CHAT_SIZE_MAX, math.floor(number))) elseif parseOnOff(arg) == false then o.size = nil else chatHelp(); return end
        o.enabled = true
    elseif word == "lines" then
        if number then o.lines = math.max(CHAT_LINES_MIN, math.min(CHAT_LINES_MAX, math.floor(number))) elseif parseOnOff(arg) == false then o.lines = nil else chatHelp(); return end
        o.enabled = true
    elseif word == "bg" then
        if number then o.bg = math.max(0, math.min(100, math.floor(number))) elseif parseOnOff(arg) == false then o.bg = nil else chatHelp(); return end
        o.enabled = true
    elseif word == "compact" or word == "kompakt" then
        local value = parseOnOff(arg)
        if value == nil then chatHelp(); return end
        o.compact = value
    elseif word == "tabs" then
        local value = parseOnOff(arg)
        if value == nil then chatHelp(); return end
        o.tabsTouch = value
    elseif word == "left" or word == "links" then
        UI.ChatDockNow()
        return
    elseif word == "dock" then
        local value = parseOnOff(arg)
        if value == nil then chatHelp(); return end
        o.dockLeft = value
    elseif word == "extra" then
        if number then o.extra = math.max(0, math.min(CHAT_EXTRA_MAX, math.floor(number))) else chatHelp(); return end
    elseif word == "filter" or word == "short" or word == "stamps" then
        local value = parseOnOff(arg)
        if value == nil then chatHelp(); return end
        -- "stamps on" = show time stamps again; the others are "feature on"
        if word == "stamps" then o.stamps = value else o[word] = value or nil end
        o.enabled = true
    else
        chatHelp()
        return
    end
    applyChatAll()
    chatStatus()
end

local function toggleWord(rest, toggle, current)
    local word = string.lower(rest or "")
    if word == "hide" or word == "aus" or word == "off" then
        if not current() then toggle() end
    elseif word == "show" or word == "an" or word == "on" then
        if current() then toggle() end
    else
        toggle()
    end
end

local function help()
    say(L.commands_keys_are_in_options_key)
    say("/tf bars [show|hide|peek]  /tf chat ...  /tf minimap [show|hide|peek|square|round|corner on|corner off|zoom big|zoom small|info]  /tf quests [show|hide|peek|size 50-100|height N|reset]")
    say("/tf xp [standard|compact|minimap|off|30-100]  /tf menubar [show|hide]  /tf bags [full|pack|off]  /tf map [fit|standard]")
    say(L.tf_settings_window_everything_by_tap)
end

function UI.Slash(message)
    if not db then return false end
    local word, rest = string.match(message or "", "^(%S*)%s*(.-)%s*$")
    word = string.lower(word or "")
    rest = string.lower(rest or "")
    if word == "bars" or word == "leisten" then
        if rest == "peek" then UI.PeekBars() else toggleWord(rest, UI.ToggleBars, function() return db.barsHidden end) end
    elseif word == "chat" then
        chatCommand(message and string.match(message, "^%S+%s*(.-)$") or "")
    elseif word == "minimap" or word == "karte" then
        if rest == "square" or rest == "eckig" then UI.SetMinimapShape("square")
        elseif rest == "round" or rest == "rund" then UI.SetMinimapShape("round")
        elseif rest == "peek" then peek("minimap")
        elseif string.match(rest, "^corner") or string.match(rest, "^rand") then
            local arg = string.match(rest, "^%a+%s*(.-)$")
            if arg == "on" or arg == "an" then UI.SetMinimapCorner(true)
            elseif arg == "off" or arg == "aus" then UI.SetMinimapCorner(false)
            else UI.SetMinimapCorner(not UI.GetMinimapCorner()) end
            say(L.minimap_at_the_corner .. (UI.GetMinimapCorner() and L.on or L.off))
        elseif rest == "info" then
            for _, line in ipairs(UI.DescribeMinimap()) do say(line) end
        elseif string.match(rest, "^zoom") then
            local arg = string.match(rest, "^zoom%s*(.-)$")
            if arg == "big" or arg == "gross" or arg == "groß" or arg == "on" or arg == "an" then UI.SetZoomBig(true)
            elseif arg == "small" or arg == "klein" or arg == "off" or arg == "aus" then UI.SetZoomBig(false)
            else UI.SetZoomBig(not UI.GetZoomBig()) end
            say(L.zoom_buttons .. (UI.GetZoomBig() and L.big or L.small))
        else toggleWord(rest, UI.ToggleMinimap, function() return db.minimapHidden end) end
    elseif word == "quests" then
        local sub = string.match(rest, "^(%S*)")
        if rest == "peek" then peek("quests")
        elseif sub == "size" or sub == "scale" or sub == "reset" or sub == "height" or sub == "hoehe" then questSizeCommand(rest)
        else toggleWord(rest, UI.ToggleQuests, function() return db.questsHidden end) end
    elseif word == "xp" or word == "erfahrung" then
        if rest == "peek" then peek("xp") else xpCommand(rest) end
    elseif word == "menubar" or word == "menu" or word == "menue" or word == "menüleiste" then
        if rest == "peek" then peek("menu") else toggleWord(rest, UI.ToggleMenuBar, function() return db.menuHidden end) end
    elseif word == "bags" or word == "bagbar" or word == "taschen" or word == "taschenleiste" then
        local modes = {
            full = "full", show = "full", an = "full", on = "full", ganz = "full",
            pack = "pack", rucksack = "pack", munition = "pack",
            off = "off", hide = "off", aus = "off",
        }
        local mode = modes[rest] or (UI.GetBagsMode() == "off" and "pack" or "off")
        UI.SetBagsMode(mode)
        notice(mode == "off" and L.bag_bar_off
            or mode == "full" and L.bag_bar_whole
            or L.bag_bar_backpack_ammo)
    elseif word == "map" or word == "karte" then
        local value = string.lower(rest or "")
        if value == "fit" or value == "on" or value == "an" then UI.SetMapFit(true)
        elseif value == "standard" or value == "off" or value == "aus" then UI.SetMapFit(false)
        else UI.SetMapFit(not UI.GetMapFit()) end
        say(UI.GetMapFit() and L.world_map_fitted_to_the_screen
            or L.world_map_size_as_in_the)
    elseif word == "peek" then
        UI.PeekAll()
    elseif word == "options" or word == "settings" or word == "window" or word == "einstellungen" or word == "fenster" then
        if UI.Open then UI.Open() end
    elseif word == "say" then
        openChat("/s ")
    elseif word == "party" then
        openChat("/p ")
    elseif word == "guild" then
        openChat("/g ")
    elseif word == "whisper" then
        openWhisper()
    elseif word == "ui" or word == "help" then
        help()
        say(string.format("bars=%s chat=%s minimap=%s quests=%s menubar=%s bagbar=%s xp=%s", db.barsHidden and "hidden" or "shown",
            db.chatHidden and "hidden" or "shown", db.minimapHidden and "hidden" or "shown", db.questsHidden and "hidden" or "shown",
            db.menuHidden and "hidden" or "shown", UI.GetBagsMode(), xpMode()))
    elseif word == "keys" then
        for _, command in ipairs(UI.COMMANDS) do
            say(string.format("%s = %s", command, tostring(GetBindingKey and GetBindingKey(command) or UI.DEFAULT_KEYS[command])))
        end
        for _, command in ipairs(UI.EXTRA_COMMANDS) do
            say(string.format("%s = %s", command, tostring(GetBindingKey and GetBindingKey(command) or UI.EXTRA_KEYS[command])))
        end
    else
        return false
    end
    return true
end

-- Everything back to the preset of the addon (what a new character gets).
function UI.ResetAll()
    if not db then return end
    UI.SetBarsHidden(false)
    UI.SetChatHidden(false)
    UI.SetMinimapHidden(false)
    UI.SetMinimapShape("round")
    UI.SetMinimapCorner(true)
    UI.SetZoomBig(true)
    UI.SetQuestsHidden(false)
    UI.ChatPhone(true)
    UI.ChatSet("mode", "normal")
    UI.SetQuestScale(QUEST_SCALE_DEFAULT)
    UI.SetQuestHeight(nil)
    UI.SetMenuHidden(false)
    UI.SetBagsMode("pack")
    UI.SetMapFit(true)
    UI.SetXpWidth(XP_WIDTH_DEFAULT)
    UI.SetXpMode("compact")
    UI.ChatSet("compact", true)
    UI.ChatSet("tabs", true)
    UI.ChatSet("extra", CHAT_EXTRA_DEFAULT)
    db.gear = false
    if UI.Open and ns.options and ns.options.applyGear then ns.options.applyGear() end
    notice(L.everything_reset_to_the_preset)
end

---------------------------------------------------------------------------
-- Events
---------------------------------------------------------------------------
local frame = CreateFrame("Frame")

local CHAT_WAKE_EVENTS = {
    "CHAT_MSG_WHISPER", "CHAT_MSG_BN_WHISPER", "CHAT_MSG_PARTY", "CHAT_MSG_PARTY_LEADER", "CHAT_MSG_RAID",
    "CHAT_MSG_RAID_LEADER", "CHAT_MSG_RAID_WARNING", "CHAT_MSG_INSTANCE_CHAT", "CHAT_MSG_INSTANCE_CHAT_LEADER",
    "CHAT_MSG_GUILD", "CHAT_MSG_OFFICER",
}
local chatWake = {}
for _, event in ipairs(CHAT_WAKE_EVENTS) do chatWake[event] = true end

local function startUp()
    if started then return end
    started = true
    watchFrames()
    watchEditBoxes()
    hookMap()
    if EventRegistry and EventRegistry.RegisterCallback then
        local owner = {}
        pcall(EventRegistry.RegisterCallback, EventRegistry, "EditMode.Enter", function()
            setReason("edit", true)
            corner.apply() -- Edit Mode: nothing is moved, only the room to drag the map to the corner is opened (clamp insets)
            later(0.3, function() if db then corner.openClamp() end end) -- Blizzard's insets may come back when Edit Mode opens
            later(0.3, function() if db then applyChatClamp() end end) -- Blizzard's insets may come back when Edit Mode opens
        end, owner)
        pcall(EventRegistry.RegisterCallback, EventRegistry, "EditMode.Exit", function()
            setReason("edit", false)
            later(0.5, applyMinimapShape)
            applyQuestSizeLater()
            later(0.5, applyChatAll)
        end, owner)
    end
    -- Mouse over / touch on the chat: Blizzard fades the chat in (tabs show), when the mouse leaves it fades out again.
    if hooksecurefunc then
        if FCF_FadeInChatFrame then
            pcall(hooksecurefunc, "FCF_FadeInChatFrame", function() tabsTouched = true; refreshChat() end)
        end
        if FCF_FadeOutChatFrame then
            pcall(hooksecurefunc, "FCF_FadeOutChatFrame", function() tabsTouched = false; refreshChat() end)
        end
    end
    applyChatShortTags()
    applyChatAll()
    applyBars()
    minimap.apply()
    hookMinimapLayout()
    applyMinimapShape()
    quests.apply()
    applyMenuBars()
    hookXpFrames()
    applyXpLater()
    applyQuestSizeLater()
    chatActiveUntil = now() + CHAT_IDLE_SECONDS
    later(CHAT_IDLE_SECONDS, refreshChat)
    later(3, function() if db then chatDock(false) end end) -- Blizzard places the chat windows a moment after login
    later(3, function() pcall(ensureDefaultKeys, false) end)
    if UI.onStart then pcall(UI.onStart) end
end

local function register(event)
    pcall(frame.RegisterEvent, frame, event)
end

frame:SetScript("OnEvent", function(_, event, arg1)
    if event == "ADDON_LOADED" then
        if arg1 == ADDON then
            TouchForeverDB = TouchForeverDB or {}
            TouchForeverDB.ui = TouchForeverDB.ui or {}
            db = TouchForeverDB.ui
            local o = chatOptions()
            if o.enabled == nil then
                -- first start: the phone preset is on (the default of this app), chat mode and visibility stay as they are
                o.enabled = true
                for key, value in pairs(CHAT_PRESET) do o[key] = value end
                o.mode = "normal"
                db.uiVersion = 2
            elseif (db.uiVersion or 1) < 2 then
                -- 1.1.0 -> 1.2.0: whoever still has the old preset untouched gets the new one; own values stay
                if o.enabled and o.size == OLD_CHAT_PRESET.size and o.lines == OLD_CHAT_PRESET.lines then
                    o.size, o.lines = CHAT_PRESET.size, CHAT_PRESET.lines
                end
                db.uiVersion = 2
            end
            if db.questsScale == nil then db.questsScale = QUEST_SCALE_DEFAULT end
            -- 1.6.0: new options; old saved data gets the defaults (an explicit value stays)
            if o.compact == nil then o.compact = true end
            if o.extra == nil then o.extra = CHAT_EXTRA_DEFAULT end
            if o.tabsTouch == nil then o.tabsTouch = true end
            if db.xpMode == nil then db.xpMode = "compact" end
            if db.xpWidth == nil then db.xpWidth = XP_WIDTH_DEFAULT end
            if db.menuHidden == nil then db.menuHidden = false end
            -- fold8.17: the bag bar has three steps; whatever fold8.16 saved (bagsHidden) becomes the preset "pack"
            if not bags.modes[db.bagsMode] then db.bagsMode = "pack" end
            db.bagsHidden = nil
            if db.minimapCorner == nil then db.minimapCorner = true end
            if db.minimapShape == nil then db.minimapShape = "round" end
            if db.zoomBig == nil then db.zoomBig = true end
            local function editOpen() return reasons.edit == true end
            minimap = newHideable({ "MinimapCluster", "Minimap" }, "minimapHidden", "minimap")
            quests = newHideable(QUEST_FRAME_NAMES, "questsHidden", "quests")
            menuBar = newHideable(MENU_BAR_FRAMES, "menuHidden", "menu", editOpen)
            bagBar = newHideable(bags.frames, function() return bags.mode() == "off" or db.menuHidden == true end, "menu", editOpen)
            xpHideable = newHideable({ XP_MANAGER_NAME }, function() return xpMode() == "off" end, "xp", editOpen)
        elseif db then
            watchFrames() -- load-on-demand windows (spellbook, collections, macros)
            hookMap()
        end
    elseif not db then
        return
    elseif event == "PLAYER_LOGIN" or event == "PLAYER_ENTERING_WORLD" then
        if not started then startUp() else
            applyBars(); minimap.apply(); applyMinimapShape(); quests.apply(); applyMenuBars(); refreshChat(); applyQuestSizeLater(); applyXpLater()
        end
    elseif event == "EDIT_MODE_LAYOUTS_UPDATED" then
        later(0.5, function() applyBars(); applyChatAll(); applyMinimapShape() end)
        applyQuestSizeLater()
        applyXpLater()
    elseif event == "UI_SCALE_CHANGED" or event == "DISPLAY_SIZE_CHANGED" then
        mapFitLater()
        applyQuestSizeLater()
        applyXpLater()
        applyMinimapZoom()
        later(0.5, corner.apply)
    elseif event == "BAG_UPDATE_DELAYED" or event == "PLAYER_EQUIPMENT_CHANGED" or event == "BAG_CONTAINER_UPDATE" then
        bags.soon()
    elseif event == "PLAYER_REGEN_ENABLED" then
        if pendingCombat then
            pendingCombat = false
            applyBars(); minimap.apply(); applyMinimapShape(); quests.apply(); applyMenuBars(); refreshChat(); applyQuestSize()
            applyChatWidth(); applyChatClamp(); chatDock(false); applyXp(); mapApplyFit()
        end
    elseif event == "CURSOR_CHANGED" or event == "CURSOR_UPDATE" then
        onCursorChanged()
    elseif chatWake[event] then
        if chatOptions().mode == "compact" and not db.chatHidden then wakeChat() end
    end
end)

for _, event in ipairs({
    "ADDON_LOADED", "PLAYER_LOGIN", "PLAYER_ENTERING_WORLD", "EDIT_MODE_LAYOUTS_UPDATED", "PLAYER_REGEN_ENABLED",
    "CURSOR_CHANGED", "CURSOR_UPDATE", "UI_SCALE_CHANGED", "DISPLAY_SIZE_CHANGED",
    "BAG_UPDATE_DELAYED", "PLAYER_EQUIPMENT_CHANGED", "BAG_CONTAINER_UPDATE",
}) do
    register(event)
end
for _, event in ipairs(CHAT_WAKE_EVENTS) do register(event) end

-- The main file's slash command hands over what it does not know.
ns.slash = UI.Slash
