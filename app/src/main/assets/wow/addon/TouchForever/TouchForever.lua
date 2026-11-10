-- TouchForever: own code, no Blizzard or WeakAuras parts.
-- Writes what the launcher needs for real icons into TouchForeverDB (SavedVariables):
--   slots    action bar slots 1-180: type, id, spell/item id, name, icon file id
--   spells   all active (non-passive) spells of the spellbook
--   mounts   collected mounts, toys  collected toys, items  consumables in the bags
-- WoW writes SavedVariables only on /reload, logout and exit; the data is refreshed right before that (PLAYER_LOGOUT).
-- After a change of the action bars a small button offers the reload. Slash command: /tf

local ADDON, ns = ...
local DB_VERSION = 1
local MAX_SLOT = 180
local MAX_MOUNTS, MAX_TOYS, MAX_ITEMS = 400, 300, 150
local BUTTON_SECONDS = 30

local frame = CreateFrame("Frame")
local db
local diskSignature          -- signature of the slots as they stand in the file on disk (what the launcher sees)
local ready = false          -- false during the login flood of ACTIONBAR_SLOT_CHANGED
local pendingKinds = {}      -- kind -> "timer" (scheduled) or true (waiting for the end of combat)
local pendingButton = false
local button, buttonToken = nil, 0

local L = ns.L

local tr_strip_on = L.cooldown_strip_on
local tr_strip_off = L.cooldown_strip_off

local function say(text)
    print("|cffffd100TouchForever:|r " .. text)
end

-- API names differ between client generations; every lookup is guarded.
local function spellName(id)
    if C_Spell and C_Spell.GetSpellName then return C_Spell.GetSpellName(id) end
    if C_Spell and C_Spell.GetSpellInfo then
        local info = C_Spell.GetSpellInfo(id)
        return info and info.name
    end
    if GetSpellInfo then return (GetSpellInfo(id)) end
end

local function spellIcon(id)
    if C_Spell and C_Spell.GetSpellTexture then return C_Spell.GetSpellTexture(id) end
    if C_Spell and C_Spell.GetSpellInfo then
        local info = C_Spell.GetSpellInfo(id)
        return info and info.iconID
    end
    if GetSpellTexture then return GetSpellTexture(id) end
end

local function itemName(id)
    if C_Item and C_Item.GetItemNameByID then return C_Item.GetItemNameByID(id) end
    if GetItemInfo then return (GetItemInfo(id)) end
end

local function itemIcon(id)
    if C_Item and C_Item.GetItemIconByID then return C_Item.GetItemIconByID(id) end
    if GetItemIcon then return GetItemIcon(id) end
end

local function setIcon(entry, icon)
    if type(icon) == "number" then
        entry.icon = icon
    elseif type(icon) == "string" and icon ~= "" then
        entry.iconPath = icon
    end
end

local function numberOrNil(value)
    if type(value) == "number" then return value end
end

---------------------------------------------------------------------------
-- Action bar slots
---------------------------------------------------------------------------
local function slotEntry(slot)
    if HasAction and not HasAction(slot) then return nil end
    local actionType, id, subType = GetActionInfo(slot)
    if not actionType then return nil end
    local entry = { type = actionType }
    if type(id) == "number" or type(id) == "string" then entry.id = id end
    if type(subType) == "string" then entry.subType = subType end
    setIcon(entry, GetActionTexture and GetActionTexture(slot))

    if actionType == "spell" and type(id) == "number" then
        entry.spell = id
        entry.name = spellName(id)
    elseif actionType == "item" and type(id) == "number" then
        entry.item = id
        entry.name = itemName(id)
    elseif actionType == "macro" then
        entry.name = GetActionText and GetActionText(slot) or nil
        if GetMacroSpell and type(id) == "number" then entry.spell = numberOrNil(GetMacroSpell(id)) end
        if not entry.spell and GetMacroItem and type(id) == "number" then
            local _, link = GetMacroItem(id)
            local itemId = link and tonumber(string.match(link, "item:(%d+)"))
            if itemId then entry.item = itemId end
        end
    elseif actionType == "summonmount" and type(id) == "number" and C_MountJournal and C_MountJournal.GetMountInfoByID then
        local name, spellId = C_MountJournal.GetMountInfoByID(id)
        entry.name = name
        entry.spell = numberOrNil(spellId)
    elseif actionType == "companion" and subType == "MOUNT" and type(id) == "number" then
        entry.spell = id
        entry.name = spellName(id)
    elseif actionType == "equipmentset" then
        entry.name = type(id) == "string" and id or nil
    end
    return entry
end

local function collectSlots()
    local slots, parts = {}, {}
    for slot = 1, MAX_SLOT do
        local ok, entry = pcall(slotEntry, slot)
        if ok and entry then
            slots[slot] = entry
            parts[#parts + 1] = slot .. ":" .. entry.type .. ":" .. tostring(entry.id or "")
        end
    end
    return slots, table.concat(parts, ";")
end

---------------------------------------------------------------------------
-- Spellbook (active spells of all tabs), mounts, toys, consumables
---------------------------------------------------------------------------
local function collectSpells()
    local list, seen = {}, {}
    local book = C_SpellBook
    if not (book and book.GetNumSpellBookSkillLines and book.GetSpellBookSkillLineInfo and book.GetSpellBookItemInfo) then
        return list
    end
    local bank = (Enum and Enum.SpellBookSpellBank and Enum.SpellBookSpellBank.Player) or 0
    local typeSpell = (Enum and Enum.SpellBookItemType and Enum.SpellBookItemType.Spell) or 1
    local typeFlyout = (Enum and Enum.SpellBookItemType and Enum.SpellBookItemType.Flyout) or 4

    local function add(id, name, icon)
        if type(id) ~= "number" or seen[id] then return end
        seen[id] = true
        local entry = { id = id, name = name or spellName(id) }
        setIcon(entry, icon or spellIcon(id))
        list[#list + 1] = entry
    end

    for line = 1, book.GetNumSpellBookSkillLines() do
        local lineInfo = book.GetSpellBookSkillLineInfo(line)
        if lineInfo and not lineInfo.offSpecID then
            local first = (lineInfo.itemIndexOffset or 0) + 1
            local last = (lineInfo.itemIndexOffset or 0) + (lineInfo.numSpellBookItems or 0)
            for index = first, last do
                local info = book.GetSpellBookItemInfo(index, bank)
                if info and not info.isOffSpec then
                    local passive = info.isPassive
                    if passive == nil and book.IsSpellBookItemPassive then
                        passive = book.IsSpellBookItemPassive(index, bank)
                    end
                    if info.itemType == typeSpell and not passive then
                        add(info.spellID, info.name, info.iconID)
                    elseif info.itemType == typeFlyout and GetFlyoutInfo and GetFlyoutSlotInfo then
                        local _, _, numSlots = GetFlyoutInfo(info.actionID)
                        for slot = 1, (numSlots or 0) do
                            local spellId, _, isKnown, spellNameText = GetFlyoutSlotInfo(info.actionID, slot)
                            if spellId and isKnown then add(spellId, spellNameText, nil) end
                        end
                    end
                end
            end
        end
    end
    return list
end

local function collectMounts()
    local list = {}
    local journal = C_MountJournal
    if not (journal and journal.GetMountIDs and journal.GetMountInfoByID) then return list end
    for _, mountId in ipairs(journal.GetMountIDs()) do
        local name, spellId, icon, _, _, _, isFavorite, _, _, hidden, isCollected = journal.GetMountInfoByID(mountId)
        if isCollected and not hidden then
            local entry = { id = mountId, spell = numberOrNil(spellId), name = name }
            if isFavorite then entry.fav = true end
            setIcon(entry, icon)
            list[#list + 1] = entry
            if #list >= MAX_MOUNTS then break end
        end
    end
    return list
end

local function collectToys()
    local list = {}
    local box = C_ToyBox
    if not (box and box.GetNumToys and box.GetToyFromIndex and box.GetToyInfo) then return list end
    for index = 1, box.GetNumToys() do
        local itemId = box.GetToyFromIndex(index)
        if type(itemId) == "number" and itemId > 0 and (not PlayerHasToy or PlayerHasToy(itemId)) then
            local _, name, icon, isFavorite = box.GetToyInfo(itemId)
            local entry = { id = itemId, item = itemId, name = name }
            if isFavorite then entry.fav = true end
            setIcon(entry, icon or itemIcon(itemId))
            list[#list + 1] = entry
            if #list >= MAX_TOYS then break end
        end
    end
    return list
end

local function collectConsumables()
    local list, seen = {}, {}
    local container = C_Container
    if not (container and container.GetContainerNumSlots and container.GetContainerItemInfo) then return list end
    local consumable = (Enum and Enum.ItemClass and Enum.ItemClass.Consumable) or 0
    local lastBag = (NUM_BAG_SLOTS or 4) + (NUM_REAGENTBAG_SLOTS or 1)
    for bag = 0, lastBag do
        for slot = 1, (container.GetContainerNumSlots(bag) or 0) do
            local info = container.GetContainerItemInfo(bag, slot)
            local itemId = info and info.itemID
            if itemId and not seen[itemId] then
                seen[itemId] = true
                local classId
                if C_Item and C_Item.GetItemInfoInstant then
                    classId = select(6, C_Item.GetItemInfoInstant(itemId))
                end
                if classId == consumable then
                    local entry = { id = itemId, item = itemId, name = info.itemName or itemName(itemId) }
                    setIcon(entry, info.iconFileID or itemIcon(itemId))
                    list[#list + 1] = entry
                    if #list >= MAX_ITEMS then return list end
                end
            end
        end
    end
    return list
end

---------------------------------------------------------------------------
-- Collecting (never in combat: some values are restricted there)
---------------------------------------------------------------------------
local function guarded(fn, fallback)
    local ok, result = pcall(fn)
    if ok and result ~= nil then return result end
    return fallback
end

local function collectHeader()
    db.version = DB_VERSION
    db.updated = time()
    db.interface = select(4, GetBuildInfo())
    db.build = select(2, GetBuildInfo())
    db.character = UnitName("player")
    db.realm = GetRealmName and GetRealmName() or nil
    db.class = select(2, UnitClass("player"))
    local specIndex = GetSpecialization and GetSpecialization()
    if specIndex and GetSpecializationInfo then
        local specId, specName = GetSpecializationInfo(specIndex)
        db.spec = specId
        db.specName = specName
    end
    return true
end

local function collect(kind)
    if InCombatLockdown and InCombatLockdown() then
        pendingKinds[kind] = true
        return
    end
    if kind == "slots" or kind == "all" then
        local ok, slots, signature = pcall(collectSlots)
        if ok and slots then
            db.slots, db.signature = slots, signature
        end
    end
    if kind == "all" or kind == "spells" then db.spells = guarded(collectSpells, db.spells or {}) end
    if kind == "all" or kind == "mounts" then db.mounts = guarded(collectMounts, db.mounts or {}) end
    if kind == "all" or kind == "toys" then db.toys = guarded(collectToys, db.toys or {}) end
    if kind == "all" or kind == "items" then db.items = guarded(collectConsumables, db.items or {}) end
    if kind == "all" then guarded(collectHeader) end
end

---------------------------------------------------------------------------
-- Reload button (only offered, never an automatic reload)
---------------------------------------------------------------------------
-- ReloadUI() is a restricted function (needs a hardware event and untainted code): called from addon code (button
-- script, slash command, settings list) the client answers "Interface action blocked by an addon". So the reload is
-- never called from Lua here: every reload control is a SecureActionButtonTemplate button with type "macro" and
-- macrotext "/reload" (a click is the hardware event, the macro runs in secure code, no taint). Secure frames get their
-- attributes only outside of combat; a button made in combat is armed on PLAYER_REGEN_ENABLED.
local unarmed = {}           -- secure reload buttons that still wait for their attributes (set in combat)

local function armReload(btn)
    if InCombatLockdown and InCombatLockdown() then
        unarmed[btn] = true
        return false
    end
    btn:SetAttribute("type", "macro")
    btn:SetAttribute("macrotext", "/reload")
    btn:SetAttribute("useOnKeyDown", false) -- fire on release; with AnyUp only, the CVar ActionButtonUseKeyDown cannot double it
    btn:RegisterForClicks("AnyUp")
    unarmed[btn] = nil
    return true
end

-- Turns a button made with "SecureActionButtonTemplate" into a reload button; the data for the launcher is written first
-- (PreClick is insecure code and may do that; PLAYER_LOGOUT collects again right before the file is written).
ns.armReloadButton = function(btn)
    btn:HookScript("PreClick", function()
        if db then pcall(collect, "all") end
    end)
    return armReload(btn)
end

-- The reload key: a hidden secure button plus an override binding CTRL-SHIFT-F4 -> CLICK TouchForeverReloadKey:LeftButton. The
-- launcher's "Neuladen" button (and its automatic reload when the game still runs an older build of this addon) sends that key:
-- a real key press is the hardware event the secure /reload macro needs. An override binding is not saved in the player's key
-- bindings (nothing to clean up, no clash with saved keys); it is set again at every login and after combat.
ns.RELOAD_KEY = "CTRL-SHIFT-F4"
local reloadKeyButton
local reloadKeyPending = false

local function bindReloadKey()
    if InCombatLockdown and InCombatLockdown() then
        reloadKeyPending = true
        return
    end
    reloadKeyPending = false
    if not reloadKeyButton then
        reloadKeyButton = CreateFrame("Button", "TouchForeverReloadKey", UIParent, "SecureActionButtonTemplate")
        reloadKeyButton:SetSize(1, 1)
        reloadKeyButton:SetPoint("TOPLEFT", UIParent, "TOPLEFT", -50, 50)
        ns.armReloadButton(reloadKeyButton)
    end
    if SetOverrideBindingClick then
        SetOverrideBindingClick(reloadKeyButton, true, ns.RELOAD_KEY, "TouchForeverReloadKey", "LeftButton")
    end
end
ns.bindReloadKey = bindReloadKey

local reloadKeyFrame = CreateFrame("Frame")
for _, event in ipairs({ "PLAYER_LOGIN", "PLAYER_ENTERING_WORLD", "PLAYER_REGEN_ENABLED" }) do
    pcall(reloadKeyFrame.RegisterEvent, reloadKeyFrame, event)
end
reloadKeyFrame:SetScript("OnEvent", function(_, event)
    if event ~= "PLAYER_REGEN_ENABLED" or reloadKeyPending then bindReloadKey() end
end)

-- The small offer button: a plain holder frame (can be shown and hidden even in combat) around the secure button.
local function ensureButton()
    if button then return button end
    button = CreateFrame("Frame", "TouchForeverReloadHolder", UIParent)
    button:SetSize(170, 26)
    button:SetPoint("TOP", UIParent, "TOP", 0, -70)
    button:SetFrameStrata("FULLSCREEN_DIALOG") -- above the settings window / game options it may be offered from
    button:SetClampedToScreen(true)
    button:SetAlpha(0.85)
    local inner = CreateFrame("Button", "TouchForeverReloadButton", button, "UIPanelButtonTemplate,SecureActionButtonTemplate")
    inner:SetAllPoints(button)
    inner:SetText(L.update_icons)
    ns.armReloadButton(inner)
    inner:HookScript("PostClick", function() button:Hide() end)
    button.reload = inner
    button:Hide()
    return button
end

-- force: also when the offer after bar changes is switched off (the "update icons" entries of the window / settings).
local function showButton(force)
    if not force and not db.settings.button then return end
    if InCombatLockdown and InCombatLockdown() then
        pendingButton = true
        return
    end
    local b = ensureButton()
    b:Show()
    buttonToken = buttonToken + 1
    local token = buttonToken
    C_Timer.After(BUTTON_SECONDS, function()
        if token == buttonToken and button then button:Hide() end
    end)
end

local function hideButton()
    buttonToken = buttonToken + 1
    if button then button:Hide() end
end

local function checkSlotsChanged()
    collect("slots")
    if ready and db.signature ~= diskSignature then
        showButton()
    else
        hideButton()
    end
end

---------------------------------------------------------------------------
-- Events
---------------------------------------------------------------------------
local function run(kind)
    if kind == "slots" then
        checkSlotsChanged()
    else
        collect(kind)
    end
end

local function schedule(kind, delay)
    if pendingKinds[kind] == "timer" then return end
    pendingKinds[kind] = "timer"
    C_Timer.After(delay, function()
        pendingKinds[kind] = nil
        run(kind)
    end)
end

local function register(event)
    pcall(frame.RegisterEvent, frame, event)
end

frame:SetScript("OnEvent", function(_, event, arg1)
    if event == "ADDON_LOADED" then
        if arg1 ~= ADDON then return end
        TouchForeverDB = TouchForeverDB or {}
        db = TouchForeverDB
        db.settings = db.settings or {}
        if db.settings.button == nil then db.settings.button = true end
        diskSignature = db.signature or ""
    elseif not db then
        return
    elseif event == "PLAYER_ENTERING_WORLD" then
        schedule("all", 4)
        C_Timer.After(12, function()
            ready = true
        end)
    elseif event == "ACTIONBAR_SLOT_CHANGED" then
        schedule("slots", 1.5)
    elseif event == "SPELLS_CHANGED" or event == "PLAYER_SPECIALIZATION_CHANGED" then
        schedule("all", 4)
    elseif event == "BAG_UPDATE_DELAYED" then
        schedule("items", 5)
    elseif event == "NEW_MOUNT_ADDED" then
        schedule("mounts", 3)
    elseif event == "NEW_TOY_ADDED" then
        schedule("toys", 3)
    elseif event == "PLAYER_REGEN_ENABLED" then
        for btn in pairs(unarmed) do armReload(btn) end
        local waiting = {}
        for kind, state in pairs(pendingKinds) do
            if state == true then waiting[#waiting + 1] = kind end
        end
        for _, kind in ipairs(waiting) do
            pendingKinds[kind] = nil
            run(kind)
        end
        if pendingButton then
            pendingButton = false
            checkSlotsChanged()
        end
    elseif event == "PLAYER_REGEN_DISABLED" then
        hideButton()
    elseif event == "PLAYER_LOGOUT" then
        -- Runs before WoW writes the SavedVariables (also on /reload): the file gets the state of this moment.
        collect("all")
    end
end)

for _, event in ipairs({
    "ADDON_LOADED", "PLAYER_ENTERING_WORLD", "ACTIONBAR_SLOT_CHANGED", "SPELLS_CHANGED",
    "PLAYER_SPECIALIZATION_CHANGED", "BAG_UPDATE_DELAYED", "NEW_MOUNT_ADDED", "NEW_TOY_ADDED",
    "PLAYER_REGEN_ENABLED", "PLAYER_REGEN_DISABLED", "PLAYER_LOGOUT",
}) do
    register(event)
end

SLASH_TOUCHFOREVER1 = "/tf"
SLASH_TOUCHFOREVER2 = "/wfb" -- old name: buttons saved by earlier versions still send it
SlashCmdList["TOUCHFOREVER"] = function(message)
    if not db then return end
    message = string.lower(strtrim and strtrim(message or "") or (message or ""))
    -- bars, chat, minimap, quests, peek ... live in TouchForeverUI.lua
    if message == "" and ns and ns.UI and ns.UI.Open then
        ns.UI.Open() -- the settings window (TouchForeverOptions.lua)
        return
    end
    if message == "strip" and ns and ns.strip then
        ns.strip.set(not ns.strip.get())
        say(ns.strip.get() and tr_strip_on or tr_strip_off)
        return
    end
    if message == "windows" and ns and ns.strip and ns.strip.describeWindows then
        local lines, reported, build = ns.strip.describeWindows()
        say(string.format("Addon build %d, windows in the strip: %d", build, reported))
        say(string.format("Language: %s (from the %s)", tostring(ns.lang), tostring(ns.langSource)))
        for _, line in ipairs(lines) do say(line) end
        return
    end
    if message == "kontext" or message == "context" then
        if ns and ns.context and ns.context.describe then
            for _, line in ipairs(ns.context.describe()) do say(line) end
        else
            say("context: not loaded")
        end
        return
    end
    if message ~= "" and message ~= "reload" and message ~= "button" and ns and ns.slash and ns.slash(message) then return end
    if message == "reload" then
        ns.reloadIcons()
        return
    end
    db.settings.button = not db.settings.button
    if not db.settings.button then hideButton() end
    say(db.settings.button and L.reload_button_after_bar_changes_on or L.reload_button_after_bar_changes_off)
    say(L.tf_settings_window_tf_strip_toggle)
end

-- Used by the settings window / page (TouchForeverOptions.lua).
ns.button = {
    get = function() return db and db.settings and db.settings.button and true or false end,
    set = function(value)
        if not db then return end
        db.settings.button = value and true or false
        if not db.settings.button then hideButton() end
    end,
}
-- No ReloadUI() call (blocked for addons, see above): collect the data and show the secure reload button; one tap reloads.
ns.reloadIcons = function()
    if not db then return end
    collect("all")
    if InCombatLockdown and InCombatLockdown() then
        say(L.not_possible_in_combat_try_again)
        return
    end
    showButton(true)
    say(L.tap_the_update_icons_button_at)
end
