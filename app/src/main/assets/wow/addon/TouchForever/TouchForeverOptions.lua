-- TouchForever, part 3: settings. Own code, no Blizzard or third party addon parts.
--   * a page "TouchForever" in the game options (Options > AddOns), built with the Settings API of the client
--     (Settings.RegisterVerticalLayoutCategory, RegisterProxySetting / RegisterAddOnSetting, CreateCheckbox / CreateSlider /
--     CreateDropdown); older clients get a panel through InterfaceOptions_AddCategory that opens the window
--   * a window made for fingers (buttons 46 high, sliders with - / + buttons, tabs instead of scrolling), movable,
--     closed by its X button or ESC; opened by the key WFB_OPTIONS, "/tf", the button on the options page, or an optional
--     small gear at the screen edge
-- Every control calls the same functions as the /tf commands (TouchForeverUI.lua), so both always do the same.

local ADDON, ns = ...
local UI = ns and ns.UI
if not UI then return end

local L = ns.L

local OPT = {}          -- ns.options: used by the smoke test
ns.options = OPT

local ROW_H = 46
local WIN_W, WIN_H = 560, 690
local PAD = 12

local function db() return UI.GetDB() end
local function chat() return UI.ChatOptions() end

---------------------------------------------------------------------------
-- The options. kind: check | slider | choice | action | buttons (a row of small buttons). page: 1 surface, 2 minimap, 3 chat,
-- 4 chat more, 5 icons and cooldowns, 6 touch and windows, 7 help (no rows, only text). Every /tf command has its row here.
---------------------------------------------------------------------------
local function percent(v) return tostring(v) .. " %" end
local function plain(v) return tostring(v) end
local function heightText(v) return v and v >= 100 and tostring(v) or L.off end

local MODES = { { "normal", L.normal }, { "compact", L.compact } }
local XP_MODES = {
    { "standard", L.standard }, { "compact", L.compact },
    { "minimap", L.at_the_minimap }, { "off", L.off_2 },
}

local SHAPES = { { "round", L.round }, { "square", L.square } }
local BAG_MODES = {
    { "full", L.whole }, { "pack", L.backpack_ammo }, { "off", L.off_2 },
}

local PEEKS = {
    { "bars", L.bars }, { "chat", L.chat_2 }, { "minimap", L.map },
    { "quests", L.quests }, { "xp", L.xp }, { "menu", L.menu },
}
local OPENERS = {
    { "say", L.say }, { "party", L.party }, { "guild", L.guild },
    { "whisper", L.whisper },
}

local list = {
    -- page 1: surface -----------------------------------------------------------------------------------------------------
    { id = "bars", page = 1, kind = "check", label = L.show_action_bars,
      tip = L.off_only_the_radial_menus_remain,
      default = true,
      get = function() return not db().barsHidden end, set = function(v) UI.SetBarsHidden(not v) end },
    { id = "quests", page = 1, kind = "check", label = L.show_quest_tracker, default = true,
      get = function() return not db().questsHidden end, set = function(v) UI.SetQuestsHidden(not v) end },
    { id = "questsize", page = 1, kind = "slider", label = L.quest_tracker_size,
      min = 50, max = 100, step = 5, default = 70, fmt = percent,
      get = function() return db().questsScale or 100 end, set = function(v) UI.SetQuestScale(v) end },
    { id = "questheight", page = 1, kind = "slider", label = L.quest_tracker_height,
      min = 0, max = 1000, step = 100, default = 0, fmt = heightText,
      get = function() return db().questsHeight or 0 end, set = function(v) UI.SetQuestHeight(v) end },
    { id = "questreset", page = 1, kind = "action", label = L.quest_tracker_reset_size,
      run = function() UI.QuestReset() end },
    { id = "xpmode", page = 1, kind = "choice", label = L.experience_bar, default = "compact", choices = XP_MODES,
      tip = L.standard_as_in_the_game_compact,
      get = function() return UI.GetDB().xpMode or "compact" end, set = function(v) UI.SetXpMode(v) end },
    { id = "xpwidth", page = 1, kind = "slider", label = L.experience_bar_width,
      min = 30, max = 100, step = 5, default = 40, fmt = percent,
      tip = L.width_in_compact_mode_percent_of,
      get = function() return UI.GetDB().xpWidth or 40 end, set = function(v) UI.SetXpWidth(v) end },
    { id = "menubar", page = 1, kind = "check", label = L.hide_menu_bar_and_bag_bar, default = false,
      tip = L.hides_the_buttons_at_the_bottom,
      get = function() return UI.GetDB().menuHidden == true end, set = function(v) UI.SetMenuHidden(v) end },
    { id = "peek", page = 1, kind = "buttons", label = L.show_briefly, items = PEEKS,
      run = function(item) UI.PeekKind(item) end },
    { id = "peekall", page = 1, kind = "action", label = L.peek_at_everything,
      run = function() UI.PeekAll() end },

    -- page 2: minimap -----------------------------------------------------------------------------------------------------
    { id = "minimap", page = 2, kind = "check", label = L.show_minimap, default = true,
      get = function() return not db().minimapHidden end, set = function(v) UI.SetMinimapHidden(not v) end },
    { id = "minimapshape", page = 2, kind = "choice", label = L.minimap_shape, default = "round", choices = SHAPES,
      tip = L.round_as_in_the_game_square,
      get = function() return UI.GetMinimapShape() end, set = function(v) UI.SetMinimapShape(v) end },
    { id = "minimapcorner", page = 2, kind = "check", label = L.minimap_at_the_corner_2, default = true,
      tip = L.moves_the_minimap_once_to_the,
      get = function() return UI.GetMinimapCorner() end, set = function(v) UI.SetMinimapCorner(v) end },
    { id = "zoombig", page = 2, kind = "check", label = L.big_zoom_buttons, default = true,
      tip = L.own_big_buttons_at_the_bottom,
      get = function() return UI.GetZoomBig() end, set = function(v) UI.SetZoomBig(v) end },
    { id = "bagbar", page = 2, kind = "choice", label = L.bag_bar, default = "pack", choices = BAG_MODES,
      tip = L.whole_all_bag_slots_backpack_ammo,
      get = function() return UI.GetBagsMode() end, set = function(v) UI.SetBagsMode(v) end },
    { id = "minimapinfo", page = 2, kind = "action", label = L.show_minimap_layout,
      tip = L.lists_the_parts_of_this_game,
      run = function() OPT.setInfo(2, table.concat(UI.DescribeMinimap(), "\n")) end },

    -- page 3: chat ---------------------------------------------------------------------------------------------------------
    { id = "chatshown", page = 3, kind = "check", label = L.show_chat, default = true,
      get = function() return not db().chatHidden end, set = function(v) UI.SetChatHidden(not v) end },
    { id = "chatphone", page = 3, kind = "check", label = L.phone_preset, default = true,
      tip = L.small_font_few_lines_see_through,
      get = function() return chat().enabled == true end, set = function(v) UI.ChatPhone(v) end },
    { id = "chatmode", page = 3, kind = "choice", label = L.chat_mode, default = "normal", choices = MODES,
      tip = L.compact_fades_after_rest_returns_on,
      get = function() return chat().mode or "normal" end, set = function(v) UI.ChatSet("mode", v) end },
    { id = "chatsize", page = 3, kind = "slider", label = L.font_size, min = 6, max = 24, step = 1,
      default = 10, fmt = plain,
      get = function() return chat().size or 12 end, set = function(v) UI.ChatSet("size", v) end },
    { id = "chatlines", page = 3, kind = "slider", label = L.lines, min = 3, max = 20, step = 1,
      default = 8, fmt = plain,
      get = function() return chat().lines or 8 end, set = function(v) UI.ChatSet("lines", v) end },
    { id = "chatbg", page = 3, kind = "slider", label = L.background, min = 0, max = 100, step = 5,
      default = 20, fmt = percent,
      get = function() return chat().bg or 20 end, set = function(v) UI.ChatSet("bg", v) end },

    -- page 4: chat more ----------------------------------------------------------------------------------------------------
    { id = "chatfilter", page = 4, kind = "check", label = L.hide_spam, default = true,
      tip = L.system_loot_experience_and_trade_channel,
      get = function() return chat().filter == true end, set = function(v) UI.ChatSet("filter", v) end },
    { id = "chatshort", page = 4, kind = "check", label = L.short_channel_names, default = true,
      get = function() return chat().short == true end, set = function(v) UI.ChatSet("short", v) end },
    { id = "chatstamps", page = 4, kind = "check", label = L.show_time_stamps, default = false,
      get = function() return chat().stamps ~= false end, set = function(v) UI.ChatSet("stamps", v) end },
    { id = "chatcompact", page = 4, kind = "check", label = L.compact_chat, default = true,
      tip = L.takes_the_buttons_beside_the_chat,
      get = function() return chat().compact ~= false end, set = function(v) UI.ChatSet("compact", v) end },
    { id = "chatextra", page = 4, kind = "slider", label = L.chat_extra_width, min = 0, max = 120, step = 6,
      default = 36, fmt = plain,
      tip = L.extra_space_for_the_chat_only,
      get = function() return chat().extra or 36 end, set = function(v) UI.ChatSet("extra", v) end },
    { id = "chattabs", page = 4, kind = "check", label = L.chat_tabs_only_on_touch, default = true,
      tip = L.the_tabs_above_the_chat_show,
      get = function() return chat().tabsTouch ~= false end, set = function(v) UI.ChatSet("tabs", v) end },
    { id = "chatdock", page = 4, kind = "check", label = L.keep_chat_flush_left, default = true,
      tip = L.moves_the_chat_window_the_last,
      get = function() return chat().dockLeft ~= false end, set = function(v) UI.ChatSet("dock", v) end },
    { id = "chatleft", page = 4, kind = "action", label = L.dock_chat_to_the_left_now,
      tip = L.puts_the_chat_window_flush_to,
      run = function() UI.ChatDockNow() end },
    { id = "openchat", page = 4, kind = "buttons", label = L.open_chat, items = OPENERS, close = true,
      run = function(item) UI.OpenChatWith(item) end },
    { id = "chatstatus", page = 4, kind = "action", label = L.show_chat_status,
      run = function() UI.ChatStatus() end },

    -- page 5: icons and cooldowns ----------------------------------------------------------------------------------------
    { id = "strip", page = 5, kind = "check", label = L.cooldown_strip, default = true,
      tip = L.tiny_colour_strip_at_the_top,
      get = function() return ns.strip and ns.strip.get() or false end, set = function(v) if ns.strip then ns.strip.set(v) end end },
    -- secure: in the touch window this row is a SecureActionButton ("/reload" macro; ReloadUI() from addon code is blocked).
    -- Elsewhere (settings list, smoke test) run() shows the secure reload button at the top of the screen.
    { id = "reloadicons", page = 5, kind = "action", secure = "reload", label = L.update_icons_reload,
      run = function() if ns.reloadIcons then ns.reloadIcons() end end },
    { id = "reloadbutton", page = 5, kind = "check", label = L.reload_button_after_bar_changes, default = true,
      tip = L.offers_update_icons_after_changes_to,
      get = function() return ns.button and ns.button.get() or false end, set = function(v) if ns.button then ns.button.set(v) end end },

    -- page 6: touch and windows --------------------------------------------------------------------------------------------
    { id = "layout", page = 6, kind = "check", label = L.touch_layout_windows, default = false,
      tip = L.touch_layout_windows_tip,
      get = function() return ns.layout ~= nil and ns.layout.IsActive() end, set = function(v) if ns.layout then ns.layout.Set(v) end end },
    { id = "layoutagain", page = 6, kind = "action", label = L.touch_layout_again,
      tip = L.touch_layout_again_tip,
      run = function() if ns.layout then ns.layout.Again() end end },
    { id = "windows", page = 6, kind = "action", label = L.show_detected_windows,
      tip = L.shows_which_open_game_windows_the,
      run = function() OPT.setInfo(6, OPT.windowsText()) end },
    { id = "mapfit", page = 6, kind = "check", label = L.fit_the_world_map_to_the, default = true,
      tip = L.scales_the_world_map_map_quest,
      get = function() return UI.GetMapFit() end, set = function(v) UI.SetMapFit(v) end },
    { id = "gear", page = 6, kind = "check", label = L.gear_button_on_the_screen_edge,
      default = false,
      tip = L.small_gear_that_opens_this_window,
      get = function() return db().gear == true end, set = function(v) db().gear = v and true or false; OPT.applyGear() end },
    { id = "defaultkeys", page = 6, kind = "action", label = L.set_standard_keys_again,
      run = function() UI.SetDefaultKeys(true) end },
    { id = "resetall", page = 6, kind = "action", label = L.reset_everything_to_the_preset,
      run = function() UI.ResetAll() end },
    -- Only a hint line (no control): swiping and touch live in the app's side menu, not in the addon.
    { id = "touchnote", page = 6, kind = "note",
      label = L.swiping_and_touch_are_set_in },
}
local byId = {}
for _, option in ipairs(list) do byId[option.id] = option end
OPT.list, OPT.byId = list, byId

-- Tab names (short) and headings (full) of the pages.
local PAGE_TITLES = {
    L.surface, L.minimap, L.chat_2, L.chat_more,
    L.icons, L.touch, L.help,
}
local PAGE_HEADINGS = {
    L.surface_bars_quests_experience_menu,
    L.minimap,
    L.chat_2,
    L.chat_filter_layout_open,
    L.icons_cooldowns,
    L.touch_windows,
    L.help,
}
OPT.pageTitles, OPT.pageHeadings = PAGE_TITLES, PAGE_HEADINGS
local HELP_PAGE = #PAGE_TITLES

-- Text shown under the rows of a page (list of detected windows, minimap layout).
local infos = {}
function OPT.setInfo(page, text)
    infos[page] = text
    if OPT.refresh then OPT.refresh() end
end

function OPT.infoText(page) return infos[page] end

-- What /tf windows prints, for the page "Touch": the addon build, the windows in the strip, the frames the scan sees.
function OPT.windowsText()
    if not (ns.strip and ns.strip.describeWindows) then return L.window_list_not_available end
    local lines, reported, build = ns.strip.describeWindows()
    local out = { string.format(L.addon_build_d_windows_in_the, build, reported) }
    for index, line in ipairs(lines) do
        if index > 8 then out[#out + 1] = "..."; break end
        out[#out + 1] = line
    end
    return table.concat(out, "\n")
end

local function helpText()
    return L.everything_here_is_also_a_command
end

local function choiceLabel(option, value)
    for _, choice in ipairs(option.choices or {}) do
        if choice[1] == value then return choice[2] end
    end
    return tostring(value)
end

local function clampStep(option, value)
    value = math.max(option.min, math.min(option.max, value))
    return value
end

-- Value change as the controls of the window do it (also used by the smoke test).
function OPT.change(id, direction)
    local option = byId[id]
    if not option then return end
    if option.kind == "check" then
        option.set(not option.get())
    elseif option.kind == "slider" then
        option.set(clampStep(option, option.get() + direction * option.step))
    elseif option.kind == "choice" then
        local index = 1
        for i, choice in ipairs(option.choices) do
            if choice[1] == option.get() then index = i end
        end
        option.set(option.choices[(index % #option.choices) + 1][1])
    elseif option.kind == "action" then
        option.run()
    elseif option.kind == "buttons" then
        option.run(option.items[1][1])
    end
end

---------------------------------------------------------------------------
-- Gear button at the screen edge (off by default)
---------------------------------------------------------------------------
local gear

function OPT.applyGear()
    local d = db()
    if not d then return end
    if not d.gear then
        if gear then gear:Hide() end
        return
    end
    if not gear then
        gear = CreateFrame("Button", "TouchForeverGear", UIParent)
        gear:SetSize(46, 46)
        gear:SetFrameStrata("MEDIUM")
        gear:SetClampedToScreen(true)
        gear:SetMovable(true)
        gear:RegisterForDrag("LeftButton")
        gear:SetAlpha(0.7)
        local back = gear:CreateTexture(nil, "BACKGROUND")
        back:SetAllPoints()
        back:SetColorTexture(0, 0, 0, 0.45)
        local icon = gear:CreateTexture(nil, "ARTWORK")
        icon:SetPoint("TOPLEFT", 5, -5)
        icon:SetPoint("BOTTOMRIGHT", -5, 5)
        icon:SetTexture("Interface\\Icons\\INV_Misc_Gear_01")
        local point = d.gearPos
        gear:SetPoint(point and point[1] or "RIGHT", UIParent, point and point[2] or "RIGHT", point and point[3] or -4, point and point[4] or 120)
        gear:SetScript("OnClick", function() OPT.toggle() end)
        gear:SetScript("OnDragStart", function(self) self:StartMoving() end)
        gear:SetScript("OnDragStop", function(self)
            self:StopMovingOrSizing()
            local p, _, rp, x, y = self:GetPoint()
            d.gearPos = { p, rp, x, y }
        end)
    end
    gear:Show()
end

---------------------------------------------------------------------------
-- The touch window
---------------------------------------------------------------------------
local win
local pages, tabButtons, refreshers = {}, {}, {}
local currentPage = 1

-- template: optional "SecureActionButtonTemplate"; its OnClick belongs to the template, so no script is set then.
local function newButton(parent, width, height, text, onClick, template)
    local button = CreateFrame("Button", nil, parent, template)
    button:SetSize(width, height)
    local back = button:CreateTexture(nil, "BACKGROUND")
    back:SetAllPoints()
    back:SetColorTexture(0.17, 0.19, 0.25, 0.95)
    button.back = back
    local light = button:CreateTexture(nil, "HIGHLIGHT")
    light:SetAllPoints()
    light:SetColorTexture(1, 1, 1, 0.14)
    local label = button:CreateFontString(nil, "OVERLAY", "GameFontNormalLarge")
    label:SetPoint("CENTER")
    label:SetText(text or "")
    button.label = label
    if template then return button end
    if button.RegisterForClicks then button:RegisterForClicks("LeftButtonUp") end
    button:SetScript("OnClick", onClick)
    return button
end

local function onOffText(on)
    return on and "|cff40ff40" .. L.on_2 .. "|r" or "|cffff6060" .. L.off_2 .. "|r"
end

local function sliderText(option)
    local value = option.get()
    local text = option.fmt and option.fmt(value) or tostring(value)
    if option.id == "questheight" then text = heightText(value) end
    return text
end

local function addRow(parent, option, y)
    local width = WIN_W - 2 * PAD
    local refresh
    if option.kind == "check" or option.kind == "choice" then
        local button = newButton(parent, width, ROW_H, "", function() OPT.change(option.id, 1); OPT.refresh() end)
        button.label:ClearAllPoints()
        button.label:SetPoint("LEFT", 12, 0)
        button.label:SetText(option.label)
        local state = button:CreateFontString(nil, "OVERLAY", "GameFontHighlightLarge")
        state:SetPoint("RIGHT", -12, 0)
        button.state = state
        button:SetPoint("TOPLEFT", parent, "TOPLEFT", 0, y)
        refresh = function()
            if option.kind == "check" then state:SetText(onOffText(option.get()))
            else state:SetText(choiceLabel(option, option.get())) end
        end
    elseif option.kind == "slider" then
        local row = CreateFrame("Frame", nil, parent)
        row:SetSize(width, ROW_H)
        row:SetPoint("TOPLEFT", parent, "TOPLEFT", 0, y)
        local back = row:CreateTexture(nil, "BACKGROUND")
        back:SetAllPoints()
        back:SetColorTexture(0.17, 0.19, 0.25, 0.6)
        local label = row:CreateFontString(nil, "OVERLAY", "GameFontNormal")
        label:SetPoint("LEFT", 12, 0)
        label:SetWidth(width - ROW_H * 2 - 84)
        label:SetJustifyH("LEFT")
        label:SetText(option.label)
        local plus = newButton(row, ROW_H, ROW_H, "+", function() OPT.change(option.id, 1); OPT.refresh() end)
        plus:SetPoint("RIGHT", row, "RIGHT", 0, 0)
        local value = row:CreateFontString(nil, "OVERLAY", "GameFontHighlightLarge")
        value:SetPoint("RIGHT", plus, "LEFT", -4, 0)
        value:SetWidth(68)
        value:SetJustifyH("CENTER")
        local minus = newButton(row, ROW_H, ROW_H, "-", function() OPT.change(option.id, -1); OPT.refresh() end)
        minus:SetPoint("RIGHT", value, "LEFT", -4, 0)
        row.value = value
        refresh = function() value:SetText(sliderText(option)) end
    elseif option.kind == "note" then
        local row = CreateFrame("Frame", nil, parent)
        row:SetSize(width, ROW_H)
        row:SetPoint("TOPLEFT", parent, "TOPLEFT", 0, y)
        local text = row:CreateFontString(nil, "OVERLAY", "GameFontHighlightSmall")
        text:SetPoint("LEFT", 4, 0)
        text:SetWidth(width - 8)
        text:SetJustifyH("LEFT")
        text:SetText(option.label)
        refresh = function() end
    elseif option.kind == "buttons" then
        local row = CreateFrame("Frame", nil, parent)
        row:SetSize(width, ROW_H)
        row:SetPoint("TOPLEFT", parent, "TOPLEFT", 0, y)
        local label = row:CreateFontString(nil, "OVERLAY", "GameFontNormal")
        label:SetPoint("LEFT", 4, 0)
        label:SetText(option.label)
        local labelWidth = 104
        local count = #option.items
        local gap = 4
        local each = (width - labelWidth - gap * count) / count
        for index, item in ipairs(option.items) do
            local button = newButton(row, each, ROW_H, item[2], function()
                option.run(item[1])
                if option.close then OPT.close() end
            end)
            button.label:SetFontObject("GameFontNormal")
            button:SetPoint("TOPLEFT", row, "TOPLEFT", labelWidth + gap + (index - 1) * (each + gap), 0)
        end
        refresh = function() end
    else
        local button
        if option.secure == "reload" and ns.armReloadButton then
            button = newButton(parent, width, ROW_H, option.label, nil, "SecureActionButtonTemplate")
            ns.armReloadButton(button)
        else
            button = newButton(parent, width, ROW_H, option.label, function() OPT.change(option.id, 1); OPT.refresh() end)
        end
        button:SetPoint("TOPLEFT", parent, "TOPLEFT", 0, y)
        refresh = function() end
    end
    refreshers[#refreshers + 1] = refresh
    return refresh
end

local function keysText()
    local lines = {}
    for _, command in ipairs(UI.COMMANDS) do
        local key = GetBindingKey and GetBindingKey(command)
        local name = _G["BINDING_NAME_" .. command] or command
        lines[#lines + 1] = name .. ": " .. (key and GetBindingText and GetBindingText(key, "KEY_") or key or "-")
    end
    return table.concat(lines, "\n")
end

local keysLabel

local function buildWindow()
    win = CreateFrame("Frame", "TouchForeverWindow", UIParent)
    win:SetSize(WIN_W, WIN_H)
    win:SetFrameStrata("DIALOG")
    win:SetClampedToScreen(true)
    win:SetMovable(true)
    win:EnableMouse(true)
    local pos = db().winPos
    win:SetPoint(pos and pos[1] or "CENTER", UIParent, pos and pos[2] or "CENTER", pos and pos[3] or 0, pos and pos[4] or 0)

    local back = win:CreateTexture(nil, "BACKGROUND")
    back:SetAllPoints()
    back:SetColorTexture(0.04, 0.05, 0.08, 0.94)

    -- title bar: drag handle
    local title = CreateFrame("Button", nil, win)
    title:SetPoint("TOPLEFT", 0, 0)
    title:SetPoint("TOPRIGHT", -ROW_H - 4, 0)
    title:SetHeight(ROW_H)
    title:RegisterForDrag("LeftButton")
    local titleBack = title:CreateTexture(nil, "BACKGROUND")
    titleBack:SetAllPoints()
    titleBack:SetColorTexture(0.1, 0.12, 0.2, 1)
    local titleText = title:CreateFontString(nil, "OVERLAY", "GameFontNormalLarge")
    titleText:SetPoint("LEFT", PAD, 0)
    titleText:SetText("TouchForever  " .. L.drag_title_to_move)
    title:SetScript("OnDragStart", function() win:StartMoving() end)
    title:SetScript("OnDragStop", function()
        win:StopMovingOrSizing()
        local p, _, rp, x, y = win:GetPoint()
        db().winPos = { p, rp, x, y }
    end)

    local close = newButton(win, ROW_H, ROW_H, "X", function() win:Hide() end)
    close:SetPoint("TOPRIGHT", 0, 0)
    close.back:SetColorTexture(0.5, 0.12, 0.12, 1)

    -- tabs
    local tabWidth = (WIN_W - 2 * PAD - 4 * (#PAGE_TITLES - 1)) / #PAGE_TITLES
    for index, text in ipairs(PAGE_TITLES) do
        local tab = newButton(win, tabWidth, ROW_H, text, function() OPT.showPage(index) end)
        tab:SetPoint("TOPLEFT", PAD + (index - 1) * (tabWidth + 4), -(ROW_H + 8))
        tab.label:SetFontObject("GameFontNormal")
        tabButtons[index] = tab
    end

    -- pages: heading, rows, then the text under the rows (windows list, minimap layout, help)
    for index = 1, #PAGE_TITLES do
        local page = CreateFrame("Frame", nil, win)
        page:SetPoint("TOPLEFT", PAD, -(2 * ROW_H + 24))
        page:SetSize(WIN_W - 2 * PAD, WIN_H - 2 * ROW_H - 40)
        pages[index] = page
        local heading = page:CreateFontString(nil, "OVERLAY", "GameFontHighlightLarge")
        heading:SetPoint("TOPLEFT", page, "TOPLEFT", 4, 0)
        heading:SetText(PAGE_HEADINGS[index])
        local y = -30
        for _, option in ipairs(list) do
            if option.page == index then
                addRow(page, option, y)
                y = y - (ROW_H + 6)
            end
        end
        local text = page:CreateFontString(nil, "OVERLAY", "GameFontHighlightSmall")
        text:SetPoint("TOPLEFT", page, "TOPLEFT", 4, y - 6)
        text:SetWidth(WIN_W - 2 * PAD - 8)
        text:SetJustifyH("LEFT")
        text:SetJustifyV("TOP")
        if index == HELP_PAGE then
            refreshers[#refreshers + 1] = function()
                text:SetText(helpText() .. "\n\n" .. L.keys_change_in_options_key_bindings .. keysText())
            end
            keysLabel = text
        else
            refreshers[#refreshers + 1] = function() text:SetText(infos[index] or "") end
        end
        page:Hide()
    end

    win:SetScript("OnShow", function() OPT.refresh() end)
    win:Hide()
    if UISpecialFrames then tinsert(UISpecialFrames, "TouchForeverWindow") end
    OPT.window = win
end

-- The text of the help page as shown (used by the smoke test).
function OPT.helpShown() return helpText() end

function OPT.refresh()
    for _, refresh in ipairs(refreshers) do refresh() end
    for index, tab in ipairs(tabButtons) do
        tab.back:SetColorTexture(index == currentPage and 0.25 or 0.17, index == currentPage and 0.32 or 0.19, index == currentPage and 0.5 or 0.25, 0.95)
    end
end

function OPT.showPage(index)
    currentPage = index
    for i, page in ipairs(pages) do
        if i == index then page:Show() else page:Hide() end
    end
    OPT.refresh()
end

function OPT.open(page)
    if not db() then return end
    if not win then buildWindow() end
    OPT.showPage(page or currentPage)
    win:Show()
end

function OPT.close()
    if win then win:Hide() end
end

function OPT.toggle()
    if win and win:IsShown() then win:Hide() else OPT.open() end
end

UI.Open, UI.Close, UI.ToggleWindow = OPT.open, OPT.close, OPT.toggle
UI.Options = OPT.toggle -- key binding WFB_OPTIONS

---------------------------------------------------------------------------
-- Page in the game options
---------------------------------------------------------------------------
local category

local function registerModernSettings()
    if not (Settings and Settings.RegisterVerticalLayoutCategory and Settings.RegisterAddOnCategory) then return false end
    local newCategory, layout = Settings.RegisterVerticalLayoutCategory("TouchForever")
    local VarType = Settings.VarType or {}
    local createCheckbox = Settings.CreateCheckbox or Settings.CreateCheckBox
    local createDropdown = Settings.CreateDropdown or Settings.CreateDropDown
    if not (createCheckbox and Settings.CreateSlider) then return false end

    local proxy = setmetatable({}, {
        __index = function(_, key) local option = byId[key]; return option and option.get() end,
        __newindex = function(_, key, value) local option = byId[key]; if option then option.set(value) end end,
    })

    local function register(option, varType)
        local variable = "WFB_OPT_" .. option.id
        if Settings.RegisterProxySetting then
            return Settings.RegisterProxySetting(newCategory, variable, varType, option.label, option.default,
                function() return option.get() end, function(value) option.set(value) end)
        end
        return Settings.RegisterAddOnSetting(newCategory, variable, option.id, proxy, varType, option.label, option.default)
    end

    local function addInitializer(initializer)
        if initializer and layout and layout.AddInitializer then layout:AddInitializer(initializer) end
    end

    -- the touch window first
    if CreateSettingsButtonInitializer then
        addInitializer(CreateSettingsButtonInitializer(L.touch_window, L.open,
            function() OPT.open() end, L.large_controls_for_the_phone_the, true))
    end

    local lastPage
    for _, option in ipairs(list) do
        if option.page ~= lastPage then
            lastPage = option.page
            if CreateSettingsListSectionHeaderInitializer then addInitializer(CreateSettingsListSectionHeaderInitializer(PAGE_HEADINGS[option.page])) end
        end
        if option.kind == "check" then
            createCheckbox(newCategory, register(option, VarType.Boolean or "boolean"), option.tip)
        elseif option.kind == "slider" then
            local setting = register(option, VarType.Number or "number")
            local options = Settings.CreateSliderOptions(option.min, option.max, option.step)
            if options.SetLabelFormatter and MinimalSliderWithSteppersMixin and MinimalSliderWithSteppersMixin.Label then
                options:SetLabelFormatter(MinimalSliderWithSteppersMixin.Label.Right, option.fmt)
            end
            Settings.CreateSlider(newCategory, setting, options, option.tip)
        elseif option.kind == "choice" and createDropdown and Settings.CreateControlTextContainer then
            local setting = register(option, VarType.String or "string")
            createDropdown(newCategory, setting, function()
                local container = Settings.CreateControlTextContainer()
                for _, choice in ipairs(option.choices) do container:Add(choice[1], choice[2]) end
                return container:GetData()
            end, option.tip)
        elseif option.kind == "buttons" and CreateSettingsButtonInitializer then
            for _, item in ipairs(option.items) do
                addInitializer(CreateSettingsButtonInitializer(option.label .. " " .. item[2], L.run, function() option.run(item[1]) end, option.tip, true))
            end
        elseif option.kind == "action" and CreateSettingsButtonInitializer then
            addInitializer(CreateSettingsButtonInitializer(option.label, L.run, function() option.run() end, option.tip, true))
        end
    end
    Settings.RegisterAddOnCategory(newCategory)
    category = newCategory
    return true
end

local function registerLegacyPanel()
    if not InterfaceOptions_AddCategory then return false end
    local panel = CreateFrame("Frame")
    panel.name = "TouchForever"
    local text = panel:CreateFontString(nil, "ARTWORK", "GameFontNormalLarge")
    text:SetPoint("TOPLEFT", 16, -16)
    text:SetText("TouchForever")
    local button = newButton(panel, 260, ROW_H, L.open_settings, function() OPT.open() end)
    button:SetPoint("TOPLEFT", 16, -60)
    InterfaceOptions_AddCategory(panel)
    return true
end

function OPT.registerSettings()
    local ok, result = pcall(registerModernSettings)
    OPT.settingsApi = ok and result and "settings" or nil
    if ok and result then return true end
    if not ok then OPT.settingsError = result end
    local legacyOk, legacy = pcall(registerLegacyPanel)
    if legacyOk and legacy then OPT.settingsApi = "legacy" end
    return OPT.settingsApi ~= nil
end

function OPT.openSettings()
    if category and Settings and Settings.OpenToCategory then
        local id = category.GetID and category:GetID() or category
        Settings.OpenToCategory(id)
    else
        OPT.open()
    end
end

-- Called by TouchForeverUI.lua at the end of the start-up.
UI.onStart = function()
    OPT.registerSettings()
    OPT.applyGear()
end
