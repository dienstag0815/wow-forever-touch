#!/usr/bin/env python3
"""Runs the interface part of the addon TouchForever (UI.lua, Options.lua, TouchForever.lua) against a small mock of the WoW API (lupa).

Not part of the build; needs `pip install lupa` (throw-away venv). Run from the repo root:  python tools/addon_ui_check.py

Checks (see CHECKS below): every Lua file compiles; an old saved variables table (addon 1.5.0) gets the defaults of 1.6.0;
status bars in the modes standard / compact / minimap / off (scale, anchors, restore); compact chat (side buttons away, extra
width, tabs on touch); menu bar hide / show; the reload buttons are secure buttons (type macro, /reload) and ReloadUI is never
called from addon code; the settings window builds with all options; global names that are read but not defined are listed
(for review: most are optional WoW functions that the code tests before use).
"""
import pathlib
import re
import sys

from lupa import LuaRuntime

ADDON = pathlib.Path(__file__).resolve().parent.parent / "app/src/main/assets/wow/addon/TouchForever"
FILES = ["TouchForeverLang.lua", "TouchForeverLocale.lua", "TouchForever.lua", "TouchForeverUI.lua", "TouchForeverLayout.lua", "TouchForeverOptions.lua", "TouchForeverCooldown.lua"]

MOCK = r"""
local unpack = unpack or table.unpack
timers, printed, undefined, reloadCalls = {}, {}, {}, 0
combat = false
local clock = 1000
local registry = {}

local M = {}
local function dummy() return setmetatable({}, { __index = function() return function() end end }) end

function M.SetAlpha(f, a) f.__alpha = a end
function M.GetAlpha(f) return f.__alpha end
function M.SetScale(f, s) f.__scale = s end
function M.GetScale(f) return f.__scale end
function M.GetEffectiveScale(f) return f.__scale * (f.__parent and f.__parent:GetEffectiveScale() or 1) end
function M.SetWidth(f, w) f.__w = w end
function M.SetHeight(f, h) f.__h = h end
function M.SetSize(f, w, h) f.__w, f.__h = w, h end
function M.GetWidth(f) return f.__w end
function M.GetHeight(f) return f.__h end
function M.GetParent(f) return f.__parent end
function M.SetParent(f, p) f.__parent = p end
function M.Show(f) f.__shown = true end
function M.Hide(f) f.__shown = false end
function M.IsShown(f) return f.__shown end
function M.IsVisible(f) return f.__shown end
function M.EnableMouse(f, v) if f.__protected and combat then error("protected") end f.__mouse = v end
function M.IsMouseEnabled(f) return f.__mouse end
function M.GetObjectType(f) return f.__kind end
function M.GetName(f) return f.__name end
function M.ClearAllPoints(f) f.__points = {} end
function M.SetPoint(f, ...) f.__points[#f.__points + 1] = { n = select("#", ...), ... } end
function M.GetNumPoints(f) return #f.__points end
function M.GetPoint(f, i) local p = f.__points[i]; if p then return unpack(p, 1, 5) end end
function M.SetAllPoints(f) end
function M.SetScript(f, name, fn) f.__scripts[name] = fn end
function M.GetScript(f, name) return f.__scripts[name] end
function M.HookScript(f, name, fn)
    local old = f.__scripts[name]
    f.__scripts[name] = function(...) if old then old(...) end fn(...) end
end
function M.RegisterEvent(f, e) f.__events[e] = true end
function M.RegisterForClicks(f, ...) f.__clicks = { ... } end
function M.RegisterForDrag(f) end
function M.SetAttribute(f, k, v)
    if f.__secure and combat then error("SetAttribute in combat on secure frame") end
    f.__attrs[k] = v
end
function M.GetAttribute(f, k) return f.__attrs[k] end
function M.GetFont(f) return "Fonts\\FRIZQT__.TTF", f.__fontSize or 14, "" end
function M.SetFont(f, font, size) f.__fontSize = size end
function M.CreateTexture(f) return dummy() end
function M.CreateFontString(f) return dummy() end
function M.SetText(f, t) f.__text = t end
function M.Click(f) f.__clicked = (f.__clicked or 0) + 1 end
function M.IsMouseOver() return false end
-- left, bottom, width, height in the frame's own units; understands the two anchors the tests use (BOTTOMLEFT / TOPLEFT of UIParent)
function M.GetRect(f)
    local p = f.__points[1]
    local x, y = 0, 0
    if p and p[1] == "BOTTOMLEFT" then x, y = p[4] or 0, p[5] or 0
    elseif p and p[1] == "TOPLEFT" then x, y = p[4] or 0, (700 / f.__scale) + (p[5] or 0) - f.__h end
    return x, y, f.__w, f.__h
end
-- edges in the frame's own units: UIParent (1200 x 700), the Minimap (inset 12 / 30 units of the cluster from its top right corner), the
-- cluster itself (170 x 190), the BagsBar (TOPRIGHT of UIParent, height 40) and its buttons (30 wide, 4 apart, from the right edge on, the
-- shown ones only). Returns right, top, bottom, left in screen units. clampEnforce: the engine keeps the cluster inside the screen by the
-- clamp insets (a positive inset lets it out by that much), like SetClampedToScreen with SetClampRectInsets.
local BAG_ORDER = { "MainMenuBarBackpackButton", "CharacterBag0Slot", "CharacterBag1Slot", "CharacterBag2Slot", "CharacterBag3Slot",
    "CharacterReagentBag0Slot", "BagBarExpandToggle" }
local function clusterOffsets()
    local c = MinimapCluster
    local p = c.__points[1]
    if not p or p[1] ~= "TOPRIGHT" then return nil end
    local ox, oy = p[4] or 0, p[5] or 0
    if c.__clampEnforce then
        local k = c.__clamp or { 0, 0, 0, 0 }
        ox, oy = math.min(ox, k[2]), math.min(oy, k[3])
    end
    return ox, oy, c.__scale
end
local function barEdges()
    local p = BagsBar.__points[1]
    if not p or p[1] ~= "TOPRIGHT" then return nil end
    local s = BagsBar.__scale
    local right, top = 1200 + (p[4] or 0) * s, 700 + (p[5] or 0) * s
    return right, top, top - 40 * s, right - BagsBar.__w * s
end
local function mapEdges(f)
    if f.__name == "UIParent" then return 1200, 700, 0, 0 end
    if f.__name == "BagsBar" then return barEdges() end
    for index, name in ipairs(BAG_ORDER) do
        if f.__name == name then
            local right, top, bottom = barEdges()
            if not right or not f.__shown then return nil end
            local x = right
            for _, other in ipairs(BAG_ORDER) do
                if other == name then break end
                if _G[other].__shown then x = x - 34 end
            end
            return x, top, bottom, x - 30
        end
    end
    local ox, oy, cs = clusterOffsets()
    if not ox then return nil end
    if f.__name == "MinimapCluster" then
        local right, top = 1200 + ox * cs, 700 + oy * cs
        return right, top, top - 190 * cs, right - 170 * cs
    end
    if f.__name ~= "Minimap" then return nil end
    -- the map hangs in the cluster by its container anchor (Blizzard: TOP 10 / -30, i.e. 12 units from the right edge and 30 from the
    -- top; header underneath: BOTTOM 10 / 30); the flush of fold8.26 moves the container, so the rectangle follows it
    local cp = MinimapCluster.MinimapContainer and MinimapCluster.MinimapContainer.__points[1]
    local cx, cy = cp and cp[4] or 10, cp and cp[5] or -30
    local rightIn = 12 - (cx - 10)
    local topIn = (cp and cp[1] == "BOTTOM") and (190 - 140 - cy) or -cy
    local right = 1200 + ox * cs - rightIn * cs
    local top = 700 + oy * cs - topIn * cs
    return right, top, top - f.__h * cs, right - f.__w * cs
end
function M.GetRight(f) local r = mapEdges(f); if r then return r / f:GetEffectiveScale() end end
function M.GetTop(f) local _, t = mapEdges(f); if t then return t / f:GetEffectiveScale() end end
function M.GetBottom(f) local _, _, b = mapEdges(f); if b then return b / f:GetEffectiveScale() end return 0 end
function M.GetLeft(f) local _, _, _, l = mapEdges(f); if l then return l / f:GetEffectiveScale() end return 0 end
function M.ScrollToBottom() end
function M.SetMaskTexture(f, t) f.__mask = t end
function M.GetClampRectInsets(f) local c = f.__clamp or { 0, 0, 0, 0 }; return c[1], c[2], c[3], c[4] end
function M.SetClampRectInsets(f, l, r, t, b) f.__clamp = { l, r, t, b }; f.__clampCalls = (f.__clampCalls or 0) + 1 end
function M.GetRegions(f) return unpack(f.__regions or {}) end
function M.GetZoom(f) return f.__zoom or 0 end
function M.SetZoom(f, z) f.__zoom = z end
function M.GetZoomLevels(f) return 6 end
function M.GetFrameLevel(f) return f.__level or 1 end

function newFrame(kind, name, parent, template)
    local f = { __kind = kind, __name = name, __parent = parent, __alpha = 1, __scale = 1, __w = 100, __h = 20, __shown = true,
        __points = {}, __attrs = {}, __scripts = {}, __events = {}, __mouse = true }
    if template and template:find("SecureActionButtonTemplate") then f.__secure = true; f.__protected = true end
    -- methods start with a capital letter; unknown lower case fields are nil like on real frames
    setmetatable(f, { __index = function(t, k) if M[k] then return M[k] end if type(k) == "string" and k:match("^%u") then return function() end end end })
    if name then _G[name] = f; registry[name] = f end
    return f
end
function CreateFrame(kind, name, parent, template) return newFrame(kind, name, parent or UIParent, template) end

function hooksecurefunc(target, method, fn)
    if type(target) == "string" then
        fn = method -- hooksecurefunc("GlobalName", hook)
        local orig = _G[target]
        _G[target] = function(...) local r = { orig(...) }; fn(...); return unpack(r) end
    else
        local orig = target[method]
        rawset(target, method, function(...) local r = { orig(...) }; fn(...); return unpack(r) end)
    end
end

UIParent = newFrame("Frame", "UIParent"); UIParent.__w = 1200; UIParent.__h = 700
function GetScreenWidth() return 1200 end
function GetLocale() return "deDE" end
function GetTime() return clock end
function InCombatLockdown() return combat end
C_Timer = { After = function(sec, fn) timers[#timers + 1] = { at = clock + sec, fn = fn } end }
function runTimers(advance)
    clock = clock + (advance or 5)
    local guard = 0
    while true do
        local due = {}
        local rest = {}
        for _, t in ipairs(timers) do if t.at <= clock then due[#due + 1] = t else rest[#rest + 1] = t end end
        timers = rest
        if #due == 0 then break end
        for _, t in ipairs(due) do t.fn() end
        guard = guard + 1
        if guard > 50 then error("timer loop") end
    end
end
function ReloadUI() reloadCalls = reloadCalls + 1 end
function print(...) local parts = {}; for i = 1, select("#", ...) do parts[#parts + 1] = tostring((select(i, ...))) end printed[#printed + 1] = table.concat(parts, " ") end
SlashCmdList = {}
UIErrorsFrame = newFrame("Frame", "UIErrorsFrame"); function UIErrorsFrame.AddMessage(self, m) printed[#printed + 1] = "[notice] " .. m end
function strtrim(s) return (s:gsub("^%s+", ""):gsub("%s+$", "")) end
function tinsert(t, v) table.insert(t, v) end
UISpecialFrames = {}
NUM_CHAT_WINDOWS = 10
bindingKeys, bindingOwner, bindCalls, allBindCalls = {}, {}, {}, {}
function GetBindingKey(command) local k = bindingKeys[command] or {}; return k[1], k[2] end
function GetBindingAction(key) return bindingOwner[key] or "" end
function SetBinding(key, command)
    bindCalls[#bindCalls + 1] = key .. ">" .. command
    allBindCalls[#allBindCalls + 1] = key .. ">" .. command
    bindingOwner[key] = command
    bindingKeys[command] = bindingKeys[command] or {}
    table.insert(bindingKeys[command], key)
    return true
end
overrideBindings = {}
function SetOverrideBindingClick(owner, priority, key, button, mouseButton)
    overrideBindings[#overrideBindings + 1] = key .. ">" .. button .. ":" .. mouseButton
end
function GetCursorInfo() return nil end
EventRegistry = { RegisterCallback = function(self, name, fn) EventRegistry[name] = fn end }
function FCF_FadeInChatFrame(chat) end
function FCF_FadeOutChatFrame(chat) end
function FCF_SavePositionAndDimensions() end
function FCF_SetChatWindowFontSize(_, frame, size) frame.__fontSize = size end
function FCF_SetWindowAlpha() end
function ToggleChannelFrame() channelsToggled = (channelsToggled or 0) + 1 end
function ChatFrame_OpenChat() end

-- WoW frames of retail 12.x that the interface part touches
local manager = newFrame("Frame", "StatusTrackingBarManager", UIParent); manager.__w = 571; manager.__h = 34
manager.__points = { { n = 1, "BOTTOM" } }
for _, name in ipairs({ "MainStatusTrackingBarContainer", "SecondaryStatusTrackingBarContainer" }) do
    local c = newFrame("Frame", name, manager); c.__w = 571; c.__h = 17
    c.__points = { { n = 1, "BOTTOM" } }
end
MinimapCluster = newFrame("Frame", "MinimapCluster", UIParent)
MinimapCluster.__points = { { n = 5, "TOPRIGHT", UIParent, "TOPRIGHT", -10, -8 } } -- Edit Mode default place: a gap of 22 / 38 units to the corner
Minimap = newFrame("Frame", "Minimap", MinimapCluster); Minimap.__w = 140; Minimap.__h = 140
Minimap.__mask = "round-default"
-- header of the cluster (Blizzard_Minimap, 12.x): children with their XML anchors; SetHeaderUnderneath lays them out like the game does
MinimapCompassTexture = newFrame("Frame", "MinimapCompassTexture", Minimap)
local function clusterChild(key, name, point, rel, relPoint, x, y)
    local f = newFrame("Frame", name, MinimapCluster)
    f.__points = { { n = 5, point, rel, relPoint, x, y } }
    MinimapCluster[key] = f
    return f
end
local container = clusterChild("MinimapContainer", nil, "TOP", MinimapCluster, "TOP", 10, -30)
local borderTop = clusterChild("BorderTop", nil, "TOP", MinimapCluster, "TOP", 15, -4)
clusterChild("ZoneTextButton", nil, "LEFT", borderTop, "LEFT", 4, 0)
local tracking = clusterChild("Tracking", nil, "RIGHT", borderTop, "LEFT", -2, 0)
clusterChild("IndicatorFrame", nil, "TOPRIGHT", tracking, "BOTTOMRIGHT", 0, 0)
clusterChild("InstanceDifficulty", nil, "TOPRIGHT", borderTop, "TOPRIGHT", 0, -15)
GameTimeFrame = newFrame("Button", "GameTimeFrame", MinimapCluster); GameTimeFrame.__points = { { n = 5, "TOPLEFT", borderTop, "TOPRIGHT", 1, 0 } }
TimeManagerClockButton = newFrame("Button", "TimeManagerClockButton", UIParent); TimeManagerClockButton.__points = { { n = 5, "BOTTOMRIGHT", MinimapCluster, "BOTTOMRIGHT", -5, 10 } }
function MinimapCluster.SetHeaderUnderneath(self, under)
    local function put(f, ...) f:ClearAllPoints(); f:SetPoint(...) end
    if under then
        put(self.MinimapContainer, "BOTTOM", self, "BOTTOM", 10, 30)
        put(self.BorderTop, "BOTTOM", self, "BOTTOM", 15, 2)
        put(self.InstanceDifficulty, "BOTTOMRIGHT", self.BorderTop, "TOPRIGHT", -2, -2)
        put(self.IndicatorFrame, "BOTTOMRIGHT", self.Tracking, "TOPRIGHT")
    else
        put(self.MinimapContainer, "TOP", self, "TOP", 10, -30)
        put(self.BorderTop, "TOP", self, "TOP", 15, -4)
        put(self.InstanceDifficulty, "TOPRIGHT", self.BorderTop, "TOPRIGHT", 0, -15)
        put(self.IndicatorFrame, "TOPRIGHT", self.Tracking, "BOTTOMRIGHT", 0, 0)
    end
end
function MiniMapIndicatorFrame_UpdatePosition()
    MinimapCluster.IndicatorFrame:ClearAllPoints()
    MinimapCluster.IndicatorFrame:SetPoint("TOPRIGHT", MinimapCluster.BorderTop, "TOPLEFT", -1, -1)
end
-- Backgrounds in the header as on the device: a texture of the cluster itself and one in the zone button; Blizzard's zoom buttons
cluster_region = newFrame("Texture", nil, MinimapCluster); MinimapCluster.__regions = { cluster_region }
zone_bar = newFrame("Texture", nil, MinimapCluster.ZoneTextButton); MinimapCluster.ZoneTextButton.__regions = { zone_bar }
Minimap.ZoomIn = newFrame("Button", nil, Minimap); Minimap.ZoomOut = newFrame("Button", nil, Minimap)
Minimap.ZoomIn.__shown = false; Minimap.ZoomOut.__shown = false
function GetMinimapZoneText() return "Coldridge Valley" end
function GetPhysicalScreenSize() return 1382, 1042 end
function GetScreenHeight() return 700 end
-- World map as the Fold shows it: sized for a wide screen (1500 units wide, the screen is 1200), anchored TOPLEFT like a UI panel
WorldMapFrame = newFrame("Frame", "WorldMapFrame", UIParent); WorldMapFrame.__w = 1500; WorldMapFrame.__h = 560
WorldMapFrame.__points = { { n = 5, "TOPLEFT", UIParent, "TOPLEFT", 30, -70 } }
WorldMapFrame.__shown = false
MicroMenuContainer = newFrame("Frame", "MicroMenuContainer", UIParent)
BagsBar = newFrame("Frame", "BagsBar", UIParent)
-- bag buttons of the bar; bag contents are set by the tests (bagFamilies: bag ID -> family of the bag, 0 = a normal bag)
MainMenuBarBackpackButton = newFrame("Button", "MainMenuBarBackpackButton", BagsBar)
for i = 0, 3 do newFrame("Button", "CharacterBag" .. i .. "Slot", BagsBar) end
CharacterReagentBag0Slot = newFrame("Button", "CharacterReagentBag0Slot", BagsBar)
BagBarExpandToggle = newFrame("Button", "BagBarExpandToggle", BagsBar)
bagFamilies = { [1] = 0, [2] = 0, [3] = 0, [4] = 0 }
bagLayouts = 0
BagsBar.__w = 7 * 34 - 4; BagsBar.__h = 40
BagsBar.__points = { { n = 5, "TOPRIGHT", UIParent, "TOPRIGHT", -3, -178 } } -- directly below Blizzard's map (lower edge 522), 3 units from the screen edge
bagLayoutBroken = false -- true: the layout runs but leaves the width of the whole bar (what the device showed in fold8.17)
function BagsBar.Layout(f)
    bagLayouts = bagLayouts + 1
    if bagLayoutBroken then return end
    local n = 0
    for _, name in ipairs(BAG_ORDER) do if _G[name].__shown then n = n + 1 end end
    f.__w = math.max(1, n * 34 - 4)
end
C_Container = { GetContainerNumFreeSlots = function(id) return 10, bagFamilies[id] end }
GeneralDockManager = newFrame("Frame", "GeneralDockManager", UIParent)
ObjectiveTrackerFrame = newFrame("Frame", "ObjectiveTrackerFrame", UIParent)
for i = 1, 10 do
    local chat = newFrame("Frame", "ChatFrame" .. i, UIParent); chat.__w = 430; chat.__h = 120
    chat.__points = { { n = 3, "BOTTOMLEFT", UIParent, "BOTTOMLEFT", 32, 95 } }
    chat.__clamp = { -35, 35, 26, -50 } -- FloatingChatFrame: room of the button column on the left
    newFrame("Button", "ChatFrame" .. i .. "Tab", UIParent)
    newFrame("Frame", "ChatFrame" .. i .. "ButtonFrame", chat)
end
newFrame("Button", "ChatFrameMenuButton", ChatFrame1ButtonFrame)
newFrame("Button", "ChatFrameChannelButton", ChatFrame1ButtonFrame)
newFrame("Button", "QuickJoinToastButton", UIParent)
newFrame("Frame", "ChatFrameToggleVoiceDeafenButton", UIParent)

TouchForeverDB = nil
nsTable = {}

function dispatch(event, ...)
    for _, f in ipairs(extraFrames or {}) do
        if f.__events[event] and f.__scripts.OnEvent then f.__scripts.OnEvent(f, event, ...) end
    end
end

-- frames made without a name are not in the registry: keep every frame that registers events
local rawNew = newFrame
extraFrames = {}
function newFrame(kind, name, parent, template)
    local f = rawNew(kind, name, parent, template)
    extraFrames[#extraFrames + 1] = f
    return f
end

setmetatable(_G, { __index = function(_, key) undefined[key] = true; return nil end })

function loadAddonFile(source, chunkName) return assert(load(source, chunkName, "t"))("TouchForever", nsTable) end
"""


def main():
    lua = LuaRuntime(unpack_returned_tuples=True)
    lua.execute(MOCK)
    G = lua.globals()
    failures = []
    count = [0]

    def check(name, condition, detail=""):
        count[0] += 1
        if not condition:
            failures.append("%s %s" % (name, detail))

    def run(code):
        return lua.execute(code)

    for name in FILES:
        source = (ADDON / name).read_text(encoding="utf-8")
        check("no CR in " + name, "\r" not in source)
        try:
            lua.eval("function(src, n) return assert(load(src, n, 't')) end")(source, name)
        except Exception as error:  # noqa: BLE001
            failures.append("compile %s: %s" % (name, error))
            print("\n".join(failures))
            return 1
    print("compile ok:", ", ".join(FILES))

    # ---- old saved variables of addon 1.5.0 --------------------------------------------------------------------------
    run("""TouchForeverDB = { settings = { button = true }, ui = { chat = { enabled = true, size = 10, lines = 8, bg = 20,
        filter = true, short = true, stamps = false, mode = "normal" }, uiVersion = 2, questsScale = 70, barsHidden = true } }""")
    for name in FILES[:6]:  # the cooldown strip has its own check (cooldown_strip_check.py)
        source = (ADDON / name).read_text(encoding="utf-8")
        G.loadAddonFile(source, "@" + name)
    run('dispatch("ADDON_LOADED", "TouchForever")')
    ui = run("return TouchForeverDB.ui")
    chat = ui["chat"]
    check("old DB: chat compact default on", chat["compact"] is True)
    check("old DB: chat extra default 36", chat["extra"] == 36)
    check("old DB: tabs on touch default on", chat["tabsTouch"] is True)
    check("old DB: xp mode compact", ui["xpMode"] == "compact")
    check("old DB: xp width 40", ui["xpWidth"] == 40)
    check("old DB: menu bar shown", ui["menuHidden"] is False)
    check("old DB: bag bar gets the preset 'pack' (backpack + ammo bag)", ui["bagsMode"] == "pack" and ui["bagsHidden"] is None)
    check("old DB: minimap corner on", ui["minimapCorner"] is True)
    check("old DB: chat dock and map fit default on (no saved value = on)", chat["dockLeft"] is None and ui["mapFit"] is None)
    check("old DB: minimap round", ui["minimapShape"] == "round")
    check("old DB: own values stay", chat["size"] == 10 and chat["lines"] == 8 and ui["barsHidden"] is True)

    # an explicit "off" of the compact chat stays
    run("""TouchForeverDB = { settings = {}, ui = { chat = { enabled = true, compact = false, extra = 12, tabsTouch = false },
        xpMode = "standard", xpWidth = 70, menuHidden = true } }""")
    run('dispatch("ADDON_LOADED", "TouchForever")')
    ui2 = run("return TouchForeverDB.ui")
    check("explicit compact=false stays", ui2["chat"]["compact"] is False and ui2["chat"]["extra"] == 12)
    check("explicit xp mode stays", ui2["xpMode"] == "standard" and ui2["xpWidth"] == 70 and ui2["menuHidden"] is True)
    run("""TouchForeverDB = { settings = {}, ui = { bagsHidden = false, mapFit = false, chat = { enabled = true, dockLeft = false } } }""")
    run('dispatch("ADDON_LOADED", "TouchForever")')
    ui3 = run("return TouchForeverDB.ui")
    check("old fold8.16 value bagsHidden=false -> preset 'pack'", ui3["bagsMode"] == "pack" and ui3["bagsHidden"] is None and run("return TouchForeverUI.GetBagsHidden()") is False)
    check("explicit mapFit=false stays", ui3["mapFit"] is False and run("return TouchForeverUI.GetMapFit()") is False)
    check("explicit dockLeft=false stays", ui3["chat"]["dockLeft"] is False)
    run("""TouchForeverDB = { settings = {}, ui = { bagsMode = "full", minimapCorner = false } }""")
    run('dispatch("ADDON_LOADED", "TouchForever")')
    ui3b = run("return TouchForeverDB.ui")
    check("explicit bagsMode=full and minimapCorner=false stay", ui3b["bagsMode"] == "full" and ui3b["minimapCorner"] is False
          and run("return TouchForeverUI.GetBagsMode()") == "full" and run("return TouchForeverUI.GetMinimapCorner()") is False)
    run("""TouchForeverDB = { settings = {}, ui = { bagsMode = "nonsense" } }""")
    run('dispatch("ADDON_LOADED", "TouchForever")')
    check("unknown bagsMode -> 'pack'", run("return TouchForeverDB.ui.bagsMode") == "pack")

    # ---- fresh character ---------------------------------------------------------------------------------------------------
    run("TouchForeverDB = nil")
    run('dispatch("ADDON_LOADED", "TouchForever")')
    check("fresh DB has ui", run("return TouchForeverDB.ui.chat.compact") is True)
    run('dispatch("PLAYER_LOGIN")')
    run("runTimers(5)")

    UI = run("return TouchForeverUI")

    def scale(name):
        return run("return %s.__scale" % name)

    def points(name):
        return run("local p = %s.__points; local out = {}; for i, a in ipairs(p) do out[i] = a[1] .. ':' .. tostring(a[2] and (a[2].__name or '?') or '-') .. ':' .. tostring(a[3]) .. ':' .. tostring(a[4]) .. ':' .. tostring(a[5]) end return table.concat(out, '|')" % name)

    def ui_state(key):
        return run("return TouchForeverDB.ui.%s" % key)

    MAIN = "MainStatusTrackingBarContainer"
    SECOND = "SecondaryStatusTrackingBarContainer"

    # ---- status bars -------------------------------------------------------------------------------------------------------
    check("xp compact: 40 percent of 1200 = 480 wide", abs(scale(MAIN) * 571 - 480) < 0.5, str(scale(MAIN)))
    check("xp compact: anchors untouched", points(MAIN) == "BOTTOM:-:nil:nil:nil", points(MAIN))
    run('TouchForeverUI.SetXpWidth(60)')
    check("xp width 60 percent", abs(scale(MAIN) * 571 - 720) < 0.5)
    run('TouchForeverUI.SetXpMode("minimap")')
    wide = 140 * run("return Minimap:GetEffectiveScale()") # the minimap corner makes the map bigger, the bar follows
    check("xp minimap: as wide as the map (140 * its scale)", abs(scale(MAIN) * 571 - wide) < 0.5, "%s %s" % (scale(MAIN), wide))
    check("xp minimap: main below the map", points(MAIN).startswith("TOP:Minimap:BOTTOM:0:-"), points(MAIN))
    check("xp minimap: second below main", points(SECOND).startswith("TOP:" + MAIN + ":BOTTOM:0:-"), points(SECOND))
    run('TouchForeverUI.SetXpMode("standard")')
    check("xp standard: scale back", scale(MAIN) == 1 and scale(SECOND) == 1)
    check("xp standard: anchors back", points(MAIN) == "BOTTOM:-:nil:nil:nil", points(MAIN))
    run('TouchForeverUI.SetXpMode("off")')
    check("xp off: manager hidden", run("return StatusTrackingBarManager.__shown") is False)
    run('TouchForeverUI.SetXpMode("compact")')
    check("xp compact again: manager shown", run("return StatusTrackingBarManager.__shown") is True)
    # Blizzard sets scale / anchors again (layout update): ours come back after the delay
    run("MainStatusTrackingBarContainer:SetScale(1.2); MainStatusTrackingBarContainer:ClearAllPoints(); MainStatusTrackingBarContainer:SetPoint('BOTTOM', UIParent, 'BOTTOM', 0, 40)")
    run("runTimers(1)")
    check("xp: ours again after Blizzard's change", abs(scale(MAIN) * 571 - 720) < 0.5, str(scale(MAIN)))
    run('TouchForeverUI.SetXpMode("standard")')
    check("xp standard: Blizzard's last values", abs(scale(MAIN) - 1.2) < 1e-6 and points(MAIN) == "BOTTOM:UIParent:BOTTOM:0:40", points(MAIN))
    run('TouchForeverUI.SetXpMode("compact"); TouchForeverUI.SetXpWidth(40); combat = true; TouchForeverUI.SetXpWidth(80)')
    check("xp: nothing is changed in combat", abs(scale(MAIN) * 571 - 480) < 0.5)
    run("combat = false; dispatch('PLAYER_REGEN_ENABLED')")
    check("xp: caught up after combat", abs(scale(MAIN) * 571 - 960) < 0.5, str(scale(MAIN)))
    run("EventRegistry['EditMode.Enter']()")
    check("xp: standard while edit mode is open", scale(MAIN) == 1.2 or abs(scale(MAIN) - 1.2) < 1e-6, str(scale(MAIN)))
    run("EventRegistry['EditMode.Exit'](); runTimers(3)")
    check("xp: ours after edit mode", abs(scale(MAIN) * 571 - 960) < 0.5, str(scale(MAIN)))
    run('TouchForeverUI.SetXpWidth(40)')

    # ---- menu bar -----------------------------------------------------------------------------------------------------------
    check("menu bar shown at start", run("return MicroMenuContainer.__shown") is True)
    BACKPACK = "MainMenuBarBackpackButton"
    SLOTS = ["CharacterBag0Slot", "CharacterBag1Slot", "CharacterBag2Slot", "CharacterBag3Slot"]
    OTHERS = ["CharacterReagentBag0Slot", "BagBarExpandToggle"]

    def shown(name):
        return run("return %s.__shown" % name) is True

    check("bag bar: the preset is 'pack', the bar itself is shown", run("return BagsBar.__shown") is True and ui_state("bagsMode") == "pack")
    check("bag bar pack: backpack stays, no ammo bag = all bag slots, reagent bag and expand arrow hidden",
          shown(BACKPACK) and not any(shown(n) for n in SLOTS + OTHERS), str([shown(n) for n in [BACKPACK] + SLOTS + OTHERS]))
    check("bag bar pack: the bar was laid out again", run("return bagLayouts") > 0)
    run("TouchForeverUI.SetMenuHidden(true)")
    check("menu bar hidden: bag bar hidden with it, the backpack is not touched", run("return (not BagsBar.__shown) and (not MicroMenuContainer.__shown)") is True and shown(BACKPACK))
    run("MicroMenuContainer:Show()")
    check("menu bar stays hidden when Blizzard shows it", run("return not MicroMenuContainer.__shown") is True)
    run("TouchForeverUI.PeekAll()")
    check("peek shows the menu bar", run("return MicroMenuContainer.__shown") is True)
    run("runTimers(20)")
    check("menu bar hidden again after the peek", run("return not MicroMenuContainer.__shown") is True)
    run("TouchForeverUI.ToggleMenuBar()")
    check("toggle shows the menu bar and the bag bar again (pack)", run("return MicroMenuContainer.__shown and BagsBar.__shown") is True and not shown(SLOTS[0]))

    # ---- bag bar: three steps, backpack + ammo bag ---------------------------------------------------------------------------
    run("CharacterBag1Slot:Show()")
    check("bag bar pack: a bag button that Blizzard shows again is hidden again", not shown("CharacterBag1Slot"))
    run("BagsBar:Show()")
    check("bag bar pack: the bar itself stays shown", run("return BagsBar.__shown") is True)
    check("bag bar option: a choice on page 2, reads 'pack'", run("return nsTable.options.byId.bagbar.get()") == "pack"
          and run("return nsTable.options.byId.bagbar.kind") == "choice" and run("return nsTable.options.byId.bagbar.page") == 2
          and run("return nsTable.options.byId.bagbar.default") == "pack")
    # a hunter: quiver (family 1) in bag slot 3, a normal bag in slot 1
    run("bagFamilies[3] = 1; dispatch('BAG_UPDATE_DELAYED'); runTimers(1)")
    check("bag bar pack: quiver in slot 3 is shown, the other bag slots stay hidden",
          shown("CharacterBag2Slot") and not any(shown(n) for n in ["CharacterBag0Slot", "CharacterBag1Slot", "CharacterBag3Slot"] + OTHERS) and shown(BACKPACK),
          str([shown(n) for n in SLOTS]))
    run("bagFamilies[4] = 2; dispatch('PLAYER_EQUIPMENT_CHANGED'); runTimers(1)")
    check("bag bar pack: quiver and ammo pouch both shown", shown("CharacterBag2Slot") and shown("CharacterBag3Slot") and not shown("CharacterBag0Slot"))
    run("bagFamilies[4] = 0; bagFamilies[3] = 0; dispatch('BAG_CONTAINER_UPDATE'); runTimers(1)")
    check("bag bar pack: the quiver is taken out of the slot, its button goes", not shown("CharacterBag2Slot") and not shown("CharacterBag3Slot") and shown(BACKPACK))
    run("bagFamilies[3] = 1; dispatch('BAG_UPDATE_DELAYED'); runTimers(1)")
    run("CharacterBag0Slot:Show(); CharacterBag2Slot:Show()")
    check("bag bar pack: Blizzard shows buttons again, only the quiver remains", shown("CharacterBag2Slot") and not shown("CharacterBag0Slot"))
    # fallback: the free slots call says nothing about families, the item in the slot does (item family, then item class)
    run("""
        C_Container.GetContainerNumFreeSlots = function() return 10 end
        C_Container.ContainerIDToInventoryID = function(id) return 30 + id end
        itemLinks = { [34] = "quiver-link" }
        function GetInventoryItemLink(unit, slot) return itemLinks[slot] end
        itemFamilies = { ["quiver-link"] = 1 }
        function GetItemFamily(link) return itemFamilies[link] end
    """)
    run("dispatch('BAG_UPDATE_DELAYED'); runTimers(1)")
    check("bag bar pack, fallback by item family: bag slot 4 holds a quiver", shown("CharacterBag3Slot") and not shown("CharacterBag2Slot"))
    run("""itemFamilies = {}; C_Item = { GetItemInfoInstant = function(link) if link == "quiver-link" then return 1, "Quiver", "Quiver", "", 0, 11, 2 end end }""")
    run("dispatch('BAG_UPDATE_DELAYED'); runTimers(1)")
    check("bag bar pack, fallback by item class (quiver): bag slot 4", shown("CharacterBag3Slot") and not shown("CharacterBag0Slot"))
    run("itemLinks = {}; dispatch('BAG_UPDATE_DELAYED'); runTimers(1)")
    check("bag bar pack, no quiver found: only the backpack", shown(BACKPACK) and not any(shown(n) for n in SLOTS))
    run("""
        C_Container.GetContainerNumFreeSlots = function(id) return 10, bagFamilies[id] end
        C_Container.ContainerIDToInventoryID = nil; GetInventoryItemLink = nil; GetItemFamily = nil; C_Item = nil
        bagFamilies = { [1] = 0, [2] = 0, [3] = 0, [4] = 0 }
    """)
    run("bagFamilies[2] = 1; dispatch('BAG_UPDATE_DELAYED'); runTimers(1)")
    check("bag bar pack: the family call alone is enough (slot 2)", shown("CharacterBag1Slot") and not shown("CharacterBag2Slot"))
    # the three steps
    run("nsTable.options.byId.bagbar.set('full')")
    check("bag bar full: every button back, Blizzard's bar", run("return BagsBar.__shown") is True and all(shown(n) for n in [BACKPACK] + SLOTS + OTHERS)
          and ui_state("bagsMode") == "full", str([shown(n) for n in [BACKPACK] + SLOTS + OTHERS]))
    run("CharacterBag0Slot:Show()")
    check("bag bar full: nothing is hidden by us any more", shown("CharacterBag0Slot"))
    run("nsTable.options.byId.bagbar.set('off')")
    check("bag bar off: the bar is hidden", run("return not BagsBar.__shown") is True and ui_state("bagsMode") == "off"
          and run("return TouchForeverUI.GetBagsHidden()") is True)
    run("BagsBar:Show()")
    check("bag bar off: stays hidden when Blizzard shows it", run("return not BagsBar.__shown") is True)
    run("TouchForeverUI.PeekKind('menu'); runTimers(1)")
    check("bag bar off: peek shows the whole bar for a moment", run("return BagsBar.__shown") is True and all(shown(n) for n in [BACKPACK] + SLOTS + OTHERS))
    run("runTimers(20)")
    check("bag bar off: hidden again after the peek", run("return not BagsBar.__shown") is True)
    run("EventRegistry['EditMode.Enter']()")
    check("bag bar off: shown while Edit Mode is open", run("return BagsBar.__shown") is True)
    run("EventRegistry['EditMode.Exit'](); runTimers(3)")
    check("bag bar off: hidden again after Edit Mode", run("return not BagsBar.__shown") is True)
    run("SlashCmdList['TOUCHFOREVER']('bags pack')")
    check("/tf bags pack", run("return BagsBar.__shown") is True and shown(BACKPACK) and not shown("CharacterBag0Slot") and shown("CharacterBag1Slot")
          and ui_state("bagsMode") == "pack")
    run("EventRegistry['EditMode.Enter']()")
    check("bag bar pack: all buttons while Edit Mode is open", all(shown(n) for n in [BACKPACK] + SLOTS + OTHERS))
    run("EventRegistry['EditMode.Exit'](); runTimers(3)")
    check("bag bar pack: reduced again after Edit Mode", shown(BACKPACK) and shown("CharacterBag1Slot") and not shown("CharacterBag0Slot"))
    run("SlashCmdList['TOUCHFOREVER']('bags full')")
    check("/tf bags full", ui_state("bagsMode") == "full" and shown("CharacterBag0Slot"))
    run("SlashCmdList['TOUCHFOREVER']('bags hide')")
    check("/tf bags hide", run("return not BagsBar.__shown") is True and ui_state("bagsMode") == "off")
    run("SlashCmdList['TOUCHFOREVER']('bags show')")
    check("/tf bags show = whole bar", run("return BagsBar.__shown") is True and ui_state("bagsMode") == "full")
    run("SlashCmdList['TOUCHFOREVER']('bags')")
    check("/tf bags alone toggles off", ui_state("bagsMode") == "off")
    run("SlashCmdList['TOUCHFOREVER']('bags')")
    check("/tf bags alone toggles back to pack", ui_state("bagsMode") == "pack" and run("return BagsBar.__shown") is True)
    run("TouchForeverUI.SetBagsHidden(true)")
    check("old API SetBagsHidden(true) = off, (false) = whole bar", ui_state("bagsMode") == "off")
    run("TouchForeverUI.SetBagsHidden(false)")
    check("old API SetBagsHidden(false) = whole bar", ui_state("bagsMode") == "full")
    run("TouchForeverUI.SetBagsMode('pack'); bagFamilies = { [1] = 0, [2] = 0, [3] = 0, [4] = 0 }; dispatch('BAG_UPDATE_DELAYED'); runTimers(1)")
    check("bag bar: back to the preset", ui_state("bagsMode") == "pack" and not shown("CharacterBag1Slot") and shown(BACKPACK))
    run("TouchForeverUI.SetBagsMode('nonsense')")
    check("bag bar: unknown step = 'pack'", ui_state("bagsMode") == "pack")

    # ---- minimap shape ------------------------------------------------------------------------------------------------------
    def cpoints(path):
        return run("local p = %s.__points; local out = {}; for i, a in ipairs(p) do out[i] = tostring(a[1]) .. ':' .. tostring(a[2] and (a[2].__name or '?') or '-') .. ':' .. tostring(a[3]) .. ':' .. string.format('%%g', a[4] or 0) .. ':' .. string.format('%%g', a[5] or 0) end return table.concat(out, '|')" % path)

    # the square map is moved inside the cluster until it sits flush top right (fold8.26): container 10 / -30 + header shift 26 + the rest 12 / 4 -> 22 / 0
    SQ_FLUSH = "TOP:MinimapCluster:TOP:22:0"
    SQ_FLUSH_UNDER = "BOTTOM:MinimapCluster:BOTTOM:22:50"  # header underneath: 30 - 26 + the 46 units between the map top and the cluster top
    SQUARE = "Interface\\BUTTONS\\WHITE8X8"
    ROUND = "Textures\\MinimapMask"
    check("minimap: round is the preset and untouched", run("return TouchForeverDB.ui.minimapShape") == "round" and run("return Minimap.__mask") == "round-default")
    check("minimap: GetMinimapShape ROUND", run("return GetMinimapShape()") == "ROUND")
    check("minimap: ring and header visible", run("return MinimapCompassTexture.__alpha == 1 and MinimapCluster.BorderTop.__alpha == 1") is True)
    orig = {k: cpoints("MinimapCluster." + k) for k in ["MinimapContainer", "BorderTop", "ZoneTextButton", "Tracking", "IndicatorFrame", "InstanceDifficulty"]}
    orig["GameTimeFrame"] = cpoints("GameTimeFrame")
    orig["TimeManagerClockButton"] = cpoints("TimeManagerClockButton")
    run("TouchForeverUI.SetMinimapShape('square')")
    check("minimap square: mask", run("return Minimap.__mask") == SQUARE, str(run("return Minimap.__mask")))
    check("minimap square: GetMinimapShape SQUARE", run("return GetMinimapShape()") == "SQUARE")
    check("minimap square: ring and header strip invisible", run("return MinimapCompassTexture.__alpha == 0 and MinimapCluster.BorderTop.__alpha == 0") is True)
    check("minimap square: map moved up into the strip", cpoints("MinimapCluster.MinimapContainer") == SQ_FLUSH, cpoints("MinimapCluster.MinimapContainer"))
    check("minimap square: zone name is a text of our own, top left inside the map",
          cpoints("TouchForeverZoneLabel") == "TOPLEFT:Minimap:TOPLEFT:4:-3" and run("return TouchForeverZoneLabel.__shown") is True, cpoints("TouchForeverZoneLabel"))
    check("minimap square: Blizzard's zone button (with its bar) invisible and click-through",
          run("return MinimapCluster.ZoneTextButton.__alpha") == 0 and run("return MinimapCluster.ZoneTextButton.__mouse") is False)
    check("minimap square: header textures invisible", run("return cluster_region.__alpha") == 0)
    check("minimap square: tracking top right, mail below", cpoints("MinimapCluster.Tracking").startswith("TOPRIGHT:Minimap:TOPRIGHT") and cpoints("MinimapCluster.IndicatorFrame").startswith("TOPRIGHT:Minimap:TOPRIGHT"))
    check("minimap square: difficulty and clock bottom left (bottom right is for the zoom buttons), calendar top left",
          cpoints("MinimapCluster.InstanceDifficulty").startswith("BOTTOMLEFT:Minimap:BOTTOMLEFT")
          and cpoints("TimeManagerClockButton").startswith("BOTTOMLEFT:Minimap:BOTTOMLEFT:") and cpoints("GameTimeFrame").startswith("TOPLEFT:Minimap:TOPLEFT"))
    inside = lambda path: cpoints(path).split(":")[1] == "Minimap"
    check("minimap square: every corner item is anchored to the map (nothing hangs over the rim)",
          all(inside(x) for x in ["GameTimeFrame", "MinimapCluster.Tracking", "MinimapCluster.IndicatorFrame", "MinimapCluster.InstanceDifficulty", "TimeManagerClockButton", "TouchForeverZoneLabel"]))
    # Blizzard moves the calendar and the tracking button back to the header (zone change, mail ...): they are put on the corners again
    run("GameTimeFrame:ClearAllPoints(); GameTimeFrame:SetPoint('TOPLEFT', MinimapCluster.BorderTop, 'TOPRIGHT', 1, 0)")
    run("MinimapCluster.Tracking:ClearAllPoints(); MinimapCluster.Tracking:SetPoint('RIGHT', MinimapCluster.BorderTop, 'LEFT', -2, 0)")
    check("minimap square: Blizzard's move is seen at once, ours comes back a moment later",
          not inside("GameTimeFrame") and not inside("MinimapCluster.Tracking"))
    run("runTimers(1)")
    check("minimap square: calendar and tracking back on the corners",
          cpoints("GameTimeFrame").startswith("TOPLEFT:Minimap:TOPLEFT") and cpoints("MinimapCluster.Tracking").startswith("TOPRIGHT:Minimap:TOPRIGHT"),
          cpoints("GameTimeFrame") + " " + cpoints("MinimapCluster.Tracking"))
    check("minimap square: own thin border, shown", run("return TouchForeverMinimapBorder ~= nil and TouchForeverMinimapBorder.__shown") is True)
    # running it again changes nothing (no drift of the offsets)
    run("TouchForeverUI.SetMinimapShape('square'); dispatch('PLAYER_ENTERING_WORLD')")
    check("minimap square: idempotent", cpoints("MinimapCluster.MinimapContainer") == SQ_FLUSH)
    # Blizzard lays the header out again (Edit Mode: header underneath): the map keeps hugging the edge, ours stay
    run("MinimapCluster:SetHeaderUnderneath(true)")
    check("minimap square: header underneath, map moved down to the edge", cpoints("MinimapCluster.MinimapContainer") == SQ_FLUSH_UNDER, cpoints("MinimapCluster.MinimapContainer"))
    check("minimap square: header strip stays invisible", run("return MinimapCluster.BorderTop.__alpha") == 0)
    run("MinimapCluster:SetHeaderUnderneath(false)")
    check("minimap square: header on top again", cpoints("MinimapCluster.MinimapContainer") == SQ_FLUSH, cpoints("MinimapCluster.MinimapContainer"))
    run("MiniMapIndicatorFrame_UpdatePosition()")
    check("minimap square: mail icon back on the corner after Blizzard moved it", cpoints("MinimapCluster.IndicatorFrame").startswith("TOPRIGHT:Minimap:TOPRIGHT"), cpoints("MinimapCluster.IndicatorFrame"))
    # back to round without a reload
    run("TouchForeverUI.SetMinimapShape('round')")
    check("minimap round again: mask", run("return Minimap.__mask") == ROUND, str(run("return Minimap.__mask")))
    check("minimap round again: GetMinimapShape ROUND", run("return GetMinimapShape()") == "ROUND")
    check("minimap round again: ring and header back", run("return MinimapCompassTexture.__alpha == 1 and MinimapCluster.BorderTop.__alpha == 1") is True)
    for key in ["MinimapContainer", "BorderTop", "ZoneTextButton", "Tracking", "InstanceDifficulty"]:
        check("minimap round again: %s anchors back" % key, cpoints("MinimapCluster." + key) == orig[key], cpoints("MinimapCluster." + key) + " != " + orig[key])
    check("minimap round again: mail icon has Blizzard's latest anchor", cpoints("MinimapCluster.IndicatorFrame").endswith(":TOPLEFT:-1:-1"), cpoints("MinimapCluster.IndicatorFrame"))
    check("minimap round again: calendar and clock anchors back", cpoints("GameTimeFrame") == orig["GameTimeFrame"] and cpoints("TimeManagerClockButton") == orig["TimeManagerClockButton"])
    check("minimap round again: border hidden", run("return TouchForeverMinimapBorder.__shown") is False)
    check("minimap round again: zone button and header textures as before, own zone text hidden",
          run("return MinimapCluster.ZoneTextButton.__alpha") == 1 and run("return MinimapCluster.ZoneTextButton.__mouse") is True
          and run("return cluster_region.__alpha") == 1 and run("return TouchForeverZoneLabel.__shown") is False)
    # not in combat
    run("combat = true; TouchForeverUI.SetMinimapShape('square')")
    check("minimap: nothing changes in combat", run("return Minimap.__mask") == ROUND and run("return MinimapCompassTexture.__alpha") == 1)
    run("combat = false; dispatch('PLAYER_REGEN_ENABLED')")
    check("minimap: square after the fight", run("return Minimap.__mask") == SQUARE and cpoints("MinimapCluster.MinimapContainer") == SQ_FLUSH)
    run("combat = true; TouchForeverUI.SetMinimapShape('round')")
    check("minimap: round also waits for the fight to end", run("return Minimap.__mask") == SQUARE)
    run("combat = false; dispatch('PLAYER_REGEN_ENABLED')")
    check("minimap: round after the fight", run("return Minimap.__mask") == ROUND and cpoints("MinimapCluster.MinimapContainer") == orig["MinimapContainer"])
    # hidden minimap and shape are independent
    run("TouchForeverUI.SetMinimapShape('square'); TouchForeverUI.SetMinimapHidden(true)")
    check("minimap: hidden + square", run("return Minimap.__mask") == SQUARE and run("return MinimapCluster.__shown") is False)
    run("TouchForeverUI.SetMinimapHidden(false); TouchForeverUI.SetMinimapShape('round')")
    # slash commands
    run("SlashCmdList['TOUCHFOREVER']('minimap square')")
    check("minimap: /tf minimap square", run("return Minimap.__mask") == SQUARE)
    run("SlashCmdList['TOUCHFOREVER']('minimap rund')")
    check("minimap: /tf minimap rund", run("return Minimap.__mask") == ROUND)
    # ---- big zoom buttons ---------------------------------------------------------------------------------------------------
    check("zoom: own buttons exist, 40 pixels, one unit = one pixel of the game picture (1382 x 1042)",
          run("return TouchForeverZoomIn.__w") == 40 and run("return TouchForeverZoomOut.__w") == 40
          and abs(run("return TouchForeverZoomIn.__scale") - 768 / 1042) < 1e-9)
    check("zoom: bottom right inside the map, minus left of plus",
          cpoints("TouchForeverZoomIn").startswith("BOTTOMRIGHT:Minimap:BOTTOMRIGHT") and cpoints("TouchForeverZoomOut").startswith("BOTTOMRIGHT:TouchForeverZoomIn:BOTTOMLEFT")
          and run("return TouchForeverZoomIn.__shown") is True)
    check("zoom: Blizzard's small buttons invisible and click-through",
          run("return Minimap.ZoomIn.__alpha") == 0 and run("return Minimap.ZoomIn.__mouse") is False and run("return Minimap.ZoomOut.__alpha") == 0)
    run("TouchForeverZoomIn.__scripts.OnClick(); TouchForeverZoomIn.__scripts.OnClick()")
    check("zoom: + zooms in", run("return Minimap.__zoom") == 2)
    run("for i = 1, 9 do TouchForeverZoomIn.__scripts.OnClick() end")
    check("zoom: stops at the last level", run("return Minimap.__zoom") == 5)
    run("TouchForeverZoomOut.__scripts.OnClick()")
    check("zoom: - zooms out", run("return Minimap.__zoom") == 4)
    run("for i = 1, 9 do TouchForeverZoomOut.__scripts.OnClick() end")
    check("zoom: stops at 0", run("return Minimap.__zoom") == 0)
    run("TouchForeverUI.SetMinimapShape('square')")
    check("zoom: also in square mode, inside the border", cpoints("TouchForeverZoomIn").startswith("BOTTOMRIGHT:Minimap:BOTTOMRIGHT:-3:3"), cpoints("TouchForeverZoomIn"))
    run("TouchForeverUI.SetMinimapShape('round')")
    run("SlashCmdList['TOUCHFOREVER']('minimap zoom small')")
    check("zoom: off = ours hidden, Blizzard's back",
          run("return TouchForeverZoomIn.__shown") is False and run("return Minimap.ZoomIn.__alpha") == 1 and run("return Minimap.ZoomIn.__mouse") is True
          and run("return TouchForeverUI.GetZoomBig()") is False)
    run("SlashCmdList['TOUCHFOREVER']('minimap zoom big')")
    check("zoom: on again", run("return TouchForeverZoomIn.__shown") is True and run("return Minimap.ZoomIn.__alpha") == 0)
    check("zoom: option zoombig", run("return nsTable.options.byId.zoombig.get()") is True)
    run("SlashCmdList['TOUCHFOREVER']('minimap info')")
    check("minimap info lists the cluster", any("cluster.ZoneTextButton" in x for x in run("return printed").values()))

    # a square start: saved choice is applied at login
    run("TouchForeverDB.ui.minimapShape = 'square'; dispatch('PLAYER_ENTERING_WORLD')")
    check("minimap: saved square is applied on entering the world", run("return Minimap.__mask") == SQUARE)
    run("TouchForeverUI.SetMinimapShape('round')")

    # ---- minimap at the corner (fold8.18: one nudge, no scaling, no hook on the position; Edit Mode is left alone) ----------------
    # mock: UIParent 1200 x 700; the cluster (170 x 190) sits TOPRIGHT at (-10, -8); the map (140 units) hangs 12 / 30 units inside the
    # cluster corner, so Blizzard's gap is 22 to the right and 38 to the top, the lower edge of the map at 522. The bag bar sits right
    # below it (TOPRIGHT -3 / -178, 7 buttons). The mock engine keeps the cluster inside the screen by its clamp insets (clampEnforce).
    def edge(which):
        return run("local s = Minimap:GetEffectiveScale(); return Minimap:Get%s() * s" % which)

    def bar_edge(which):
        return run("return BagsBar:Get%s() * BagsBar:GetEffectiveScale()" % which)

    def cluster_at():
        return run("local p = MinimapCluster.__points[1]; return p[1] .. ':' .. p[2].__name .. ':' .. p[3] .. ':' .. string.format('%g:%g', p[4], p[5]) .. '/' .. #MinimapCluster.__points")

    def bar_at():
        return run("local p = BagsBar.__points[1]; return p[1] .. ':' .. p[2].__name .. ':' .. p[3] .. ':' .. string.format('%.1f:%.1f', p[4], p[5]) .. '/' .. #BagsBar.__points")

    def blizzard_cluster(x, y):
        run("MinimapCluster:SetScale(1); MinimapCluster:ClearAllPoints(); MinimapCluster:SetPoint('TOPRIGHT', UIParent, 'TOPRIGHT', %s, %s)" % (x, y))

    def blizzard_bar(x, y):
        run("BagsBar:ClearAllPoints(); BagsBar:SetPoint('TOPRIGHT', UIParent, 'TOPRIGHT', %s, %s)" % (x, y))

    def corner_state(right, top, bottom, detail):
        ok = abs(edge("Right") - right) < 0.05 and abs(edge("Top") - top) < 0.05 and abs(edge("Bottom") - bottom) < 0.05
        return ok, "%s: right %.2f top %.2f bottom %.2f" % (detail, edge("Right"), edge("Top"), edge("Bottom"))

    BLIZZ_CLUSTER = "TOPRIGHT:UIParent:TOPRIGHT:-10:-8/1"
    BLIZZ_BAR = "TOPRIGHT:UIParent:TOPRIGHT:-3.0:-178.0/1"
    NUDGED = "TOPRIGHT:UIParent:TOPRIGHT:10:28/1"   # 20 right, 36 up: the gap 22 / 38 becomes 2 / 2
    # a clean start: option off (Blizzard's place), the engine clamps like the game, Blizzard's bag bar place, xp mode "compact"
    run('TouchForeverUI.SetXpMode("compact"); TouchForeverUI.SetMinimapCorner(false); runTimers(3)')
    blizzard_cluster(-10, -8)
    blizzard_bar(-3, -178)
    run("BagsBar.__shown = true; BagsBar.__w = 30; MinimapCluster.__clampEnforce = true; MinimapCluster.__clamp = { 0, 0, 0, 0 }")
    check("minimap corner: without the option the clamp keeps the map 12 / 30 units inside (engine mock)", abs(edge("Right") - 1178) < 0.05 and abs(edge("Top") - 662) < 0.05)

    run("TouchForeverUI.SetMinimapCorner(true); runTimers(3)")
    check("minimap corner: on by default", run("return TouchForeverUI.GetMinimapCorner()") is True and ui_state("minimapCorner") is True)
    ok, detail = corner_state(1198, 698, 558, "default")
    check("minimap corner: 2 units from the top right corner (the clamp insets are opened, else the engine holds it back)", ok, detail)
    check("minimap corner: the cluster is moved only, not scaled (nothing below is covered)", run("return MinimapCluster.__scale") == 1 and cluster_at() == NUDGED, cluster_at())
    clamp = list(run("local c = MinimapCluster.__clamp; return {c[1], c[2], c[3], c[4]}").values())
    check("minimap corner: clamp insets opened by the margin around the map (right 13, top 31), left / bottom untouched", clamp == [0, 13, 31, 0], str(clamp))
    check("minimap corner: no hook on the position - a SetPoint by Blizzard stays (nothing pushes it back)",
          run("MinimapCluster:ClearAllPoints(); MinimapCluster:SetPoint('TOPRIGHT', UIParent, 'TOPRIGHT', -30, -8); runTimers(3); return MinimapCluster.__points[1][4]") == -30)
    blizzard_cluster(-10, -8)
    blizzard_bar(-3, -178)
    run("TouchForeverUI.SetMinimapCorner(true); TouchForeverUI.SetMinimapCorner(true); runTimers(2)")
    ok, detail = corner_state(1198, 698, 558, "twice")
    check("minimap corner: idempotent (again from Blizzard's place, no creeping)", ok and cluster_at() == NUDGED, detail + " " + cluster_at())
    run("TouchForeverUI.SetMinimapShape('square')")
    ok, detail = corner_state(1198, 698, 558, "square")
    check("minimap corner: also with the square map", ok, detail)
    run("TouchForeverUI.SetMinimapShape('round')")
    ok, detail = corner_state(1198, 698, 558, "round")
    check("minimap corner: and back to round", ok, detail)
    run("SlashCmdList['TOUCHFOREVER']('minimap info')")
    printed = list(run("return printed").values())
    check("minimap info: says the corner state and lists the bag buttons", any("corner=on (placed true)" in x for x in printed) and any("CharacterBag0Slot" in x for x in printed))
    check("minimap info: rectangles, gap to the screen edge, clamp insets, anchors, bag bar width, last nudge",
          all(any(key in x for x in printed) for key in ["cluster rect:", "map rect:", "gap map-screen: right 2.0 top 2.0", "clamp insets L0 R13 T31 B0",
                                                         "cluster anchors:", "BagsBar anchors:", "BagsBar width=", "last nudge:"]), str(printed)[-900:])

    # the bag bar: right aligned right under the map, as long as its visible buttons
    check("bag bar: follows the map (right edge = the map's right edge 1198, 2 units below its lower edge 558)", abs(bar_edge("Right") - 1198) < 0.05 and abs(bar_edge("Top") - 556) < 0.05,
          "%.1f %.1f" % (bar_edge("Right"), bar_edge("Top")))
    run("BagsBar.__w = 30")
    check("bag bar: only the backpack is shown (pack) and the bar is 30 wide = one button", run("return BagsBar.__w") == 30)
    run("bagLayoutBroken = true; BagsBar.__w = 234; BagsBar:Layout(); runTimers(1)")
    check("bag bar: layout of the game leaves the width of the whole bar -> set to the visible buttons (30)", run("return BagsBar.__w") == 30, str(run("return BagsBar.__w")))
    run("bagLayoutBroken = false")
    run("TouchForeverUI.SetBagsMode('full'); runTimers(1)")
    check("bag bar: 'full' shows every button and the game's layout sets its width again", run("return BagsBar.__w") == 7 * 34 - 4 and run("return BagsBar.__shown") is True, str(run("return BagsBar.__w")))
    run("TouchForeverUI.SetBagsMode('pack'); runTimers(1)")
    check("bag bar: back to 'pack' = short again", run("return BagsBar.__w") == 30, str(run("return BagsBar.__w")))
    # two buttons (ammo bag) = two buttons wide
    run("bagFamilies[2] = 2; bagLayoutBroken = true; BagsBar.__w = 234; TouchForeverUI.SetBagsMode('full'); TouchForeverUI.SetBagsMode('pack'); bagLayoutBroken = true; BagsBar.__w = 234; BagsBar:Layout(); runTimers(1)")
    check("bag bar: backpack + ammo bag = two buttons wide (64), also after a broken layout", run("return BagsBar.__w") == 64, str(run("return BagsBar.__w")))
    run("bagLayoutBroken = false; bagFamilies[2] = 0; TouchForeverUI.SetBagsMode('full'); TouchForeverUI.SetBagsMode('pack'); runTimers(1)")

    # status bars "at the minimap": the bag bar is below them, the map stays as it is
    run('TouchForeverUI.SetXpMode("minimap"); runTimers(3)')
    drop = 2 + 3 + 1 + 2 * 17 * scale(MAIN)
    check("bag bar: below the status bars of the mode 'at the minimap' (not over them)", abs(bar_edge("Top") - (558 - drop)) < 0.2 and abs(bar_edge("Right") - 1198) < 0.05,
          "%.2f vs %.2f" % (bar_edge("Top"), 558 - drop))
    check("minimap corner: the status bars at the minimap are as wide as the map (not scaled)", abs(scale(MAIN) * 571 - 140) < 0.5, str(scale(MAIN)))
    run('TouchForeverUI.SetXpMode("compact"); runTimers(3)')
    check("bag bar: back to 2 units below the map with the other status bar modes", abs(bar_edge("Top") - 556) < 0.05, "%.2f" % bar_edge("Top"))

    # Edit Mode: nothing is moved, no jump; the clamp is open to drag the map to the corner; afterwards the nudge is done again
    before_cluster, before_bar = cluster_at(), bar_at()
    run("EventRegistry['EditMode.Enter'](); runTimers(2)")
    check("minimap corner: Edit Mode opens without a jump (cluster and bag bar stay where they are)", cluster_at() == before_cluster and bar_at() == before_bar, cluster_at() + " " + bar_at())
    run("MinimapCluster:SetClampRectInsets(0, 0, 0, 0); runTimers(1)")
    clamp = list(run("local c = MinimapCluster.__clamp; return {c[1], c[2], c[3], c[4]}").values())
    check("minimap corner: the clamp insets Blizzard sets again are opened again (one nudge, not a loop)", clamp == [0, 13, 31, 0], str(clamp))
    run("EventRegistry['EditMode.Exit'](); runTimers(3)")
    ok, detail = corner_state(1198, 698, 558, "after Edit Mode")
    check("minimap corner: after Edit Mode the same place (idempotent)", ok and cluster_at() == NUDGED, detail)
    # the player drags the map toward the corner in Edit Mode (Blizzard's place + 6 / 5 units from the corner), saves
    run("EventRegistry['EditMode.Enter']()")
    run("MinimapCluster:ClearAllPoints(); MinimapCluster:SetPoint('TOPRIGHT', UIParent, 'TOPRIGHT', 0, 0); runTimers(1)")
    check("minimap corner: in Edit Mode the player's drag stays (nothing pushes it back)", cluster_at() == "TOPRIGHT:UIParent:TOPRIGHT:0:0/1", cluster_at())
    run("EventRegistry['EditMode.Exit'](); runTimers(3)")
    ok, detail = corner_state(1198, 698, 558, "dragged")
    check("minimap corner: dragged near the corner, after Edit Mode the map is 2 units from the corner", ok, detail)
    # layout update of the game (login, layout switch): Blizzard's place again, the bag bar too
    blizzard_cluster(-20, -8)
    blizzard_bar(-3, -178)
    run("dispatch('EDIT_MODE_LAYOUTS_UPDATED'); runTimers(2)")
    ok, detail = corner_state(1198, 698, 558, "layout update")
    check("minimap corner: Blizzard lays the cluster out again (layout update), the nudge is done again", ok, detail)
    check("bag bar: and follows the map again", abs(bar_edge("Right") - 1198) < 0.05 and abs(bar_edge("Top") - 556) < 0.05, "%.1f %.1f" % (bar_edge("Right"), bar_edge("Top")))
    # fold8.26: at the game's own anchor the nudge is done whatever the distance (Fold: the map was 57 / 66 units from the corner, the old
    # limit of 50 made the nudge skip itself)
    blizzard_cluster(-45, -55)
    blizzard_bar(-3, -178)
    run("printed = {}; dispatch('EDIT_MODE_LAYOUTS_UPDATED'); runTimers(2); runTimers(2)")
    ok, detail = corner_state(1198, 698, 558, "far, game anchor")
    check("minimap corner: the cluster at the game's anchor is nudged also when the map is 57 / 85 units from the corner", ok, detail)
    run("SlashCmdList['TOUCHFOREVER']('minimap info')")
    printed = list(run("return printed").values())
    check("minimap info: says why it pushed (game anchor) and the gap after the nudge",
          any("corner decision: pushed: the cluster is at the game's anchor" in x and "gap after: right 2.0 top 2.0" in x for x in printed), str([x for x in printed if "decision" in x]))
    blizzard_cluster(-70, -8)  # a place of the player: anchor with offsets above 60 and the map 82 units away
    run("printed = {}; dispatch('EDIT_MODE_LAYOUTS_UPDATED'); runTimers(2); SlashCmdList['TOUCHFOREVER']('minimap info')")
    printed = list(run("return printed").values())
    check("minimap corner: an own anchor with a large gap is left alone, the info says why",
          cluster_at() == "TOPRIGHT:UIParent:TOPRIGHT:-70:-8/1" and any("corner decision: not pushed: own place" in x for x in printed), cluster_at())
    # fold8.26: the square map sits flush top right in its cluster, so the Edit Mode box in the corner means the map in the corner
    blizzard_cluster(-10, -8)
    blizzard_bar(-3, -178)
    run("TouchForeverUI.SetMinimapShape('square'); runTimers(3)")
    check("minimap square: the map sits flush top right in the cluster (container moved by 12 / 4 units)",
          cpoints("MinimapCluster.MinimapContainer") == SQ_FLUSH and abs(run("return Minimap:GetRight() * Minimap:GetEffectiveScale() - MinimapCluster:GetRight() * MinimapCluster:GetEffectiveScale()")) < 0.05
          and abs(run("return Minimap:GetTop() * Minimap:GetEffectiveScale() - MinimapCluster:GetTop() * MinimapCluster:GetEffectiveScale()")) < 0.05)
    ok, detail = corner_state(1198, 698, 558, "square flush")
    check("minimap square: 2 units from the corner", ok, detail)
    run("printed = {}; SlashCmdList['TOUCHFOREVER']('minimap info')")
    printed = list(run("return printed").values())
    check("minimap info: says how far the map was moved inside the cluster", any("map in cluster: moved by x 12.0 y 4.0" in x and "rest right 0.0 top 0.0" in x for x in printed), str(printed)[-700:])
    run("EventRegistry['EditMode.Enter']()")
    run("MinimapCluster:ClearAllPoints(); MinimapCluster:SetPoint('TOPRIGHT', UIParent, 'TOPRIGHT', 0, 0); runTimers(2)")
    ok, detail = corner_state(1200, 700, 560, "square, in Edit Mode")
    check("minimap square: dragging the Edit Mode box into the corner puts the map in the corner (no rest between box and map)", ok, detail)
    run("EventRegistry['EditMode.Exit'](); runTimers(3)")
    ok, detail = corner_state(1200, 700, 560, "square, after Edit Mode")
    check("minimap square: after Edit Mode the map in the corner stays (0 to 4 units is not pushed away)", ok and cluster_at() == "TOPRIGHT:UIParent:TOPRIGHT:0:0/1", detail + " " + cluster_at())
    # save + reload / layout switch: Blizzard puts the container and the cluster on its own anchors, the flush and the nudge come back
    run("MinimapCluster.MinimapContainer:ClearAllPoints(); MinimapCluster.MinimapContainer:SetPoint('TOP', MinimapCluster, 'TOP', 10, -30)")
    run("dispatch('EDIT_MODE_LAYOUTS_UPDATED'); runTimers(3)")
    ok, detail = corner_state(1200, 700, 560, "square, layout update")
    check("minimap square: Blizzard lays the container out again (save / reload): flush again", ok and cpoints("MinimapCluster.MinimapContainer") == SQ_FLUSH, detail + " " + cpoints("MinimapCluster.MinimapContainer"))
    blizzard_cluster(-10, -8)
    blizzard_bar(-3, -178)
    run("dispatch('PLAYER_ENTERING_WORLD'); runTimers(3)")
    ok, detail = corner_state(1198, 698, 558, "square, entering the world")
    check("minimap square: login / zone change / reload: the cluster at the game's anchor goes to 2 units", ok, detail)
    run("TouchForeverUI.SetMinimapShape('round'); runTimers(3)")
    ok, detail = corner_state(1198, 698, 558, "round again")
    check("minimap square -> round: the container is back at Blizzard's anchor and the map 2 units from the corner", ok and cpoints("MinimapCluster.MinimapContainer") == orig["MinimapContainer"], detail)
    blizzard_cluster(-10, -8)
    blizzard_bar(-3, -178)
    run("dispatch('EDIT_MODE_LAYOUTS_UPDATED'); runTimers(2)")
    # an own place far from the corner stays; a bag bar of its own place stays
    blizzard_cluster(-400, -300)
    blizzard_bar(-3, -178)
    run("dispatch('EDIT_MODE_LAYOUTS_UPDATED'); runTimers(2)")
    check("minimap corner: a place far from the corner (own Edit Mode position) is left alone, the bag bar too", cluster_at() == "TOPRIGHT:UIParent:TOPRIGHT:-400:-300/1" and bar_at() == BLIZZ_BAR, cluster_at() + " " + bar_at())
    blizzard_cluster(-10, -8)
    blizzard_bar(-3, -600)  # far below the map: an own place
    run("dispatch('EDIT_MODE_LAYOUTS_UPDATED'); runTimers(2)")
    check("bag bar: an own place far below the map stays where it is, the map is nudged", cluster_at() == NUDGED and bar_at() == "TOPRIGHT:UIParent:TOPRIGHT:-3.0:-600.0/1", cluster_at() + " " + bar_at())
    # off: Blizzard's anchors and clamp are back
    blizzard_cluster(-10, -8)
    blizzard_bar(-3, -178)
    run("TouchForeverUI.SetMinimapCorner(false); TouchForeverUI.SetMinimapCorner(true); runTimers(2)")
    ok, detail = corner_state(1198, 698, 558, "again")
    check("minimap corner: back to the normal place", ok, detail)
    run("TouchForeverUI.SetMinimapCorner(false); runTimers(2)")
    clamp = list(run("local c = MinimapCluster.__clamp; return {c[1], c[2], c[3], c[4]}").values())
    check("minimap corner off: Blizzard's place, the bag bar's place and the clamp insets are back", cluster_at() == BLIZZ_CLUSTER and bar_at() == BLIZZ_BAR and clamp == [0, 0, 0, 0] and run("return MinimapCluster.__scale") == 1,
          cluster_at() + " " + bar_at() + " " + str(clamp))
    check("minimap corner off: option reads it", run("return nsTable.options.byId.minimapcorner.get()") is False and run("return nsTable.options.byId.minimapcorner.page") == 2)
    # the player's own place (changed after our nudge) is not overwritten when switching off
    run("TouchForeverUI.SetMinimapCorner(true); runTimers(2)")
    blizzard_cluster(-77, -55)
    run("TouchForeverUI.SetMinimapCorner(false); runTimers(2)")
    check("minimap corner off: a place that is not ours is not put back", cluster_at() == "TOPRIGHT:UIParent:TOPRIGHT:-77:-55/1", cluster_at())
    blizzard_cluster(-10, -8)
    # combat: nothing is moved, the fight's end catches up
    run("TouchForeverUI.SetMinimapCorner(true); runTimers(2); combat = true; TouchForeverUI.SetMinimapCorner(false)")
    check("minimap corner: nothing changes in combat", cluster_at() == NUDGED, cluster_at())
    run("combat = false; dispatch('PLAYER_REGEN_ENABLED'); runTimers(1)")
    check("minimap corner: switched off after the fight", cluster_at() == BLIZZ_CLUSTER and run("return MinimapCluster.__scale") == 1, cluster_at())
    run("combat = true; SlashCmdList['TOUCHFOREVER']('minimap corner on')")
    check("minimap corner: /tf minimap corner on in combat waits", cluster_at() == BLIZZ_CLUSTER)
    run("combat = false; dispatch('PLAYER_REGEN_ENABLED'); runTimers(2)")
    ok, detail = corner_state(1198, 698, 558, "after the fight")
    check("minimap corner: on after the fight", ok, detail)
    run("SlashCmdList['TOUCHFOREVER']('minimap corner off')")
    check("minimap corner: /tf minimap corner off", cluster_at() == BLIZZ_CLUSTER and run("return TouchForeverUI.GetMinimapCorner()") is False)
    run("SlashCmdList['TOUCHFOREVER']('minimap rand')")
    check("minimap corner: /tf minimap rand toggles", run("return TouchForeverUI.GetMinimapCorner()") is True and cluster_at() == NUDGED, cluster_at())
    # a login with the saved value off leaves the game's map untouched
    run("TouchForeverUI.SetMinimapCorner(false); runTimers(1)")
    run("TouchForeverDB.ui.minimapCorner = false; dispatch('PLAYER_ENTERING_WORLD'); runTimers(2)")
    check("minimap corner: saved 'off' at login keeps Blizzard's place", cluster_at() == BLIZZ_CLUSTER and bar_at() == BLIZZ_BAR)
    run("TouchForeverUI.SetMinimapCorner(true); runTimers(2)")
    run("MinimapCluster.__clampEnforce = nil")

    # ---- extra keys for the standard windows of the radial menu "WoW-Menü Rest" -----------------------------------------------
    calls = run("return table.concat(bindCalls, ',')").split(",")
    for pair in ["ALT-CTRL-F1>TOGGLECHARACTER2", "ALT-CTRL-F2>TOGGLECURRENCY", "ALT-CTRL-F3>TOGGLECHARACTER4", "ALT-CTRL-F4>TOGGLERAIDTAB",
                 "CTRL-SHIFT-F5>WFB_TOGGLEBARS"]:
        check("login sets " + pair, pair in calls, str(calls))
    run("bindCalls = {}; runTimers(5)")
    check("keys are set once only", run("return #bindCalls") == 0)
    # forced again (options page): already bound keys are not bound twice, a taken key counts as conflict, two own keys stay alone
    run("bindingKeys.TOGGLECURRENCY = nil; bindingOwner['ALT-CTRL-F2'] = 'SOMETHINGELSE'")
    run("bindingKeys.TOGGLERAIDTAB = { 'U', 'SHIFT-U' }; bindingOwner['ALT-CTRL-F4'] = nil")
    run("bindingKeys.TOGGLECHARACTER4 = { 'H' }; bindingOwner['ALT-CTRL-F3'] = nil")
    run("TouchForeverUI.SetDefaultKeys(true)")
    calls = run("return table.concat(bindCalls, ',')").split(",")
    check("forced: a free key is added next to the player's own key", "ALT-CTRL-F3>TOGGLECHARACTER4" in calls, str(calls))
    check("forced: the player's two own keys are left alone", not any(c.endswith(">TOGGLERAIDTAB") for c in calls), str(calls))
    check("forced: a key that is taken is not stolen", not any(c.endswith(">TOGGLECURRENCY") for c in calls), str(calls))
    check("forced: reputation already has its key", not any(c.endswith(">TOGGLECHARACTER2") for c in calls), str(calls))
    run("bindCalls = {}")

    # ---- compact chat -------------------------------------------------------------------------------------------------------
    run("runTimers(15)")
    side = ["ChatFrameMenuButton", "ChatFrameChannelButton", "QuickJoinToastButton", "ChatFrame1ButtonFrame", "ChatFrameToggleVoiceDeafenButton"]
    for name in side:
        check("compact chat: %s invisible" % name, run("return %s.__alpha" % name) == 0)
        check("compact chat: %s click-through" % name, run("return %s.__mouse" % name) is False)
    check("compact chat: chat visible", run("return ChatFrame1.__alpha") == 1)
    check("compact chat: extra width", run("return ChatFrame1.__w") == 430 + 36, str(run("return ChatFrame1.__w")))

    # the room of the button column on the left (clamp inset -35) is taken away: the chat window can be pushed to the screen edge
    def clamp(i):
        return list(run("local c = ChatFrame%d.__clamp; return {c[1], c[2], c[3], c[4]}" % i).values())
    check("compact chat: left clamp inset 0 on all windows, the other three untouched",
          all(clamp(i) == [0, 35, 26, -50] for i in range(1, 11)), str([clamp(i) for i in (1, 10)]))
    run("ChatFrame3.__clampCalls = 0; TouchForeverUI.ChatSet('extra', 36)")
    check("compact chat: a window that is already flush is not set again", run("return ChatFrame3.__clampCalls") == 0)
    run("ChatFrame1:SetClampRectInsets(-35, 35, 26, -50); EventRegistry['EditMode.Exit'](); runTimers(1)")
    check("compact chat: Blizzard sets the inset again (Edit Mode): flush again", clamp(1) == [0, 35, 26, -50], str(clamp(1)))
    run("combat = true; ChatFrame2:SetClampRectInsets(-35, 35, 26, -50); TouchForeverUI.ChatSet('extra', 36)")
    check("compact chat: nothing is changed in combat", clamp(2) == [-35, 35, 26, -50], str(clamp(2)))
    run("combat = false; dispatch('PLAYER_REGEN_ENABLED')")
    check("compact chat: caught up after the fight", clamp(2) == [0, 35, 26, -50], str(clamp(2)))
    check("compact chat: tabs hidden until touched", run("return ChatFrame1Tab.__alpha") == 0)
    run("FCF_FadeInChatFrame(ChatFrame1)")
    check("compact chat: tabs show on touch", run("return ChatFrame1Tab.__alpha") == 1 and run("return ChatFrame1Tab.__mouse") is True)
    run("FCF_FadeOutChatFrame(ChatFrame1)")
    check("compact chat: tabs hide again", run("return ChatFrame1Tab.__alpha") == 0)
    run("TouchForeverUI.ChatSet('extra', 60)")
    check("chat extra 60", run("return ChatFrame1.__w") == 490)
    # Edit mode sets the width again
    run("ChatFrame1:SetWidth(500); runTimers(1)")
    check("chat width follows Blizzard's new value", run("return ChatFrame1.__w") == 560, str(run("return ChatFrame1.__w")))
    run("TouchForeverUI.ChatSet('compact', false)")
    check("compact off: original width back", run("return ChatFrame1.__w") == 500, str(run("return ChatFrame1.__w")))
    for name in side:
        check("compact off: %s visible again" % name, run("return %s.__alpha" % name) == 1)
        check("compact off: %s clickable again" % name, run("return %s.__mouse" % name) is True)
    check("compact off: tabs visible", run("return ChatFrame1Tab.__alpha") == 1)
    check("compact off: Blizzard's clamp insets back on all windows", all(clamp(i) == [-35, 35, 26, -50] for i in range(1, 11)), str([clamp(i) for i in (1, 2)]))
    run("combat = true; TouchForeverUI.ChatSet('compact', true)")
    check("compact on in combat: clamp waits", clamp(1) == [-35, 35, 26, -50])
    run("combat = false; dispatch('PLAYER_REGEN_ENABLED')")
    check("compact on after the fight: flush again", clamp(1) == [0, 35, 26, -50], str(clamp(1)))
    run("TouchForeverUI.ChatSet('compact', false)")
    run("TouchForeverUI.ChatSet('compact', true); TouchForeverUI.SetChatHidden(true); runTimers(2)")
    check("chat hidden: windows invisible", run("return ChatFrame1.__alpha") == 0)
    run("TouchForeverUI.SetChatHidden(false)")
    check("chat shown again", run("return ChatFrame1.__alpha") == 1)

    # ---- chat flush to the left edge ----------------------------------------------------------------------------------------
    def chat_x():
        return run("return ChatFrame1.__points[1][4]")

    def chat_y():
        return run("return ChatFrame1.__points[1][5]")

    def put_chat(x, y=95):
        run("ChatFrame1:ClearAllPoints(); ChatFrame1:SetPoint('BOTTOMLEFT', UIParent, 'BOTTOMLEFT', %s, %s)" % (x, y))

    check("chat dock: the window that Blizzard put 32 units from the edge sits flush left (height kept)", chat_x() == 0 and chat_y() == 95, "%s %s" % (chat_x(), chat_y()))
    put_chat(36)
    run("EventRegistry['EditMode.Exit'](); runTimers(2)")
    check("chat dock: moved flush again after Edit Mode", chat_x() == 0 and chat_y() == 95, str(chat_x()))
    put_chat(120, 200)
    run("EventRegistry['EditMode.Exit'](); runTimers(2)")
    check("chat dock: a place chosen on purpose further in stays", chat_x() == 120 and chat_y() == 200, str(chat_x()))
    run("TouchForeverUI.ChatDockNow()")
    check("chat dock: the settings button moves it from anywhere", chat_x() == 0 and chat_y() == 200, str(chat_x()))
    put_chat(36)
    run("TouchForeverUI.ChatSet('dock', false)")
    check("chat dock: option off leaves it alone", chat_x() == 36, str(chat_x()))
    run("TouchForeverUI.ChatSet('dock', true)")
    check("chat dock: option on again moves it", chat_x() == 0, str(chat_x()))
    run("EventRegistry['EditMode.Enter']()")
    put_chat(36)
    run("TouchForeverUI.ChatSet('extra', 36)")
    check("chat dock: not while Edit Mode is open (the player is dragging)", chat_x() == 36, str(chat_x()))
    run("EventRegistry['EditMode.Exit'](); runTimers(2)")
    check("chat dock: after Edit Mode", chat_x() == 0, str(chat_x()))
    put_chat(36)
    run("combat = true; TouchForeverUI.ChatSet('extra', 36)")
    check("chat dock: nothing in combat", chat_x() == 36, str(chat_x()))
    run("combat = false; dispatch('PLAYER_REGEN_ENABLED')")
    check("chat dock: caught up after the fight", chat_x() == 0, str(chat_x()))
    # more than one anchor: not ours to shift
    run("ChatFrame1:SetPoint('TOPRIGHT', UIParent, 'TOPRIGHT', 0, 0)")
    run("ChatFrame1.__points[1][4] = 36; TouchForeverUI.ChatSet('extra', 36)")
    check("chat dock: two anchors are left alone", chat_x() == 36, str(chat_x()))
    put_chat(95)
    # Blizzard's insets come back without Edit Mode (layout change): the hook puts the left inset to 0 again
    run("ChatFrame1:SetClampRectInsets(-35, 35, 26, -50); runTimers(1)")
    check("chat clamp: the hook sets the left inset to 0 again", clamp(1) == [0, 35, 26, -50], str(clamp(1)))
    run("TouchForeverUI.ChatSet('compact', false)")
    put_chat(36)
    run("TouchForeverUI.ChatSet('extra', 36)")
    check("chat dock: only with the compact chat", chat_x() == 36, str(chat_x()))
    run("TouchForeverUI.ChatSet('compact', true)")
    check("chat dock: compact on again", chat_x() == 0, str(chat_x()))
    run("SlashCmdList['TOUCHFOREVER']('chat dock off')")
    check("chat dock: /tf chat dock off", run("return TouchForeverDB.ui.chat.dockLeft") is False)
    run("SlashCmdList['TOUCHFOREVER']('chat dock on')")
    check("chat dock options exist on page 4", run("return nsTable.options.byId.chatdock.page") == 4 and run("return nsTable.options.byId.chatleft.page") == 4)

    # ---- world map fits the screen ------------------------------------------------------------------------------------------
    def map_scale():
        return run("return WorldMapFrame.__scale")

    def map_right():
        return run("local l, b, w, h = WorldMapFrame:GetRect(); return (l + w) * WorldMapFrame.__scale")

    def map_left():
        return run("local l = WorldMapFrame:GetRect(); return l * WorldMapFrame.__scale")

    def map_shows(width=None, height=None):
        if width:
            run("WorldMapFrame.__w = %s; WorldMapFrame.__h = %s" % (width, height))
        run("WorldMapFrame.__shown = true; WorldMapFrame.__scripts.OnShow(WorldMapFrame); runTimers(1)")

    check("map: hooked (show and size change)", run("return WorldMapFrame.__scripts.OnShow ~= nil and WorldMapFrame.__scripts.OnSizeChanged ~= nil") is True)
    map_shows()
    check("map: 1500 units wide on a 1200 screen: scaled to fit (1192 / 1500)", abs(map_scale() - 1192 / 1500) < 0.001, str(map_scale()))
    check("map: right edge inside the screen (the close button is visible)", map_right() <= 1196 + 0.5, str(map_right()))
    check("map: left edge inside the screen", map_left() >= 4 - 0.5, str(map_left()))
    run("nsTable.options.byId.mapfit.set(false)")
    check("map: option off: size as in the game", map_scale() == 1 and run("return nsTable.options.byId.mapfit.get()") is False, str(map_scale()))
    run("nsTable.options.byId.mapfit.set(true)")
    check("map: option on again", abs(map_scale() - 1192 / 1500) < 0.001 and map_right() <= 1196.5, "%s %s" % (map_scale(), map_right()))
    run("WorldMapFrame:SetScale(0.9); runTimers(1)")
    check("map: Blizzard's own scale is the one to fit from (same result for a too wide map)", abs(map_scale() - 1192 / 1500) < 0.001, str(map_scale()))
    map_shows(1000, 500)
    check("map: a map that fits keeps Blizzard's scale (0.9)", abs(map_scale() - 0.9) < 0.001, str(map_scale()))
    map_shows(1000, 800)
    check("map: too high is scaled, too (692 / 720 of 0.9)", abs(map_scale() - 0.9 * 692 / 720) < 0.001, str(map_scale()))
    run("WorldMapFrame:SetScale(1); runTimers(1)")
    run("WorldMapFrame:SetSize(1500, 560); WorldMapFrame.__scripts.OnSizeChanged(WorldMapFrame); runTimers(1)")
    check("map: maximise (size changes): fitted again", abs(map_scale() - 1192 / 1500) < 0.001, str(map_scale()))
    run("combat = true; WorldMapFrame:SetSize(1400, 560); WorldMapFrame.__scripts.OnSizeChanged(WorldMapFrame); runTimers(1)")
    check("map: nothing is changed in combat", abs(map_scale() - 1192 / 1500) < 0.001, str(map_scale()))
    run("combat = false; dispatch('PLAYER_REGEN_ENABLED')")
    check("map: caught up after the fight (1192 / 1400)", abs(map_scale() - 1192 / 1400) < 0.001, str(map_scale()))
    run("SlashCmdList['TOUCHFOREVER']('map standard')")
    check("map: /tf map standard", map_scale() == 1 and run("return TouchForeverDB.ui.mapFit") is False)
    run("SlashCmdList['TOUCHFOREVER']('map fit')")
    check("map: /tf map fit", abs(map_scale() - 1192 / 1400) < 0.001 and run("return TouchForeverDB.ui.mapFit") is True, str(map_scale()))
    run("WorldMapFrame.__shown = false; WorldMapFrame:SetScale(1)")

    # ---- reload buttons -------------------------------------------------------------------------------------------------------
    run("TouchForeverUI.Open()")  # settings window
    window = run("return TouchForeverWindow")
    check("window exists", window is not None)
    run("SlashCmdList['TOUCHFOREVER']('reload')")
    holder = run("return TouchForeverReloadHolder")
    button = run("return TouchForeverReloadButton")
    check("reload offer: holder is a plain frame, the button inside is secure", holder is not None and button is not None and run("return TouchForeverReloadButton.__secure") is True)
    check("reload offer: macro /reload", run("return TouchForeverReloadButton.__attrs.type") == "macro" and run("return TouchForeverReloadButton.__attrs.macrotext") == "/reload")
    check("reload offer: clicks on release", list(run("return TouchForeverReloadButton.__clicks").values()) == ["AnyUp"])
    check("reload offer: shown", run("return TouchForeverReloadHolder.__shown") is True)
    check("ReloadUI is never called by addon code", run("return reloadCalls") == 0)
    # window row
    found = run("local n = 0; for _, f in ipairs(extraFrames) do if f.__secure and f.__attrs.macrotext == '/reload' then n = n + 1 end end return n")
    check("reload buttons: offer + window row + hidden key button, all armed", found == 3, str(found))
    check("reload key: hidden secure button TouchForeverReloadKey", run("return TouchForeverReloadKey.__secure") is True
          and run("return TouchForeverReloadKey.__attrs.macrotext") == "/reload")
    check("reload key: override binding CTRL-SHIFT-F4 clicks it", run("return overrideBindings[1]") == "CTRL-SHIFT-F4>TouchForeverReloadKey:LeftButton",
          str(run("return overrideBindings[1]")))
    # made in combat -> armed after it
    run("TouchForeverReloadHolder:Hide()")
    # combat: hide of the holder works, no error; the button cannot be created now but exists
    run("combat = true; TouchForeverReloadHolder:Hide(); TouchForeverUI.SetBarsHidden(true)")
    run("combat = false; dispatch('PLAYER_REGEN_ENABLED')")

    # ---- options ---------------------------------------------------------------------------------------------------------------
    run("ns_options = nsTable.options")
    listing = run("local out = {}; for i, o in ipairs(ns_options.list) do out[#out + 1] = o.id .. ':' .. o.page .. ':' .. o.kind end return table.concat(out, ',')")
    pages = {}
    for entry in listing.split(","):
        _id, page, _kind = entry.split(":")
        pages.setdefault(int(page), []).append(_id)
    print("options per page:", {k: len(v) for k, v in sorted(pages.items())})
    for new in ["xpmode", "xpwidth", "menubar", "minimapshape", "chatcompact", "chatextra", "chattabs", "zoombig", "minimapcorner", "bagbar", "questreset", "peek",
                "openchat", "chatstatus", "windows", "minimapinfo"]:
        check("option " + new, new in listing)
    for page, ids_on_page in pages.items():
        check("page %d fits the window (<= 10 rows)" % page, len(ids_on_page) <= 10, str(len(ids_on_page)))
    # every option can be changed and read back without an error, in and out of combat
    run("""for _, o in ipairs(ns_options.list) do
        if o.kind == 'check' or o.kind == 'choice' then ns_options.change(o.id, 1); ns_options.change(o.id, 1)
        elseif o.kind == 'slider' then ns_options.change(o.id, 1); ns_options.change(o.id, -1) end
    end""")
    run("ns_options.change('menubar', 1); ns_options.change('menubar', 1)")
    for choice in ["standard", "compact", "minimap", "off"]:
        run('ns_options.byId.xpmode.set("%s")' % choice)
        check("xpmode %s read back" % choice, run("return ns_options.byId.xpmode.get()") == choice)
    run('ns_options.byId.xpmode.set("compact")')
    run("ns_options.byId.minimapshape.set('square')")
    check("option minimapshape: read back square, mask square", run("return ns_options.byId.minimapshape.get()") == "square" and run("return Minimap.__mask") == SQUARE)
    run("ns_options.change('minimapshape', 1)")
    check("option minimapshape: cycles back to round", run("return ns_options.byId.minimapshape.get()") == "round" and run("return Minimap.__mask") == ROUND)
    run("ns_options.byId.minimapshape.set('square')")
    run("TouchForeverUI.ResetAll()")
    check("reset: minimap round again", run("return Minimap.__mask") == ROUND and run("return TouchForeverDB.ui.minimapShape") == "round")
    check("reset: defaults", run("return TouchForeverDB.ui.xpMode") == "compact" and run("return TouchForeverDB.ui.menuHidden") is False
          and run("return TouchForeverDB.ui.chat.compact") is True and run("return TouchForeverDB.ui.chat.extra") == 36)

    # ---- slash commands ----------------------------------------------------------------------------------------------------------
    run("SlashCmdList['TOUCHFOREVER']('xp minimap'); SlashCmdList['TOUCHFOREVER']('xp 55'); SlashCmdList['TOUCHFOREVER']('xp standard')")
    run("SlashCmdList['TOUCHFOREVER']('menubar hide'); SlashCmdList['TOUCHFOREVER']('menubar show')")
    run("SlashCmdList['TOUCHFOREVER']('chat compact off'); SlashCmdList['TOUCHFOREVER']('chat extra 24'); SlashCmdList['TOUCHFOREVER']('chat tabs off'); SlashCmdList['TOUCHFOREVER']('chat compact on')")
    run("SlashCmdList['TOUCHFOREVER']('ui')")
    run("TouchForeverUI.OpenChannels(); TouchForeverUI.OpenChatMenu()")
    check("channels binding calls the game function", run("return channelsToggled") == 1)
    check("chat menu binding clicks the menu button", run("return ChatFrameMenuButton.__clicked") == 1)

    # ---- every /tf command has its control in the settings window --------------------------------------------------------------
    # command (as typed after /tf) -> id of the option that does the same; None = the window / help page itself
    COMMAND_OPTIONS = [
        ("", None), ("options", None), ("ui", None), ("keys", None),
        ("bars hide", "bars"), ("bars show", "bars"), ("bars peek", "peek"),
        ("chat", "chatstatus"), ("chat show", "chatshown"), ("chat hide", "chatshown"), ("chat toggle", "chatshown"), ("chat peek", "peek"),
        ("chat phone", "chatphone"), ("chat reset", "chatphone"), ("chat mode compact", "chatmode"), ("chat mode normal", "chatmode"),
        ("chat size 12", "chatsize"), ("chat lines 8", "chatlines"), ("chat bg 20", "chatbg"), ("chat filter on", "chatfilter"),
        ("chat short on", "chatshort"), ("chat stamps off", "chatstamps"), ("chat compact on", "chatcompact"),
        ("chat extra 24", "chatextra"), ("chat tabs on", "chattabs"),
        ("minimap show", "minimap"), ("minimap hide", "minimap"), ("minimap peek", "peek"), ("minimap square", "minimapshape"),
        ("minimap round", "minimapshape"), ("minimap corner on", "minimapcorner"), ("minimap corner off", "minimapcorner"),
        ("bags full", "bagbar"), ("bags pack", "bagbar"), ("bags off", "bagbar"), ("bags hide", "bagbar"), ("bags show", "bagbar"),
        ("minimap zoom big", "zoombig"), ("minimap zoom small", "zoombig"), ("minimap info", "minimapinfo"),
        ("quests show", "quests"), ("quests hide", "quests"), ("quests peek", "peek"), ("quests size 70", "questsize"),
        ("quests height 300", "questheight"), ("quests reset", "questreset"),
        ("xp compact", "xpmode"), ("xp 40", "xpwidth"), ("xp peek", "peek"),
        ("menubar show", "menubar"), ("menubar hide", "menubar"), ("menubar peek", "peek"),
        ("peek", "peekall"), ("say", "openchat"), ("party", "openchat"), ("guild", "openchat"), ("whisper", "openchat"),
        ("layout info", "layout"), ("layout again", "layoutagain"), ("windows", "windows"), ("strip", "strip"), ("button", "reloadbutton"), ("reload", "reloadicons"),
    ]
    option_ids = {entry.split(":")[0] for entry in listing.split(",")}
    for command, option_id in COMMAND_OPTIONS:
        if option_id is not None:
            check("/tf %s has its control in the window (%s)" % (command, option_id), option_id in option_ids)
        if command not in ("windows", "strip", "reload", "button", "reset"):  # these need the cooldown file / are tested above
            try:
                run("SlashCmdList['TOUCHFOREVER']('%s')" % command)
            except Exception as error:  # noqa: BLE001
                check("/tf %s runs" % command, False, str(error))
    run("SlashCmdList['TOUCHFOREVER']('chat compact on'); SlashCmdList['TOUCHFOREVER']('chat reset'); TouchForeverUI.ChatPhone(true); TouchForeverUI.SetMinimapShape('round'); TouchForeverUI.SetZoomBig(true); TouchForeverUI.SetMinimapCorner(true); TouchForeverUI.SetBagsMode('pack')")
    check("settings window: seven pages with a tab and a heading each, help last",
          run("return #nsTable.options.pageTitles") == 7 and run("return #nsTable.options.pageHeadings") == 7)
    run("nsTable.options.open(7)")
    check("settings window: help page lists the windows command", "/tf windows" in run("return nsTable.options.helpShown()"))
    run("nsTable.options.byId.windows.run()")
    check("settings window: 'detected windows' answers (also without the cooldown part)", run("return nsTable.options.infoText(6)") not in (None, ""))
    run("nsTable.options.byId.minimapinfo.run()")
    check("settings window: minimap layout is shown under the rows", "MinimapCluster" in run("return nsTable.options.infoText(2)"))

    check("WFB_OPTIONS has a standard key: CTRL-SHIFT-F11", run("return TouchForeverUI.DEFAULT_KEYS.WFB_OPTIONS") == "CTRL-SHIFT-F11")
    check("WFB_OPTIONS is bound to the window at login", "CTRL-SHIFT-F11>WFB_OPTIONS" in run("return table.concat(allBindCalls, ',')"))
    check("WFB_OPTIONS toggles the window", run("nsTable.options.close(); TouchForeverUI.Options(); local a = TouchForeverWindow.__shown; TouchForeverUI.Options(); return a and not TouchForeverWindow.__shown") is True)

    # bindings
    xml = (ADDON / "Bindings.xml").read_text(encoding="utf-8")
    for fn in re.findall(r"TouchForeverUI\.(\w+)\(\)", xml):
        check("binding function " + fn, run("return type(TouchForeverUI.%s)" % fn) == "function")

    # ---- language: TouchForeverLocale.lua (App-set language, then the client locale, then English) --------------------------
    locale_src = (ADDON / "TouchForeverLocale.lua").read_text(encoding="utf-8")
    load_locale = lua.eval("""function(src, app, client)
        local ns = { appLanguage = app }
        local saved = GetLocale
        GetLocale = client and function() return client end or nil
        assert(load(src, "locale", "t"))("TouchForever", ns)
        GetLocale = saved
        return ns
    end""")
    for app, client, want, source in [(None, "enUS", "en", "client"), (None, "deDE", "de", "client"), ("de", "enUS", "de", "app"),
                                      ("en", "deDE", "en", "app"), ("xx", "deDE", "de", "client"), (None, None, "en", "client")]:
        loc = load_locale(locale_src, app, client)
        check("language %s/%s" % (app, client), loc["lang"] == want and loc["langSource"] == source, "%s %s" % (loc["lang"], loc["langSource"]))
    loc = load_locale(locale_src, "de", "enUS")
    texts = loc["texts"]
    de_keys, en_keys = set(texts["de"].keys()), set(texts["en"].keys())
    check("locale: same keys in L.de and L.en", de_keys == en_keys, str(sorted(de_keys ^ en_keys)))
    check("locale: at least 150 texts", len(en_keys) >= 150, str(len(en_keys)))
    bad_formats = [k for k in en_keys if sorted(re.findall(r"%[-0-9.]*[sdfx%]", texts["de"][k])) != sorted(re.findall(r"%[-0-9.]*[sdfx%]", texts["en"][k]))]
    check("locale: German and English texts have the same format codes", not bad_formats, str(bad_formats))
    german_in_en = [k for k in en_keys if re.search("[äöüÄÖÜß]", texts["en"][k]) and "WoW-Men" not in texts["en"][k]]
    check("locale: no German letters in L.en (except the app's radial menu name)", not german_in_en, str(german_in_en))
    same = [k for k in en_keys if texts["de"][k] == texts["en"][k]]
    check("locale: German differs from English except for words that are the same", len(same) <= 15, str(same))
    check("locale: unknown key gives the key", run("return 1") == 1 and lua.eval("function(L) return L.no_such_key end")(loc["L"]) == "no_such_key")
    check("locale: L reads the active language", loc["L"]["peek_at_everything"] == texts["de"]["peek_at_everything"])
    # the other parts hold no German text any more: German letters only in comments, in L.de, and in a few command words
    allowed = {"TouchForeverUI.lua": {'arg == "groß"', 'word == "menüleiste"'}}
    for name in FILES[2:]:
        for number, line in enumerate((ADDON / name).read_text(encoding="utf-8").splitlines(), 1):
            code = line.split("--", 1)[0]
            if re.search("[äöüÄÖÜß]", code) and not any(a in code for a in allowed.get(name, ())) and "WoW-Men" not in code:
                check("no German text in %s" % name, False, "line %d: %s" % (number, line.strip()[:100]))
    check("no raw GetLocale outside the locale file", all("GetLocale" not in (ADDON / n).read_text(encoding="utf-8") for n in FILES[2:]))

    undefined = sorted(k for k in run("local t = {}; for k in pairs(undefined) do t[#t + 1] = k end return t").values())
    # frame names of the bars / chat windows are many; they are all looked up with a nil check
    shown = [k for k in undefined if not re.match(r"^(MultiBar|ActionButton|StanceButton|PetActionButton|PossessButton|ChatFrame\d+|CHAT_)", k)]
    print("undefined globals read, %d more are frame / format names (review the rest):" % (len(undefined) - len(shown)), ", ".join(shown))
    print("checks: %d, failures: %d" % (count[0], len(failures)))
    for failure in failures:
        print("FAIL", failure)
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())
