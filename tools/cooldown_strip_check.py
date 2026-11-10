#!/usr/bin/env python3
"""Checks the Lua side of the cooldown strip (addon TouchForever, TouchForeverCooldown.lua) with lupa.

Not part of the build; needs `pip install lupa` (do it in a throw-away venv, not in the repo). It
  1. compiles every Lua file of the addon (syntax check),
  2. runs the cooldown file against a small mock of the WoW API, lets the addon update its strip for several scenarios
     and decodes the cell colours with an independent Python decoder of the documented format,
  3. checks the window section (open big game windows: bags, map ...) with mocked frames: the rectangles in shares of the
     game picture, merging, dropping of small windows, the limit of four, the last good rectangle when the game withholds it
  4. checks the scroll section (build 3: scrollable lists inside the open windows, the areas where the app turns a swipe into mouse
     wheel ticks) with mocked ScrollBox / ScrollFrame / ScrollingMessageFrame children: reporting, nesting (smallest first), duplicates,
     lists that do not need to scroll, the limit of six, the switch
  5. checks the context section (build 4: the unit under the pointer, the item slot under the pointer, the spell lists, the keys and
     buttons of the long-press menu of the launcher, throwing away only after a Yes) in TouchForeverContext.lua with mocked unit, spell
     and container functions
  6. with --write-vectors FILE writes the scenarios (cell colours and expected values) for the Kotlin unit test
     (app/src/test/resources/cooldown/cooldown_strip_vectors.txt), which decodes them with the app's decoder.

Run from the repo root:  python tools/cooldown_strip_check.py [--write-vectors app/src/test/resources/cooldown/cooldown_strip_vectors.txt]
"""
import argparse
import math
import pathlib
import sys

from lupa import LuaRuntime

ADDON = pathlib.Path(__file__).resolve().parent.parent / "app/src/main/assets/wow/addon/TouchForever"

MOCK = r"""
SECRET = {}
state = {
    now = 1000.0,
    actions = {}, cooldowns = {}, charges = {}, usable = {}, range = {},
    gcd = { startTime = 0, duration = 0 },
    secretSlots = {}, hasSecretApi = false,
}
local frames = {}
function newMock(kind, name)
    local f = { kind = kind, name = name, scripts = {}, events = {}, shown = true, textures = {}, scale = 1 }
    return setmetatable(f, { __index = function(t, k)
        if k == "SetScript" then return function(self, n, fn) self.scripts[n] = fn end end
        if k == "RegisterEvent" then return function(self, e) self.events[e] = true end end
        if k == "HookScript" then return function(self, n, fn) local h = rawget(self, "hooks"); if not h then h = {}; rawset(self, "hooks", h) end; h[n] = fn end end
        if k == "SetScale" then return function(self, s) self.scale = s end end
        if k == "SetAttribute" then return function(self, n, v) local a = rawget(self, "attrs"); if not a then a = {}; rawset(self, "attrs", a) end; a[n] = v end end
        if k == "GetAttribute" then return function(self, n) local a = rawget(self, "attrs"); return a and a[n] end end
        if k == "SetSize" then return function(self, w, h) self.w, self.h = w, h end end
        if k == "GetName" then return function(self) return self.name end end
        if k == "GetScript" then return function(self, n) return self.scripts[n] end end
        if k == "Show" then return function(self) self.shown = true end end
        if k == "Hide" then return function(self) self.shown = false end end
        if k == "CreateTexture" then
            return function(self)
                local tex = newMock("Texture")
                tex.sets = 0
                tex.SetColorTexture = function(s, r, g, b, a) s.r, s.g, s.b, s.sets = r, g, b, s.sets + 1 end
                tex.SetPoint = function(s, p, rel, rp, x, y) s.px, s.py = x, y end
                self.textures[#self.textures + 1] = tex
                return tex
            end
        end
        return function() end
    end })
end
function CreateFrame(kind, name) local f = newMock(kind, name); frames[#frames + 1] = f; return f end
UIParent = newMock("Frame", "UIParent")
-- Screen of 2448 x 1848 physical pixels; UIParent has the effective scale 0.5, so its units are 1017.35 / 0.5 wide and 768 / 0.5 high.
uiScale = 0.5
UIParent.GetEffectiveScale = function() return uiScale end
function GetScreenWidth() return 768 * 2448 / 1848 / uiScale end
function GetScreenHeight() return 768 / uiScale end
-- A window frame as the game has them: a global, hidden until a test shows it; GetRect may be withheld (combat).
function makeWindow(name, left, bottom, width, height)
    local f = newMock("Frame", name)
    f.visible, f.rect, f.hooks, f.withheld, f.escale = false, { left, bottom, width, height }, {}, false, uiScale
    f.IsVisible = function(self) return self.visible end
    f.GetEffectiveScale = function(self) return self.escale end
    f.GetRect = function(self)
        if self.withheld then return nil end
        return self.rect[1], self.rect[2], self.rect[3], self.rect[4]
    end
    f.HookScript = function(self, script, fn) self.hooks[script] = fn end
    f.children = {}
    f.GetChildren = function(self) return table.unpack(self.children) end
    _G[name] = f
    return f
end
-- A child frame inside a window (plain table: none of the mock fallbacks). kind: "plain", "scrollbox", "scrollframe", "smf".
-- extent: whether the content is longer than what is visible. Rectangle in the same units as makeWindow.
function makeChild(parent, kind, left, bottom, width, height, extent, options)
    options = options or {}
    local f = { rect = { left, bottom, width, height }, children = {}, shown = true, escale = uiScale }
    f.IsShown = function(self) return self.shown end
    f.GetChildren = function(self) return table.unpack(self.children) end
    f.GetRect = function(self) return self.rect[1], self.rect[2], self.rect[3], self.rect[4] end
    f.GetEffectiveScale = function(self) return self.escale end
    if kind == "scrollbox" then
        f.HasScrollableExtent = function(self) return extent end
        if options.horizontal ~= nil then f.IsHorizontal = function(self) return options.horizontal end end
    elseif kind == "scrollframe" then
        f.GetObjectType = function(self) return "ScrollFrame" end
        f.GetVerticalScrollRange = function(self) return extent and 400 or 0 end
    elseif kind == "smf" then
        f.GetObjectType = function(self) return "ScrollingMessageFrame" end
        f.GetMaxScrollRange = function(self) return extent and 25 or 0 end
    end
    parent.children[#parent.children + 1] = f
    return f
end
SlashCmdList = {}
C_Timer = { After = function() end }
inCombat = false
function InCombatLockdown() return inCombat end
overrideBindings = {}
function SetOverrideBindingClick(owner, priority, key, button, mouseButton)
    overrideBindings[#overrideBindings + 1] = { owner = owner, priority = priority, key = key, button = button, mouseButton = mouseButton }
end
function GetLocale() return "enUS" end
function GetTime() return state.now end
function GetPhysicalScreenSize() return 2448, 1848 end
function HasAction(slot) return state.actions[slot] and true or false end
function GetActionCharges(slot) local c = state.charges[slot]; if c then return c[1], c[2] end end
function IsUsableAction(slot) local u = state.usable[slot]; if u then return u[1], u[2] end return true, false end
function IsActionInRange(slot) local r = state.range[slot]; if r == nil then return nil end return r end
C_ActionBar = { GetActionCooldown = function(slot)
    if state.secretSlots[slot] then return { startTime = SECRET, duration = SECRET, isEnabled = SECRET } end
    local c = state.cooldowns[slot]
    if c then return { startTime = c[1], duration = c[2], isEnabled = true } end
    return { startTime = 0, duration = 0, isEnabled = true }
end }
C_Spell = { GetSpellCooldown = function() return { startTime = state.gcd.startTime, duration = state.gcd.duration } end }
issecretvalue = function(v) return v == SECRET end

function loadAddonFile(source, ns) return assert(load(source, "addon", "t"))("TouchForever", ns) end
function findFrame(name) for _, f in ipairs(frames) do if f.name == name then return f end end end
function firePlayerLogin()
    for _, f in ipairs(frames) do
        if f.events.PLAYER_LOGIN and f.scripts.OnEvent then f.scripts.OnEvent(f, "PLAYER_LOGIN") end
    end
end
function fireEvent(event)
    for _, f in ipairs(frames) do
        if f.events[event] and f.scripts.OnEvent then f.scripts.OnEvent(f, event) end
    end
end
function tick(frame, seconds) frame.scripts.OnUpdate(frame, seconds) end
function cellColor(frame, index) local t = frame.textures[index + 1]; return t.r, t.g, t.b end
"""


def decode(cells, slots_order):
    """Independent decoder: cells = list of (r, g, b) floats 0..1 as given to SetColorTexture."""
    px = [tuple(int(round(v * 255)) for v in c) for c in cells]
    if len(px) != 323:
        raise ValueError("cell count %d" % len(px))
    cal = [sum(px[5 + k]) / 3.0 for k in range(16)]
    assert all(cal[k + 1] - cal[k] > 3 for k in range(15)), "calibration"
    thr = [(cal[k] + cal[k + 1]) / 2 for k in range(15)]

    def nib(v):
        return sum(1 for t in thr if v >= t)

    def nibs(i):
        return [nib(v) for v in px[i]]

    info = nibs(4)
    seq = list(info)
    slots = []
    for i in range(96):
        a = nibs(21 + 2 * i)
        b = nibs(22 + 2 * i)
        seq += a + b
        slots.append((a[0] * 256 + a[1] * 16 + a[2], b[0] * 16 + b[1], b[2]))
    c = 0
    for n in seq:
        c = (c * 33 + n + 1) % 4093
    chk = nibs(213)
    assert chk[0] * 256 + chk[1] * 16 + chk[2] == c, "checksum"
    head = [tuple(1 if v > 128 else 0 for v in px[i]) for i in range(4)]
    tail = [tuple(1 if v > 128 else 0 for v in px[214 + i]) for i in range(2)]
    assert head == [(1, 0, 1), (0, 1, 0), (0, 0, 1), (1, 1, 0)], head
    assert tail == [(0, 1, 1), (1, 0, 0)], tail
    result = {"version": info[0], "counter": info[1], "infoFlags": info[2], "slots": slots, "windows": None}
    result["scroll"] = None
    result["context"] = None
    result["chat"] = decode_chat(px, thr)
    if info[2] & 8:
        raw = decode_windows(px, thr)
        result["windowFlagsRaw"] = raw[0]
        result["windows"] = (raw[0] & 5, raw[1])     # the scroll flag (8) and the context flag (2) are judged by their own tests
        if raw[0] & 8:
            result["scroll"] = decode_scroll(px, thr)
        if raw[0] & 2:
            result["context"] = decode_context(px, thr)
    return result


def decode_chat(px, thr):
    """Independent decoder of the chat section (cells 315..322, second row): (flags, (l, t, r, b) codes 0..255 or None)."""
    def nib(v):
        return sum(1 for t in thr if v >= t)

    head = [tuple(1 if v > 128 else 0 for v in px[315 + i]) for i in range(2)]
    tail = tuple(1 if v > 128 else 0 for v in px[322])
    assert head == [(1, 0, 1), (1, 1, 0)], head
    assert tail == (1, 0, 1), tail
    seq = []
    for cell in range(317, 321):
        seq += [nib(v) for v in px[cell]]
    assert len(seq) == 12
    c = 0
    for n in seq:
        c = (c * 33 + n + 1) % 4093
    chk = [nib(v) for v in px[321]]
    assert chk[0] * 256 + chk[1] * 16 + chk[2] == c, "chat checksum"
    version, count, flags = seq[0], seq[1], seq[2]
    assert version == 1 and count <= 1, (version, count)
    rect = tuple(seq[3 + 2 * e] * 16 + seq[3 + 2 * e + 1] for e in range(4)) if count else None
    if not count:
        assert all(v == 0 for v in seq[3:]), "unused chat rectangle not zero"
    assert seq[11] == 0, "chat kind"
    return flags, rect


def decode_context(px, thr):
    """Independent decoder of the context section: the list of 165 nibbles (payload of cells 258..312)."""
    def nib(v):
        return sum(1 for t in thr if v >= t)

    head = [tuple(1 if v > 128 else 0 for v in px[256 + i]) for i in range(2)]
    tail = tuple(1 if v > 128 else 0 for v in px[314])
    assert head == [(1, 1, 0), (0, 0, 1)], head
    assert tail == (0, 1, 0), tail
    seq = []
    for cell in range(258, 313):
        seq += [nib(v) for v in px[cell]]
    assert len(seq) == 165
    c = 0
    for n in seq:
        c = (c * 33 + n + 1) % 4093
    chk = [nib(v) for v in px[313]]
    assert chk[0] * 256 + chk[1] * 16 + chk[2] == c, "context checksum"
    assert seq[0] == 1, seq[0]
    return seq


def decode_windows(px, thr):
    """Independent decoder of the window section: (flags, [(l, t, r, b, kind), ...]) with codes 0..255."""
    def nib(v):
        return sum(1 for t in thr if v >= t)

    head = [tuple(1 if v > 128 else 0 for v in px[216 + i]) for i in range(2)]
    tail = tuple(1 if v > 128 else 0 for v in px[232])
    assert head == [(1, 0, 0), (0, 1, 1)], head
    assert tail == (1, 1, 1), tail
    seq = []
    for cell in range(218, 231):
        seq += [nib(v) for v in px[cell]]
    c = 0
    for n in seq:
        c = (c * 33 + n + 1) % 4093
    chk = [nib(v) for v in px[231]]
    assert chk[0] * 256 + chk[1] * 16 + chk[2] == c, "window checksum"
    version, count, flags = seq[0], seq[1], seq[2]
    assert 1 <= version <= 15 and count <= 4, (version, count)
    rects = []
    for i in range(count):
        b = 3 + 9 * i
        rects.append(tuple(seq[b + 2 * e] * 16 + seq[b + 2 * e + 1] for e in range(4)) + (seq[b + 8],))
    LAST_WINDOW_VERSION[0] = version
    return flags, rects


def decode_scroll(px, thr):
    """Independent decoder of the scroll section: (flags, [(l, t, r, b, kind), ...]) with codes 0..255, smallest first."""
    def nib(v):
        return sum(1 for t in thr if v >= t)

    head = [tuple(1 if v > 128 else 0 for v in px[233 + i]) for i in range(2)]
    tail = tuple(1 if v > 128 else 0 for v in px[255])
    assert head == [(0, 1, 0), (1, 0, 1)], head
    assert tail == (1, 1, 0), tail
    seq = []
    for cell in range(235, 254):
        seq += [nib(v) for v in px[cell]]
    assert len(seq) == 57
    c = 0
    for n in seq:
        c = (c * 33 + n + 1) % 4093
    chk = [nib(v) for v in px[254]]
    assert chk[0] * 256 + chk[1] * 16 + chk[2] == c, "scroll checksum"
    version, count, flags = seq[0], seq[1], seq[2]
    assert version == 1 and count <= 6, (version, count)
    rects = []
    for i in range(count):
        b = 3 + 9 * i
        rects.append(tuple(seq[b + 2 * e] * 16 + seq[b + 2 * e + 1] for e in range(4)) + (seq[b + 8],))
    # the unused rectangles are zero
    for i in range(count, 6):
        b = 3 + 9 * i
        assert all(v == 0 for v in seq[b:b + 9]), "unused scroll rectangle not zero"
    return flags, rects


LAST_WINDOW_VERSION = [0]   # section version (= addon build) of the last decoded window section


def remaining_from_code(code):
    return code / 10.0 if code < 3600 else 360 + (code - 3600) * 30


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--write-vectors")
    args = parser.parse_args()

    lua = LuaRuntime(unpack_returned_tuples=True)
    for path in sorted(ADDON.glob("*.lua")):
        text = path.read_text(encoding="utf-8")
        assert "\r" not in text, path.name + " has CR"
        lua.eval("function(src) return assert(load(src, 'x', 't')) end")(text)
    print("compile ok:", ", ".join(p.name for p in sorted(ADDON.glob("*.lua"))))

    lua.execute(MOCK)
    g = lua.globals()
    ns = lua.table()
    locale_src = (ADDON / "TouchForeverLocale.lua").read_text(encoding="utf-8")   # every part reads its texts from ns.L
    g.loadAddonFile(locale_src, ns)
    g.loadAddonFile((ADDON / "TouchForeverCooldown.lua").read_text(encoding="utf-8"), ns)
    S = ns.strip
    assert S.CELL_COUNT == 216 and S.TOTAL_CELLS == 323 and S.ROW_CELLS == 256 and S.WIDTH == 1024 and S.SLOT_COUNT == 96 and S.WIN_MAX == 4
    assert S.CELL_CTX == 256 and S.CELL_CTX_INFO == 258 and S.CELL_CTX_UNIT == 259 and S.CELL_CTX_FOCUS == 261 and S.CELL_CTX_BAR == 263
    assert S.CELL_CTX_SPELL == 265 and S.CELL_CTX_CHECK == 313 and S.CELL_CTX_TAIL == 314 and S.CONTEXT_NIBBLES == 165 and S.WF_CONTEXT_AWARE == 2
    assert S.CELL_WIN_TAIL == 232 and S.CELL_SCR == 233 and S.CELL_SCR_INFO == 235 and S.CELL_SCR_RECT == 236
    assert S.CELL_SCR_CHECK == 254 and S.CELL_SCR_TAIL == 255 and S.SCROLL_MAX == 6
    assert S.CELL_CHAT == 315 and S.CELL_CHAT_INFO == 317 and S.CELL_CHAT_RECT == 318 and S.CELL_CHAT_CHECK == 321 and S.CELL_CHAT_TAIL == 322
    assert S.CHAT_VERSION == 1 and S.CHAT_BUILD == 5 and S.CF_VISIBLE == 1 and S.CF_BOX == 2
    assert S.WIN_VERSION == 8 and S.WF_TRUNCATED == 1 and S.WF_COMBAT == 4 and S.WF_SCROLL_AWARE == 8
    order = [S.SLOTS[i + 1] for i in range(96)]
    assert order[:13] == list(range(1, 13)) + [25] and order[-1] == 180, order

    # pure coding
    assert S.remainingCode(0) == 0 and S.remainingCode(0.04) == 1 and S.remainingCode(12.5) == 125
    assert S.remainingCode(359.94) == 3599 and S.remainingCode(360) == 3600 and S.remainingCode(900) == 3618
    assert S.remainingCode(10 ** 7) == 4095
    assert S.fractionCode(15, 30) == 128 and S.fractionCode(0.001, 30) == 1 and S.fractionCode(40, 30) == 255

    st = g.state
    for slot in order:
        st.actions[slot] = True
    g.firePlayerLogin()
    frame = g.findFrame("TouchForeverStrip")
    assert frame is not None and frame.shown
    assert abs(frame.scale - 768 / 1848) < 1e-9
    assert len(frame.textures) == 323
    # geometry: the first row is exactly the one of build 3 (256 cells, 1024 pixels, 2 pixels high); the 59 context cells and the 8 chat cells stand in a
    # second row right below it (pixel rows 2 and 3) from the same left edge, so the strip is 4 pixels high and never wider than 1024
    assert frame.w == 1024 and frame.h == 4, (frame.w, frame.h)
    for i in range(1, 324):
        t = frame.textures[i]
        if i <= 256:
            assert (t.px, t.py) == ((i - 1) * 4, 0), (i, t.px, t.py)
        else:
            assert (t.px, t.py) == ((i - 257) * 4, -2), (i, t.px, t.py)
    assert max(frame.textures[i].px + 4 for i in range(1, 324)) == 1024
    assert max(frame.textures[i].px + 4 for i in range(257, 324)) == 268

    scenarios = []

    def snapshot(name):
        g.tick(frame, 0.11)
        cells = [tuple(g.cellColor(frame, i)) for i in range(323)]
        scenarios.append((name, cells, decode(cells, order)))
        return scenarios[-1][2]

    # A: idle
    d = snapshot("idle")
    assert d["version"] == 1 and all(s == (0, 0, 1) for s in d["slots"]), d["slots"][:3]
    first_counter = d["counter"]

    # B: various states
    now = st.now
    def idx(slot): return order.index(slot)
    st.cooldowns[1] = lua.table(now - 17.5, 30)          # 12.5 s left of 30
    st.gcd.startTime, st.gcd.duration = now - 0.5, 1.5
    st.cooldowns[30] = lua.table(now - 0.5, 1.5)         # only the GCD
    st.range[61] = False
    st.usable[62] = lua.table(False, True)               # no mana
    st.cooldowns[145] = lua.table(now - 100, 900)        # 800 s left
    st.cooldowns[170] = lua.table(now - 5, 20)           # spell with charges left: not a cooldown
    st.charges[170] = lua.table(1, 2)
    st.cooldowns[40] = lua.table(now - 3590, 3600)       # 10 s left
    d = snapshot("mixed")
    assert d["counter"] == (first_counter + 1) % 16
    s = d["slots"]
    assert s[idx(1)][0] == 125 and s[idx(1)][2] == 1, s[idx(1)]
    assert abs(s[idx(1)][1] - round(12.5 / 30 * 255)) <= 1
    assert s[idx(30)][2] == 1 + 8 and s[idx(30)][0] > 0                  # GCD flag
    assert s[idx(61)][2] == 1 + 4 and s[idx(62)][2] == 1 + 2
    assert remaining_from_code(s[idx(145)][0]) == 810 or abs(remaining_from_code(s[idx(145)][0]) - 800) <= 15
    assert s[idx(170)] == (0, 0, 1)
    assert s[idx(40)][0] == 100

    # C: time passes, the sweep gets shorter
    st.now = now + 2.5
    d = snapshot("later")
    assert d["slots"][idx(1)][0] == 100, d["slots"][idx(1)]

    # D: secret values in combat: the cooldown seen before goes on counting
    st.secretSlots[1] = True
    st.now = now + 5.0
    d = snapshot("secret")
    assert d["slots"][idx(1)][0] == 75 and d["infoFlags"] == 1 + 4 + 8, (d["slots"][idx(1)], d["infoFlags"])  # secret + chat-aware + windows-aware

    # E: cooldown over, slot removed
    st.secretSlots[1] = None
    st.now = now + 40
    st.actions[2] = None
    d = snapshot("over")
    assert d["slots"][idx(1)] == (0, 0, 1) and d["slots"][idx(2)] == (0, 0, 0) and d["infoFlags"] == 4 + 8

    # E2: the chat edit box: the info flag follows the focus of the box (voice text is only typed while it is set)
    lua.execute("""
        ChatFrame1EditBox = { hooks = {}, shown = false, focus = false,
            IsShown = function(self) return self.shown end, HasFocus = function(self) return self.focus end,
            HookScript = function(self, name, fn) self.hooks[name] = fn end }
    """)
    box = g.ChatFrame1EditBox
    d = snapshot("chat closed")
    assert d["infoFlags"] == 4 + 8, d["infoFlags"]
    box.shown, box.focus = True, True
    d = snapshot("chat open")
    assert d["infoFlags"] == 4 + 2 + 8, d["infoFlags"]
    for name in ("OnShow", "OnHide", "OnEditFocusGained", "OnEditFocusLost"):
        assert box.hooks[name] is not None, name
    box.focus = False                                    # shown but without keyboard focus: letters would not go into it
    d = snapshot("chat shown without focus")
    assert d["infoFlags"] == 4 + 8, d["infoFlags"]
    box.shown = False

    # E2b: the chat section (build 5): where the main chat window is (with the open edit box), whether it is visible, whether the box is open
    d = snapshot("chat: no window")
    assert d["chat"] == (0, None), d["chat"]
    chat = g.makeWindow("ChatFrame1", 20, 300, 500, 240)
    chat.visible = True
    chat.IsShown = lambda self: self.visible
    chat.alpha = 1
    chat.GetEffectiveAlpha = lambda self: self.alpha
    def chat_rect(left, bottom, w, h):
        sw, sh = 768 * 2448 / 1848, 768.0
        return (code(left * 0.5 / sw), code(1 - (bottom + h) * 0.5 / sh), code((left + w) * 0.5 / sw), code(1 - bottom * 0.5 / sh))
    def code(v): return int(math.floor(min(1.0, max(0.0, v)) * 255 + 0.5))
    d = snapshot("chat: window visible, box closed")
    assert d["chat"] == (1, chat_rect(20, 300, 500, 240)), d["chat"]
    box.shown, box.focus = True, True
    box.GetRect = lambda self: (20, 240, 500, 30)
    box.GetEffectiveScale = lambda self: 0.5
    d = snapshot("chat: box open below the window")
    assert d["chat"] == (3, chat_rect(20, 240, 500, 300)), d["chat"]       # window and box as one rectangle
    box.GetRect = lambda self: (20, 540, 500, 30)                           # box above the window
    d = snapshot("chat: box open above the window")
    assert d["chat"] == (3, chat_rect(20, 300, 500, 270)), d["chat"]
    box.GetRect = None
    box.GetEffectiveScale = None
    box.shown, box.focus = False, False
    chat.alpha = 0                                                          # faded out (chat hidden): the place is still reported
    d = snapshot("chat: faded out")
    assert d["chat"] == (0, chat_rect(20, 300, 500, 240)), d["chat"]
    chat.alpha = 1
    chat.withheld = True                                                    # the game withholds the rectangle: no rectangle, flags stay
    d = snapshot("chat: place unknown")
    assert d["chat"] == (1, None), d["chat"]
    chat.withheld = False
    chat.visible = False
    d = snapshot("chat: window hidden")
    assert d["chat"][0] == 0 and d["chat"][1] == chat_rect(20, 300, 500, 240), d["chat"]
    chat.visible = True

    # E3: the window section: open big game windows as shares of the game picture
    g.STRIP = S
    lua.execute("""
        local S = STRIP
        assert(S.windowCode(0) == 0 and S.windowCode(1) == 255 and S.windowCode(0.5) == 128 and S.windowCode(-3) == 0 and S.windowCode(7) == 255)
        local out, trunc = S.mergeWindows({
            { l = 0.60, t = 0.50, r = 0.70, b = 0.90, kind = 1 },
            { l = 0.705, t = 0.55, r = 0.80, b = 0.95, kind = 1 },  -- touches the first one (gap <= 0.01): one window
            { l = 0.10, t = 0.10, r = 0.40, b = 0.50, kind = 3 },
            { l = 0.15, t = 0.15, r = 0.35, b = 0.45, kind = 5 },   -- inside the window above: dropped
            { l = 0.00, t = 0.00, r = 0.04, b = 0.50, kind = 5 },   -- too narrow: dropped
        })
        assert(#out == 2 and not trunc, #out)
        assert(out[1].kind == 3 and out[2].kind == 1)                -- the bigger first
        assert(out[2].l == S.windowCode(0.60) and out[2].r == S.windowCode(0.80) and out[2].t == S.windowCode(0.50) and out[2].b == S.windowCode(0.95))
        local none, t2 = S.mergeWindows({})
        assert(#none == 0 and not t2)
    """)

    def code(v): return int(math.floor(min(1.0, max(0.0, v)) * 255 + 0.5))
    def expect(left, bottom, w, h, kind):
        # UIParent has the effective scale 0.5; the screen is 768 * 2448 / 1848 by 768 in the units of scale 1
        sw, sh = 768 * 2448 / 1848, 768.0
        return (code(left * 0.5 / sw), code(1 - (bottom + h) * 0.5 / sh), code((left + w) * 0.5 / sw), code(1 - bottom * 0.5 / sh), kind)
    def same(got, want, name):
        assert len(got) == len(want), (name, got, want)
        for a, b in zip(got, want):
            assert all(abs(x - y) <= 1 for x, y in zip(a[:4], b[:4])) and a[4] == b[4], (name, got, want)

    d = snapshot("windows none")
    assert d["windows"] == (0, []), d["windows"]
    bags = g.makeWindow("ContainerFrameCombinedBags", 1400, 60, 520, 700)
    bags.visible = True
    st.now += 0.3                                         # the periodic scan (every 0.25 s) finds it
    d = snapshot("bags open")
    same(d["windows"][1], [expect(1400, 60, 520, 700, 1)], "bags")
    assert d["windows"][0] == 0
    assert bags.hooks["OnShow"] and bags.hooks["OnHide"] and bags.hooks["OnSizeChanged"]
    merchant = g.makeWindow("MerchantFrame", 200, 100, 600, 900)       # exists but is hidden: the next scan hooks it
    worldmap = g.makeWindow("WorldMapFrame", 100, 100, 1800, 1300)
    st.now += 0.3
    d = snapshot("frames exist")
    assert len(d["windows"][1]) == 1 and merchant.hooks["OnShow"] and worldmap.hooks["OnShow"], d["windows"]
    merchant.visible = True
    d = snapshot("vendor shown, no hook yet")              # no time passed and the hook did not fire: still the old list
    assert len(d["windows"][1]) == 1, d["windows"]
    merchant.hooks["OnShow"]()                             # the hook asks for a scan at once
    d = snapshot("vendor and bags")
    same(d["windows"][1], [expect(200, 100, 600, 900, 5), expect(1400, 60, 520, 700, 1)], "vendor+bags")
    worldmap.visible = True
    worldmap.hooks["OnShow"]()
    d = snapshot("map and bags")                           # the vendor lies inside the map: one rectangle less
    same(d["windows"][1], [expect(100, 100, 1800, 1300, 2), expect(1400, 60, 520, 700, 1)], "map+bags")
    worldmap.withheld = True                               # the game withholds the rectangle (combat): the last good one stays
    st.now += 0.3
    d = snapshot("map withheld")
    same(d["windows"][1], [expect(100, 100, 1800, 1300, 2), expect(1400, 60, 520, 700, 1)], "withheld")
    worldmap.visible, merchant.visible, bags.visible = False, False, False
    st.now += 0.3
    d = snapshot("windows closed")
    assert d["windows"] == (0, []), d["windows"]
    loot = g.makeWindow("LootFrame", 500, 500, 60, 100)    # too small to matter
    loot.visible = True
    st.now += 0.3
    d = snapshot("small window")
    assert d["windows"] == (0, []), d["windows"]
    loot.visible = False
    five = [("CharacterFrame", 50, 1000, 500, 450), ("GameMenuFrame", 700, 1000, 510, 450), ("MerchantFrame", 1400, 1000, 520, 450),
            ("FriendsFrame", 50, 100, 480, 450), ("WorldMapFrame", 700, 100, 600, 500)]
    for name, left, bottom, w, h in five:
        win = g.makeWindow(name, left, bottom, w, h)
        win.visible = True
    st.now += 0.3
    d = snapshot("five windows")
    assert d["windows"][0] == 1 and len(d["windows"][1]) == 4, d["windows"]    # truncated: the smallest is left out
    assert d["windows"][1][0][4] == 2                                          # the map is the biggest
    for name, *_ in five:
        g[name].visible = False
    st.now += 0.3
    saved_width = g.GetScreenWidth
    g.GetScreenWidth = None                                # cannot measure: the strip does not claim to know about windows
    d = snapshot("windows unavailable")
    assert d["infoFlags"] == 4 and d["windows"] is None, (d["infoFlags"], d["windows"])
    g.GetScreenWidth = saved_width
    st.now += 0.3
    d = snapshot("windows back")
    assert d["infoFlags"] == 4 + 8 and d["windows"] == (0, []), (d["infoFlags"], d["windows"])

    # E4: the cases from the device (character, professions book and combined bags, the maximised map), the build number
    # in the section version and the combat flag
    assert LAST_WINDOW_VERSION[0] == 8 and S.WIN_VERSION == 8
    bags.visible = True
    char = g.makeWindow("CharacterFrame", 60, 200, 700, 1200)          # created after the login: found by the periodic scan
    char.visible = True
    st.now += 0.3
    d = snapshot("character and bags")
    same(d["windows"][1], [expect(60, 200, 700, 1200, 3), expect(1400, 60, 520, 700, 1)], "character+bags")
    book = g.makeWindow("ProfessionsBookFrame", 800, 300, 500, 900)  # load-on-demand window, opened later
    book.visible = True
    st.now += 0.3
    d = snapshot("professions and bags")
    assert len(d["windows"][1]) == 3 and sorted(r[4] for r in d["windows"][1]) == [1, 3, 3], d["windows"]
    bigmap = g.makeWindow("WorldMapFrame", 0, 0, 768 * 2448 / 1848 / 0.5, 768 / 0.5)   # maximised: the whole picture
    bigmap.visible = True
    st.now += 0.3
    d = snapshot("maximised map")
    assert len(d["windows"][1]) == 1 and d["windows"][1][0][:4] == (0, 0, 255, 255) and d["windows"][1][0][4] == 2, d["windows"]
    bigmap.visible, char.visible, book.visible, bags.visible = False, False, False, False
    assert d["windows"][0] & 4 == 0
    g.inCombat = True
    st.now += 0.3
    d = snapshot("combat")
    assert d["windows"][0] & 4 == 4 and d["windows"][1] == [], d["windows"]
    g.inCombat = False
    st.now += 0.3
    d = snapshot("combat over")
    assert d["windows"][0] == 0, d["windows"]
    lines, reported, build = S.describeWindows()
    assert build == 8 and reported == 0 and len(lines) == 1 and "no window" in lines[1], (list(lines.values()), reported, build)

    # E4b: the small dialogs on the left (quest giver, gossip, vendor) as seen on a foldable: x 30..690 / y 220..1190 of a
    # 2000 x 1510 picture. Every one of them must be in the strip, whatever its group (kind) is.
    def shares(l, t, r, b):
        sw, sh = 768 * 2448 / 1848, 768.0
        return l * sw / 0.5, (1 - b) * sh / 0.5, (r - l) * sw / 0.5, (b - t) * sh / 0.5
    dl, dt, dr, db_ = 30 / 2000.0, 220 / 1510.0, 690 / 2000.0, 1190 / 1510.0
    dialog = lambda kind: [(code(dl), code(dt), code(dr), code(db_), kind)]
    for name, kind, label in (("QuestFrame", 5, "quest dialog left"), ("GossipFrame", 5, "gossip"), ("MerchantFrame", 5, "merchant")):
        win = g.makeWindow(name, *shares(dl, dt, dr, db_))
        win.visible = True
        st.now += 0.3
        d = snapshot(label)
        same(d["windows"][1], dialog(kind), name)
        assert d["windows"][0] == 0 and (name == "MerchantFrame" or (win.hooks["OnShow"] and win.hooks["OnHide"])), name
        lines, reported, build = S.describeWindows()
        listed = [lines[i] for i in range(1, len(lines) + 1)]
        assert reported == 1 and any(l.startswith(name + " (kind 5): 0.01 0.15 0.34 0.79") for l in listed), (listed, reported)
        assert any(l.startswith("strip 1: %d %d %d %d (kind 5)" % dialog(kind)[0][:4]) for l in listed), listed
        win.visible = False
        st.now += 0.3
        d = snapshot(label + " closed")
        assert d["windows"] == (0, []), (name, d["windows"])
    # a window of this client that is not on our list but registered with the game as a panel (UIPanelWindows) is found as well
    lua.execute("UIPanelWindows = { MysteryTalkFrame = { area = 'left', pushable = 0 }, QuestFrame = { area = 'left' }, UIParentPanelContainer = {} }")
    mystery = g.makeWindow("MysteryTalkFrame", *shares(dl, dt, dr, db_))
    mystery.visible = True
    st.now += 0.3
    d = snapshot("panel not on the list")
    same(d["windows"][1], dialog(0), "panel")
    assert mystery.hooks["OnShow"]
    lines, reported, build = S.describeWindows()
    assert any(lines[i].startswith("MysteryTalkFrame (kind 0) [panel]: 0.01 0.15") for i in range(1, len(lines) + 1)), list(lines.values())
    mystery.visible = False
    st.now += 0.3
    d = snapshot("panel closed")
    assert d["windows"] == (0, []), d["windows"]
    lua.execute("UIPanelWindows = nil")
    # a window that is open but too small to be reported is still listed with the reason
    tiny = g.makeWindow("LootFrame", 500, 500, 60, 100)
    tiny.visible = True
    lines, reported, build = S.describeWindows()
    assert any("LootFrame" in lines[i] and "too small" in lines[i] for i in range(1, len(lines) + 1)), list(lines.values())
    tiny.visible = False
    st.now += 0.3
    snapshot("dialogs done")

    # E4c: the scroll section (build 3): scrollable lists inside the open windows, where the app turns a swipe into wheel ticks
    lua.execute("""
        local S = STRIP
        local out, trunc = S.mergeScrollAreas({
            { l = 0.10, t = 0.10, r = 0.60, b = 0.90, kind = 1 },
            { l = 0.101, t = 0.10, r = 0.60, b = 0.90, kind = 2 },  -- the same list (a ScrollBox and the frame around it): one
            { l = 0.20, t = 0.30, r = 0.40, b = 0.50, kind = 2 },   -- nested and smaller: first
            { l = 0.70, t = 0.10, r = 0.72, b = 0.50, kind = 1 },   -- too narrow
            { l = 0.70, t = 0.10, r = 0.90, b = 0.12, kind = 1 },   -- too flat
        })
        assert(#out == 2 and not trunc, #out)
        assert(out[1].kind == 2 and out[2].kind == 1)
        assert(out[2].l == S.windowCode(0.10) and out[2].b == S.windowCode(0.90) and out[1].t == S.windowCode(0.30))
        local none, t0 = S.mergeScrollAreas({})
        assert(#none == 0 and not t0)
        local many = {}
        for i = 1, 8 do many[i] = { l = 0.05, t = 0.05, r = 0.05 + 0.04 * i, b = 0.95, kind = 1 } end
        local six, cut = S.mergeScrollAreas(many)
        assert(#six == 6 and cut)                                          -- the six biggest are kept, smallest of them first
        assert(six[1].r == S.windowCode(0.05 + 0.04 * 3) and six[6].r == S.windowCode(0.05 + 0.04 * 8))
    """)

    def scroll_of(d):
        return d["scroll"]

    def scroll_same(got, want, name):
        assert got is not None, name
        same(got[1], want, name)

    def close_all(*frames):
        for f in frames:
            f.visible = False
        st.now += 0.6

    # no window open: the section is there (flag set) and empty
    st.now += 0.6
    d = snapshot("scroll: no window")
    assert d["scroll"] == (0, []) and d["windowFlagsRaw"] & 8, d["scroll"]

    # reputation list in the character window (a ScrollBox inside a plain frame inside the window)
    char = g.makeWindow("CharacterFrame", 60, 200, 700, 1200)
    char.visible = True
    repframe = g.makeChild(char, "plain", 80, 220, 660, 1100, False)
    repbox = g.makeChild(repframe, "scrollbox", 100, 300, 600, 800, True)
    st.now += 0.3
    d = snapshot("scroll: reputation list")
    scroll_same(scroll_of(d), [expect(100, 300, 600, 800, 1)], "reputation")
    assert scroll_of(d)[0] == 0
    # the content gets shorter than the list: no swipe area any more (found at the next periodic scan, half a second)
    repbox.HasScrollableExtent = lua.eval("function() return false end")
    st.now += 0.6
    d = snapshot("scroll: nothing to scroll")
    assert d["scroll"] == (0, []), d["scroll"]
    repbox.HasScrollableExtent = lua.eval("function() return true end")
    st.now += 0.6
    d = snapshot("scroll: reputation list again")
    scroll_same(scroll_of(d), [expect(100, 300, 600, 800, 1)], "reputation again")
    # the window closes: the area is gone at once (the set of open windows changed)
    char.visible = False
    st.now += 0.3
    d = snapshot("scroll: window closed")
    assert d["scroll"] == (0, []), d["scroll"]

    # settings: two lists side by side, smallest first
    panel = g.makeWindow("SettingsPanel", 100, 100, 1800, 1300)
    panel.visible = True
    categories = g.makeChild(panel, "scrollbox", 120, 150, 400, 1100, True)
    settingsbox = g.makeChild(panel, "scrollbox", 560, 150, 1300, 1100, True)
    st.now += 0.3
    d = snapshot("scroll: settings two lists")
    scroll_same(scroll_of(d), [expect(120, 150, 400, 1100, 1), expect(560, 150, 1300, 1100, 1)], "settings")
    close_all(panel)

    # quest log: a legacy ScrollFrame inside the map window
    worldmap = g.makeWindow("WorldMapFrame", 100, 100, 1800, 1300)
    worldmap.visible = True
    questmap = g.makeChild(worldmap, "plain", 1380, 120, 500, 1200, False)
    questlist = g.makeChild(questmap, "scrollframe", 1400, 200, 450, 1000, True)
    st.now += 0.3
    d = snapshot("scroll: quest log")
    scroll_same(scroll_of(d), [expect(1400, 200, 450, 1000, 2)], "quest log")
    close_all(worldmap)

    # nested: a ScrollFrame inside a ScrollBox inside a window; the inner one is first
    helpframe = g.makeWindow("HelpFrame", 300, 300, 1200, 800)
    helpframe.visible = True
    outer = g.makeChild(helpframe, "scrollbox", 320, 320, 1160, 760, True)
    target = g.makeChild(outer, "plain", 320, 320, 1160, 1500, False)
    inner = g.makeChild(target, "scrollframe", 500, 500, 500, 300, True)
    st.now += 0.3
    d = snapshot("scroll: nested lists")
    scroll_same(scroll_of(d), [expect(500, 500, 500, 300, 2), expect(320, 320, 1160, 760, 1)], "nested")
    close_all(helpframe)

    # nothing to scroll: short content, a horizontal list, a hidden list, a list that is too small
    config = g.makeWindow("ChatConfigFrame", 300, 300, 1200, 800)
    config.visible = True
    g.makeChild(config, "scrollbox", 320, 320, 500, 300, False)
    g.makeChild(config, "scrollframe", 900, 320, 500, 300, False)
    g.makeChild(config, "scrollbox", 320, 700, 500, 300, True, lua.table(horizontal=True))
    hidden = g.makeChild(config, "scrollbox", 900, 700, 500, 300, True)
    hidden.shown = False
    g.makeChild(config, "scrollbox", 1450, 320, 20, 300, True)
    st.now += 0.3
    d = snapshot("scroll: nothing to scroll here")
    assert d["scroll"] == (0, []), d["scroll"]
    # a chat style list (ScrollingMessageFrame) with more lines than it shows
    chatlist = g.makeChild(config, "smf", 900, 320, 500, 300, True)
    st.now += 0.6
    d = snapshot("scroll: message frame")
    scroll_same(scroll_of(d), [expect(900, 320, 500, 300, 3)], "message frame")
    close_all(config)

    # a ScrollBox and the ScrollFrame around it with the same rectangle are one area
    gm = g.makeWindow("GameMenuFrame", 400, 400, 900, 700)
    gm.visible = True
    sframe = g.makeChild(gm, "scrollframe", 420, 420, 860, 650, True)
    g.makeChild(sframe, "scrollbox", 420, 420, 860, 650, True)
    st.now += 0.3
    d = snapshot("scroll: same list twice")
    assert len(d["scroll"][1]) == 1, d["scroll"]
    close_all(gm)

    # more lists than fit: the six biggest, smallest of them first, truncated flag
    ac = g.makeWindow("AddonList", 100, 100, 1800, 1300)
    ac.visible = True
    for i in range(8):
        g.makeChild(ac, "scrollbox", 120 + 5 * i, 150, 100 + 60 * i, 1100, True)
    st.now += 0.3
    d = snapshot("scroll: eight lists")
    assert d["scroll"][0] & 1 == 1 and len(d["scroll"][1]) == 6, d["scroll"]
    widths = [r[2] - r[0] for r in d["scroll"][1]]
    assert widths == sorted(widths), widths
    close_all(ac)

    # a window full of small frames (a map with hundreds of pins): the list near the top is still found (breadth first, shared budget)
    pve = g.makeWindow("PVEFrame", 100, 100, 1800, 1300)
    pve.visible = True
    lua.execute("""
        local pins = makeChild(PVEFrame, "plain", 100, 100, 1800, 1300, false)
        for i = 1, 40 do
            local group = makeChild(pins, "plain", 100, 100, 50, 50, false)
            for j = 1, 100 do makeChild(group, "plain", 100, 100, 5, 5, false) end   -- 4000 small frames, more than the budget
        end
        makeChild(PVEFrame, "scrollbox", 1300, 200, 500, 900, true)                 -- the list is the LAST child of the window
    """)
    st.now += 0.3
    d = snapshot("scroll: crowded window")
    scroll_same(scroll_of(d), [expect(1300, 200, 500, 900, 1)], "crowded window")
    close_all(pve)

    # build 6: the addon has no switch any more, the lists are always reported (the app decides whether a swipe scrolls); an old
    # saved value settings.scroll = false does not switch the reporting off
    char.visible = True
    st.now += 0.3
    d = snapshot("scroll: before the old switch value")
    assert len(d["scroll"][1]) == 1
    assert not hasattr(S, "scrollSet") or S.scrollSet is None, "the addon switch is gone"
    lua.execute("TouchForeverDB = TouchForeverDB or {}; TouchForeverDB.settings = TouchForeverDB.settings or {}; TouchForeverDB.settings.scroll = false")
    st.now += 0.6
    d = snapshot("scroll: old switch value false")
    assert d["scroll"] is not None and len(d["scroll"][1]) == 1, d["scroll"]
    assert d["windowFlagsRaw"] & 8 == 8, d["windowFlagsRaw"]
    lines, reported, build = S.describeWindows()
    assert not any("swipe scrolling is off" in lines[i] for i in range(1, len(lines) + 1)), list(lines.values())
    d = snapshot("scroll: switched on again")
    assert len(d["scroll"][1]) == 1, d["scroll"]
    lines, reported, build = S.describeWindows()
    assert any(lines[i].startswith("scroll 1: ") for i in range(1, len(lines) + 1)), list(lines.values())
    close_all(char)
    # no window information (cannot measure): no scroll section either
    char.visible = True
    st.now += 0.3
    saved_width = g.GetScreenWidth
    g.GetScreenWidth = None
    d = snapshot("scroll: windows unavailable")
    assert d["scroll"] is None and d["windows"] is None, (d["scroll"], d["windows"])
    g.GetScreenWidth = saved_width
    close_all(char)
    d = snapshot("scroll: done")
    assert d["scroll"] == (0, []), d["scroll"]

    # E5: the reload key (TouchForever.lua): a hidden secure button with the /reload macro and the override binding CTRL-SHIFT-F4
    ns2 = lua.table()
    g.inCombat = True                                       # login in combat: nothing is set, the end of combat does it
    g.loadAddonFile(locale_src, ns2)
    g.loadAddonFile((ADDON / "TouchForever.lua").read_text(encoding="utf-8"), ns2)
    g.firePlayerLogin()
    assert len(g.overrideBindings) == 0
    g.inCombat = False
    g.fireEvent("PLAYER_REGEN_ENABLED")
    keyButton = g.findFrame("TouchForeverReloadKey")
    assert keyButton is not None and keyButton.GetAttribute(keyButton, "type") == "macro" and keyButton.GetAttribute(keyButton, "macrotext") == "/reload"
    assert len(g.overrideBindings) == 1, len(g.overrideBindings)
    ob = g.overrideBindings[1]
    assert ob.key == "CTRL-SHIFT-F4" and ob.button == "TouchForeverReloadKey" and ob.mouseButton == "LeftButton" and ob.priority
    assert ns2.RELOAD_KEY == "CTRL-SHIFT-F4"
    g.firePlayerLogin()
    assert len(g.overrideBindings) == 2                     # set again (idempotent), the button is not created twice
    assert sum(1 for f in [g.findFrame("TouchForeverReloadKey")] if f) == 1


    # E6: the context section (build 4): unit under the pointer, item slot under the pointer, spell lists, keys, throwing away
    lua.execute("overrideBindings = {}")
    lua.execute(r"""
        ctx = { unit = {}, target = nil, combat = false, cursor = nil, deleted = 0, cleared = 0, popups = {}, picked = nil,
                tooltip = {}, foci = nil, cvars = { alwaysCompareItems = "0" }, merchant = false }
        function UnitExists(u) if u == "mouseover" then return ctx.unit.exists and true or false end
            if u == "target" then return ctx.target ~= nil end return false end
        function UnitIsUnit(a, b)
            if a == "mouseover" and b == "player" then return ctx.unit.self == true end
            if (a == "mouseover" and b == "target") or (a == "target" and b == "mouseover") then return ctx.unit.exists and ctx.target == ctx.unit.guid end
            return a == b
        end
        function UnitIsPlayer(u) if u == "target" then return ctx.targetPlayer == true end return ctx.unit.player == true end
        function UnitIsDeadOrGhost(u) return ctx.unit.dead == true end
        function UnitReaction(u, o) return ctx.unit.reaction end
        function UnitCanAttack(a, u) return ctx.unit.attackable == true end
        function UnitInParty(u) return ctx.unit.group == true end
        function UnitInRaid(u) return false end
        function CheckInteractDistance(u, i) local d = ctx.unit.distance; if d == nil then return nil end return d <= ({ [1] = 28, [2] = 11.11, [3] = 9.9, [4] = 28 })[i] end
        function GetUnitName(u, full) return "Anna-Realm" end
        UNIT_SKINNABLE_LEATHER = "Skinnable"
        C_TooltipInfo = { GetUnit = function(u) return { lines = ctx.tooltip } end }
        function GetMouseFoci() if ctx.foci then return { ctx.foci } end return {} end
        function CursorHasItem() return ctx.cursor ~= nil end
        MerchantFrame = { IsShown = function() return ctx.merchant end }
        function GetCVar(n) return ctx.cvars[n] end
        function SetCVar(n, v) ctx.cvars[n] = v end
        C_Container = {
            GetContainerItemInfo = function(bag, slot) local it = ctx.items and ctx.items[bag .. ":" .. slot]; if it then return { hyperlink = it.link, stackCount = it.count } end end,
            PickupContainerItem = function(bag, slot) local it = ctx.items[bag .. ":" .. slot]; ctx.cursor = it and it.link; ctx.picked = bag .. ":" .. slot end,
        }
        function IsEquippableItem(link) return link == "|Hitem:sword|h[Sword]|h" end
        function GetItemSpell(link) if link == "|Hitem:potion|h[Potion]|h" then return "Heal" end return nil end
        function GetCursorInfo() if ctx.cursor then return "item", 1, ctx.cursor end end
        function ClearCursor() ctx.cursor = nil; ctx.cleared = ctx.cleared + 1 end
        function DeleteCursorItem() ctx.deleted = ctx.deleted + 1; ctx.cursor = nil end
        StaticPopupDialogs = {}
        function StaticPopup_Show(name, text) ctx.popups[#ctx.popups + 1] = { name = name, text = text } end
        -- the spellbook of the mage: ranks, a passive, a profession, helpful spells
        local book = {
            { id = 100, name = "Fireball", sub = "Rank 1" }, { id = 101, name = "Fireball", sub = "Rank 2" },
            { id = 102, name = "Frostbolt", sub = "Rank 1" }, { id = 103, name = "Quickness", sub = "Passive", passive = true },
            { id = 200, name = "Heal", sub = "Rank 1" }, { id = 201, name = "Heal", sub = "Rank 2" },
            { id = 202, name = "Blessing", sub = "Rank 1" }, { id = 300, name = "Mining", sub = "Apprentice" },
            { id = 150, name = "Stoneform", sub = "Racial" }, { id = 151, name = "Track Beasts", sub = "" }, { id = 152, name = "Frost Nova", sub = "Rank 1" },
        }
        ctx.book = book
        C_SpellBook = {
            GetNumSpellBookSkillLines = function() return 1 end,
            GetSpellBookSkillLineInfo = function() return { itemIndexOffset = 0, numSpellBookItems = #ctx.book } end,
            GetSpellBookItemInfo = function(i) local b = ctx.book[i]; return { itemType = 1, spellID = b.id, name = b.name, subName = b.sub, isPassive = b.passive or false } end,
        }
        ctx.harmful = { [100] = true, [101] = true, [102] = true, [152] = true }
        ctx.helpful = { [200] = true, [201] = true, [202] = true, [150] = true, [151] = true }
        ctx.noRange = { [150] = true, [151] = true, [152] = true }          -- self buffs, tracking, a spell around the caster
        ctx.range = {}
        ctx.unusable = {}
        ctx.cd = {}
        local gcdApi = C_Spell
        C_Spell = {
            GetSpellCooldown = function(id)
                local c = ctx.cd[id]
                if c then return { startTime = c[1], duration = c[2] } end
                return gcdApi.GetSpellCooldown(id)
            end,
            IsSpellHarmful = function(id) return ctx.harmful[id] or false end,
            IsSpellHelpful = function(id) return ctx.helpful[id] or false end,
            IsSpellUsable = function(id) return not ctx.unusable[id], false end,
            IsSpellInRange = function(id, u) return ctx.range[id] end,
            SpellHasRange = function(id) return not ctx.noRange[id] end,
            GetSpellName = function(id) for _, b in ipairs(ctx.book) do if b.id == id then return b.name end end end,
        }
        GetActionInfo = function(slot)
            if slot == 1 then return "spell", 101 end
            if slot == 2 then return "spell", 201 end
            if slot == 3 then return "macro", 9 end
            return nil
        end
        GetMacroSpell = function(id) if id == 9 then return 102 end end
    """)
    ctxns = g.lua_ns_context = lua.table()
    ctxns.strip = S
    g.loadAddonFile(locale_src, ctxns)
    g.loadAddonFile((ADDON / "TouchForeverContext.lua").read_text(encoding="utf-8"), ctxns)
    X = ctxns.context
    ns.context = X                                   # the strip finds it through the shared table
    assert X.NIBBLES == 165 and X.SPELLS == 8 and X.ENTRIES == 16

    # pure coding
    assert X.cooldownCode(0) == 0 and X.cooldownCode(0.04) == 1 and X.cooldownCode(10) == 100 and X.cooldownCode(25) == 115
    assert X.cooldownCode(1000) == 255
    pure = X.nibbles(lua.eval("""{ flags = 3, unit = { exists = true, player = true, dead = false, skinnable = true,
        relation = 2, source = 1, reach = 3, group = true, reaction = 2, hasTarget = true, isTarget = true, isSelf = false },
        focus = { kind = 2, filled = true, equippable = true, usable = false, stacked = true, merchant = true, cursor = true, count = 7 },
        bar = { 1, 2, 3, 0, 0, 0, 0, 0, 0, 0, 0, 3 },
        entries = { [1] = { id = 1048577, flags = 1 + 4, cd = 25 }, [16] = { id = 77, flags = 1, cd = 0.3 } } }"""))
    pure = [pure[i] for i in range(1, 166)]
    assert len(pure) == 165 and pure[0] == 1 and pure[2] == 3
    assert pure[3] == 1 + 2 + 8 and pure[4] == 2 + 4 * 1 and pure[5] == 3 + 8 and pure[6] == 2 and pure[7] == 1 + 2 and pure[8] == 0
    assert pure[9] == 2 and pure[10] == 1 + 2 + 8 and pure[11] == 1 + 2 and pure[12] == 7           # focus
    assert pure[15] == 1 + 4 * 2 and pure[16] == 3 and pure[17] == 0 and pure[20] == 4 * 3        # slots 1+2, 3+4, ..., 11+12
    e1 = pure[21:30]
    assert e1 == [1, 0, 0, 0, 0, 1, 5, 7, 3], e1               # 1048577 = 0x100001, flags 1+4, cooldown code 115 = 0x73
    e16 = pure[21 + 9 * 15:21 + 9 * 16]
    assert e16 == [0, 0, 0, 0, 4, 13, 1, 0, 3], e16            # id 77 = 0x4D, cooldown 0.3 s = code 3

    # login: buttons, bindings, lists
    g.firePlayerLogin()
    bound = {}
    for i in range(1, len(g.overrideBindings) + 1):
        ob = g.overrideBindings[i]
        assert ob.priority and ob.mouseButton == "LeftButton"
        bound[ob.key] = ob.button
    for i, key in enumerate(["ALT-CTRL-F%d" % n for n in range(5, 13)], start=1):
        assert bound[key] == "TouchForeverCtxSpell%d" % i, (key, bound.get(key))
    expected_keys = {"attack": "ALT-SHIFT-F1", "popup": "ALT-SHIFT-F2", "whisper": "ALT-SHIFT-F3", "invite": "ALT-SHIFT-F4",
                     "follow": "ALT-SHIFT-F5", "trade": "ALT-SHIFT-F6", "inspect": "ALT-SHIFT-F7", "friend": "ALT-SHIFT-F8",
                     "clearcursor": "ALT-SHIFT-F9", "link": "ALT-SHIFT-F10", "compare": "ALT-SHIFT-F11", "discard": "ALT-SHIFT-F12"}
    for action, key in expected_keys.items():
        assert X.KEYS[action] == key, action
        assert bound[key] == "TouchForeverCtx" + action[0].upper() + action[1:], (action, bound.get(key))
    ours = ["ALT-CTRL-F%d" % n for n in range(5, 13)] + list(expected_keys.values())
    assert len(set(ours)) == 20 and all(k in bound for k in ours)                # 8 spell keys + 12 actions (6 of them for the mini menu), no key twice
    assert sum(1 for i in range(1, len(g.overrideBindings) + 1) if g.overrideBindings[i].key in ours) == 20
    assert [X.ACTIONS[i] for i in range(1, len(X.ACTIONS) + 1)] == list(expected_keys.keys())   # same order as ContextKeys.ACTION_KEYS
    assert g.findFrame("TouchForeverCtxFollow").GetAttribute(g.findFrame("TouchForeverCtxFollow"), "macrotext") == "/follow"
    btn = g.findFrame("TouchForeverCtxSpell1")
    assert btn.GetAttribute(btn, "type") == "macro"
    assert btn.GetAttribute(btn, "macrotext") == "/cast [@target,harm,nodead] Fireball; [@target,help,nodead] Heal", btn.GetAttribute(btn, "macrotext")
    btn2 = g.findFrame("TouchForeverCtxSpell2")
    assert btn2.GetAttribute(btn2, "macrotext") == "/cast [@target,harm,nodead] Frostbolt; [@target,help,nodead] Blessing"
    btn3 = g.findFrame("TouchForeverCtxSpell3")
    assert btn3.GetAttribute(btn3, "macrotext") == ""
    assert g.findFrame("TouchForeverCtxAttack").GetAttribute(g.findFrame("TouchForeverCtxAttack"), "macrotext") == "/startattack"
    popup = g.findFrame("TouchForeverCtxPopup")                         # the game's own menu of the target: a secure button of type togglemenu
    assert popup.GetAttribute(popup, "type") == "togglemenu" and popup.GetAttribute(popup, "unit") == "target"
    assert popup.hooks.PostClick is not None
    assert g.ctx.cvars.alwaysCompareItems == "1"                        # the comparison is always shown
    # highest rank only, no passive, no profession; harmful to the enemy list, helpful to the friend list
    pick_e, pick_f, cut = X.pickLists(lua.eval("{ {id=1,name='A',rank=1}, {id=2,name='A',rank=3}, {id=3,name='A',rank=2} }"),
                                      lua.eval("function(s) return true end"), lua.eval("function(s) return false end"))
    assert len(pick_e) == 1 and pick_e[1].id == 2 and len(pick_f) == 0 and not cut

    def snap_ctx(name):
        d = snapshot(name)
        assert d["context"] is not None and d["windowFlagsRaw"] & 2, name
        return d["context"]

    def fields(n):
        return {"flags": n[2], "exists": n[3] & 1, "player": (n[3] >> 1) & 1, "dead": (n[3] >> 2) & 1, "skin": (n[3] >> 3) & 1,
                "relation": n[4] & 3, "source": n[4] >> 2, "reach": n[5] & 7, "group": (n[5] >> 3) & 1, "reaction": n[6], "tflags": n[7],
                "kind": n[9], "fflags": n[10], "mflags": n[11], "count": n[12], "attack": (n[7] >> 3) & 1}

    n = snap_ctx("context: nothing")
    f = fields(n)
    assert f["flags"] == 1 + 2 and f["exists"] == 0 and f["kind"] == 0 and f["source"] == 0, f   # keys armed, lists known
    # entries: enemy list = Fireball (rank 2, id 101) and Frostbolt; friend list = Heal (201) and Blessing
    def entry(n, e):
        b = 21 + 9 * e
        return (sum(n[b + i] << (4 * (5 - i)) for i in range(6)), n[b + 6], n[b + 7] * 16 + n[b + 8])
    assert entry(n, 0)[0] == 101 and entry(n, 1)[0] == 102 and entry(n, 2) == (0, 0, 0)
    assert entry(n, 8)[0] == 201 and entry(n, 9)[0] == 202
    assert entry(n, 0)[1] == 1 and entry(n, 8)[1] == 1                # filled only: usable, range unknown (nil)
    # Stoneform, Track Beasts (helpful) and Frost Nova (harmful) have no range: they are in no list
    ids = [entry(n, e)[0] for e in range(16)]
    assert 150 not in ids and 151 not in ids and 152 not in ids, ids
    bar = []
    for k in range(6):
        bar += [n[15 + k] & 3, n[15 + k] >> 2]
    assert bar[:3] == [1, 2, 1], bar                                  # slot 1 Fireball harmful, slot 2 Heal helpful, slot 3 macro -> Frostbolt harmful

    # the unit under the pointer
    ctx_ = g.ctx
    ctx_.unit = lua.table(exists=True, player=False, dead=False, reaction=2, distance=5, guid="m1", attackable=True)
    ctx_.target = "m1"
    ctx_.range[101] = True
    ctx_.range[102] = False
    ctx_.unusable[201] = True
    ctx_.cd[101] = lua.table(st.now - 5, 30)                          # 25 s left
    ctx_.cd[102] = lua.table(st.now - 0.5, 1.5)                       # only the global cooldown
    n = snap_ctx("context: hostile mob")
    f = fields(n)
    assert f["exists"] == 1 and f["player"] == 0 and f["dead"] == 0 and f["relation"] == 2 and f["source"] == 1 and f["reach"] == 4, f
    assert f["reaction"] == 2 and f["tflags"] & 3 == 3                # has target, is target
    assert f["attack"] == 1, f
    assert entry(n, 0) == (101, 1, 115), entry(n, 0)                  # 25 s -> code 115
    assert entry(n, 1)[1] == 1 + 4 + 8 and entry(n, 1)[2] == 0, entry(n, 1)   # out of range, global cooldown only
    assert entry(n, 8)[1] == 1 + 2, entry(n, 8)                        # not usable (heal)

    # a client that cannot say whether a spell has a range: the answer against the target decides (no answer = cannot be cast on it)
    lua.execute("ctx.saveHasRange = C_Spell.SpellHasRange; C_Spell.SpellHasRange = nil")
    g.fireEvent("SPELLS_CHANGED")
    n = snap_ctx("context: no range information")
    ids = [entry(n, e)[0] for e in range(16)]
    assert 101 in ids and 102 in ids, ids                              # answers 1 and 0 (out of range): castable
    assert 150 not in ids and 151 not in ids and 152 not in ids and 201 not in ids, ids   # no answer with a target: dropped
    lua.execute("C_Spell.SpellHasRange = ctx.saveHasRange")
    g.fireEvent("SPELLS_CHANGED")

    ctx_.unit = lua.table(exists=True, player=False, dead=True, reaction=2, distance=5, guid="m1")
    ctx_.tooltip = lua.eval('{ { leftText = "Boar" }, { leftText = "Skinnable" } }')
    n = snap_ctx("context: dead skinnable mob")
    f = fields(n)
    assert f["dead"] == 1 and f["skin"] == 1 and f["relation"] == 2, f
    ctx_.tooltip = lua.eval('{ { leftText = "Boar" } }')
    n = snap_ctx("context: dead mob")
    assert fields(n)["skin"] == 0

    ctx_.unit = lua.table(exists=True, player=True, dead=False, reaction=5, distance=20, group=False, guid="p1")
    ctx_.target = None
    n = snap_ctx("context: friendly player")
    f = fields(n)
    assert f["player"] == 1 and f["relation"] == 1 and f["reach"] == 2 and f["group"] == 0 and f["tflags"] & 3 == 0, f
    ctx_.unit = lua.table(exists=True, player=True, dead=False, reaction=5, distance=3, group=True, guid="p1")
    n = snap_ctx("context: friendly player in group, close")
    f = fields(n)
    assert f["reach"] == 4 and f["group"] == 1
    ctx_.unit = lua.table(exists=True, player=False, dead=False, reaction=4, distance=None, guid="n1")
    n = snap_ctx("context: neutral npc, distance unknown")
    f = fields(n)
    assert f["relation"] == 3 and f["reach"] == 0, f
    assert f["attack"] == 0, f                                          # a neutral NPC that cannot be attacked (vendor, quest giver)
    ctx_.unit = lua.table(exists=True, player=False, dead=False, reaction=4, distance=8, guid="n3", attackable=True)
    f = fields(snap_ctx("context: yellow mob that can be attacked"))
    assert f["relation"] == 2 and f["reaction"] == 4 and f["attack"] == 1, f   # attackable beats "neutral": it gets the attack menu
    ctx_.unit = lua.table(exists=True, player=False, dead=False, reaction=6, distance=50, guid="n2")
    n = snap_ctx("context: friendly npc far")
    f = fields(n)
    assert f["relation"] == 1 and f["reach"] == 1 and f["attack"] == 0, f
    ctx_.unit = lua.eval('{ exists = true, self = true, player = true, reaction = 5, distance = 0, guid = "me" }')
    f = fields(snap_ctx("context: myself"))
    assert f["tflags"] & 4 == 4
    # no mouseover, a target: the fallback
    ctx_.unit = lua.table()
    ctx_.target = "t1"
    ctx_.targetPlayer = False
    f = fields(snap_ctx("context: target only"))
    assert f["source"] == 2 and f["exists"] == 1, f
    ctx_.target = None

    # the item slot under the pointer
    ctx_["items"] = lua.eval('{ ["0:3"] = { link = "|Hitem:sword|h[Sword]|h", count = 1 }, ["1:2"] = { link = "|Hitem:potion|h[Potion]|h", count = 5 }, ["0:4"] = { link = "|Hitem:junk|h[Junk]|h", count = 1 } }')
    ctx_.foci = lua.eval('{ GetBagID = function() return 0 end, GetID = function() return 3 end, GetName = function() return "ContainerFrame1Item2" end }')
    f = fields(snap_ctx("context: bag sword"))
    assert f["kind"] == 1 and f["fflags"] == 1 + 2 and f["mflags"] == 0 and f["count"] == 1, f          # filled, equippable
    # 1.15 may have moved the global check: the namespace one, then the equip slot of the item data
    lua.execute('IsEquippableItem = nil; C_Item = { IsEquippableItem = function(l) return l == "|Hitem:sword|h[Sword]|h" end }')
    f = fields(snap_ctx("context: bag sword, C_Item check"))
    assert f["fflags"] == 1 + 2, f
    lua.execute('C_Item = { GetItemInfoInstant = function(l) if l == "|Hitem:sword|h[Sword]|h" then return 1, "Armor", "Cloth", "INVTYPE_CLOAK" end return 2, "Misc", "Junk", "" end }')
    f = fields(snap_ctx("context: bag sword, equip slot only"))
    assert f["fflags"] == 1 + 2, f
    worn, how = X.equipInfo("|Hitem:junk|h[Junk]|h")
    assert worn is False and "equip slot" in how, (worn, how)
    lua.execute('C_Item = nil; function IsEquippableItem(link) return link == "|Hitem:sword|h[Sword]|h" end')
    ctx_.foci = lua.eval('{ GetBagID = function() return 1 end, GetID = function() return 2 end, GetName = function() return "ContainerFrame2Item4" end }')
    ctx_.merchant = True
    f = fields(snap_ctx("context: bag potion at the vendor"))
    assert f["kind"] == 1 and f["fflags"] == 1 + 4 + 8 and f["mflags"] == 1 and f["count"] == 5, f      # filled, usable, stacked, vendor
    ctx_.merchant = False
    ctx_.cursor = "|Hitem:junk|h[Junk]|h"
    f = fields(snap_ctx("context: bag potion, item on the cursor"))
    assert f["mflags"] == 2, f
    ctx_.cursor = None
    ctx_.foci = lua.eval('{ GetBagID = function() return -1 end, GetID = function() return 4 end, GetName = function() return "BankFrameItem4" end }')
    lua.execute('ctx.items["-1:4"] = { link = "|Hitem:junk|h[Junk]|h", count = 1 }')
    f = fields(snap_ctx("context: bank slot"))
    assert f["kind"] == 2 and f["fflags"] == 1, f
    ctx_.foci = lua.eval('{ GetBagID = function() return 0 end, GetID = function() return 9 end, GetName = function() return "ContainerFrame1Item1" end }')
    f = fields(snap_ctx("context: empty bag slot"))
    assert f["kind"] == 1 and f["fflags"] == 0, f
    lua.execute('ctx.foci = { GetID = function() return 16 end, GetName = function() return "CharacterMainHandSlot" end }')
    lua.execute('GetInventoryItemLink = function(u, slot) if slot == 16 then return "|Hitem:sword|h[Sword]|h" end end; GetInventoryItemCount = function() return 1 end')
    f = fields(snap_ctx("context: equipment slot"))
    assert f["kind"] == 3 and f["fflags"] == 1, f                     # equipped: not "equippable" again
    ctx_.foci = None

    # the game's own menu of the target: way 1 is the secure button, PostClick checks that a menu opened; if not, the insecure ways follow
    lua.execute("""
        ctx.dropdown = false; ctx.opened = nil
        DropDownList1 = { IsShown = function() return ctx.dropdown end }
        function UnitName(u) return "Anna", "Realm" end
        function UnitPopup_OpenMenu(which, data) ctx.opened = { which = which, unit = data.unit, name = data.name }; ctx.dropdown = true end
    """)
    ctx_.target = "p1"
    ctx_.unit = lua.table(exists=True, player=True, dead=False, reaction=5, distance=5, guid="p1")
    ctx_.targetPlayer = True
    lua.execute("ctx.dropdown = true")                                  # the secure click opened it
    popup.hooks.PostClick(popup, "LeftButton")
    assert "menu open" in X.lastPopup and g.ctx.opened is None, X.lastPopup
    lua.execute("ctx.dropdown = false")                                 # a client without togglemenu: nothing open after the click
    popup.hooks.PostClick(popup, "LeftButton")
    assert g.ctx.opened.which == "PLAYER", g.ctx.opened.which
    assert "UnitPopup_OpenMenu" in X.lastPopup and g.ctx.opened.unit == "target" and g.ctx.opened.name == "Anna"
    assert X.popupKind("target") == "PLAYER"
    ctx_.unit = lua.table(exists=True, player=True, dead=False, reaction=5, distance=5, guid="p1", group=True)
    assert X.popupKind("target") == "PARTY"
    ctx_.unit = lua.table(exists=True, player=True, dead=False, reaction=2, distance=5, guid="p1", attackable=True)
    assert X.popupKind("target") == "ENEMY_PLAYER"
    ctx_.targetPlayer = False
    assert X.popupKind("target") == "TARGET"
    # no unit at all: the insecure ways do nothing
    ctx_.target = None
    ctx_.unit = lua.table()
    lua.execute("ctx.dropdown = false; ctx.opened = nil")
    assert X.openPopupInsecure() is None and g.ctx.opened is None
    lua.execute("DropDownList1 = nil; UnitPopup_OpenMenu = nil")

    # throwing away: nothing is deleted without a tap on Yes
    ctx_.foci = lua.eval('{ GetBagID = function() return 0 end, GetID = function() return 4 end, GetName = function() return "ContainerFrame1Item3" end }')
    ctx_.cursor = None
    db = g.findFrame("TouchForeverCtxDiscard")
    db.scripts.OnClick(db, "LeftButton")
    assert g.ctx.deleted == 0 and X.pendingDiscard == "|Hitem:junk|h[Junk]|h" and len(g.ctx.popups) == 1, (g.ctx.deleted, X.pendingDiscard)
    assert g.ctx.popups[1].name == "TOUCHFOREVER_DISCARD" and g.ctx.popups[1].text == "|Hitem:junk|h[Junk]|h"
    dialog = g.StaticPopupDialogs["TOUCHFOREVER_DISCARD"]
    dialog.OnCancel()                                                  # No / Escape: the item goes back
    assert g.ctx.deleted == 0 and g.ctx.cursor is None and X.pendingDiscard is None
    db.scripts.OnClick(db, "LeftButton")
    dialog = g.StaticPopupDialogs["TOUCHFOREVER_DISCARD"]
    ctx_.cursor = "|Hitem:other|h[Other]|h"                           # something else hangs on the cursor now: no deletion
    dialog.OnAccept()
    assert g.ctx.deleted == 0, g.ctx.deleted
    ctx_.cursor = None
    db.scripts.OnClick(db, "LeftButton")
    g.StaticPopupDialogs["TOUCHFOREVER_DISCARD"].OnAccept()            # Yes: exactly this item goes
    assert g.ctx.deleted == 1 and g.ctx.cursor is None
    g.StaticPopupDialogs["TOUCHFOREVER_DISCARD"].OnAccept()            # a second Yes without a question deletes nothing
    assert g.ctx.deleted == 1
    # no way to ask: the item stays; in combat nothing is picked up
    saved = g.StaticPopup_Show
    g.StaticPopup_Show = None
    before = g.ctx.deleted
    db.scripts.OnClick(db, "LeftButton")
    assert g.ctx.deleted == before and g.ctx.cursor is None
    g.StaticPopup_Show = saved
    g.inCombat = True
    ctx_.picked = None
    db.scripts.OnClick(db, "LeftButton")
    assert ctx_.picked is None and g.ctx.deleted == before
    g.inCombat = False
    ctx_.foci = None
    # put the cursor item back (the launcher presses this key before a right click)
    cb = g.findFrame("TouchForeverCtxClearcursor")
    ctx_.cursor = "|Hitem:junk|h[Junk]|h"
    cb.scripts.OnClick(cb, "LeftButton")
    assert g.ctx.cursor is None

    # combat: the spells of the buttons stay as they were, only the flags go on changing
    ctx_.book = lua.eval("""{ { id = 400, name = "Pyroblast", sub = "Rank 1" } }""")
    ctx_.harmful = lua.eval("{ [400] = true }")
    g.inCombat = True
    g.fireEvent("SPELLS_CHANGED")
    assert btn.GetAttribute(btn, "macrotext") == "/cast [@target,harm,nodead] Fireball; [@target,help,nodead] Heal"
    g.inCombat = False
    g.fireEvent("PLAYER_REGEN_ENABLED")
    assert btn.GetAttribute(btn, "macrotext") == "/cast [@target,harm,nodead] Pyroblast", btn.GetAttribute(btn, "macrotext")
    assert btn2.GetAttribute(btn2, "macrotext") == ""
    # more than eight harmful spells: the first eight, flag "more than fit"
    many = [{"id": 500 + i, "name": "Bolt%d" % i, "sub": "Rank 1"} for i in range(11)]
    lua.execute("ctx.book = { " + ", ".join('{ id = %d, name = "%s", sub = "Rank 1" }' % (m["id"], m["name"]) for m in many) + " }")
    lua.execute("ctx.harmful = {}; for i = 0, 10 do ctx.harmful[500 + i] = true end")
    g.fireEvent("SPELLS_CHANGED")
    n = snap_ctx("context: eleven harmful spells")
    assert fields(n)["flags"] & 4 == 4 and entry(n, 7)[0] == 507 and entry(n, 8) == (0, 0, 0), (fields(n), entry(n, 7))
    lines = X.describe()
    assert any("focus:" in lines[i] for i in range(1, len(lines) + 1)) and any("ALT-SHIFT-F12" in lines[i] for i in range(1, len(lines) + 1))
    # without a window list (cannot measure) the strip does not claim to know the context
    saved_width = g.GetScreenWidth
    g.GetScreenWidth = None
    st.now += 0.3
    d = snapshot("context: windows unavailable")
    assert d["context"] is None
    g.GetScreenWidth = saved_width
    st.now += 0.3
    # without the context file (older addon files in the folder) the section is empty and the flag stays clear
    ns.context = None
    d = snapshot("context: file missing")
    assert d["context"] is None and d["windowFlagsRaw"] & 2 == 0
    ns.context = X
    ctx_.book = lua.eval(
        """{ { id = 100, name = "Fireball", sub = "Rank 1" }, { id = 101, name = "Fireball", sub = "Rank 2" }, { id = 102, name = "Frostbolt", sub = "Rank 1" },
             { id = 103, name = "Quickness", sub = "Passive", passive = true }, { id = 200, name = "Heal", sub = "Rank 1" }, { id = 201, name = "Heal", sub = "Rank 2" },
             { id = 202, name = "Blessing", sub = "Rank 1" }, { id = 300, name = "Mining", sub = "Apprentice" } }""")
    lua.execute("ctx.harmful = { [100] = true, [101] = true, [102] = true }")
    g.fireEvent("SPELLS_CHANGED")
    ctx_.unit = lua.table(exists=True, player=False, dead=False, reaction=2, distance=5, guid="m1")
    ctx_.target = "m1"
    snapshot("context: hostile mob again")
    ctx_.unit = lua.table()
    ctx_.target = None
    snapshot("context: back to idle")

    # F: only changed cells are painted again
    sets_before = sum(t.sets for t in frame.textures.values())
    g.tick(frame, 0.11)
    changed = sum(t.sets for t in frame.textures.values()) - sets_before
    assert changed <= 2, "idle tick repainted %d cells" % changed   # counter and checksum cell

    # G: switched off
    S.set(False)
    assert frame.shown is False
    S.set(True)
    assert frame.shown is True

    print("scenarios ok:", ", ".join(n for n, _, _ in scenarios))

    if args.write_vectors:
        out = pathlib.Path(args.write_vectors)
        out.parent.mkdir(parents=True, exist_ok=True)
        lines = ["# Generated by tools/cooldown_strip_check.py from TouchForeverCooldown.lua (do not edit by hand).",
                 "# name | counter | infoFlags | slot index:remainingCode:fractionCode:flags (only slots that are not idle/empty) | 323 cells as rrggbb | windows: - (no window section) or section flags;left:top:right:bottom:kind;... (codes 0..255) | scroll: - (no scroll section) or flags;left:top:right:bottom:kind;... (smallest first) | context: - (no context section) or the 165 payload nibbles as hex digits | chat: chat flags;left:top:right:bottom (codes 0..255) or chat flags;- (no rectangle)"]
        for name, cells, d in scenarios:
            slots = ";".join("%d:%d:%d:%d" % (i, a, b, c) for i, (a, b, c) in enumerate(d["slots"]) if (a, b, c) != (0, 0, 0))
            hexes = "".join("%02x%02x%02x" % tuple(int(round(v * 255)) for v in c) for c in cells)
            w = d["windows"]
            windows = "-" if w is None else ";".join([str(d["windowFlagsRaw"])] + ["%d:%d:%d:%d:%d" % r for r in w[1]])
            sc = d["scroll"]
            scroll = "-" if sc is None else ";".join([str(sc[0])] + ["%d:%d:%d:%d:%d" % r for r in sc[1]])
            cx = d["context"]
            context = "-" if cx is None else "".join("%x" % v for v in cx)
            ch = d["chat"]
            chat_text = "%d;%s" % (ch[0], "-" if ch[1] is None else "%d:%d:%d:%d" % ch[1])
            lines.append("%s|%d|%d|%s|%s|%s|%s|%s|%s" % (name, d["counter"], d["infoFlags"], slots, hexes, windows, scroll, context, chat_text))
        out.write_text("\n".join(lines) + "\n", encoding="utf-8", newline="\n")
        print("written", out)


if __name__ == "__main__":
    sys.exit(main())
