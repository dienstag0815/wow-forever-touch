-- TouchForever, part 5: context for the launcher's menu on long press (addon build 4; build 11: quests under the pointer; build 14: leave the group). Own code, no Blizzard or third party addon parts.
--
-- Hold a finger on another player, an NPC or an enemy and the launcher shows a menu with what could be done there. This file
--   1. reports the unit under the mouse pointer ("mouseover", fallback "target") in the context section of the strip
--      (TouchForeverCooldown.lua writes the cells, the Kotlin decoder is app/gamenative/ui/screen/wow/context/ContextWire.kt),
--   2. keeps two spell lists (enemy: IsHarmfulSpell, friend: IsHelpfulSpell; highest rank only, no passives; 8 each) and binds them,
--      together with a few fixed actions, to buttons that the launcher presses by key (override bindings, not saved in the player's
--      key bindings),
--   3. tells the launcher per spell whether it is in range, usable and on cooldown.
--
-- Payload (nibbles 0..15, the list the strip carries; 1-based here, the Kotlin side counts from 0):
--   1 version | 2 reserved | 3 flags (1 keys armed, 2 spell lists known, 4 more spells than fit)
--   4..6  unit A: [exists 1, player 2, dead 4, skinnable 8] | relation (0 unknown, 1 friend, 2 hostile, 3 neutral) + 4 * source
--         (0 none, 1 mouseover, 2 target) | reach (0 unknown, 1 far, 2 within 28 yd, 3 within 11 yd, 4 within 9.9 yd) + 8 * in group
--   7..9  unit B: reaction (0..8) | [has target 1, is target 2, is self 4, can attack 8] | 0
--   10..15 mouse focus (bag, bank or equipment slot or a quest under the pointer): kind (0 none, 1 bag, 2 bank, 3 equipment, 4 quest) |
--         flags (items: 1 filled, 2 equippable, 4 usable, 8 stack > 1; quest: 1 present, 2 can be shared with the group, 4 tracked) |
--         1 vendor open + 2 cursor carries an item | stack size (max 15) | 0 | 0
--   16..21 classes of the action bar 1 slots 1..12 (0 none, 1 harmful, 2 helpful, 3 both), two slots per nibble (low bits first)
--   22..  16 spell entries of 9 nibbles: spell id (6 nibbles, high first) | flags (1 filled, 2 not usable, 4 out of range, 8 global
--         cooldown only) | cooldown code (2 nibbles, high first: 0 none, 1..100 tenths up to 10 s, 101..255 whole seconds beyond 10 s)
--         entries 1..8 = enemy list, 9..16 = friend list; entry i of either list is pressed with the spell key i
--
-- Keys (the same table is in app/gamenative/ui/screen/wow/context/ContextKeys.kt, a unit test compares them):
--   spell keys 1..8  ALT-CTRL-F5 .. ALT-CTRL-F12   secure macro "/cast [@target,harm,nodead] <enemy i>; [@target,help,nodead] <friend i>"
--   ALT-SHIFT-F1 attack (/startattack)   F2 the game's own context menu of the target (what a right click on the target frame opens:
--   whisper, invite, trade, inspect, follow, duel, focus, marker ...; the player taps the entries in the game; the "More ..." entry of
--   the launcher's mini menu for players)
--   mini menu for players: F3 whisper (opens the chat box with "/w Name ")   ALT-CTRL-INSERT invite, or leave the group when the target is in my group (not F4: Alt+F4 closes the window)   F5 follow (/follow)   F6 trade   F7 inspect
--   F8 add friend   ("wave" is a chat command typed by the launcher, no key; own figure under the finger: the launcher presses the keys of
--   the game's window bindings, nothing from here)
--   F9 put the item on the cursor back (ClearCursor)   F10 link the item or the quest under the pointer in the chat   F11 show the
--   comparison tooltip   F12 throw the item under the pointer away: ONLY after an own Yes / No question (nothing is deleted without a
--   tap on Yes)
--   Quest under the pointer (quest log entry or quest tracker block; the table stays as it is, two keys get a second meaning that
--   applies only while a quest and no item slot is under the pointer): F10 link the quest in the chat (an open chat box gets the link,
--   a closed one is opened with it, nothing is sent)   F11 share the quest with the group (instead of the comparison tooltip)
--   F9 track / untrack the quest (instead of putting the cursor item back)
--   The launcher selects the unit as target first (a left click), so every action works on "target".
-- Spell lists: only spells that can be cast on another unit (the spell has a range; no tracking, no self buffs like Stoneform).
-- Secure buttons get their attributes (the spells) only outside of combat; in combat the lists and keys stay as they were and only
-- the flags (range, usable, cooldown) go on changing.

local ADDON, ns = ...
ns = ns or {}
local C = {}
ns.context = C

local L = ns.L
local function say(text) print("|cffffd100TouchForever:|r " .. text) end

C.VERSION = 1
C.SPELLS = 8                    -- per list
C.ENTRIES = 16
C.NIBBLES = 165                 -- 3 * (info 1 + unit 2 + focus 2 + bar 2 + 3 * 16 entries)
C.F_KEYS, C.F_SPELLS, C.F_TRUNC = 1, 2, 4
C.E_FILLED, C.E_NOT_USABLE, C.E_OUT_OF_RANGE, C.E_GCD = 1, 2, 4, 8
C.SPELL_KEYS = { "ALT-CTRL-F5", "ALT-CTRL-F6", "ALT-CTRL-F7", "ALT-CTRL-F8", "ALT-CTRL-F9", "ALT-CTRL-F10", "ALT-CTRL-F11", "ALT-CTRL-F12" }
C.KEYS = {
    attack = "ALT-SHIFT-F1", popup = "ALT-SHIFT-F2",
    whisper = "ALT-SHIFT-F3", invite = "ALT-CTRL-INSERT", follow = "ALT-SHIFT-F5", trade = "ALT-SHIFT-F6", inspect = "ALT-SHIFT-F7",
    friend = "ALT-SHIFT-F8",
    clearcursor = "ALT-SHIFT-F9", link = "ALT-SHIFT-F10", compare = "ALT-SHIFT-F11", discard = "ALT-SHIFT-F12",
}
-- Order of the fixed actions (buttons are made in this order).
C.ACTIONS = { "attack", "popup", "whisper", "invite", "follow", "trade", "inspect", "friend", "clearcursor", "link", "compare", "discard" }

---------------------------------------------------------------------------
-- Pure coding (no game API; the test drives this part directly)
---------------------------------------------------------------------------
-- Remaining seconds -> 0..255 (see the header).
function C.cooldownCode(seconds)
    if type(seconds) ~= "number" or seconds <= 0 then return 0 end
    if seconds <= 10 then
        local code = math.floor(seconds * 10 + 0.5)
        if code < 1 then code = 1 end
        return code
    end
    local code = 100 + math.floor(seconds - 10 + 0.5)
    if code > 255 then code = 255 end
    return code
end

-- data = { flags =, unit = { exists, player, dead, skinnable, relation, source, reach, group, reaction, hasTarget, isTarget, isSelf, canAttack },
--          focus = { kind, filled, equippable (quest: shareable), usable (quest: tracked), stacked, merchant, cursor, count }, bar = { class of slot 1..12 }, entries = { [1..16] = { id =, flags =, cd = seconds } } }
-- Returns the list of NIBBLES values.
function C.nibbles(data)
    local n = {}
    for i = 1, C.NIBBLES do n[i] = 0 end
    n[1] = C.VERSION
    n[3] = (data.flags or 0) % 16
    local u = data.unit or {}
    n[4] = (u.exists and 1 or 0) + (u.player and 2 or 0) + (u.dead and 4 or 0) + (u.skinnable and 8 or 0)
    n[5] = ((u.relation or 0) % 4) + 4 * ((u.source or 0) % 4)
    n[6] = math.min(u.reach or 0, 4) + (u.group and 8 or 0)
    n[7] = math.min(u.reaction or 0, 8)
    n[8] = (u.hasTarget and 1 or 0) + (u.isTarget and 2 or 0) + (u.isSelf and 4 or 0) + (u.canAttack and 8 or 0)
    local f = data.focus or {}
    n[10] = (f.kind or 0) % 16
    n[11] = (f.filled and 1 or 0) + (f.equippable and 2 or 0) + (f.usable and 4 or 0) + (f.stacked and 8 or 0)
    n[12] = (f.merchant and 1 or 0) + (f.cursor and 2 or 0)
    n[13] = math.min(f.count or 0, 15)
    local bar = data.bar or {}
    for k = 0, 5 do
        n[16 + k] = ((bar[2 * k + 1] or 0) % 4) + 4 * ((bar[2 * k + 2] or 0) % 4)
    end
    local entries = data.entries or {}
    for e = 1, C.ENTRIES do
        local entry = entries[e]
        if entry then
            local base = 21 + 9 * (e - 1)           -- 0-based index of the entry's first nibble
            local id = math.floor(entry.id or 0) % 16777216
            for i = 0, 5 do
                n[base + 1 + i] = math.floor(id / (16 ^ (5 - i))) % 16
            end
            n[base + 7] = (entry.flags or 0) % 16
            local code = C.cooldownCode(entry.cd)
            n[base + 8] = math.floor(code / 16) % 16
            n[base + 9] = code % 16
        end
    end
    return n
end

---------------------------------------------------------------------------
-- Game API (every call guarded; names differ between client generations)
---------------------------------------------------------------------------
local isSecret = (type(issecretvalue) == "function") and issecretvalue or function() return false end

local function plain(...)       -- the first value unless it is hidden by the game
    local v = ...
    if isSecret(v) then return nil end
    return v
end

local function try(fn, ...)     -- pcall that gives back only the first result or nil
    if type(fn) ~= "function" then return nil end
    local ok, value = pcall(fn, ...)
    if ok and not isSecret(value) then return value end
    return nil
end

local function truthy(value) return value and value ~= 0 and true or false end

local function inCombat()
    if InCombatLockdown then
        local ok, locked = pcall(InCombatLockdown)
        return ok and locked and true or false
    end
    return false
end

local function spellNameOf(id)
    if C_Spell and C_Spell.GetSpellName then return try(C_Spell.GetSpellName, id) end
    if C_Spell and C_Spell.GetSpellInfo then
        local info = try(C_Spell.GetSpellInfo, id)
        return type(info) == "table" and info.name or nil
    end
    if GetSpellInfo then return try(GetSpellInfo, id) end
end

local function spellHarmful(id, name)
    if C_Spell and C_Spell.IsSpellHarmful then return truthy(try(C_Spell.IsSpellHarmful, id)) end
    if IsHarmfulSpell and name then return truthy(try(IsHarmfulSpell, name)) end
    return false
end

local function spellHelpful(id, name)
    if C_Spell and C_Spell.IsSpellHelpful then return truthy(try(C_Spell.IsSpellHelpful, id)) end
    if IsHelpfulSpell and name then return truthy(try(IsHelpfulSpell, name)) end
    return false
end

-- Whether the spell has a range at all, i.e. can be cast on another unit: true / false, nil = this client cannot tell. Self buffs
-- (Stoneform), tracking (Find Treasure, Track Beasts) and spells around the caster have none.
local function spellHasRange(id, name)
    if C_Spell and C_Spell.SpellHasRange then
        local v = try(C_Spell.SpellHasRange, id)
        if v ~= nil then return truthy(v) end
    end
    if SpellHasRange then
        local v = try(SpellHasRange, name or id)
        if v ~= nil then return truthy(v) end
    end
    return nil
end
C.spellHasRange = spellHasRange

---------------------------------------------------------------------------
-- The unit under the finger
---------------------------------------------------------------------------
local skinWords = nil
local function skinStrings()
    if skinWords then return skinWords end
    skinWords = {}
    for _, name in ipairs({ "UNIT_SKINNABLE_LEATHER", "UNIT_SKINNABLE_HERB", "UNIT_SKINNABLE_ROCK", "UNIT_SKINNABLE_BOLTS", "UNIT_SKINNABLE" }) do
        local text = _G[name]
        if type(text) == "string" and text ~= "" then skinWords[text] = true end
    end
    return skinWords
end
C.skinStrings = skinStrings

local scanTip
local function tooltipLines(unit)
    local lines = {}
    if C_TooltipInfo and C_TooltipInfo.GetUnit then
        local data = try(C_TooltipInfo.GetUnit, unit)
        if type(data) == "table" and type(data.lines) == "table" then
            for _, line in ipairs(data.lines) do
                if type(line) == "table" and type(line.leftText) == "string" then lines[#lines + 1] = line.leftText end
            end
            return lines
        end
    end
    if CreateFrame then
        if not scanTip and CreateFrame then
            local ok, tip = pcall(CreateFrame, "GameTooltip", "TouchForeverContextTip", nil, "GameTooltipTemplate")
            if ok then scanTip = tip end
        end
        if scanTip and scanTip.SetOwner and scanTip.SetUnit then
            pcall(scanTip.SetOwner, scanTip, UIParent, "ANCHOR_NONE")
            pcall(scanTip.SetUnit, scanTip, unit)
            for index = 1, 12 do
                local region = _G["TouchForeverContextTipTextLeft" .. index]
                if not region or not region.GetText then break end
                local text = try(region.GetText, region)
                if type(text) == "string" then lines[#lines + 1] = text end
            end
        end
    end
    return lines
end

-- Whether the (dead) unit can be skinned: a tooltip line is one of the game's own "skinnable" strings (localised by the client).
local function skinnable(unit)
    local words = skinStrings()
    if next(words) == nil then return false end
    for _, text in ipairs(tooltipLines(unit)) do
        if words[text] then return true end
    end
    return false
end

-- 0 unknown, 1 far, 2 within 28 yd, 3 within 11 yd, 4 within 9.9 yd. CheckInteractDistance may be unavailable in combat.
local function reachOf(unit)
    if not CheckInteractDistance then return 0 end
    local function within(index)
        local ok, result = pcall(CheckInteractDistance, unit, index)
        if not ok or isSecret(result) then return nil end
        return result
    end
    local duel, trade, inspect, follow = within(3), within(2), within(1), within(4)
    if duel == nil and trade == nil and inspect == nil and follow == nil then return 0 end
    if duel then return 4 end
    if trade then return 3 end
    if inspect or follow then return 2 end
    return 1
end

-- relation: 1 friend, 2 hostile, 3 neutral, 0 unknown; second result the reaction (1..8, 0 unknown). Everything that can be attacked
-- (UnitCanAttack, also yellow / neutral mobs) counts as hostile: it gets the attack menu, "talk" is for what cannot be attacked.
local function relationOf(unit, canAttack)
    local reaction = try(UnitReaction, unit, "player")
    if canAttack then return 2, (type(reaction) == "number") and reaction or 0 end
    if type(reaction) == "number" then
        if reaction <= 3 then return 2, reaction end
        if reaction == 4 then return 3, reaction end
        return 1, reaction
    end
    if truthy(try(UnitIsFriend, "player", unit)) then return 1, 0 end
    return 0, 0
end

-- The unit table of the payload.
function C.readUnit()
    local u = { exists = false, player = false, dead = false, skinnable = false, relation = 0, source = 0, reach = 0, group = false,
        reaction = 0, hasTarget = false, isTarget = false, isSelf = false, canAttack = false }
    local unit
    if UnitExists and truthy(try(UnitExists, "mouseover")) then
        unit, u.source = "mouseover", 1
    elseif UnitExists and truthy(try(UnitExists, "target")) then
        unit, u.source = "target", 2
    end
    u.hasTarget = UnitExists and truthy(try(UnitExists, "target")) or false
    if not unit then
        u.source = 0
        return u
    end
    u.exists = true
    u.isSelf = truthy(try(UnitIsUnit, unit, "player"))
    u.isTarget = (unit == "target") or truthy(try(UnitIsUnit, unit, "target"))
    u.player = truthy(try(UnitIsPlayer, unit))
    u.dead = truthy(try(UnitIsDeadOrGhost, unit)) or truthy(try(UnitIsDead, unit))
    u.canAttack = truthy(try(UnitCanAttack, "player", unit)) and not u.isSelf
    u.relation, u.reaction = relationOf(unit, u.canAttack)
    u.group = truthy(try(UnitInParty, unit)) or truthy(try(UnitInRaid, unit))
    u.reach = reachOf(unit)
    if u.dead and not u.player then
        local ok, result = pcall(skinnable, unit)
        u.skinnable = ok and result and true or false
    end
    return u
end

---------------------------------------------------------------------------
-- The item slot under the pointer (bags, bank, character window)
---------------------------------------------------------------------------
local function mouseFrame()
    if GetMouseFoci then
        local foci = try(GetMouseFoci)
        if type(foci) == "table" then return foci[1] end
    end
    if GetMouseFocus then return try(GetMouseFocus) end
end

C.mouseFrame = mouseFrame

-- bag id of the lowest bank bag: the bank itself is -1 (and the reagent bank -3); the bags of the bank come after the player's bags
local function bankBagMin()
    if Enum and Enum.BagIndex and Enum.BagIndex.BankBag_1 then return Enum.BagIndex.BankBag_1 end
    return (NUM_BAG_SLOTS or 4) + 1
end

local function bagKind(bag)
    if bag == -1 or bag == -3 or bag >= bankBagMin() then return 2 end
    return 1
end

-- Where the pointer rests: { kind = 0..3, bag =, slot =, inv = } for a bag / bank / equipment slot button, else { kind = 0 }.
function C.focusSlot(frame)
    frame = frame or mouseFrame()
    if type(frame) ~= "table" then return { kind = 0 } end
    local name = frame.GetName and try(frame.GetName, frame) or nil
    if type(frame.GetBagID) == "function" then
        local bag, slot = try(frame.GetBagID, frame), try(frame.GetID, frame)
        if type(bag) == "number" and type(slot) == "number" then return { kind = bagKind(bag), bag = bag, slot = slot } end
    end
    if type(name) == "string" then
        if string.match(name, "^ContainerFrame%d+Item%d+$") and frame.GetParent then
            local parent = try(frame.GetParent, frame)
            local bag = parent and try(parent.GetID, parent)
            local id = try(frame.GetID, frame)
            if type(bag) == "number" and type(id) == "number" then return { kind = bagKind(bag), bag = bag, slot = id } end
        end
        if string.match(name, "^Character.+Slot$") and frame.GetID then
            local inv = try(frame.GetID, frame)
            if type(inv) == "number" and inv > 0 then return { kind = 3, inv = inv } end
        end
        if string.match(name, "^BankFrameItem%d+$") and frame.GetID then
            local id = try(frame.GetID, frame)
            if type(id) == "number" then return { kind = 2, bag = -1, slot = id } end
        end
    end
    return { kind = 0 }
end

local function containerItem(bag, slot)
    if C_Container and C_Container.GetContainerItemInfo then
        local info = try(C_Container.GetContainerItemInfo, bag, slot)
        if type(info) == "table" then return info.hyperlink, info.stackCount or 1 end
        return nil
    end
    if GetContainerItemInfo then
        local _, count, _, _, _, _, link = GetContainerItemInfo(bag, slot)
        return link, count or 1
    end
end

-- The link and stack size of the item on a slot found by focusSlot (nil link = empty).
function C.slotItem(where)
    if where.kind == 3 then
        local link = GetInventoryItemLink and try(GetInventoryItemLink, "player", where.inv)
        local count = GetInventoryItemCount and try(GetInventoryItemCount, "player", where.inv) or 1
        return link, count
    elseif where.kind == 1 or where.kind == 2 then
        return containerItem(where.bag, where.slot)
    end
end

-- Whether the item can be worn: the game's own check (the namespace of newer clients first, 1.15 has moved the global one),
-- then the equip slot of the item data. Second result: which way answered (for /tf kontext).
local function equipInfo(link)
    if C_Item and C_Item.IsEquippableItem then
        local v = try(C_Item.IsEquippableItem, link)
        if v ~= nil then return truthy(v), "C_Item.IsEquippableItem" end
    end
    if IsEquippableItem then
        local v = try(IsEquippableItem, link)
        if v ~= nil then return truthy(v), "IsEquippableItem" end
    end
    local function slotOf(fn, index)
        if type(fn) ~= "function" then return nil end
        local results = { pcall(fn, link) }
        if not results[1] then return nil end
        local loc = results[index + 1]
        if isSecret(loc) then return nil end
        return loc
    end
    local loc = slotOf(C_Item and C_Item.GetItemInfoInstant, 4)
    if loc == nil then loc = slotOf(GetItemInfoInstant, 4) end
    if loc ~= nil then return (type(loc) == "string" and loc ~= "" and loc ~= "INVTYPE_NON_EQUIP"), "equip slot (item data)" end
    loc = slotOf(GetItemInfo, 9)
    if loc ~= nil then return (type(loc) == "string" and loc ~= "" and loc ~= "INVTYPE_NON_EQUIP"), "equip slot (GetItemInfo)" end
    return false, "no way to tell"
end
C.equipInfo = equipInfo

local function itemIsEquippable(link)
    return (equipInfo(link))
end

local function itemIsUsable(link)
    if C_Item and C_Item.GetItemSpell then
        local results = { pcall(C_Item.GetItemSpell, link) }
        if results[1] and results[2] ~= nil then return true end
    end
    if GetItemSpell then return try(GetItemSpell, link) ~= nil end
    if C_Item and C_Item.IsUsableItem then return truthy(try(C_Item.IsUsableItem, link)) end
    return false
end

---------------------------------------------------------------------------
-- The quest under the pointer (quest log entry, quest tracker block)
---------------------------------------------------------------------------
-- The game gives no direct answer to "which quest is under the pointer", so the frame under the pointer and its parents are
-- looked at (a quest log button or a tracker block carries the quest id as a field, older buttons the log index as their id). Nothing
-- is assumed: the id must be a quest that really is in the log (a header is none), else there is no quest and everything stays as
-- before. Every access is guarded.
C.QUEST_KIND = 4
local QUEST_DEPTH = 6

-- All the fields that can name a quest, read in one guarded call.
local function questFields(frame)
    return frame.questID, frame.questId, frame.questLogIndex, frame.id, frame.module
end

local function isQuestModule(module)
    if type(module) ~= "table" then return false end
    for _, name in ipairs({ "QUEST_TRACKER_MODULE", "QuestObjectiveTracker", "CampaignQuestObjectiveTracker", "WorldQuestObjectiveTracker" }) do
        if module == _G[name] then return true end
    end
    return false
end

local function positive(value)
    if type(value) == "number" and value > 0 and not isSecret(value) then return math.floor(value) end
    return nil
end

local function questLogCount()
    if C_QuestLog and C_QuestLog.GetNumQuestLogEntries then return tonumber(try(C_QuestLog.GetNumQuestLogEntries)) or 0 end
    if GetNumQuestLogEntries then return tonumber(try(GetNumQuestLogEntries)) or 0 end
    return 0
end

-- Log index of a quest id (nil = not in the log).
local function logIndexOf(questID)
    local index
    if C_QuestLog and C_QuestLog.GetLogIndexForQuestID then index = try(C_QuestLog.GetLogIndexForQuestID, questID) end
    if positive(index) == nil and GetQuestLogIndexByID then index = try(GetQuestLogIndexByID, questID) end
    return positive(index)
end

-- Quest id of a log index (nil = a header or nothing).
local function questIdOfIndex(index)
    if C_QuestLog and C_QuestLog.GetInfo then
        local info = try(C_QuestLog.GetInfo, index)
        if type(info) == "table" then
            if info.isHeader then return nil end
            return positive(info.questID)
        end
    end
    if GetQuestLogTitle then
        local results = { pcall(GetQuestLogTitle, index) }
        if results[1] and not isSecret(results[5]) and not results[5] then return positive(results[9]) end
    end
    return nil
end

-- { id =, index = } for a quest id or a log index found at the frame, else nil. Needs a quest that is in the log.
local function resolveQuest(questID, index)
    if questID then
        index = logIndexOf(questID)
        if not index then return nil end
        return { id = questID, index = index }
    end
    if index then
        if index > questLogCount() then return nil end
        local id = questIdOfIndex(index)
        if id then return { id = id, index = index } end
    end
    return nil
end

-- The quest at a frame or one of its parents: { id =, index = } or nil.
function C.questAt(frame)
    if frame == nil then frame = mouseFrame() end
    for _ = 1, QUEST_DEPTH do
        if type(frame) ~= "table" then return nil end
        local ok, questID, questId, logIndex, id, module = pcall(questFields, frame)
        if ok then
            local quest = resolveQuest(positive(questID) or positive(questId), nil)
            if quest then return quest end
            if isQuestModule(module) then
                quest = resolveQuest(positive(id), nil)
                if quest then return quest end
            end
            if positive(logIndex) then
                quest = resolveQuest(nil, positive(logIndex))
                if quest then return quest end
            end
            -- the buttons of the older quest log: "QuestLogTitle<n>", their id is the log index
            local name = frame.GetName and try(frame.GetName, frame) or nil
            if type(name) == "string" and string.match(name, "^QuestLogTitle%d+$") and frame.GetID then
                quest = resolveQuest(nil, positive(try(frame.GetID, frame)))
                if quest then return quest end
            end
        end
        frame = frame.GetParent and try(frame.GetParent, frame) or nil
    end
    return nil
end

-- The quest under the pointer, unless an item slot is there (items go first).
function C.focusQuest()
    local frame = mouseFrame()
    if C.focusSlot(frame).kind ~= 0 then return nil end
    return C.questAt(frame)
end

-- Whether the quest can be shared: in a group and the game says it may be pushed. Older clients can only tell for the selected
-- quest (selecting it here would move the player's selection at every refresh), so there a group is enough; the key checks again.
local function questShareable(quest)
    if not (IsInGroup and truthy(try(IsInGroup))) then return false end
    if C_QuestLog and C_QuestLog.IsPushableQuest then return truthy(try(C_QuestLog.IsPushableQuest, quest.id)) end
    return true
end
C.questShareable = questShareable

local function questTracked(quest)
    if C_QuestLog and C_QuestLog.GetQuestWatchType then return try(C_QuestLog.GetQuestWatchType, quest.id) ~= nil end
    if C_QuestLog and C_QuestLog.IsQuestWatched then return truthy(try(C_QuestLog.IsQuestWatched, quest.id)) end
    if IsQuestWatched then return truthy(try(IsQuestWatched, quest.index)) end
    return false
end
C.questTracked = questTracked

local function questTitle(quest)
    local title
    if C_QuestLog and C_QuestLog.GetTitleForQuestID then title = try(C_QuestLog.GetTitleForQuestID, quest.id) end
    if type(title) ~= "string" and C_QuestLog and C_QuestLog.GetInfo then
        local info = try(C_QuestLog.GetInfo, quest.index)
        if type(info) == "table" then title = info.title end
    end
    if type(title) ~= "string" and GetQuestLogTitle then title = try(GetQuestLogTitle, quest.index) end
    if type(title) == "string" and title ~= "" then return title end
    return nil
end

local function questLevel(quest)
    if C_QuestLog and C_QuestLog.GetInfo then
        local info = try(C_QuestLog.GetInfo, quest.index)
        if type(info) == "table" and positive(info.level) then return positive(info.level) end
    end
    if GetQuestLogTitle then
        local results = { pcall(GetQuestLogTitle, quest.index) }
        if results[1] and positive(results[3]) then return positive(results[3]) end
    end
    return 0
end

-- The chat link of the quest: the game's own (checked: it must name this very quest, GetQuestLink takes an id in some clients and a
-- log index in others), else built in the link format the game uses.
function C.questLink(quest)
    local function names(link)
        return type(link) == "string" and tonumber(string.match(link, "|Hquest:(%d+)")) == quest.id
    end
    local link
    if GetQuestLink then
        link = try(GetQuestLink, quest.id)
        if not names(link) then link = try(GetQuestLink, quest.index) end
    end
    if not names(link) and C_QuestLog and C_QuestLog.GetQuestLink then link = try(C_QuestLog.GetQuestLink, quest.id) end
    if names(link) then return link end
    local title = questTitle(quest)
    if not title then return nil end
    return string.format("|cffffff00|Hquest:%d:%d|h[%s]|h|r", quest.id, questLevel(quest), title)
end

-- The focus table of the payload. Items first; a quest only where no item slot is.
function C.readFocus()
    local f = { kind = 0, filled = false, equippable = false, usable = false, stacked = false, merchant = false, cursor = false, count = 0 }
    if CursorHasItem then f.cursor = truthy(try(CursorHasItem)) end
    if MerchantFrame and MerchantFrame.IsShown then f.merchant = truthy(try(MerchantFrame.IsShown, MerchantFrame)) end
    local frame = mouseFrame()
    local where = C.focusSlot(frame)
    f.kind = where.kind
    if where.kind == 0 then
        local ok, quest = pcall(C.questAt, frame)
        if ok and quest then
            f.kind, f.filled = C.QUEST_KIND, true
            f.equippable = questShareable(quest)       -- for a quest: can be shared
            f.usable = questTracked(quest)             -- for a quest: is tracked
        end
        return f
    end
    local link, count = C.slotItem(where)
    if link then
        f.filled = true
        f.equippable = where.kind ~= 3 and itemIsEquippable(link)
        f.usable = itemIsUsable(link)
        f.count = math.min(tonumber(count) or 1, 15)
        f.stacked = (tonumber(count) or 1) > 1
    end
    return f
end

---------------------------------------------------------------------------
-- Spell lists
---------------------------------------------------------------------------
-- The spells of the spellbook in book order: { id =, name =, rank = }; known, not passive, not of another specialisation.
local function bookSpells()
    local out = {}
    local book = C_SpellBook
    if book and book.GetNumSpellBookSkillLines and book.GetSpellBookSkillLineInfo and book.GetSpellBookItemInfo then
        local bank = (Enum and Enum.SpellBookSpellBank and Enum.SpellBookSpellBank.Player) or 0
        local typeSpell = (Enum and Enum.SpellBookItemType and Enum.SpellBookItemType.Spell) or 1
        for line = 1, book.GetNumSpellBookSkillLines() do
            local lineInfo = book.GetSpellBookSkillLineInfo(line)
            if lineInfo and not lineInfo.offSpecID then
                local first = (lineInfo.itemIndexOffset or 0) + 1
                local last = (lineInfo.itemIndexOffset or 0) + (lineInfo.numSpellBookItems or 0)
                for index = first, last do
                    local info = book.GetSpellBookItemInfo(index, bank)
                    if info and not info.isOffSpec and info.itemType == typeSpell and type(info.spellID) == "number" then
                        local passive = info.isPassive
                        if passive == nil and book.IsSpellBookItemPassive then passive = book.IsSpellBookItemPassive(index, bank) end
                        if not passive then
                            local rank = tonumber(string.match(info.subName or "", "(%d+)")) or 0
                            out[#out + 1] = { id = info.spellID, name = info.name or spellNameOf(info.spellID), rank = rank }
                        end
                    end
                end
            end
        end
        return out
    end
    -- older books (tabs, GetSpellBookItemName)
    if GetNumSpellTabs and GetSpellTabInfo and GetSpellBookItemName then
        for tab = 1, GetNumSpellTabs() do
            local _, _, offset, count = GetSpellTabInfo(tab)
            for index = (offset or 0) + 1, (offset or 0) + (count or 0) do
                local kind, id = nil, nil
                if GetSpellBookItemInfo then kind, id = GetSpellBookItemInfo(index, "spell") end
                local name, sub = GetSpellBookItemName(index, "spell")
                local passive = IsPassiveSpell and IsPassiveSpell(index, "spell")
                if (kind == nil or kind == "SPELL") and type(id) == "number" and not passive then
                    out[#out + 1] = { id = id, name = name or spellNameOf(id), rank = tonumber(string.match(sub or "", "(%d+)")) or 0 }
                end
            end
        end
    end
    return out
end

-- Highest rank of every spell (by name) and the two lists. Pure given the book list and the two predicates; returns
-- enemy, friend (lists of { id =, name = }, at most SPELLS each) and whether some were left out.
function C.pickLists(book, harmful, helpful)
    local best, order = {}, {}
    for _, spell in ipairs(book) do
        local key = spell.name or ("#" .. tostring(spell.id))
        local known = best[key]
        if not known then
            best[key] = spell
            order[#order + 1] = key
        elseif spell.rank >= known.rank then
            best[key] = spell
        end
    end
    local enemy, friend, cut = {}, {}, false
    for _, key in ipairs(order) do
        local spell = best[key]
        if harmful(spell) then
            if #enemy < C.SPELLS then enemy[#enemy + 1] = spell else cut = true end
        end
        if helpful(spell) then
            if #friend < C.SPELLS then friend[#friend + 1] = spell else cut = true end
        end
    end
    return enemy, friend, cut
end

local enemyList, friendList, listsTruncated = {}, {}, false
local listsKnown = false
local listsDirty = true

local function rebuildLists()
    local ok, book = pcall(bookSpells)
    if not ok then return end
    -- Only spells that can be cast on another unit: a spell without a range is dropped (nil = cannot tell: it stays, and the
    -- range check against the target in entryState decides later).
    local function castable(spell)
        spell.hasRange = spellHasRange(spell.id, spell.name)
        return spell.hasRange ~= false
    end
    enemyList, friendList, listsTruncated = C.pickLists(book,
        function(spell) return spellHarmful(spell.id, spell.name) and castable(spell) end,
        function(spell) return spellHelpful(spell.id, spell.name) and castable(spell) end)
    listsKnown = #book > 0
    listsDirty = false
end

---------------------------------------------------------------------------
-- Buttons and keys
---------------------------------------------------------------------------
local buttons = {}          -- name -> button
local spellButtons = {}     -- 1..8
local keysArmed = false
local pendingBind = false

-- The game's own context menu of the target: what a right click on the target frame opens (whisper, invite, trade, inspect, follow,
-- duel, focus, marker ...). Only the opening is ours; the entries are tapped in the game, so nothing protected runs from here.
-- Way 1 (the one the launcher presses): a secure button of type "togglemenu" on the unit "target", bound to a key. A key press is a hardware
-- event, the click is made by the game's secure code, the menu is as untainted as one from a real right click on the frame (set focus,
-- target marker and the like keep working). PostClick looks whether a menu really opened; only if one surely did not, way 2
-- (insecure, fallbacks in this order): UnitPopup_OpenMenu (new menus), the target frame's drop down, a click on the target frame.
-- C.lastPopup keeps what happened for /tf kontext.
C.lastPopup = "not used yet"

-- true: a menu is open, false: surely none, nil: cannot tell with this client.
local function popupOpen()
    local seen = false
    if DropDownList1 and DropDownList1.IsShown then
        seen = true
        if truthy(try(DropDownList1.IsShown, DropDownList1)) then return true end
    end
    if Menu and Menu.GetManager then
        local manager = try(Menu.GetManager)
        if type(manager) == "table" and manager.GetOpenMenu then
            seen = true
            if try(manager.GetOpenMenu, manager) ~= nil then return true end
        end
    end
    if seen then return false end
    return nil
end
C.popupOpen = popupOpen

local function popupUnit()
    if UnitExists and truthy(try(UnitExists, "target")) then return "target" end
    if UnitExists and truthy(try(UnitExists, "mouseover")) then return "mouseover" end
end

-- Menu kind of the game for a unit (the names of the game's unit menus).
local function popupKind(unit)
    if truthy(try(UnitIsUnit, unit, "player")) then return "SELF" end
    if truthy(try(UnitIsPlayer, unit)) then
        if truthy(try(UnitInRaid, unit)) then return "RAID_PLAYER" end
        if truthy(try(UnitInParty, unit)) then return "PARTY" end
        if truthy(try(UnitCanAttack, "player", unit)) then return "ENEMY_PLAYER" end
        return "PLAYER"
    end
    return "TARGET"
end
C.popupKind = popupKind

-- Insecure ways to open the menu; returns the name of the way that worked (a menu is open or the client cannot tell), else nil.
function C.openPopupInsecure()
    local unit = popupUnit()
    if not unit then return nil end
    local ways = {}
    if UnitPopup_OpenMenu then
        ways[#ways + 1] = { "UnitPopup_OpenMenu", function()
            local name, server = nil, nil
            if UnitName then name, server = UnitName(unit) end
            UnitPopup_OpenMenu(popupKind(unit), { unit = unit, name = name, server = server })
        end }
    end
    if ToggleDropDownMenu and TargetFrameDropDown then
        ways[#ways + 1] = { "TargetFrameDropDown", function() ToggleDropDownMenu(1, nil, TargetFrameDropDown, "cursor", 0, 0) end }
    end
    if TargetFrame and TargetFrame.Click then
        ways[#ways + 1] = { "TargetFrame:Click", function() TargetFrame:Click("RightButton") end }
    end
    for _, way in ipairs(ways) do
        if pcall(way[2]) and popupOpen() ~= false then return way[1] end
    end
    return nil
end

-- PostClick of the secure button: if the secure way did not open a menu (a client without "togglemenu"), the insecure ways run.
local function popupAfterClick()
    local open = popupOpen()
    if open == true then
        C.lastPopup = "secure togglemenu: menu open"
        return
    end
    local way = C.openPopupInsecure()
    if way then
        C.lastPopup = "secure togglemenu: " .. (open == false and "no menu" or "unknown") .. ", then " .. way
    else
        C.lastPopup = "secure togglemenu: " .. (open == false and "no menu" or "unknown") .. ", no other way worked"
    end
end
C.popupAfterClick = popupAfterClick

-- The insecure actions. They run in the key press that the launcher sends, so the "hardware event" rules are met.
local function targetName()
    if not (UnitExists and truthy(try(UnitExists, "target"))) then return nil end
    local name
    if GetUnitName then name = try(GetUnitName, "target", true) end
    if not name and UnitName then name = try(UnitName, "target") end
    return name
end

local function playerTarget()
    if not (UnitIsPlayer and truthy(try(UnitIsPlayer, "target"))) then return nil end
    return targetName()
end

-- Puts a link into the chat box; nothing is sent. The open box takes it, else the box is opened with the link in it.
function C.insertChatLink(link)
    if ChatEdit_InsertLink and ChatEdit_InsertLink(link) then return true end
    if ChatFrame_OpenChat then return pcall(ChatFrame_OpenChat, link) end
    return false
end

-- Share the quest with the group (the game asks the members). Only a quest the game lets us push.
function C.questShare(quest)
    if not C.questShareable(quest) then return false end
    if C_QuestLog and C_QuestLog.SetSelectedQuest then pcall(C_QuestLog.SetSelectedQuest, quest.id)
    elseif SelectQuestLogEntry then pcall(SelectQuestLogEntry, quest.index) end
    if GetQuestLogPushable and not truthy(try(GetQuestLogPushable)) then return false end
    if QuestLogPushQuest then return pcall(QuestLogPushQuest) end
    return false
end

-- Track / untrack the quest (the tracker redraws itself on the game's event; older clients are asked to).
function C.questToggleTrack(quest)
    local tracked = C.questTracked(quest)
    if C_QuestLog and C_QuestLog.AddQuestWatch and C_QuestLog.RemoveQuestWatch then
        if tracked then
            pcall(C_QuestLog.RemoveQuestWatch, quest.id)
        else
            local manual = Enum and Enum.QuestWatchType and Enum.QuestWatchType.Manual
            pcall(C_QuestLog.AddQuestWatch, quest.id, manual)
        end
    elseif AddQuestWatch and RemoveQuestWatch then
        pcall(tracked and RemoveQuestWatch or AddQuestWatch, quest.index)
        if QuestWatch_Update then pcall(QuestWatch_Update) end
    else
        return false
    end
    return true
end

-- Whether the target is another player in my own party or raid, and I am in a group.
function C.targetInMyGroup()
    if not (IsInGroup and truthy(try(IsInGroup))) then return false end
    if not (UnitExists and truthy(try(UnitExists, "target"))) then return false end
    if truthy(try(UnitIsUnit, "target", "player")) then return false end
    return truthy(try(UnitInParty, "target")) or truthy(try(UnitInRaid, "target"))
end

-- Leave my group (the game's own call; no further question, the menu entry is the deliberate choice).
function C.leaveGroup()
    if not (IsInGroup and truthy(try(IsInGroup))) then return false end
    if C_PartyInfo and C_PartyInfo.LeaveParty then return pcall(C_PartyInfo.LeaveParty) end
    if LeaveParty then return pcall(LeaveParty) end
    return false
end

C.handlers = {
    -- Whisper (the launcher's mini menu for players): opens the chat box with "/w Name " for the target. The launcher then types on its
    -- on-screen keyboard or starts its voice input, which appends the spoken text to this very box (it never opens a box itself).
    whisper = function()
        local name = playerTarget()
        if not name then return end
        if ChatFrame_SendTell then pcall(ChatFrame_SendTell, name)
        elseif ChatFrame_OpenChat then pcall(ChatFrame_OpenChat, "/w " .. name .. " ") end
    end,
    -- Invite; with a target that is in my group (and I am in a group) the same key leaves the group instead (the launcher's entry
    -- "Leave group" stands in place of "Invite"). Never leaves without a group the game reports.
    invite = function()
        local name = playerTarget()
        if not name then return end
        if C.targetInMyGroup() then C.leaveGroup() return end
        if C_PartyInfo and C_PartyInfo.InviteUnit then pcall(C_PartyInfo.InviteUnit, name)
        elseif InviteUnit then pcall(InviteUnit, name) end
    end,
    trade = function()
        if playerTarget() and InitiateTrade then pcall(InitiateTrade, "target") end
    end,
    inspect = function()
        if playerTarget() and InspectUnit then pcall(InspectUnit, "target") end
    end,
    friend = function()
        local name = playerTarget()
        if not name then return end
        if C_FriendList and C_FriendList.AddFriend then pcall(C_FriendList.AddFriend, name)
        elseif AddFriend then pcall(AddFriend, name) end
    end,
    -- The item that a tap picked up goes back to where it came from. With a quest under the pointer the same key tracks / untracks
    -- the quest (the launcher sends it only from the quest menu; an item slot under the pointer always wins).
    clearcursor = function()
        local quest = C.focusQuest()
        if quest then C.questToggleTrack(quest) return end
        if ClearCursor then pcall(ClearCursor) end
    end,
    -- Links the item or, where no item slot is, the quest under the pointer: an open chat box gets the link, a closed one is opened with it.
    link = function()
        local link = C.slotItem(C.focusSlot())
        if not link then
            local quest = C.focusQuest()
            link = quest and C.questLink(quest)
        end
        if not link then return end
        C.insertChatLink(link)
    end,
    -- Shows the comparison with the worn gear (the pointer still rests on the item): the tooltip is built again with the CVar on.
    -- With a quest under the pointer the same key shares the quest with the group instead.
    compare = function()
        local quest = C.focusQuest()
        if quest then C.questShare(quest) return end
        C.alwaysCompare()
        local frame = C.mouseFrame()
        if type(frame) == "table" and frame.GetScript then
            local onEnter = try(frame.GetScript, frame, "OnEnter")
            if type(onEnter) == "function" then pcall(onEnter, frame) end
        end
    end,
    discard = function() C.discardAsk() end,
}
-- Item comparison with the worn gear without holding Shift (only when the client has the CVar).
function C.alwaysCompare()
    if GetCVar and SetCVar and not inCombat() then
        local current = try(GetCVar, "alwaysCompareItems")
        if current ~= nil and current ~= "1" then pcall(SetCVar, "alwaysCompareItems", "1") end
    end
end

-- Throwing an item away: the item is picked up (outside of combat), then a big Yes / No question names it. Only "Yes" deletes,
-- and only while the cursor still carries the very same item; everything else (No, Escape, another item, combat)
-- puts the item back with ClearCursor. Nothing is deleted without that tap.
C.pendingDiscard = nil          -- link of the item that waits for the answer
function C.discardAccept()
    local wanted = C.pendingDiscard
    C.pendingDiscard = nil
    if not wanted then return false end
    local kind, _, link = nil, nil, nil
    if GetCursorInfo then kind, _, link = GetCursorInfo() end
    if kind == "item" and link == wanted and DeleteCursorItem then
        DeleteCursorItem()
        return true
    end
    if ClearCursor then ClearCursor() end
    return false
end

function C.discardCancel()
    C.pendingDiscard = nil
    if ClearCursor then pcall(ClearCursor) end
end

function C.discardAsk()
    if inCombat() then return false end
    local where = C.focusSlot()
    if where.kind == 0 then return false end
    local link = C.slotItem(where)
    if not link then return false end
    if ClearCursor then pcall(ClearCursor) end
    if where.kind == 3 then
        if PickupInventoryItem then pcall(PickupInventoryItem, where.inv) end
    elseif C_Container and C_Container.PickupContainerItem then
        pcall(C_Container.PickupContainerItem, where.bag, where.slot)
    elseif PickupContainerItem then
        pcall(PickupContainerItem, where.bag, where.slot)
    end
    local kind, _, held = nil, nil, nil
    if GetCursorInfo then kind, _, held = GetCursorInfo() end
    if kind ~= "item" or not held then
        if ClearCursor then pcall(ClearCursor) end
        return false
    end
    C.pendingDiscard = held
    if StaticPopupDialogs and StaticPopup_Show then
        StaticPopupDialogs["TOUCHFOREVER_DISCARD"] = {
            text = L.really_throw_away_s_the_item,
            button1 = L.yes_throw_away,
            button2 = L.no,
            OnAccept = function() C.discardAccept() end,
            OnCancel = function() C.discardCancel() end,
            timeout = 0, whileDead = 1, hideOnEscape = 1, exclusive = 1, showAlert = 1, preferredIndex = 3,
        }
        pcall(StaticPopup_Show, "TOUCHFOREVER_DISCARD", held)
    else
        C.discardCancel()           -- no way to ask: the item stays
        return false
    end
    return true
end

-- The secure ones: macro text per action, and attributes per action (the menu of the target).
C.macros = { attack = "/startattack", follow = "/follow" }
C.secureAttrs = { popup = { type = "togglemenu", unit = "target" } }

local function secureButton(name)
    local button = CreateFrame("Button", name, UIParent, "SecureActionButtonTemplate")
    button:SetSize(1, 1)
    button:SetPoint("TOPLEFT", UIParent, "TOPLEFT", -60, 60)
    button:RegisterForClicks("AnyUp")
    button:SetAttribute("useOnKeyDown", false)
    button:SetAttribute("type", "macro")
    buttons[name] = button
    return button
end

local function plainButton(name, handler)
    local button = CreateFrame("Button", name, UIParent)
    button:SetSize(1, 1)
    button:SetPoint("TOPLEFT", UIParent, "TOPLEFT", -60, 60)
    button:RegisterForClicks("AnyUp")
    button:SetScript("OnClick", function() pcall(handler) end)
    buttons[name] = button
    return button
end

local function macroFor(index)
    local enemy, friend = enemyList[index], friendList[index]
    local parts = {}
    if enemy and enemy.name then parts[#parts + 1] = "[@target,harm,nodead] " .. enemy.name end
    if friend and friend.name then parts[#parts + 1] = "[@target,help,nodead] " .. friend.name end
    if #parts == 0 then return "" end
    return "/cast " .. table.concat(parts, "; ")
end
C.macroFor = macroFor

-- Assigns the spells to the spell buttons (outside of combat only: secure attributes).
local function applyLists()
    if inCombat() then pendingBind = true return false end
    for index = 1, C.SPELLS do
        local button = spellButtons[index]
        if button then button:SetAttribute("macrotext", macroFor(index)) end
    end
    return true
end

local function bindAll()
    if inCombat() then
        pendingBind = true
        return
    end
    pendingBind = false
    if not CreateFrame then return end
    for index = 1, C.SPELLS do
        if not spellButtons[index] then spellButtons[index] = secureButton("TouchForeverCtxSpell" .. index) end
    end
    for _, action in ipairs(C.ACTIONS) do
        local name = "TouchForeverCtx" .. string.upper(string.sub(action, 1, 1)) .. string.sub(action, 2)
        if not buttons[name] then
            if C.macros[action] then
                local button = secureButton(name)
                button:SetAttribute("macrotext", C.macros[action])
            elseif C.secureAttrs[action] then
                local button = secureButton(name)
                for attribute, value in pairs(C.secureAttrs[action]) do button:SetAttribute(attribute, value) end
                if action == "popup" and button.HookScript then button:HookScript("PostClick", function() pcall(popupAfterClick) end) end
            else
                plainButton(name, C.handlers[action])
            end
        end
    end
    if listsDirty then rebuildLists() end
    applyLists()
    C.alwaysCompare()
    if SetOverrideBindingClick then
        for index = 1, C.SPELLS do
            SetOverrideBindingClick(spellButtons[index], true, C.SPELL_KEYS[index], spellButtons[index]:GetName(), "LeftButton")
        end
        for _, action in ipairs(C.ACTIONS) do
            local name = "TouchForeverCtx" .. string.upper(string.sub(action, 1, 1)) .. string.sub(action, 2)
            SetOverrideBindingClick(buttons[name], true, C.KEYS[action], name, "LeftButton")
        end
        keysArmed = true
    end
end
C.bindAll = bindAll

local eventFrame = CreateFrame("Frame")
for _, event in ipairs({
    "PLAYER_LOGIN", "PLAYER_ENTERING_WORLD", "PLAYER_REGEN_ENABLED", "SPELLS_CHANGED", "LEARNED_SPELL_IN_SKILL_LINE",
    "PLAYER_SPECIALIZATION_CHANGED", "UPDATE_MOUSEOVER_UNIT", "PLAYER_TARGET_CHANGED", "ACTIONBAR_SLOT_CHANGED",
}) do
    pcall(eventFrame.RegisterEvent, eventFrame, event)
end
eventFrame:SetScript("OnEvent", function(_, event)
    if event == "SPELLS_CHANGED" or event == "LEARNED_SPELL_IN_SKILL_LINE" or event == "PLAYER_SPECIALIZATION_CHANGED" then
        listsDirty = true
        if not inCombat() then
            rebuildLists()
            applyLists()
        else
            pendingBind = true
        end
    elseif event == "PLAYER_LOGIN" or event == "PLAYER_ENTERING_WORLD" then
        bindAll()
    elseif event == "PLAYER_REGEN_ENABLED" then
        if pendingBind or listsDirty then
            if listsDirty then rebuildLists() end
            bindAll()
        end
    end
    -- mouseover / target changes and bar changes: the strip should show them at the next frame
    if ns.strip and ns.strip.touch then ns.strip.touch() end
end)

---------------------------------------------------------------------------
-- The payload for the strip
---------------------------------------------------------------------------
-- Class of the spell in an action bar slot: 1 harmful, 2 helpful, 3 both, 0 neither / unknown (macros, items).
local function barClassOf(slot)
    if not GetActionInfo then return 0 end
    local ok, kind, id = pcall(GetActionInfo, slot)
    if not ok or isSecret(kind) or isSecret(id) then return 0 end
    if kind == "macro" and GetMacroSpell and type(id) == "number" then
        local spell = try(GetMacroSpell, id)
        if type(spell) == "number" then kind, id = "spell", spell end
    end
    if kind ~= "spell" or type(id) ~= "number" then return 0 end
    local name = spellNameOf(id)
    return (spellHarmful(id, name) and 1 or 0) + (spellHelpful(id, name) and 2 or 0)
end

local function now() return GetTime and GetTime() or 0 end

-- Entry for the payload: range, usability and cooldown of one listed spell.
local function entryState(spell, t)
    local flags, cd = C.E_FILLED, 0
    local usable
    if C_Spell and C_Spell.IsSpellUsable then usable = try(C_Spell.IsSpellUsable, spell.id)
    elseif IsUsableSpell then usable = try(IsUsableSpell, spell.id) end
    if usable ~= nil and not truthy(usable) then flags = flags + C.E_NOT_USABLE end
    local range
    if C_Spell and C_Spell.IsSpellInRange then range = try(C_Spell.IsSpellInRange, spell.id, "target")
    elseif IsSpellInRange and spell.name then range = try(IsSpellInRange, spell.name, "target") end
    if range == false or range == 0 then flags = flags + C.E_OUT_OF_RANGE end
    -- No answer although there is a target: the spell cannot be cast on it (only judged when the book could not say, see rebuildLists).
    if range == nil and spell.hasRange == nil and UnitExists and truthy(try(UnitExists, "target")) then return nil end
    local start, duration
    if C_Spell and C_Spell.GetSpellCooldown then
        local info = try(C_Spell.GetSpellCooldown, spell.id)
        if type(info) == "table" then start, duration = plain(info.startTime), plain(info.duration) end
    elseif GetSpellCooldown then
        start, duration = try(GetSpellCooldown, spell.id), nil
        if start then
            local _, d = GetSpellCooldown(spell.id)
            duration = plain(d)
        end
    end
    if type(start) == "number" and type(duration) == "number" and duration > 0 then
        local left = start + duration - t
        if left > 0 then
            if duration > 1.51 then cd = left else flags = flags + C.E_GCD end
        end
    end
    return { id = spell.id, flags = flags, cd = cd }
end

local barCache, barCacheAt = {}, -10
local function barClasses(t)
    -- spells on the bar change rarely: looked at twice a second
    if t - barCacheAt >= 0.5 then
        barCacheAt = t
        for slot = 1, 12 do
            local ok, class = pcall(barClassOf, slot)
            barCache[slot] = ok and class or 0
        end
    end
    return barCache
end

-- The data of this moment (the table C.nibbles takes).
function C.read()
    local t = now()
    if listsDirty and not inCombat() then rebuildLists() end
    local entries = {}
    for i = 1, C.SPELLS do
        if enemyList[i] then entries[i] = entryState(enemyList[i], t) end
        if friendList[i] then entries[C.SPELLS + i] = entryState(friendList[i], t) end
    end
    local flags = 0
    if keysArmed then flags = flags + C.F_KEYS end
    if listsKnown then flags = flags + C.F_SPELLS end
    if listsTruncated then flags = flags + C.F_TRUNC end
    return { flags = flags, unit = C.readUnit(), focus = C.readFocus(), bar = barClasses(t), entries = entries }
end

-- Called by the strip at every refresh: the nibble list, or nil when the context cannot be read at all.
function C.get()
    local ok, data = pcall(C.read)
    if not ok or type(data) ~= "table" then return nil end
    return C.nibbles(data)
end

---------------------------------------------------------------------------
-- /tf kontext
---------------------------------------------------------------------------
local relationNames = { [0] = "unknown", "friend", "hostile", "neutral" }
local sourceNames = { [0] = "none", "mouseover", "target" }
local reachNames = { [0] = "unknown", "far (>28)", "<=28", "<=11", "<=9.9" }

function C.describe()
    local lines = {}
    local u = C.readUnit()
    lines[#lines + 1] = string.format("unit: %s (%s) player=%s dead=%s skinnable=%s relation=%s reaction=%d reach=%s group=%s target=%s self=%s angreifbar=%s",
        u.exists and "yes" or "none", sourceNames[u.source] or "?", tostring(u.player), tostring(u.dead), tostring(u.skinnable),
        relationNames[u.relation] or "?", u.reaction, reachNames[u.reach] or "?", tostring(u.group), tostring(u.isTarget), tostring(u.isSelf),
        u.canAttack and "ja" or "nein")
    local f = C.readFocus()
    lines[#lines + 1] = string.format("focus: kind=%d filled=%s equippable=%s usable=%s stacked=%s vendor=%s cursor=%s count=%d",
        f.kind, tostring(f.filled), tostring(f.equippable), tostring(f.usable), tostring(f.stacked), tostring(f.merchant), tostring(f.cursor), f.count)
    if f.kind == C.QUEST_KIND then
        local quest = C.focusQuest()
        local link = quest and C.questLink(quest)
        lines[#lines + 1] = string.format("quest: id=%s index=%s shareable=%s tracked=%s link=%s   (focus flags: equippable = shareable, usable = tracked)",
            tostring(quest and quest.id), tostring(quest and quest.index), tostring(f.equippable), tostring(f.usable), tostring(link))
    elseif f.kind ~= 0 and f.filled then
        local link = C.slotItem(C.focusSlot())
        local worn, how = C.equipInfo(link)
        lines[#lines + 1] = string.format("item: %s   equippable=%s (%s)", tostring(link), tostring(worn), how)
    end
    lines[#lines + 1] = string.format("keys armed: %s   lists known: %s   pending: %s   combat: %s", tostring(keysArmed), tostring(listsKnown),
        tostring(pendingBind), tostring(inCombat()))
    for index = 1, C.SPELLS do
        local button = spellButtons[index]
        local attr = button and button:GetAttribute("macrotext") or "(no button)"
        local bound = GetBindingAction and GetBindingAction(C.SPELL_KEYS[index], true) or "?"
        lines[#lines + 1] = string.format("%s -> %s   [%s]", C.SPELL_KEYS[index], attr ~= "" and attr or "(empty)", tostring(bound))
    end
    for _, action in ipairs(C.ACTIONS) do
        local bound = GetBindingAction and GetBindingAction(C.KEYS[action], true) or "?"
        lines[#lines + 1] = string.format("%s -> %s   [%s]", C.KEYS[action], action, tostring(bound))
    end
    lines[#lines + 1] = string.format("enemy list: %d, friend list: %d%s", #enemyList, #friendList, listsTruncated and " (more spells than fit)" or "")
    local names = {}
    for _, spell in ipairs(enemyList) do names[#names + 1] = (spell.name or "?") .. "(" .. tostring(spell.hasRange) .. ")" end
    lines[#lines + 1] = "enemy spells (has range): " .. (#names > 0 and table.concat(names, ", ") or "-")
    names = {}
    for _, spell in ipairs(friendList) do names[#names + 1] = (spell.name or "?") .. "(" .. tostring(spell.hasRange) .. ")" end
    lines[#lines + 1] = "friend spells (has range): " .. (#names > 0 and table.concat(names, ", ") or "-")
    lines[#lines + 1] = "game menu of the target: " .. tostring(C.lastPopup) .. "   open now: " .. tostring(popupOpen())
    return lines
end

C.text = L.context_for_the_app_s_long
