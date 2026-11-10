# Changes to WoW Forever Touch

One section per version, newest on top. Format: `## <Version> – <Date>`, followed by `### New`, `### Improved` and `### Fixed` with
bullet points in plain language. The launcher reads this file (copy in `app/src/main/assets/CHANGELOG.md`) and shows it as the change list;
when an update is offered, the text for the new versions comes from the update feed. German readers get
`CHANGELOG.de.md` when the app language is German.

## 2.3.3-touch9.1 – 10.11.2026

### New
- Backup: Settings › App › Backup saves your layouts and settings to a file and restores them – for example before a reinstall. Login data and the device ID are deliberately left out.

## 2.3.3-touch9.0 – 10.11.2026

### New
- New name: WoW Forever Touch – runs on the Galaxy Fold just as well as on other tablets and phones.
- Own app icon (a gauntlet that taps), matching notification icon, banner and wallpaper.
- Updates now come from the project's GitHub releases.
- The app is signed with a new key. Uninstall once and install again; after that, updates arrive on their own again.

## 2.3.3-fold8.31 – 10.11.2026

### New
- Backup: Settings › App › Backup saves your layouts and settings to a file and restores them – for example before a reinstall. Login data and the device ID are deliberately left out.

## 2.3.3-fold8.30 – 10.11.2026

### Improved
- The editor now shows WoW action, menu and standard button names in the app language (key picker, “Current”, radial menu presets, button texts). Saved layouts stay untouched, and so do names you typed yourself.
- In English the “Keyboard” button now reads “Keys” and the WoW menu button “Panels”, so nothing gets cut off.

## 2.3.3-fold8.29 – 10.11.2026

### Improved
- Calmer colours across the launcher: antique gold instead of bright yellow gold for titles, lines and the “New” badge, muted wine red instead of signal red for the “Play” button, less glow and softer shadows. All text stays easy to read.

## 2.3.3-fold8.28 – 10.11.2026

### Improved
- Settings regrouped by meaning instead of by space: Game, Start and updates, Controls in the game, App, Help. Rows that jump to another page now read “Open ›”; the error report starts directly; version and client build sit as a quiet footer under “Start and updates”. “Manage addons” is gone – use the “Addons” tile on the home screen.

## 2.3.3-fold8.27 – 11.11.2026

### New
- The app now runs in English – the default on every phone. Choose German in Settings under “More › Language / Sprache”; it applies right away and is remembered.
- The in-game addon (TouchForever) also speaks English or German, following the app language – regardless of the game client's language. Addon build 8.
- Launcher, side menu, start check, dialogs, voice control, cooldowns and the layout editor are fully translated.

### Improved
- What's new: the newest version uses the free card height for whole points instead of cutting them off after three lines; small cards stay small.
- The arrow at the top right of the cards is now a real icon.
- Settings: cards rearranged, no scrolling in portrait. The former “Language” row for voice input is now called “Voice control”.
- Privacy: cloud backup and device transfer of app data are excluded; the error report additionally masks the account folder and your own device ID; voice input prefers on-device recognition.

### Fixed
- With an Arabic, Persian, Hindi or Thai system language the game's Windows ID was regenerated on every start. It now stays stable.
- The subtitle of the “Controls” tile no longer wraps to two lines on the outer display.

## 2.3.3-fold8.26 – 11.11.2026

### New
- The home screen now has a "Check for updates" button. You don't have to restart the app or open a menu for it. When expanded it sits at the top right next to the status indicator, when collapsed it is an icon at the bottom right of the footer.
- The button shows what is going on: "Checking ..." (while searching, it can't be triggered twice), "Up to date (Version ...)", "Install update ..." or, if something is wrong, "No connection" with a tap to try again. Results stay on the button for a few seconds, then it shows "Check for updates" again. If an update is already ready, it appears on the button right away, and a tap installs it.

### Improved
- The pages behind the tiles (Settings, Controls, Add-ons, Game files, What's new, Help & diagnostics and About) are now overviews made of cards that use the whole screen on the unfolded Fold and need no scrolling: three columns in landscape, two in portrait. Each card shows only the essentials, for example region, resolution and frame rate right there to switch, or for add-ons status, install and the "Active" switch.
- Everything else opens with a tap on the card (arrow at the top right) into a detail view across the whole page, where you can also scroll: setting descriptions and badges, the list of your own add-ons, the full change lists, the license texts. The Back key or gesture closes the detail view first, then the page. No feature was removed.
- On the outer display the cards stand in one column below each other, as before with scrolling.
- On the "What's new" page the newest version is shown large with New, Improved and Fixed side by side, with the two previous versions briefly next to it; "All changes" opens the full list.

### Fixed
- The minimap now really sits in the top right corner. Until now it stayed about 55 to 65 units away from the edge on the Fold, because the automatic push did not even start at such a large distance. If the minimap is still at the game's default position, it is always moved into the corner (2 units gap), no matter how large the distance was before. A custom position from HUD edit mode is left untouched.
- The square minimap now fills the yellow frame of HUD edit mode at the top and right without empty space. If you drag the frame into the corner, the minimap also sits in the corner. Empty space remains only at the left and bottom of the frame. Saving and reloading change nothing here.
- Under "Show minimap layout" (or in chat with "/tf minimap info") it now shows whether and why the minimap was moved, and how far it is from the edge afterwards.

## 2.3.3-fold8.25 – 11.11.2026

### New
- The "Add-ons" page now has the section "Custom add-ons". You can have a custom add-on installed from a link (GitHub page, WoWInterface page or a direct .zip link) or choose a downloaded .zip file ("From file"). Pasting from the clipboard works with one tap.
- Before installing, the launcher shows what is in the package: the add-on folders it found, with title, version and interface number. If the interface number does not match WoW Forever, it says "Maybe for another WoW version"; you can still install it. Nothing is written until you tap "Install".
- If the folder already exists, the launcher asks whether to replace it and keeps the old version as "<folder>_bak". The checked add-ons (Questie & Co.) and our TouchForever are never overwritten.
- Each custom add-on is listed with its version and source, can be switched on and off, and removed. For GitHub sources there is "Check for update" and "Update". Changes take effect from the next game start.
- CurseForge and Wago do not allow direct downloads. If you paste such a link, the launcher explains this, opens the page in the browser on request, and you then choose the downloaded .zip via "From file".

### Improved
- The home screen is easier to read: titles and lines of the hero and tiles now sit on a dark bar at the bottom edge that fades upward, so they stay legible on every image. The titles are much larger and bolder (hero 40 to 48, tiles 20 to 23), the lines below them brighter and larger (15 to 16), and nothing is smaller than 13.
- The large main image is centered: it fills the space and crops so the figure stays in the middle instead of being cut off at the edge. This applies to the five WoW images and the bundled images, also when collapsed.
- The home screen is calmer: instead of ornate double frames, each tile has a thin, subtle gold line with rounded corners, the card images are slightly darkened and desaturated, and the gaps between the tiles are a bit larger.
- Header and footer are easier to read (subtitle and footer at least 13 to 14, brighter). On the Settings, Help, What's new, About and Add-ons pages, row titles, descriptions and badges are larger and brighter, without changing the layout.

## 2.3.3-fold8.24 – 11.11.2026

### New
- In the editor, every stick (and every trackpad) on the "Behavior" page now has a "Sensitivity" slider. It shows the value that applies to exactly this stick (movement or camera, depending on the assignment) and takes effect immediately for all sticks of this kind. With key or mouse assignments it is greyed out and says that it only applies to gamepad stick assignments. The same value is also in Side menu › Controls › Sticks.
- The side menu in the game is tidier: the "Controls" tab now has collapsible groups – Layout (always open), Sticks, Tap and hold, Click wheel, Swipe, Touch elements and Other input modes. Only one group is open at a time; the menu remembers the one you last opened. When collapsed, each group shows its values on one line, for example "Movement 1.25 · Camera 1.0".
- Everything for controls can now be set in the game and takes effect immediately: 2-finger right-click, click wheel on/off, hold time, double-tap window, the five directions of the click wheel (a tap opens a selection list), "Swipe scrolls lists" including its speed, and "Reset layout to default" (with a confirmation).
- In the "Game" tab, under "Icons and cooldowns", there is the switch "Also global cooldown".

### Improved
- In the add-on (/tf, "Touch" page), the switch "Swipe scrolls lists" is gone. In its place there is a note that you set swipe and touch in Side menu › Controls. The add-on now always reports scrollable lists; only the app decides whether a swipe scrolls. A switch that was turned off in the add-on earlier no longer silently disables swiping.
- The launcher is tidier: what also exists in the game no longer appears twice in the settings. The tile is now called "Controls" and shows the control mode plus a note where to adjust the details in the game (Side menu › Controls).
- If you still want to change the game settings in the launcher, you will find the line "Change game settings here …" at the very bottom of the "Controls" page. After a confirmation, sticks, hiding, appearance, click wheel, layout reset, recognition language and cooldowns expand.
- The rows in the side menu are compact: short title on the left, value on the right; values use "-" and "+" instead of "Title: value" over two lines. Choices such as "Context menu on hold" or "Appearance" are buttons side by side.
- A value set in the game (hold time, double tap, click wheel, 2-finger right-click) is kept at the next start; the "Gestures" gear and the launcher also show the same value.
- The side menu reads its values fresh each time it opens, for example the stick sensitivity when it was changed in the touch editor.

### Fixed
- The official WoW images on the home screen no longer show a white, jagged edge.

## 2.3.3-fold8.23 – 11.11.2026

### New
- The launcher now ships with its own images (fantasy paintings, castles, mountains, caves from free sources): six large images for the home screen that change at every start, and one each for the tiles. So it looks good even without a network. Who painted or photographed them is listed under "About" at "Images".

## 2.3.3-fold8.22 – 11.11.2026

### New
- The launcher's home screen now fits the screen without scrolling, both expanded and collapsed. Header, "Play", tiles and footer share the height; only on very small windows does the page still scroll.
- Headings, the wordmark, tile titles and the "Play" button are now set in the Cinzel typeface (SIL Open Font License 1.1, the license text is under "About").
- The launcher shows pictures: the newest screenshot of your own from the Screenshots folder of your game is shown large on the home screen. If there is none, it shows official WoW artwork that it loads once from the network into app storage (a different one of the five images at every start), and zone maps on the tiles. Without a network, the drawn art remains.
- Two new switches under Settings › Start and updates: "Show my own screenshots in the launcher" and "Real WoW images in the launcher" (both on, both "Here only"). Off means: no images of this kind; for the WoW images nothing is loaded and no storage is used.
- Under Settings › "Start and updates" there is the switch "Install updates automatically" (off at first). When it is on, the launcher downloads a new version in the background and installs it while it is open and the game is not starting or running. Before that, the note "Installing update" with "Cancel" appears for 5 seconds.

### Improved
- Header, status pill, footer and spacing are more compact; the footer is one line.
- The subpages (Settings, Help, Game files and others) are set more densely: smaller line heights and spacing, so much more fits on one screen.
- On the first update after a manual installation, Android asks again; the note now says clearly: from the next update on, without asking.

## 2.3.3-fold8.21 – 10.11.2026

### New
- The launcher now looks like a fantasy game: warm stone and leather instead of navy blue, gold frames with small ornaments in the corners, gold headings in capitals and a red "Play" button with a gold edge. The status pill is a gem, the images are in gold and violet. Everything is drawn by hand; no third-party images or fonts are included.
- On the settings page every row shows where it can be changed: "Here only · from next start" takes effect only at the next game start, "Also in game" names the place, for example "Side menu > Controls". A line at the top explains the badges.
- On the page "Help and diagnostics" there is a new entry "What can I change in the game?" with everything that exists only in the game (side menu, touch editor, add-on options, WoW's own options).

### Improved
- Tappable areas on the home screen are now at least 48 dp high; the text has good readable contrast on all areas.
- Tiles light up gold when pressed; the large button gets brighter when pressed.

## 2.3.3-fold8.20 – 10.11.2026

### New
- New home screen as a tile layout: "Play" large on the left (or what is required beforehand: allow file access, install, update), six tiles on the right – Touch layout, Add-ons, Settings, Game files, What's new and Help and diagnostics. Each tile names its state in the second line and opens its own page with "Back". Collapsed, Play is at the top and the tiles in two columns below.
- At the top right, a status pill shows whether the game is ready (green), something is missing or an update is needed (yellow), something is running (blue), or there is a problem (red). A tap on it opens "Help and diagnostics".
- New pages: "Game files" (status, install or update, choose folder or find an existing copy, change or forget the Battle.net sign-in), "What's new" (the change list), "Help and diagnostics" (readiness list, error report) and "About" (version, license, source code, note that the app is unofficial and contains no game files).
- Footer with app version, licenses, source code and the note "unofficial". Names and images are provisional and will be replaced later.

### Improved
- A launcher update appears as a narrow strip with "Details" and "Install" instead of a large card; the changes for it are on the "What's new" page.
- The long change list and the large readiness list no longer fill the home screen. The Play button is always immediately visible.
- The "Touch layout" tile jumps to the controls settings.

## 2.3.3-fold8.19 – 10.11.2026

### Improved
- Key bar: when you switch to "Custom", the bar starts with a clearly different preset – Enter, Space, Tab, Escape and Backspace – so you can see straight away that the selection was applied. Custom entries you already created remain unchanged.
- Unassigned buttons are completely invisible in the game and let touches pass through. In the editor they stay visible with a faint "+".

## 2.3.3-fold8.18 – 10.11.2026

### New
- In the editor, the top of each element's sheet shows the "Button type" with picture and name (key, D-pad, key bar, stick, trackpad, dynamic sticks, scroll wheel). A tap switches the element; position and size stay.
- When adding, there is the "Key bar" as its own entry; in the sheet it is now also called that and no longer "Elongated button".
- "Entries" page of the key bar: at the top it says "Visible in the bar". There you directly see and change how many of the entries the bar shows at once. If you choose "Custom", the sheet jumps straight to the entries.

### Improved
- Minimap at the edge, fully in the corner: the map is no longer enlarged (that had covered the bag bar, coordinates and quest tracker); instead it is moved only once into the top right corner, leaving a small gap. The game previously held it back with its edge margin; that margin is now open.
- Edit mode and the minimap no longer work against each other: on opening, the map no longer jumps, and you can drag it into the corner yourself there. The add-on only intervenes once after logging in, after closing edit mode and when switching, and no longer keeps pushing it back.
- Bag bar "Backpack + ammo": the bar (frame and background) is now only as long as the visible buttons and sits right-aligned directly under the minimap (below it with "XP bar under the minimap"), without covering coordinates and quest tracker. If the bar is somewhere else in edit mode, it stays there.
- `/tf minimap info` additionally shows the rectangle of the map and cluster, the distance to the picture edge, the clamp distances, anchors, the width of the bag bar and the anchors saved in edit mode, so that you can measure on the device.

### Fixed
- Key bar with custom entries: a bar with fewer than four visible keys now grows when you add an entry, instead of staying at two keys.
- A new, unassigned button no longer shows the text "NONE". In the editor it carries a faint "+"; in the game it stays without text.

## 2.3.3-fold8.17 – 10.11.2026

### New
- Minimap at the edge: the map moves into the top right corner up to a small gap, and becomes slightly larger in the process; its bottom edge stays where it was. This makes it look more finished, and nothing below has to shift. In edit mode the game's default applies again; a custom position far from the corner is left untouched. Under Add-on options > Minimap there is the switch "Minimap at the edge" (also `/tf minimap corner on|off`).
- Chat buttons instead of a fixed chat bar: language, send (Enter), keyboard, switch channel and cancel are now ordinary buttons that you can move, enlarge and style in the editor like all others (style Current, F, G). You find them under "WoW action" > "Chat buttons" when assigning. The fixed bar under the chat and the switch "Chat bar" in the language settings are removed.
- In the editor, every button has the section "Visibility" and the setting "Show": Always, When chat is visible or When the chat line is open. In the editor you see every button.
- "Pin to chat window": the button keeps its distance to a chosen corner of the chat window and moves with it when you move or enlarge the chat. The distance is re-measured when moving in the editor; if the game does not report the chat window, the button stays where you put it.
- Every element has "When windows are open": Automatic (as before, according to the setting), Always visible (stays even with "All except ESC/keyboard") or Hide (disappears as soon as a larger window is open). The last escape or keyboard button always stays visible, so you cannot lock yourself out; the editor points this out. The chat buttons are set to "Always visible".
- The standard profile "Touch (WoW)" comes with two chat buttons (language and send), pinned to the top right corner of the chat window. Your own profiles that were already edited remain unchanged; you add the buttons there yourself in the editor.

### Improved
- Bag bar in three stages: "Full", "Backpack + ammo" (default) and "Off". In the default only the backpack with the number of free slots and, where present, the quiver or ammo pouch stay visible; the other bag slots are hidden. Selectable under Add-on options > Minimap (also `/tf bags full|pack|off`). If you had hidden or shown the bar in fold8.16, you get the new default.

## 2.3.3-fold8.16 – 10.11.2026

### New
- Radial menus open toward the middle of the screen: if the button sits at the top edge, the arc opens downward, at the left edge to the right, at the right edge to the left, and diagonally in the corners. The middle stays on the button; the menu is only shifted when it does not fit the screen in any direction. This applies to all radial menus, including the context menu.
- In the editor, every radial-menu button has the setting "Opens to": Automatic, Up, Down, Left or Right. A fixed direction applies, unless it would run off the screen: then the menu takes the next direction that fits. Old profiles stay on Automatic.
- Chat bar: directly at the chat window there is now a row of large buttons. It needs no keyboard: microphone (voice), Enter, keyboard, switch channel and cancel. When the input field is closed it shows only microphone and Enter to open; when it is open, the other buttons are added. If you have linked an item, you speak the rest and the line is sent with one tap. Under Settings > Language there is the switch "Chat bar".
- The chat window is aligned flush to the left edge when it is close to it, also after editing in the HUD editor. In the game there is more under Chat: "Keep chat flush left" and the button "Dock chat left now".
- The bag bar under the minimap is now hidden. You still open the bags with the key, the self menu or the WoW menu. In the add-on options (Minimap) it can be shown again.

### Improved
- The click wheel and the preview images in the editor now look the same as the other radial menus (ring segments in the selected style). Assignment and hit areas of the click wheel stay as they were.
- The center "Cancel" of the radial menu is opaque: the label of the button below no longer shows through.
- Update in the launcher: the bar shows real progress and ends visibly with "Update done" and a full bar, instead of running endlessly at "App restarting …".

### Fixed
- The world map (also maximized) no longer sticks out at the right edge of the screen: it is scaled down so that it fits completely on the screen and the X is visible. Can be turned off in the add-on options under Touch.

## 2.3.3-fold8.15 – 10.11.2026

### New
- All radial menus now look the same as the stick ring: real ring segments in the selected style (Current, F, G) instead of single tiles or pie slices. This applies to the context menu, the "WoW menu" and the radial menus of your buttons.
- Everywhere the same arc: 270 degrees upward, a piece at the bottom stays free. The assignment and order of the radial menus stay the same; the fields only spread along the arc, and with more than eight fields a second ring is added outside.
- Hold your own character and the self menu opens: bags, character, spellbook, talents and map.
- Hold another player and a mini menu appears: inside whisper by voice, whisper by keyboard, invite, follow, trade and wave; outside add friend, inspect, helpful spells in range and "More …". "More …" opens the game's player menu as before.
- Empty fields of the stick ring and radial menus are hidden in the game and cannot be tapped: fields without an assignment, and fields whose action-bar slot is currently empty. In the editor all fields stay visible (empty ones dashed) so that you can assign them.

### Improved
- The icons in the radial menus are slightly smaller, the name sits small below. The field under the finger lights up and shows its full name next to it.

## 2.3.3-fold8.14 – 10.11.2026

### New
- Change list in the launcher: when an update is offered, you see all changes since your version, large and sorted into New, Improved and Fixed.
- The list stays visible while the update downloads and installs, so you can read it at your leisure.
- After an update the launcher shows "New in this version" once, with a button "Got it".
- Under Settings > Updates there is the entry "Changes" with the complete list.
- The context menu is now a circle around your finger: the entries lie in an arc at the top, a piece at the bottom stays free so the hand covers nothing. The important ones (attacks, use) are inside and at the top; more than eight entries go onto a second ring outside.
- Hold another player and the game's own menu opens (whisper, invite, trade, inspect, follow, focus and more), just like a right-click on their portrait.
- The entries show icons instead of text: spells with their icon from the game, attack, loot, skin, talk to, select target, move, discard and the others with drawn icons and the name small below. If a picture is missing, the name is shown.
- In the bags, every item has "Use" (for equipment "Equip", at a vendor "Sell") as a real right-click.

### Improved
- The menu avoids covering the target window and tooltip where possible and moves inward at the screen edge. Only if the finger would otherwise lie on a tile does the arc turn, as little as necessary.
- The spell lists now contain only spells that can be cast on another unit.
- The command /tf context in the game now also shows whether a unit is attackable, how equipping was detected and what the game's menu did.

### Fixed
- Yellow (neutral) enemies that can be attacked showed "Talk to". Everything attackable now gets the attack menu; "Talk to" appears only for vendors, quest givers and similar.
- For players, spells such as Track Hidden, Find Treasure and Stoneform were in the menu.
- For a cape in the bags, "Equip" was missing.

## 2.3.3-fold8.13 – 10.11.2026

### New
- Context menu on hold: if you hold a unit (player, NPC, living or dead enemy), suitable actions are offered, attacks sorted by range.
- The context menu also works on items in bags, bank and equipment. "Discard" asks yes or no first.

### Improved
- The TouchForever add-on now recognizes what you are pressing (unit, bag, bank, equipment).

## 2.3.3-fold8.12 – 09.11.2026

### New
- Touch buttons show symbols instead of large text (Space, Tab, Shift, Enter, arrows, mouse). The text adapts to the button size.

### Improved
- The touch editor has calmer colors (slate and bronze) and is more clearly structured.

### Fixed
- Labels of buttons in the editor are now displayed reliably.
- Several errors in the touch editor fixed.

## 2.3.3-fold8.11 – 09.11.2026

### New
- Key bar "Custom": you set the entries yourself, assign them like the ring buttons, and sort them by grip or arrow.
- Two more bar styles (F and G) for the key bar.

## 2.3.3-fold8.10 – 09.11.2026

### New
- Style switch for the touch elements: "Current", "F" or "G" for comparing on the device.
- Contrast slider and idle timer: after a pause the touch elements become more subtle.

## 2.3.3-fold8.9 – 09.11.2026

### New
- Swipe in lists: vertical swipes in lists and windows of the game scroll like the mouse wheel.
- Progress bar when installing a launcher update.

## 2.3.3-fold8.8 – 09.11.2026

### New
- Scroll wheel: the speed can be set (with presets).
- On the stick there is a second, outer ring with buttons.
- In the side menu, "Add-on settings" opens the add-on's settings window without tapping.

### Improved
- Square minimap: zone name, day-and-night indicator and search sit neatly in the corners, the zoom buttons are larger.
- The add-on's settings window is organized into seven clear pages.
- The chat window ends flush with the screen edge.
- Touch elements make way for an open window already at 10 percent overlap.

### Fixed
- The scroll speed of a mouse-wheel element could not be changed in the editor.

## 2.3.3-fold8.7 – 09.11.2026

### Fixed
- The add-on was not up to date in versions fold8.2 to fold8.6. Now the current version is always shipped and checked before publishing.
- The game interface is no longer reloaded by itself.
- Touch elements are hidden behind an open world map.
