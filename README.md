[![GitHub last commit](https://img.shields.io/github/last-commit/JustZhenya/TelegramChat.svg)](https://github.com/JustZhenya/TelegramChat/commits/master)

![resource icon](https://www.spigotmc.org/data/resource_icons/16/16576.jpg?1476392100)

## Welcome to the TelegramChat GitHub repository!
TelegramChat is a Bukkit plugin compatible with Paper/Spigot versions 1.7 through 1.20.4 (and possibly more recent versions), that connects Telegram with Minecraft.

## Fork changes
- Fixed so-called "reconnects" to TG API on every error
- Removed latency TG->MC and drastically reduced latency MC->TG and TG->TG
- Users that blocked the bot or that have been deleted on TG are now removed from broadcast list
- Added commands /online and /linktelegram to TG bot
- Date is now long instead of int
- Reworked getUpdates loop
- Simplified code a bit

### TODO
- Thread safety (Bukkit forbits calling their APIs from other threads)
- Proper localisation

## Usage
1. Create a new bot by messaging the @BotFather and following the instructions.
2. Obtain the token (`/tokentype` in Telegram) and type  `/linktelegram <token>` to link the newly created Bot to your Server. This needs to be done once.
3. Each user needs to be linked to their Telegram account in order to chat mc <-> telegram.
 
### Private chats
1. Your users need to type `/telegram` in-game to get a temporary code for linking
2. The code is then sent to the Telegram bot.

### Group chats
1. As an admin, you need to set the privacy setting to disabled using the BotFather. This is done by typing `/setprivacy` and then disabled.
2. Users just need to join the group to see the MC chat. They might want to link their account by posting their /linktelegram code in the group chat.

## Credits
Originally made by [mastercake10](https://github.com/mastercake10) on [GitHub](https://github.com/mastercake10/TelegramChat)
Forked by [JustZhenya](https://github.com/JustZhenya) on [GitHub](https://github.com/JustZhenya/TelegramChat)