-- TouchForever: language chosen by the launcher app. The app rewrites this file at every start (it follows the language of the phone:
-- ns.appLanguage = "de" or "en"). As shipped (nil) the addon follows the game client: deDE -> German, otherwise English.
-- Read by TouchForeverLocale.lua, which is loaded right after this file.
local ADDON, ns = ...
if ns then ns.appLanguage = nil end
