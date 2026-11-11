-- TouchForever, part 4: cooldown strip. Own code, no Blizzard or third party addon parts.
--
-- SavedVariables are written only on /reload, so the launcher cannot learn about cooldowns from them. This file draws a tiny
-- strip of coloured cells (2 pixels high, 1024 pixels wide, plus a second row of 2 pixels and 236 pixels for the context section) in the top left corner of the screen. The launcher reads these
-- pixels from the game picture on every frame and draws the cooldown sweep on its touch buttons. The format is documented
-- in app/gamenative/cooldown/CooldownStrip.kt (the Kotlin decoder is the other half and is unit tested against this file).
--
-- Cells are 4 x 2 pixels, one nibble (0..15) per colour channel, level = nibble * 17:
--   0..3     sync head (fixed colours with channels 0 / 255)
--   4        info: R = format version, G = counter (0..15, changes with every update), B = info flags
--   5..20    calibration ramp: cell 5+k is grey (17k, 17k, 17k); the launcher reads it and so survives gamma or colour curves
--   21..212  data, two cells per slot for the 96 slots of the bars 1-8: A = remaining time code (12 bit, R G B),
--            B = fraction remaining (8 bit, R G) and flags (B)
--   213      checksum (12 bit) over all nibbles of info + data
--   214..215 sync tail (shows that the strip is complete and not scaled)
--   216..232 window section (optional: only read when info flag I_WINDOWS_AWARE is set; an app that does not know it stops
--            after cell 215 and is not disturbed): rectangles of the open big game windows (bags, map, character, menus ...),
--            so the launcher can hide its touch elements that lie over them.
--            216..217 sync head (255,0,0) (0,255,255) | 218 info: R = section version, G = number of rectangles (0..4),
--            B = section flags (bit 0: more windows were open than fit, bit 2 (value 4): the player is in combat) | 219..230 four rectangles of three cells (nibbles:
--            left hi lo, top hi lo, right hi lo, bottom hi lo, kind; 0..255 = 0..1 of the game picture, origin top left;
--            unused rectangles are zero) | 231 checksum (12 bit, same formula, over the 39 nibbles of 218..230) | 232 tail (255,255,255)
--            The section version R doubles as the build number of the addon (ADDON BUILD, S.WIN_VERSION): 1 = TouchForever 1.6.0
--            with window section, 2 = + combat flag and the reload key (CTRL-SHIFT-F4 clicks TouchForeverReloadKey). The launcher
--            compares it with the build of the bundled addon and reloads the interface when the game still runs an older one.
--            Build 6: the scroll section is always reported (the former addon switch "swiping in lists scrolls" is gone, the app decides).
--            Build 8: no change of the strip; the texts of the addon come from TouchForeverLocale.lua (German/English, language set by the app).
--            Build 10: no change of the strip; the layout plan (TouchForeverLayout.lua) puts the quest tracker flush right below the minimap.
--            Build 9: no change of the strip; TouchForeverBindings.lua puts replacement keys on the action bars 2 to 5 (override bindings).
--            A launcher that only knows version 1 ignores newer sections (windows unknown), a newer launcher reads version 1.
--   233..255 scroll section (build 3 and up; only read when the window section is read AND its section flag bit 3 (value 8,
--            WF_SCROLL_AWARE) is set): rectangles of the scrollable lists (ScrollBox / ScrollFrame / ScrollingMessageFrame whose content is
--            longer than what is visible) inside the open windows, so the launcher turns a vertical swipe on them into mouse wheel ticks.
--            233..234 sync head (0,255,0) (255,0,255) | 235 info: R = scroll version (1), G = number of rectangles (0..6), B = flags
--            (bit 0: more areas than fit) | 236..253 six rectangles of three cells (same nibble layout as the windows; kind: 1 ScrollBox,
--            2 ScrollFrame, 3 ScrollingMessageFrame; unused ones zero; SMALLEST FIRST, so the first one that holds a finger is the
--            innermost list) | 254 checksum (12 bit, same formula, over the 57 nibbles of 235..253) | 255 tail (255,255,0)
--   256..314 context section (build 4 and up; only read when the window section is read AND its section flag bit 1 (value 2,
--            WF_CONTEXT_AWARE) is set): the unit under the finger, the item slot under the pointer and the spell lists of the
--            launcher's menu on long press. These 59 cells are drawn in a SECOND ROW right below the first one (pixel rows 2 and 3,
--            same left edge, same cell width), cell 256 at x = 0, so the first row is exactly the one of build 3 (1024 pixels) and the
--            second is 236 pixels wide. 256..257 sync head (255,255,0) (0,0,255) | 258..312 payload of 55 cells = 165 nibbles
--            (layout and meaning: TouchForeverContext.lua, header; Kotlin: ContextWire.kt) | 313 checksum (12 bit, same formula, over the
--            165 nibbles) | 314 tail (0,255,0)
--   315..322 chat section (build 5 and up; the same second row, right behind the context section, cell 315 at x = 236; no flag in the
--            window section: it announces itself by its own sync head and checksum, an older addon draws nothing there): where the chat is and
--            whether its edit box is open, so the launcher puts its row of big touch buttons (microphone, Enter, keyboard, cancel, channel)
--            right at the chat window. 315..316 sync head (255,0,255) (255,255,0) | 317 info: R = chat section version (1), G = number of
--            rectangles (0..1), B = chat flags (1: the main chat window is visible, 2: an edit box has the keyboard focus, same as the info
--            flag I_CHAT_OPEN) | 318..320 one rectangle of three cells (the window layout; the rectangle is the main chat window together with
--            the edit box while one is open; kind 0; zero while the count is 0) | 321 checksum (12 bit, same formula, over the 12 nibbles of
--            317..320) | 322 tail (255,0,255)
-- Pixel perfect: the frame ignores the parent scale and uses the scale 768 / physical screen height, so one unit is one pixel.

local ADDON, ns = ...
ns = ns or {}
local S = {}
ns.strip = S

local L = ns.L

S.VERSION = 1
S.CELL_W, S.CELL_H = 4, 2
S.SLOT_FIRST = { 1, 25, 37, 49, 61, 145, 157, 169 } -- first slot of each bar of 12 (ActionSlotMap.kt)
S.CELL_INFO, S.CELL_CAL, S.CELL_DATA = 4, 5, 21
S.SLOTS = {}
for _, first in ipairs(S.SLOT_FIRST) do
    for n = 0, 11 do S.SLOTS[#S.SLOTS + 1] = first + n end
end
S.SLOT_COUNT = #S.SLOTS
S.CELL_CHECK = S.CELL_DATA + 2 * S.SLOT_COUNT
S.CELL_TAIL = S.CELL_CHECK + 1
S.CELL_COUNT = S.CELL_TAIL + 2          -- cells of the cooldown part (0..215); the window section follows
S.WIN_VERSION = 10                      -- section version = addon build (see the format description above); keep in step with
                                        -- CooldownStrip.ADDON_BUILD (a unit test compares the two)
S.WF_TRUNCATED, S.WF_COMBAT, S.WF_SCROLL_AWARE = 1, 4, 8      -- section flag bits (8: the scroll section follows, build 3)
S.WF_CONTEXT_AWARE = 2                  -- section flag bit (build 4): the context section follows and is real
S.WIN_MAX = 4
S.CELL_WIN = S.CELL_COUNT               -- first cell of the window section
S.CELL_WIN_INFO = S.CELL_WIN + 2
S.CELL_WIN_RECT = S.CELL_WIN_INFO + 1
S.CELL_WIN_CHECK = S.CELL_WIN_RECT + 3 * S.WIN_MAX
S.CELL_WIN_TAIL = S.CELL_WIN_CHECK + 1
S.SCROLL_MAX = 6
S.SCROLL_VERSION = 1
S.CELL_SCR = S.CELL_WIN_TAIL + 1        -- first cell of the scroll section
S.CELL_SCR_INFO = S.CELL_SCR + 2
S.CELL_SCR_RECT = S.CELL_SCR_INFO + 1
S.CELL_SCR_CHECK = S.CELL_SCR_RECT + 3 * S.SCROLL_MAX
S.CELL_SCR_TAIL = S.CELL_SCR_CHECK + 1
S.CONTEXT_VERSION = 1
S.CONTEXT_ENTRIES = 16
S.CELL_CTX = S.CELL_SCR_TAIL + 1        -- first cell of the context section
S.CELL_CTX_INFO = S.CELL_CTX + 2
S.CELL_CTX_UNIT = S.CELL_CTX_INFO + 1
S.CELL_CTX_FOCUS = S.CELL_CTX_UNIT + 2
S.CELL_CTX_BAR = S.CELL_CTX_FOCUS + 2
S.CELL_CTX_SPELL = S.CELL_CTX_BAR + 2
S.CELL_CTX_CHECK = S.CELL_CTX_SPELL + 3 * S.CONTEXT_ENTRIES
S.CELL_CTX_TAIL = S.CELL_CTX_CHECK + 1
S.CONTEXT_NIBBLES = 3 * (S.CELL_CTX_CHECK - S.CELL_CTX_INFO)
S.CHAT_VERSION = 1
S.CHAT_BUILD = 5                        -- first addon build that has the chat section (CooldownStrip.CHAT_BUILD)
S.CELL_CHAT = S.CELL_CTX_TAIL + 1       -- first cell of the chat section (second row)
S.CELL_CHAT_INFO = S.CELL_CHAT + 2
S.CELL_CHAT_RECT = S.CELL_CHAT_INFO + 1
S.CELL_CHAT_CHECK = S.CELL_CHAT_RECT + 3
S.CELL_CHAT_TAIL = S.CELL_CHAT_CHECK + 1
S.TOTAL_CELLS = S.CELL_CHAT_TAIL + 1    -- all cells of the list (first row, then the context and chat cells of the second row)
S.CF_VISIBLE, S.CF_BOX = 1, 2           -- chat flags: the main chat window is visible / an edit box has the keyboard focus
S.ROW_CELLS = S.CELL_SCR_TAIL + 1       -- cells of the first row: exactly what build 3 draws
S.WIDTH = S.ROW_CELLS * S.CELL_W        -- width of the first row (1024)

-- flag bits of a slot
S.F_FILLED, S.F_NO_RESOURCE, S.F_OUT_OF_RANGE, S.F_GCD = 1, 2, 4, 8
-- info flag bits
S.I_SECRET = 1
S.I_CHAT_OPEN = 2   -- a chat edit box has the keyboard focus (the launcher types its voice text only while this is set)
S.I_CHAT_AWARE = 4  -- this addon reports I_CHAT_OPEN (an older addon never sets it: the launcher then cannot tell)
S.I_WINDOWS_AWARE = 8 -- this addon writes the window section (cells 216 and on); an older addon never sets it

local HEAD = { { 255, 0, 255 }, { 0, 255, 0 }, { 0, 0, 255 }, { 255, 255, 0 } }
local TAIL = { { 0, 255, 255 }, { 255, 0, 0 } }
local WIN_HEAD = { { 255, 0, 0 }, { 0, 255, 255 } }
local WIN_TAIL = { 255, 255, 255 }
local SCR_HEAD = { { 0, 255, 0 }, { 255, 0, 255 } }
local SCR_TAIL = { 255, 255, 0 }
local CTX_HEAD = { { 255, 255, 0 }, { 0, 0, 255 } }
local CTX_TAIL = { 0, 255, 0 }
local CHAT_HEAD = { { 255, 0, 255 }, { 255, 255, 0 } }
local CHAT_TAIL = { 255, 0, 255 }

---------------------------------------------------------------------------
-- Pure coding (no game API; the test drives this part directly)
---------------------------------------------------------------------------
-- Remaining seconds -> 12 bit: 0.1 s steps up to 359.9 s, then 30 s steps up to about 4.2 h. 0 = no cooldown.
function S.remainingCode(seconds)
    if type(seconds) ~= "number" or seconds <= 0 then return 0 end
    local tenths = math.floor(seconds * 10 + 0.5)
    if tenths < 1 then tenths = 1 end
    if tenths < 3600 then return tenths end
    local code = 3600 + math.floor((seconds - 360) / 30 + 0.5)
    if code < 3600 then code = 3600 end
    if code > 4095 then code = 4095 end
    return code
end

-- Remaining / total -> 8 bit (0 = no cooldown, 1..255).
function S.fractionCode(remaining, total)
    if type(remaining) ~= "number" or remaining <= 0 then return 0 end
    local fraction = 1
    if type(total) == "number" and total > 0 then fraction = remaining / total end
    if fraction > 1 then fraction = 1 end
    local code = math.floor(fraction * 255 + 0.5)
    if code < 1 then code = 1 end
    return code
end

-- The six nibbles of a slot: remaining code (3), fraction code (2), flags (1).
function S.slotNibbles(remaining, total, flags)
    local r = S.remainingCode(remaining)
    local f = S.fractionCode(remaining, total)
    flags = flags or 0
    return {
        math.floor(r / 256) % 16, math.floor(r / 16) % 16, r % 16,
        math.floor(f / 16) % 16, f % 16, flags % 16,
    }
end

function S.checksum(nibbles)
    local c = 0
    for i = 1, #nibbles do c = (c * 33 + nibbles[i] + 1) % 4093 end
    return c
end

local function grey(n) return { n * 17, n * 17, n * 17 } end

-- 0..1 -> 0..255 (the window rectangles)
function S.windowCode(fraction)
    if type(fraction) ~= "number" then return 0 end
    if fraction < 0 then fraction = 0 end
    if fraction > 1 then fraction = 1 end
    return math.floor(fraction * 255 + 0.5)
end

-- Windows smaller than this are not worth hiding touch elements for (a loot window, a small dialog ...).
S.WIN_MIN_W, S.WIN_MIN_H, S.WIN_MIN_AREA = 0.05, 0.08, 0.012
-- Rectangles of the same kind that touch (gap in shares of the picture) are reported as one (several open bags).
S.WIN_MERGE_GAP = 0.01

local function rectArea(r) return (r.r - r.l) * (r.b - r.t) end

local function near(a, b, gap)
    return a.l <= b.r + gap and b.l <= a.r + gap and a.t <= b.b + gap and b.t <= a.b + gap
end

-- list = { { l =, t =, r =, b =, kind = }, ... } in 0..1 of the game picture (origin top left). Returns the rectangles to report
-- (at most WIN_MAX, biggest first, as { l =, t =, r =, b =, kind = } with codes 0..255) and whether some were left out.
-- Pure, no game API. Too small rectangles are dropped, rectangles of the same kind that touch are merged into one, a rectangle
-- that lies inside a bigger one is dropped.
function S.mergeWindows(list)
    local rects = {}
    for _, w in ipairs(list or {}) do
        local l, t, r, b = math.max(0, w.l), math.max(0, w.t), math.min(1, w.r), math.min(1, w.b)
        local rect = { l = l, t = t, r = r, b = b, kind = w.kind or 0 }
        if r - l >= S.WIN_MIN_W and b - t >= S.WIN_MIN_H and rectArea(rect) >= S.WIN_MIN_AREA then
            rects[#rects + 1] = rect
        end
    end
    local merged = true
    while merged do
        merged = false
        for i = 1, #rects do
            for j = i + 1, #rects do
                local a, c = rects[i], rects[j]
                if a.kind == c.kind and near(a, c, S.WIN_MERGE_GAP) then
                    a.l, a.t, a.r, a.b = math.min(a.l, c.l), math.min(a.t, c.t), math.max(a.r, c.r), math.max(a.b, c.b)
                    table.remove(rects, j)
                    merged = true
                    break
                end
            end
            if merged then break end
        end
    end
    table.sort(rects, function(x, y)
        local ax, ay = rectArea(x), rectArea(y)
        if ax ~= ay then return ax > ay end
        return x.l < y.l
    end)
    local kept = {}
    for _, rect in ipairs(rects) do
        local inside = false
        for _, big in ipairs(kept) do
            local tol = 0.004
            if rect.l >= big.l - tol and rect.t >= big.t - tol and rect.r <= big.r + tol and rect.b <= big.b + tol then
                inside = true
                break
            end
        end
        if not inside then kept[#kept + 1] = rect end
    end
    local out, truncated = {}, false
    for i, rect in ipairs(kept) do
        if i > S.WIN_MAX then
            truncated = true
            break
        end
        out[i] = { l = S.windowCode(rect.l), t = S.windowCode(rect.t), r = S.windowCode(rect.r), b = S.windowCode(rect.b),
            kind = rect.kind % 16 }
    end
    return out, truncated
end

-- Scrollable lists smaller than this are not worth a swipe area.
S.SCROLL_MIN_W, S.SCROLL_MIN_H = 0.03, 0.05
-- Two rectangles whose edges lie closer than this (shares of the picture) are the same list (a ScrollBox and the frame around it).
S.SCROLL_SAME_TOL = 0.004

-- list = { { l =, t =, r =, b =, kind = }, ... } in 0..1 of the game picture. Returns the areas to report as codes 0..255, SMALLEST FIRST
-- (so the launcher takes the first one that holds the finger = the innermost list), at most SCROLL_MAX (the biggest ones are kept) and
-- whether some were left out. Pure, no game API. Too small ones and duplicates are dropped.
function S.mergeScrollAreas(list)
    local rects = {}
    for _, w in ipairs(list or {}) do
        local l, t, r, b = math.max(0, w.l), math.max(0, w.t), math.min(1, w.r), math.min(1, w.b)
        if r - l >= S.SCROLL_MIN_W and b - t >= S.SCROLL_MIN_H then
            local dup = false
            for _, k in ipairs(rects) do
                local tol = S.SCROLL_SAME_TOL
                if math.abs(k.l - l) <= tol and math.abs(k.t - t) <= tol and math.abs(k.r - r) <= tol and math.abs(k.b - b) <= tol then
                    dup = true
                    break
                end
            end
            if not dup then rects[#rects + 1] = { l = l, t = t, r = r, b = b, kind = w.kind or 0 } end
        end
    end
    table.sort(rects, function(x, y)           -- biggest first: these are the ones to keep
        local ax, ay = rectArea(x), rectArea(y)
        if ax ~= ay then return ax > ay end
        if x.l ~= y.l then return x.l < y.l end
        return x.t < y.t
    end)
    local truncated = #rects > S.SCROLL_MAX
    while #rects > S.SCROLL_MAX do rects[#rects] = nil end
    local out = {}
    for i = #rects, 1, -1 do                    -- smallest first
        local rect = rects[i]
        out[#out + 1] = { l = S.windowCode(rect.l), t = S.windowCode(rect.t), r = S.windowCode(rect.r), b = S.windowCode(rect.b),
            kind = rect.kind % 16 }
    end
    return out, truncated
end

-- The nibbles of a section made of rectangles (window or scroll section): 3 + 9 * max (version, count, flags, then the rectangles).
local function rectNibbles(version, list, max, flags)
    local count = math.min(#list, max)
    local nibbles = { version % 16, count, flags % 16 }
    for i = 1, max do
        local w = list[i]
        if i > count then w = { l = 0, t = 0, r = 0, b = 0, kind = 0 } end
        for _, v in ipairs({ w.l, w.t, w.r, w.b }) do
            nibbles[#nibbles + 1] = math.floor(v / 16) % 16
            nibbles[#nibbles + 1] = v % 16
        end
        nibbles[#nibbles + 1] = (w.kind or 0) % 16
    end
    return nibbles
end

-- The nibbles of the window section (info cell and rectangles): 3 + 9 * WIN_MAX. The scroll flag says that the scroll section is real.
local function windowNibbles(windows, truncated, combat, scrollKnown, contextKnown)
    return rectNibbles(S.WIN_VERSION, windows or {}, S.WIN_MAX,
        (truncated and S.WF_TRUNCATED or 0) + (combat and S.WF_COMBAT or 0) + (scrollKnown and S.WF_SCROLL_AWARE or 0)
        + (contextKnown and S.WF_CONTEXT_AWARE or 0))
end

-- Colour of cell number `cell` (0-based within a section) of a nibble list: three nibbles per cell.
local function nibbleColor(n, cell) return { n[3 * cell + 1] * 17, n[3 * cell + 2] * 17, n[3 * cell + 3] * 17 } end

-- slots[i] (i = 1..SLOT_COUNT) = { remaining =, total =, flags = } or nil for an empty slot.
-- windows = { { l =, t =, r =, b =, kind = }, ... } as mergeWindows returns it (codes 0..255), nil = none. The caller sets
-- I_WINDOWS_AWARE in infoFlags only when it has a real list: without the flag the launcher ignores the window section.
-- combat = the player is in combat (section flag WF_COMBAT; the launcher reloads the interface only outside of combat).
-- scrollAreas = { { l =, t =, r =, b =, kind = }, ... } as mergeScrollAreas returns it (codes 0..255, smallest first), nil = the addon does
-- not report scroll areas right now (switched off): the section is written empty and WF_SCROLL_AWARE stays clear.
-- context = the list of CONTEXT_NIBBLES values that TouchForeverContext.lua gives (nil = no context: the section is written empty and
-- WF_CONTEXT_AWARE stays clear).
-- chat = { rect = { l =, t =, r =, b = } (codes 0..255) or nil, flags = CF_VISIBLE + CF_BOX } as S.chatSnapshot gives it (nil = nothing known:
-- the section is written without a rectangle and without flags).
-- Returns the list of TOTAL_CELLS colours { r, g, b } (0..255) in cell order.
function S.build(slots, counter, infoFlags, windows, truncated, combat, scrollAreas, scrollTruncated, context, chat)
    local colors = {}
    for i = 1, 4 do colors[i] = HEAD[i] end
    local sum = {}
    local info = { S.VERSION % 16, (counter or 0) % 16, (infoFlags or 0) % 16 }
    for i = 1, 3 do sum[#sum + 1] = info[i] end
    colors[S.CELL_INFO + 1] = { info[1] * 17, info[2] * 17, info[3] * 17 }
    for k = 0, 15 do colors[S.CELL_CAL + k + 1] = grey(k) end
    for i = 1, S.SLOT_COUNT do
        local s = slots[i]
        local n
        if s then
            n = S.slotNibbles(s.remaining or 0, s.total or 0, s.flags or 0)
        else
            n = { 0, 0, 0, 0, 0, 0 }
        end
        for j = 1, 6 do sum[#sum + 1] = n[j] end
        local base = S.CELL_DATA + 2 * (i - 1)
        colors[base + 1] = { n[1] * 17, n[2] * 17, n[3] * 17 }
        colors[base + 2] = { n[4] * 17, n[5] * 17, n[6] * 17 }
    end
    local c = S.checksum(sum)
    colors[S.CELL_CHECK + 1] = { math.floor(c / 256) * 17, (math.floor(c / 16) % 16) * 17, (c % 16) * 17 }
    colors[S.CELL_TAIL + 1] = TAIL[1]
    colors[S.CELL_TAIL + 2] = TAIL[2]
    -- window section
    colors[S.CELL_WIN + 1] = WIN_HEAD[1]
    colors[S.CELL_WIN + 2] = WIN_HEAD[2]
    local wn = windowNibbles(windows, truncated, combat, scrollAreas ~= nil, context ~= nil)
    for cell = 0, 3 * S.WIN_MAX do -- cell 0 = info cell, then the rectangles
        colors[S.CELL_WIN_INFO + cell + 1] = nibbleColor(wn, cell)
    end
    local wc = S.checksum(wn)
    colors[S.CELL_WIN_CHECK + 1] = { math.floor(wc / 256) * 17, (math.floor(wc / 16) % 16) * 17, (wc % 16) * 17 }
    colors[S.CELL_WIN_TAIL + 1] = WIN_TAIL
    -- scroll section
    colors[S.CELL_SCR + 1] = SCR_HEAD[1]
    colors[S.CELL_SCR + 2] = SCR_HEAD[2]
    local sn = rectNibbles(S.SCROLL_VERSION, scrollAreas or {}, S.SCROLL_MAX, scrollTruncated and S.WF_TRUNCATED or 0)
    for cell = 0, 3 * S.SCROLL_MAX do
        colors[S.CELL_SCR_INFO + cell + 1] = nibbleColor(sn, cell)
    end
    local sc = S.checksum(sn)
    colors[S.CELL_SCR_CHECK + 1] = { math.floor(sc / 256) * 17, (math.floor(sc / 16) % 16) * 17, (sc % 16) * 17 }
    colors[S.CELL_SCR_TAIL + 1] = SCR_TAIL
    -- context section
    colors[S.CELL_CTX + 1] = CTX_HEAD[1]
    colors[S.CELL_CTX + 2] = CTX_HEAD[2]
    local cn = {}
    for i = 1, S.CONTEXT_NIBBLES do cn[i] = ((context and context[i]) or 0) % 16 end
    for cell = 0, (S.CELL_CTX_CHECK - S.CELL_CTX_INFO) - 1 do
        colors[S.CELL_CTX_INFO + cell + 1] = nibbleColor(cn, cell)
    end
    local cc = S.checksum(cn)
    colors[S.CELL_CTX_CHECK + 1] = { math.floor(cc / 256) * 17, (math.floor(cc / 16) % 16) * 17, (cc % 16) * 17 }
    colors[S.CELL_CTX_TAIL + 1] = CTX_TAIL
    -- chat section
    colors[S.CELL_CHAT + 1] = CHAT_HEAD[1]
    colors[S.CELL_CHAT + 2] = CHAT_HEAD[2]
    local rects = {}
    if chat and chat.rect then rects[1] = { l = chat.rect.l, t = chat.rect.t, r = chat.rect.r, b = chat.rect.b, kind = 0 } end
    local hn = rectNibbles(S.CHAT_VERSION, rects, 1, chat and chat.flags or 0)
    for cell = 0, 3 do
        colors[S.CELL_CHAT_INFO + cell + 1] = nibbleColor(hn, cell)
    end
    local hc = S.checksum(hn)
    colors[S.CELL_CHAT_CHECK + 1] = { math.floor(hc / 256) * 17, (math.floor(hc / 16) % 16) * 17, (hc % 16) * 17 }
    colors[S.CELL_CHAT_TAIL + 1] = CHAT_TAIL
    return colors
end

---------------------------------------------------------------------------
-- Game API (every call guarded; names differ between client generations)
---------------------------------------------------------------------------
local isSecret = (type(issecretvalue) == "function") and issecretvalue or function() return false end

local function cooldownOf(slot)
    if C_ActionBar and C_ActionBar.GetActionCooldown then
        local info = C_ActionBar.GetActionCooldown(slot)
        if type(info) == "table" then
            return info.startTime, info.duration, info.isEnabled
        end
        return nil
    end
    if GetActionCooldown then return GetActionCooldown(slot) end
end

local function gcdOf()
    local start, duration
    if C_Spell and C_Spell.GetSpellCooldown then
        local info = C_Spell.GetSpellCooldown(61304)
        if type(info) == "table" then start, duration = info.startTime, info.duration end
    elseif GetSpellCooldown then
        start, duration = GetSpellCooldown(61304)
    end
    if isSecret(start) or isSecret(duration) then return nil, nil end
    if type(start) == "number" and type(duration) == "number" then return start, duration end
end

local lastEnd, lastTotal = {}, {}   -- slot -> end time / total of the last readable cooldown (used while values are secret)

-- Returns remaining, total, flags, secret for one slot; nil when the slot is empty.
local function slotState(slot, now, gcdStart, gcdDuration)
    if HasAction and not HasAction(slot) then
        lastEnd[slot], lastTotal[slot] = nil, nil
        return nil
    end
    local flags = S.F_FILLED
    local secret = false
    local remaining, total = 0, 0
    local start, duration, enabled = cooldownOf(slot)
    if isSecret(start) or isSecret(duration) or isSecret(enabled) then
        -- Values of the game are hidden (restricted in combat): go on counting down a cooldown that was seen before.
        secret = true
        local finish = lastEnd[slot]
        if finish and finish > now then
            remaining, total = finish - now, lastTotal[slot] or (finish - now)
        end
    elseif type(start) == "number" and type(duration) == "number" and duration > 0
        and enabled ~= 0 and enabled ~= false then
        local left = start + duration - now
        if left > 0 then
            remaining, total = left, duration
            lastEnd[slot], lastTotal[slot] = start + duration, duration
            if total <= 2.5 then
                local isGcd
                if gcdStart and gcdDuration and gcdDuration > 0 then
                    isGcd = math.abs(start - gcdStart) < 0.03 and math.abs(duration - gcdDuration) < 0.03
                else
                    isGcd = total <= 1.51
                end
                if isGcd then flags = flags + S.F_GCD end
            end
        else
            lastEnd[slot], lastTotal[slot] = nil, nil
        end
    else
        lastEnd[slot], lastTotal[slot] = nil, nil
    end

    if remaining > 0 and GetActionCharges then
        -- A spell with charges is usable while a charge is left: the recharge is not shown as a cooldown.
        local current, maximum = GetActionCharges(slot)
        if not (isSecret(current) or isSecret(maximum)) and type(current) == "number" and type(maximum) == "number"
            and maximum > 1 and current > 0 then
            remaining, total = 0, 0
            if flags >= S.F_GCD then flags = flags - S.F_GCD end -- only FILLED and GCD can be set at this point
        end
    end

    if IsUsableAction then
        local usable, noMana = IsUsableAction(slot)
        if isSecret(usable) or isSecret(noMana) then
            secret = true
        elseif not usable and noMana then
            flags = flags + S.F_NO_RESOURCE
        end
    end
    if IsActionInRange then
        local inRange = IsActionInRange(slot)
        if isSecret(inRange) then
            secret = true
        elseif inRange == false or inRange == 0 then
            flags = flags + S.F_OUT_OF_RANGE
        end
    end
    return remaining, total, flags, secret
end

---------------------------------------------------------------------------
-- Open game windows (reported in the window section of the strip)
---------------------------------------------------------------------------
-- Global names of the big windows of the 12.x client, checked against the Blizzard UI source (Gethe/wow-ui-source, branch live,
-- Interface/AddOns/Blizzard_*): every name below is declared there (XML name= or CreateFrame) except where a comment says
-- otherwise. Frames of load-on-demand addons do not exist before the addon loads; the scan looks them up in _G each time.
-- kind: 1 bags and storage, 2 map, 3 character and collection panels, 4 system menus, 5 dealing with a character (vendor,
-- quest, mail ...), 6 social. The launcher does not use the kind; it helps when reading a strip by eye.
S.WINDOW_NAMES = {
    { "ContainerFrameCombinedBags", 1 }, { "BankFrame", 1 }, { "GuildBankFrame", 1 },
    { "WorldMapFrame", 2 }, { "FlightMapFrame", 2 },
    { "CharacterFrame", 3 }, { "PlayerSpellsFrame", 3 }, { "ProfessionsBookFrame", 3 }, { "ProfessionsFrame", 3 },
    { "CollectionsJournal", 3 }, { "EncounterJournal", 3 }, { "PVEFrame", 3 }, { "AchievementFrame", 3 },
    { "InspectFrame", 3 }, { "CalendarFrame", 3 }, { "ExpansionLandingPage", 3 }, { "GarrisonLandingPage", 3 },
    { "HousingDashboardFrame", 3 }, { "TransmogFrame", 3 }, { "ArtifactFrame", 3 }, { "AzeriteEssenceUI", 3 },
    { "GenericTraitFrame", 3 }, { "PerksProgramFrame", 3 }, { "WeeklyRewardsFrame", 3 }, { "ChallengesKeystoneFrame", 3 },
    { "MacroFrame", 3 }, { "ClickBindingFrame", 3 }, { "CooldownViewerSettings", 3 }, { "ArchaeologyFrame", 3 },
    { "DelvesCompanionConfigurationFrame", 3 }, { "DelvesDifficultyPickerFrame", 3 }, { "PlayerChoiceFrame", 3 },
    { "SoulbindViewer", 3 }, { "RuneforgeFrame", 3 }, { "OrderHallTalentFrame", 3 }, { "PVPMatchResults", 3 },
    { "ItemUpgradeFrame", 3 }, { "MajorFactionRenownFrame", 3 }, -- the last one is built by a template: found through _G only
    { "GameMenuFrame", 4 }, { "SettingsPanel", 4 }, { "AddonList", 4 }, { "HelpFrame", 4 }, { "ChatConfigFrame", 4 },
    { "EditModeManagerFrame", 4 }, { "StoreFrame", 4 }, { "CatalogShopFrame", 4 },
    { "MerchantFrame", 5 }, { "GossipFrame", 5 }, { "QuestFrame", 5 }, { "QuestLogPopupDetailFrame", 5 },
    { "ItemTextFrame", 5 }, { "MailFrame", 5 }, { "AuctionHouseFrame", 5 }, { "BlackMarketFrame", 5 }, { "LootFrame", 5 },
    { "TradeFrame", 5 }, { "ClassTrainerFrame", 5 }, { "StableFrame", 5 }, { "TabardFrame", 5 }, { "TaxiFrame", 5 },
    { "ItemSocketingFrame", 5 }, { "ScrappingMachineFrame", 5 }, { "ItemInteractionFrame", 5 }, { "ObliterumForgeFrame", 5 },
    { "ProfessionsCustomerOrdersFrame", 5 },
    { "CommunitiesFrame", 6 }, { "FriendsFrame", 6 }, { "GuildControlUI", 6 }, { "ReportFrame", 6 },
}
-- The separate bags ContainerFrame1 .. n (up to 6 declared in Blizzard_UIPanels_Game, more are built when needed).
S.MAX_BAG_FRAMES = 13
-- Panels of UIPanelWindows that are no windows to hide touch elements for.
S.PANEL_SKIP = { UIParentPanelContainer = true, ObjectiveTrackerFrame = true }

local windowList          -- { { name, kind }, ... }: the fixed list, the separate bags, then the panels the game registers itself
local windowListPanels = -1  -- how many names of UIPanelWindows the list was built for (rebuilt when more were registered)
local windowHooked = {}   -- name -> true once the frame's show / hide scripts tell the strip
local windowRects = {}    -- name -> last good normalized rectangle (used when the game withholds it, e.g. in combat)
local windowsDirty = true
local WINDOW_SCAN = 0.25  -- seconds between two scans (show / hide of a hooked frame scans at once)
local nextWindowScan = 0
local windowSnap, windowTruncated   -- result of the last scan (nil = scanning impossible)
local SCROLL_SCAN = 0.5      -- seconds between two scans for scrollable lists
local nextScrollScan = 0
local scrollSignature = ""   -- the open windows the scroll list was made for
local scrollSnap, scrollTruncated   -- result of the last scroll scan (nil = not reported)
local settings               -- TouchForeverDB.settings (set at login)

local function panelCount()
    local n = 0
    if type(UIPanelWindows) == "table" then
        for _ in pairs(UIPanelWindows) do n = n + 1 end
    end
    return n
end

-- Our own list can miss a window of this client (other frame name, new window). The game keeps a table of every window it
-- positions as a panel (UIPanelWindows: quest, gossip, vendor, trainer ...): all of them are looked at as well, kind 0 = not on
-- our list. This is the net under the fixed list.
local function windowEntries()
    local panels = panelCount()
    if windowList and panels == windowListPanels then return windowList end
    windowList = {}
    local known = {}
    for _, entry in ipairs(S.WINDOW_NAMES) do
        windowList[#windowList + 1] = entry
        known[entry[1]] = true
    end
    for index = 1, S.MAX_BAG_FRAMES do
        windowList[#windowList + 1] = { "ContainerFrame" .. index, 1 }
        known["ContainerFrame" .. index] = true
    end
    if type(UIPanelWindows) == "table" then
        local names = {}
        for name in pairs(UIPanelWindows) do
            if type(name) == "string" and not known[name] and not S.PANEL_SKIP[name] then names[#names + 1] = name end
        end
        table.sort(names)
        for _, name in ipairs(names) do windowList[#windowList + 1] = { name, 0 } end
    end
    windowListPanels = panels
    return windowList
end

local function numberOf(v)
    if type(v) == "number" and not isSecret(v) and v == v then return v end
end

-- The rectangle of a frame as shares of the game picture (origin top left), or nil.
-- The coordinates of a frame times its effective scale are in the same units as GetScreenWidth() * UIParent scale.
local function normalizedRect(frame)
    if not (frame and frame.GetRect and frame.GetEffectiveScale and UIParent and GetScreenWidth and GetScreenHeight) then return end
    local scale = numberOf(frame:GetEffectiveScale())
    local parentScale = numberOf(UIParent:GetEffectiveScale())
    local screenW, screenH = numberOf(GetScreenWidth()), numberOf(GetScreenHeight())
    if not (scale and parentScale and screenW and screenH) or screenW <= 0 or screenH <= 0 or parentScale <= 0 then return end
    local left, bottom, width, height = frame:GetRect()
    left, bottom, width, height = numberOf(left), numberOf(bottom), numberOf(width), numberOf(height)
    if not (left and bottom and width and height) then return end
    local sw, sh = screenW * parentScale, screenH * parentScale
    return {
        l = left * scale / sw, r = (left + width) * scale / sw,
        t = 1 - (bottom + height) * scale / sh, b = 1 - bottom * scale / sh,
    }
end

local function hookWindow(name, frame)
    if windowHooked[name] or not frame.HookScript then return end
    windowHooked[name] = true
    for _, script in ipairs({ "OnShow", "OnHide", "OnSizeChanged" }) do
        pcall(frame.HookScript, frame, script, function() windowsDirty = true end)
    end
end

-- The rectangles of the open windows: the merged list for the strip (see mergeWindows) and whether some were left out.
-- Returns nil when scanning is not possible at all (then the strip does not claim to know about windows).
local visibleFrames = {}   -- the frames of the last scan that were visible (their insides are searched for scroll lists)
local visibleSignature = ""  -- their names: a change asks for a scroll scan at once

function S.scanWindows()
    if not (UIParent and GetScreenWidth) then return nil end
    local list = {}
    visibleFrames = {}
    local names = {}
    for _, entry in ipairs(windowEntries()) do
        local name, kind = entry[1], entry[2]
        local frame = _G[name]
        if type(frame) == "table" and frame.IsVisible then
            hookWindow(name, frame)
            local okVisible, visible = pcall(frame.IsVisible, frame)
            if okVisible and visible then
                local okRect, rect = pcall(normalizedRect, frame)
                if okRect and rect then
                    windowRects[name] = rect
                else
                    rect = windowRects[name] -- withheld right now (combat): the last good rectangle
                end
                if rect then list[#list + 1] = { l = rect.l, t = rect.t, r = rect.r, b = rect.b, kind = kind } end
                visibleFrames[#visibleFrames + 1] = frame
                names[#names + 1] = name
            else
                windowRects[name] = nil
            end
        end
    end
    visibleSignature = table.concat(names, ",")
    return S.mergeWindows(list)
end

---------------------------------------------------------------------------
-- Scrollable lists inside the open windows (reported in the scroll section: the launcher turns a swipe there into mouse wheel ticks)
---------------------------------------------------------------------------
S.SCROLL_DEPTH = 12     -- levels of children searched below a window
S.SCROLL_BUDGET = 1500  -- frames looked at per scan (a bag window or a long list has hundreds of rows)

-- 1 ScrollBox (Blizzard_SharedXML Scroll: HasScrollableExtent), 2 ScrollFrame (legacy, GetVerticalScrollRange), 3 ScrollingMessageFrame
-- (GetMaxScrollRange), nil = not a list that scrolls vertically right now.
function S.scrollKind(frame)
    if type(frame) ~= "table" then return nil end
    if type(frame.HasScrollableExtent) == "function" then
        local ok, extent = pcall(frame.HasScrollableExtent, frame)
        if not (ok and extent == true) then return nil end
        if type(frame.IsHorizontal) == "function" then
            local okH, horizontal = pcall(frame.IsHorizontal, frame)
            if okH and horizontal == true then return nil end
        end
        return 1
    end
    if type(frame.GetObjectType) ~= "function" then return nil end
    local okType, objectType = pcall(frame.GetObjectType, frame)
    if not okType then return nil end
    if objectType == "ScrollFrame" and type(frame.GetVerticalScrollRange) == "function" then
        local ok, range = pcall(frame.GetVerticalScrollRange, frame)
        range = ok and numberOf(range)
        if range and range > 1 then return 2 end
    elseif objectType == "ScrollingMessageFrame" and type(frame.GetMaxScrollRange) == "function" then
        local ok, range = pcall(frame.GetMaxScrollRange, frame)
        range = ok and numberOf(range)
        if range and range > 0 then return 3 end
    end
    return nil
end

local function isShown(frame)
    if type(frame.IsShown) ~= "function" then return true end
    local ok, shown = pcall(frame.IsShown, frame)
    return ok and shown and true or false
end

-- Breadth first over the shown children of the open windows (to the depth and frame count above): the lists near the top of a window
-- are found before the hundreds of small frames deep inside a map or a bag. Adds the scrollable lists to `out`.
local function collectScroll(roots, out, budget)
    local queue, head = {}, 1
    for _, frame in ipairs(roots) do queue[#queue + 1] = { frame, 0 } end
    while queue[head] and budget.left > 0 do
        local item = queue[head]
        queue[head] = false
        head = head + 1
        local frame, depth = item[1], item[2]
        if depth < S.SCROLL_DEPTH and type(frame.GetChildren) == "function" then
            local ok, children = pcall(function() return { frame:GetChildren() } end)
            if ok and type(children) == "table" then
                for _, child in ipairs(children) do
                    if budget.left <= 0 then break end
                    budget.left = budget.left - 1
                    if type(child) == "table" and isShown(child) then
                        local kind = S.scrollKind(child)
                        if kind then
                            local okRect, rect = pcall(normalizedRect, child)
                            if okRect and rect then out[#out + 1] = { l = rect.l, t = rect.t, r = rect.r, b = rect.b, kind = kind } end
                        end
                        queue[#queue + 1] = { child, depth + 1 }
                    end
                end
            end
        end
    end
end

-- The scrollable lists inside the windows that the last scanWindows found open: the merged list for the strip (see mergeScrollAreas)
-- and whether some were left out. An empty list when no window is open.
function S.scanScrollAreas()
    local list = {}
    local budget = { left = S.SCROLL_BUDGET }
    for _, frame in ipairs(visibleFrames) do
        local kind = S.scrollKind(frame)   -- a window that is itself a list (rare)
        if kind then
            local okRect, rect = pcall(normalizedRect, frame)
            if okRect and rect then list[#list + 1] = { l = rect.l, t = rect.t, r = rect.r, b = rect.b, kind = kind } end
        end
    end
    collectScroll(visibleFrames, list, budget)
    return S.mergeScrollAreas(list)
end

-- For "/tf windows" and the settings window: what the scan sees right now, one line per visible frame of the list (also those too
-- small to be reported, with the reason), then the rectangles in the strip (codes 0..255 as the launcher gets them), the number of
-- rectangles in the strip (-1: the strip does not claim to know about windows) and the addon build.
function S.describeWindows()
    local lines = {}
    local list = {}
    for _, entry in ipairs(windowEntries()) do
        local frame = _G[entry[1]]
        if type(frame) == "table" and frame.IsVisible then
            local okVisible, visible = pcall(frame.IsVisible, frame)
            if okVisible and visible then
                local okRect, rect = pcall(normalizedRect, frame)
                local tag = entry[2] == 0 and " [panel]" or ""
                if okRect and rect then
                    local small = (rect.r - rect.l < S.WIN_MIN_W or rect.b - rect.t < S.WIN_MIN_H
                        or (rect.r - rect.l) * (rect.b - rect.t) < S.WIN_MIN_AREA) and " (too small, not reported)" or ""
                    lines[#lines + 1] = string.format("%s (kind %d)%s: %.2f %.2f %.2f %.2f%s", entry[1], entry[2], tag,
                        rect.l, rect.t, rect.r, rect.b, small)
                    list[#list + 1] = { l = rect.l, t = rect.t, r = rect.r, b = rect.b, kind = entry[2] }
                else
                    lines[#lines + 1] = entry[1] .. " (kind " .. entry[2] .. ")" .. tag .. ": no rectangle"
                end
            end
        end
    end
    if #lines == 0 then lines[1] = "(no window of the list is open)" end
    local merged = S.mergeWindows(list)
    for index, w in ipairs(merged) do
        lines[#lines + 1] = string.format("strip %d: %d %d %d %d (kind %d)", index, w.l, w.t, w.r, w.b, w.kind)
    end
    -- scroll lists (swipe areas): what the scroll section carries, smallest first
    local okScroll, areas = pcall(S.scanScrollAreas)
    if okScroll and areas then
        for index, a in ipairs(areas) do
            lines[#lines + 1] = string.format("scroll %d: %d %d %d %d (kind %d)", index, a.l, a.t, a.r, a.b, a.kind)
        end
    end
    return lines, windowSnap and #windowSnap or -1, S.WIN_VERSION
end

---------------------------------------------------------------------------
-- The strip on the screen
---------------------------------------------------------------------------
local TICK = 0.1          -- seconds between two updates
local MIN_GAP = 0.03      -- events cause an update at the next frame, but not more often than this
local frame, textures
local painted = {}        -- cell index -> packed colour on screen
local counter = 0
local sinceUpdate, dirty = 0, true
local failures = 0

local function enabled()
    return settings == nil or settings.strip ~= false
end

local function applyScale()
    if not frame then return end
    local height
    if GetPhysicalScreenSize then
        local _, h = GetPhysicalScreenSize()
        height = h
    end
    if not height or height <= 0 then
        height = (GetScreenHeight and UIParent and UIParent.GetEffectiveScale) and GetScreenHeight() * UIParent:GetEffectiveScale() or 768
    end
    frame:SetScale(768 / height)
end

local function paint(colors)
    for i = 1, S.TOTAL_CELLS do
        local c = colors[i]
        local packed = c[1] * 65536 + c[2] * 256 + c[3]
        if painted[i] ~= packed then
            painted[i] = packed
            local t = textures[i]
            if t.SetColorTexture then
                t:SetColorTexture(c[1] / 255, c[2] / 255, c[3] / 255, 1)
            else
                t:SetTexture(c[1] / 255, c[2] / 255, c[3] / 255, 1)
            end
        end
    end
end

-- Asks for an update at the next frame (the context changed: mouseover, target).
function S.touch() dirty = true end

-- Whether a chat edit box has the keyboard focus (typed letters then go into the chat, not into the key bindings).
function S.chatOpen()
    for index = 1, NUM_CHAT_WINDOWS or 10 do
        local box = _G["ChatFrame" .. index .. "EditBox"]
        if box and box.IsShown and box.HasFocus then
            local ok, shown, focus = pcall(function() return box:IsShown(), box:HasFocus() end)
            if ok and shown and focus then return true end
        end
    end
    return false
end

-- The chat for the strip: the rectangle of the main chat window (together with the open edit box, if there is one) as codes 0..255 of the
-- game picture, and the flags S.CF_VISIBLE (the window is shown and not faded out) / S.CF_BOX (an edit box has the focus).
-- Returns nil when there is no chat window; the rectangle is nil when its place cannot be read.
function S.chatSnapshot()
    local main = rawget(_G, "ChatFrame1")
    if not main then return nil end
    local flags = 0
    local okShown, shown = pcall(function() return main.IsShown and main:IsShown() end)
    local alpha = 1
    if main.GetEffectiveAlpha then
        local okAlpha, value = pcall(main.GetEffectiveAlpha, main)
        if okAlpha then alpha = numberOf(value) or 1 end
    end
    if okShown and shown and alpha > 0.05 then flags = flags + S.CF_VISIBLE end
    local box
    for index = 1, NUM_CHAT_WINDOWS or 10 do
        local candidate = rawget(_G, "ChatFrame" .. index .. "EditBox")
        if candidate and candidate.IsShown and candidate.HasFocus then
            local ok, isShown, focus = pcall(function() return candidate:IsShown(), candidate:HasFocus() end)
            if ok and isShown and focus then box = candidate; break end
        end
    end
    if box then flags = flags + S.CF_BOX end
    local rect = normalizedRect(main)
    if not rect then return { flags = flags } end
    if box then
        local boxRect = normalizedRect(box)
        if boxRect then
            rect = { l = math.min(rect.l, boxRect.l), t = math.min(rect.t, boxRect.t), r = math.max(rect.r, boxRect.r), b = math.max(rect.b, boxRect.b) }
        end
    end
    return {
        flags = flags,
        rect = { l = S.windowCode(rect.l), t = S.windowCode(rect.t), r = S.windowCode(rect.r), b = S.windowCode(rect.b) },
    }
end

-- The edit boxes tell the strip at once when they open or close (otherwise it would take up to one tick).
local chatHooked = {}
local function hookChatBoxes()
    for index = 1, NUM_CHAT_WINDOWS or 10 do
        local name = "ChatFrame" .. index .. "EditBox"
        local box = _G[name]
        if box and box.HookScript and not chatHooked[name] then
            chatHooked[name] = true
            for _, script in ipairs({ "OnShow", "OnHide", "OnEditFocusGained", "OnEditFocusLost" }) do
                pcall(box.HookScript, box, script, function() dirty = true end)
            end
        end
    end
end

local function refresh()
    hookChatBoxes()
    local now = GetTime()
    local gcdStart, gcdDuration = gcdOf()
    local slots, secretSeen = {}, false
    for i = 1, S.SLOT_COUNT do
        local ok, remaining, total, flags, secret = pcall(slotState, S.SLOTS[i], now, gcdStart, gcdDuration)
        if ok then
            if remaining then slots[i] = { remaining = remaining, total = total, flags = flags } end
            if secret then secretSeen = true end
        else
            failures = failures + 1
        end
    end
    counter = (counter + 1) % 16
    local info = S.I_CHAT_AWARE
    if secretSeen then info = info + S.I_SECRET end
    if S.chatOpen() then info = info + S.I_CHAT_OPEN end
    -- Open windows: scanned every WINDOW_SCAN seconds, at once after a hooked frame was shown, hidden or resized.
    if windowsDirty or now >= nextWindowScan then
        windowsDirty = false
        nextWindowScan = now + WINDOW_SCAN
        local ok, list, truncated = pcall(S.scanWindows)
        if ok and list then
            windowSnap, windowTruncated = list, truncated
        else
            windowSnap = nil
            if not ok then failures = failures + 1 end
        end
    end
    -- Scrollable lists inside the open windows (swipe areas): every SCROLL_SCAN seconds, at once when the set of open windows changed.
    -- Always reported (build 6 and up: the app alone decides whether a swipe scrolls). Nothing known about windows: nil, the scroll flag
    -- stays clear and the launcher does not swipe-scroll.
    if not windowSnap then
        scrollSnap, scrollTruncated = nil, nil
        nextScrollScan = 0
    elseif visibleSignature ~= scrollSignature or now >= nextScrollScan then
        scrollSignature = visibleSignature
        nextScrollScan = now + SCROLL_SCAN
        if #visibleFrames == 0 then
            scrollSnap, scrollTruncated = {}, false
        else
            local okScroll, areas, cut = pcall(S.scanScrollAreas)
            if okScroll and areas then
                scrollSnap, scrollTruncated = areas, cut
            else
                scrollSnap, scrollTruncated = nil, nil
                if not okScroll then failures = failures + 1 end
            end
        end
    end
    -- Without a list the strip does not claim to know about windows: the launcher then hides nothing.
    if windowSnap then info = info + S.I_WINDOWS_AWARE end
    local inCombat = false
    if InCombatLockdown then
        local okCombat, locked = pcall(InCombatLockdown)
        inCombat = okCombat and locked and true or false
    end
    -- The context (unit under the pointer, item slot, spell lists): only with a window list, like the scroll section.
    local context
    if windowSnap and ns.context and ns.context.get then
        local okContext, nibbles = pcall(ns.context.get)
        if okContext then context = nibbles elseif not okContext then failures = failures + 1 end
    end
    -- The chat (rectangle of the window, edit box open): always reported, it does not depend on the window list.
    local chat
    local okChat, snapshot = pcall(S.chatSnapshot)
    if okChat then chat = snapshot else failures = failures + 1 end
    paint(S.build(slots, counter, info, windowSnap, windowTruncated, inCombat, scrollSnap, scrollTruncated, context, chat))
end

local function onUpdate(_, elapsed)
    sinceUpdate = sinceUpdate + elapsed
    if sinceUpdate >= TICK or ((dirty or windowsDirty) and sinceUpdate >= MIN_GAP) then
        sinceUpdate = 0
        dirty = false
        if failures < 400 then
            local ok = pcall(refresh)
            if not ok then failures = failures + 1 end
        end
    end
end

local function ensureFrame()
    if frame then return frame end
    frame = CreateFrame("Frame", "TouchForeverStrip", UIParent)
    if frame.SetIgnoreParentScale then frame:SetIgnoreParentScale(true) end
    frame:SetFrameStrata("TOOLTIP")
    frame:SetFrameLevel(9000)
    frame:EnableMouse(false)
    frame:SetSize(S.WIDTH, 2 * S.CELL_H)
    frame:SetPoint("TOPLEFT", UIParent, "TOPLEFT", 0, 0)
    textures = {}
    for i = 1, S.TOTAL_CELLS do
        local t = frame:CreateTexture(nil, "OVERLAY")
        t:SetSize(S.CELL_W, S.CELL_H)
        if i <= S.ROW_CELLS then
            t:SetPoint("TOPLEFT", frame, "TOPLEFT", (i - 1) * S.CELL_W, 0)
        else -- the context cells: second row, directly below the first, from the left edge
            t:SetPoint("TOPLEFT", frame, "TOPLEFT", (i - 1 - S.ROW_CELLS) * S.CELL_W, -S.CELL_H)
        end
        if t.SetSnapToPixelGrid then t:SetSnapToPixelGrid(true) end
        if t.SetTexelSnappingBias then t:SetTexelSnappingBias(0) end
        textures[i] = t
    end
    frame:SetScript("OnUpdate", onUpdate)
    for _, event in ipairs({
        "ACTIONBAR_UPDATE_COOLDOWN", "SPELL_UPDATE_COOLDOWN", "ACTIONBAR_UPDATE_USABLE", "ACTIONBAR_SLOT_CHANGED",
        "ACTIONBAR_PAGE_CHANGED", "UPDATE_BONUS_ACTIONBAR", "PLAYER_ENTERING_WORLD", "UI_SCALE_CHANGED",
        "DISPLAY_SIZE_CHANGED", "BAG_UPDATE_COOLDOWN", "ADDON_LOADED", "PLAYER_REGEN_DISABLED", "PLAYER_REGEN_ENABLED",
    }) do
        pcall(frame.RegisterEvent, frame, event)
    end
    frame:SetScript("OnEvent", function(_, event)
        if event == "UI_SCALE_CHANGED" or event == "DISPLAY_SIZE_CHANGED" or event == "PLAYER_ENTERING_WORLD" then
            applyScale()
        end
        if event == "ADDON_LOADED" then windowsDirty = true end -- a load-on-demand window may exist now (hook it, scan it)
        dirty = true
    end)
    applyScale()
    return frame
end

local function apply()
    if enabled() then
        ensureFrame():Show()
        dirty = true
    elseif frame then
        frame:Hide()
    end
end

-- The scroll areas are always reported (build 6 and up); whether a swipe scrolls is the app's setting (Side menu > Controls). An old
-- saved value settings.scroll = false from the former addon switch is ignored.

-- Used by the settings window and /tf strip.
function S.get() return enabled() end
function S.set(value)
    TouchForeverDB = TouchForeverDB or {}
    settings = TouchForeverDB.settings or {}
    TouchForeverDB.settings = settings
    settings.strip = value and true or false
    apply()
end

local starter = CreateFrame("Frame")
starter:RegisterEvent("PLAYER_LOGIN")
starter:SetScript("OnEvent", function()
    TouchForeverDB = TouchForeverDB or {}
    TouchForeverDB.settings = TouchForeverDB.settings or {}
    settings = TouchForeverDB.settings
    apply()
end)

S.text = L.cooldown_strip_top_left_2_pixels
S.tip = L.tiny_colour_strip_the_app_reads
