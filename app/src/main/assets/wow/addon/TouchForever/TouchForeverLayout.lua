-- TouchForever, part 2b: the game windows where the touch layout of the launcher leaves room. Own code, no Blizzard or third
-- party addon parts. The Edit Mode of the client keeps layouts; this part adds ONE layout "Touch Forever" (a copy of the layout
-- that is active, type Account) in which the action bars sit between the two sticks, the chat above the left stick, the unit
-- frames at the top left and the quest tracker at the right edge below the minimap, and switches to it. Nothing else of the player's layouts is
-- touched. Off (or "/tf layout off") goes back to the layout that was active before.
--   * never by itself without the signal of the launcher (ns.layoutStandard, written to TouchForeverLang.lua when the player
--     picked the standard layout there) and then only ONCE (db.layout.done); after that the player decides: /tf layout, options
--   * never in combat and never while the Edit Mode is open: the job waits (PLAYER_REGEN_ENABLED / Edit Mode closed)
--   * API used (Blizzard_EditMode, Blizzard_APIDocumentationGenerated/EditModeManagerDocumentation): C_EditMode.GetLayouts,
--     SaveLayouts, SetActiveLayout, OnLayoutAdded, IsValidLayoutName; presets via EditModePresetLayoutManager
-- Zones: fractions of the screen taken from the launcher's standard layout (profiles/controls-8.icp); see ZONE below.

local ADDON, ns = ...
local UI = ns and ns.UI
if not UI then return end

local L = ns.L

local LAY = {}
ns.layout = LAY

local LAYOUT_NAME = "Touch Forever"
local MAX_PER_TYPE = 5      -- Constants.EditModeConsts.EditModeMaxLayoutsPerType
local POLL_SECONDS = 3      -- while the Edit Mode is open: look again after this time
local POLL_MAX = 60
local LATE = 0.05

-- Where the launcher draws its controls (profiles/controls-8.icp; x as a fraction of the screen WIDTH, y as a fraction of the HEIGHT
-- from the top). The sizes of the controls follow the width (1/100 of it per snapping step), so the radii are fractions of the WIDTH:
-- left stick 6 + 3.25 steps (ring), right stick 6 + 6.25 steps (ring and outer ring), a little margin on top of both.
local ZONE = {
    leftStick = { 0.288, 0.75 }, rightStick = { 0.696, 0.75 }, leftR = 0.094, rightR = 0.124,
    radialsBottomY = 0.812, radialHalf = 0.0238, -- the emote and menu buttons between the sticks (centre y, half size as a fraction of the width)
    leftColumnRight = 0.095,                     -- buttons and scroll wheel at the left edge reach this far (fraction of the width)
    bookBottomY = 0.0345, bookHalf = 0.0243,     -- the top row ESC, keyboard, Menu, book (centre y: flush with the top edge; half size)
    topRowLeft = 0.417,
    topCentreRight = 0.612,                      -- right edge of the book button: bars at the top start behind it
    micLeft = 0.248, enterBottomY = 0.256,       -- microphone and Enter buttons above each other (left edge, centre y of the lower one)
}
LAY.ZONE = ZONE

local ICON_BASE = 45        -- action button at 100 % (Edit Mode icon size)
local ICON_PAD = 2
local UNIT_H = 100          -- height of a unit frame (player, target) at the top left
local UNIT_W = 200          -- width of a unit frame (measured on the device: 192 / 188 incl. portrait)
local TARGET_GAP = 205      -- left edge of the target frame minus the left edge of the player frame (device, units)
local CHAT_H = 170          -- chat window with its tabs (default size; the edit line sits in the 40 units below, see Chat)
local TRACKER_TOP = 0.272    -- quest tracker: just below the minimap (fraction of the height), flush with the right edge
local TRACKER_W = 248       -- quest tracker width (estimated)
local MINIMAP_H = 200       -- minimap with its coordinate line (estimated)
local MINIMAP_W = 220       -- minimap with its buttons at the top right (estimated; the layout leaves it where it is)
local SLIM_H, PET_W, STANCE_W = 40, 350, 200   -- stance bar and pet bar: height of both, width of the pet bar and of the stance bar (estimated)
local MICRO_H = 40          -- micro menu (estimated); the bags sit above it
local GAP = 6

local function inCombat() return InCombatLockdown and InCombatLockdown() end
local function later(seconds, fn) C_Timer.After(seconds + LATE, fn) end
local function say(text) print("|cffffd100TouchForever:|r " .. text) end
local function r1(v) return math.floor(v * 10 + 0.5) / 10 end

local function state()
    local ui = UI.GetDB()
    if not ui then return nil end
    ui.layout = ui.layout or {}
    return ui.layout
end

local function enumNum(group, key, fallback)
    local g = Enum and Enum[group]
    local v = type(g) == "table" and g[key]
    if type(v) == "number" then return v end
    return fallback
end

local function deepCopy(value)
    if type(value) ~= "table" then return value end
    local copy = {}
    for k, v in pairs(value) do copy[k] = deepCopy(v) end
    return copy
end

---------------------------------------------------------------------------
-- The plan: a pure function of the size of the UI parent (units of UIParent)
---------------------------------------------------------------------------
-- Icon size in steps of 10 % (raw value (percent - 50) / 10) for the given size of one icon (with its padding).
local function percentOf(per)
    local percent = math.floor((per - ICON_PAD) / ICON_BASE * 10) * 10
    percent = math.max(50, math.min(100, percent))
    return percent, (percent - 50) / 10
end

-- As many icons of a row as fit into `room` units.
local function iconPercent(room, count) return percentOf(room / count) end

-- Returns { entries = { {key, system, index, point, x, y, settings = {Name = value}} ... }, questHeight, percent, warnings }
-- Everything sits in the free areas around the controls of controls-8.icp (see ZONE): the main bar between the sticks below the two
-- round buttons, bar 2 below the left stick, bar 3 with stance and pet bar at the top behind the book button, chat above the left
-- stick, unit frames at the top left, side bars and quest tracker at the right edge, micro menu and bags in the bottom right corner.
function LAY.plan(w, h)
    local plan, warn = { entries = {}, warnings = {} }, {}
    local rL, rR = ZONE.leftR * w, ZONE.rightR * w
    local lx, ly = ZONE.leftStick[1] * w, ZONE.leftStick[2] * h
    local rx, ry = ZONE.rightStick[1] * w, ZONE.rightStick[2] * h
    local leftTop = ly - rL                     -- top edge of the left stick, from the top
    local rightTop = ry - rR
    local radialsBottom = ZONE.radialsBottomY * h + ZONE.radialHalf * w
    local bookBottom = ZONE.bookBottomY * h + ZONE.bookHalf * w

    -- bars: 2 rows of 6. Main bar between the sticks below the round buttons, bar 2 below the left stick; one icon size for all.
    local betweenLo, betweenHi = lx + rL, rx - rR
    local per = math.min((betweenHi - betweenLo - 2 * GAP) / 6, (h - radialsBottom - 2 * GAP) / 2, (h - (ly + rL) - 2 * GAP) / 2)
    local percent, raw = percentOf(per)
    plan.percent = percent
    local barH = 2 * (ICON_BASE * percent / 100 + ICON_PAD)
    if per < ICON_BASE * 0.5 + ICON_PAD then warn[#warn + 1] = "bars_small" end
    local function bar(key, index, point, x, y)
        plan.entries[#plan.entries + 1] = { key = key, system = "ActionBar", index = index, point = point, relativePoint = point,
            x = r1(x), y = r1(y), settings = { IconSize = raw, IconPadding = ICON_PAD, Orientation = 0, NumRows = 2, NumIcons = 12 } }
    end
    local function slim(key, index, x, y)
        plan.entries[#plan.entries + 1] = { key = key, system = "ActionBar", index = index, point = "TOPLEFT", relativePoint = "TOPLEFT",
            x = r1(x), y = r1(y), settings = {} }
    end
    bar("MainBar", enumNum("EditModeActionBarSystemIndices", "MainBar", 1), "BOTTOM", (betweenLo + betweenHi) / 2 - w / 2, GAP)
    bar("Bar2", enumNum("EditModeActionBarSystemIndices", "Bar2", 2), "BOTTOM", lx - w / 2, GAP)
    local topX = ZONE.topCentreRight * w + GAP
    -- bar 3 stands right of the book button at the top; where the minimap leaves no room for its 6 icons it goes below the book button
    local barW = 6 * (ICON_BASE * percent / 100 + ICON_PAD)
    local bar3Fits = topX + barW <= w - MINIMAP_W - GAP
    local bar3Top = bar3Fits and GAP or bookBottom + 4
    bar("Bar3", enumNum("EditModeActionBarSystemIndices", "Bar3", 3), "TOPLEFT", bar3Fits and topX or math.min(topX, w - MINIMAP_W - GAP - barW), -bar3Top)
    local stanceTop = bar3Top + barH + 4
    slim("StanceBar", enumNum("EditModeActionBarSystemIndices", "StanceBar", 11), math.min(topX, w - MINIMAP_W - GAP - STANCE_W), -stanceTop)
    -- the pet bar is wider: it starts below the book button and ends before the minimap
    local petTop = math.max(stanceTop + SLIM_H + 4, bookBottom + 4)
    slim("PetActionBar", enumNum("EditModeActionBarSystemIndices", "PetActionBar", 12), math.min(topX, w - MINIMAP_W - GAP - PET_W), -petTop)

    -- side bars: the quest tracker owns the right edge (below the minimap), so the two side bars (6 icons each, in a column) stand
    -- left of it, between the minimap row and the top of the right stick
    local sideTop = math.max(TRACKER_TOP * h, MINIMAP_H + 2)    -- also the top of the tracker
    local sidePercent, sideRaw = iconPercent(rightTop - GAP - sideTop, 6)
    local sideSize = ICON_BASE * sidePercent / 100 + ICON_PAD
    local side = { IconSize = sideRaw, IconPadding = ICON_PAD, Orientation = 1, NumRows = 1, NumIcons = 6 }
    plan.entries[#plan.entries + 1] = { key = "RightBar1", system = "ActionBar", index = enumNum("EditModeActionBarSystemIndices", "RightBar1", 4),
        point = "TOPRIGHT", relativePoint = "TOPRIGHT", x = r1(-(TRACKER_W + GAP)), y = r1(-sideTop), settings = deepCopy(side) }
    plan.entries[#plan.entries + 1] = { key = "RightBar2", system = "ActionBar", index = enumNum("EditModeActionBarSystemIndices", "RightBar2", 5),
        point = "TOPRIGHT", relativePoint = "TOPRIGHT", x = r1(-(TRACKER_W + GAP + sideSize)), y = r1(-sideTop), settings = deepCopy(side) }

    -- chat: right of the buttons at the left edge, above the left stick (the edit line needs about 40 more units), below the Enter button
    plan.entries[#plan.entries + 1] = { key = "Chat", system = "ChatFrame", index = 1, point = "BOTTOMLEFT", relativePoint = "BOTTOMLEFT",
        x = r1(ZONE.leftColumnRight * w + GAP), y = r1(h - leftTop + 40), settings = {} }
    local chatTop = leftTop - 40 - CHAT_H       -- top edge of the chat, from the top
    if chatTop < ZONE.enterBottomY * h + ZONE.radialHalf * w + GAP then warn[#warn + 1] = "chat_high" end

    -- unit frames at the top left, left of the microphone button
    plan.entries[#plan.entries + 1] = { key = "Player", system = "UnitFrame", index = enumNum("EditModeUnitFrameSystemIndices", "Player", 1),
        point = "TOPLEFT", relativePoint = "TOPLEFT", x = GAP, y = -GAP, settings = {} }
    -- the target frame sits right of the player frame (same row); where the top row of buttons leaves no room it goes below it
    local targetX = GAP + TARGET_GAP
    local targetBeside = targetX + UNIT_W <= ZONE.topRowLeft * w - GAP
    plan.entries[#plan.entries + 1] = { key = "Target", system = "UnitFrame", index = enumNum("EditModeUnitFrameSystemIndices", "Target", 2),
        point = "TOPLEFT", relativePoint = "TOPLEFT", x = targetBeside and targetX or GAP,
        y = targetBeside and -GAP or -(GAP + UNIT_H + 8), settings = {} }
    -- the unit frames end above the microphone button (or, with the target below, stay left of it)
    local unitBottom = targetBeside and GAP + UNIT_H or GAP + 2 * UNIT_H + 8
    if unitBottom > ZONE.enterBottomY * h - 2 * ZONE.radialHalf * w - ZONE.radialHalf * w - GAP and GAP + UNIT_W > ZONE.micLeft * w then
        warn[#warn + 1] = "unit_mic"
    end

    -- quest tracker: flush with the right edge directly below the minimap, down to the top of the right stick; the height goes to the addon (questsHeight)
    plan.entries[#plan.entries + 1] = { key = "Tracker", system = "ObjectiveTracker", index = nil, point = "TOPRIGHT", relativePoint = "TOPRIGHT",
        x = -2, y = r1(-sideTop), settings = {} }
    local height = math.floor((rightTop - sideTop - 8) / 10) * 10
    plan.questHeight = math.max(100, math.min(1000, height))
    if height < 100 then warn[#warn + 1] = "tracker_low" end

    -- micro menu and bags in the bottom right corner (below the side bars, right of the right stick)
    plan.entries[#plan.entries + 1] = { key = "MicroMenu", system = "MicroMenu", index = nil, point = "BOTTOMRIGHT", relativePoint = "BOTTOMRIGHT",
        x = -GAP, y = GAP, settings = {} }
    plan.entries[#plan.entries + 1] = { key = "Bags", system = "Bags", index = nil, point = "BOTTOMRIGHT", relativePoint = "BOTTOMRIGHT",
        x = -GAP, y = GAP + MICRO_H + 4, settings = {} }

    -- game tooltip (default anchor): bottom left above the buttons at the left edge, where the touch layout leaves room (device 11.11.2026:
    -- left edge 5, bottom edge 193 units above the bottom at a height of 768, 122 x 69 units); the entry is skipped by the game if the client has no such system
    plan.entries[#plan.entries + 1] = { key = "Tooltip", system = "HudTooltip", index = nil, point = "BOTTOMLEFT", relativePoint = "BOTTOMLEFT",
        x = 5, y = r1(0.2517 * h), settings = {} }
    plan.warnings = warn
    return plan
end

---------------------------------------------------------------------------
-- Layout data
---------------------------------------------------------------------------
local function systemId(name)
    -- MicroMenu and Bags have no fallback number: without them in Enum the entry counts as missing and the game keeps their place
    return enumNum("EditModeSystem", name, ({ ActionBar = 0, Minimap = 2, UnitFrame = 3, ChatFrame = 8, ObjectiveTracker = 12 })[name])
end

local function findSystem(info, entry)
    local system = systemId(entry.system)
    for _, si in ipairs(info.systems or {}) do
        if si.system == system and (entry.index == nil or si.systemIndex == entry.index) then return si end
    end
end

local function setSetting(si, setting, value)
    si.settings = si.settings or {}
    for _, s in ipairs(si.settings) do
        if s.setting == setting then s.value = value; return end
    end
    si.settings[#si.settings + 1] = { setting = setting, value = value }
end

-- Moves the systems of `info` (a layout) as the plan says. Returns nil, reason when the layout cannot take the plan
-- ("nomainbar" = Gamepad interface, whose layout has no main bar).
function LAY.apply(info, plan)
    if not findSystem(info, plan.entries[1]) then return nil, "nomainbar" end
    local missing = {}
    for _, entry in ipairs(plan.entries) do
        local si = findSystem(info, entry)
        if si then
            si.anchorInfo = { point = entry.point, relativeTo = "UIParent", relativePoint = entry.relativePoint,
                offsetX = entry.x, offsetY = entry.y }
            si.anchorInfo2 = nil
            si.isInDefaultPosition = false
            for name, value in pairs(entry.settings) do
                setSetting(si, enumNum("EditModeActionBarSetting", name, ({ Orientation = 0, NumRows = 1, NumIcons = 2, IconSize = 3, IconPadding = 4 })[name]), value)
            end
        else
            missing[#missing + 1] = entry.key
        end
    end
    return true, missing
end

---------------------------------------------------------------------------
-- Edit Mode access
---------------------------------------------------------------------------
local function apiReady()
    return type(C_EditMode) == "table" and C_EditMode.GetLayouts and C_EditMode.SaveLayouts and C_EditMode.SetActiveLayout
        and C_EditMode.OnLayoutAdded and true or false
end

local function numPresets() return enumNum("EditModePresetLayoutsMeta", "NumValues", 5) end

local function presetList()
    local list
    local manager = _G.EditModePresetLayoutManager
    if type(manager) == "table" and manager.GetCopyOfPresetLayouts then
        local ok, result = pcall(manager.GetCopyOfPresetLayouts, manager)
        if ok and type(result) == "table" then list = result end
    end
    if not list and _G.EditModeManagerFrame and EditModeManagerFrame.GetLayouts then
        local ok, all = pcall(EditModeManagerFrame.GetLayouts, EditModeManagerFrame)
        if ok and type(all) == "table" then
            list = {}
            for i = 1, numPresets() do list[i] = deepCopy(all[i]) end
        end
    end
    return list
end

local function editOpen()
    local frame = _G.EditModeManagerFrame
    if type(frame) ~= "table" then return false end
    if frame.IsEditModeActive then
        local ok, active = pcall(frame.IsEditModeActive, frame)
        if ok then return active and true or false end
    end
    return frame.IsShown and frame:IsShown() and true or false
end

local function typeId(name, fallback) return enumNum("EditModeLayoutType", name, fallback) end

local function readState()
    local raw = C_EditMode.GetLayouts()
    if type(raw) ~= "table" or type(raw.layouts) ~= "table" then return nil end
    return raw, raw.layouts, tonumber(raw.activeLayout) or 1
end

local function findOwn(saved)
    local preset = typeId("Preset", 0)
    for i, info in ipairs(saved) do
        if info.layoutName == LAYOUT_NAME and info.layoutType ~= preset then return i end
    end
end

local function activeInfo(saved, activeIdx, presets)
    local n = numPresets()
    if activeIdx > n then return saved[activeIdx - n] end
    if presets then return presets[activeIdx] end
    if _G.EditModeManagerFrame and EditModeManagerFrame.GetActiveLayoutInfo then
        local ok, info = pcall(EditModeManagerFrame.GetActiveLayoutInfo, EditModeManagerFrame)
        if ok then return info end
    end
end

local function describe(info, idx)
    return { name = info and info.layoutName or "", type = info and info.layoutType or 0, index = idx }
end

local function payload(presets, saved, active)
    local layouts = {}
    for _, p in ipairs(presets or {}) do layouts[#layouts + 1] = p end
    for _, s in ipairs(saved) do layouts[#layouts + 1] = s end
    return { layouts = layouts, activeLayout = active }
end

local function screenSize()
    local w, h = 0, 0
    if UIParent and UIParent.GetWidth then w, h = UIParent:GetWidth(), UIParent:GetHeight() end
    if not w or w < 100 or not h or h < 100 then w, h = 1366, 768 end
    return w, h
end

local function setQuestHeight(d, plan)
    local ui = UI.GetDB()
    if ui and ui.questsHeight == nil and plan.questHeight then
        UI.SetQuestHeight(plan.questHeight)
        d.questSet = plan.questHeight
    end
end

local function activateLater(idx)
    later(0.6, function()
        local ok, raw = pcall(C_EditMode.GetLayouts)
        if ok and type(raw) == "table" and tonumber(raw.activeLayout) ~= idx then pcall(C_EditMode.SetActiveLayout, idx) end
    end)
end

---------------------------------------------------------------------------
-- Actions (only outside of combat and with the Edit Mode closed; see run())
---------------------------------------------------------------------------
-- on / again: returns a status word ("ok", "again", "already", "gamepad", "full", "api", "unreadable", "name")
local function doEnable(d, again)
    if not apiReady() then return "api" end
    local raw, saved, activeIdx = readState()
    if not raw then return "unreadable" end
    local n = numPresets()
    local plan = LAY.plan(screenSize())
    local own = findOwn(saved)
    if own then
        local idx = n + own
        if again then
            local ok, why = LAY.apply(saved[own], plan)
            if not ok then return why == "nomainbar" and "gamepad" or "unreadable" end
            C_EditMode.SaveLayouts(payload(presetList(), saved, activeIdx))
            setQuestHeight(d, plan)
            C_EditMode.SetActiveLayout(idx)
            activateLater(idx)
            d.active = true
            return "again"
        end
        if activeIdx == idx then d.active = true; return "already" end
        d.prev = describe(activeInfo(saved, activeIdx, presetList()), activeIdx)
        C_EditMode.SetActiveLayout(idx)
        activateLater(idx)
        setQuestHeight(d, plan)
        d.active = true
        return "ok"
    end

    local accountType = typeId("Account", 1)
    local account = 0
    for _, info in ipairs(saved) do
        if info.layoutType == accountType then account = account + 1 end
    end
    if account >= MAX_PER_TYPE then return "full" end
    if C_EditMode.IsValidLayoutName and not C_EditMode.IsValidLayoutName(LAYOUT_NAME) then return "name" end
    local presets = presetList()
    local base = activeInfo(saved, activeIdx, presets)
    if type(base) ~= "table" then return "unreadable" end
    local info = deepCopy(base)             -- the copy keeps interfaceStyle, so that it can be activated
    info.layoutName, info.layoutType = LAYOUT_NAME, accountType
    local ok, why = LAY.apply(info, plan)
    if not ok then return why == "nomainbar" and "gamepad" or "unreadable" end
    local position = account + 1
    local newIdx = n + position
    table.insert(saved, position, info)
    local active = activeIdx
    if active >= newIdx then active = active + 1 end
    d.prev = describe(base, activeIdx)
    d.baseName = base.layoutName
    d.questSet = nil
    C_EditMode.SaveLayouts(payload(presets, saved, active))
    C_EditMode.OnLayoutAdded(newIdx, true, false)
    setQuestHeight(d, plan)
    activateLater(newIdx)
    d.active = true
    return "ok"
end

local function doDisable(d)
    if not apiReady() then return "api" end
    local raw, saved, activeIdx = readState()
    if not raw then return "unreadable" end
    local n = numPresets()
    local own = findOwn(saved)
    local result = "off"
    if d.questSet then
        local ui = UI.GetDB()
        if ui and ui.questsHeight == d.questSet then UI.SetQuestHeight(nil) end
        d.questSet = nil
    end
    if own and activeIdx == n + own then
        local prev, target = d.prev, nil
        if prev and prev.name ~= "" then
            if prev.type == typeId("Preset", 0) then target = prev.index
            else
                for i, info in ipairs(saved) do
                    if info.layoutName == prev.name and info.layoutType == prev.type then target = n + i; break end
                end
            end
        end
        C_EditMode.SetActiveLayout(target or 1)
        result = target and "back" or "first"
        d.backName = prev and prev.name or ""
    elseif not own or d.active ~= true then
        result = "notOn"
    end
    d.active, d.prev = false, nil
    return result
end

---------------------------------------------------------------------------
-- Waiting for the end of combat / the Edit Mode
---------------------------------------------------------------------------
local pending, polls = nil, 0
local run

local function report(status, d, auto)
    if status == "ok" or status == "again" or status == "already" then
        say(string.format(L.layout_is_on, LAYOUT_NAME))
    elseif status == "back" then
        say(string.format(L.layout_back_to, d.backName ~= "" and d.backName or "-"))
    elseif status == "first" then
        say(string.format(L.layout_back_to, "1"))
    elseif status == "off" or status == "notOn" then
        if not auto then say(L.layout_was_not_on) end
    elseif status == "gamepad" then say(L.layout_gamepad)
    elseif status == "full" then say(L.layout_full)
    elseif status == "api" then say(L.layout_no_api)
    elseif status == "name" then say(L.layout_bad_name)
    else say(L.layout_unreadable) end
end

local function poll()
    if not pending then return end
    polls = polls + 1
    if polls > POLL_MAX then pending = nil; return end
    if inCombat() or editOpen() then later(POLL_SECONDS, poll); return end
    local p = pending
    pending = nil
    run(p.action, p.auto)
end

-- action: "on" | "off" | "again". The result word goes to the chat (not for the automatic start, if nothing happened).
function run(action, auto)
    local d = state()
    if not d then return "nodb" end
    if inCombat() or editOpen() then
        pending = { action = action, auto = auto }
        polls = 0
        if not auto then say(inCombat() and L.layout_wait_combat or L.layout_wait_edit) end
        if not inCombat() then later(POLL_SECONDS, poll) end
        return "wait"
    end
    pending = nil
    local ok, status = pcall(function()
        if action == "off" then return doDisable(d) end
        return doEnable(d, action == "again")
    end)
    if not ok then
        say(string.format(L.layout_failed, tostring(status)))
        return "error"
    end
    if action ~= "off" then d.done = true end   -- one definite try; after that the player decides
    report(status, d, auto)
    return status
end

---------------------------------------------------------------------------
-- Public
---------------------------------------------------------------------------
function LAY.Enable() return run("on", false) end
function LAY.Disable() return run("off", false) end
function LAY.Again() return run("again", false) end
function LAY.Pending() return pending and pending.action or nil end

-- true while our layout is the active one
function LAY.IsActive()
    local d = state()
    if not d or not d.active or not apiReady() then return false end
    local ok, raw, saved, activeIdx = pcall(readState)
    if not ok or not raw then return false end
    local own = findOwn(saved)
    return own ~= nil and activeIdx == numPresets() + own
end

function LAY.Set(value)
    if value then LAY.Enable() else LAY.Disable() end
end

function LAY.Info()
    local d = state() or {}
    local w, h = screenSize()
    local plan = LAY.plan(w, h)
    say(string.format(L.layout_info, LAY.IsActive() and L.on or L.off, math.floor(w), math.floor(h), plan.percent,
        (d.prev and d.prev.name ~= "") and d.prev.name or "-"))
    say(string.format("  signal=%s done=%s combat=%s edit=%s%s", tostring(ns.layoutStandard == true), tostring(d.done == true),
        tostring(inCombat() and true or false), tostring(editOpen()),
        #plan.warnings > 0 and (" warnings=" .. table.concat(plan.warnings, ",")) or ""))
end

-- /tf layout [on|off|again|info]
function LAY.Slash(rest)
    rest = string.lower(rest or "")
    if rest == "on" or rest == "an" then LAY.Enable()
    elseif rest == "off" or rest == "aus" then LAY.Disable()
    elseif rest == "again" or rest == "neu" then LAY.Again()
    elseif rest == "info" then LAY.Info()
    elseif rest == "" then LAY.Set(not LAY.IsActive())
    else say(L.tf_layout_on_off_again_info) end
end

---------------------------------------------------------------------------
-- Events: wait for the end of combat; once, and only with the launcher's signal, apply by itself
---------------------------------------------------------------------------
local frame = CreateFrame("Frame")
local started = false
frame:SetScript("OnEvent", function(_, event)
    local d = state()
    if not d then return end
    if event == "PLAYER_REGEN_ENABLED" then
        if pending then
            if editOpen() then later(POLL_SECONDS, poll)
            else local p = pending; pending = nil; run(p.action, p.auto) end
        end
    elseif event == "PLAYER_ENTERING_WORLD" and not started then
        started = true
        if ns.layoutStandard and not d.done then
            later(4, function()
                local now_d = state()
                if now_d and ns.layoutStandard and not now_d.done then run("on", true) end
            end)
        end
    end
end)
for _, event in ipairs({ "PLAYER_ENTERING_WORLD", "PLAYER_REGEN_ENABLED" }) do
    pcall(frame.RegisterEvent, frame, event)
end
if _G.EventRegistry and EventRegistry.RegisterCallback then
    pcall(EventRegistry.RegisterCallback, EventRegistry, "EditMode.Exit", function()
        if pending and not inCombat() then later(0.5, poll) end
    end, LAY)
end
