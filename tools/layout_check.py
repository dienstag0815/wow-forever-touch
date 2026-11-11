#!/usr/bin/env python
"""Check of TouchForeverLayout.lua (the Edit Mode layout "Touch Forever") with lupa and a mock of the Edit Mode API.

Run: python -I tools/layout_check.py        (needs: pip install lupa; exit code 0 = all fine)

Covers: syntax of the file, texts in German and English, the zones (plan() for several screen sizes against the touch controls of
profiles/controls-8.icp: no window of the plan may lie under a control or under another window),
the flow on / off / again / combat / Edit Mode open / signal / once / 5 layouts / Gamepad base / API missing / way back.
The mock follows what Blizzard_EditMode does (presets in front of the saved layouts, activeLayout counts the presets); the real
game was not available when this was written, see docs/reports/uilayout_2026-11-10.md ("am Geraet zu pruefen").
"""
import json
import math
import pathlib
import re
import sys

import lupa

ROOT = pathlib.Path(__file__).resolve().parent.parent
ADDON = ROOT / "app/src/main/assets/wow/addon/TouchForever"

STANDARD_PROFILE = ROOT / "app/src/main/assets/inputcontrols/profiles/controls-8.icp"

# Estimated sizes (UI units) of what the plan does not size itself; keep in step with the constants at the top of the layout file.
CHAT_W, CHAT_H, CHAT_EDIT = 430, 170, 32     # chat window and the edit line below it
UNIT_W, UNIT_H = 200, 100
STANCE_W, PET_W, SLIM_H = 200, 350, 40
MICRO_W, MICRO_H = 300, 40
BAGS_W, BAGS_H = 200, 45
TOOLTIP_W, TOOLTIP_H = 125, 72                  # default tooltip of a unit (device 11.11.2026: 240 x 135 px of a 1510 px high screen = 122 x 69 units)
TRACKER_W = 248                               # keep in step with TRACKER_W of the layout file
MINIMAP_W, MINIMAP_H = 220, 200               # the plan leaves the minimap at the top right corner


def touch_shapes(width, height):
    """The controls of the standard layout as ('c', cx, cy, r) / ('r', l, t, r, b) in pixels, as ControlElement.computeBoundingBox does."""
    profile = json.loads(STANDARD_PROFILE.read_text(encoding="utf-8"))
    snap = width // 100
    max_height = round(height / snap) * snap
    shapes = []
    for e in profile["elements"]:
        x, y, scale = int(e["x"] * width), int(e["y"] * max_height), e["scale"]
        ws, hs = scale * e.get("widthScale", 1), scale * e.get("heightScale", 1)
        if e["type"] == "STICK":
            half = 6 * snap * scale
            ring = e.get("ring")
            if ring:
                outer = ring.get("outer") or []
                extent = (0.5 + ring["width"]) if any(outer) else 0
                half += math.ceil((ring["gap"] + ring["width"] + extent) * snap * scale)
            shapes.append(("c", x, y, half))
        elif e["type"] == "SCROLL_WHEEL":
            hw, hh = (5, 2) if e.get("orientation", 0) == 1 else (2, 5)
            shapes.append(("r", x - hw * snap * ws, y - hh * snap * hs, x + hw * snap * ws, y + hh * snap * hs))
        else:
            kind = e["shape"]
            if kind == "CIRCLE":
                shapes.append(("c", x, y, 3 * snap * ws))
                continue
            hw, hh = {"PILL": (5 * snap, int(snap * 1.5)), "SQUARE": (int(snap * 2.5), int(snap * 2.5))}.get(kind, (4 * snap, 2 * snap))
            shapes.append(("r", x - hw * ws, y - hh * hs, x + hw * ws, y + hh * hs))
    return shapes


def shape_depth(a, b):
    """How far two shapes reach into each other (0 = apart); same unit as the shapes."""
    if a[0] == "r" and b[0] == "r":
        return max(0.0, min(min(a[3], b[3]) - max(a[1], b[1]), min(a[4], b[4]) - max(a[2], b[2])))
    if a[0] == "c" and b[0] == "c":
        return max(0.0, a[3] + b[3] - math.hypot(a[1] - b[1], a[2] - b[2]))
    c, r = (a, b) if a[0] == "c" else (b, a)
    nx, ny = max(r[1], min(c[1], r[3])), max(r[2], min(c[2], r[4]))
    return max(0.0, c[3] - math.hypot(c[1] - nx, c[2] - ny))


# The top row of buttons sits flush with the top edge on the Fold inner display (about 4:3); on wider screens its size (a fraction of
# the width) makes it leave the top edge by a few units, which is the accepted price.
TOP_SLACK = 5


def screen_fits(shapes, width, height):
    for sh in shapes:
        l, t, r, b = (sh[1] - sh[3], sh[2] - sh[3], sh[1] + sh[3], sh[2] + sh[3]) if sh[0] == "c" else sh[1:]
        if l < 0 or t < -TOP_SLACK or r > width or b > height:
            return False
    return True


def window_rect(w, h, point, x, y, bw, bh):
    """Rectangle (l, t, r, b), y from the top, of a window of bw x bh anchored with its `point` at the offset (x, y) of the same point of the screen."""
    px = w / 2 + x if point in ("BOTTOM", "TOP", "CENTER") else (x if "LEFT" in point else w + x)
    py = h - y if "BOTTOM" in point else (-y if "TOP" in point else h / 2 - y)
    left = px - bw / 2 if point in ("BOTTOM", "TOP", "CENTER") else (px if "LEFT" in point else px - bw)
    top = py - bh if "BOTTOM" in point else (py if "TOP" in point else py - bh / 2)
    return ("r", left, top, left + bw, top + bh)


def plan_windows(plan, w, h):
    """name -> rectangle for every entry of the plan, plus the minimap that stays where it is."""
    out = {}
    for i in range(1, len(plan.entries) + 1):
        e = plan.entries[i]
        key, point, x, y = str(e["key"]), str(e["point"]), float(e["x"]), float(e["y"])
        st = e["settings"]
        if key in ("MainBar", "Bar2", "Bar3"):
            size = 45 * (50 + 10 * st.IconSize) / 100 + st.IconPadding
            dims = (6 * size, 2 * size)
        elif key in ("RightBar1", "RightBar2"):
            size = 45 * (50 + 10 * st.IconSize) / 100 + st.IconPadding
            dims = (size, st.NumIcons * size)
        elif key == "StanceBar":
            dims = (STANCE_W, SLIM_H)
        elif key == "PetActionBar":
            dims = (PET_W, SLIM_H)
        elif key == "Chat":
            dims = (CHAT_W, CHAT_H + CHAT_EDIT)
            y -= CHAT_EDIT
        elif key in ("Player", "Target"):
            dims = (UNIT_W, UNIT_H)
        elif key == "Tracker":
            dims = (TRACKER_W, float(plan.questHeight))
        elif key == "Tooltip":
            dims = (TOOLTIP_W, TOOLTIP_H)
        elif key == "MicroMenu":
            dims = (MICRO_W, MICRO_H)
        elif key == "Bags":
            dims = (BAGS_W, BAGS_H)
        else:
            raise AssertionError("no size known for plan entry " + key)
        out[key] = window_rect(w, h, point, x, y, *dims)
    out["Minimap"] = window_rect(w, h, "TOPRIGHT", 0, 0, MINIMAP_W, MINIMAP_H)
    return out


MOCK = r"""
unpack = unpack or table.unpack
printed, timers = {}, {}
combat, editOpen, saveCalls, setActiveCalls, addedCalls = false, false, 0, {}, {}
lastPayload = nil
uiW, uiH = 1365, 768

local function copy(v) if type(v) ~= "table" then return v end local c = {} for k, x in pairs(v) do c[k] = copy(x) end return c end
function print(...) local t = {} for i = 1, select("#", ...) do t[#t + 1] = tostring((select(i, ...))) end printed[#printed + 1] = table.concat(t, " ") end
function GetTime() return 0 end
function InCombatLockdown() return combat end
C_Timer = { After = function(s, fn) timers[#timers + 1] = fn end }
function step() local q = timers timers = {} for _, f in ipairs(q) do f() end end
function flush() local n = 0 while #timers > 0 and n < 200 do local f = table.remove(timers, 1) f() n = n + 1 end end
UIParent = { GetWidth = function() return uiW end, GetHeight = function() return uiH end }

Enum = {
    EditModeSystem = { ActionBar = 0, CastBar = 1, Minimap = 2, UnitFrame = 3, EncounterBar = 4, ExtraAbilities = 5, ChatFrame = 8, ObjectiveTracker = 12, HudTooltip = 11, MicroMenu = 13, Bags = 14 },
    EditModeActionBarSystemIndices = { MainBar = 1, Bar2 = 2, Bar3 = 3, RightBar1 = 4, RightBar2 = 5, StanceBar = 11, PetActionBar = 12 },
    EditModeUnitFrameSystemIndices = { Player = 1, Target = 2 },
    EditModeActionBarSetting = { Orientation = 0, NumRows = 1, NumIcons = 2, IconSize = 3, IconPadding = 4 },
    EditModeLayoutType = { Preset = 0, Account = 1, Character = 2 },
    EditModePresetLayoutsMeta = { NumValues = 3 },
}

local function system(sys, idx, point, x, y)
    return { system = sys, systemIndex = idx, anchorInfo = { point = point, relativeTo = "UIParent", relativePoint = point, offsetX = x, offsetY = y },
        settings = { { setting = 3, value = 5 }, { setting = 7, value = 1 } }, isInDefaultPosition = true }
end
function mkLayout(name, ltype, style, mainBar)
    local s = {}
    if mainBar then
        s[#s + 1] = system(0, 1, "BOTTOM", 0, 45)
        s[#s + 1] = system(0, 2, "BOTTOM", 0, 90); s[#s + 1] = system(0, 3, "BOTTOM", 0, 135)
        s[#s + 1] = system(0, 4, "RIGHT", 5, -77); s[#s + 1] = system(0, 5, "RIGHT", -40, -77)
        s[#s + 1] = system(0, 11, "BOTTOM", 0, 200); s[#s + 1] = system(0, 12, "BOTTOM", 0, 230)
    end
    s[#s + 1] = system(2, nil, "TOPRIGHT", 0, 0)
    s[#s + 1] = system(3, 1, "BOTTOMRIGHT", -300, 250); s[#s + 1] = system(3, 2, "BOTTOMLEFT", 300, 250)
    s[#s + 1] = system(8, 1, "BOTTOMLEFT", 35, 50)
    s[#s + 1] = system(12, nil, "TOPRIGHT", -110, -275); s[#s + 1] = system(11, nil, "BOTTOMRIGHT", -10, 100)
    s[#s + 1] = system(13, nil, "BOTTOMRIGHT", -10, 0); s[#s + 1] = system(14, nil, "BOTTOMRIGHT", -10, 45)
    return { layoutName = name, layoutType = ltype, interfaceStyle = style, systems = s }
end

presets = { mkLayout("Modern", 0, 0, true), mkLayout("Classic", 0, 1, true), mkLayout("Gamepad", 0, 2, false) }
EditModePresetLayoutManager = { GetCopyOfPresetLayouts = function() return copy(presets) end }
EditModeManagerFrame = { IsEditModeActive = function() return editOpen end }

function resetEditMode(saved, active, noApi)
    savedLayouts, activeLayout = saved or {}, active or 1
    saveCalls, setActiveCalls, addedCalls, lastPayload = 0, {}, {}, nil
    if noApi then C_EditMode = nil return end
    C_EditMode = {
        GetLayouts = function() return { layouts = copy(savedLayouts), activeLayout = activeLayout } end,
        SaveLayouts = function(info)
            saveCalls = saveCalls + 1; lastPayload = copy(info)
            local out = {}
            for _, l in ipairs(info.layouts) do if l.layoutType ~= 0 then out[#out + 1] = copy(l) end end
            savedLayouts = out
        end,
        OnLayoutAdded = function(idx, activate, imported) addedCalls[#addedCalls + 1] = idx; if activate then activeLayout = idx end end,
        SetActiveLayout = function(idx) setActiveCalls[#setActiveCalls + 1] = idx; activeLayout = idx end,
        IsValidLayoutName = function(name) return name ~= "" end,
    }
end

frames = {}
function CreateFrame() local f = { scripts = {} } f.SetScript = function(self, n, fn) self.scripts[n] = fn end f.RegisterEvent = function() end frames[#frames + 1] = f return f end
function fire(event) for _, f in ipairs(frames) do if f.scripts.OnEvent then f.scripts.OnEvent(f, event) end end end

-- stub of the interface module: only what the layout part uses
function newUI()
    local ui = { layout = nil }
    local U = { questCalls = {} }
    function U.GetDB() return ui end
    function U.SetQuestHeight(h) ui.questsHeight = h; U.questCalls[#U.questCalls + 1] = h or "nil" end
    return U
end

function loadFile(src, name, ns)
    local f = assert(load(src, "@" .. name, "t"))
    f("TouchForever", ns)
end

function setupAddon(localeSrc, layoutSrc, signal)
    frames = {}; timers = {}
    local ns = {}
    ns.layoutStandard = signal or nil
    loadFile(localeSrc, "TouchForeverLocale.lua", ns)
    ns.UI = newUI()
    loadFile(layoutSrc, "TouchForeverLayout.lua", ns)
    return ns
end
function savedNames() local t = {} for i, l in ipairs(savedLayouts) do t[#t + 1] = l.layoutName .. ":" .. l.layoutType end return table.concat(t, ",") end
function findSys(layout, sys, idx) for _, s in ipairs(layout.systems) do if s.system == sys and (idx == nil or s.systemIndex == idx) then return s end end end
function lastPrinted() return printed[#printed] or "" end
"""


def main():
    failures, count = [], [0]

    def check(name, ok, detail=""):
        count[0] += 1
        if not ok:
            failures.append("%s %s" % (name, detail))

    lua = lupa.LuaRuntime(unpack_returned_tuples=True)
    layout_src = (ADDON / "TouchForeverLayout.lua").read_text(encoding="utf-8")
    locale_src = (ADDON / "TouchForeverLocale.lua").read_text(encoding="utf-8")
    check("no CR", "\r" not in layout_src)
    try:
        lua.eval("function(src) return assert(load(src, 'layout', 't')) end")(layout_src)
    except Exception as error:  # noqa: BLE001
        print("compile FAIL:", error)
        return 1
    print("compile ok: TouchForeverLayout.lua")
    toc = (ADDON / "TouchForever.toc").read_text(encoding="utf-8")
    files = [l.strip() for l in toc.splitlines() if l.strip() and not l.startswith("##")]
    check("toc: layout after ui, before options", files.index("TouchForeverUI.lua") + 1 == files.index("TouchForeverLayout.lua") == files.index("TouchForeverOptions.lua") - 1)

    # ---- texts ------------------------------------------------------------------------------------------------------------
    used = set(re.findall(r"\bL\.([a-z_0-9]+)", layout_src))
    options_src = (ADDON / "TouchForeverOptions.lua").read_text(encoding="utf-8")
    used |= {k for k in re.findall(r"\bL\.([a-z_0-9]+)", options_src) if "layout" in k}
    de_block = locale_src[locale_src.index("S.de = {"):locale_src.index("S.en = {")]
    en_block = locale_src[locale_src.index("S.en = {"):]
    for key in sorted(used):
        check("text %s in de" % key, re.search(r"\b%s = " % key, de_block) is not None)
        check("text %s in en" % key, re.search(r"\b%s = " % key, en_block) is not None)

    run = lua.execute
    run(MOCK)
    G = lua.globals()
    ev = lua.eval

    def setup(signal=False, saved=None, active=1, api=True, w=1365, h=768):
        G.uiW, G.uiH = w, h
        G.combat, G.editOpen = False, False
        G.printed = lua.table()
        ev("function(s, a, n) resetEditMode(s, a, n) end")(saved if saved is not None else lua.table(), active, not api)
        return ev("function(l, y, s) return setupAddon(l, y, s) end")(locale_src, layout_src, signal)

    def printed():
        return [str(G.printed[i]) for i in range(1, len(G.printed) + 1)]

    def text(key, lang="en"):
        return str(ev("function(ns, k) return ns.texts[%r][k] end" % lang)(NS[0], key))

    NS = [None]

    # ---- plan() against the touch controls of controls-8.icp -------------------------------------------------------------------
    NS[0] = setup()
    # UI units of the parent (height 768): the Fold inner display (about 4:3) and neighbours. The touch layout only fits screens up
    # to about 3:2 (the top row would leave the screen above that) and the unit frames only left of the microphone button from
    # about 1.3:1 up (the plan warns "unit_mic" below that), so only screens in between are checked against the controls
    for w, h in [(1016, 768), (1024, 768), (1152, 768), (1365, 768), (1280, 720), (1600, 720), (1706, 768), (2000, 900)]:
        plan = NS[0].layout.plan(w, h)
        by = {str(plan.entries[i]["key"]): plan.entries[i] for i in range(1, len(plan.entries) + 1)}
        tag = "%dx%d" % (w, h)
        warnings = [str(plan.warnings[i]) for i in range(1, len(plan.warnings) + 1)]
        check("%s: icon size 50..100 %%" % tag, 50 <= plan.percent <= 100 and plan.percent % 10 == 0)
        check("%s: tracker height 100..1000" % tag, 100 <= plan.questHeight <= 1000 and plan.questHeight % 10 == 0, str(plan.questHeight))
        check("%s: right bars vertical at the right edge" % tag, by["RightBar1"]["point"] == "TOPRIGHT" and by["RightBar1"]["settings"].Orientation == 1)
        check("%s: tracker flush right below the minimap" % tag,
              by["Tracker"]["point"] == "TOPRIGHT" and -4 <= by["Tracker"]["x"] <= 0 and -by["Tracker"]["y"] >= MINIMAP_H, str(by["Tracker"]["y"]))
        check("%s: unit frames at the top left, target beside (or below when narrow)" % tag, by["Player"]["y"] == -6 and by["Player"]["point"] == "TOPLEFT"
              and ((by["Target"]["y"] == -6 and by["Target"]["x"] > 6 + UNIT_W) or (by["Target"]["y"] < by["Player"]["y"] and by["Target"]["x"] == 6)))
        if abs(w / h - 1016 / 768) < 0.01:
            check("%s: target right of the player frame like on the device" % tag, by["Target"]["y"] == -6 and by["Target"]["x"] == 211, str(by["Target"]["x"]))
        if w / h > 1.5:
            continue
        # snap sizes as in the app: pixels of a screen of the same shape, width 2000
        px_w, px_h = 2000, round(2000 * h / w)
        scale = w / px_w
        controls = [tuple([sh[0]] + [v * scale for v in sh[1:]]) for sh in touch_shapes(px_w, px_h)]
        check("%s: the controls fit on the screen" % tag, screen_fits(controls, w, h))
        windows = plan_windows(plan, w, h)
        for name, rect in windows.items():
            check("%s: %s inside the screen" % (tag, name), rect[1] >= -0.5 and rect[2] >= -0.5 and rect[3] <= w + 0.5 and rect[4] <= h + 0.5,
                  "%s" % (tuple(round(v) for v in rect[1:]),))
            hit = [i for i, c in enumerate(controls) if shape_depth(rect, c) > 0.01]
            check("%s: %s does not lie under a control" % (tag, name), not hit, "controls %s, rect %s" % (hit, tuple(round(v) for v in rect[1:])))
        names = list(windows)
        for i in range(len(names)):
            for j in range(i + 1, len(names)):
                depth = shape_depth(windows[names[i]], windows[names[j]])
                check("%s: %s and %s do not overlap" % (tag, names[i], names[j]), depth <= 0.01, "%.1f" % depth)
        check("%s: no warnings" % tag, not warnings, str(warnings))
    # tall narrow window: warning instead of a crash
    plan = NS[0].layout.plan(400, 800)
    check("narrow screen still gives a plan", len(plan.entries) == 14, str(len(plan.entries)))

    # ---- without signal: nothing by itself -----------------------------------------------------------------------------------
    NS[0] = setup(signal=False, saved=lua.table())
    ev("function() fire('PLAYER_ENTERING_WORLD'); flush() end")()
    check("no signal: no layout created", int(G.saveCalls) == 0 and int(ev("function() return #savedLayouts end")()) == 0)

    # ---- with signal: once, automatically --------------------------------------------------------------------------------------
    NS[0] = setup(signal=True)
    ev("function() fire('PLAYER_ENTERING_WORLD'); flush() end")()
    check("signal: one save, one add", int(G.saveCalls) == 1 and len(G.addedCalls) == 1)
    check("signal: layout is saved as Touch Forever (Account)", str(ev("savedNames")()) == "Touch Forever:1", str(ev("savedNames")()))
    check("signal: new layout index 4 and active", int(G.activeLayout) == 4 and G.addedCalls[1] == 4, str(G.activeLayout))
    own = ev("function() return savedLayouts[1] end")()
    check("signal: interface style copied from the base (Modern 0)", own.interfaceStyle == 0)
    main = ev("function() return findSys(savedLayouts[1], 0, 1) end")()
    check("signal: main bar moved, not default position", main.isInDefaultPosition is False and main.anchorInfo.point == "BOTTOM" and main.anchorInfo.offsetY != 45)
    settings = {int(main.settings[i].setting): main.settings[i].value for i in range(1, len(main.settings) + 1)}
    check("signal: main bar 2 rows, 12 icons, icon size raw 0..5, other settings kept", settings[1] == 2 and settings[2] == 12 and 0 <= settings[3] <= 5 and settings[7] == 1, str(settings))
    minimap = ev("function() return findSys(savedLayouts[1], 2) end")()
    check("signal: minimap untouched", minimap.isInDefaultPosition is True and minimap.anchorInfo.offsetX == 0)
    check("signal: presets stay in front of the payload", ev("function() return #lastPayload.layouts == 4 and lastPayload.layouts[1].layoutType == 0 and lastPayload.layouts[3].layoutName == 'Gamepad' end")())
    check("signal: previous layout remembered (preset 1)", ev("function(ns) return ns.UI.GetDB().layout.prev.index == 1 and ns.UI.GetDB().layout.prev.type == 0 end")(NS[0]))
    check("signal: quest height set", ev("function(ns) return ns.UI.GetDB().questsHeight ~= nil and ns.UI.GetDB().layout.questSet == ns.UI.GetDB().questsHeight end")(NS[0]))
    check("signal: message printed", any("Touch Forever" in p for p in printed()), str(printed()))
    check("signal: presets in the mock stay unchanged", ev("function() return findSys(presets[1], 0, 1).anchorInfo.offsetY == 45 end")())

    # again login: done flag, nothing more
    saves = int(G.saveCalls)
    ev("function() fire('PLAYER_ENTERING_WORLD'); flush() end")()
    check("signal: second world entry does nothing", int(G.saveCalls) == saves)
    # a new session (module loaded again) with the saved variables: done stays
    ns_old_db = NS[0].UI.GetDB()
    G.LAYSRC = layout_src
    ev("function(ns, db) frames = {}; timers = {}; local n2 = {layoutStandard = true}; n2.texts = ns.texts; n2.L = ns.L; n2.UI = newUI(); n2.UI.GetDB = function() return db end; loadFile(LAYSRC, 'l', n2); fire('PLAYER_ENTERING_WORLD'); flush() end")(NS[0], ns_old_db)
    check("signal: next start (done flag saved) does nothing", int(G.saveCalls) == saves)

    # ---- way back --------------------------------------------------------------------------------------------------------------
    NS[0] = setup(signal=False)
    NS[0].layout.Enable()
    ev("flush")()
    check("manual on: active = own", NS[0].layout.IsActive())
    ev("function(ns) ns.layout.Disable() end")(NS[0])
    check("off: back on preset 1", int(G.activeLayout) == 1 and not NS[0].layout.IsActive(), str(G.activeLayout))
    check("off: quest height taken back", ev("function(ns) return ns.UI.GetDB().questsHeight == nil end")(NS[0]))
    check("off: own layout stays saved", str(ev("savedNames")()) == "Touch Forever:1")
    NS[0].layout.Disable()
    check("off twice: message, no switch", "was not on" in printed()[-1] and len(G.setActiveCalls) == 1, printed()[-1])
    # on again with the existing layout: activation only
    saves = int(G.saveCalls)
    NS[0].layout.Enable()
    check("on with existing layout: no new save, activated", int(G.saveCalls) == saves and int(G.activeLayout) == 4)
    # again: updates in place
    ev("function() uiW, uiH = 1600, 720 end")()
    NS[0].layout.Again()
    check("again: saved in place, still one layout", str(ev("savedNames")()) == "Touch Forever:1" and int(G.saveCalls) == saves + 1)
    main = ev("function() return findSys(savedLayouts[1], 0, 1) end")()
    check("again: new screen size used (x differs)", abs(main.anchorInfo.offsetX - (0.477 * 1600 - 800)) < 0.2, str(main.anchorInfo.offsetX))

    # player switched to another layout meanwhile: off must not pull him back
    NS[0] = setup(signal=False)
    NS[0].layout.Enable(); ev("flush")()
    ev("function() activeLayout = 2 end")()
    calls = len(G.setActiveCalls)
    NS[0].layout.Disable()
    check("off while another layout is active: no switch", int(G.activeLayout) == 2 and len(G.setActiveCalls) == calls)

    # way back to a saved (custom) layout, with a character layout behind the account ones
    custom = ev("function() return { mkLayout('Mein Layout', 1, 0, true), mkLayout('Char', 2, 0, true) } end")()
    NS[0] = setup(signal=False, saved=custom, active=5)  # active = Char (3 presets + 2)
    NS[0].layout.Enable(); ev("flush")()
    check("custom base: own inserted between account and character layouts", str(ev("savedNames")()) == "Mein Layout:1,Touch Forever:1,Char:2", str(ev("savedNames")()))
    check("custom base: payload keeps Char active before the switch", ev("function() return lastPayload.activeLayout == 6 end")(), str(ev("function() return lastPayload.activeLayout end")()))
    check("custom base: own is index 5 and active", int(G.activeLayout) == 5)
    check("custom base: copy of Char, but type Account", ev("function() return savedLayouts[2].layoutType == 1 and savedLayouts[3].layoutName == 'Char' end")())
    NS[0].layout.Disable()
    check("way back to the character layout by name (index 6)", int(G.activeLayout) == 6, str(G.activeLayout))
    check("custom layouts untouched", ev("function() return findSys(savedLayouts[1], 0, 1).isInDefaultPosition == true and findSys(savedLayouts[3], 0, 1).isInDefaultPosition == true end")())

    # ---- combat / Edit Mode -------------------------------------------------------------------------------------------------
    NS[0] = setup(signal=False)
    G.combat = True
    r = NS[0].layout.Enable()
    check("combat: waits", str(r) == "wait" and int(G.saveCalls) == 0 and NS[0].layout.Pending() == "on")
    ev("function() fire('PLAYER_REGEN_ENABLED') end")()
    check("combat: PLAYER_REGEN_ENABLED while still flagged does nothing", int(G.saveCalls) == 0)
    G.combat = False
    ev("function() fire('PLAYER_REGEN_ENABLED') end")()
    check("after combat: done", int(G.saveCalls) == 1 and NS[0].layout.Pending() is None, str(G.saveCalls))
    NS[0] = setup(signal=False)
    G.editOpen = True
    r = NS[0].layout.Enable()
    check("edit mode open: waits, no save", str(r) == "wait" and int(G.saveCalls) == 0)
    ev("function() step() end")()  # one poll, Edit Mode still open
    check("edit mode open: still nothing after the poll", int(G.saveCalls) == 0 and NS[0].layout.Pending() == "on")
    G.editOpen = False
    ev("function() step() end")()
    check("edit mode closed: job runs at the next look", int(G.saveCalls) == 1 and NS[0].layout.Pending() is None, str(G.saveCalls))
    # off in combat
    NS[0] = setup(signal=False)
    NS[0].layout.Enable()
    G.combat = True
    NS[0].layout.Disable()
    check("off in combat: waits", NS[0].layout.Pending() == "off" and NS[0].layout.IsActive())
    G.combat = False
    ev("function() fire('PLAYER_REGEN_ENABLED') end")()
    check("off after combat: back", int(G.activeLayout) == 1)
    # automatic start in combat is caught up too, once
    NS[0] = setup(signal=True)
    G.combat = True
    ev("function() fire('PLAYER_ENTERING_WORLD'); flush() end")()
    check("signal in combat: nothing yet", int(G.saveCalls) == 0 and NS[0].layout.Pending() == "on")
    G.combat = False
    ev("function() fire('PLAYER_REGEN_ENABLED') end")()
    check("signal in combat: caught up after combat", int(G.saveCalls) == 1)

    # ---- limits ----------------------------------------------------------------------------------------------------------------
    five = ev("function() local t = {} for i = 1, 5 do t[i] = mkLayout('L' .. i, 1, 0, true) end return t end")()
    NS[0] = setup(signal=False, saved=five, active=4)
    r = NS[0].layout.Enable()
    check("5 account layouts: refused, nothing saved", str(r) == "full" and int(G.saveCalls) == 0 and "5 account layouts" in printed()[-1], printed()[-1])
    # Gamepad base (no main bar)
    NS[0] = setup(signal=True, active=3)
    ev("function() fire('PLAYER_ENTERING_WORLD'); flush() end")()
    check("gamepad base: skipped with a message, nothing saved", int(G.saveCalls) == 0 and "main bar" in printed()[-1], str(printed()))
    check("gamepad base: counted as done (no retry at every start)", ev("function(ns) return ns.UI.GetDB().layout.done == true end")(NS[0]))
    # no API
    NS[0] = setup(signal=False, api=False)
    r = NS[0].layout.Enable()
    check("no C_EditMode: message, no error", str(r) == "api" and "not available" in printed()[-1])
    # error inside the API is caught
    NS[0] = setup(signal=False)
    ev("function() C_EditMode.SaveLayouts = function() error('boom') end end")()
    r = NS[0].layout.Enable()
    check("API error is caught and reported", str(r) == "error" and "boom" in printed()[-1], printed()[-1])
    # slash words
    NS[0] = setup(signal=False)
    for word in ["on", "info", "again", "off", "", "xyz"]:
        try:
            NS[0].layout.Slash(word)
        except Exception as error:  # noqa: BLE001
            check("slash %r runs" % word, False, str(error))
    check("slash unknown word prints the usage", "/tf layout" in printed()[-1], printed()[-1])

    print("checks: %d, failures: %d" % (count[0], len(failures)))
    for failure in failures:
        print("FAIL", failure)
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())
